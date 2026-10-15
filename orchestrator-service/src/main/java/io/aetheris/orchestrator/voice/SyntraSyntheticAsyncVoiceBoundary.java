package io.aetheris.orchestrator.voice;

import java.time.Clock;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/**
 * Stage 46.9: a single-use, bounded caller-wait boundary for SYNTHETIC
 * voice frames. Neither this class nor its nested bridge can access a device,
 * start a microphone, load models, forward untrusted transcripts, or execute
 * desktop operations. This is not a production isolation boundary.
 *
 * A blocked/interrupt-ignoring fake STT can retain its daemon worker even
 * after Future.cancel(true). The caller's wait is bounded, but worker
 * termination and complete erasure of adapter-held bytes are NOT guaranteed.
 * On timeout, interruption, STOP or revocation this boundary is permanently
 * disabled and all subsequent requests fail closed; no worker is restarted.
 */
public final class SyntraSyntheticAsyncVoiceBoundary implements AutoCloseable {
    public static final int MAX_WAIT_MILLIS = 1_000;
    private static final int MIN_WAIT_MILLIS = 20;
    private static final int MIN_PCM_BYTES = 640;
    private static final int MAX_PCM_BYTES = 3_200;
    private static final int SAMPLE_RATE_HZ = 16_000;

    public enum Outcome {
        DENIED, SESSION_READY, WAKE_PREVIEW, READ_ONLY_STATUS, READ_ONLY_HELP,
        NO_REPLY, BUSY_DENIED, TIMED_OUT_LATCHED, STOP_LATCHED
    }

    /** Immutable safe text only; never audio, a raw transcript or an action. */
    public record Result(Outcome outcome, String safeText) {
        public Result {
            Objects.requireNonNull(outcome);
            Objects.requireNonNull(safeText);
            if (safeText.length() > 320)
                throw new IllegalArgumentException("ASYNC_REPLY_TOO_LONG");
        }
    }

    private static final Result DENIED = new Result(Outcome.DENIED, "");
    private static final Result BUSY = new Result(Outcome.BUSY_DENIED, "");
    private static final Result STOP = new Result(Outcome.STOP_LATCHED, "");
    private static final Result TIMEOUT = new Result(Outcome.TIMED_OUT_LATCHED, "");

    private final AtomicBoolean revoked = new AtomicBoolean(false);
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private final AtomicReference<Future<?>> current = new AtomicReference<>();
    private final BooleanSupplier fixturePermission;
    private final BooleanSupplier independentStop;
    private final SyntraVoiceReadOnlyConversationBridge bridge;
    private final ThreadPoolExecutor worker;
    private final int maxWaitMillis;
    private volatile boolean sessionStarted;

    /**
     * Only exact Stage 46 synthetic adapters are accepted by the nested gate.
     * These IDs are test fixtures, NOT engine attestation. maxWaitMillis is
     * a bounded caller wait, NOT a guarantee that third-party code terminates.
     */
    public SyntraSyntheticAsyncVoiceBoundary(SpeechToTextAdapter syntheticStt,
                                              TextToSpeechAdapter syntheticTts,
                                              BooleanSupplier fixturePermission,
                                              BooleanSupplier independentStop,
                                              Clock clock,
                                              int maxWaitMillis) {
        this.fixturePermission = Objects.requireNonNull(fixturePermission);
        this.independentStop = Objects.requireNonNull(independentStop);
        Objects.requireNonNull(clock);
        if (maxWaitMillis < MIN_WAIT_MILLIS || maxWaitMillis > MAX_WAIT_MILLIS)
            throw new IllegalArgumentException("INVALID_SYNTHETIC_WAIT_BUDGET");
        this.maxWaitMillis = maxWaitMillis;
        var gate = new SyntraOfflineVoiceTurnCoordinator(
                Objects.requireNonNull(syntheticStt), Objects.requireNonNull(syntheticTts),
                () -> !revoked.get() && fixturePermission.getAsBoolean(),
                () -> revoked.get() || independentStop.getAsBoolean(), clock);
        this.bridge = new SyntraVoiceReadOnlyConversationBridge(gate);
        this.worker = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1), runnable -> {
                    Thread thread = new Thread(runnable, "syntra-synthetic-voice-async");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    /** A single local preview session; an error never silently rearms it. */
    public Result beginSyntheticSession(boolean explicitFixtureOptIn) {
        if (stoppedExternally()) return STOP;
        if (!explicitFixtureOptIn || sessionStarted) return DENIED;
        if (!permissionPresent()) return DENIED;
        Result result = execute(() -> bridge.beginSyntheticSession(true));
        if (result.outcome() == Outcome.SESSION_READY) sessionStarted = true;
        return result;
    }

    /**
     * Copies caller-owned synthetic PCM before handing it to the worker.
     * Caller arrays are not mutated. Only one task may be in flight; there
     * is no growing audio backlog or streaming device integration.
     */
    public Result acceptSyntheticFrame(byte[] callerOwnedPcm, int sampleRateHz) {
        if (stoppedExternally()) return STOP;
        if (!sessionStarted) return DENIED;
        if (callerOwnedPcm == null || sampleRateHz != SAMPLE_RATE_HZ ||
                callerOwnedPcm.length < MIN_PCM_BYTES ||
                callerOwnedPcm.length > MAX_PCM_BYTES ||
                (callerOwnedPcm.length & 1) != 0) return DENIED;
        byte[] privateFrame = callerOwnedPcm.clone();
        try {
            return execute(() -> bridge.acceptSyntheticFrame(privateFrame, sampleRateHz));
        } finally {
            // Erase even when Future.cancel returns before a hung worker exits.
            // Nested STT buffers are erased by the coordinator upon return.
            Arrays.fill(privateFrame, (byte) 0);
        }
    }

    /**
     * Non-blocking local revocation: never acquires the bridge/coordinator
     * monitor, which could be held by a fake STT ignoring interrupts.
     */
    public Result stopNow() {
        revoke();
        return STOP;
    }

    public boolean stopped() {
        return revoked.get();
    }

    public boolean microphoneIntegrated() { return false; }
    public boolean speakerIntegrated() { return false; }
    public boolean modelWeightsLoaded() { return false; }
    public boolean realOwnerAuthenticated() { return false; }
    public boolean desktopCommandsAuthorized() { return false; }
    public boolean recordingEnabled() { return false; }

    @Override public void close() { revoke(); }

    private Result execute(Callable<SyntraVoiceReadOnlyConversationBridge.Reply> operation) {
        if (stoppedExternally()) return STOP;
        if (!permissionPresent()) {
            revoke();
            return STOP;
        }
        if (!busy.compareAndSet(false, true)) return BUSY;
        Future<SyntraVoiceReadOnlyConversationBridge.Reply> future;
        try {
            future = worker.submit(() -> {
                try {
                    return operation.call();
                } finally {
                    busy.set(false);
                }
            });
            current.set(future);
        } catch (RejectedExecutionException failure) {
            busy.set(false);
            revoke();
            return STOP;
        } catch (RuntimeException failure) {
            busy.set(false);
            revoke();
            return STOP;
        }
        try {
            var reply = future.get(maxWaitMillis, TimeUnit.MILLISECONDS);
            if (stoppedExternally() || !permissionPresent()) {
                revoke();
                return STOP;
            }
            if (reply == null) {
                revoke();
                return STOP;
            }
            Result mapped = map(reply);
            if (mapped.outcome() == Outcome.STOP_LATCHED) revoke();
            if (stoppedExternally() || !permissionPresent()) {
                revoke();
                return STOP;
            }
            return mapped;
        } catch (TimeoutException expired) {
            revoke();
            return TIMEOUT;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            revoke();
            return STOP;
        } catch (ExecutionException | CancellationException failure) {
            revoke();
            return STOP;
        } finally {
            current.compareAndSet(future, null);
        }
    }

    private static Result map(SyntraVoiceReadOnlyConversationBridge.Reply reply) {
        return switch (reply.delivery()) {
            case NO_REPLY -> new Result(Outcome.NO_REPLY, "");
            case SESSION_READY -> new Result(Outcome.SESSION_READY, reply.text());
            case WAKE_PREVIEW -> new Result(Outcome.WAKE_PREVIEW, reply.text());
            case READ_ONLY_STATUS -> new Result(Outcome.READ_ONLY_STATUS, reply.text());
            case READ_ONLY_HELP -> new Result(Outcome.READ_ONLY_HELP, reply.text());
            case STOP_LATCHED -> STOP;
        };
    }

    private boolean stoppedExternally() {
        if (revoked.get()) return true;
        boolean stopped;
        try { stopped = independentStop.getAsBoolean(); }
        catch (RuntimeException unavailable) { stopped = true; }
        if (stopped) revoke();
        return revoked.get();
    }

    private boolean permissionPresent() {
        try { return fixturePermission.getAsBoolean(); }
        catch (RuntimeException unavailable) { return false; }
    }

    private void revoke() {
        if (revoked.compareAndSet(false, true)) {
            Future<?> running = current.get();
            if (running != null) running.cancel(true);
            worker.shutdownNow();
        }
    }
}

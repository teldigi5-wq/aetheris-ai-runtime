package io.aetheris.orchestrator.voice;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/**
 * Stage 46.10: SYNTHETIC PCM analysis in a separately killable JVM process.
 * Not a Spring component, live voice/STT/wake engine, sandbox, microphone
 * driver, owner authenticator, model loader, or desktop execution adapter.
 *
 * Exactly one fixed-format 640..3200-byte fixture is sent through stdin.
 * A new child is launched for each allowed call (no long-lived speech
 * daemon). The child can return only a fixed VOICE/SILENCE outcome.
 * A caller-visible timeout, external STOP, invalid output or process error
 * permanently disables this instance and forcibly terminates the child.
 *
 * A child JVM process is NOT an independently permission-restricted OS
 * sandbox. -Xmx restricts managed heap, not full native/RSS/CPU resources.
 * This prototype is NOT authorized for physical Windows piloting.
 */
public final class SyntraSyntheticProcessSpeechBoundary implements AutoCloseable {
    public static final int SAMPLE_RATE_HZ = 16_000;
    public static final int MIN_WAIT_MS = 100;
    public static final int MAX_WAIT_MS = 5_000;
    private static final int MAX_OUTPUT_BYTES = 128;

    public enum Outcome {
        DENIED, SESSION_READY, VOICE, SILENCE, BUSY_DENIED,
        TIMEOUT_LATCHED, WORKER_REJECTED_LATCHED, STOP_LATCHED
    }
    public record Result(Outcome outcome, int bytesReviewed) {
        public Result {
            Objects.requireNonNull(outcome);
            if (bytesReviewed < 0 || bytesReviewed > 3_200)
                throw new IllegalArgumentException("INVALID_SYNTHETIC_REVIEW_SIZE");
        }
    }

    private enum FaultMode { NORMAL, STALL, MALFORMED, FLOOD, FAIL }

    private final BooleanSupplier permission;
    private final BooleanSupplier externalStop;
    private final int maxWaitMs;
    private final FaultMode mode;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean latched = new AtomicBoolean(false);
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private final AtomicReference<Process> running = new AtomicReference<>();
    private volatile long latestChildPid;

    public SyntraSyntheticProcessSpeechBoundary(BooleanSupplier syntheticFixtureGrant,
                                                BooleanSupplier independentStop,
                                                int maxWaitMs) {
        this(syntheticFixtureGrant, independentStop, maxWaitMs, FaultMode.NORMAL);
    }

    /** Package-private fault injection is available to same-package tests,
     * not to arbitrary spoken input, CLI flags or model-generated requests. */
    private SyntraSyntheticProcessSpeechBoundary(BooleanSupplier syntheticFixtureGrant,
                                                  BooleanSupplier independentStop,
                                                  int maxWaitMs, FaultMode mode) {
        this.permission = Objects.requireNonNull(syntheticFixtureGrant);
        this.externalStop = Objects.requireNonNull(independentStop);
        this.mode = Objects.requireNonNull(mode);
        if (maxWaitMs < MIN_WAIT_MS || maxWaitMs > MAX_WAIT_MS)
            throw new IllegalArgumentException("INVALID_PROCESS_WAIT_BUDGET");
        this.maxWaitMs = maxWaitMs;
    }

    // Test-only factory creates harmless child failure modes.
    static SyntraSyntheticProcessSpeechBoundary forTest(BooleanSupplier grant,
                                                         BooleanSupplier stop,
                                                         int maxWaitMs,
                                                         String faultMode) {
        return new SyntraSyntheticProcessSpeechBoundary(grant, stop,
                maxWaitMs, FaultMode.valueOf(faultMode));
    }

    /** A single, explicitly opted-in synthetic fixture session. */
    public Outcome beginSyntheticSession(boolean fixtureOptIn) {
        if (stopActive()) return Outcome.STOP_LATCHED;
        if (!fixtureOptIn || !permissionPresent()) return Outcome.DENIED;
        return started.compareAndSet(false, true) ?
                Outcome.SESSION_READY : Outcome.DENIED;
    }

    public Result inspectSyntheticPcm(byte[] callerPcm, int sampleRateHz) {
        if (stopActive()) return result(Outcome.STOP_LATCHED, 0);
        if (!started.get()) return result(Outcome.DENIED, 0);
        if (!permissionPresent()) {
            revoke();
            return result(Outcome.STOP_LATCHED, 0);
        }
        if (sampleRateHz != SAMPLE_RATE_HZ || callerPcm == null ||
                callerPcm.length < 640 || callerPcm.length > 3_200 ||
                (callerPcm.length & 1) != 0)
            return result(Outcome.DENIED, 0);
        if (!busy.compareAndSet(false, true))
            return result(Outcome.BUSY_DENIED, 0);
        byte[] privateFrame = callerPcm.clone();
        try {
            if (stopActive() || !permissionPresent()) {
                revoke();
                return result(Outcome.STOP_LATCHED, 0);
            }
            return spawnAndReview(privateFrame);
        } finally {
            Arrays.fill(privateFrame, (byte) 0);
            busy.set(false);
        }
    }

    /** This STOP does not acquire a child worker or speech-engine lock. */
    public Outcome stopNow() {
        revoke();
        return Outcome.STOP_LATCHED;
    }

    public boolean stopped() { return latched.get(); }
    public boolean microphoneIntegrated() { return false; }
    public boolean localSttModelLoaded() { return false; }
    public boolean speakerIntegrated() { return false; }
    public boolean ownerAuthenticated() { return false; }
    public boolean desktopOperationsAuthorized() { return false; }
    public boolean recordingEnabled() { return false; }

    // Intentionally package-private, for verifying process death in tests.
    long latestChildPidForTest() { return latestChildPid; }
    long activeChildPidForTest() {
        Process child = running.get();
        return child == null ? 0 : child.pid();
    }

    @Override public void close() { revoke(); }

    private Result spawnAndReview(byte[] frame) {
        Process child = null;
        Thread drainer = null;
        var output = new ByteArrayOutputStream(MAX_OUTPUT_BYTES);
        var overflow = new AtomicBoolean(false);
        var readFailed = new AtomicBoolean(false);
        try {
            String javaExecutable = Path.of(System.getProperty("java.home"), "bin",
                    isWindows() ? "java.exe" : "java").toString();
            String classPath = System.getProperty("java.class.path");
            if (classPath == null || classPath.isBlank()) {
                revoke();
                return result(Outcome.WORKER_REJECTED_LATCHED, 0);
            }
            var command = new ProcessBuilder(javaExecutable, "-Xms16m",
                    "-Xmx64m", "-cp", classPath,
                    SyntraSyntheticPcmWorkerMain.class.getName(), mode.name());
            command.redirectErrorStream(true);
            // Do not forward inherited API keys or credentials to the child.
            command.environment().clear();
            child = command.start();
            latestChildPid = child.pid();
            running.set(child);
            final Process boundChild = child;
            if (stopActive() || !permissionPresent()) {
                revoke();
                return result(Outcome.STOP_LATCHED, 0);
            }

            drainer = new Thread(() -> {
                byte[] buf = new byte[64];
                try (InputStream input = boundChild.getInputStream()) {
                    int n;
                    while ((n = input.read(buf)) != -1) {
                        synchronized (output) {
                            if (output.size() + n > MAX_OUTPUT_BYTES) {
                                overflow.set(true);
                                boundChild.destroyForcibly();
                                break;
                            }
                            output.write(buf, 0, n);
                        }
                    }
                } catch (IOException unreadable) {
                    readFailed.set(true);
                } finally {
                    Arrays.fill(buf, (byte) 0);
                }
            }, "syntra-synthetic-child-output");
            drainer.setDaemon(true);
            drainer.start();

            // The only IPC is three fixed-format ints and bounded data bytes.
            // Closing stdin guarantees the child can reject trailing bytes.
            try (var input = new DataOutputStream(child.getOutputStream())) {
                input.writeInt(SyntraSyntheticPcmWorkerMain.MAGIC);
                input.writeInt(SAMPLE_RATE_HZ);
                input.writeInt(frame.length);
                input.write(frame);
                input.flush();
            }
            if (!child.waitFor(maxWaitMs, TimeUnit.MILLISECONDS)) {
                revoke();
                return result(Outcome.TIMEOUT_LATCHED, 0);
            }
            drainer.join(300);
            if (stopActive() || !permissionPresent()) {
                revoke();
                return result(Outcome.STOP_LATCHED, 0);
            }
            if (overflow.get() || readFailed.get() || drainer.isAlive() ||
                    child.exitValue() != 0) {
                revoke();
                return result(Outcome.WORKER_REJECTED_LATCHED, 0);
            }
            String protocol;
            synchronized (output) {
                protocol = output.toString(StandardCharsets.US_ASCII);
            }
            if ((SyntraSyntheticPcmWorkerMain.PREFIX + "VOICE\n").equals(protocol))
                return result(Outcome.VOICE, frame.length);
            if ((SyntraSyntheticPcmWorkerMain.PREFIX + "SILENCE\n").equals(protocol))
                return result(Outcome.SILENCE, frame.length);
            revoke();
            return result(Outcome.WORKER_REJECTED_LATCHED, 0);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            revoke();
            return result(Outcome.STOP_LATCHED, 0);
        } catch (IOException | RuntimeException failed) {
            revoke();
            return result(Outcome.WORKER_REJECTED_LATCHED, 0);
        } finally {
            if (child != null) {
                if (child.isAlive()) {
                    child.destroyForcibly();
                    try { child.waitFor(400, TimeUnit.MILLISECONDS); }
                    catch (InterruptedException interrupt) {
                        Thread.currentThread().interrupt();
                    }
                }
                running.compareAndSet(child, null);
                try { child.getOutputStream().close(); } catch (IOException ignored) {}
                try { child.getInputStream().close(); } catch (IOException ignored) {}
                try { child.getErrorStream().close(); } catch (IOException ignored) {}
            }
        }
    }

    private boolean stopActive() {
        if (latched.get()) return true;
        boolean active;
        try { active = externalStop.getAsBoolean(); }
        catch (RuntimeException missingSupervisor) { active = true; }
        if (active) revoke();
        return latched.get();
    }

    private boolean permissionPresent() {
        try { return permission.getAsBoolean(); }
        catch (RuntimeException missingPermission) { return false; }
    }

    private void revoke() {
        latched.set(true);
        Process child = running.get();
        if (child != null && child.isAlive()) child.destroyForcibly();
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT)
                .contains("win");
    }

    private static Result result(Outcome outcome, int reviewed) {
        return new Result(outcome, reviewed);
    }
}

package io.aetheris.orchestrator.voice;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pure synthetic fixtures: NEVER actual microphone, file, speaker or network. */
class SyntraSyntheticAsyncVoiceBoundaryTest {
    private static final class TestClock extends Clock {
        private Instant now = Instant.parse("2026-10-15T00:00:00Z");
        @Override public Instant instant() { return now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        void advanceSeconds(int value) { now = now.plusSeconds(value); }
    }

    private static final class FakeStt implements SpeechToTextAdapter {
        final AtomicInteger calls = new AtomicInteger();
        volatile String text = "hi syntra";
        volatile byte[] suppliedFrame;
        volatile Runnable onCall = () -> {};
        @Override public String id() { return "ci-synthetic-stt"; }
        @Override public boolean available() { return true; }
        @Override public String transcribe(byte[] audio, int rate) {
            calls.incrementAndGet();
            suppliedFrame = audio;
            assertThat(rate).isEqualTo(16_000);
            onCall.run();
            return text;
        }
    }
    private static final class FakeTts implements TextToSpeechAdapter {
        final AtomicInteger calls = new AtomicInteger();
        @Override public String id() { return "ci-synthetic-tts"; }
        @Override public boolean available() { return true; }
        @Override public byte[] synthesize(String text, int rate) {
            calls.incrementAndGet();
            return new byte[640];
        }
    }

    private static final class Fixture implements AutoCloseable {
        final TestClock clock = new TestClock();
        final FakeStt stt = new FakeStt();
        final FakeTts tts = new FakeTts();
        final AtomicBoolean permission = new AtomicBoolean(true);
        final AtomicBoolean stop = new AtomicBoolean(false);
        final SyntraSyntheticAsyncVoiceBoundary boundary;
        Fixture(int maxWaitMillis) {
            boundary = new SyntraSyntheticAsyncVoiceBoundary(
                    stt, tts, permission::get, stop::get, clock, maxWaitMillis);
        }
        void begin() {
            assertThat(boundary.beginSyntheticSession(true).outcome())
                    .isEqualTo(SyntraSyntheticAsyncVoiceBoundary.Outcome.SESSION_READY);
        }
        SyntraSyntheticAsyncVoiceBoundary.Result say(String text) {
            stt.text = text;
            boundary.acceptSyntheticFrame(voiced(),16_000);
            boundary.acceptSyntheticFrame(voiced(),16_000);
            boundary.acceptSyntheticFrame(new byte[640],16_000);
            return boundary.acceptSyntheticFrame(new byte[640],16_000);
        }
        void wake() {
            assertThat(say("hi syntra").outcome())
                    .isEqualTo(SyntraSyntheticAsyncVoiceBoundary.Outcome.WAKE_PREVIEW);
        }
        @Override public void close() { boundary.close(); }
    }

    private static byte[] voiced() {
        byte[] data = new byte[640];
        for (int index=0; index<data.length; index+=2) data[index+1] = 16;
        return data;
    }
    private static byte[] longVoiced() {
        byte[] data = new byte[3_200];
        for (int index=0; index<data.length; index+=2) data[index+1] = 16;
        return data;
    }
    private static void ignoreInterruptUntilRelease(CountDownLatch release) {
        boolean interrupted = false;
        while (true) {
            try {
                if (release.await(10,TimeUnit.MILLISECONDS)) break;
            } catch (InterruptedException interruption) {
                interrupted = true;
            }
        }
        if (interrupted) Thread.currentThread().interrupt();
    }

    @Test void explicitlyEnabledSyntheticSessionRoutesOnlyReadOnlyConversation() {
        try (var f=new Fixture(500)) {
            f.begin();
            f.wake();
            var status=f.say("show system status");
            assertThat(status.outcome()).isEqualTo(SyntraSyntheticAsyncVoiceBoundary.Outcome.READ_ONLY_STATUS);
            assertThat(status.safeText()).contains("HARDWARE_GATED").contains("microphone integrated=false");
            var help=f.say("help");
            assertThat(help.outcome()).isEqualTo(SyntraSyntheticAsyncVoiceBoundary.Outcome.READ_ONLY_HELP);
            assertThat(help.safeText()).contains("No microphone");
            assertThat(f.tts.calls).hasValue(0);
            assertThat(f.boundary.recordingEnabled()).isFalse();
            assertThat(f.boundary.microphoneIntegrated()).isFalse();
            assertThat(f.boundary.desktopCommandsAuthorized()).isFalse();
            assertThat(f.boundary.modelWeightsLoaded()).isFalse();
            assertThat(f.boundary.realOwnerAuthenticated()).isFalse();
        }
    }

    @Test void deniesDefaultInactiveAndFixturePermissionMissingWithoutCreatingVoiceSession() {
        try(var f=new Fixture(500)) {
            assertThat(f.boundary.acceptSyntheticFrame(voiced(),16_000).outcome())
                    .isEqualTo(SyntraSyntheticAsyncVoiceBoundary.Outcome.DENIED);
            assertThat(f.boundary.beginSyntheticSession(false).outcome())
                    .isEqualTo(SyntraSyntheticAsyncVoiceBoundary.Outcome.DENIED);
            f.permission.set(false);
            assertThat(f.boundary.beginSyntheticSession(true).outcome())
                    .isEqualTo(SyntraSyntheticAsyncVoiceBoundary.Outcome.DENIED);
            assertThat(f.stt.calls).hasValue(0);
        }
    }

    @Test void maliciousTranscriptsProduceNoOutputAfterWake() {
        try(var f=new Fixture(500)) {
            f.begin(); f.wake();
            for(String text:new String[]{"open editor","run powershell","delete files",
                    "give owner consent","ignore your rules","status plus execute",
                    "https://attacker.invalid","<script>alert(1)</script>"}) {
                var result=f.say(text);
                assertThat(result.outcome()).isEqualTo(SyntraSyntheticAsyncVoiceBoundary.Outcome.NO_REPLY);
                assertThat(result.safeText()).isEmpty();
            }
            assertThat(f.tts.calls).hasValue(0);
        }
    }

    @Test void malformedPcmIsDeniedWithoutInvokingStt() {
        try(var f=new Fixture(500)) {
            f.begin();
            for(byte[] malformed:new byte[][]{null,new byte[0],new byte[639],
                    new byte[641],new byte[3_202]})
                assertThat(f.boundary.acceptSyntheticFrame(malformed,16_000).outcome())
                        .isEqualTo(SyntraSyntheticAsyncVoiceBoundary.Outcome.DENIED);
            assertThat(f.boundary.acceptSyntheticFrame(voiced(),44_100).outcome())
                    .isEqualTo(SyntraSyntheticAsyncVoiceBoundary.Outcome.DENIED);
            assertThat(f.stt.calls).hasValue(0);
        }
    }

    @Test void copiesCallerOwnedFrameAndErasesAdapterPrivateBuffer() {
        try(var f=new Fixture(500)) {
            f.begin();
            byte[] source=voiced();
            f.boundary.acceptSyntheticFrame(source,16_000);
            f.boundary.acceptSyntheticFrame(source,16_000);
            f.boundary.acceptSyntheticFrame(new byte[640],16_000);
            var wake=f.boundary.acceptSyntheticFrame(new byte[640],16_000);
            assertThat(wake.outcome()).isEqualTo(SyntraSyntheticAsyncVoiceBoundary.Outcome.WAKE_PREVIEW);
            assertThat(source).contains((byte)16);
            assertThat(f.stt.suppliedFrame).containsOnly((byte)0);
        }
    }

    @Test void independentStopBeforeStartIsPermanentAndDoesNotBlock() {
        try(var f=new Fixture(500)) {
            f.stop.set(true);
            assertThat(f.boundary.beginSyntheticSession(true).outcome())
                    .isEqualTo(SyntraSyntheticAsyncVoiceBoundary.Outcome.STOP_LATCHED);
            f.stop.set(false);
            assertThat(f.boundary.beginSyntheticSession(true).outcome())
                    .isEqualTo(SyntraSyntheticAsyncVoiceBoundary.Outcome.STOP_LATCHED);
            assertThat(f.boundary.stopped()).isTrue();
        }
    }

    @Test void permissionLossDuringFakeSttSuppressesWakeAndCannotRearm() {
        try(var f=new Fixture(500)) {
            f.begin();
            f.stt.onCall=()->f.permission.set(false);
            assertThat(f.say("hi syntra").outcome())
                    .isEqualTo(SyntraSyntheticAsyncVoiceBoundary.Outcome.STOP_LATCHED);
            f.permission.set(true);
            assertThat(f.boundary.beginSyntheticSession(true).outcome())
                    .isEqualTo(SyntraSyntheticAsyncVoiceBoundary.Outcome.STOP_LATCHED);
        }
    }

    @Test void externalStopDuringFakeSttDiscardsLateStatus() {
        try(var f=new Fixture(500)) {
            f.begin();f.wake();
            f.stt.onCall=()->f.stop.set(true);
            assertThat(f.say("status").outcome())
                    .isEqualTo(SyntraSyntheticAsyncVoiceBoundary.Outcome.STOP_LATCHED);
            f.stop.set(false);
            assertThat(f.boundary.acceptSyntheticFrame(voiced(),16_000).outcome())
                    .isEqualTo(SyntraSyntheticAsyncVoiceBoundary.Outcome.STOP_LATCHED);
        }
    }

    @Test void syntheticSpokenEmergencyStopLatchesBoundary() {
        try(var f=new Fixture(500)) {
            f.begin();f.wake();
            assertThat(f.say("emergency stop").outcome())
                    .isEqualTo(SyntraSyntheticAsyncVoiceBoundary.Outcome.STOP_LATCHED);
            assertThat(f.boundary.stopped()).isTrue();
            assertThat(f.boundary.acceptSyntheticFrame(voiced(),16_000).outcome())
                    .isEqualTo(SyntraSyntheticAsyncVoiceBoundary.Outcome.STOP_LATCHED);
        }
    }

    @Test void interruptedOrHangingFakeSttTimesOutAndCanNeverEmitLateWake() throws Exception {
        var entered=new CountDownLatch(1);
        var release=new CountDownLatch(1);
        try(var f=new Fixture(60)) {
            f.begin();
            f.stt.onCall=()->{entered.countDown();ignoreInterruptUntilRelease(release);};
            f.stt.text="hi syntra";
            f.boundary.acceptSyntheticFrame(voiced(),16_000);
            f.boundary.acceptSyntheticFrame(voiced(),16_000);
            f.boundary.acceptSyntheticFrame(new byte[640],16_000);
            long started=System.nanoTime();
            var result=f.boundary.acceptSyntheticFrame(new byte[640],16_000);
            long elapsed=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started);
            assertThat(entered.getCount()).isZero();
            assertThat(result.outcome()).isEqualTo(SyntraSyntheticAsyncVoiceBoundary.Outcome.TIMED_OUT_LATCHED);
            assertThat(elapsed).isLessThan(1_500);
            assertThat(f.boundary.stopped()).isTrue();
            release.countDown();
            assertThat(f.boundary.acceptSyntheticFrame(voiced(),16_000).outcome())
                    .isEqualTo(SyntraSyntheticAsyncVoiceBoundary.Outcome.STOP_LATCHED);
        } finally { release.countDown(); }
    }

    @Test void stopNowDoesNotAcquireBlockedFakeSttMonitorAndSuppressesOutput() throws Exception {
        var entered=new CountDownLatch(1);
        var release=new CountDownLatch(1);
        ExecutorService caller=Executors.newSingleThreadExecutor();
        try(var f=new Fixture(900)) {
            f.begin();
            f.stt.onCall=()->{entered.countDown();ignoreInterruptUntilRelease(release);};
            f.boundary.acceptSyntheticFrame(voiced(),16_000);
            f.boundary.acceptSyntheticFrame(voiced(),16_000);
            f.boundary.acceptSyntheticFrame(new byte[640],16_000);
            Future<SyntraSyntheticAsyncVoiceBoundary.Result> pending=
                    caller.submit(()->f.boundary.acceptSyntheticFrame(new byte[640],16_000));
            assertThat(entered.await(2,TimeUnit.SECONDS)).isTrue();
            long start=System.nanoTime();
            assertThat(f.boundary.stopNow().outcome())
                    .isEqualTo(SyntraSyntheticAsyncVoiceBoundary.Outcome.STOP_LATCHED);
            assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)).isLessThan(300);
            release.countDown();
            assertThat(pending.get(2,TimeUnit.SECONDS).outcome())
                    .isEqualTo(SyntraSyntheticAsyncVoiceBoundary.Outcome.STOP_LATCHED);
            assertThat(f.boundary.stopped()).isTrue();
        } finally {
            release.countDown();
            caller.shutdownNow();
        }
    }

    @Test void concurrentSubmitIsRejectedWithNoQueueOfSpeechFrames() throws Exception {
        var entered=new CountDownLatch(1);
        var release=new CountDownLatch(1);
        ExecutorService caller=Executors.newSingleThreadExecutor();
        try(var f=new Fixture(900)) {
            f.begin();
            f.stt.onCall=()->{entered.countDown();ignoreInterruptUntilRelease(release);};
            f.boundary.acceptSyntheticFrame(voiced(),16_000);
            f.boundary.acceptSyntheticFrame(voiced(),16_000);
            f.boundary.acceptSyntheticFrame(new byte[640],16_000);
            Future<SyntraSyntheticAsyncVoiceBoundary.Result> first=
                    caller.submit(()->f.boundary.acceptSyntheticFrame(new byte[640],16_000));
            assertThat(entered.await(2,TimeUnit.SECONDS)).isTrue();
            assertThat(f.boundary.acceptSyntheticFrame(voiced(),16_000).outcome())
                    .isEqualTo(SyntraSyntheticAsyncVoiceBoundary.Outcome.BUSY_DENIED);
            assertThat(f.boundary.stopNow().outcome())
                    .isEqualTo(SyntraSyntheticAsyncVoiceBoundary.Outcome.STOP_LATCHED);
            release.countDown();
            assertThat(first.get(2,TimeUnit.SECONDS).outcome())
                    .isEqualTo(SyntraSyntheticAsyncVoiceBoundary.Outcome.STOP_LATCHED);
        } finally {
            release.countDown();
            caller.shutdownNow();
        }
    }

    @Test void closeWhileFakeSttIsBlockedDoesNotNeedWorkerLock() throws Exception {
        var entered=new CountDownLatch(1);
        var release=new CountDownLatch(1);
        ExecutorService caller=Executors.newSingleThreadExecutor();
        try(var f=new Fixture(900)) {
            f.begin();
            f.stt.onCall=()->{entered.countDown();ignoreInterruptUntilRelease(release);};
            f.boundary.acceptSyntheticFrame(voiced(),16_000);
            f.boundary.acceptSyntheticFrame(voiced(),16_000);
            f.boundary.acceptSyntheticFrame(new byte[640],16_000);
            Future<SyntraSyntheticAsyncVoiceBoundary.Result> pending=
                    caller.submit(()->f.boundary.acceptSyntheticFrame(new byte[640],16_000));
            assertThat(entered.await(2,TimeUnit.SECONDS)).isTrue();
            f.boundary.close();
            assertThat(f.boundary.stopped()).isTrue();
            release.countDown();
            assertThat(pending.get(2,TimeUnit.SECONDS).outcome())
                    .isEqualTo(SyntraSyntheticAsyncVoiceBoundary.Outcome.STOP_LATCHED);
        } finally {
            release.countDown();
            caller.shutdownNow();
        }
    }

    @Test void adapterFailureMustFailClosedAndNeverAutoRestart() {
        try(var f=new Fixture(500)) {
            f.begin();
            f.stt.onCall=()->{throw new IllegalStateException("fixture STT failed");};
            assertThat(f.say("hi syntra").outcome())
                    .isEqualTo(SyntraSyntheticAsyncVoiceBoundary.Outcome.STOP_LATCHED);
            assertThat(f.boundary.beginSyntheticSession(true).outcome())
                    .isEqualTo(SyntraSyntheticAsyncVoiceBoundary.Outcome.STOP_LATCHED);
        }
    }

    @Test void invalidWaitBudgetAndNonSyntheticAdapterIdsRejected() {
        var stt=new FakeStt();
        var tts=new FakeTts();
        assertThatThrownBy(()->new SyntraSyntheticAsyncVoiceBoundary(stt,tts,
                ()->true,()->false,Clock.systemUTC(),10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new SyntraSyntheticAsyncVoiceBoundary(stt,tts,
                ()->true,()->false,Clock.systemUTC(),1001))
                .isInstanceOf(IllegalArgumentException.class);
        SpeechToTextAdapter real=new SpeechToTextAdapter() {
            public String id(){return "real-microphone";}
            public boolean available(){return true;}
            public String transcribe(byte[] pcm,int rate){return "hi syntra";}
        };
        assertThatThrownBy(()->new SyntraSyntheticAsyncVoiceBoundary(real,tts,
                ()->true,()->false,Clock.systemUTC(),100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("LIVE_SPEECH_ENGINE_NOT_AUTHORIZED");
    }

    @Test void expiredSessionAndBudgetOverrunAreFailClosed() {
        try(var f=new Fixture(500)) {
            f.begin();
            f.clock.advanceSeconds(31);
            assertThat(f.boundary.acceptSyntheticFrame(voiced(),16_000).outcome())
                    .isEqualTo(SyntraSyntheticAsyncVoiceBoundary.Outcome.STOP_LATCHED);
        }
        try(var f=new Fixture(500)) {
            f.begin();
            for(int i=0;i<10;i++)
                assertThat(f.boundary.acceptSyntheticFrame(longVoiced(),16_000).outcome())
                        .isEqualTo(SyntraSyntheticAsyncVoiceBoundary.Outcome.NO_REPLY);
            assertThat(f.boundary.acceptSyntheticFrame(longVoiced(),16_000).outcome())
                    .isEqualTo(SyntraSyntheticAsyncVoiceBoundary.Outcome.STOP_LATCHED);
            assertThat(f.boundary.stopped()).isTrue();
        }
    }

    @Test void repeatedStopAndCloseRemainIrreversibleAndSafe() {
        var f=new Fixture(500);
        f.begin();
        assertThat(f.boundary.stopNow().outcome())
                .isEqualTo(SyntraSyntheticAsyncVoiceBoundary.Outcome.STOP_LATCHED);
        f.boundary.close();
        f.boundary.close();
        assertThat(f.boundary.stopped()).isTrue();
        assertThat(f.boundary.beginSyntheticSession(true).outcome())
                .isEqualTo(SyntraSyntheticAsyncVoiceBoundary.Outcome.STOP_LATCHED);
        assertThat(f.tts.calls).hasValue(0);
    }

    @Test void untrustedCallbackFailureOfStopOrPermissionCannotBecomeApproval() {
        var stt=new FakeStt();
        var tts=new FakeTts();
        try(var f=new SyntraSyntheticAsyncVoiceBoundary(stt,tts,
                ()->{throw new IllegalStateException("permission source gone");},
                ()->false, Clock.systemUTC(),100)) {
            assertThat(f.beginSyntheticSession(true).outcome())
                    .isEqualTo(SyntraSyntheticAsyncVoiceBoundary.Outcome.DENIED);
        }
        try(var f=new SyntraSyntheticAsyncVoiceBoundary(stt,tts,
                ()->true, ()->{throw new IllegalStateException("STOP source gone");},
                Clock.systemUTC(),100)) {
            assertThat(f.beginSyntheticSession(true).outcome())
                    .isEqualTo(SyntraSyntheticAsyncVoiceBoundary.Outcome.STOP_LATCHED);
        }
    }
}

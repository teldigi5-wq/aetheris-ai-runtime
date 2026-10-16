package io.aetheris.orchestrator.voice;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 46.25 — ensure independently compiled test worker and source-side
 * invocation contract do not drift when source ownership eventually moves.
 *
 * Tests are intentionally in src/test/java. No microphone, model, desktop
 * action, network request or owner-PC pilot can be activated by this class.
 */
class SyntheticWorkerProtocolContractTest {

    @Test
    void standaloneWorkerClassNameMatchesFixedFailClosedInvocationTarget() {
        assertThat(SyntraSyntheticHostLimits.FIXED_PCM_WORKER_CLASS)
                .isEqualTo(SyntraSyntheticPcmWorkerMain.class.getName());
    }

    @Test
    void frozenMagicAndProtocolPrefixAgreeWithCanonicalTestWorker() {
        assertThat(SyntraSyntheticProcessSpeechBoundary.FIXED_PCM_MAGIC)
                .isEqualTo(SyntraSyntheticPcmWorkerMain.MAGIC);
        assertThat(SyntraSyntheticProcessSpeechBoundary.FIXED_PCM_PREFIX)
                .isEqualTo(SyntraSyntheticPcmWorkerMain.PREFIX);
        assertThat(SyntraSyntheticPcmWorkerMain.SAMPLE_RATE)
                .isEqualTo(SyntraSyntheticProcessSpeechBoundary.SAMPLE_RATE_HZ);
    }

    @Test
    void portableCommandUsesOnlyTheReviewedWorkerWithFixedMode() {
        List<String> actual = SyntraSyntheticHostLimits.command(
                SyntraSyntheticHostLimits.Profile.PORTABLE_SYNTHETIC_ONLY,
                "java-fixed", "ci-lab-classes", "NORMAL");
        assertThat(actual).containsExactly(
                "java-fixed", "-Xms16m", "-Xmx64m",
                "-cp", "ci-lab-classes",
                SyntraSyntheticPcmWorkerMain.class.getName(), "NORMAL");
    }

    @Test
    void testOnlyWorkerTypeHasNoSpringRegistration() {
        // The production JAR excludes this main class; no Spring component
        // or other implicit activation annotation is permitted on the fixture.
        assertThat(SyntraSyntheticPcmWorkerMain.class.getAnnotations()).isEmpty();
        assertThat(SyntraSyntheticJobProbeMain.class.getAnnotations()).isEmpty();
    }
}

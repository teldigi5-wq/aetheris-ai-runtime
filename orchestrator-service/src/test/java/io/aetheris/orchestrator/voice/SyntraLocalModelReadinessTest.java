package io.aetheris.orchestrator.voice;

import org.junit.jupiter.api.Test;
import java.util.OptionalLong;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SyntraLocalModelReadinessTest {
    private static final String HASH="a".repeat(64);

    @Test void knownHostRamLargerThanDiskStillNeverClaimsFitOrGpu() {
        var memory=new SyntraLocalModelReadiness.HostMemory(
                OptionalLong.of(16L<<30),OptionalLong.of(10L<<30));
        var assessment=SyntraLocalModelReadiness.evaluate("qwen3:4b",3L<<30,HASH,memory);
        assertThat(assessment.advisory())
                .isEqualTo("MODEL_DISK_SIZE_WITHIN_FREE_HOST_RAM_NOT_A_LOAD_CERTIFICATION");
        assertThat(assessment.loadCertified()).isFalse();
        assertThat(assessment.gpuCertified()).isFalse();
        assertThat(assessment.sha256Prefix()).isEqualTo("a".repeat(12));
    }

    @Test void largerModelFileThanHostRamTriggersAdvisoryNotHardwareInference() {
        var memory=new SyntraLocalModelReadiness.HostMemory(
                OptionalLong.of(8L<<30),OptionalLong.of(2L<<30));
        var assessment=SyntraLocalModelReadiness.evaluate("qwen3:4b",5L<<30,HASH,memory);
        assertThat(assessment.advisory())
                .isEqualTo("MODEL_DISK_SIZE_GREATER_THAN_FREE_HOST_RAM_NOT_A_LOAD_ESTIMATE");
        assertThat(assessment.loadCertified()).isFalse();
        assertThat(assessment.gpuCertified()).isFalse();
    }

    @Test void unknownHardwareNeverProducesAFalseCpuOrGpuCertification() {
        var unknown=new SyntraLocalModelReadiness.HostMemory(
                OptionalLong.empty(),OptionalLong.empty());
        var assessment=SyntraLocalModelReadiness.evaluate("qwen3:4b",1_000_000_000L,HASH,unknown);
        assertThat(assessment.advisory()).isEqualTo("HOST_MEMORY_UNKNOWN_NO_LOAD_CERTIFICATION");
        assertThat(assessment.hostMemory().freeBytes()).isEmpty();
        assertThat(assessment.gpuCertified()).isFalse();
    }

    @Test void rejectsImpossibleMemoryAndForgedModelMetadata() {
        assertThatThrownBy(()->new SyntraLocalModelReadiness.HostMemory(
                OptionalLong.of(1024),OptionalLong.of(2048)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new SyntraLocalModelReadiness.HostMemory(
                OptionalLong.of(-1),OptionalLong.empty()))
                .isInstanceOf(IllegalArgumentException.class);
        var unknown=new SyntraLocalModelReadiness.HostMemory(
                OptionalLong.empty(),OptionalLong.empty());
        assertThatThrownBy(()->SyntraLocalModelReadiness.evaluate("bad/name",123,HASH,unknown))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->SyntraLocalModelReadiness.evaluate("qwen3:4b",0,HASH,unknown))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->SyntraLocalModelReadiness.evaluate("qwen3:4b",1024,"unknown",unknown))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void localHardwareObserverNeverClaimsCertainty() {
        var sample=SyntraLocalModelReadiness.observe();
        assertThat(sample.totalBytes().isPresent()).isEqualTo(sample.freeBytes().isPresent());
        var review=SyntraLocalModelReadiness.evaluate("qwen3:4b",1_000_000,HASH,sample);
        assertThat(review.gpuCertified()).isFalse();
        assertThat(review.loadCertified()).isFalse();
    }
}

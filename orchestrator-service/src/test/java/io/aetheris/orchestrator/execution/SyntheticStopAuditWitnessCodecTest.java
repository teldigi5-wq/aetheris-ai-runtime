package io.aetheris.orchestrator.execution;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pure fixed-protocol safety tests, no production keys or database. */
class SyntheticStopAuditWitnessCodecTest {
    private static SyntheticStopAuditWitnessLab.Witness witness() {
        return new SyntheticStopAuditWitnessLab.Witness("ciSyntheticWorker",
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                2L, 1L, 1750000000L, "a".repeat(128));
    }

    @Test
    void canonicalCheckpointRoundTripIsExact() {
        var sample=witness();
        String text=SyntheticStopAuditWitnessLab.canonicalText(sample);
        assertThat(SyntheticStopAuditWitnessLab.parseCanonicalText(text)).isEqualTo(sample);
        assertThat(text).startsWith("AETHERIS45STOPAUDITV1|");
        assertThat(text).endsWith("a".repeat(128) + "\n");
    }

    @Test
    void malformedOrRewrittenVersionsCannotPassAuditDecoder() {
        String data=SyntheticStopAuditWitnessLab.canonicalText(witness());
        for (String bad : new String[]{
                data.replace("AETHERIS45STOPAUDITV1", "AETHERIS45STOPAUDITV0"),
                data.replace("|2|1|", "|1|1|"),
                data.substring(0, data.length()-1),
                data + "appended",
                data.replace("|1750000000|", "|01750000000|")
        }) {
            assertThatThrownBy(() -> SyntheticStopAuditWitnessLab.parseCanonicalText(bad))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void malformedOrNegativeWitnessEpochsAndSignatureRefusedAtConstruction() {
        assertThatThrownBy(() -> new SyntheticStopAuditWitnessLab.Witness(
                "ciSyntheticWorker", witness().jobId(), 1, 1, 1750000000L,
                "a".repeat(128))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SyntheticStopAuditWitnessLab.Witness(
                "../foreign", witness().jobId(), 2, 1, 1750000000L,
                "a".repeat(128))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SyntheticStopAuditWitnessLab.Witness(
                "ciSyntheticWorker", witness().jobId(), 2, 1, 1750000000L,
                "not-a-signature")).isInstanceOf(IllegalArgumentException.class);
    }
}

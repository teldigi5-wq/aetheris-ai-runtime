package io.aetheris.orchestrator.security;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class OwnerBoundResourcePolicyTest {
    private static final Long OWNER_A = 101L;
    private static final Long OWNER_B = 202L;
    private static final UUID PROJECT_A = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID PROJECT_B = UUID.fromString("44444444-4444-4444-8444-444444444444");

    @Test
    void twoDistinctOwnersCannotSeeEachOthersRecordsEvenWithinSameProject() {
        assertThat(OwnerBoundResourcePolicy.evaluate(OWNER_A,PROJECT_A,OWNER_B,PROJECT_A))
                .isEqualTo(OwnerBoundResourcePolicy.Result.OWNER_MISMATCH);
        assertThat(OwnerBoundResourcePolicy.evaluate(OWNER_B,PROJECT_A,OWNER_A,PROJECT_A))
                .isEqualTo(OwnerBoundResourcePolicy.Result.OWNER_MISMATCH);
        assertThat(OwnerBoundResourcePolicy.permitted(OWNER_A,PROJECT_A,OWNER_B,PROJECT_A))
                .isFalse();
    }

    @Test
    void projectScopedRecordsRequireSameVerifiedProjectAsWellAsOwner() {
        assertThat(OwnerBoundResourcePolicy.evaluate(OWNER_A,PROJECT_A,OWNER_A,PROJECT_A))
                .isEqualTo(OwnerBoundResourcePolicy.Result.MATCH);
        assertThat(OwnerBoundResourcePolicy.evaluate(OWNER_A,PROJECT_A,OWNER_A,PROJECT_B))
                .isEqualTo(OwnerBoundResourcePolicy.Result.PROJECT_MISMATCH);
        assertThat(OwnerBoundResourcePolicy.evaluate(OWNER_A,PROJECT_A,OWNER_A,null))
                .isEqualTo(OwnerBoundResourcePolicy.Result.PROJECT_MISMATCH);
    }

    @Test
    void existingUnownedRowsAreNeverAssignedToTheFirstReader() {
        assertThat(OwnerBoundResourcePolicy.evaluate(null,PROJECT_A,OWNER_A,PROJECT_A))
                .isEqualTo(OwnerBoundResourcePolicy.Result.OWNERLESS_LEGACY_ROW);
        assertThat(OwnerBoundResourcePolicy.evaluate(null,null,OWNER_B,null))
                .isEqualTo(OwnerBoundResourcePolicy.Result.OWNERLESS_LEGACY_ROW);
        assertThat(OwnerBoundResourcePolicy.evaluate(OWNER_A,PROJECT_A,null,PROJECT_A))
                .isEqualTo(OwnerBoundResourcePolicy.Result.UNVERIFIED_CALLER);
    }

    @Test
    void ownerGlobalRecordsCanBeReadByTheCorrectOwnerWithoutClaimingAProject() {
        assertThat(OwnerBoundResourcePolicy.permitted(OWNER_A,null,OWNER_A,null)).isTrue();
        assertThat(OwnerBoundResourcePolicy.permitted(OWNER_A,null,OWNER_A,PROJECT_B)).isTrue();
        assertThat(OwnerBoundResourcePolicy.permitted(OWNER_A,null,OWNER_B,null)).isFalse();
    }
}

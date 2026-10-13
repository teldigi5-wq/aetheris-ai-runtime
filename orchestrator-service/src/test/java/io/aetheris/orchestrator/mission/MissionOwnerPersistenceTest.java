package io.aetheris.orchestrator.mission;

import io.aetheris.orchestrator.security.OwnerBoundResourcePolicy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class MissionOwnerPersistenceTest {
    @Autowired MissionSessionRepository repository;

    @Test
    void missionOwnerAndProjectFieldsPersistAndCannotLeakAcrossAccounts() {
        Long ownerA=101L, ownerB=202L;
        UUID project=UUID.randomUUID();
        MissionSessionEntity a=repository.save(new MissionSessionEntity(UUID.randomUUID(),
                "Owner A", "isolated mission", ownerA,project));
        MissionSessionEntity b=repository.save(new MissionSessionEntity(UUID.randomUUID(),
                "Owner B", "isolated mission", ownerB,project));
        MissionSessionEntity old=repository.save(new MissionSessionEntity(UUID.randomUUID(),
                "Legacy", "not bound to a principal"));
        repository.flush();

        assertThat(repository.findById(a.getId()).orElseThrow().getOwnerUserId()).isEqualTo(ownerA);
        assertThat(repository.findById(a.getId()).orElseThrow().getOwnerProjectId()).isEqualTo(project);
        assertThat(repository.findTop100ByOwnerUserIdAndOwnerProjectIdOrderByUpdatedAtDesc(ownerA,project))
                .extracting(MissionSessionEntity::getId).contains(a.getId())
                .doesNotContain(b.getId(),old.getId());
        assertThat(repository.findTop100ByOwnerUserIdOrderByUpdatedAtDesc(ownerB))
                .extracting(MissionSessionEntity::getId).contains(b.getId())
                .doesNotContain(a.getId(),old.getId());
        assertThat(OwnerBoundResourcePolicy.permitted(old.getOwnerUserId(),old.getOwnerProjectId(),ownerA,project))
                .isFalse();
    }

    @Test
    void cannotClaimAProjectWithoutAnAuthenticatedMissionOwner() {
        assertThatThrownBy(()->new MissionSessionEntity(UUID.randomUUID(),"invalid",
                "unverified",null,UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Verified mission owner");
    }
}

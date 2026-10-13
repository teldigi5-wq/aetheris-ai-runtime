package io.aetheris.orchestrator.task;

import io.aetheris.orchestrator.policy.OperationMode;
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
class TaskOwnerPersistenceTest {

    @Autowired TaskRepository repository;

    @Test
    void ownerBindingIsDurableAndScopedQueriesExcludeOtherOwnerAndLegacyRows() {
        UUID ownerA=UUID.randomUUID(), ownerB=UUID.randomUUID();
        UUID projectA=UUID.randomUUID(), projectB=UUID.randomUUID();
        TaskEntity a=repository.save(new TaskEntity(UUID.randomUUID(),"owner A","sample",
                OperationMode.PRIVATE,ownerA,projectA));
        TaskEntity b=repository.save(new TaskEntity(UUID.randomUUID(),"owner B","sample",
                OperationMode.PRIVATE,ownerB,projectA));
        TaskEntity otherProject=repository.save(new TaskEntity(UUID.randomUUID(),"other project",
                "sample",OperationMode.BALANCED,ownerA,projectB));
        TaskEntity legacy=repository.save(new TaskEntity(UUID.randomUUID(),"legacy internal",
                "sample",OperationMode.BALANCED));
        repository.flush();

        assertThat(repository.findById(a.getId()).orElseThrow().getOwnerUserId()).isEqualTo(ownerA);
        assertThat(repository.findById(a.getId()).orElseThrow().getOwnerProjectId()).isEqualTo(projectA);
        assertThat(repository.findTop50ByOwnerUserIdAndOwnerProjectIdOrderByUpdatedAtDesc(ownerA,projectA))
                .extracting(TaskEntity::getId).contains(a.getId()).doesNotContain(b.getId(),otherProject.getId(),legacy.getId());
        assertThat(repository.findTop50ByOwnerUserIdOrderByUpdatedAtDesc(ownerA))
                .extracting(TaskEntity::getId).contains(a.getId(),otherProject.getId())
                .doesNotContain(b.getId(),legacy.getId());
        assertThat(repository.findById(legacy.getId()).orElseThrow().getOwnerUserId()).isNull();
        assertThat(OwnerBoundResourcePolicy.permitted(legacy.getOwnerUserId(),legacy.getOwnerProjectId(),ownerA,projectA))
                .isFalse();
    }

    @Test
    void constructorRejectsProjectOrOwnerBindingWithoutVerifiedUserId() {
        assertThatThrownBy(()->new TaskEntity(UUID.randomUUID(),"bad","test",
                OperationMode.BALANCED,null,UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Verified task owner");
    }
}

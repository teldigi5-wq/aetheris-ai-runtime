package io.aetheris.orchestrator.task;

import io.aetheris.orchestrator.policy.OperationMode;
import jakarta.persistence.Column;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Review-only real H2/JPA persistence proof for scoped owner selectors.
 * It does not generate an authenticated principal or authorize HTTP routes.
 * SQL fixture updates stand in for an independently signed creation path
 * that is not yet implemented. Every test is rolled back.
 */
@SpringBootTest
@Transactional
class TaskOwnerScopedRepositoryIntegrationTest {
    private static final long ALICE = 92018081L;
    private static final long BOB = 92018082L;

    @Autowired TaskRepository tasks;
    @Autowired JdbcTemplate sql;
    @Autowired EntityManager entityManager;

    private TaskEntity createUnbound(String title) {
        return tasks.save(new TaskEntity(UUID.randomUUID(), title,
                "Synthetic test-only command; never execute", OperationMode.PRIVATE));
    }

    private void simulateTrustedDatabaseProvisioning(TaskEntity task, long uid,
                                                      String tenant, String project) {
        // Not a product API: only the disposable test database is provisioned.
        int changed = sql.update("""
                update aetheris_tasks
                   set owner_identity_uid = ?, owner_tenant_key = ?,
                       owner_project_key = ?, owner_verified_at = ?
                 where id = ?
                """, uid, tenant, project,
                Timestamp.from(Instant.parse("2026-11-08T00:00:00Z")), task.getId());
        assertThat(changed).isEqualTo(1);
    }

    @Test
    void scopedReadRejectsOtherUidTenantProjectAndLegacyOwnerlessRecord() {
        TaskEntity aliceAlpha = createUnbound("Alice alpha");
        TaskEntity aliceBeta = createUnbound("Alice beta");
        TaskEntity bobAlpha = createUnbound("Bob alpha");
        TaskEntity legacy = createUnbound("Legacy ownerless");
        tasks.flush();

        simulateTrustedDatabaseProvisioning(aliceAlpha, ALICE, "tenant-alpha", "project-red");
        simulateTrustedDatabaseProvisioning(aliceBeta, ALICE, "tenant-beta", "project-red");
        simulateTrustedDatabaseProvisioning(bobAlpha, BOB, "tenant-alpha", "project-red");
        entityManager.clear();

        assertThat(tasks.findScopedBoundTask(aliceAlpha.getId(), ALICE,
                "tenant-alpha", "project-red")).isPresent();
        assertThat(tasks.findScopedBoundTask(aliceAlpha.getId(), BOB,
                "tenant-alpha", "project-red")).isEmpty();
        assertThat(tasks.findScopedBoundTask(aliceAlpha.getId(), ALICE,
                "tenant-beta", "project-red")).isEmpty();
        assertThat(tasks.findScopedBoundTask(aliceAlpha.getId(), ALICE,
                "tenant-alpha", "project-blue")).isEmpty();
        assertThat(tasks.findScopedBoundTask(legacy.getId(), ALICE,
                "tenant-alpha", "project-red")).isEmpty();
        assertThat(tasks.findScopedBoundTask(bobAlpha.getId(), ALICE,
                "tenant-alpha", "project-red")).isEmpty();
        assertThat(tasks.findScopedBoundTask(aliceBeta.getId(), ALICE,
                "tenant-beta", "project-red")).isPresent();
    }

    @Test
    void recentListIsBoundToExactTripleNotGlobalRecentMethod() {
        TaskEntity first = createUnbound("Alice alpha");
        TaskEntity second = createUnbound("Alice different project");
        TaskEntity third = createUnbound("Bob alpha");
        createUnbound("Unbound quarantine");
        tasks.flush();
        simulateTrustedDatabaseProvisioning(first, ALICE, "tenant-alpha", "project-red");
        simulateTrustedDatabaseProvisioning(second, ALICE, "tenant-alpha", "project-blue");
        simulateTrustedDatabaseProvisioning(third, BOB, "tenant-alpha", "project-red");
        entityManager.clear();

        List<TaskEntity> allowed = tasks
            .findTop50ByOwnerIdentityUidAndOwnerTenantKeyAndOwnerProjectKeyAndOwnerVerifiedAtIsNotNullOrderByUpdatedAtDesc(
                ALICE, "tenant-alpha", "project-red");
        assertThat(allowed).extracting(TaskEntity::getId).containsExactly(first.getId());
        assertThat(tasks
            .findTop50ByOwnerIdentityUidAndOwnerTenantKeyAndOwnerProjectKeyAndOwnerVerifiedAtIsNotNullOrderByUpdatedAtDesc(
                ALICE, "tenant-beta", "project-red")).isEmpty();
        assertThat(tasks
            .findTop50ByOwnerIdentityUidAndOwnerTenantKeyAndOwnerProjectKeyAndOwnerVerifiedAtIsNotNullOrderByUpdatedAtDesc(
                BOB, "tenant-alpha", "project-red")).extracting(TaskEntity::getId)
            .containsExactly(third.getId());
    }

    @Test
    void ownerFieldsAreDatabaseReadOnlyAndCannotBeSetViaEntityMutators() {
        for (String fieldName : List.of("ownerIdentityUid", "ownerTenantKey",
                "ownerProjectKey", "ownerVerifiedAt")) {
            Field field;
            try { field = TaskEntity.class.getDeclaredField(fieldName); }
            catch (NoSuchFieldException error) { throw new AssertionError(error); }
            Column column = field.getAnnotation(Column.class);
            assertThat(column).isNotNull();
            assertThat(column.insertable()).isFalse();
            assertThat(column.updatable()).isFalse();
            assertThatThrownBy(() -> TaskEntity.class.getMethod(
                "set" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1),
                field.getType())).isInstanceOf(NoSuchMethodException.class);
        }
    }
}

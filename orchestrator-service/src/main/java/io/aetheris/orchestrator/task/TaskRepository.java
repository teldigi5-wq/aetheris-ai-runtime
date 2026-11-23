package io.aetheris.orchestrator.task;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<TaskEntity, UUID> {
    List<TaskEntity> findTop50ByOrderByUpdatedAtDesc();
    List<TaskEntity> findByStateIn(Collection<TaskState> states);

    /**
     * Data-level exact owner+tenant+project filter, NOT an authorization
     * decision. Caller must separately verify signed principal, live tenant
     * and project membership, session freshness/revocation and task access.
     * Do not expose this method through ownerless HTTP routes.
     */
    @Query("""
        select t from TaskEntity t
         where t.id = :id and t.ownerIdentityUid = :uid
           and t.ownerTenantKey = :tenant and t.ownerProjectKey = :project
           and t.ownerVerifiedAt is not null
        """)
    Optional<TaskEntity> findScopedBoundTask(@Param("id") UUID id,
                                            @Param("uid") long uid,
                                            @Param("tenant") String tenant,
                                            @Param("project") String project);

    /** Still data filtering only; not an authenticated list endpoint. */
    List<TaskEntity> findTop50ByOwnerIdentityUidAndOwnerTenantKeyAndOwnerProjectKeyAndOwnerVerifiedAtIsNotNullOrderByUpdatedAtDesc(
            long uid, String tenant, String project);
}

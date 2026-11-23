package io.aetheris.orchestrator.mission;

import io.aetheris.orchestrator.security.RuntimeE6SCanonicalMissionApplication;
import io.aetheris.orchestrator.security.RuntimePinnedPrincipalVerifier;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * S0-E6T nonregistered service-layer PREVIEW for canonical Mission data.
 *
 * This is deliberately NOT an @Service replacement for the original
 * MissionService; it is an explicit candidate for independent staging.
 * The existing service's create(), recent(), attach(), taskEvents(),
 * messages() and status() MUST NOT be exposed through user APIs without
 * separately verified owner/tenant/resource binding.
 *
 * All calls use the signed JTI SQL boundary and CANONICAL decision; no
 * MissionSessionEntity, MissionSessionRepository or taskIdsCsv fallback.
 * Each method is independently authorized within its SQL transaction; a
 * consumer needing a single coherent multi-method snapshot requires a
 * future consolidated SQL transaction, NOT concatenating these methods.
 */
public final class RuntimeE6TCanonicalMissionService {
    private final RuntimeE6TCanonicalMissionRepository repo;
    private final RuntimeE6SCanonicalMissionApplication canonical;

    public RuntimeE6TCanonicalMissionService(
            RuntimeE6TCanonicalMissionRepository repo,
            RuntimeE6SCanonicalMissionApplication canonical) {
        this.repo=Objects.requireNonNull(repo,"canonical Mission SQL repository");
        this.canonical=Objects.requireNonNull(canonical,"canonical link and event adapter");
    }

    public Optional<RuntimeE6TCanonicalMissionRepository.MissionProjection>
    describe(RuntimePinnedPrincipalVerifier.VerifiedPrincipal verified,
             UUID mission,String tenant,String project) throws SQLException {
        return repo.findAuthorized(verified,mission,tenant,project);
    }

    public boolean attach(RuntimePinnedPrincipalVerifier.VerifiedPrincipal verified,
                          UUID mission,UUID task,String tenant,String project)
            throws SQLException {
        return canonical.attach(verified,mission,task,tenant,project);
    }

    public List<UUID> taskIds(
            RuntimePinnedPrincipalVerifier.VerifiedPrincipal verified,
            UUID mission,String tenant,String project,int limit)
            throws SQLException {
        return canonical.taskIds(verified,mission,tenant,project,limit);
    }

    public List<RuntimeE6SCanonicalMissionApplication.EventHeader> eventHeaders(
            RuntimePinnedPrincipalVerifier.VerifiedPrincipal verified,
            UUID mission,String tenant,String project,int limit)
            throws SQLException {
        return canonical.eventHeaders(verified,mission,tenant,project,limit);
    }
}

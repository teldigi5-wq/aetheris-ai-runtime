package io.aetheris.orchestrator.syntracore;

import io.aetheris.orchestrator.stage32.EmergencyMode;
import io.aetheris.orchestrator.stage33.AdapterActivationAuthorityBridge;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class SyntraAdapterLifecycleGovernanceService {
    private final SyntraAdapterPromotionGovernanceService promotionGovernance;
    private final AdapterActivationAuthorityBridge authority;

    public SyntraAdapterLifecycleGovernanceService() {
        this(new SyntraAdapterPromotionGovernanceService(), new AdapterActivationAuthorityBridge());
    }

    public SyntraAdapterLifecycleGovernanceService(
            SyntraAdapterPromotionGovernanceService promotionGovernance,
            AdapterActivationAuthorityBridge authority) {
        this.promotionGovernance = Objects.requireNonNull(promotionGovernance, "promotionGovernance");
        this.authority = Objects.requireNonNull(authority, "authority");
    }

    public AdapterLifecycleResult activate(
            AdapterLifecycleRequest request,
            LocalAdapterActivationPort port,
            AdapterActivationAuthorityBridge.ScopedOwnerApproval ownerApproval,
            Instant now,
            EmergencyMode emergencyMode) {
        validateContinuity(request);
        Objects.requireNonNull(port, "port");
        Objects.requireNonNull(now, "now");

        AdapterArtifactIdentity identity = request.artifactIdentity();
        AdapterLifecycleResult previewFailure = preview(
                port,
                identity,
                false,
                "ADAPTER_ALREADY_ACTIVE");
        if (previewFailure != null) {
            return previewFailure;
        }

        AdapterActivationAuthorityBridge.AuthorizationTicket ticket = authority.authorize(
                new AdapterActivationAuthorityBridge.AuthorityRequest(
                        request.activationActionId(),
                        request.actorId(),
                        AdapterActivationAuthorityBridge.Action.ACTIVATE_ADAPTER,
                        identity.promotedAdapterAddress(),
                        true),
                ownerApproval,
                now,
                emergencyMode);
        if (!ticket.executionEligible()) {
            return blocked(identity, ticket.reasons(), false);
        }

        AdapterArtifactObservation post;
        try {
            post = port.activate(identity);
        } catch (RuntimeException failure) {
            AdapterActivationAuthorityBridge.ExecutionTruth truth = authority.finalizeExecution(
                    ticket,
                    new AdapterActivationAuthorityBridge.Verification(
                            true,
                            false,
                            false,
                            false,
                            "",
                            Set.of()));
            return result(
                    AdapterLifecycleStatus.ACTIVATION_UNVERIFIED,
                    identity,
                    true,
                    false,
                    false,
                    true,
                    ticket.oneTimeApprovalConsumed(),
                    merge(ticket.reasons(), truth.reason(), "LOCAL_ADAPTER_PORT_EXCEPTION:" + failure.getClass().getSimpleName()));
        }

        AdapterActivationAuthorityBridge.ExecutionTruth truth = authority.finalizeExecution(
                ticket,
                verification(identity, post, true));
        return mapActivation(identity, post, ticket, truth);
    }

    public AdapterLifecycleResult rollback(
            AdapterLifecycleRequest request,
            LocalAdapterActivationPort port,
            AdapterActivationAuthorityBridge.ScopedOwnerApproval ownerApproval,
            Instant now,
            EmergencyMode emergencyMode) {
        validateContinuity(request);
        Objects.requireNonNull(port, "port");
        Objects.requireNonNull(now, "now");

        AdapterArtifactIdentity identity = request.artifactIdentity();
        AdapterLifecycleResult previewFailure = preview(
                port,
                identity,
                true,
                "ADAPTER_NOT_ACTIVE");
        if (previewFailure != null) {
            return previewFailure;
        }

        AdapterActivationAuthorityBridge.AuthorizationTicket ticket = authority.authorize(
                new AdapterActivationAuthorityBridge.AuthorityRequest(
                        request.rollbackActionId(),
                        request.actorId(),
                        AdapterActivationAuthorityBridge.Action.DETACH_ADAPTER,
                        identity.promotedAdapterAddress(),
                        true),
                ownerApproval,
                now,
                emergencyMode);
        if (!ticket.executionEligible()) {
            return blocked(identity, ticket.reasons(), true);
        }

        AdapterArtifactObservation post;
        try {
            post = port.detach(identity);
        } catch (RuntimeException failure) {
            AdapterActivationAuthorityBridge.ExecutionTruth truth = authority.finalizeExecution(
                    ticket,
                    new AdapterActivationAuthorityBridge.Verification(
                            true,
                            false,
                            false,
                            false,
                            "",
                            Set.of()));
            return result(
                    AdapterLifecycleStatus.ROLLBACK_UNVERIFIED,
                    identity,
                    true,
                    false,
                    true,
                    true,
                    ticket.oneTimeApprovalConsumed(),
                    merge(ticket.reasons(), truth.reason(), "LOCAL_ADAPTER_PORT_EXCEPTION:" + failure.getClass().getSimpleName()));
        }

        AdapterActivationAuthorityBridge.ExecutionTruth truth = authority.finalizeExecution(
                ticket,
                verification(identity, post, false));
        return mapRollback(identity, post, ticket, truth);
    }

    private AdapterLifecycleResult preview(
            LocalAdapterActivationPort port,
            AdapterArtifactIdentity expected,
            boolean mustBeActive,
            String wrongStateReason) {
        AdapterArtifactObservation observation;
        try {
            observation = port.inspect(expected);
        } catch (RuntimeException failure) {
            return blocked(expected, List.of("ARTIFACT_PREVIEW_UNAVAILABLE:" + failure.getClass().getSimpleName()), false);
        }
        if (observation == null) {
            return blocked(expected, List.of("ARTIFACT_PREVIEW_MISSING"), false);
        }
        if (!expected.equals(observation.identity())) {
            return blocked(expected, List.of("ARTIFACT_IDENTITY_MISMATCH"), observation.active());
        }
        if (!observation.exists()) {
            return blocked(expected, List.of("APPROVED_ARTIFACT_NOT_FOUND"), false);
        }
        if (observation.active() != mustBeActive) {
            return blocked(expected, List.of(wrongStateReason), observation.active());
        }
        return null;
    }

    private void validateContinuity(AdapterLifecycleRequest request) {
        Objects.requireNonNull(request, "request");
        AdapterPromotionDecision recomputed = promotionGovernance.decide(
                request.plan(),
                request.evidence(),
                request.promotionPolicy(),
                true);
        if (!recomputed.equals(request.promotionDecision())) {
            throw new IllegalArgumentException("promotion decision does not match revalidated Slice 8 evidence and policy");
        }
        AdapterPromotionDecision promotion = request.promotionDecision();
        if (promotion.status() != AdapterPromotionStatus.APPROVED_FOR_LOCAL_USE
                || !promotion.ownerApproved()
                || !promotion.routingEligible()
                || !promotion.localOnly()
                || promotion.baseWeightsMutable()
                || !promotion.blockers().isEmpty()
                || !"DETACH_ADAPTER".equals(promotion.rollbackStrategy())) {
            throw new IllegalArgumentException("adapter is not eligible for local activation");
        }

        AdapterArtifactIdentity artifact = request.artifactIdentity();
        String promotedAddress = promotion.promotedAdapterAddress().orElseThrow();
        if (!request.plan().experimentId().equals(artifact.experimentId())
                || !request.plan().candidateAdapterAddress().equals(artifact.candidateAdapterAddress())
                || !promotedAddress.equals(artifact.promotedAdapterAddress())
                || !request.plan().baseModelId().equals(artifact.baseModelId())
                || !request.plan().datasetHash().equals(artifact.datasetHash())) {
            throw new IllegalArgumentException("adapter artifact identity drifted from the approved evaluation identity");
        }
        if (!artifact.localOnly() || artifact.baseWeightsMutable()) {
            throw new IllegalArgumentException("adapter artifact violates local immutable-base safety invariants");
        }
    }

    private AdapterActivationAuthorityBridge.Verification verification(
            AdapterArtifactIdentity expected,
            AdapterArtifactObservation observed,
            boolean expectedActive) {
        if (observed == null) {
            return new AdapterActivationAuthorityBridge.Verification(
                    true,
                    false,
                    false,
                    false,
                    "",
                    Set.of());
        }
        boolean exactIdentity = expected.equals(observed.identity());
        if (!exactIdentity) {
            return new AdapterActivationAuthorityBridge.Verification(
                    true,
                    true,
                    false,
                    false,
                    observed.verifierId(),
                    Set.of(observed.evidenceRef()));
        }
        boolean success = observed.exists() && observed.active() == expectedActive;
        return new AdapterActivationAuthorityBridge.Verification(
                true,
                true,
                true,
                success,
                observed.verifierId(),
                Set.of(observed.evidenceRef()));
    }

    private AdapterLifecycleResult mapActivation(
            AdapterArtifactIdentity identity,
            AdapterArtifactObservation post,
            AdapterActivationAuthorityBridge.AuthorizationTicket ticket,
            AdapterActivationAuthorityBridge.ExecutionTruth truth) {
        boolean active = post != null && identity.equals(post.identity()) && post.exists() && post.active();
        return switch (truth.status()) {
            case VERIFIED_SUCCESS -> result(
                    AdapterLifecycleStatus.ACTIVATED_VERIFIED,
                    identity,
                    true,
                    true,
                    true,
                    true,
                    ticket.oneTimeApprovalConsumed(),
                    merge(ticket.reasons(), truth.reason()));
            case VERIFIED_FAILURE -> result(
                    AdapterLifecycleStatus.ACTIVATION_FAILED_VERIFIED,
                    identity,
                    true,
                    true,
                    active,
                    true,
                    ticket.oneTimeApprovalConsumed(),
                    merge(ticket.reasons(), truth.reason()));
            case UNVERIFIED, BLOCKED -> result(
                    AdapterLifecycleStatus.ACTIVATION_UNVERIFIED,
                    identity,
                    true,
                    false,
                    active,
                    true,
                    ticket.oneTimeApprovalConsumed(),
                    merge(ticket.reasons(), truth.reason()));
        };
    }

    private AdapterLifecycleResult mapRollback(
            AdapterArtifactIdentity identity,
            AdapterArtifactObservation post,
            AdapterActivationAuthorityBridge.AuthorizationTicket ticket,
            AdapterActivationAuthorityBridge.ExecutionTruth truth) {
        boolean active = post == null || !identity.equals(post.identity()) || !post.exists() || post.active();
        return switch (truth.status()) {
            case VERIFIED_SUCCESS -> result(
                    AdapterLifecycleStatus.ROLLED_BACK_VERIFIED,
                    identity,
                    true,
                    true,
                    false,
                    true,
                    ticket.oneTimeApprovalConsumed(),
                    merge(ticket.reasons(), truth.reason()));
            case VERIFIED_FAILURE -> result(
                    AdapterLifecycleStatus.ROLLBACK_FAILED_VERIFIED,
                    identity,
                    true,
                    true,
                    active,
                    true,
                    ticket.oneTimeApprovalConsumed(),
                    merge(ticket.reasons(), truth.reason()));
            case UNVERIFIED, BLOCKED -> result(
                    AdapterLifecycleStatus.ROLLBACK_UNVERIFIED,
                    identity,
                    true,
                    false,
                    active,
                    true,
                    ticket.oneTimeApprovalConsumed(),
                    merge(ticket.reasons(), truth.reason()));
        };
    }

    private AdapterLifecycleResult blocked(
            AdapterArtifactIdentity identity,
            List<String> reasons,
            boolean active) {
        List<String> safeReasons = reasons == null || reasons.isEmpty()
                ? List.of("STAGE33_AUTHORITY_BLOCKED")
                : reasons;
        return result(
                AdapterLifecycleStatus.BLOCKED,
                identity,
                false,
                false,
                active,
                false,
                false,
                safeReasons);
    }

    private AdapterLifecycleResult result(
            AdapterLifecycleStatus status,
            AdapterArtifactIdentity identity,
            boolean effectAttempted,
            boolean verified,
            boolean active,
            boolean authorityGranted,
            boolean oneTimeApprovalConsumed,
            List<String> reasons) {
        return new AdapterLifecycleResult(
                status,
                identity,
                effectAttempted,
                verified,
                active,
                authorityGranted,
                oneTimeApprovalConsumed,
                reasons);
    }

    private List<String> merge(List<String> existing, String... additions) {
        List<String> merged = new ArrayList<>(existing == null ? List.of() : existing);
        for (String addition : additions) {
            if (addition != null && !addition.isBlank()) {
                merged.add(addition);
            }
        }
        return List.copyOf(merged);
    }
}

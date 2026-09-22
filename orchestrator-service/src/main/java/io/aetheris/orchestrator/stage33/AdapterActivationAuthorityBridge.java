package io.aetheris.orchestrator.stage33;

import io.aetheris.orchestrator.agent.RiskLevel;
import io.aetheris.orchestrator.policy.OperationMode;
import io.aetheris.orchestrator.stage32.EmergencyMode;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Narrow public bridge that lets the local adapter lifecycle reuse Stage 33 authority
 * without exposing Stage 33's package-private governance model outside its boundary.
 */
public final class AdapterActivationAuthorityBridge {
    private final CrossSystemGovernanceEngine governance;
    private final GovernanceVerificationService verification;

    public AdapterActivationAuthorityBridge() {
        this(new CrossSystemGovernanceEngine(), new GovernanceVerificationService());
    }

    AdapterActivationAuthorityBridge(
            CrossSystemGovernanceEngine governance,
            GovernanceVerificationService verification) {
        this.governance = Objects.requireNonNull(governance, "governance");
        this.verification = Objects.requireNonNull(verification, "verification");
    }

    public AuthorizationTicket authorize(
            AuthorityRequest request,
            ScopedOwnerApproval approval,
            Instant now,
            EmergencyMode emergencyMode) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(now, "now");

        GovernanceAction action = new GovernanceAction(
                request.actionId(),
                request.actorId(),
                "syntra-local-adapter",
                request.action().name(),
                request.scope(),
                OperationMode.PRIVATE,
                RiskLevel.HIGH,
                DataClassification.PRIVATE,
                false,
                0d,
                false,
                false,
                false,
                false,
                false,
                true,
                false,
                true);

        ApprovalGrant grant = approval == null ? null : approval.toGrant();
        GovernanceDecision decision = governance.preflight(
                action,
                List.of(),
                grant,
                request.previewPassed(),
                emergencyMode == null ? EmergencyMode.NORMAL : emergencyMode,
                now);
        return new AuthorizationTicket(request.actionId(), request.scope(), decision);
    }

    public ExecutionTruth finalizeExecution(
            AuthorizationTicket ticket,
            Verification result) {
        Objects.requireNonNull(ticket, "ticket");

        ExecutionObservation observation = result == null
                ? null
                : new ExecutionObservation(result.attempted(), result.completed(), result.evidenceRefs().stream().findFirst().orElse(""));
        VerificationReceipt receipt = result == null
                ? null
                : new VerificationReceipt(
                        result.verified(),
                        result.success(),
                        result.verifier(),
                        result.evidenceRefs());

        GovernanceExecutionRecord record = verification.finalizeExecution(
                ticket.preflight,
                observation,
                receipt);
        return new ExecutionTruth(
                TruthStatus.valueOf(record.status().name()),
                record.evidenceRefs(),
                record.reason());
    }

    public enum Action {
        ACTIVATE_ADAPTER,
        DETACH_ADAPTER
    }

    public enum TruthStatus {
        BLOCKED,
        UNVERIFIED,
        VERIFIED_SUCCESS,
        VERIFIED_FAILURE
    }

    public record AuthorityRequest(
            String actionId,
            String actorId,
            Action action,
            String scope,
            boolean previewPassed) {
        public AuthorityRequest {
            actionId = requireText(actionId, "actionId");
            actorId = requireText(actorId, "actorId");
            action = Objects.requireNonNull(action, "action");
            scope = requireText(scope, "scope");
        }
    }

    public record ScopedOwnerApproval(
            String tokenId,
            String approverId,
            String actionId,
            String scope,
            Instant issuedAt,
            Instant expiresAt,
            boolean oneTime,
            boolean privateModeOverride) {
        public ScopedOwnerApproval {
            tokenId = requireText(tokenId, "tokenId");
            approverId = requireText(approverId, "approverId");
            actionId = requireText(actionId, "actionId");
            scope = requireText(scope, "scope");
            issuedAt = Objects.requireNonNull(issuedAt, "issuedAt");
            expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
            if (!expiresAt.isAfter(issuedAt)) {
                throw new IllegalArgumentException("approval expiry must be after issue time");
            }
        }

        private ApprovalGrant toGrant() {
            return new ApprovalGrant(
                    tokenId,
                    approverId,
                    actionId,
                    scope,
                    issuedAt,
                    expiresAt,
                    oneTime,
                    privateModeOverride);
        }
    }

    public record Verification(
            boolean attempted,
            boolean completed,
            boolean verified,
            boolean success,
            String verifier,
            Set<String> evidenceRefs) {
        public Verification {
            verifier = verifier == null ? "" : verifier.trim();
            evidenceRefs = Set.copyOf(evidenceRefs == null ? Set.of() : evidenceRefs);
            if (success && !verified) {
                throw new IllegalArgumentException("success cannot be claimed without verification");
            }
            if (verified && (verifier.isBlank() || evidenceRefs.isEmpty())) {
                throw new IllegalArgumentException("verified execution requires verifier and evidence refs");
            }
        }
    }

    public record ExecutionTruth(
            TruthStatus status,
            Set<String> evidenceRefs,
            String reason) {
        public ExecutionTruth {
            status = Objects.requireNonNull(status, "status");
            evidenceRefs = Set.copyOf(evidenceRefs == null ? Set.of() : evidenceRefs);
            reason = reason == null ? "" : reason.trim();
        }
    }

    public static final class AuthorizationTicket {
        private final String actionId;
        private final String scope;
        private final GovernanceDecision preflight;

        private AuthorizationTicket(String actionId, String scope, GovernanceDecision preflight) {
            this.actionId = actionId;
            this.scope = scope;
            this.preflight = Objects.requireNonNull(preflight, "preflight");
        }

        public String actionId() {
            return actionId;
        }

        public String scope() {
            return scope;
        }

        public boolean executionEligible() {
            return preflight.executionEligible();
        }

        public boolean verificationRequired() {
            return preflight.verificationRequired();
        }

        public boolean oneTimeApprovalConsumed() {
            return preflight.approvalConsumed();
        }

        public List<String> reasons() {
            return preflight.reasons();
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}

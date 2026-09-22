package io.aetheris.orchestrator.stage33;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ScopedApprovalService {
    private final Set<String> consumedOneTimeTokens = ConcurrentHashMap.newKeySet();

    public ApprovalValidation validateAndConsume(ApprovalGrant grant, GovernanceAction action, Instant now, boolean privateOverrideRequired) {
        if (grant == null) return new ApprovalValidation(false, false, "No owner approval supplied");
        if (now == null) throw new IllegalArgumentException("now is required");
        if (now.isBefore(grant.issuedAt())) return new ApprovalValidation(false, false, "Approval is not active yet");
        if (!now.isBefore(grant.expiresAt())) return new ApprovalValidation(false, false, "Approval is expired");
        if (!grant.actionId().equals(action.actionId())) return new ApprovalValidation(false, false, "Approval action ID does not match");
        if (!grant.scope().equals(action.scope())) return new ApprovalValidation(false, false, "Approval scope does not match exactly");
        if (privateOverrideRequired && !grant.privateModeOverride()) return new ApprovalValidation(false, false, "PRIVATE-mode off-device override was not explicitly approved");
        if (grant.oneTime() && !consumedOneTimeTokens.add(grant.tokenId())) return new ApprovalValidation(false, false, "One-time approval has already been consumed");
        return new ApprovalValidation(true, grant.oneTime(), "Owner approval is valid and scope-bounded");
    }

    public boolean consumed(String tokenId) { return consumedOneTimeTokens.contains(tokenId); }
}

package io.aetheris.orchestrator.syntracore;

import java.util.List;
import java.util.Objects;

public record AdapterLifecycleResult(
        AdapterLifecycleStatus status,
        AdapterArtifactIdentity identity,
        boolean effectAttempted,
        boolean executionVerified,
        boolean active,
        boolean authorityGranted,
        boolean oneTimeApprovalConsumed,
        List<String> reasons) {

    public AdapterLifecycleResult {
        status = Objects.requireNonNull(status, "status");
        identity = Objects.requireNonNull(identity, "identity");
        reasons = List.copyOf(Objects.requireNonNull(reasons, "reasons"));
        reasons.forEach(reason -> {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("reasons must not contain blank values");
            }
        });
        if (effectAttempted && !authorityGranted) {
            throw new IllegalArgumentException("an adapter lifecycle effect cannot be attempted without authority");
        }

        switch (status) {
            case BLOCKED -> {
                if (effectAttempted || executionVerified || authorityGranted) {
                    throw new IllegalArgumentException("blocked lifecycle outcomes must not claim execution or authority");
                }
            }
            case ACTIVATED_VERIFIED -> requireVerifiedEffect(effectAttempted, executionVerified, authorityGranted, active);
            case ROLLED_BACK_VERIFIED -> requireVerifiedEffect(effectAttempted, executionVerified, authorityGranted, !active);
            case ACTIVATION_FAILED_VERIFIED, ROLLBACK_FAILED_VERIFIED -> {
                if (!effectAttempted || !executionVerified || !authorityGranted) {
                    throw new IllegalArgumentException("verified failed lifecycle outcomes require an authorized, verified attempted effect");
                }
            }
            case ACTIVATION_UNVERIFIED, ROLLBACK_UNVERIFIED -> {
                if (!effectAttempted || executionVerified || !authorityGranted) {
                    throw new IllegalArgumentException("unverified lifecycle outcomes require an authorized attempted effect without verified success");
                }
            }
        }
    }

    private static void requireVerifiedEffect(
            boolean effectAttempted,
            boolean executionVerified,
            boolean authorityGranted,
            boolean expectedStateObserved) {
        if (!effectAttempted || !executionVerified || !authorityGranted || !expectedStateObserved) {
            throw new IllegalArgumentException("verified lifecycle outcome is inconsistent with execution truth");
        }
    }
}

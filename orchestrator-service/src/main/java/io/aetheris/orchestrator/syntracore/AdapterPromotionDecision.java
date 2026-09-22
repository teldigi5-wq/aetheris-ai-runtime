package io.aetheris.orchestrator.syntracore;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record AdapterPromotionDecision(
        String experimentId,
        String candidateAdapterAddress,
        AdapterPromotionStatus status,
        Optional<String> promotedAdapterAddress,
        boolean ownerApproved,
        boolean localOnly,
        boolean routingEligible,
        boolean baseWeightsMutable,
        String rollbackStrategy,
        List<String> blockers) {

    public AdapterPromotionDecision {
        if (experimentId == null || experimentId.isBlank()) {
            throw new IllegalArgumentException("experimentId must not be blank");
        }
        if (candidateAdapterAddress == null
                || !candidateAdapterAddress.startsWith("aetheris-adapter://candidate/")) {
            throw new IllegalArgumentException("candidateAdapterAddress must use candidate adapter scheme");
        }
        status = Objects.requireNonNull(status, "status");
        promotedAdapterAddress = Objects.requireNonNull(promotedAdapterAddress, "promotedAdapterAddress");
        blockers = List.copyOf(Objects.requireNonNull(blockers, "blockers"));
        blockers.forEach(blocker -> {
            if (blocker == null || blocker.isBlank()) {
                throw new IllegalArgumentException("blockers must not contain blank values");
            }
        });
        if (!localOnly) {
            throw new IllegalArgumentException("adapter promotion must remain local-only");
        }
        if (baseWeightsMutable) {
            throw new IllegalArgumentException("base model weights must remain immutable");
        }
        if (!"DETACH_ADAPTER".equals(rollbackStrategy)) {
            throw new IllegalArgumentException("rollbackStrategy must be DETACH_ADAPTER");
        }

        boolean approved = status == AdapterPromotionStatus.APPROVED_FOR_LOCAL_USE;
        if (routingEligible != approved) {
            throw new IllegalArgumentException("routingEligible must exactly match approved local-use status");
        }
        if (approved) {
            if (!ownerApproved) {
                throw new IllegalArgumentException("approved local use requires explicit owner approval");
            }
            if (!blockers.isEmpty()) {
                throw new IllegalArgumentException("approved local use cannot retain blockers");
            }
            String promoted = promotedAdapterAddress.orElseThrow(
                    () -> new IllegalArgumentException("approved local use requires a promoted adapter address"));
            if (!promoted.startsWith("aetheris-adapter://promoted/")) {
                throw new IllegalArgumentException("promoted adapter address must use promoted adapter scheme");
            }
        } else if (promotedAdapterAddress.isPresent()) {
            throw new IllegalArgumentException("held or rejected candidates cannot have a promoted adapter address");
        }
    }
}

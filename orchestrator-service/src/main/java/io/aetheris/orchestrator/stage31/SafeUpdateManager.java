package io.aetheris.orchestrator.stage31;

import java.util.Objects;

public class SafeUpdateManager {

    public UpdateTransition transition(UpdateState current, UpdateEvent event, boolean healthEvidenceVerified) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(event, "event");

        return switch (current) {
            case PROPOSED -> require(event, UpdateEvent.STAGE, UpdateState.STAGED,
                    "Update staged without production promotion.");
            case STAGED -> require(event, UpdateEvent.START_CANARY, UpdateState.CANARY_RUNNING,
                    "Canary started; promotion remains blocked.");
            case CANARY_RUNNING -> switch (event) {
                case HEALTH_PASS -> {
                    if (!healthEvidenceVerified) {
                        throw new IllegalStateException("health pass requires verified health evidence");
                    }
                    yield new UpdateTransition(current, UpdateState.HEALTHY,
                            "Canary health verified; promotion may now be considered.");
                }
                case HEALTH_FAIL -> new UpdateTransition(current, UpdateState.ROLLBACK_REQUIRED,
                        "Canary health failed; rollback is required.");
                default -> throw invalid(current, event);
            };
            case HEALTHY -> {
                if (event != UpdateEvent.PROMOTE || !healthEvidenceVerified) {
                    throw new IllegalStateException("promotion requires HEALTHY state and verified health evidence");
                }
                yield new UpdateTransition(current, UpdateState.PROMOTED,
                        "Verified healthy canary promoted.");
            }
            case ROLLBACK_REQUIRED -> require(event, UpdateEvent.ROLLBACK, UpdateState.ROLLED_BACK,
                    "Rollback recorded after failed health verification.");
            case PROMOTED, ROLLED_BACK -> throw new IllegalStateException(
                    "terminal update state does not accept further transitions: " + current);
        };
    }

    private static UpdateTransition require(
            UpdateEvent actual, UpdateEvent expected, UpdateState next, String reason) {
        if (actual != expected) {
            throw new IllegalStateException("expected " + expected + " but received " + actual);
        }
        UpdateState from = switch (expected) {
            case STAGE -> UpdateState.PROPOSED;
            case START_CANARY -> UpdateState.STAGED;
            case ROLLBACK -> UpdateState.ROLLBACK_REQUIRED;
            default -> throw new IllegalArgumentException("unsupported helper transition");
        };
        return new UpdateTransition(from, next, reason);
    }

    private static IllegalStateException invalid(UpdateState state, UpdateEvent event) {
        return new IllegalStateException("invalid update transition: " + state + " + " + event);
    }
}

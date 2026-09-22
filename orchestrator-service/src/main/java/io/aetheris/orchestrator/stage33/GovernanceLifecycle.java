package io.aetheris.orchestrator.stage33;

import java.util.List;
import java.util.Objects;

public final class GovernanceLifecycle {
    private static final List<GovernancePhase> ORDER = List.of(
            GovernancePhase.UNDERSTAND,
            GovernancePhase.PLAN,
            GovernancePhase.CHECK_RULES,
            GovernancePhase.ASSESS_RISK,
            GovernancePhase.SIMULATE_PREVIEW,
            GovernancePhase.APPROVE_WHEN_REQUIRED,
            GovernancePhase.EXECUTE,
            GovernancePhase.VERIFY,
            GovernancePhase.RECORD,
            GovernancePhase.LEARN,
            GovernancePhase.REPORT);

    public List<GovernancePhase> order() { return ORDER; }

    public boolean canAdvance(GovernancePhase from, GovernancePhase to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        int index = ORDER.indexOf(from);
        return index >= 0 && index + 1 < ORDER.size() && ORDER.get(index + 1) == to;
    }

    public void requireAdvance(GovernancePhase from, GovernancePhase to) {
        if (!canAdvance(from, to)) throw new IllegalStateException("Governance lifecycle cannot jump from " + from + " to " + to);
    }

    public List<GovernancePhase> through(GovernancePhase phase) {
        int index = ORDER.indexOf(Objects.requireNonNull(phase, "phase"));
        if (index < 0) throw new IllegalArgumentException("Unknown governance phase");
        return List.copyOf(ORDER.subList(0, index + 1));
    }
}

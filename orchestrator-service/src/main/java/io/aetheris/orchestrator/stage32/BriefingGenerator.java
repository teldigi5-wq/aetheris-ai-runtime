package io.aetheris.orchestrator.stage32;

public final class BriefingGenerator {
    public String daily(BriefingInput input) { return render("DAILY", input); }
    public String endOfDay(BriefingInput input) { return render("END_OF_DAY", input); }
    private String render(String kind, BriefingInput input) {
        return kind + "\ncompleted=" + input.completed() + "\nrisks=" + input.risks()
                + "\npendingApprovals=" + input.pendingApprovals() + "\nevidence=" + input.evidenceReferences()
                + "\ncostUsd=" + String.format(java.util.Locale.ROOT, "%.4f", input.costUsd());
    }
}

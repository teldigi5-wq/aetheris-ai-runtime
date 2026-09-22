package io.aetheris.orchestrator.syntracore;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record ModelRouteDecision(
        Optional<ModelRouteSelection> selection,
        List<String> rejectionReasons) {

    public ModelRouteDecision {
        selection = Objects.requireNonNull(selection, "selection");
        rejectionReasons = List.copyOf(Objects.requireNonNull(rejectionReasons, "rejectionReasons"));
    }

    public static ModelRouteDecision selected(ModelRouteSelection selection, List<String> rejectionReasons) {
        return new ModelRouteDecision(Optional.of(selection), rejectionReasons);
    }

    public static ModelRouteDecision unavailable(List<String> rejectionReasons) {
        return new ModelRouteDecision(Optional.empty(), rejectionReasons);
    }

    public boolean selected() {
        return selection.isPresent();
    }
}

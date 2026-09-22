package io.aetheris.orchestrator.syntracore;

import java.util.Objects;

public final class SyntraAdaptationPlanningService {

    public AdaptationExperimentPlan plan(
            AdaptationExperimentRequest request,
            AdaptationDatasetManifest dataset) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(dataset, "dataset");

        if (!request.ownerOptIn()) {
            throw new IllegalArgumentException("explicit owner opt-in is required before adaptation planning");
        }

        String candidateAddress = "aetheris-adapter://candidate/"
                + request.experimentId()
                + "/"
                + request.outputAdapterId();

        return new AdaptationExperimentPlan(
                request.experimentId(),
                request.method(),
                request.baseModelId(),
                dataset.datasetHash(),
                dataset.entries().size(),
                dataset.totalRedactions(),
                candidateAddress,
                true,
                true,
                false,
                false,
                "DETACH_ADAPTER");
    }
}

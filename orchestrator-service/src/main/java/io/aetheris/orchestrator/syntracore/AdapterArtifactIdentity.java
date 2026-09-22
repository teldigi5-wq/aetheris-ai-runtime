package io.aetheris.orchestrator.syntracore;

public record AdapterArtifactIdentity(
        String experimentId,
        String candidateAdapterAddress,
        String promotedAdapterAddress,
        String baseModelId,
        String datasetHash,
        String artifactSha256,
        String artifactAddress,
        boolean localOnly,
        boolean baseWeightsMutable) {

    public AdapterArtifactIdentity {
        experimentId = requireText(experimentId, "experimentId");
        candidateAdapterAddress = requireScheme(
                candidateAdapterAddress,
                "aetheris-adapter://candidate/",
                "candidateAdapterAddress");
        promotedAdapterAddress = requireScheme(
                promotedAdapterAddress,
                "aetheris-adapter://promoted/",
                "promotedAdapterAddress");
        baseModelId = requireText(baseModelId, "baseModelId");
        if (datasetHash == null || !datasetHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("datasetHash must be a lowercase SHA-256 hex digest");
        }
        if (artifactSha256 == null || !artifactSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("artifactSha256 must be a lowercase SHA-256 hex digest");
        }
        String expectedAddress = "aetheris-adapter-artifact://sha256/" + artifactSha256;
        if (!expectedAddress.equals(artifactAddress)) {
            throw new IllegalArgumentException("artifactAddress must exactly bind the artifact SHA-256");
        }
        if (!localOnly) {
            throw new IllegalArgumentException("adapter artifact must remain local-only");
        }
        if (baseWeightsMutable) {
            throw new IllegalArgumentException("adapter artifact must not mutate base weights");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static String requireScheme(String value, String prefix, String field) {
        if (value == null || !value.startsWith(prefix)) {
            throw new IllegalArgumentException(field + " has an invalid adapter address scheme");
        }
        return value;
    }
}

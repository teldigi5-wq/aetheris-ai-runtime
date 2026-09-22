package io.aetheris.orchestrator.vault;

/**
 * Non-secret context that must accompany every credential-value resolution.
 * Callers and purposes are intentionally finite enums so policy and telemetry
 * cannot be expanded by arbitrary user-controlled strings.
 */
public record SecretAccessRequest(Caller caller, Purpose purpose, String alias) {

    public enum Caller {
        GITHUB_ADAPTER,
        CLOUD_MODEL_PROVIDER
    }

    public enum Purpose {
        GITHUB_READ,
        GITHUB_PUBLISH,
        MODEL_INFERENCE
    }
}

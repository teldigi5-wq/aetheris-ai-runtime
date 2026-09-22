package io.aetheris.orchestrator.syntracore;

import java.util.Set;

public enum InferenceTask {
    CHAT(Set.of(ModelCapability.CHAT)),
    CODING(Set.of(ModelCapability.CHAT, ModelCapability.CODING)),
    REASONING(Set.of(ModelCapability.CHAT, ModelCapability.REASONING)),
    TOOL_PLANNING(Set.of(ModelCapability.CHAT, ModelCapability.TOOL_CALLING));

    private final Set<ModelCapability> requiredCapabilities;

    InferenceTask(Set<ModelCapability> requiredCapabilities) {
        this.requiredCapabilities = Set.copyOf(requiredCapabilities);
    }

    public Set<ModelCapability> requiredCapabilities() {
        return requiredCapabilities;
    }
}

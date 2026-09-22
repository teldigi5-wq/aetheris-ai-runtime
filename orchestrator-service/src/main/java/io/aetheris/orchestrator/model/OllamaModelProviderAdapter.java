package io.aetheris.orchestrator.model;

import io.aetheris.orchestrator.agent.ModelClass;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

@Component
public class OllamaModelProviderAdapter implements ModelProviderAdapter {

    private final LocalModelService localModel;

    public OllamaModelProviderAdapter(LocalModelService localModel) {
        this.localModel = localModel;
    }

    @Override
    public String id() {
        return "ollama";
    }

    @Override
    public boolean local() {
        return true;
    }

    @Override
    public boolean zeroCost() {
        return true;
    }

    @Override
    public Set<ModelClass> supportedClasses() {
        return EnumSet.allOf(ModelClass.class);
    }

    @Override
    public ModelProviderSnapshot snapshot() {
        LocalModelHealth health = localModel.health();
        return new ModelProviderSnapshot(id(), health.available(), true, true, health.configuredModel(), health.detail());
    }

    @Override
    public LocalGenerateResponse generate(LocalGenerateRequest request) {
        return localModel.generate(request);
    }
}

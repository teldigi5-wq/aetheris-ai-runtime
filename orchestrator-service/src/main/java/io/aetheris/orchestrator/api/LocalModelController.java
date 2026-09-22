package io.aetheris.orchestrator.api;

import io.aetheris.orchestrator.model.LocalGenerateRequest;
import io.aetheris.orchestrator.model.LocalGenerateResponse;
import io.aetheris.orchestrator.model.LocalModelHealth;
import io.aetheris.orchestrator.model.LocalModelService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orchestrator/models/local")
public class LocalModelController {

    private final LocalModelService localModel;

    public LocalModelController(LocalModelService localModel) {
        this.localModel = localModel;
    }

    @GetMapping("/health")
    public LocalModelHealth health() {
        return localModel.health();
    }

    @PostMapping("/generate")
    public LocalGenerateResponse generate(@Valid @RequestBody LocalGenerateRequest request) {
        return localModel.generate(request);
    }
}

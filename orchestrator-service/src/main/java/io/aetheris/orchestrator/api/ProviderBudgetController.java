package io.aetheris.orchestrator.api;

import io.aetheris.orchestrator.model.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/orchestrator/models")
public class ProviderBudgetController {
    private final ModelProviderRegistry providers; private final ProviderUsageService usage;
    public ProviderBudgetController(ModelProviderRegistry providers,ProviderUsageService usage){this.providers=providers;this.usage=usage;}
    @GetMapping("/budgets") public List<ProviderBudgetSnapshot> budgets(){return providers.snapshots().stream().map(snapshot->usage.snapshot(providers.getRequired(snapshot.providerId()))).toList();}
}

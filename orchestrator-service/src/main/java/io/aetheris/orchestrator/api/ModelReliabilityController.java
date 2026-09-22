package io.aetheris.orchestrator.api;
import io.aetheris.orchestrator.model.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
@RequestMapping("/api/orchestrator/models")
public class ModelReliabilityController {
    private final ProviderReliabilityService reliability; private final ModelArenaService arena;
    public ModelReliabilityController(ProviderReliabilityService reliability,ModelArenaService arena){this.reliability=reliability;this.arena=arena;}
    @GetMapping("/provider-health") public List<ProviderHealthSnapshot> health(){return reliability.snapshots();}
    @GetMapping("/arena") public List<ModelArenaMeasurementEntity> arena(){return arena.recent();}
    @PostMapping("/arena") public ModelArenaMeasurementEntity record(@RequestBody ModelArenaRecordRequest request){return arena.record(request);}
}

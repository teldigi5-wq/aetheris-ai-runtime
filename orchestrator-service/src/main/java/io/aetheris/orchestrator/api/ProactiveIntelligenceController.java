package io.aetheris.orchestrator.api;
import io.aetheris.orchestrator.proactive.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/orchestrator/proactive")
public class ProactiveIntelligenceController {private final ProactiveIntelligenceService service;public ProactiveIntelligenceController(ProactiveIntelligenceService service){this.service=service;}@PostMapping("/scan") public ProactiveScanResult scan(){return service.scan();}}

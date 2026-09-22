package io.aetheris.orchestrator.api;
import io.aetheris.orchestrator.career.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/orchestrator/career")
public class CareerIntelligenceController {private final CareerIntelligenceService career;public CareerIntelligenceController(CareerIntelligenceService career){this.career=career;}@PostMapping("/audit") public CareerAuditResult audit(@RequestBody CareerAuditRequest r){return career.audit(r);}@GetMapping("/audits") public List<CareerAuditEntity> recent(){return career.recent();}}

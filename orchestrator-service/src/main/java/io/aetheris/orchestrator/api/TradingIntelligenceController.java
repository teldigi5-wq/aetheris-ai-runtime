package io.aetheris.orchestrator.api;
import io.aetheris.orchestrator.trading.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/orchestrator/trading")
public class TradingIntelligenceController {private final TradingIntelligenceService trading;public TradingIntelligenceController(TradingIntelligenceService trading){this.trading=trading;}@GetMapping("/risk-policy") public TradingRiskPolicyEntity policy(){return trading.policy();}@PutMapping("/risk-policy") public TradingRiskPolicyEntity policy(@RequestBody TradingRiskPolicyRequest r){return trading.setPolicy(r);}@PostMapping("/analyze") public TradeSignalEntity analyze(@RequestBody TradeAnalysisRequest r){return trading.analyze(r);}@GetMapping("/signals") public List<TradeSignalEntity> signals(){return trading.recent();}@PostMapping("/signals/{id}/accept") public TradeDecisionResult accept(@PathVariable UUID id){return trading.accept(id);}@PostMapping("/signals/{id}/reject") public TradeDecisionResult reject(@PathVariable UUID id){return trading.reject(id);}}

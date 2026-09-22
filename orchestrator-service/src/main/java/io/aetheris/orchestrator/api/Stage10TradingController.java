package io.aetheris.orchestrator.api;

import io.aetheris.orchestrator.trading.Stage10PaperOrderSimulatorService;
import io.aetheris.orchestrator.trading.Stage10TestnetReadinessService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orchestrator/stage10/trading")
public class Stage10TradingController {
    private final Stage10PaperOrderSimulatorService simulator;
    private final Stage10TestnetReadinessService testnet;

    public Stage10TradingController(Stage10PaperOrderSimulatorService simulator, Stage10TestnetReadinessService testnet) {
        this.simulator = simulator;
        this.testnet = testnet;
    }

    @PostMapping("/paper/simulate-order")
    public Stage10PaperOrderSimulatorService.Stage10PaperOrderSimulation simulate(
            @RequestBody Stage10PaperOrderSimulatorService.Stage10PaperOrderRequest request) {
        return simulator.simulate(request);
    }

    @GetMapping("/testnet/readiness")
    public Stage10TestnetReadinessService.TestnetReadiness testnet() { return testnet.readiness(); }
}

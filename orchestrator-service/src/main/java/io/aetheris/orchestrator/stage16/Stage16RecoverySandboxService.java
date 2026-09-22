package io.aetheris.orchestrator.stage16;

import io.aetheris.orchestrator.stage15.Stage15RecoveryPlanEntity;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service
public class Stage16RecoverySandboxService {
    private static final Set<String> SCENARIOS = Set.of("NORMAL", "ACTION_FAILURE", "VERIFY_FAILURE",
            "ROLLBACK_FAILURE", "ADAPTER_DISCONNECT", "TIMEOUT");

    public SandboxResult run(Stage15RecoveryPlanEntity plan, Stage16AdapterEntity adapter, String scenarioValue) {
        if (plan == null || adapter == null) throw new IllegalArgumentException("Plan and adapter are required");
        if (!adapter.isSimulationOnly()) throw new IllegalStateException("Stage 16 repository sandbox refuses non-simulated adapters");
        String scenario = scenarioValue == null || scenarioValue.isBlank() ? "NORMAL" : scenarioValue.trim().toUpperCase(Locale.ROOT);
        if (!SCENARIOS.contains(scenario)) throw new IllegalArgumentException("Unsupported Stage 16 sandbox scenario");

        boolean actionAttempted = true;
        boolean actionSucceeded = true;
        boolean verificationAttempted = false;
        boolean verificationSucceeded = false;
        boolean rollbackAttempted = false;
        boolean rollbackSucceeded = false;
        String outcome;

        switch (scenario) {
            case "ACTION_FAILURE" -> {
                actionSucceeded = false;
                outcome = "SIMULATED_ACTION_FAILED";
            }
            case "ADAPTER_DISCONNECT" -> {
                actionSucceeded = false;
                outcome = "SIMULATED_ADAPTER_DISCONNECTED";
            }
            case "TIMEOUT" -> {
                actionSucceeded = false;
                outcome = "SIMULATED_TIMEOUT";
            }
            case "VERIFY_FAILURE" -> {
                verificationAttempted = true;
                verificationSucceeded = false;
                rollbackAttempted = true;
                rollbackSucceeded = true;
                outcome = "SIMULATED_VERIFY_FAILED_ROLLED_BACK";
            }
            case "ROLLBACK_FAILURE" -> {
                verificationAttempted = true;
                verificationSucceeded = false;
                rollbackAttempted = true;
                rollbackSucceeded = false;
                outcome = "SIMULATED_VERIFY_FAILED_ROLLBACK_FAILED";
            }
            default -> {
                verificationAttempted = true;
                verificationSucceeded = true;
                outcome = "SIMULATED_RECOVERED";
            }
        }

        String receiptMaterial = plan.getId() + "|" + adapter.getId() + "|" + scenario + "|" + outcome + "|"
                + actionAttempted + "|" + actionSucceeded + "|" + verificationAttempted + "|"
                + verificationSucceeded + "|" + rollbackAttempted + "|" + rollbackSucceeded;
        return new SandboxResult(outcome, scenario, actionAttempted, actionSucceeded, verificationAttempted,
                verificationSucceeded, rollbackAttempted, rollbackSucceeded, sha(receiptMaterial), true, false, false);
    }

    private String sha(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    public record SandboxResult(String outcome, String scenario, boolean actionAttempted, boolean actionSucceeded,
                                boolean verificationAttempted, boolean verificationSucceeded,
                                boolean rollbackAttempted, boolean rollbackSucceeded, String receiptSha256,
                                boolean simulationOnly, boolean targetMutated, boolean externalActionAttempted) {}
}

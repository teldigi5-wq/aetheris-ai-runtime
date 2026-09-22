package io.aetheris.orchestrator.stage10;

import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class Stage10OperationsService {
    public SloAssessment assess(SloEvidence evidence) {
        if (evidence == null) throw new IllegalArgumentException("SLO evidence is required");
        List<String> violations = new ArrayList<>();
        if (!evidence.measuredOnTargetRuntime()) violations.add("evidence has not been measured on the target workstation/runtime");
        if (evidence.commandAckMs() > 300) violations.add("command acknowledgement exceeds 300 ms target");
        if (evidence.priorityControlMs() > 250) violations.add("priority STOP/PAUSE/TAKE_CONTROL exceeds 250 ms target");
        if (evidence.missionEventDeliveryMs() > 500) violations.add("mission event delivery exceeds 500 ms target");
        if (evidence.hostHeartbeatAgeSeconds() > 30) violations.add("host heartbeat is older than 30 seconds");
        if (evidence.queueOldestReadySeconds() > 120) violations.add("oldest ready queue item exceeds 120 seconds");
        if (evidence.errorRatePct() > 1.0) violations.add("runtime error rate exceeds 1%");
        if (evidence.memoryPressurePct() > 92) violations.add("memory pressure exceeds 92%");
        String status = !evidence.measuredOnTargetRuntime() ? "EVIDENCE_REQUIRED" : violations.isEmpty() ? "HEALTHY" : "DEGRADED";
        return new SloAssessment(status, List.copyOf(violations), evidence,
                "SLO evidence is advisory observability. It cannot override emergency stop, owner policy, capability grants or financial risk controls.");
    }

    public RecoveryRunbook recovery() {
        return new RecoveryRunbook(
                List.of(
                        "Issue deterministic STOP ALL and prevent new tool/model work",
                        "Capture host/runtime health, active leases and recent audit receipts",
                        "Revoke remote sessions or host pairing if compromise is suspected",
                        "Stop the workstation host service before package repair or rollback",
                        "Restore the last owner-approved package/checkpoint and restart in simulation/observe mode",
                        "Run health checks, safety regression packs and owner capability review before re-enabling automation"),
                List.of(
                        "never delete owner projects as part of automated recovery",
                        "never discard audit receipts or trade journals to hide a failure",
                        "never weaken UAC, firewall, credential-vault or owner-policy controls to make recovery pass",
                        "never resume live-money execution because Stage 10 has no live-money capability"),
                List.of(
                        "owner-created workspace/project files",
                        "owner rules and capability policy",
                        "knowledge lineage/source state",
                        "audit receipts/evaluation evidence",
                        "paper trading journal/equity evidence",
                        "paired-device and host revocation state"));
    }

    public InstallerUpdatePolicy updatePolicy() {
        return new InstallerUpdatePolicy(
                "SIGNED_CANARY_REQUIRED",
                List.of("package checksum", "publisher/signature verification", "version provenance", "rollback package", "pre-update safety regression result"),
                List.of("deploy to explicit canary/owner workstation only", "run health and regression checks", "promote only after owner/policy gate", "retain previous package for rollback"),
                false,
                "Stage 10 defines the update contract but does not claim a signed Windows installer has been produced or executed on the target PC.");
    }

    public record SloEvidence(long commandAckMs, long priorityControlMs, long missionEventDeliveryMs,
                              long hostHeartbeatAgeSeconds, long queueOldestReadySeconds, double errorRatePct,
                              double memoryPressurePct, boolean measuredOnTargetRuntime) {}
    public record SloAssessment(String status, List<String> violations, SloEvidence evidence, String detail) {}
    public record RecoveryRunbook(List<String> steps, List<String> prohibitions, List<String> preserve) {}
    public record InstallerUpdatePolicy(String state, List<String> requiredEvidence, List<String> rolloutSteps,
                                        boolean installerProduced, String detail) {}
}

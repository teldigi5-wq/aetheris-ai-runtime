package io.aetheris.orchestrator.stage11;

import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class Stage11DeploymentService {
    public PackagePlan packagePlan() {
        return new PackagePlan(
                "SOURCE_PACKAGE_READY_TARGET_VALIDATION_PENDING",
                "AetherisHostAgent",
                List.of("host-agent.jar", "install.ps1", "uninstall.ps1", "manifest.json", "checksums.sha256", "signature.txt"),
                List.of(
                        "per-user least privilege by default",
                        "no unrestricted shell capability",
                        "signed package + checksum required before install",
                        "pairing/capability grants happen after install health checks",
                        "rollback artifact retained before activation",
                        "STOP ALL remains reachable during upgrade"),
                false,
                "CI can validate package policy and source artifacts; the installer is not considered produced or installed until a real Windows build/signing run succeeds");
    }

    public PackageValidation validatePackage(PackageEvidence evidence) {
        if (evidence == null) throw new IllegalArgumentException("Package evidence is required");
        List<String> blockers = new ArrayList<>();
        String checksum = evidence.sha256() == null ? "" : evidence.sha256().trim().toLowerCase(Locale.ROOT);
        if (!checksum.matches("[a-f0-9]{64}")) blockers.add("valid SHA-256 checksum evidence is required");
        if (!evidence.signatureVerified()) blockers.add("package signature must verify");
        if (!evidence.provenanceVerified()) blockers.add("build provenance must verify");
        if (!evidence.regressionGatePassed()) blockers.add("Stage 10 regression gate must pass");
        if (!evidence.rollbackArtifactPresent()) blockers.add("rollback artifact is required");
        if (evidence.adminRuntimeRequired()) blockers.add("normal host runtime must not require administrator privileges");
        String status = blockers.isEmpty()
                ? evidence.measuredOnTargetWindows() ? "TARGET_PACKAGE_VERIFIED" : "CI_PACKAGE_VERIFIED_TARGET_PENDING"
                : "BLOCKED";
        return new PackageValidation(status, List.copyOf(blockers), evidence.measuredOnTargetWindows(),
                blockers.isEmpty()
                        ? "Package policy passed; target-PC installation is only claimed when measuredOnTargetWindows=true"
                        : "Package activation is blocked until every integrity, rollback and privilege requirement passes");
    }

    public DesktopPerformanceResult evaluateDesktop(DesktopPerformanceEvidence evidence) {
        if (evidence == null) throw new IllegalArgumentException("Desktop performance evidence is required");
        List<String> failures = new ArrayList<>();
        if (!evidence.measuredOnTargetHardware()) failures.add("desktop evidence was not measured on the target workstation");
        if (evidence.p95FrameMs() <= 0 || evidence.p95FrameMs() > 12.0) failures.add("p95 frame time exceeds 12 ms interaction target");
        if (evidence.inputP95Ms() <= 0 || evidence.inputP95Ms() > 80) failures.add("p95 input response exceeds 80 ms");
        if (evidence.idleGpuPct() < 0 || evidence.idleGpuPct() > 20) failures.add("idle UI GPU usage exceeds 20%");
        if (evidence.inferenceGpuHeadroomPct() < 20) failures.add("less than 20% GPU headroom remains for local inference");
        if (evidence.crashCount() != 0) failures.add("desktop profiling run recorded crashes");
        String status = !evidence.measuredOnTargetHardware() ? "EVIDENCE_REQUIRED" : failures.isEmpty() ? "VERIFIED" : "FAIL";
        return new DesktopPerformanceResult(status, List.copyOf(failures),
                "144 Hz-capable interaction is a responsiveness goal, not a requirement to render every static view at 144 FPS");
    }

    public record PackagePlan(String status, String packageName, List<String> expectedArtifacts,
                              List<String> securityInvariants, boolean installerProduced, String detail) {}
    public record PackageEvidence(String sha256, boolean signatureVerified, boolean provenanceVerified,
                                  boolean regressionGatePassed, boolean rollbackArtifactPresent,
                                  boolean adminRuntimeRequired, boolean measuredOnTargetWindows) {}
    public record PackageValidation(String status, List<String> blockers, boolean targetMeasured, String detail) {}
    public record DesktopPerformanceEvidence(double p95FrameMs, double inputP95Ms, double idleGpuPct,
                                             double inferenceGpuHeadroomPct, int crashCount,
                                             boolean measuredOnTargetHardware) {}
    public record DesktopPerformanceResult(String status, List<String> failures, String detail) {}
}

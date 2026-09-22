package io.aetheris.orchestrator.reach;

import io.aetheris.orchestrator.policy.OperationMode;

import java.util.List;
import java.util.Set;

public final class ReachTypes {
    private ReachTypes() {}

    public enum Channel {
        WEB_PAGE,
        WEB_SEARCH,
        RSS,
        GITHUB,
        YOUTUBE,
        REDDIT,
        X,
        FACEBOOK,
        INSTAGRAM,
        LINKEDIN,
        BILIBILI,
        XIAOHONGSHU,
        V2EX,
        XUEQIU,
        XIAOYUZHOU
    }

    public enum Capability {
        READ,
        SEARCH,
        TRANSCRIPT,
        PROFILE,
        FEED,
        REPOSITORY
    }

    public enum Transport {
        OFFICIAL_API,
        MCP,
        CLI,
        HTTP_READER,
        RSS,
        BROWSER,
        UI_AUTOMATION
    }

    public enum ImplementationStatus {
        IMPLEMENTED,
        ADAPTER_REQUIRED,
        PHYSICAL_VALIDATION_REQUIRED
    }

    public enum InstallScope {
        NONE,
        USER_LOCAL,
        SYSTEM_APPROVAL_REQUIRED
    }

    public enum HealthStatus {
        READY,
        AUTH_REQUIRED,
        RUNTIME_UNAVAILABLE,
        ADAPTER_REQUIRED,
        PHYSICAL_VALIDATION_REQUIRED
    }

    public record BackendDescriptor(
            String id,
            String displayName,
            Channel channel,
            Transport transport,
            Set<Capability> capabilities,
            int priority,
            boolean billable,
            boolean sendsDataOffDevice,
            boolean credentialRequired,
            ImplementationStatus implementationStatus,
            InstallScope installScope,
            String installHint
    ) {
        public BackendDescriptor {
            capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
            installHint = installHint == null ? "" : installHint.trim();
        }
    }

    public record ChannelDescriptor(
            Channel channel,
            String displayName,
            Set<Capability> capabilities,
            List<BackendDescriptor> backends
    ) {
        public ChannelDescriptor {
            capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
            backends = backends == null ? List.of() : List.copyOf(backends);
        }
    }

    public record RouteRequest(
            String agentId,
            Channel channel,
            Capability capability,
            OperationMode mode,
            boolean protectedData,
            Set<String> runtimeAvailableBackendIds,
            Set<String> authenticatedBackendIds
    ) {
        public RouteRequest {
            mode = mode == null ? OperationMode.BALANCED : mode;
            runtimeAvailableBackendIds = runtimeAvailableBackendIds == null ? Set.of() : Set.copyOf(runtimeAvailableBackendIds);
            authenticatedBackendIds = authenticatedBackendIds == null ? Set.of() : Set.copyOf(authenticatedBackendIds);
        }
    }

    public record RouteDecision(
            boolean routable,
            BackendDescriptor primary,
            List<BackendDescriptor> fallbacks,
            List<String> blockedBackendIds,
            boolean requiresApproval,
            List<String> policyReasons,
            String reason
    ) {
        public RouteDecision {
            fallbacks = fallbacks == null ? List.of() : List.copyOf(fallbacks);
            blockedBackendIds = blockedBackendIds == null ? List.of() : List.copyOf(blockedBackendIds);
            policyReasons = policyReasons == null ? List.of() : List.copyOf(policyReasons);
            reason = reason == null ? "" : reason;
        }
    }

    public record DoctorRequest(
            Set<String> runtimeAvailableBackendIds,
            Set<String> authenticatedBackendIds
    ) {
        public DoctorRequest {
            runtimeAvailableBackendIds = runtimeAvailableBackendIds == null ? Set.of() : Set.copyOf(runtimeAvailableBackendIds);
            authenticatedBackendIds = authenticatedBackendIds == null ? Set.of() : Set.copyOf(authenticatedBackendIds);
        }
    }

    public record BackendHealth(
            String backendId,
            Channel channel,
            HealthStatus status,
            String detail
    ) {}

    public record DoctorReport(
            List<BackendHealth> backends,
            String physicalMachineStatus,
            String summary
    ) {
        public DoctorReport {
            backends = backends == null ? List.of() : List.copyOf(backends);
        }
    }

    public record InstallPlanRequest(
            Set<Channel> channels,
            Set<String> runtimeAvailableBackendIds,
            boolean allowSystemChanges
    ) {
        public InstallPlanRequest {
            channels = channels == null ? Set.of() : Set.copyOf(channels);
            runtimeAvailableBackendIds = runtimeAvailableBackendIds == null ? Set.of() : Set.copyOf(runtimeAvailableBackendIds);
        }
    }

    public record InstallPlanStep(
            Channel channel,
            String backendId,
            InstallScope scope,
            boolean ownerApprovalRequired,
            String action
    ) {}

    public record InstallPlan(
            List<InstallPlanStep> steps,
            boolean executableNow,
            String note
    ) {
        public InstallPlan {
            steps = steps == null ? List.of() : List.copyOf(steps);
        }
    }
}

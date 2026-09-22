package io.aetheris.orchestrator;

import io.aetheris.orchestrator.policy.OperationMode;
import io.aetheris.orchestrator.reach.ReachCatalogService;
import io.aetheris.orchestrator.reach.ReachService;
import io.aetheris.orchestrator.reach.ReachTypes.BackendHealth;
import io.aetheris.orchestrator.reach.ReachTypes.Capability;
import io.aetheris.orchestrator.reach.ReachTypes.Channel;
import io.aetheris.orchestrator.reach.ReachTypes.DoctorReport;
import io.aetheris.orchestrator.reach.ReachTypes.DoctorRequest;
import io.aetheris.orchestrator.reach.ReachTypes.HealthStatus;
import io.aetheris.orchestrator.reach.ReachTypes.ImplementationStatus;
import io.aetheris.orchestrator.reach.ReachTypes.InstallPlan;
import io.aetheris.orchestrator.reach.ReachTypes.InstallPlanRequest;
import io.aetheris.orchestrator.reach.ReachTypes.RouteDecision;
import io.aetheris.orchestrator.reach.ReachTypes.RouteRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AetherisReachLayerTest {

    @Autowired ReachCatalogService catalog;
    @Autowired ReachService reach;

    @Test
    void catalogExposesFifteenChannelsWithoutPretendingPlannedAdaptersAreImplemented() {
        assertThat(catalog.channels()).hasSize(Channel.values().length);
        assertThat(Channel.values()).hasSize(15);
        assertThat(catalog.backends().stream()
                .filter(backend -> backend.implementationStatus() == ImplementationStatus.IMPLEMENTED)
                .map(backend -> backend.id()))
                .containsExactly("github.official-api");
    }

    @Test
    void githubRoutesOnlyWhenImplementedRuntimeAndCredentialsAreReady() {
        RouteDecision decision = reach.route(new RouteRequest(
                "backend-engineer",
                Channel.GITHUB,
                Capability.READ,
                OperationMode.BALANCED,
                false,
                Set.of("github.official-api"),
                Set.of("github.official-api")));

        assertThat(decision.routable()).isTrue();
        assertThat(decision.primary().id()).isEqualTo("github.official-api");
        assertThat(decision.reason()).contains("implemented backend");
    }

    @Test
    void privateModeBlocksProtectedContextFromOffDeviceReach() {
        RouteDecision decision = reach.route(new RouteRequest(
                "backend-engineer",
                Channel.GITHUB,
                Capability.READ,
                OperationMode.PRIVATE,
                true,
                Set.of("github.official-api"),
                Set.of("github.official-api")));

        assertThat(decision.routable()).isFalse();
        assertThat(decision.blockedBackendIds()).anyMatch(value -> value.contains("github.official-api:POLICY_DENIED"));
    }

    @Test
    void credentialsAreNeverAssumedReady() {
        RouteDecision decision = reach.route(new RouteRequest(
                "backend-engineer",
                Channel.GITHUB,
                Capability.READ,
                OperationMode.BALANCED,
                false,
                Set.of("github.official-api"),
                Set.of()));

        assertThat(decision.routable()).isFalse();
        assertThat(decision.blockedBackendIds()).contains("github.official-api:AUTH_REQUIRED");
    }

    @Test
    void plannedYoutubeBackendCannotBecomeRoutableFromAClaimedRuntimeFlag() {
        RouteDecision decision = reach.route(new RouteRequest(
                "research-scientist",
                Channel.YOUTUBE,
                Capability.TRANSCRIPT,
                OperationMode.ZERO_COST,
                false,
                Set.of("youtube.yt-dlp"),
                Set.of()));

        assertThat(decision.routable()).isFalse();
        assertThat(decision.blockedBackendIds()).contains("youtube.yt-dlp:ADAPTER_REQUIRED");
    }

    @Test
    void doctorSeparatesReadyRuntimeFromUnimplementedAdapters() {
        DoctorReport report = reach.doctor(new DoctorRequest(
                Set.of("github.official-api", "youtube.yt-dlp"),
                Set.of("github.official-api")));

        BackendHealth github = report.backends().stream()
                .filter(item -> item.backendId().equals("github.official-api"))
                .findFirst().orElseThrow();
        BackendHealth youtube = report.backends().stream()
                .filter(item -> item.backendId().equals("youtube.yt-dlp"))
                .findFirst().orElseThrow();

        assertThat(github.status()).isEqualTo(HealthStatus.READY);
        assertThat(youtube.status()).isEqualTo(HealthStatus.ADAPTER_REQUIRED);
        assertThat(report.physicalMachineStatus()).isEqualTo("BLOCKED_PENDING_HARDWARE");
    }

    @Test
    void installPlanningNeverPerformsOrClaimsSystemSetup() {
        InstallPlan plan = reach.installPlan(new InstallPlanRequest(
                Set.of(Channel.YOUTUBE),
                Set.of(),
                false));

        assertThat(plan.executableNow()).isFalse();
        assertThat(plan.steps()).hasSize(1);
        assertThat(plan.steps().getFirst().backendId()).isEqualTo("youtube.yt-dlp");
        assertThat(plan.note()).contains("plan only");
    }
}

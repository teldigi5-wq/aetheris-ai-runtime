package io.aetheris.orchestrator.stage32;

import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class Stage32FoundationTest {
    private static final Instant NOW = Instant.parse("2026-09-14T06:30:00Z");

    @Test void emergencyPriorityIsDeterministicAndOwnerReleaseIsRequired() {
        EmergencyControlService control = new EmergencyControlService();
        assertEquals(EmergencyMode.PAUSE, control.request(EmergencyMode.PAUSE));
        assertEquals(EmergencyMode.TAKE_CONTROL, control.request(EmergencyMode.TAKE_CONTROL));
        assertEquals(EmergencyMode.STOP, control.request(EmergencyMode.STOP));
        assertEquals(EmergencyMode.STOP, control.request(EmergencyMode.PAUSE));
        assertThrows(SecurityException.class, () -> control.release(false));
        assertEquals(EmergencyMode.NORMAL, control.release(true));
    }

    @Test void stopPauseAndTakeControlPreemptNormalWork() {
        AdvancedScheduler scheduler = new AdvancedScheduler();
        AutomationWork work = work(false, 5, NOW.plus(Duration.ofHours(1)), Duration.ofMinutes(10));
        ResourceBudget budget = budget(false, 10);
        RetryBudget retry = new RetryBudget(3, 0);
        assertEquals(RuntimeState.CANCELLED, scheduler.decide(work, budget, retry, EmergencyMode.STOP, NOW).state());
        assertEquals(RuntimeState.PAUSED, scheduler.decide(work, budget, retry, EmergencyMode.PAUSE, NOW).state());
        assertEquals(RuntimeState.PAUSED, scheduler.decide(work, budget, retry, EmergencyMode.TAKE_CONTROL, NOW).state());
    }

    @Test void schedulerRespectsRetryResourceAndUserLoadBudgets() {
        AdvancedScheduler scheduler = new AdvancedScheduler();
        AutomationWork work = work(false, 5, NOW.plus(Duration.ofHours(1)), Duration.ofMinutes(10));
        assertEquals(RuntimeState.BLOCKED, scheduler.decide(work, budget(false, 10), new RetryBudget(2, 2), EmergencyMode.NORMAL, NOW).state());
        assertEquals(RuntimeState.WAITING, scheduler.decide(work, budget(false, 95), new RetryBudget(2, 0), EmergencyMode.NORMAL, NOW).state());
        AutomationWork tooLarge = new AutomationWork("large", false, 5, 90, 20, null, Duration.ofMinutes(5));
        assertEquals(RuntimeState.BLOCKED, scheduler.decide(tooLarge, budget(false, 10), new RetryBudget(2, 0), EmergencyMode.NORMAL, NOW).state());
    }

    @Test void hardDeadlineOverridesEnergyDeferralButNotEmergencyStop() {
        AdvancedScheduler scheduler = new AdvancedScheduler();
        AutomationWork deadline = work(false, 4, NOW.plusSeconds(30), Duration.ofMinutes(10));
        assertEquals(RuntimeState.RUNNING, scheduler.decide(deadline, budget(true, 95), new RetryBudget(3, 0), EmergencyMode.NORMAL, NOW).state());
        assertEquals(RuntimeState.CANCELLED, scheduler.decide(deadline, budget(true, 95), new RetryBudget(3, 0), EmergencyMode.STOP, NOW).state());
    }

    @Test void runtimeCheckpointEnforcesMaxRuntimeAndEmergencyState() {
        AdvancedScheduler scheduler = new AdvancedScheduler();
        AutomationWork work = work(false, 5, null, Duration.ofMinutes(2));
        assertEquals(RuntimeState.CANCELLED, scheduler.checkpoint(work, EmergencyMode.NORMAL, NOW.minusSeconds(121), NOW).state());
        assertEquals(RuntimeState.PAUSED, scheduler.checkpoint(work, EmergencyMode.TAKE_CONTROL, NOW.minusSeconds(10), NOW).state());
    }

    @Test void watcherNormalizesAllRequiredEventSourcesAndTriggerDebounces() {
        WatcherAbstraction watcher = new WatcherAbstraction();
        EventTriggerEngine engine = new EventTriggerEngine();
        for (EventSource source : EventSource.values()) {
            AutomationEvent event = watcher.normalize(source, "source", "event-" + source, NOW, "evidence://" + source);
            assertTrue(engine.accept(event, Duration.ofSeconds(30)));
            AutomationEvent duplicate = watcher.normalize(source, "source", "event-" + source, NOW.plusSeconds(10), "evidence://2");
            assertFalse(engine.accept(duplicate, Duration.ofSeconds(30)));
        }
    }

    @Test void notificationHubHonorsQuietHoursFocusAndCriticalBypass() {
        NotificationHub hub = new NotificationHub();
        LocalTime quietNow = LocalTime.of(23, 0);
        NotificationRoute quiet = hub.route(NotificationUrgency.NORMAL, quietNow, LocalTime.of(22, 0), LocalTime.of(7, 0), true, false);
        assertFalse(quiet.desktop()); assertFalse(quiet.external());
        NotificationRoute critical = hub.route(NotificationUrgency.CRITICAL, quietNow, LocalTime.of(22, 0), LocalTime.of(7, 0), true, true);
        assertTrue(critical.desktop()); assertTrue(critical.external()); assertTrue(critical.sound());
    }

    @Test void focusModeExitsImmediatelyAndOnlyChangesPolicy() {
        FocusModeService focus = new FocusModeService();
        assertTrue(focus.enter()); assertFalse(focus.automationAllowed(false)); assertTrue(focus.automationAllowed(true));
        assertFalse(focus.exit()); assertTrue(focus.automationAllowed(false));
    }

    @Test void timelineAndAuditExplorerReconstructWhyEvidenceCostAndOutcome() {
        AutomationTimeline timeline = new AutomationTimeline();
        timeline.append(new TimelineEntry("1", NOW, "scheduler", "AUTOMATION", "owner", "DEFERRED", 3, "evidence://load", 0.0, "user busy"));
        timeline.append(new TimelineEntry("2", NOW.plusSeconds(1), "remote", "CONTROL", "owner", "ALLOWED", 8, "evidence://auth", 0.0, "take control"));
        List<TimelineEntry> results = new AuditExplorer().search(timeline.snapshot(), new AuditQuery(null, null, "remote", null, "owner", null, 5, "auth"));
        assertEquals(1, results.size()); assertEquals("take control", results.getFirst().reason());
    }

    @Test void remoteControlRequiresOwnerShortLivedSessionCapabilitiesAndReplayProtection() {
        RemoteControlGuard guard = new RemoteControlGuard();
        RemoteControlSession session = new RemoteControlSession("s1", true, NOW.minusSeconds(5), NOW.plusSeconds(60), Set.of("READ_STATUS", "TAKE_CONTROL"));
        assertTrue(guard.authorize(session, "n1", "READ_STATUS", NOW, EmergencyMode.NORMAL).allowed());
        assertFalse(guard.authorize(session, "n1", "READ_STATUS", NOW.plusSeconds(1), EmergencyMode.NORMAL).allowed());
        assertFalse(guard.authorize(session, "n2", "UNGRANTED", NOW, EmergencyMode.NORMAL).allowed());
        RemoteControlSession notOwner = new RemoteControlSession("s2", false, NOW.minusSeconds(1), NOW.plusSeconds(20), Set.of("READ_STATUS"));
        assertFalse(guard.authorize(notOwner, "n3", "READ_STATUS", NOW, EmergencyMode.NORMAL).allowed());
    }

    @Test void emergencyStateRestrictsRemotePlaneToEmergencyCapabilities() {
        RemoteControlGuard guard = new RemoteControlGuard();
        RemoteControlSession session = new RemoteControlSession("s1", true, NOW.minusSeconds(1), NOW.plusSeconds(30), Set.of("READ_STATUS", "TAKE_CONTROL", "RUN_WORKFLOW"));
        assertFalse(guard.authorize(session, "n1", "RUN_WORKFLOW", NOW, EmergencyMode.STOP).allowed());
        assertTrue(guard.authorize(session, "n2", "TAKE_CONTROL", NOW, EmergencyMode.STOP).allowed());
    }

    @Test void artifactManagerTracksProvenanceAndRetention() {
        ArtifactManager manager = new ArtifactManager();
        ArtifactRecord record = new ArtifactRecord("a1", "artifact://report", "stage32", "abc123", Set.of("evidence://ci"), NOW, NOW.plus(Duration.ofDays(7)));
        assertEquals(record, manager.register(record));
        assertEquals(1, manager.retainedAt(NOW.plus(Duration.ofDays(1))).size());
        assertEquals(0, manager.retainedAt(NOW.plus(Duration.ofDays(8))).size());
    }

    @Test void workflowComposerOrdersDependenciesAndRejectsCycles() {
        WorkflowComposer composer = new WorkflowComposer();
        List<String> order = composer.compose(List.of(new WorkflowStep("test", Set.of("build"), "VERIFY"), new WorkflowStep("build", Set.of(), "BUILD")));
        assertEquals(List.of("build", "test"), order);
        assertThrows(IllegalArgumentException.class, () -> composer.compose(List.of(new WorkflowStep("a", Set.of("b"), "A"), new WorkflowStep("b", Set.of("a"), "B"))));
    }

    @Test void briefingsAlwaysExposeCostsRisksApprovalsAndEvidence() {
        BriefingInput input = new BriefingInput(List.of("ci green"), List.of("hardware pending"), List.of("deploy"), List.of("evidence://run"), 0.25);
        String daily = new BriefingGenerator().daily(input);
        String eod = new BriefingGenerator().endOfDay(input);
        for (String value : List.of("costUsd=0.2500", "hardware pending", "deploy", "evidence://run")) {
            assertTrue(daily.contains(value)); assertTrue(eod.contains(value));
        }
    }

    @Test void stage32DoesNotEnableAHiddenSecondaryOrchestrator() {
        OrchestratorBoundary boundary = new OrchestratorBoundary();
        assertEquals("orchestrator-service", boundary.canonicalRuntime());
        assertFalse(boundary.hiddenSecondaryOrchestratorEnabled());
    }

    private static AutomationWork work(boolean emergency, int urgency, Instant deadline, Duration maxRuntime) {
        return new AutomationWork("work", emergency, urgency, 40, 40, deadline, maxRuntime);
    }
    private static ResourceBudget budget(boolean energySaver, double userLoad) {
        return new ResourceBudget(80, 80, userLoad, 70, energySaver);
    }
}

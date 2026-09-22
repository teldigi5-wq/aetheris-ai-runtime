package io.aetheris.orchestrator.stage31;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class Stage31DigitalTwinService {
    private final Stage31ProactiveIntelligenceEngine proactive = new Stage31ProactiveIntelligenceEngine();
    private final GoalPriorityBrain priorityBrain = new GoalPriorityBrain();
    private final SelfHealingPlanner selfHealingPlanner = new SelfHealingPlanner();
    private final SafeUpdateManager safeUpdateManager = new SafeUpdateManager();
    private final ModelBenchmarkEngine benchmarkEngine = new ModelBenchmarkEngine();

    public Stage31AssessmentResult assess(Stage31AssessmentRequest request) {
        List<ProactiveIssue> issues = proactive.scan(request.project(), request.pc(), request.workspace());
        List<PrioritizedGoal> priorities = priorityBrain.rank(request.goals());
        boolean pendingPhysicalValidation = !request.pc().physicalEvidenceVerified();
        Instant evaluatedAt = request.project().updatedAt().isAfter(request.pc().updatedAt())
                ? request.project().updatedAt()
                : request.pc().updatedAt();
        String truthStatus = pendingPhysicalValidation
                ? "BLOCKED_PENDING_HARDWARE"
                : "PHYSICAL_EVIDENCE_VERIFIED";
        return new Stage31AssessmentResult(issues, priorities, pendingPhysicalValidation, truthStatus, evaluatedAt);
    }

    public RecoveryPlan planRecovery(RecoveryAction action) {
        return selfHealingPlanner.plan(action);
    }

    public UpdateTransition transitionUpdate(UpdateTransitionRequest request) {
        return safeUpdateManager.transition(
                request.current(), request.event(), request.healthEvidenceVerified());
    }

    public List<ModelBenchmarkScore> rankModels(ModelBenchmarkRequest request) {
        return benchmarkEngine.rank(request.samples(), request.zeroCostMode(), request.privateOnly());
    }
}

package io.aetheris.orchestrator.executive;

import io.aetheris.orchestrator.agent.RiskLevel;
import io.aetheris.orchestrator.approval.*;
import io.aetheris.orchestrator.notification.*;
import io.aetheris.orchestrator.policy.OperationMode;
import io.aetheris.orchestrator.task.*;
import io.aetheris.orchestrator.voice.*;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class ExecutiveAgentService {
    private final ExecutiveBriefingRepository briefings;
    private final TaskService tasks;
    private final ApprovalService approvals;
    private final NotificationService notifications;
    private final VoiceSessionService voice;

    public ExecutiveAgentService(ExecutiveBriefingRepository briefings, TaskService tasks,
                                 ApprovalService approvals, NotificationService notifications,
                                 VoiceSessionService voice) {
        this.briefings = briefings;
        this.tasks = tasks;
        this.approvals = approvals;
        this.notifications = notifications;
        this.voice = voice;
    }

    @Transactional
    public ExecutiveOvernightBriefing run(ExecutiveOvernightRunRequest request) {
        if (request == null) throw new IllegalArgumentException("Overnight run request is required");
        List<OvernightSignal> signals = request.signals() == null ? List.of() : request.signals();
        if (signals.isEmpty()) throw new IllegalArgumentException("At least one overnight signal is required");

        Instant generatedAt = Instant.now();
        Instant nextBriefingAt = request.nextBriefingAt() == null
                ? generatedAt.plus(Duration.ofHours(8)) : request.nextBriefingAt();
        if (!nextBriefingAt.isAfter(generatedAt)) {
            throw new IllegalArgumentException("nextBriefingAt must be in the future");
        }
        String ownerId = text(request.ownerId(), "owner");
        UUID runId = UUID.randomUUID();
        List<ExecutiveAction> actions = new ArrayList<>();
        int autoHandled = 0, drafts = 0, approvalsRequired = 0, signups = 0, verifiedCodeUpdates = 0;

        for (OvernightSignal signal : signals) {
            if (signal == null || signal.type() == null) throw new IllegalArgumentException("Every signal requires a type");
            String source = text(signal.source(), "synthetic");
            String subject = text(signal.subject(), signal.type().name());
            String detail = text(signal.detail(), subject);

            switch (signal.type()) {
                case SIGNUP -> {
                    int count = Math.max(1, signal.signups());
                    signups += count;
                    actions.add(new ExecutiveAction(source, signal.type(), ExecutiveDisposition.TRACKED,
                            "Tracked " + count + " new signup(s): " + subject, null, null));
                }
                case CODE_UPDATE -> {
                    boolean verified = "PASS".equalsIgnoreCase(text(signal.verificationStatus(), ""))
                            && signal.verificationRuns() >= 3;
                    if (verified) {
                        verifiedCodeUpdates++;
                        actions.add(new ExecutiveAction(source, signal.type(), ExecutiveDisposition.VERIFIED,
                                "Verified code/product update after " + signal.verificationRuns() + " successful checks: " + subject,
                                null, null));
                    } else {
                        drafts++;
                        notifications.publish("executive-code-review:" + runId + ":" + actions.size(),
                                "EXECUTIVE_CODE_REVIEW", NotificationSeverity.WARNING,
                                "Code update needs owner review", subject + " did not meet the repeated-verification threshold.",
                                runId.toString(), true);
                        actions.add(new ExecutiveAction(source, signal.type(), ExecutiveDisposition.ESCALATED,
                                "Escalated code/product update because verification evidence is incomplete: " + subject,
                                null, null));
                    }
                }
                case CALENDAR_EVENT -> actions.add(new ExecutiveAction(
                        source,
                        signal.type(),
                        ExecutiveDisposition.TRACKED,
                        "Tracked upcoming read-only calendar item: " + subject,
                        null,
                        null
                ));
                case BILLING, DEPLOYMENT -> {
                    TaskEntity task = tasks.create(new CreateTaskRequest(
                            "Executive approval: " + subject, detail, OperationMode.BALANCED));
                    tasks.transition(task.getId(), new TaskTransitionRequest(
                            TaskState.PLANNING, "executive-planner", "Executive agent prepared a consequential action for review"));
                    ApprovalEntity approval = approvals.request(new CreateApprovalRequest(
                            task.getId(), "EXECUTIVE_" + signal.type().name(), subject + ": " + detail, RiskLevel.HIGH));
                    approvalsRequired++;
                    actions.add(new ExecutiveAction(source, signal.type(), ExecutiveDisposition.APPROVAL_REQUIRED,
                            "Paused consequential action for explicit owner approval: " + subject,
                            task.getId(), approval.getId()));
                }
                case EMAIL, DM, FAQ, PRODUCT_INTEREST -> {
                    if (signal.trustedLowRisk()) {
                        autoHandled++;
                        actions.add(new ExecutiveAction(source, signal.type(), ExecutiveDisposition.AUTO_HANDLED,
                                lowRiskSummary(signal.type(), subject), null, null));
                    } else {
                        drafts++;
                        actions.add(new ExecutiveAction(source, signal.type(), ExecutiveDisposition.DRAFTED,
                                "Prepared a draft for owner review; no external send was executed: " + subject,
                                null, null));
                    }
                }
            }
        }

        String briefing = "Overnight executive report: inspected " + signals.size() + " signal(s); auto-handled "
                + autoHandled + " trusted low-risk item(s); prepared " + drafts + " draft/escalation item(s); paused "
                + approvalsRequired + " consequential action(s) for owner approval; tracked " + signups
                + " signup(s); verified " + verifiedCodeUpdates + " code/product update(s). Next briefing: "
                + nextBriefingAt + ".";

        VoiceSessionEntity voiceSession = voice.start(new StartVoiceSessionRequest(null, null));
        voice.transcript(voiceSession.getId(), new VoiceTranscriptRequest(briefing, true));

        ExecutiveBriefingEntity saved = briefings.save(new ExecutiveBriefingEntity(
                runId, ownerId, generatedAt, nextBriefingAt, voiceSession.getId(), signals.size(),
                autoHandled, drafts, approvalsRequired, signups, verifiedCodeUpdates, briefing));

        notifications.publish("executive-briefing:" + runId, "EXECUTIVE_BRIEFING_READY", NotificationSeverity.INFO,
                "Overnight executive briefing ready", briefing, runId.toString(), approvalsRequired > 0);

        return new ExecutiveOvernightBriefing(saved.getId(), saved.getOwnerId(), saved.getGeneratedAt(),
                saved.getNextBriefingAt(), saved.getVoiceSessionId(), saved.getTotalSignals(), saved.getAutoHandled(),
                saved.getDrafts(), saved.getApprovalsRequired(), saved.getSignups(), saved.getVerifiedCodeUpdates(),
                saved.getBriefing(), List.copyOf(actions));
    }

    public List<ExecutiveBriefingEntity> recent() {
        return briefings.findTop50ByOrderByGeneratedAtDesc();
    }

    private static String lowRiskSummary(ExecutiveSignalType type, String subject) {
        return switch (type) {
            case PRODUCT_INTEREST -> "Shared the approved product/agent information for trusted low-risk interest: " + subject;
            case FAQ -> "Answered an approved low-risk FAQ automatically: " + subject;
            case EMAIL -> "Handled a trusted low-risk inbox item automatically: " + subject;
            case DM -> "Handled a trusted low-risk community/DM item automatically: " + subject;
            default -> subject;
        };
    }

    private static String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}

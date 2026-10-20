package io.aetheris.orchestrator.skills;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aetheris.orchestrator.agent.*;
import io.aetheris.orchestrator.policy.OperationMode;
import io.aetheris.orchestrator.task.TaskEntity;
import io.aetheris.orchestrator.task.TaskService;
import io.aetheris.orchestrator.task.TaskState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class CandidateSkillProofPreflightTest {
    private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");
    private static final Clock TIME = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String TASK_TEXT =
            "Review responsive keyboard focus accessibility without any edits";
    private final ObjectMapper mapper = new ObjectMapper();
    private TaskService tasks;
    private TaskEntity task;
    private AgentCatalogService agents;
    private GovernedSkillAdviceService advisor;
    private UUID taskId;
    private KeyPair candidate;
    private KeyPair rogue;
    private CandidateSkillProofPreflight preflight;

    @BeforeEach
    void init() throws Exception {
        JsonNode locked;
        try (InputStream stream = getClass().getResourceAsStream(
                "/enterprise/canonical-main-specialists.json")) {
            assertThat(stream).isNotNull();
            locked = mapper.readTree(stream);
        }
        List<AgentDefinition> canonical = new ArrayList<>();
        for (JsonNode row : locked.path("agents")) {
            List<String> toolIds = new ArrayList<>();
            row.path("allowedTools").forEach(tool -> toolIds.add(tool.asText()));
            canonical.add(new AgentDefinition(row.path("id").asText(),
                    row.path("id").asText(), row.path("division").asText(),
                    "Frozen canonical role", List.of(), toolIds,
                    RiskLevel.valueOf(row.path("riskLevel").asText()),
                    ModelClass.valueOf(row.path("preferredModelClass").asText()),
                    row.path("approvalRequiredForHighImpactActions").asBoolean()));
        }
        agents = new AgentCatalogService(new AgentCatalogProperties(canonical));
        tasks = mock(TaskService.class);
        task = mock(TaskEntity.class);
        taskId = UUID.randomUUID();
        when(tasks.getRequired(taskId)).thenReturn(task);
        when(task.getState()).thenReturn(TaskState.RUNNING);
        when(task.getActiveAgentId()).thenReturn("frontend-engineer");
        when(task.getMode()).thenReturn(OperationMode.ZERO_COST);
        advisor = new GovernedSkillAdviceService(tasks, agents, mapper, true);
        candidate = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        rogue = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        preflight = new CandidateSkillProofPreflight(
                tasks, agents, advisor, candidate.getPublic(), TIME);
    }

    private static String digest(String text) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    private CandidateSkillProofPreflight.Proof sign(String text,
            UUID id, String role, String skill, OperationMode mode,
            String nonce, Instant from, Instant expires,
            KeyPair key) throws Exception {
        var base = new CandidateSkillProofPreflight.Proof(1,
                "READ_ONLY_SKILL_REFERENCE_PREFLIGHT", id, role, skill, mode,
                digest(text), nonce, from, expires, null);
        Signature s = Signature.getInstance("Ed25519");
        s.initSign(key.getPrivate());
        s.update(CandidateSkillProofPreflight.canonicalBytes(base));
        return new CandidateSkillProofPreflight.Proof(base.version(), base.purpose(),
                base.taskId(), base.agentId(), base.skillId(), base.mode(),
                base.taskSha256(), base.nonceHex(), base.issuedAt(), base.expiresAt(),
                Base64.getUrlEncoder().withoutPadding().encodeToString(s.sign()));
    }

    private CandidateSkillProofPreflight.Proof good() throws Exception {
        return sign(TASK_TEXT, taskId, "frontend-engineer", "animate",
                OperationMode.ZERO_COST, "ab".repeat(16),
                NOW.minusSeconds(1), NOW.plusSeconds(240), candidate);
    }

    @Test
    void realEd25519AndPersistedTaskReturnReferenceButNeverAuthority() throws Exception {
        var outcome = preflight.inspect(good(), TASK_TEXT);
        assertThat(outcome.candidateSignatureVerified()).isTrue();
        assertThat(outcome.taskScopeMatched()).isTrue();
        assertThat(outcome.reference().skillId()).isEqualTo("animate");
        assertThat(outcome.actualOwnerAuthenticated()).isFalse();
        assertThat(outcome.ownerApprovalGranted()).isFalse();
        assertThat(outcome.executionAuthorized()).isFalse();
        assertThat(outcome.durableReplayCertified()).isFalse();
        assertThat(outcome.productionReleased()).isFalse();
        assertThat(outcome.modelCalls()).isZero();
        assertThat(outcome.toolCalls()).isZero();
        assertThat(outcome.reference().modelCalled()).isFalse();
        assertThat(outcome.reference().toolCalled()).isFalse();
        assertThat(outcome.proofSha256()).hasSize(64);
    }

    @Test
    void rogueUnpinnedSignerAndMutatedPayloadRejected() throws Exception {
        var rogueSigned = sign(TASK_TEXT, taskId, "frontend-engineer", "animate",
                OperationMode.ZERO_COST, "ab".repeat(16), NOW.minusSeconds(1),
                NOW.plusSeconds(240), rogue);
        assertThatThrownBy(() -> preflight.inspect(rogueSigned, TASK_TEXT))
                .hasMessage("E39G4_CANDIDATE_SIGNATURE_INVALID");
        var original = good();
        var forgedSkill = new CandidateSkillProofPreflight.Proof(
                original.version(), original.purpose(), original.taskId(),
                original.agentId(), "aetheris-security-audit",
                original.mode(), original.taskSha256(), original.nonceHex(),
                original.issuedAt(), original.expiresAt(),
                original.signatureBase64Url());
        assertThatThrownBy(() -> preflight.inspect(forgedSkill, TASK_TEXT))
                .hasMessage("E39G4_CANDIDATE_SIGNATURE_INVALID");
    }

    @Test
    void exactTextShaAndTaskStateAreNotOverriddenBySignature() throws Exception {
        assertThatThrownBy(() -> preflight.inspect(good(), TASK_TEXT+" changed"))
                .hasMessage("E39G4_TASK_DIGEST_MISMATCH");
        when(task.getActiveAgentId()).thenReturn("qa-engineer");
        assertThatThrownBy(() -> preflight.inspect(good(), TASK_TEXT))
                .hasMessage("E39G4_NOT_ASSIGNED_ACTIVE_TASK");
        when(task.getActiveAgentId()).thenReturn("frontend-engineer");
        when(task.getState()).thenReturn(TaskState.AWAITING_APPROVAL);
        assertThatThrownBy(() -> preflight.inspect(good(), TASK_TEXT))
                .hasMessage("E39G4_NOT_ASSIGNED_ACTIVE_TASK");
    }

    @Test
    void actualTaskModeMismatchRejectedWithValidSignedMode() throws Exception {
        when(task.getMode()).thenReturn(OperationMode.BALANCED);
        assertThatThrownBy(() -> preflight.inspect(good(), TASK_TEXT))
                .hasMessage("E39G4_REAL_TASK_MODE_MISMATCH");
    }

    @Test
    void crossRoleAttemptCannotExploitCryptographicProof() throws Exception {
        var bad = sign(TASK_TEXT, taskId, "trading-risk-officer", "animate",
                OperationMode.ZERO_COST, "cd".repeat(16),
                NOW.minusSeconds(2), NOW.plusSeconds(60), candidate);
        assertThatThrownBy(() -> preflight.inspect(bad, TASK_TEXT))
                .hasMessage("E39G4_NOT_ASSIGNED_ACTIVE_TASK");
        var other = sign(TASK_TEXT, taskId, "frontend-engineer",
                "aetheris-security-audit", OperationMode.ZERO_COST,
                "ef".repeat(16), NOW.minusSeconds(2),
                NOW.plusSeconds(60), candidate);
        assertThatThrownBy(() -> preflight.inspect(other, TASK_TEXT))
                .hasMessageContaining("E39G3_ROLE_TO_SKILL_DENIED");
    }

    @Test
    void replayInSameInstanceRejectedButNotClaimedDurable() throws Exception {
        var signed = good();
        assertThat(preflight.inspect(signed,TASK_TEXT).candidateSignatureVerified()).isTrue();
        assertThatThrownBy(() -> preflight.inspect(signed,TASK_TEXT))
                .hasMessage("E39G4_NONCE_REPLAY_INSTANCE_DENIED");
        // A second Java process or instance can reuse the proof: this test
        // demonstrates WHY durable replay is explicitly uncertified.
        var separate = new CandidateSkillProofPreflight(
                tasks, agents, advisor, candidate.getPublic(), TIME);
        assertThat(separate.inspect(signed,TASK_TEXT).durableReplayCertified()).isFalse();
    }

    @Test
    void expiredFutureAndOversizeLifetimesAreRefused() throws Exception {
        var expired = sign(TASK_TEXT, taskId, "frontend-engineer", "animate",
                OperationMode.ZERO_COST, "ca".repeat(16),
                NOW.minusSeconds(301), NOW, candidate);
        assertThatThrownBy(() -> preflight.inspect(expired,TASK_TEXT))
                .hasMessage("E39G4_EXPIRED_OR_NOT_YET_VALID");
        var future = sign(TASK_TEXT, taskId, "frontend-engineer", "animate",
                OperationMode.ZERO_COST, "cb".repeat(16),
                NOW.plusSeconds(1), NOW.plusSeconds(60),candidate);
        assertThatThrownBy(() -> preflight.inspect(future,TASK_TEXT))
                .hasMessage("E39G4_EXPIRED_OR_NOT_YET_VALID");
        var oversize = sign(TASK_TEXT, taskId, "frontend-engineer", "animate",
                OperationMode.ZERO_COST, "cc".repeat(16),
                NOW.minusSeconds(10), NOW.plusSeconds(301),candidate);
        assertThatThrownBy(() -> preflight.inspect(oversize,TASK_TEXT))
                .hasMessage("E39G4_EXPIRED_OR_NOT_YET_VALID");
    }

    @Test
    void disabledReferenceSelectorStillRefusesEvenWithValidSignature() throws Exception {
        var off = new GovernedSkillAdviceService(tasks,agents,mapper,false);
        var secure = new CandidateSkillProofPreflight(
                tasks,agents,off,candidate.getPublic(),TIME);
        assertThatThrownBy(() -> secure.inspect(good(), TASK_TEXT))
                .hasMessage("E39G3_SKILL_ADVICE_DEFAULT_OFF");
    }

    @Test
    void unexpectedPurposeAndUnpinnedKeyDenied() throws Exception {
        assertThatThrownBy(() -> new CandidateSkillProofPreflight(
                tasks, agents, advisor, null, TIME))
                .hasMessage("E39G4_EXTERNALLY_PINNED_ED25519_KEY_REQUIRED");
        var p = good();
        var wrong = new CandidateSkillProofPreflight.Proof(1,
                "PRODUCTION_DEPLOY",p.taskId(),p.agentId(),p.skillId(),
                p.mode(),p.taskSha256(),p.nonceHex(),p.issuedAt(),
                p.expiresAt(),p.signatureBase64Url());
        assertThatThrownBy(() -> preflight.inspect(wrong,TASK_TEXT))
                .hasMessage("E39G4_PROOF_FORMAT_OR_PURPOSE_REJECTED");
    }

    @Test
    void forgedNonceOrSignatureTextCannotBeAccepted() throws Exception {
        var p = good();
        var badNonce = new CandidateSkillProofPreflight.Proof(1,p.purpose(),
                p.taskId(),p.agentId(),p.skillId(),p.mode(),
                p.taskSha256(),"../../../etc",p.issuedAt(),p.expiresAt(),
                p.signatureBase64Url());
        assertThatThrownBy(() -> preflight.inspect(badNonce,TASK_TEXT))
                .hasMessage("E39G4_PROOF_FORMAT_OR_PURPOSE_REJECTED");
        var badSig = new CandidateSkillProofPreflight.Proof(1,p.purpose(),
                p.taskId(),p.agentId(),p.skillId(),p.mode(),
                p.taskSha256(),p.nonceHex(),p.issuedAt(),p.expiresAt(),"garbage");
        assertThatThrownBy(() -> preflight.inspect(badSig,TASK_TEXT))
                .hasMessage("E39G4_SIGNATURE_ENCODING_INVALID");
    }
}

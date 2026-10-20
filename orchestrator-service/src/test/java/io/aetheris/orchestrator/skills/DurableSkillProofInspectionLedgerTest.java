package io.aetheris.orchestrator.skills;

import io.aetheris.orchestrator.agent.AgentCatalogService;
import io.aetheris.orchestrator.policy.OperationMode;
import io.aetheris.orchestrator.task.TaskEntity;
import io.aetheris.orchestrator.task.TaskRepository;
import io.aetheris.orchestrator.task.TaskService;
import io.aetheris.orchestrator.task.TaskState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real H2 database transaction, global nonce primary-key reservation
 * and competing JVM thread tests; production DB and restart persistence
 * must still be separately certified.
 */
@SpringBootTest(properties = "aetheris.governed-skills.reference-enabled=true")
class DurableSkillProofInspectionLedgerTest {
    private static final String TEXT =
            "Review accessibility of the website without changing files or execution";
    @Autowired private DurableSkillProofInspectionLedger ledger;
    @Autowired private SkillProofNonceRepository nonces;
    @Autowired private TaskRepository taskRepository;
    @Autowired private TaskService taskService;
    @Autowired private AgentCatalogService agents;
    @Autowired private GovernedSkillAdviceService advice;

    private UUID taskId;
    private KeyPair signer;

    @BeforeEach
    void setup() throws Exception {
        nonces.deleteAll();
        signer = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        taskId = UUID.randomUUID();
        TaskEntity task = new TaskEntity(taskId, "Read-only website review",
                TEXT, OperationMode.ZERO_COST);
        task.transitionTo(TaskState.RUNNING, "frontend-engineer");
        taskRepository.saveAndFlush(task);
    }

    @AfterEach
    void cleanup() {
        nonces.deleteAll();
        if (taskId != null && taskRepository.existsById(taskId)) {
            taskRepository.deleteById(taskId);
        }
    }

    private CandidateSkillProofPreflight verifier(KeyPair key) {
        return new CandidateSkillProofPreflight(
                taskService, agents, advice, key.getPublic(), Clock.systemUTC());
    }

    private CandidateSkillProofPreflight.Proof proof(
            String nonce, String role, String skill, UUID id, KeyPair key)
            throws Exception {
        Instant now = Instant.now();
        var original = new CandidateSkillProofPreflight.Proof(
                1, "READ_ONLY_SKILL_REFERENCE_PREFLIGHT", id, role, skill,
                OperationMode.ZERO_COST, HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(
                            TEXT.getBytes(StandardCharsets.UTF_8))),
                nonce, now.minusSeconds(3), now.plusSeconds(180), null);
        Signature sign = Signature.getInstance("Ed25519");
        sign.initSign(key.getPrivate());
        sign.update(CandidateSkillProofPreflight.canonicalBytes(original));
        return new CandidateSkillProofPreflight.Proof(
                original.version(), original.purpose(), original.taskId(),
                original.agentId(), original.skillId(), original.mode(),
                original.taskSha256(), original.nonceHex(), original.issuedAt(),
                original.expiresAt(),
                Base64.getUrlEncoder().withoutPadding().encodeToString(sign.sign()));
    }

    @Test
    void databaseRecordsOnlyReadOnlyCandidateEvidenceNeverAuthority() throws Exception {
        var p = proof("aa".repeat(16), "frontend-engineer","animate",taskId,signer);
        var received = ledger.inspectAndRecord(p,TEXT,verifier(signer));
        assertThat(received.databaseNonceRecorded()).isTrue();
        assertThat(received.humanOwnerAuthenticated()).isFalse();
        assertThat(received.ownerApprovalGranted()).isFalse();
        assertThat(received.durableActionReplayCertified()).isFalse();
        assertThat(received.toolExecutionAuthorized()).isFalse();
        assertThat(received.stage45Released()).isFalse();
        assertThat(received.modelCalls()).isZero();
        assertThat(received.toolCalls()).isZero();
        assertThat(nonces.count()).isEqualTo(1);
        var persisted = nonces.findById(p.nonceHex()).orElseThrow();
        assertThat(persisted.getTaskId()).isEqualTo(taskId);
        assertThat(persisted.getAgentId()).isEqualTo("frontend-engineer");
        assertThat(persisted.getSkillId()).isEqualTo("animate");
        assertThat(persisted.getProofSha256()).matches("[0-9a-f]{64}");
        assertThat(persisted.getEvidenceState())
                .isEqualTo("CANDIDATE_SIGNATURE_INSPECTED_NOT_OWNER_AUTHENTICATED");
    }

    @Test
    void replayByFreshVerifierStillFailsAtDatabasePrimaryKey() throws Exception {
        var p = proof("bb".repeat(16),"frontend-engineer","animate",taskId,signer);
        ledger.inspectAndRecord(p,TEXT,verifier(signer));
        // Independently constructed verifier has an EMPTY in-memory nonce set.
        assertThatThrownBy(() -> ledger.inspectAndRecord(p,TEXT,verifier(signer)))
                .isInstanceOf(RuntimeException.class);
        assertThat(nonces.count()).isEqualTo(1);
        assertThat(nonces.findById(p.nonceHex()).orElseThrow().getTaskId())
                .isEqualTo(taskId);
    }

    @Test
    void globalNonceCannotBeReusedByAnotherTaskAndSignedProof() throws Exception {
        var p = proof("cc".repeat(16),"frontend-engineer","animate",taskId,signer);
        ledger.inspectAndRecord(p,TEXT,verifier(signer));
        UUID other = UUID.randomUUID();
        TaskEntity t = new TaskEntity(other,"Another task",TEXT,OperationMode.ZERO_COST);
        t.transitionTo(TaskState.RUNNING,"frontend-engineer");
        taskRepository.saveAndFlush(t);
        try {
            var changed = proof("cc".repeat(16),
                    "frontend-engineer","animate",other,signer);
            assertThatThrownBy(() -> ledger.inspectAndRecord(
                    changed,TEXT,verifier(signer))).isInstanceOf(RuntimeException.class);
            assertThat(nonces.count()).isEqualTo(1);
        } finally {
            taskRepository.deleteById(other);
        }
    }

    @Test
    void wrongSignerOrChangedTaskNeverWritesEvidence() throws Exception {
        var p = proof("dd".repeat(16),"frontend-engineer","animate",taskId,signer);
        KeyPair unrelated = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        assertThatThrownBy(() -> ledger.inspectAndRecord(p,TEXT,verifier(unrelated)))
                .hasMessage("E39G4_CANDIDATE_SIGNATURE_INVALID");
        assertThatThrownBy(() -> ledger.inspectAndRecord(p,TEXT + " forged",verifier(signer)))
                .hasMessage("E39G4_TASK_DIGEST_MISMATCH");
        assertThat(nonces.count()).isZero();
    }

    @Test
    void signedCrossRoleSkillAndInactiveTaskFailWithoutNonce() throws Exception {
        var unauthorized = proof("ee".repeat(16),
                "frontend-engineer","aetheris-security-audit",taskId,signer);
        assertThatThrownBy(() -> ledger.inspectAndRecord(
                unauthorized,TEXT,verifier(signer)))
                .hasMessageContaining("E39G3_ROLE_TO_SKILL_DENIED");
        var task = taskRepository.findById(taskId).orElseThrow();
        task.transitionTo(TaskState.PAUSED, "frontend-engineer");
        taskRepository.saveAndFlush(task);
        var valid = proof("ef".repeat(16),"frontend-engineer","animate",taskId,signer);
        assertThatThrownBy(() -> ledger.inspectAndRecord(valid,TEXT,verifier(signer)))
                .hasMessageContaining("E39G4_NOT_ASSIGNED_ACTIVE_TASK");
        assertThat(nonces.count()).isZero();
    }

    @Test
    void concurrentProofsCompeteForExactlyOnePersistentGlobalNonce() throws Exception {
        var p = proof("ff".repeat(16),"frontend-engineer","animate",taskId,signer);
        var start = new CountDownLatch(1);
        var accepted = new AtomicInteger();
        var refused = new AtomicInteger();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var pending = java.util.stream.IntStream.range(0,2)
                    .mapToObj(i -> pool.submit(() -> {
                        start.await();
                        try {
                            var result = ledger.inspectAndRecord(
                                    p,TEXT,verifier(signer));
                            if (result.databaseNonceRecorded()
                                    && !result.ownerApprovalGranted()
                                    && !result.toolExecutionAuthorized()) {
                                accepted.incrementAndGet();
                            }
                        } catch (RuntimeException conflict) {
                            refused.incrementAndGet();
                        }
                        return null;
                    })).toList();
            start.countDown();
            for (var future : pending) future.get(30,TimeUnit.SECONDS);
        }
        assertThat(accepted.get()).isEqualTo(1);
        assertThat(refused.get()).isEqualTo(1);
        assertThat(nonces.count()).isEqualTo(1);
    }
}

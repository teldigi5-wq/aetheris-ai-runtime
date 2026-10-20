package io.aetheris.orchestrator.skills;

import io.aetheris.orchestrator.agent.AgentCatalogService;
import io.aetheris.orchestrator.agent.AgentDefinition;
import io.aetheris.orchestrator.policy.OperationMode;
import io.aetheris.orchestrator.task.TaskEntity;
import io.aetheris.orchestrator.task.TaskService;
import io.aetheris.orchestrator.task.TaskState;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.EdECPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * E3.9G-4: INTERNAL candidate-key signed skill scope verification.
 *
 * A key must be independently pinned by a trusted caller; this class neither
 * enrolls keys nor proves the human owner's identity. This object is NOT a
 * Spring Bean, controller, approval service or executable lease authority.
 *
 * An in-memory nonce cache rejects reuse in one object instance, but it is
 * NOT a durable, transactional, restart-safe replay guarantee.
 */
public final class CandidateSkillProofPreflight {
    private static final String DOMAIN = "AETHERIS_E39G4_CANDIDATE_SKILL_REFERENCE_PROOF_V1";
    private static final String PURPOSE = "READ_ONLY_SKILL_REFERENCE_PREFLIGHT";
    private static final Pattern SLUG = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private static final Pattern NONCE = Pattern.compile("[0-9a-f]{32}");
    private static final Pattern DIGEST = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern SIGNED = Pattern.compile("[A-Za-z0-9_-]{86}");
    private static final int MAX_SECONDS = 300;
    private static final Set<TaskState> ALLOWED_STATES =
            Set.of(TaskState.RUNNING, TaskState.VERIFYING);

    private final TaskService tasks;
    private final AgentCatalogService agents;
    private final GovernedSkillAdviceService advice;
    private final PublicKey pinnedCandidateKey;
    private final Clock clock;
    private final Set<String> seenNonces = ConcurrentHashMap.newKeySet();

    public CandidateSkillProofPreflight(
            TaskService tasks, AgentCatalogService agents,
            GovernedSkillAdviceService advice, PublicKey pinnedCandidateKey,
            Clock clock) {
        this.tasks = Objects.requireNonNull(tasks, "tasks");
        this.agents = Objects.requireNonNull(agents, "agents");
        this.advice = Objects.requireNonNull(advice, "advice");
        this.clock = Objects.requireNonNull(clock, "clock");
        require(pinnedCandidateKey instanceof EdECPublicKey ed
                && "Ed25519".equals(ed.getParams().getName()),
                "E39G4_EXTERNALLY_PINNED_ED25519_KEY_REQUIRED");
        this.pinnedCandidateKey = pinnedCandidateKey;
    }

    /** Proof has no trusted principal or self-declared verification key. */
    public record Proof(int version, String purpose, UUID taskId, String agentId,
                        String skillId, OperationMode mode, String taskSha256,
                        String nonceHex, Instant issuedAt, Instant expiresAt,
                        String signatureBase64Url) {}

    /** All authority booleans are false; this is NOT an owner-approval object. */
    public record Inspection(String evidenceClass, String proofSha256,
            UUID taskId, String agentId, String skillId,
            GovernedSkillAdviceService.ReferenceReceipt reference,
            boolean candidateSignatureVerified, boolean taskScopeMatched,
            boolean actualOwnerAuthenticated, boolean ownerApprovalGranted,
            boolean executionAuthorized, boolean durableReplayCertified,
            boolean productionReleased, int modelCalls, int toolCalls) {}

    /**
     * Proof-of-possession + exact durable task scope + advice default-off gate.
     * This emits advisory reference metadata ONLY, with zero tool/model calls.
     */
    public Inspection inspect(Proof proof, String boundedTaskSummary) {
        require(proof != null && boundedTaskSummary != null,
                "E39G4_PROOF_INPUT_REQUIRED");
        byte[] data = canonicalBytes(proof);
        Instant now = clock.instant();
        require(!now.isBefore(proof.issuedAt())
                && now.isBefore(proof.expiresAt())
                && proof.expiresAt().isAfter(proof.issuedAt())
                && !proof.expiresAt().isAfter(proof.issuedAt().plusSeconds(MAX_SECONDS)),
                "E39G4_EXPIRED_OR_NOT_YET_VALID");
        require(DIGEST.matcher(proof.taskSha256()).matches()
                && proof.taskSha256().equals(sha256(boundedTaskSummary.getBytes(StandardCharsets.UTF_8))),
                "E39G4_TASK_DIGEST_MISMATCH");
        require(proof.signatureBase64Url() != null
                && SIGNED.matcher(proof.signatureBase64Url()).matches(),
                "E39G4_SIGNATURE_ENCODING_INVALID");
        final byte[] signature;
        try {
            signature = Base64.getUrlDecoder().decode(proof.signatureBase64Url());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("E39G4_SIGNATURE_ENCODING_INVALID", exception);
        }
        require(signature.length == 64
                && Base64.getUrlEncoder().withoutPadding().encodeToString(signature)
                        .equals(proof.signatureBase64Url()),
                "E39G4_SIGNATURE_ENCODING_INVALID");
        try {
            Signature validator = Signature.getInstance("Ed25519");
            validator.initVerify(pinnedCandidateKey);
            validator.update(data);
            require(validator.verify(signature), "E39G4_CANDIDATE_SIGNATURE_INVALID");
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("E39G4_SIGNATURE_VERIFICATION_FAILED", e);
        }
        // Revalidate against actual task and specialist; signatures alone are
        // NEVER sufficient, nor is a client-claimed role.
        TaskEntity task = tasks.getRequired(proof.taskId());
        require(task != null && ALLOWED_STATES.contains(task.getState())
                && proof.agentId().equals(task.getActiveAgentId()),
                "E39G4_NOT_ASSIGNED_ACTIVE_TASK");
        require(proof.mode() == task.getMode(),
                "E39G4_REAL_TASK_MODE_MISMATCH");
        AgentDefinition agent = agents.getRequired(proof.agentId());
        require(agent != null && agent.id().equals(proof.agentId()),
                "E39G4_UNKNOWN_ROLE");
        // Never register consumed nonce until all validations, including the
        // independent default-off role/skill advice gate, pass.
        var requested = new GovernedSkillAdviceService.ReferenceRequest(
                proof.taskId(), proof.agentId(), proof.skillId(), boundedTaskSummary,
                proof.mode(), true);
        var reference = advice.select(requested);
        require(reference.readOnly() && !reference.modelCalled()
                && !reference.toolCalled() && !reference.approvalGranted()
                && !reference.ownerAuthenticated() && !reference.stage45Certified(),
                "E39G4_UNEXPECTED_SKILL_REFERENCE_AUTHORITY");
        require(seenNonces.add(proof.nonceHex()), "E39G4_NONCE_REPLAY_INSTANCE_DENIED");
        return new Inspection(
                "E39G4_CANDIDATE_SIGNATURE_VALID_NOT_OWNER_IDENTITY",
                sha256(data), proof.taskId(), proof.agentId(), proof.skillId(),
                reference, true, true, false, false, false, false, false, 0, 0);
    }

    /**
     * Stable canonical length-prefixed bytes. Package-private solely to test
     * cryptographic fixtures; no key generation or signing in production.
     */
    static byte[] canonicalBytes(Proof proof) {
        require(proof != null && proof.version() == 1 && PURPOSE.equals(proof.purpose())
                && proof.taskId() != null && proof.mode() != null
                && slug(proof.agentId()) && slug(proof.skillId())
                && proof.taskSha256() != null && DIGEST.matcher(proof.taskSha256()).matches()
                && proof.nonceHex() != null && NONCE.matcher(proof.nonceHex()).matches()
                && proof.issuedAt() != null && proof.expiresAt() != null,
                "E39G4_PROOF_FORMAT_OR_PURPOSE_REJECTED");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            write(out, DOMAIN);
            out.writeInt(proof.version());
            write(out, PURPOSE);
            write(out, proof.taskId().toString());
            write(out, proof.agentId());
            write(out, proof.skillId());
            write(out, proof.mode().name());
            write(out, proof.taskSha256());
            write(out, proof.nonceHex());
            out.writeLong(proof.issuedAt().getEpochSecond());
            out.writeInt(proof.issuedAt().getNano());
            out.writeLong(proof.expiresAt().getEpochSecond());
            out.writeInt(proof.expiresAt().getNano());
            out.flush();
            return bytes.toByteArray();
        } catch (Exception error) {
            throw new IllegalStateException("E39G4_CANONICAL_ENCODING_FAILED", error);
        }
    }

    private static boolean slug(String s) {
        return s != null && s.length() <= 64 && SLUG.matcher(s).matches();
    }

    private static void write(DataOutputStream out, String s) throws Exception {
        byte[] b = s.getBytes(StandardCharsets.US_ASCII);
        require(b.length > 0 && b.length <= 128, "E39G4_FIELD_TOO_LONG");
        out.writeInt(b.length);
        out.write(b);
    }

    private static String sha256(byte[] raw) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(raw));
        } catch (Exception noAlgorithm) {
            throw new IllegalStateException("E39G4_SHA256_MISSING", noAlgorithm);
        }
    }

    private static void require(boolean valid, String code) {
        if (!valid) throw new IllegalArgumentException(code);
    }
}

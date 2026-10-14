package io.aetheris.orchestrator.execution;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Stage 45.2 isolated-worker ADMISSION LAB, not an executable tool.
 *
 * A trusted issuer (NOT included here) signs owner-bound, single-use job
 * claims with Ed25519. This class holds only an externally provisioned
 * verification public key. It is not a Spring bean, API, task scheduler,
 * host-command bridge, Docker client or source of authenticated identity.
 * The caller MUST obtain TrustedContext from an independently verified
 * server-side principal, owner approval and worker identity; constructing a
 * TrustedContext from request headers would void the security boundary.
 */
public final class SignedSandboxJobGate {
    private static final int MAX_NONCES = 1024;
    private static final int MAX_ACTIVE = 32;
    private final PublicKey issuerPublicKey;
    private final Clock clock;
    private final Set<UUID> usedNonces = new HashSet<>();
    private final Set<UUID> usedJobIds = new HashSet<>();
    private final Set<UUID> activeJobs = new HashSet<>();
    private boolean stopped = false;

    public SignedSandboxJobGate(PublicKey issuerPublicKey, Clock clock) {
        this.issuerPublicKey = Objects.requireNonNull(issuerPublicKey, "issuerPublicKey");
        if (!"EdDSA".equalsIgnoreCase(issuerPublicKey.getAlgorithm())
                && !"Ed25519".equalsIgnoreCase(issuerPublicKey.getAlgorithm())) {
            throw new IllegalArgumentException("An externally managed Ed25519 public key is required");
        }
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Only this fixed, harmless CI profile is supported; no shell argv. */
    public enum Profile { SYNTHETIC_SLEEP }

    public record JobClaims(UUID jobId, UUID taskId, UUID nonce,
                            String ownerId, String projectId, String workerId,
                            String approvalGrantId, Profile profile,
                            long issuedAt, long expiresAt, int cpuMilli,
                            int memoryMiB, int processLimit) {}

    /** MUST be obtained from independently verified server-side authority. */
    public record TrustedContext(UUID taskId, String ownerId, String projectId,
                                 String workerId, String approvalGrantId) {}

    public record SignedJob(JobClaims claims, byte[] signature) {
        public SignedJob {
            Objects.requireNonNull(claims, "claims");
            signature = Objects.requireNonNull(signature, "signature").clone();
        }
        @Override public byte[] signature() { return signature.clone(); }
    }

    /** Deterministic, domain-separated, length-prefixed binary wire transcript. */
    public static byte[] canonical(JobClaims c) {
        requireValidClaims(c);
        try {
            var bytes = new ByteArrayOutputStream();
            var out = new DataOutputStream(bytes);
            out.writeUTF("aetheris:stage45:ci-only:signed-job:v1");
            out.writeUTF(c.jobId().toString());
            out.writeUTF(c.taskId().toString());
            out.writeUTF(c.nonce().toString());
            out.writeUTF(c.ownerId());
            out.writeUTF(c.projectId());
            out.writeUTF(c.workerId());
            out.writeUTF(c.approvalGrantId());
            out.writeUTF(c.profile().name());
            out.writeLong(c.issuedAt());
            out.writeLong(c.expiresAt());
            out.writeInt(c.cpuMilli());
            out.writeInt(c.memoryMiB());
            out.writeInt(c.processLimit());
            out.flush();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to encode job claims");
        }
    }

    /**
     * No automatic credential issuance, queued work or execution. Atomic
     * admission prevents STOP / nonce races *within this one lab instance*.
     * No persistence or distributed replay guarantee; fail closed on overflow.
     */
    public synchronized UUID verifyAndAdmit(SignedJob request, TrustedContext trusted) {
        if (stopped) throw new SecurityException("WORKER_STOP_ENGAGED");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(trusted, "trusted");
        JobClaims c = request.claims();
        byte[] encoded = canonical(c);
        if (!Objects.equals(trusted.taskId(), c.taskId())
                || !Objects.equals(trusted.ownerId(), c.ownerId())
                || !Objects.equals(trusted.projectId(), c.projectId())
                || !Objects.equals(trusted.workerId(), c.workerId())
                || !Objects.equals(trusted.approvalGrantId(), c.approvalGrantId())) {
            throw new SecurityException("WORKER_OWNER_SCOPE_DENIED");
        }
        long now = Instant.now(clock).getEpochSecond();
        if (c.issuedAt() > now || c.expiresAt() <= now
                || c.expiresAt() - c.issuedAt() > 60
                || c.expiresAt() <= c.issuedAt()) {
            throw new SecurityException("WORKER_LEASE_EXPIRED");
        }
        if (request.signature().length != 64) throw new SecurityException("JOB_SIGNATURE_INVALID");
        try {
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(issuerPublicKey);
            verifier.update(encoded);
            if (!verifier.verify(request.signature())) {
                throw new SecurityException("JOB_SIGNATURE_INVALID");
            }
        } catch (GeneralSecurityException ex) {
            throw new SecurityException("JOB_SIGNATURE_INVALID");
        }
        if (usedNonces.contains(c.nonce()) || usedJobIds.contains(c.jobId())) {
            throw new SecurityException("JOB_REPLAY_DENIED");
        }
        if (usedNonces.size() >= MAX_NONCES || activeJobs.size() >= MAX_ACTIVE) {
            throw new SecurityException("WORKER_CAPACITY_DENIED");
        }
        usedNonces.add(c.nonce());
        usedJobIds.add(c.jobId());
        activeJobs.add(c.jobId());
        return c.jobId();
    }

    public synchronized boolean isAdmitted(UUID id) {
        return !stopped && activeJobs.contains(id);
    }

    public synchronized void complete(UUID id) {
        if (id != null) activeJobs.remove(id);
    }

    /** One-way STOP in the lab; a real worker must additionally kill its OS process group. */
    public synchronized void engageStop() {
        stopped = true;
        activeJobs.clear();
    }

    public synchronized boolean isStopped() { return stopped; }

    private static void requireValidClaims(JobClaims c) {
        Objects.requireNonNull(c, "claims");
        Objects.requireNonNull(c.jobId(), "jobId");
        Objects.requireNonNull(c.taskId(), "taskId");
        Objects.requireNonNull(c.nonce(), "nonce");
        Objects.requireNonNull(c.profile(), "profile");
        requireId(c.ownerId());
        requireId(c.projectId());
        requireId(c.workerId());
        requireId(c.approvalGrantId());
        if (c.profile() != Profile.SYNTHETIC_SLEEP
                || c.cpuMilli() != 500 || c.memoryMiB() != 128
                || c.processLimit() != 32
                || c.expiresAt() <= c.issuedAt()
                || c.expiresAt() - c.issuedAt() > 60) {
            throw new IllegalArgumentException("Unsafe synthetic job constraints");
        }
    }

    private static void requireId(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{1,80}")) {
            throw new IllegalArgumentException("Invalid owner-bound job identifier");
        }
    }
}

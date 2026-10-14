package io.aetheris.orchestrator.execution;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/**
 * Only a CI synthetic supervisor may sign its *observed Docker state*.
 * The signature authenticates the reporter, NOT that a real-world process
 * was killed. No OS probe, Docker client, Spring bean, agent endpoint,
 * process control or production signing key is embedded in this class.
 */
public final class SyntheticOciExitReceiptLab {
    private static final String DOMAIN = "aetheris:stage45:ci-oci-exit-evidence:v1";
    private SyntheticOciExitReceiptLab() {}

    public record Observation(UUID jobId, String workerId, long stopEpoch,
                              String containerId, long observedAt,
                              int exitCode, boolean running, long runtimePid,
                              boolean noLiveProcessListing, boolean dbStopRevoked) {
        public Observation {
            Objects.requireNonNull(jobId, "jobId");
            if (workerId == null || !workerId.matches("[A-Za-z0-9_-]{1,80}")
                    || stopEpoch < 1
                    || containerId == null || !containerId.matches("[0-9a-f]{64}")
                    || observedAt <= 0)
                throw new IllegalArgumentException("Malformed synthetic OCI observation");
        }
    }

    public record SignedReceipt(Observation observation, byte[] signature) {
        public SignedReceipt {
            Objects.requireNonNull(observation, "observation");
            signature = Objects.requireNonNull(signature, "signature").clone();
        }
        @Override public byte[] signature() { return signature.clone(); }
    }

    public static byte[] canonical(Observation observation) {
        Objects.requireNonNull(observation, "observation");
        try {
            var encoded = new ByteArrayOutputStream();
            var out = new DataOutputStream(encoded);
            out.writeUTF(DOMAIN);
            out.writeUTF(observation.jobId().toString());
            out.writeUTF(observation.workerId());
            out.writeLong(observation.stopEpoch());
            out.writeUTF(observation.containerId());
            out.writeLong(observation.observedAt());
            out.writeInt(observation.exitCode());
            out.writeBoolean(observation.running());
            out.writeLong(observation.runtimePid());
            out.writeBoolean(observation.noLiveProcessListing());
            out.writeBoolean(observation.dbStopRevoked());
            out.flush();
            return encoded.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException("Unexpected observation encoding failure", impossible);
        }
    }

    /** Strict synthetic SIGKILL exit state only; do not sign a still-live task. */
    public static SignedReceipt signObservedExit(Observation observed, PrivateKey labSigner) {
        if (!validStopped(observed)) throw new SecurityException("SYNTHETIC_EXIT_NOT_PROVEN");
        try {
            var signature = Signature.getInstance("Ed25519");
            signature.initSign(Objects.requireNonNull(labSigner));
            signature.update(canonical(observed));
            return new SignedReceipt(observed, signature.sign());
        } catch (GeneralSecurityException e) {
            throw new SecurityException("SYNTHETIC_SIGNING_UNAVAILABLE", e);
        }
    }

    /**
     * Independent verifier pins reporter key, exact owner-scoped job/worker
     * and durable STOP epoch. No secret or command accepted.
     */
    public static boolean verify(SignedReceipt receipt, PublicKey pinnedReporter,
                                 UUID expectedJob, String expectedWorker, long expectedEpoch,
                                 String expectedContainerId, Clock clock) {
        if (receipt == null || pinnedReporter == null || clock == null
                || expectedJob == null || expectedWorker == null || expectedContainerId == null)
            return false;
        Observation o = receipt.observation();
        if (!validStopped(o) || !expectedJob.equals(o.jobId())
                || !expectedWorker.equals(o.workerId()) || expectedEpoch != o.stopEpoch()
                || !expectedContainerId.equals(o.containerId()))
            return false;
        long delta = Instant.now(clock).getEpochSecond() - o.observedAt();
        if (delta < 0 || delta > 300) return false;
        byte[] signatureBytes = receipt.signature();
        if (signatureBytes.length != 64) return false;
        byte[] data = canonical(o);
        try {
            Signature check = Signature.getInstance("Ed25519");
            check.initVerify(pinnedReporter);
            check.update(data);
            return check.verify(signatureBytes);
        } catch (GeneralSecurityException failed) {
            return false;
        } finally {
            Arrays.fill(data, (byte) 0);
            Arrays.fill(signatureBytes, (byte) 0);
        }
    }

    private static boolean validStopped(Observation o) {
        return o != null && !o.running() && o.runtimePid() == 0L
                && o.noLiveProcessListing() && o.dbStopRevoked()
                && o.exitCode() == 137;
    }
}

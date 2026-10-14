package io.aetheris.orchestrator.execution;

import io.aetheris.orchestrator.execution.SignedSandboxJobGate.JobClaims;
import io.aetheris.orchestrator.execution.SignedSandboxJobGate.Profile;
import io.aetheris.orchestrator.execution.SignedSandboxJobGate.SignedJob;
import io.aetheris.orchestrator.execution.SignedSandboxJobGate.TrustedContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real disposable PostgreSQL 16 tests only. Explicitly simulates rewinding
 * synthetic STOP/revocation rows via SQL, not an actual PITR/HA restore.
 * Neither source nor tests modify production records or execute agents.
 */
@EnabledOnOs(OS.LINUX)
@SpringBootTest(properties = "aetheris.memory.retention-sweep-ms=3600000")
class SyntheticStopAuditRollbackIT {
    @TempDir Path temp;
    @Autowired SandboxWorkerFenceRepository fences;
    @Autowired SandboxJobReservationRepository reservations;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;

    private record Fixture(String worker, JobClaims job, KeyPair signer,
                           DurableSandboxAdmissionLab admission,
                           SyntheticStopAuditWitnessLab audit) {}

    @BeforeEach
    void disposablePostgresOnly() {
        if (!"true".equals(System.getenv("GITHUB_ACTIONS"))
                || !"true".equals(System.getenv("AETHERIS_STAGE45_AUDIT_CI")))
            throw new SecurityException("AUDIT_SYNTHETIC_POSTGRES_CI_ONLY");
        String engine = jdbc.execute((java.sql.Connection connection) ->
                connection.getMetaData().getDatabaseProductName());
        if (!engine.contains("PostgreSQL"))
            throw new SecurityException("AUDIT_REQUIRES_DISPOSABLE_POSTGRES");
    }

    private Fixture setup() throws Exception {
        String worker = "ci_" + UUID.randomUUID().toString().replace("-", "");
        var fence = new SandboxWorkerFenceEntity(worker);
        fence.armSyntheticFixture();
        fences.saveAndFlush(fence);
        long now = Instant.now().getEpochSecond();
        var job = new JobClaims(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "syntheticOwner", "syntheticProject",
                worker, "syntheticApproval", Profile.SYNTHETIC_SLEEP,
                now - 1, now + 35, 500, 128, 32);
        var grant = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var scope = new TrustedContext(job.taskId(), job.ownerId(), job.projectId(),
                worker, job.approvalGrantId());
        var admission = new DurableSandboxAdmissionLab(
                fences, reservations, transactions, grant.getPublic(), Clock.systemUTC(),
                task -> task.equals(job.taskId()) ? scope : null);
        var signature = Signature.getInstance("Ed25519");
        signature.initSign(grant.getPrivate());
        signature.update(SignedSandboxJobGate.canonical(job));
        assertThat(admission.reserve(new SignedJob(job, signature.sign())))
                .isEqualTo(job.jobId());
        var witness = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var audit = new SyntheticStopAuditWitnessLab(fences, reservations,
                transactions, Clock.systemUTC());
        return new Fixture(worker, job, witness, admission, audit);
    }

    private record Attested(SyntheticStopAuditWitnessLab.Witness witness,
                            SyntheticStopAuditFileLab.Anchored anchor) {}

    private Attested stopAndWitness(Fixture f) throws Exception {
        assertThat(f.admission().stopWorker(f.worker()).epoch()).isEqualTo(2L);
        var witness = f.audit().attestAfterCommittedStop(
                f.worker(), f.job().jobId(), f.signer().getPrivate());
        Path store = Files.createDirectory(temp.resolve("audit-" + UUID.randomUUID()));
        return new Attested(witness, SyntheticStopAuditFileLab.appendOnce(store, witness));
    }

    @Test
    void signedCheckpointIsVerifiedAgainstAlreadyCommittedDatabaseStop() throws Exception {
        Fixture f=setup();
        var checkpoint=stopAndWitness(f);
        assertThat(SyntheticStopAuditFileLab.readAnchored(checkpoint.anchor()))
                .isEqualTo(checkpoint.witness());
        var decision=f.audit().verifyRecovery(checkpoint.witness(),
                f.signer().getPublic(),f.worker(),f.job().jobId());
        assertThat(decision.verdict())
                .isEqualTo(SyntheticStopAuditWitnessLab.Verdict.STOP_CONFIRMED);
        assertThat(decision.mustTerminate()).isTrue();
        assertThatThrownBy(() -> SyntheticStopAuditFileLab.appendOnce(
                checkpoint.anchor().recordPath().getParent(), checkpoint.witness()))
                .isInstanceOf(java.nio.file.FileAlreadyExistsException.class);
    }

    @Test
    void simulatedPostgresRollbackOfStopAndRevocationIsRejectedBySeparateWitness()
            throws Exception {
        Fixture f=setup();
        var checkpoint=stopAndWitness(f);
        assertThat(f.audit().verifyRecovery(checkpoint.witness(),
                f.signer().getPublic(),f.worker(),f.job().jobId()).verdict())
                .isEqualTo(SyntheticStopAuditWitnessLab.Verdict.STOP_CONFIRMED);

        // Simulates a restored PRE-STOP snapshot, on this test-created
        // worker/job only, in disposable CI PostgreSQL. Deliberately does
        // NOT rewrite the separately pinned out-of-DB STOP checkpoint.
        assertThat(jdbc.update("UPDATE aetheris_sandbox_worker_fence "
                + "SET stopped = false, epoch = 1 WHERE worker_id = ?",
                f.worker())).isEqualTo(1);
        assertThat(jdbc.update("UPDATE aetheris_sandbox_job_reservations "
                + "SET revoked = false WHERE job_id = ?",
                f.job().jobId())).isEqualTo(1);
        var result=f.audit().verifyRecovery(
                SyntheticStopAuditFileLab.readAnchored(checkpoint.anchor()),
                f.signer().getPublic(),f.worker(),f.job().jobId());
        assertThat(result.verdict())
                .isEqualTo(SyntheticStopAuditWitnessLab.Verdict.DATABASE_ROLLBACK_OR_REVOCATION_LOST);
        assertThat(result.mustTerminate()).isTrue();
    }

    @Test
    void missingDeletedAndCorruptedAuditFileCannotCertifySafeRecovery()
            throws Exception {
        Fixture f=setup();
        var checkpoint=stopAndWitness(f);
        Path file=checkpoint.anchor().recordPath();
        assertThatThrownBy(() -> Files.writeString(file, "forged-audit",
                StandardOpenOption.CREATE_NEW)).isInstanceOf(java.nio.file.FileAlreadyExistsException.class);
        Files.writeString(file,"corrupted",StandardOpenOption.TRUNCATE_EXISTING);
        assertThatThrownBy(() -> SyntheticStopAuditFileLab.readAnchored(
                checkpoint.anchor())).isInstanceOf(SecurityException.class);
        assertThat(f.audit().verifyRecovery(null,f.signer().getPublic(),
                f.worker(),f.job().jobId()).verdict())
                .isEqualTo(SyntheticStopAuditWitnessLab.Verdict.MISSING_WITNESS);
        Files.delete(file);
        assertThatThrownBy(() -> SyntheticStopAuditFileLab.readAnchored(
                checkpoint.anchor())).isInstanceOf(java.nio.file.NoSuchFileException.class);
    }

    @Test
    void substitutedSignatureOrDifferentJobFailsWithoutExecutionPermission()
            throws Exception {
        Fixture f=setup();
        var checkpoint=stopAndWitness(f);
        KeyPair different = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        assertThat(f.audit().verifyRecovery(checkpoint.witness(),
                different.getPublic(),f.worker(),f.job().jobId()).verdict())
                .isEqualTo(SyntheticStopAuditWitnessLab.Verdict.INVALID_WITNESS);
        assertThat(f.audit().verifyRecovery(checkpoint.witness(),
                f.signer().getPublic(),f.worker(),UUID.randomUUID()).verdict())
                .isEqualTo(SyntheticStopAuditWitnessLab.Verdict.INVALID_WITNESS);
        assertThatThrownBy(() -> SyntheticStopAuditWitnessLab.parseCanonicalText(
                SyntheticStopAuditWitnessLab.canonicalText(checkpoint.witness())
                        .replace("AETHERIS45STOPAUDITV1","AETHERIS45STOPAUDITV0")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unsignedOrUncommittedStopCanNeverReceiveAuditCheckpoint() throws Exception {
        Fixture f=setup();
        assertThatThrownBy(() -> f.audit().attestAfterCommittedStop(
                f.worker(),f.job().jobId(),f.signer().getPrivate()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("AUDIT_STOP_NOT_DURABLY_COMMITTED");
    }
}

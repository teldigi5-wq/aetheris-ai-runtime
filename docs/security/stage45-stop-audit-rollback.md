# Stage 45.5.15 — Out-of-database signed STOP witness and rollback test

**STATUS: SYNTHETIC CI LAB ONLY — RELEASE GATE CLOSED.**
This is a stacked DRAFT review on top of Stage 45.5.14 PR #61 and all
preceding unmerged Stage 43–45 security PRs. No production migration,
live data, autonomous commands, deployment, Windows-PC execution, or merge.

## Problem and implemented scope

The existing synthetic worker, signed heartbeat, local STOP tombstone, and
separate fixed-container kill broker support fail-closed cancellation, but
a **database restore to an earlier snapshot could resurrect an older worker
STOP epoch or a previously revoked reservation** if the database is trusted
as the only historical authority. This slice models one narrow defense:

1. `SyntheticStopAuditWitnessLab` is a plain Java, **NON-EXECUTING**
   class (not a Spring bean/controller/tool/worker). Only after a read-only
   transaction sees a **committed STOP fence** AND a revoked, correctly
   scoped reservation does it produce a canonical, domain-separated,
   Ed25519-signed STOP witness. It binds exact worker ID, job UUID,
   STOP epoch, original reservation epoch and timestamp. The
   cryptographic signer is an ephemeral CI-only key.
2. A disposable test-only `SyntheticStopAuditFileLab` creates a
   **single-create, private (`0600`) signed witness file outside the
   PostgreSQL database**, synchronizes it to disk, and pins its SHA-256
   digest separately in the test controller. Rewriting or truncating the
   file, replacing the signing key, tampering with the job/worker, or
   deleting the witness fails verification rather than yielding
   continued authorization. This is ONE witnessed STOP checkpoint,
   **not yet a multi-event append-only hash-chain service**.
3. The read-only recovery verifier checks both the independent
   witnessed STOP signature and the CURRENT database fence/reservation.
   A missing/invalid witness, missing database, rolled-back STOP epoch,
   restored `stopped=false`, restored `revoked=false`, inconsistent
   worker identity or lost reservation all **DENY**. The successful
   result `STOP_CONFIRMED` also requires termination: it does NOT
   permit a restart.
4. Tests simulate an earlier database snapshot by altering ONLY
   synthetic STOP/epoch/revocation rows in a disposable PostgreSQL 16
   GitHub Actions service **after** a correctly witnessed STOP.
   They check combined rewind, STOP epoch-only rewind and reservation
   revocation-only rewind. None of these tests accesses real data.
   They do **NOT** perform or certify a real PostgreSQL PITR/backup restore.

Other tests verify that an uncommitted stop cannot receive a witness,
that signed and anchored checkpoints survive ordinary reads, that
different signing keys or jobs fail, and that damaged/deleted audit
evidence cannot silently reauthorize work. Protocol canonicalization
tests run in the complete Java regression.

## Security boundary and limitations

- **Not a truly independent external witness.** The signer and
  file/digest anchor are controlled by the disposable CI JVM and same
  host. A privileged host can compromise the signer, rewrite both the
  audit file and its anchor, or restore the entire CI machine to an
  earlier snapshot. The Ed25519 signature protects against simple
  offline tampering only when its public-key identity is separately
  trusted, and the file digest only protects when the anchor survives.
- **Not an append-only multi-event audit ledger**: this stage stores a
  single write-once record as a first proof of signed STOP evidence.
  Persistent independently governed audit ingestion, immutable/retention
  storage, hash-chain validation, key rotation, recovery quorum, and
  revocation high-water marks are future production gates.
- **Not transactional with PostgreSQL STOP.** The witness is signed
  AFTER DB commit. A host crash before it is written leaves the audit
  absent, which the recovery verifier treats as denial rather than
  relying on an unaudited restored DB. There is no atomic distributed
  transaction spanning PostgreSQL and file storage.
- **Not integrated into active admissions or agents.** The witness
  verifier has no runtime tool, Spring service registration, STOP
  dispatcher or command-enabling path. It is only called by explicit
  CI fault-injection tests. Real active admission still requires a
  separately deployed, authenticated and integrated recovery authority.
- No independent VM/KMS/TPM key protection, rootless/microVM host
  isolation, trusted OS exit evidence, physical Windows/WSL test,
  adversarial multitenant concurrency, database failover/PITR
  certification, or owner approval integration.

## Next gates

Build an **independently deployed** append-only audit sink protected from
the controller/worker trust domain, with signed monotonic revocation
epochs, secure checkpoint distribution and rollback-resistant anchoring.
Prove full PostgreSQL backup/restore and failover behavior in a separate
isolated environment, reconcile lost witnesses without ever rearming
a worker, and carry these decisions into the owner-authorized runtime
supervisor. Then proceed with multi-agent permissions/quotas, Windows
workstation tests, owner-controlled voice/desktop operations, and an
independent security review before any production pilot.

Keep security changes on stacked **OPEN DRAFT** PRs. Command execution
stays default OFF; no deploy or merge is authorized by this CI lab.

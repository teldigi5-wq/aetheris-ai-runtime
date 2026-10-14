# Stage 45.4 — Durable signed job replay and STOP persistence laboratory

**Status: DRAFT, lab only; no operational agent execution, public routes or
deployment.** This is stacked on unmerged Stage 45.3 PR #46 and previous
Stage 43–45 security reviews.

## Implemented database boundary

A new `DurableSandboxAdmissionLab` verifies Stage 45.2's canonical
Ed25519 signature using an externally supplied **public verification
key** and separately resolved test-only `TrustedContext` obtained by
the injected authoritative provider. It is a **plain Java class, NOT
registered as a Spring service**, task tool, worker or endpoint.

Once signature and clock checks pass, a Spring `TransactionTemplate`
opens a database transaction, locks an existing worker STOP fence with
JPA `PESSIMISTIC_WRITE`, rechecks the signed lease and independently
resolved owner/project/task/worker/approval context, and inserts
an immutable synthetic reservation with its job UUID, global nonce,
worker, owner/project, approval, expiration and current STOP epoch.

The new `SandboxJobReservationEntity` has an immutable primary
`job_id` and a **unique nonce database constraint**. Duplicate
reservations are denied, including across independently instantiated
coordinator objects. Tombstones are retained even after STOP. There
is NO token/key/raw command, prompt, memory payload or user document
in these records.

`SandboxWorkerFenceEntity` defaults to **STOPPED** for a newly
constructed row. No missing-row auto-bootstrap and no release/rearm
method is provided by the ledger. Synthetic tests must explicitly
create and arm a disposable worker fixture. Admission against a missing
or stopped fence fails closed.

`stopWorker(workerId)` serializes against reservation using the **same
worker row lock**, increments the worker STOP epoch once, sets
`stopped=true` permanently, and bulk-revokes existing reservations.
Repeated STOP remains idempotent. The return value ALWAYS says
`STOP_PERSISTED_OS_TERMINATION_UNVERIFIED` because this operation
DOES NOT kill processes or contact a worker. It is never safe to
interpret a DB STOP record as proof of operating-system termination.
`reservationVisible()` is an advisory DB read only, NOT a race-free
launch permission or active-process status.

## Verification

- H2/Spring Data JPA adversarial tests create synthetic Ed25519
  keys and owners in test fixtures only. Cases cover changed
  coordinator objects (simulated JVM restart while keeping DB),
  replay of identical job IDs/nonces, duplicate nonce with fresh
  job ID, wrong owner, forged signer, missing/default-stopped
  fences, multi-user/multi-worker separation, revocation across
  coordinator instances, no STOP release and no OS-kill claim.
- A separate GitHub Actions job provisions an **ephemeral PostgreSQL
  16** service and runs the same JPA integration tests against that
  backend, **not real user records**. Hibernate creates/drops test
  tables in the disposable DB. The job does not access or connect
  a live PostgreSQL server.
- Complete orchestrator Java regression remains mandatory.

## Restrictions and known gaps

- **Not a durable standalone runner:** no jobs execute, no broker or
  signed result receipts exist. The Stage 45.3 fake backend does
  not use these tables. There is no transport linking a worker
  to the ledger or the Stage 45.2 OCI STOP test.
- **Crash/restart simulation is limited:** newly constructed Java
  service instances reuse the same running test DB. No real
  database shutdown/restore of encrypted backups is certified,
  nor stale-worker reaper, automatic reconciliation or tenant
  revocation propagation.
- A transaction lock and unique constraint reduce races, but a
  further integration test must check true multi-node concurrent
  admission versus STOP with real authenticated workers. There
  is no guarantee a process isn't already executing when STOP
  persists. A real worker needs continuous fencing on the STOP
  epoch, external kill and a receipt proving OS process exit.
- **The trusted authority callback is a test seam** and can be
  insecure if implemented as an echo of client input. Production
  must use durable approval records, owner authentication,
  worker attestation and task-policy lookup not sourced from LLM
  prompts or request headers. The issuer key must be configured
  and rotated by an independent trusted service, not supplied
  by untrusted clients.
- The application currently uses Hibernate test `create-drop`
  for disposable databases. This is **NOT** a reviewed production
  SQL migration. JPA `ddl-auto=update` must NOT be relied on for
  irreversible security/schema controls or production deployment.
- No retention pruning of replay records is allowed by this
  implementation. In a real system durable DB backups, failover,
  stale replicas and restore rollbacks can themselves cause
  replay; global monotonic epochs/external consensus needed.
- Read-only denial does not prevent privileged DB operators from
  modifying rows; production needs independent DB permissions,
  audited recovery and separate key storage.
- Windows/WSL, container escape, output integrity, rootless VM
  worker, STOP process-group teardown and secure network egress
  remain completely uncertified.

**Release gate CLOSED:** do not merge protected branches, deploy,
change production configs, enable ProcessBuilder or Docker access
to Syntra, start real tasks, handle owner data, or provide hardware
permissions under this draft.

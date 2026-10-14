# Stage 45.5.9 — Host-side STOP supervisor, stale fence and synthetic exit proof

**STATUS: CI-only security laboratory / OPEN DRAFT / DO NOT DEPLOY.**
Stacked on Stage 45.5.8 draft PR #55 and earlier unmerged security changes.

## Security gap addressed

The preceding non-root OCI guard polls short signed leases, but its owner or
publisher can be disconnected, it can be paused or fail, and a short STOP
polling cadence cannot itself prove host process termination. A separate
host-side control plane must be able to enforce cancellation even when a
workload refuses to cooperate.

This slice adds a plain Java, **NON-EXECUTING**
`SyntheticHostStopFenceLab`. It is not a Spring service/endpoint, runtime
agent tool, Docker adapter, background scheduler, or user-visible STOP API.
It checks a **trusted, test-only pinned job ID, worker ID and expected
epoch** against persistent JPA state independently of any worker self-report.

The host evaluator returns a **mandatory termination** decision if the
worker fence is stopped/missing, an epoch is stale, reservation is
missing/revoked/owned by another worker, the lease has expired, or
the database transaction fails/unavailable. It does not trust an
unresponsive worker's assertion of health. An active decision is
`ACTIVE_SNAPSHOT_NOT_EXECUTION_AUTH` — explicitly **not permission to
start a real process**. A transactional DB read is not a monotonic
distributed stop guarantee.

## Actual host enforcement in disposable CI

An explicit Linux CI-only `SyntheticHostStopSupervisorIT` test
contains ALL Docker CLI invocation code; no corresponding process
execution is added to production Java main sources.

The test creates synthetic Ed25519-signed owner/project/task/worker-
bound reservations in **disposable PostgreSQL 16**, then launches the
fixed, digest-pinned BusyBox non-root OCI profile with no network,
read-only filesystem/workspace, caps dropped, no-new-privileges,
128 MiB memory, 0.5 CPU, PID cap 32 and no mounted Docker socket.
The fixed workload starts harmless nested sleep children and
**does not implement any STOP polling**.

While this workload remains running, a separate test thread
independently polls the durable host-side evaluator. A database STOP
is committed in another thread. The host observer notices the
committed STOP and explicitly issues `docker kill --signal=KILL` for
the fixed, identity-bound container, even though the workload never
cooperatively observed a signed STOP. Docker's observed stopped state,
PID 0, exit code 137 and failure of `docker top` after termination
provide independent CI-test assertions about the container namespace.

The test reuses `SyntheticOciExitReceiptLab` to have an ephemeral
**host observer** Ed25519-sign that limited observation with exact
job UUID, worker, container ID and committed STOP epoch. A separate
verification call pins the observer key; wrong job/epoch fails. This
signature authenticates the CI observer's assertion. It is **NOT**
cryptographic kernel attestation or a production-grade proof of all
descendant host process exits.

A second real Docker test deliberately supplies a stale expected
epoch while the DB has not yet stopped the job: the observer must
terminate that fixed workload too. No false successful STOP receipt
is signed for a state without durable DB STOP.

Additional H2/Spring JPA adversarial tests cover missing worker,
cross-worker reservation, stale epoch, revocation, expiration and
unavailable DB (the result never becomes an implicit KEEP grant).

## Serious limitations

- **Not a separately privileged production supervisor.** Although
  the host observer runs outside the isolated container, the actual
  test harness and DB controller are on the SAME ephemeral CI host,
  with access to its privileged Docker daemon. There is no separately
  hardened service identity, rootless runtime, VM boundary, KMS
  signing principal, process attestation authority or remote mutual
  authentication. A compromised CI host can forge the synthetic
  observer key or observations.
- **No automatic deployment of cancellation.** There is no
  `TaskControlService` integration, production STOP scheduler,
  owner approval issuance, secure worker heartbeat protocol,
  durable signed exit audit storage, or multiple-worker kill broker.
- **No guaranteed reaction time/availability:** the explicit test
  polls briefly and issues a synchronous Docker kill. Real systems
  need bounded, highly available independent supervisors with
  idempotent retry, crash/failover reconciliation, and a denylist of
  stale launch tokens. A broken/unavailable Docker daemon might
  prevent kill even though DB policy correctly says MUST_TERMINATE.
- **No atomic launch/STOP across host and database:** a STOP can
  race with a process launching and the worker may temporarily run
  until the host observer acts. Neither JPA snapshots nor signed
  synthetic reports close that race in production.
- **No robustness against privileged host rollback or database
  PITR:** local `launch.once`/STOP markers and signed STOP epochs
  are not protected against privileged deletion or DB restore.
- The fixed workload is only synthetic sleep. No general
  untrusted commands, model-generated code, local browser control,
  cybersecurity tooling, finance/trading actions or real user data.
- No physical Windows/WSL, non-Linux service, or real-machine
  credential/permission certification has taken place.

## Remaining release gates

Implement an independently deployed and audited rootless/microVM host
supervisor, an authenticated owner's narrowly scoped job issuer,
revocation epochs that survive DB failover/restore, mandatory host
liveness/kill guarantees, secure signed exit receipts held in external
audit storage, multitenant stress testing, Windows PC validation and
independent security review.

Until then keep **all Stage 45 PRs as OPEN DRAFT**, protected branches
unchanged and real Syntra command execution/default host access OFF.

# Stage 45.5.10 — Separate authenticated host watchdog and synthetic heartbeat recovery

**STATUS: DRAFT / DISPOSABLE CI LAB / PRODUCTION GATE CLOSED.**
Stacked on Stage 45.5.9 PR #56 and the preceding unmerged security PRs.

## New independent process

`tests/sandbox/stage45_host_watchdog.go` compiles to a **separate,
standalone Go process on a disposable Linux GitHub Actions host**.
Unlike the previous Java host-policy object, it executes separately
from the synthetic Spring/PostgreSQL heartbeat publisher. It has
only a fixed synthetic action: verify one known digest-pinned OCI
container identity and, on STOP or loss of trusted permission,
execute the exact `docker kill --signal=KILL <pinned-container-id>`
operation with a short timeout. It does not accept arbitrary Docker
arguments, agent commands, user scripts, images or API requests.

It verifies Ed25519 heartbeats against a pinned issuer PUBLIC key.
The private signing key exists only in the ephemeral CI publisher
JVM. Signed frames bind protocol domain, worker identity, job UUID,
**actual 64-character immutable Docker container ID**, STOP epoch,
monotonic sequence, issued/expiry time and ACTIVE/STOP state.

- An ACTIVE heartbeat requires the originally pinned worker epoch.
- An authenticated STOP must carry a **higher** epoch.
- Signature failure, unexpected identity, decreasing sequence,
  stale/future timestamps, malformed data, missing files or expiry
  all fail closed and trigger a **host-enforced** termination attempt.
- The Go process uses a fixed total watch budget and bounded Docker
  operations. Successful STOP is separately observed through actual
  Docker stopped state / PID namespace, not merely a Java boolean.

## Database-backed heartbeat publisher

`SyntheticSupervisorPulseLab` is a **test-only, manually constructed,
non-Spring component**. It reads and locks the JPA worker STOP fence,
validates the immutable synthetic reservation/worker/epoch, and
persists a monotonic heartbeat sequence to a new
`SyntheticSupervisorPulseEntity` in disposable PostgreSQL.
A new ACTIVE heartbeat requires an unrevoked nonexpired job and
nonstopped matching epoch. A STOP frame requires a previously
committed STOP epoch **and revoked job**, then becomes terminal:
it cannot issue ACTIVE again from the same synthetic pulse record.

After a DB heartbeat transaction commits, the test publisher
Ed25519-signs a short, bounded lease. This is NOT atomic with
publishing it to a local file. Failure between DB commit and
publication causes expiry and fail-closed host termination.

## Actual testing

New Go adversarial tests: trusted signed heartbeat/STOP, wrong issuer,
cross-worker/job/container, expired, tampered, future and replayed
sequence rejection.

H2 / Spring JPA tests: monotonic durable sequence, committed STOP
required, irreversible STOP pulse, wrong-worker and epoch rejection.

Explicit Java synthetic integration tests on real disposable
PostgreSQL 16 + Docker:

1. Launch a fixed harmless non-root, no-network, read-only OCI sleep
   tree that **does not poll STOP**. Send a signed ACTIVE heartbeat,
   run a separate Go watchdog process, commit DB STOP and publish
   the higher-epoch signed STOP. The watchdog itself invokes
   `docker kill` and confirms actual Docker stopped state/PID zero.
   An ephemeral host observer test key signs the limited synthetic
   STOP observation, verified against job/worker/epoch/container.
2. Launch another uncooperative fixed workload, send initial signed
   ACTIVE heartbeat and then **stop publishing**. The watchdog
   enforces lease expiry by killing the container while the DB
   reservation remains active. No STOP receipt is signed in this
   scenario because an actual DB STOP was NOT committed.

Full Java regression is a separate CI gate.

## Crucial production blockers

- This is a **separate process, not an independently protected service
  identity or machine**. The CI controller, file transport, watchdog
  and Docker daemon all run on the same disposable host. A privileged
  host or compromised heartbeat issuer can forge signed activity,
  kill/replace the watchdog or roll back its files.
- The OCI workload is non-root, but its **host Docker daemon remains
  privileged**. There is no rootless daemon, microVM, production
  cgroup/LSM attestation, mutual-authenticated remote RPC,
  supervisor systemd service, external KMS, automatic restart
  supervisor or reliable multi-host quorum.
- **Not truly durable watchdog anti-replay.** Sequence is persisted in
  a synthetic DB row, but Go's most recent sequence is process-local.
  A watchdog process restart without an independent durable high-water
  mark could accept an older unexpired signed heartbeat.
- STOP is observed asynchronously; signing and writing a heartbeat
  file is not atomic with DB STOP. Termination deadlines are bounded
  in this CI lab, but lack production service-availability guarantees,
  HA retry and worst-case fault injection.
- A missing heartbeat intentionally **kills even when the DB job
  remains active**: this is fail-closed liveness enforcement, not
  revocation or an audited successful STOP.
- The test observer's Ed25519 signature only authenticates its
  own Docker observation; it is NOT kernel/hardware proof or an
  attestation that any arbitrary host process tree died.
- No production TaskControlService event hook, real owner approval,
  general RUN operation, filesystem access, app opening, autonomous
  cybersecurity tools, trading, Windows/WSL hardware integration,
  live database migration or deployment exists.

## Next gates

Protect the watchdog under a dedicated non-admin OS principal or
microVM; establish host-to-controller mutually authenticated
transport, durable local monotonic heartbeat high-water marks,
externally audited signed process-exit receipts, atomic worker
registration/launch fencing, watcher self-monitoring and bounded
kill retry on Docker daemon failure, multi-tenant concurrency,
rollback/failover tests, Windows-PC verification and independent
security review. Until those pass **all security PRs remain
unmerged drafts** with actual agent command execution OFF.

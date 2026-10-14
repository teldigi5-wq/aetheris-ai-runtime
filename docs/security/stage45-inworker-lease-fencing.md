# Stage 45.5.6 — Signed lease verification inside a non-root OCI worker

**STATUS: CI-only laboratory, default-off, draft. No production execution or protected merge.**
Stacks on draft Stage 45.5.5 PR #52 and the preceding unmerged Stage 43–45 reviews.

## What materially changed

Unlike the preceding stage's worker-side *policy object in the test JVM*,
this stage compiles a tiny **static Go binary** and actually launches it as
PID 1 **inside the fixed, digest-pinned BusyBox OCI container**, under
UID/GID 65534 with network disabled, read-only image and mounted fixture,
all capabilities dropped, no-new-privileges, IPC isolation, CPU 0.5,
128 MiB memory, PID 32 and noexec temporary memory filesystem.

The Go binary contains **only the fixed synthetic sleep profile**, with
no generic command strings, model-supplied scripts, plugins, job imports,
network client, owner data or production secrets. It knows only an
Ed25519 verification public key, a signed lease, a fixed worker identifier,
synthetic job UUID and minimum STOP epoch. The private issuer key never
enters the container. The Java `InWorkerSignedLeaseOciPlan` only builds
immutable OCI arguments; it does not launch processes in main source.

The in-container worker verifies, before launching any fixed child
process: a canonical, length-bounded Ed25519 signed lease with exactly
the job, worker, fixed profile, initial STOP epoch, ALLOW state and
a very short expiry. The worker re-reads and verifies the signed permit
**every 100 ms**. A signed STOP at a higher epoch triggers fixed
process-group SIGKILL and worker exit; missing, altered, expired or
inconsistent permissions likewise terminate the lab workload.

The disposable CI test harness constructs a synthetic grant and reserves
the task in actual PostgreSQL 16 via the existing Ed25519/JPA admission
gate. It checks against the previously implemented worker-side verifier,
signs the initial ALLOW lease, and starts the OCI fixture. It also
proves a tampered lease is rejected **inside the OCI container before
fixed child work begins**. After synthetic database STOP is committed,
the test publishes the higher-epoch signed STOP by **atomic file
replacement**. The container observes it and exits voluntarily; the
host then independently inspects Docker to confirm stopped container
PID and expected code. No host-issued Docker SIGKILL is needed on the
successful cooperative-STOP path. The test's teardown retains a forced
Docker cleanup fallback solely for disposable CI safety.

Go unit tests test real Ed25519 verification, mismatched worker/job,
invalid STOP epoch, tampering, expired leases and unauthorized epoch
transitions. Java fixed-plan tests check read-only mount, pinned image,
sandbox options, and symlink/mount delimiters. Complete orchestrator
Java regression is also mandatory.

## Security boundaries and important caveats

- **Not a production worker.** The CI host creates signed leases from
  synthetic PostgreSQL records and copies a static binary into a
  **read-only** container fixture. There is no production authority,
  authentication provider, independent issuer, owner-session
  validation, or networked agent worker. The CI host Docker daemon
  remains privileged and controls the workload.
- **Not direct database verification inside the worker.** The worker
  authenticates the lease publisher's signature and scope, not the
  underlying database itself. The publisher is trusted to sign a
  higher STOP epoch **only after DB commit**. A compromised publisher
  can lie; trust is not fully independent.
- **Not truly continuous authorization.** A 100 ms poll and a short
  expiry are bounded response mechanisms, not an atomic guarantee.
  There is a check-to-start race, polling delay and a risk from rollback
  of an old, still-valid signed ALLOW file. The worker does not keep a
  durable STOP tombstone across restarts. Do not use this as a safety
  boundary for malicious, long-running real tasks.
- **No cryptographic kernel attestation.** The test independently reads
  actual Docker stopped/PID/exit state and verifies child exit. Those
  are CI observations, not a hardware-rooted or signed OS attestation.
  Prior Stage 45.5.5 synthetic signed reports remain test evidence,
  not a production report of kernel truths.
- **No host application control**: the workload only runs a fixed
  harmless nested sleep process tree. No shell supplied by agents,
  ability to open desktop applications, browse, edit user files,
  run cyber tools, trade or execute generated code.
- **Not rootless daemon/microVM security:** inside the container the
  guard runs as unprivileged user, but the Docker daemon on the hosted
  Linux runner is still privileged. Independent LSM/seccomp review,
  host-service isolation, output provenance and kernel escape testing
  remain absent.
- **No Windows/WSL or production PostgreSQL migration.** CI runs in
  a disposable hosted Linux amd64 runner; the database is ephemeral
  and automatically dropped. No owner-PC or real records touched.

## Next production gates

Build a separately operated, non-administrator, VM/rootless worker
with protected key and identity provision; an authoritative,
transactional, non-replayable job claim and STOP epoch channel checked
*atomically* at launch and continuously during work; durable local
revocation tombstones; signed process-exit observation by an external
OS supervisor rather than the workload; accountable output staging;
adversarial multitenant concurrency, persistent crash/restore recovery,
Windows PC certification and an independent security assessment.

**Keep raw host command execution disabled by default. No merge,
deployment, paid infrastructure, user data, secrets or production job
scheduling is authorized by this laboratory slice.**

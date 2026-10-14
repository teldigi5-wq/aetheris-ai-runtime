# Stage 45.5.12 — UID-separated verifier and fixed-target kill broker

**Review-only CI laboratory. Release gate CLOSED. No production worker or arbitrary commands.**
This stage stacks on the verified, unmerged Stage 45.5.11 draft PR #58.

## Trust separation being tested

Previous stages ran a single host watchdog with both an Ed25519 heartbeat
verifier and host Docker control. This stage introduces **two separately
launched Linux CI processes** on one disposable GitHub Actions host.

**Unprivileged verification process (UID 65534):**
- Runs the static Go `stage45_unprivileged_watch_verifier.go` binary with
  no Docker CLI code, no Docker socket mount, and no container-control
  interface. It only reads a narrowly fixed synthetic job's Ed25519
  issuer-signed heartbeat and per-worker local high-water journal.
- Has access to a synthetic private Ed25519 IPC-signing key, provisioned
  for the CI user only, and a pinned publisher public key. Owner/task,
  immutable OCI ID, lease expiry, STOP epoch and sequence are checked.
- On a valid higher-epoch STOP, missing/expired heartbeat or detected
  replay/corrupt journal, sends one narrowly scoped, signed STOP
  request over a **local Unix domain socket**. It cannot choose an
  executable or container ID through model/tool/user input.

**Host kill broker (CI hosted-runner OS account):**
- Runs `stage45_fixed_kill_broker.go` in a *separate process* with the
  hosted runner's Docker CLI authority, not the non-root verifier's.
- Pins one exact 64-byte-hex Docker container ID, synthetic worker,
  job UUID and original STOP epoch at startup; checks actual Docker
  inspect ID and PID before kill.
- Accepts only a domain-separated Ed25519-signed STOP transcript with
  current timestamp and fixed scope from the pinned verifier signing
  public key, **AND** validates Linux `SO_PEERCRED` peer UID 65534.
  Its Unix socket is group-restricted for the CI verifier. There is
  **no** network/TCP API, general Docker command, RUN operation, or
  ability to retarget an unrelated container.
- Forces a bounded, fixed `docker kill --signal=KILL` then inspects
  the pinned container ID for stopped PID namespace. If the verifier
  fails/disconnects, a fixed broker deadman deadline also attempts
  termination. Docker failures never count as verified termination.

The heartbeat issuer remains the disposable Spring/JPA implementation
from PR #57, using ephemeral Ed25519 signing keys and a real disposable
PostgreSQL 16 service. It cannot issue ACTIVE once the database STOP is
committed; heartbeat loss is treated as reason to terminate, not proof
of a database STOP.

## Tests

Dedicated Go tests assert signature, exact worker/job/container and epoch,
protocol framing, expiry, anti-replay and durable high-water state.
The new CI-only Java integration tests run real, fixed-profile OCI
non-root synthetic sleep workloads and verify two separate scenarios:

1. Host Java publisher persists STOP and revokes reservation in PostgreSQL,
   signs STOP with a higher epoch; UID 65534 verifier authenticates it;
   host broker verifies the peer identity + signed request, kills only
   the pinned OCI container, and confirms Docker exit state.
2. Java publisher stops renewing signed ACTIVE heartbeat while the
   DB job remains active. The unprivileged verifier requests STOP on
   expiry, and broker kills its pinned OCI fixture. No false DB STOP
   acknowledgement is asserted.

Full Java orchestrator regression is a separate mandatory CI job.

## Limitations and blocked deployment

- Separation is **two UIDs on the SAME privileged CI host**, not a
  hypervisor/microVM security boundary. The broker has Docker group
  power, effectively root-like access. A compromised broker or host
  remains able to circumvent the policy.
- The verifier private IPC signing key is test-provisioned on the
  local host. It has no KMS/TPM protection. Any privileged host
  account can extract it or impersonate the verifier; no production
  secure user/OS identity is being claimed.
- The Unix socket and journal are in a disposable per-job filesystem.
  There is no permanent external append-only log, trusted host
  anti-rollback device or consensus/quorum for STOP epochs.
- A deadman deadline is a fail-closed *attempt*. A killed broker,
  unavailable Docker daemon, locked kernel or failed cgroup can
  prevent actual termination; no guaranteed upper bound for real
  host process-tree death has been proven.
- The broker's role is **fixed-container kill only**. No arbitrary
  shell, desktop control, browser, repository write, autonomous
  cybersecurity action, trading or generated code execution.
- No Windows/WSL physical test, owner approval identity, runtime
  TaskControlService STOP bridge, production credential migration,
  cross-host authentication or independent security certification.
- The OCI host Docker daemon is privileged although the OCI workload
  is nonroot with fixed digest, network disabled and read-only mounts.
  Test observations are not signed kernel/hardware attestation.

**Leave all security PRs OPEN DRAFT, production execution default OFF,
no deployment, no merge, no live owner data.**

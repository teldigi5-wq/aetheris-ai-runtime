# Stage 45.5.1 — Separate-JVM, authenticated local STOP transport (DRAFT)

**Review gate CLOSED. No production worker, agent execution or deployment.**
Stacked on draft PR #47 and the earlier unmerged Stage 43–45 work.

## What is actually implemented

This slice introduces a **separate Java process**, started only in a
synthetic Linux GitHub Actions test, which listens on a Unix-domain
socket inside a private `0700` directory. It has exactly one possible
operation: **STOP**. There is no RUN, command, filesystem tool, model
request, container launch, arbitrary path/URL input, network listener,
Spring registration, main-orchestrator integration or installed service.

The `SandboxWorkerStopWire` protocol authenticates a fixed, bounded
request using the trusted controller's pinned Ed25519 **public key**.
Requests are domain separated, canonically encoded, capped at 512
bytes, and contain only a nonce, target worker ID, issued/expiry times
and stop epoch. The worker denies unrecognized senders, tampering,
wrong audiences, expired/future or long leases, and invalid frames.
It is not possible to provide an arbitrary shell command or plugin.

After validating the request, the worker creates a local
`STOP_LATCHED` file with `CREATE_NEW`, `0600` permissions and
`FileChannel.force(true)` before responding. It signs a bound receipt
using the worker's test-only private Ed25519 key. The controller
verifies the pinned worker public key and nonce/worker/epoch bindings
rather than believing an untrusted response.

A worker that has already persisted STOP **refuses to restart into
an accepting state**. The synthetic integration test starts another
JVM process and verifies wrong-key STOP denial, expired/misaddressed
requests, valid signed STOP acknowledgement, marker persistence
after the process exits and refusal to restart with the marker.

Keys are generated for CI and passed only through private synthetic
files, never through process arguments or environment variables.
No production secrets are provisioned. Unix socket access and the
signed protocol provide independent layers of defense, but a local
process running under the same OS user may still access test key files.
Production requires a separate restricted OS identity / VM boundary.

## Trust and scope limitations

- This is **authenticated STOP IPC only**, not an executable,
  OS-confined worker. The separate JVM is not run inside a VM,
  rootless container or a separate OS identity. It has no workload
  to terminate, so the receipt truthfully says
  `STOP_FENCE_PERSISTED_NO_OS_TASK`.
- No linkage exists between this file-based STOP latch and Stage 45.4's
  PostgreSQL STOP epoch or real `TaskControlService`. A malicious
  worker or crashed process could withhold acknowledgement. A signed
  receipt shows only the test worker persisted its own marker;
  it is NOT proof a process tree exited.
- The `CI` environment gates are not authentication. They make
  accidental use less likely but environment flags can be forged.
  No production secrets should ever be used with this binary.
- No secure peer-UID check, socket access-control audit, live
  authenticated user identity, trusted issuer/approval lookup,
  challenge-response freshness, durable nonce replay ledger shared
  with a worker, client retry policy, bounded worker connection
  timeouts, OS sandbox, worker attestation, key rotation or tamper-
  resistant disk state is certified here.
- `FileChannel.force(true)` and marker presence are an initial
  local recovery check, not a crash-consistent HA or durable DB
  guarantee. No database transaction spans STOP IPC.
- No physical Windows/WSL tests; Unix-domain sockets are tested only
  on GitHub-hosted Ubuntu with Java 21. No general host command
  execution was enabled.

## Next engineering gates

1. Dedicated rootless/VM-isolated worker running under distinct OS
   credentials, with socket owner/permissions checked by the host
   and a tightly restricted mounted workspace.
2. Signed job admission bound to independently verified owner,
   project and approval, plus Stage45.4 monotonic durable DB STOP
   epoch checked by the worker itself before/while running.
3. Authenticated transport hardened against DoS, partial frames,
   disconnected clients, persistent replay, stale key rotation,
   malicious output and restart races.
4. Real process-tree kill with signed and independently verified exit
   receipt; whole-system STOP propagation from TaskControlService.
5. Adversarial full lifecycle CI on an isolated Linux worker and
   eventually Windows/WSL hardware; independent security review.

**No merge, deployment, OS worker activation, real task scheduling or
production data use is authorized by this proof-of-concept.**

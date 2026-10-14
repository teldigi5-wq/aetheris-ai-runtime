# Stage 45.5.13 — Fixed OCI broker identity and failure handling (CI lab)

**DRAFT / SYNTHETIC CI ONLY / DO NOT DEPLOY.** Stacked on the Stage 45.5.12 draft PR #59. No user data, unrestricted agent commands, physical Windows PC interaction, deployment, or merge.

## Implemented in this review slice

The existing separately running host broker still has only one capability:
`docker kill --signal=KILL <fixed synthetic immutable container ID>`.
It cannot start workloads, choose arbitrary images, execute user or
model-supplied commands, inspect host files, or retarget the configured
worker based on an untrusted heartbeat or IPC message. The host broker
remains the only process with access to the privileged CI Docker daemon.

New `stage45_broker_recovery.go` strengthens the CI termination path:
- Inspect and parse the exact 64-digit immutable Docker container ID,
  running flag, host PID and exit code. Reject malformed output,
  identity substitution, contradictory stopped/PID fields and missing PID.
- Repeat the inspection before the fixed kill; if the container identity
  or observed PID changed during these checks, **do not issue kill**.
- Use only an invariant Docker subcommand with the already pinned full
  container ID. Require an independently observed stopped PID 0
  **and exit code 137** before reporting that termination was observed.
- Bound inspection and post-kill polling. If the Docker daemon fails,
  reports misleading state, becomes unreachable or fails to kill,
  return an **error / termination unverified**, never silently report
  success. Test-injectable Docker calls are limited to this CI-only
  module; runtime agent execution adapters remain unchanged/default off.

New Go adversarial tests simulate a switched container ID, a changing
host PID, unavailable Docker inspect, failed kill, still-running worker,
already-stopped worker, malformed daemon state and a valid positive path.
These tests do NOT claim to simulate all host-kernel or Docker exploits.

The disposable real Docker + PostgreSQL 16 test additionally starts the
fixed broker **without starting the unprivileged verifier**. The broker's
bounded deadman expires, forcibly terminates its exact synthetic OCI
sleep container and confirms Docker exit without pretending a
database STOP was committed. Existing signed-STOP and missing-heartbeat
tests remain mandatory, along with the full Java regression.

## Remaining major security risks

- **Inspect/KILL is still not fully atomic**: a hostile Docker daemon,
  privileged operator or shared host can lie, race inspect/kills or
  replace a running process. Rechecking ID and PID narrows accidental
  substitution but is NOT a kernel-enforced lifetime handle.
- **Docker group == root-equivalent privilege** on the CI host.
  A compromised broker or host remains outside this protection.
  There is no independently authenticated microVM supervisor, rootless
  daemon, seccomp/LSM attestation, TPM-backed signing, or immutable
  trusted host audit.
- **Daemon downtime cannot guarantee termination**. Failure is
  accurately reported as UNVERIFIED, but restoring the daemon,
  host reboot reconciliation, remote kill retry, and monitoring an
  unavailable broker all need separate fault-injection and independent
  service supervision.
- A signed STOP report is limited to the CI observer's claim about
  Docker state, **not hardware/kernel attestation** of arbitrary host
  process descendants.
- **No database rollback/failover certification**, cross-host revocation
  quorum, production owner identity, TaskControlService STOP bridge,
  real agent command execution, Windows/WSL physical laptop tests,
  user data, or deployment.

## Subsequent gates

Stage 45.5.14: Protected signing-key custody, isolated broker identity
and hardened verifier/broker communication. Stage 45.5.15: external
append-only security audit and rollback tests. Later: real owner
approvals and active agent STOP integration, Windows/WSL certification,
voice/desktop operations, and security pilot sign-off.

**Keep this and all preceding PRs OPEN DRAFT; release gate CLOSED.**

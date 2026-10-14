# Stage 45.5.11 — Watchdog restart/replay fence (synthetic CI only)

**REVIEW GATE CLOSED.** This is a draft extension of Stage 45.5.10 PR #57,
building on all prior unmerged Stage 43–45 hardening branches. It performs
no production agent commands, workstation operations, deployment or merge.

## Problem addressed

Stage 45.5.10 gave an independently running Go host watchdog an authenticated
Ed25519 heartbeat, but it kept its most recently accepted sequence number
**only in memory**. A watchdog crash/restart could forget that sequence and
accept the *same old, still-unexpired signed ACTIVE heartbeat*. The prior
host watchdog was also co-located with the CI publisher and privileged host.

## Actual change

A standalone Go `stage45_host_watch_state.go` implements a narrowly scoped,
per-container **host-local heartbeat high-water journal**:

- Persisted entry binds version, worker ID, job UUID, immutable 64-char
  container ID, original worker epoch, Ed25519 public-key fingerprint,
  strictly increasing heartbeat sequence and ACTIVE/STOP state.
- The watchdog runs with **one exclusively locked state directory**. A
  competing watchdog is treated as a failure and cannot grant continued
  activity. It will fail closed and attempt fixed-container termination.
- On accepting a NEW authenticated heartbeat, Go writes a private
  `0600` staging file via exclusive creation, synchronizes the file,
  atomically renames it into place and synchronizes the parent directory
  before treating the heartbeat as accepted.
- On restart, a saved high-water sequence **requires a strictly higher
  signed sequence**. Replaying the latest old, still-valid heartbeat
  fails closed; a previously saved terminal STOP state also fails closed.
- Missing state directory, unsafe permissions, symlink, corrupt/mismatched
  state, interrupted staging file, unavailable signing key or concurrent
  directory lock causes a fixed-container termination attempt. A failed
  Docker kill is reported as **TERMINATION_UNVERIFIED**, never success.
- The watchdog continues to allow the *same* heartbeat during a single
  process lifetime only until its short signed expiry, while preserving
  a maximum synthetic runtime budget. Missing publisher or expired
  heartbeat still forces termination.

The **only execution operation** remains `docker kill --signal=KILL`
against one identity-verified immutable synthetic container ID, in the
CI-only Go executable. No Spring agent execution adapter, general
shell, command input, Docker socket exposed inside OCI, real user data,
production signer, or worker scheduling is introduced.

## Synthetic verification

New Go tests cover persistent sequence across reconstructed state,
terminal STOP persistence, wrong worker/job/container/key fingerprint,
corruption, symlinks, interrupted writes, concurrent locking and unsafe
state-directory permissions.

New real Docker + disposable PostgreSQL 16 cases, in addition to the
prior signed STOP and absent-publisher cases:

1. Start a non-root OCI fixed sleep container and a separate Go watchdog.
   After an initial authenticated ACTIVE heartbeat is journaled, forcibly
   kill **only the watchdog process** without stopping the container.
   A restarted Go watchdog receives the same still-valid signed ACTIVE
   but must **reject its replay and kill the container**.
2. Provide a deliberately corrupted on-disk heartbeat journal before
   starting the watchdog; it must refuse to trust that state and
   terminate the still-running synthetic container.

Independent Spring JPA heartbeat issuer policy and the complete Java
regression remain mandatory CI gates.

## Important trust limitations

**This is NOT rollback-proof security.** The state journal is on the same
disposable CI host as the Docker daemon and controller. Privileged host
users or malware can erase, replace or roll back the journal. `fsync`
improves ordinary crash persistence, not protection against storage
rollback, full-machine compromise or database point-in-time restore.

**This is NOT a dedicated locked-down OS principal.** The separate Go
process still shares the host's account and privileged Docker-daemon
access with the Java publisher. Granting a less-privileged user Docker
socket access would effectively recreate root power, so **no false
OS-identity isolation is claimed**. Before production, separate the
privileged kill broker, non-privileged signature verifier and issuer
by authenticated, strictly scoped interfaces, under independent OS
identities or VMs.

**No absolute execution containment or STOP latency guarantee.**
The watchdog requires a functioning Docker daemon to kill the worker;
if that daemon stalls or the host crashes, the best it can do is report
failure. The signed heartbeat is a short lease, not an atomic database
launch transaction. The worker can run until the watcher notices
STOP/lease expiry; forced-kill observation is synthetic CI Docker
evidence, not TPM/kernel attestation of every possible host child.

**No production key custody, unique host attestation, multi-host quorum,
Windows/WSL test, real owner approval, arbitrary agent command, stable
external event log or production migration exists in this PR.**

## Next engineering gate

Separate unprivileged verification from privileged kill broker using
mutually authenticated narrow IPC, independently protected supervisor
identity, replay-resistant durable journal outside the app/worker
trust domain, external append-only exit records, bounded retry/backoff
and worst-case fault injection (host daemon failure, DB restore and
concurrent STOP/launch). Require owner-PC tests and independent
security review before any actual autonomous execution.

Keep all security PRs **OPEN DRAFT**, command execution default-OFF,
production release gate **CLOSED**.

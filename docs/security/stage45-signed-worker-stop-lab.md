# Stage 45.2 — Signed Worker Admission and Process-Tree STOP Lab

**Status: DRAFT, synthetic tests ONLY. Production agent execution remains DISABLED.**
This PR depends on Stage 45.1 draft PR #44 and the unmerged preceding
Stage 43/44 security review stack. It does not merge, deploy, grant access
to the host, start a real user workload, or change runtime configuration.

## Authenticated job-request design probe

`SignedSandboxJobGate` is a **non-Spring, non-executing in-memory gate**.
It verifies a **server-signed Ed25519** job request using an externally
supplied public key; no secret signing key is embedded. Every signed
transcript contains domain/version separation, immutable job/task IDs,
single-use nonce, owner, project, target worker, previously approved
grant identifier, the exact fixed `SYNTHETIC_SLEEP` test profile, a
short issued-at/expiry lease and locked CPU/memory/PID limits.

The gate separately compares the signature-bound values to a
`TrustedContext` supplied by its caller. THIS CONTEXT IS **NOT** an
authentication mechanism: a production caller would need to obtain it
from a server-validated principal, authoritative task/project DB
membership, an owner-approval ledger and an authenticated worker
identity. In this lab it is created solely by unit-test fixtures.
Passing headers, prompts, model output or other caller-supplied values
as `TrustedContext` would be insecure and is prohibited.

The gate rejects forged signatures, wrong worker/owner/project/task/
approval, expired or not-yet-valid grants, leases exceeding 60 s,
wrong command profile/quotas, reused nonce or job ID, and exhausted
bounded in-memory replay capacity. STOP is an irreversible latch within
the instance: it revokes all active admissions and refuses further
admissions, but **does not itself terminate OS processes**. No tokens
are issued, no approvals are created, no shell command or image is
chosen by the model, and there is no public endpoint.

**Not certified:** durable cross-process replay protection, broker
revocation, client private-key custody, incident audit, clock skew
policies, credential rotation, crash recovery, distributed STOP or
tenant isolation on deployed hardware.

## Independent OS process-tree STOP check

`tests/sandbox/stage45_worker_stop_probe.sh` is a **fixed, CI-only**
Linux Docker test with a pinned BusyBox digest and an intentionally
harmless workload: a parent shell creates several nested sleeping
processes inside a separate PID namespace. The test first proves that
the child process tree actually exists, then applies a container-level
`docker kill --signal=KILL`. It checks container state is stopped,
PID namespace has exited, no live container process listing remains
and no disposable read-only workspace file changed. Network access,
mount writes, capabilities, privileges, IPC, memory, CPU and process
count are restricted, as in Stage 45.1. The host secret is synthetic
and is not passed into the container.

The **Java admission gate and the container STOP probe are separate,
independently validated components**. There is **NO authenticated
transport or production orchestration linking them** in this PR.
A true execution worker would be a separate least-privileged service,
preferably confined within a disposable VM/microVM with rootless OCI.
Do not grant Docker daemon access to Syntra, Aetheris, model prompts,
plugins or web clients. A Docker rootful daemon is a privileged
control plane, even when its launched workload is unprivileged.

## Next certification gates

1. Privileged trusted issuer on a protected service, short-lived
   audience-bound worker identity and a server-verified approval
   grant with an immutable owner/task/project association.
2. Durable, shared anti-replay and revoke/STOP epochs, restricted
   message transport, signature and worker-key rotation, and logs
   that reveal no tokens, prompts, files or secrets.
3. A dedicated separate sandbox worker that **verifies admission
   itself** and runs a fixed profile only through OS-enforced isolation.
   Require zero Docker socket or host credentials inside workloads.
4. Integrate STOP atomically with task cancellation and broker job
   admission. Verify process-tree cancellation, disconnection,
   crash/restart, race conditions, queued jobs and stale approvals.
5. Adversarial owner-A/owner-B integration tests, timeouts, fork bombs,
   output redaction, signed artifacts and safe output staging.
6. Independent Windows/WSL owner-PC validation, kernel escape review,
   externally provisioned secrets and rollback/recovery exercises.

**Decision:** Keep `CommandSandboxService` raw subprocess opt-in
`aetheris.execution.command-execution-enabled=false` by default.
Do not make this lab a generally accessible command-execution API.
The owner retains control; no security system can guarantee
zero breaches from passing CI alone.

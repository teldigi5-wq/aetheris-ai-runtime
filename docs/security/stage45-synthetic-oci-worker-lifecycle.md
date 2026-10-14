# Stage 45.5.4 — Signed, constrained OCI synthetic worker lifecycle (CI LAB)

**Status: DRAFT / NOT PRODUCTION / NO AGENT COMMAND EXECUTION.**
Stacked on draft PR #50 and preceding unmerged Stage 43–45 security PRs.

## Actual implementation and test evidence

`SyntheticOciSleepProfilePlan` constructs an immutable, fixed-image OCI
profile for a single **synthetic sleep** fixture. It does **not** execute
the plan, register a Spring service, call Docker, open an API, or accept
model-selected commands, user paths, image tags, capabilities or flags.
The image is digest-pinned to the Stage 45.1 laboratory BusyBox fixture.

`SyntheticOciWorkerLifecycleIT` is an **explicit CI-only** Maven test
(not included in the normal `mvn test` lifecycle). On a disposable,
GitHub-hosted Linux runner with a temporary PostgreSQL 16 service, it:

1. Creates synthetic identities, a temporarily armed test-only worker
   fence and an Ed25519 key pair. Wrong-owner signed claims are denied;
   the database must contain no reservation for such a claim.
2. Accepts only an independently resolved **test-fixture** grant with
   matching task, owner, project, worker and approval. Authenticates its
   signature and persists a unique job ID/nonce with a worker STOP
   epoch. Duplicate admission fails.
3. **In test code only**, launches the preselected OCI fixture after a
   reservation exists and is currently visible. It runs with no
   network access, read-only root/workspace, all Linux capabilities
   dropped, no-new-privileges, nonroot UID/GID 65534, IPC isolation,
   128 MiB memory, 0.5 CPU, PID cap 32 and nonexecuting tmpfs.
   The container has no Docker socket, agent secrets or writable
   host paths. The only task is a harmless, fixed sleep process tree.
4. Verifies ready state and child process presence. Persists database
   STOP **before** issuing `docker kill --signal=KILL`.
   Confirms that the reservation is revoked, no subsequent grant can
   be admitted and Docker reports no running container PID namespace.
   Ensures the protected fixture was not overwritten.

The disposable CI host's Docker daemon is privileged, and the Java
test harness uses `ProcessBuilder` to invoke **fixed Docker CLI
arguments**. These methods are in test sources ONLY, not available
through the Syntra runtime/agent service. No Docker socket is
mounted inside the fixture container.

## Trust limitations — DO NOT SHIP

- **No independently authenticating worker:** the controller runs the
  database signature/owner/fence check *before* Docker starts.
  The separate container does not verify the signed grant or fetch
  the monotonic STOP epoch itself. A STOP between this check and
  actual launch creates a TOCTOU race. This is NOT an end-to-end
  authorized production worker despite successful real OCI tests.
- **No automatically connected STOP path:** the CI harness explicitly
  calls persistent DB STOP, then Docker kill. No TaskControlService
  subscription, independent worker supervisor, durable cancellation
  broker, or external cryptographically signed OS process exit receipt
  exists yet. The test observes container process namespace exit
  directly, which is useful test evidence but is NOT a signed
  production attestation.
- **No rootless or microVM confinement:** this uses a privileged
  Docker daemon on a disposable GitHub Actions runner. Kernel
  escape, daemon compromise and compromised host remain outside
  the isolation guarantees; containers are not a general security
  boundary for arbitrary malicious agent code.
- **No general execution capability:** only a harmless synthetic
  sleep operation is represented. No arbitrary shell, Python,
  browsing, application-opening, workspace modifications, plugins,
  model downloads, trading, scanning, or cybersecurity operations.
- **No genuine owner authentication/approval issuer:** all claims,
  keys and approvals are test-only fixtures. Production identity
  security, least-privilege approvals, key storage/rotation, audit,
  quota scheduling and tenant isolation are still absent.
- **No cross-process durable STOP verification inside the workload:**
  an independent, versioned authenticated worker-to-controller
  transport and monotonic persisted fence must be validated at
  launch, periodically during work and before outputs are accepted.
- **No Windows PC/WSL test, HA PostgreSQL, crash recovery, signed
  output artifacts, denied egress audit, security review or deployment.**

## Next gate

A separately deployed non-admin, rootless OCI or disposable VM worker
with authenticated transport, independently verified owner-bound
job/STOP epoch, no Docker socket access from the main orchestrator,
atomic job-claim/revocation semantics, reliable process-tree STOP and
signed exit evidence, bounded output staging, adversarial multi-owner
concurrency and physical Windows-PC evaluation.

Until then **leave host command execution default-off and all PRs
OPEN DRAFT**. No protected merge, deployment, production credentials
or live owner memory is authorized by this CI-only milestone.

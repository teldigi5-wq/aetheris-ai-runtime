# Stage 45.5.16 — Multi-agent owner scope, atomic quotas and STOP (CI lab)

**OPEN DRAFT — SYNTHETIC ONLY — PRODUCTION RELEASE CLOSED.**
Stacked on Stage 45.5.15 draft PR #62 and earlier unmerged reviews.
Nothing in this stage starts a process, connects to an agent tool, grants
desktop privileges, deploys, merges or modifies real user data.

## What this actually implements

Three new model-layer components store a **per-owner/project shared fixed
resource budget** and a **single-use agent-to-job claim** in a disposable
JPA/PostgreSQL database:

- `SyntheticAgentQuotaEntity` and row-locking repository. A pre-created
  owner/project budget is required (missing budgets DENY); no untrusted
  request may create a new budget. Fixed synthetic maximum: 2 outstanding
  jobs, 1000 CPU milli, 256 MiB, 64 PIDs. One job exclusively reserves
  500 CPU milli, 128 MiB, 32 PIDs; resource quantities are not chosen by
  agents. The same owner/project's research, cybersecurity and design
  agents share the ledger, so one agent cannot spend resources twice.
- `SyntheticAgentClaimEntity` records immutable agent ID, job UUID,
  owner/project/worker/approval and admitted worker STOP epoch. Claims
  are single-use and are not transferred to a different agent.
- `SyntheticMultiAgentAuthorityLab` is a **non-Spring, non-executing**
  explicit synthetic admission/check class. It verifies an Ed25519
  signed fixed-profile job with the existing independent
  `WorkerSideFenceVerifierLab`; then under a PostgreSQL transaction
  locks the worker STOP row **before** locking the shared budget.
  Every supplied agent ID must agree with a **separately sourced
  authoritative task→agent grant**. Durable reservation owner, project,
  worker, nonce, task, approval, expiration and STOP epoch must match.
  Quota and immutable agent claim are committed as one transaction.
- A provided emergency-STOP signal (which can be bound explicitly to
  `TaskControlService::isEmergencyStopActive` in tests) blocks new
  claims. A permanent scoped STOP latches the quota row; existing
  recorded jobs return **MUST_TERMINATE** on reconciliation. A stopped
  worker, revived/revoked grant, missing DB record, failed authority,
  expired permission or unavailable STOP signal also demands
  termination.

**Important:** `CHECK_ONLY_NOT_LAUNCH_AUTHORITY` does NOT authorize
actual executable work. This new class is not a Spring bean, API,
TaskControl listener or trusted host worker. Existing production
agent execution and command access are not enabled by this PR.
Scope STOP **does not claim to have killed a process**, and volatile
global STOP **does not automatically write a scope tombstone**; tests
call the explicit functions.

## Resource reservation is conservative

Once a synthetic job has claimed a budget, the ledger **does NOT
release resources on cancellation, expiry, or a STOP request**. That
would be dangerous without independently verified process-tree
termination and a cryptographically bound completion receipt. No
release API is offered in this stage. Thus a stopped/stale job can
retain budget until future reviewed recovery with independently
attested termination. This is safer than silently overbooking.

## Test matrix

H2/JPA tests:
- Two different agents for the same owner/project succeed; a third
  is refused. The fixed CPU/memory/PID and concurrent-job limits
  remain unchanged.
- Cross-agent identity substitution and invalid Ed25519 job signatures
  never reserve resources. Duplicate admission never charges twice.
- Removing authoritative task→agent grant demands termination for
  the previously reserved job.
- Scoped STOP remains latched and charges remain reserved. Worker
  STOP or an existing TaskControl emergency flag deny any new work;
  reconciliation refuses continued work.
- Each test uses synthetic IDs, keys and disposable in-memory data.

Separate **PostgreSQL 16 hosted CI job** tests simultaneous competing
agent claims against one row-locked budget and confirms the final
ledger never exceeds 2 jobs/1000 CPU milli/256 MiB/64 PIDs, despite
concurrent admissions. A transaction failure counts as denial, not
permission to run. Complete 533+ Java regression remains mandatory.

## Blocking requirements before any actual agent use

- The authoritative task→agent mapping in tests is a trusted
  Java map, **not real authenticated owner approval storage**.
  The volatile STOP probe is not an automatic cross-service
  cancellation subscription.
- There is **no host broker integration** to forcibly terminate
  real concurrent child processes, no cryptographically verified
  completion/release, and no secure durable global STOP quorum.
- Quotas are **logical reservation limits**, not independently
  enforced OS cgroups for arbitrary agents; the previous fixed
  nonroot OCI sleep profile is the only actual CI process workload.
- The earlier STOP-audit checkpoint still lives in a disposable CI
  trust domain, not externally immutable storage. Broker identity
  and signing keys remain unprotected against compromised privileged
  host/kernel/daemon. No actual PostgreSQL PITR/HA test.
- No Windows/WSL physical machine validation, voice wake phrase,
  real desktop commands, multitenant production identity,
  privilege escalation testing or autonomous agent execution.
- No real data, live integration, protected merge, billing or deployment.

Next: Stage 45.5.17 should prepare Windows/WSL workstation install and
permissions **without activating agent OS commands**. Then Stage
45.5.18 owner-approved voice/desktop operation prototypes, and Stage
45.5.19 complete end-to-end threat review and controlled pilot gate.

Keep all security PRs OPEN DRAFT; actual command execution remains OFF.

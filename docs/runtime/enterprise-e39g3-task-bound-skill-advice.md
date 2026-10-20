# E3.9G-3 — Java task-bound skill reference service (internal only)

**Status:** DRAFT engineering slice, base [#156](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/156)
exact commit `b50f0f112cf96f267a54e919cc648b5d31ea3fe4`.
Do not merge to protected main, activate an owner PC, claim Stage45 GO,
access external websites, or authorize live-trading/production actions.

## What this adds that E3.9G-2 did not

A genuine **Java Spring-managed `@Service`** in the orchestrator:
`io.aetheris.orchestrator.skills.GovernedSkillAdviceService`.

Unlike the Python-only manual skill selector, this class accesses the
**existing persistent `TaskService`** and **real `AgentCatalogService`**:
- Exact task UUID MUST exist via `tasks.getRequired(taskId)`.
- Existing active specialist MUST match the requested agent ID byte-for-byte.
- Task state MUST be `RUNNING` or `VERIFYING` and operation mode MUST match.
- Requested role and skill MUST match a frozen seven-role scope mapping
  validated independently against the Python policy and all fourteen pinned
  native/vendor skill references.
- Existing agent catalog MUST have 26 distinct configured identities,
  13 real legacy divisions, and correct division for each of seven mapped roles.
- `aetheris.governed-skills.reference-enabled` **defaults to false**.
  The caller additionally must supply `explicitReferenceRequest=true`.
- The result is a **read-only 180-character maximum vetted summary**, source
  path, trust tier, task+role+skill metadata and unsigned SHA256 integrity
  receipt, with `modelCallCount=0` and `toolCallCount=0`.
- Text of third-party skill files is **never loaded into the Java service**.
  Vetted summaries are treated as untrusted advice/reference, not commands.

Cross-language policy resource:
`orchestrator-service/src/main/resources/enterprise/skill-advice-bindings-v1.json`.
`tools/enterprise/e39g3_validate_java_skill_manifest.py verify` compares
exact role-to-skill lists, source paths, source trust tiers, immutable vendor
revision, read-only schema, and canonical workforce against the previously
validated, integrity-checked registry. Windows and Ubuntu tests mutate policy
copies to verify denial of forged roles, skill entries and JSON duplicates.

## Security truth / non-goals

**This is service-level task *binding*, not human authentication.**
A user-provided task UUID and agent ID do not prove a principal's identity.
Therefore this stage deliberately **has no HTTP controller or external
interface** and cannot be used by untrusted clients. There is no model
invocation, prompt assembly or skill-body execution; it does not yet build
websites or run penetration/security scans. The service is an internal
reference gate only. The unsigned SHA256 is NOT a signature, lease,
approval, owner proof, immutable audit ledger or security principal.

`TaskState.RUNNING` proves the persisted task assignment, not owner
consent for additional privileged actions. This new service does not widen
the existing agent `allowedTools` list and does not reuse existing
`ToolExecutionAuthorityService` for reference selection (because no tool
is called).

Default off is intentional. Do not set the enable flag on production without
a future genuinely authenticated caller and tested deterministic policy
boundaries. A later separate PR should add a trusted internal caller,
authenticated/leased principal context, resource-scoped outputs and
independent provenance. Never use raw user-supplied `agentId` as proof.

## Verification

New `GovernedSkillAdviceServiceTest` tests actual canonical 26-agent fixture,
opt-in, task/agent/mode/state mismatch, cross-role denial, high-risk financial
and red-team roles, prompt-injection as data, and no tool invocation.
New CI also runs existing full Java tests, Windows/Ubuntu verified source
digest and role/skill parity tests, E3.9F model refusal tests,
the network-disabled original site build and real Chromium UI QA.

A passing Java unit test proves task policy under controlled test doubles,
**not external authenticated access or successful model-generated websites**.

## What is next

E3.9G-4: tested, authenticated, explicitly authorized internal workflow
to assemble low-trust skill context *without any tool authority*, with
signed/bounded task leases and actual model output review, then independent
real website + app security benchmarks.
E3.9F: separately complete actual external withheld multi-domain real-model
benchmarks; do not promote Granite4/Hermes beyond current evidence.

Remaining broad roadmap: E3.9F/E3.9G-4 and E4–E9, plus independent Stage45.
These are high-level milestones, not exact PR/day promises.

# Aetheris E3.9G-5 — persistent deny-only candidate skill proof nonce ledger

**Draft engineering proof — not production owner authorization, and not Stage45 GO.**
Stacked directly on [PR #158](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/158)
with its latest successful exact head `08add5196a704b566d135730f435cdd618bf6ed0`.

## Engineering scope

The previous `CandidateSkillProofPreflight` used a per-object in-memory
`ConcurrentHashMap.newKeySet()` nonce set. That correctly refused the same
proof in a single verifier instance, but was **not** safe across new
verifier objects, restarts or multiple workers connected to a shared DB.

This slice adds a **separate database-global 128-bit nonce table**
`enterprise_skill_candidate_proof_nonces`:
- `SkillProofNonceEntity`: JPA entity with `nonce_hex` as a **global primary key**,
  immutable task UUID, agent ID, skill ID, SHA256 signed proof digest,
  first observed UTC instant, proof expiry and explicitly deny-only evidence state.
- `SkillProofNonceRepository`: internal Spring Data evidence lookup only.
- `DurableSkillProofInspectionLedger`: real Spring `@Service`, real
  `@Transactional` method `inspectAndRecord(proof,taskSummary,pinnedVerifier)`.
  This calls the actual pinned-key Ed25519 proof verifier from PR #158,
  including persisted task/agent/mode/skill checking, before any insert.
  It **never** accepts caller-manufactured `Inspection` as signature proof.
- A new global nonce is `EntityManager.persist` + `flush`, **not**
  `JpaRepository.save`, which could merge an assigned primary key.
  Race arbitration is the database unique primary-key constraint;
  a collision propagates an exception and rolls back.
- The receipt has `databaseNonceRecorded=true` after flush, but
  `humanOwnerAuthenticated=false`, `ownerApprovalGranted=false`,
  `durableActionReplayCertified=false`,
  `toolExecutionAuthorized=false`, `stage45Released=false`,
  `modelCalls=0`, `toolCalls=0`.

## Tested acceptance contract

The new Spring Boot H2 test executes **real JPA transactions**, not an
in-memory Java-only fake ledger:
- Legitimately signed candidate proof creates exactly one database row
  that is explicitly *not* approval.
- A **fresh verifier** with a new in-memory set cannot add the same nonce
  to the same database.
- Different real task UUID with an independently signed proof cannot
  reuse that database-global nonce.
- Wrong candidate signing key, altered task digest, unauthorized security
  skill, inactive task state all reject **without** database insert.
- Two independently constructed candidate verifiers on different JVM
  threads race for the same nonce; exactly one database insertion succeeds.
  Even the successful result grants no privileged authority.
- Run on Windows Server 2022 and Ubuntu 24.04, together with complete
  Java orchestrator regression, existing security/policy checks and
  independent original-site real Chromium QA.

## Critical limitations

This is **database-backed duplicate rejection for signed-candidate
proof *inspections***. Unlike E3.9G-4's in-memory set it can reject across
new verifier instances sharing the same database. It does **not** establish
a registered human principal, sign a production authorization, or provide
durable *action-approval* replay certification. It is not a database-failover,
real PostgreSQL, multi-node or crash-recovery certification. H2 CI proves
the JPA primary-key behavior but **real PostgreSQL and migrations are
still outstanding**.

The `pinnedVerifier` must be constructed by a separately trusted caller
with an independently pinned key. A malicious caller can supply their own
candidate key, so **this code must not be reachable from untrusted clients
or interpreted as an identity check**. No endpoint is added. No models,
browser actions, new tools, GitHub writes by the application, owner PC,
live trading or production release occurs.

Do not purge or recycle nonce rows within any possible proof acceptance
or replay horizon; a future shared-backend retention/migration policy
must be reviewed before deployment.

## Next work

1. Independent human principal enrollment/identity proof and task-specific
   consent in a separately authenticated channel, with live token/key
   revocation and auditing.
2. PostgreSQL integration under a pinned CI database image, restart/failover
   and transactional replay tests, reviewed schema migration and retention.
3. A constrained **model** advisory consumer with typed low-trust context,
   no tool privileges, real prompt-injection refusals and proof of exactly
   which skill text/citation was used.
4. Real independently selected website and defensive-security benchmark
   tasks with Chromium, accessibility, 3D motion/performance, security QA.
5. Remaining E3.9F genuine externally held-out model repair scores and
   later E4–E9 plus separate Stage45 certification.

**No claims of autonomous website building, operational 100 divisions,
cryptographically authenticated human owner, or real model improvement.**

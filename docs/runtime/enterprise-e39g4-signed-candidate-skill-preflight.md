# E3.9G-4 — candidate-signed task/skill preflight (NOT human auth)

**Status: draft engineering proof only.** Stacked on [PR #157](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/157)
at exact base `231934f632c77245fe25c613675d71e60207227b`.
Protected main remains unchanged. Stage45 NO_GO, zero paid-model/owner-PC/live-trading operations.

## Why this specific gate

The existing `GovernedSkillAdviceService` verified persistent task, 26-agent
catalog, exact role/skill mapping and operation mode, but an internal task
assignment alone never authenticated a real human owner. The existing E2.2D
`EnterpriseOfflineKeyTrust` explicitly resolves **candidate keys**, not
owner principals, and cannot be silently promoted to authorization.

This slice adds `CandidateSkillProofPreflight`, a **non-Spring,
internal-only** verifier requiring a candidate Ed25519 public key
independently pinned by a trusted caller, not provided in the proof.

The scope is domain-separated, length-prefixed and signed: version,
fixed purpose `READ_ONLY_SKILL_REFERENCE_PREFLIGHT`, exact task UUID,
agent ID, skill ID, claimed operation mode, SHA256 task summary,
128-bit nonce, issued-at and expires-at. A proof is valid for at most
300 seconds, only when within its exact issued/expiry window. Real JDK
Ed25519 verification runs before any task/skill selection.

A verified signature **does not** authorize an action. The independent
persistent `TaskService` must confirm task RUNNING/VERIFYING, its
actual active specialist and exact mode. The existing
`GovernedSkillAdviceService` (default OFF, with explicit request)
must separately approve the *read-only reference*, and the returned
metadata must attest **zero model/tool calls** and **no approval**.

Once all validations pass, a per-instance in-memory nonce set rejects
second reuse. This is explicitly **NOT transactionally durable across
restart, multiple pods, or other instances**, and is not suitable for
privileged workflows. A second instance is deliberately allowed to
reinspect the same proof in tests, while reporting
`durableReplayCertified=false`. There is no key provisioning flow
and no authorization HTTP route. No positive permissions are conferred.

The `Inspection` record hardcodes false for
`actualOwnerAuthenticated`, `ownerApprovalGranted`,
`executionAuthorized`, `durableReplayCertified`,
`productionReleased` and zero model/tool calls. The passed signature
shows only possession of a **candidate** signing key.

## Tests

- Real ephemeral Ed25519 JDK keypairs generated **inside test code only**.
  No production private signing keys or fixtures in the repo.
- A good signed 300-second-or-less scoped candidate proof may retrieve
  the existing reference-only metadata when the local advisory flag
  is deliberately enabled by the caller.
- Rogue public keys, substituted skills, payload tampering, expired
  and not-yet-valid proofs, excess lifetimes, invalid nonce/signature
  encodings, absent key and altered task summary are refused.
- Actual durable task state/agent/mode mismatch, cross-role requests
  and default-off advisor are refused even with valid signature.
- Per-instance replay rejects reuse; separate instance remains
  explicitly not durable-certified.
- Dedicated CI runs genuine Ed25519 tests on **Windows and Ubuntu**,
  full Java 21 orchestrator regression, independent registry/policy
  checks, real Chromium original website, and zero-HTTP/no-activation
  static guard.

## Next blockers and scope honesty

**NOT implemented:** enrollment/verification of a real human owner,
authenticated user/tenant identity, root-pinned operator authorization
configuration, a durable transactionally replay-safe approval ledger for
skills, private model inference, tool execution, autonomous website
building, externally curated sealed test corpus or Stage45 certification.

The only workflow available remains the **read-only, internal,
explicitly enabled skill-reference gate**. It should not be exposed to
external clients or treated as authorization for privileged work.

Next: a separately reviewed trusted identity channel + principal-to-task
lease assignment, durable unique-nonce ledger and a bounded
model-consumer that treats all skill text as data. Independently test
website/security outcomes in realistic held-out scenarios before
claiming productivity or autonomy.

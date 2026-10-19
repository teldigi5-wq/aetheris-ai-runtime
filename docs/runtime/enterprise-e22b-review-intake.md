# E2.2B-1 — Quarantined Durable Design-Review Intake

**OPEN DRAFT · NO VERIFIED OWNER PRINCIPAL · NO POSITIVE APPROVAL · NO AGENT EXECUTION · NO PUBLIC HTTP API**

## What was implemented

A real Spring JPA/H2-testable **review-intent persistence and denial-audit
foundation**, separate from the model-produced E2.1 advisory packet and
the E2.2A SVG/JSON design artifacts.

The internal `EnterpriseDesignReviewIntakeService`:

- Calls the independent E2.2A SVG and manifest verifier before accepting
  an artifact.
- Stores a **15-minute, UUID-scoped immutable design review intent** in
  `enterprise_design_review_intents` with project/company/division,
  normalized brief fingerprint and exact SVG/manifest SHA-256 digests.
- Assigns only `PENDING_OWNER_AUTHENTICATION_UNAVAILABLE`. There is
  **no APPROVED/CONSUMED state transition**, and no JWT, approval token,
  capability lease or execution key exists in this slice.
- On each `tryAuthorize(intentId, artifact)` call, checks expiry,
  artifact validity, digest and project/company/division scope.
  Regardless of the match, it always returns
  `actionAuthorized=false`, `trustedOwnerVerified=false`,
  `approvalConsumed=false`, `executionReleased=false`.
- Persists each denial reason and timestamp in
  `enterprise_design_review_denials` transactionally.
  Denial rows are operational diagnostic evidence; **they are not
  tamper-evident or cryptographically authenticated owner audit logs**.
- Has **no HTTP endpoint** and never starts a model, shell, tool,
  browser, deployment, financial trade or device operation.

These features make unauthorized execution impossible through the
implemented service. They **do not** demonstrate a functioning owner
approval mechanism, safe production DB migration, authorized reviewer
identity, or one-time positive-approval replay defense.

## Real tests

`EnterpriseDesignReviewIntakeServiceTest` exercises real H2-backed
Spring transactions and database records rather than mocked
persistence, including:

1. Persisted, exact-scope intent with a fixed 900-second expiry.
2. Correct artifact still denied and denial audited.
3. Repeated attempts always denied, with per-attempt audit records.
4. Wrong company/project/artifact hashes rejected.
5. Forged owner approval in JSON rejected, even if digests are recomputed.
6. Expired review refused before authorization checks.
7. Missing artifacts refused with denial evidence.
8. Malformed and unknown request IDs refused.
9. Malformed unverified source artifacts rejected before database insert.

Run on Windows and Linux together with E0–E2.2A regression.
All Java tests and source no-activation checks are CI-gated.

## Engineering debt and gates

- **Production database:** The JPA model is a schema contract only.
  No production Flyway/Liquibase migration has been supplied or
  applied. The required PostgreSQL DDL, rollback/backup plan, SQL
  permissions and load/concurrency tests remain future work.
- **Identity:** We have not verified a trustworthy Syntra desktop
  owner-authentication/session assertion in this repository stack.
  Accepting a caller-provided "I'm the owner" claim would be unsafe.
- **Approvals:** A new verified principal integration, explicit
  authorization policy, durable one-use nonce, atomic consume and
  permission-segregated execution adapter must be implemented and
  independently tested before positive decisions become possible.
- **Audit:** Append-only at the application level does not equal
  tamper-evident or WORM storage. Secure custody and principal-bound
  signatures still require review.
- **Security certification:** The Stage45 policy sources are absent
  on this E2.2A-derived branch. Its 13 release blockers remain
  binding; the production/device/owner-PC release is still **NO_GO**.
- **Autonomy:** Even a good design artifact, saved intent and denied
  review cannot certify that autonomous agents completed a website.

## Dependency chain and next increment

E0 PR #102 → E1 PR #104 → E2.1 PR #106 → E2.2A PR #107
→ this draft E2.2B-1.

**E2.2B-2 next:** Integrate a real owner identity verifier from the
platform after separate proof of its cryptographic trust boundary;
introduce PostgreSQL migrations; implement atomic, one-time,
action-scoped approval consumption and rejection of racing/replayed
tokens; retain production NO_GO until host/Stage45 certification.

No merge into protected `main` is authorized by these tests.

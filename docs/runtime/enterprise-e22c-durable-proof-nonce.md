# Enterprise E2.2B-2C — Durable signed-review nonce inspection ledger

**Review-only draft. Zero positive approvals, zero owner identity enrollment, zero agent / desktop / deploy authority.**

## Implemented — exact scope
Aetheris now has a separate, internal `EnterpriseSignedProofReplayLedger` that obtains a real stored design review intent, verifies a purpose-bound Ed25519 proof against that stored scope, and then records a globally unique 128-bit nonce in a relational table. **This records cryptographic inspection only**, and does not establish the public key as belonging to the user.

- All 13 company and 100 division taxonomy proposals and the E0 canonical agent identity baseline remain unchanged.
- A signed proof is checked against the stored project/company/division, exact brief/manifest/storyboard hashes, timestamps and `REVIEW_STATIC_DESIGN_ONLY` purpose.
- A committed PostgreSQL V2 schema defines `enterprise_review_proof_nonces` with `nonce_hex` as a global primary key. A second copy of the same nonce, even for another review intent, fails at the SQL uniqueness boundary.
- Rows also hold intent and project reference, proof-content SHA256, observed time, expiry and **only** `SIGNED_BYTES_SEEN_OWNER_UNVERIFIED` evidence status. V2 uses FK `ON DELETE RESTRICT`, restricted public ACL, hash/nonce constraints and positive-expiry checks.
- The service intentionally DOES NOT run a non-atomic `existsById` check before insert: `saveAndFlush` with DB primary-key uniqueness is the arbiter when two transactions race.
- One successful *nonce inspection reservation* returns `nonceRecorded=true`; `actualOwnerAuthenticated=false`, `approvalGranted=false`, `nonceConsumedForAction=false`, `executionReleased=false` and `positiveActionReplayProtectionCertified=false`. Any duplicate DB exception fails closed; it is not translated into approval.
- Neither an HTTP controller nor an executable permission adapter is added. User/device key enrollment, trustworthy pinning, owner session verification, revocation and actual action authorization are **not** implemented.
- Globally unique nonce inspection is a defense for future review flows, **not proof of end-to-end action replay protection**; no positive action exists or consumes a nonce. Malicious but valid candidate-key proofs must not be allowed to fill this table through an unauthenticated public endpoint.

## Evidence and test coverage
New `EnterpriseSignedProofReplayLedgerTest` uses genuine Spring/JPA database transactions:
1. Persist valid signed-byte review evidence with no approval.
2. Repeat the same exact signature/nonce and reject it.
3. Reject the same nonce signed for an entirely different review.
4. Reject forged signing keys and altered company scope without writing evidence.
5. Race two simultaneous insertions and assert **exactly one persisted reservation, zero approvals**.
6. Cleanup respects nonce-to-intent foreign key order.

New CI also starts a **disposable PostgreSQL 16** instance, applies reviewed V1+V2 SQL, runs real JPA tests including two concurrent nonce insertions, checks that CI-only legacy JPA bootstrap does not change the three reviewed table definitions, and executes adversarial SQL tests (unique constraint, orphan FK, false approval status, invalid hash/expiry, PUBLIC privileges). Targeted Ubuntu and Windows H2 runs, whole Java regression and a static zero-authority gate are independent required jobs.

CI-only `ddl-auto=update` bootstraps unrelated legacy entities because this development stack does not yet have complete orchestrator migration DDL. **This is not production PostgreSQL migration certification.** Database-admin credentials in disposable CI are not acceptable as runtime permissions.

## Remaining security blockers
1. Trusted owner enrollment/key rotation and a verified session principal. A caller-supplied candidate verification key cannot establish identity.
2. Atomic *positive-approval* grant/consume, policy and actor separation — these cannot be enabled by a cryptographically valid planning proposal alone.
3. Revocation, duplicate/racing submit protection, privileged interservice transports and rejection of direct orchestrator gateway bypass.
4. DB runtime privilege validation with dedicated non-owner credentials, tamper-resistant audit custody, backup and migration restore drills.
5. The actual Stage45 13-blocker suite is absent on the E0→E2.2C draft stack and has **not** been run for this slice; host/Windows/production release remains **NO_GO**.

Stacked review sequence: PR #102 → #104 → #106 → #107 → #109 → #110 → #112 → this draft. No direct commit to protected `main` and no deployment.

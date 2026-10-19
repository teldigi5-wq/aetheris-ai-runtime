# E2.2D — Offline-root-signed candidate key trust, rotation and revocation

**DRAFT ONLY — does not authenticate a human, enroll an owner's key in production, authorize a tool, permit production publication, or touch the physical Windows PC.**

## Code implemented

`EnterpriseOfflineKeyTrust` verifies an Ed25519 signature on an offline-generated **candidate-key trust bundle**. The operator supplies a detached root public key and a minimum accepted bundle generation through an independently protected channel. These must never be supplied by the same untrusted request as the bundle. The implementation is a pure, side-effect-free Java class with no Spring Bean, inbound endpoint, private key storage or secret-access mechanism.

The signed, domain-separated, length-prefixed bundle binds:
- Schema version and strictly monotonic bundle generation.
- Issuance and expiration with a maximum 24-hour signed-bundle validity window.
- Up to eight uniquely named candidate Ed25519 public keys encoded as canonical SPKI/base64url.
- Each key's declared validity window and explicit state `ACTIVE_CANDIDATE_UNATTESTED` or `REVOKED`.

Only a correctly signed, unexpired bundle meeting the separately pinned minimum generation can resolve a candidate key. A revoked key cannot be resolved. Unknown IDs, wrong curve, malformed key bytes, duplicate IDs, overlong bundles, altered public keys, forged signatures and old generations fail closed.

The method `inspectWithSignedBundle(...)` uses the resolved public key to verify an existing E2.2B signed proof against a stored review scope. A valid proof reports **signature-valid only**; owner-authenticated, approval-granted, nonce-consumed and execution-released all remain false. The durable nonce replay ledger introduced in PR #113 is unchanged.

## Security qualifications

- **Offline operator root != verified end-user identity.** Our tests sign with ephemeral keys generated inside JUnit. No private keys are committed, emailed or handled by the AI agent.
- Bundle generation alone is not durable rollback prevention. A future independent operator-controlled monotonic counter / secure persisted minimum generation must be implemented and certified. Allowing the requester to choose the root or minimum generation would make this safeguard meaningless.
- Signed revocation cannot instantly invalidate a previously issued short-lived bundle without a trusted fresh-bundle delivery channel or secure monotonic generation update. Maximum 24-hour bundle lifetime bounds, but does not remove, this stale-revocation window.
- A verified owner consent flow, public-key enrollment ceremony, hardware-backed key storage, session/device authentication, withdrawal/revocation distribution, actor separation and real single-use action approvals remain unimplemented.
- No HTTP controller, root signing endpoint, model/agent execution bridge, money/trading or privileged operation is created. Any direct API accepting a client-provided key as “owner” would be a prohibited trust-boundary violation.
- The actual Stage45 13-blocker release gate is not integrated/executed on this draft-stack branch. Production, protected-main merge authorization, desktop/owner-PC activity and financial operations remain **NO_GO**.

## Verification

The new `EnterpriseOfflineKeyTrustTest` runs genuine JDK Ed25519 signature generation/verification and tests cryptographic rotation, revoked keys, forged root, altered key bytes, generation rollback, expired bundles and individual keys, duplicate IDs, invalid Ed448 SPKI, oversized roster and a proof verified with the resolved candidate key while still reporting no owner authorization.

Cross-platform GitHub Actions runs the new tests plus the previously passing E0 workforce, E2.2B review, nonce and signed-proof tests on Ubuntu and Windows; an independent full Java regression and static non-activation check are required.

## Roadmap to an actually working system

1. **Trusted enrollment integration:** choose and prove a trusted local desktop owner identity and key-custody workflow with explicit human ceremony and audited offline root fingerprint.
2. **Monotonic generation/revocation:** persistent and tamper-resistant minimum-generation policy; key identity and device loss/recovery; revoke and rotate signed bundles safely.
3. **True owner approval:** verify authenticated principal and a specific irreversible action; add atomic single-use grant/consume in PostgreSQL with replay and racing tests.
4. **E3 isolated worker:** disposable sandbox, bounded code-generation tools, process cancellation, artifact/output capture and deny-by-default policy; no user PC access until Stage45/physical host qualification.
5. **E5/E6 flagship real demonstration:** have an actual worker build, run and test a cinematic GSAP/Three.js website, rather than generating static design concepts alone.

Dependency chain: E0 #102 → E1 #104 → E2.1 #106 → E2.2A #107 → E2.2B intake #109 → PostgreSQL #110 → signed proof #112 → durable nonce #113 → **E2.2D**. All prior branches remain unmerged drafts; this work must not bypass their review.

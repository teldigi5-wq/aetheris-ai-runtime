# Aetheris E2.2B-2B — Signed Proof Preflight (Not Owner Authentication)

**OPEN DRAFT. NO positive approval, trusted owner identity, replay-safe consumption, network route, deployment or tool execution.**

## What is implemented

`EnterpriseOwnerProofPreflight` uses JDK 21 Ed25519 to independently verify the signature of a tightly constrained owner-**candidate** proof for a single, existing design-review intent. A valid signature proves only that the private key matching the supplied preflight public key signed these bytes. **It does not prove who owns that key**; real owner-key provisioning, user session authentication and key revocation remain unimplemented.

The canonical, domain-separated envelope signs all of:
- Purpose fixed to `REVIEW_STATIC_DESIGN_ONLY`
- Exact persisted intent UUID; company, division and project IDs
- Exact normalized brief, SVG storyboard and JSON manifest SHA256 values
- The stored intent issuance time and 15-minute expiry
- One proposed 128-bit nonce (32 lower-case hex chars)
- Proof issuance and expiration timestamps, capped at five minutes

Inputs use strict ASCII UUID/slug/hash/nonce validation and reject cross-company or cross-project substitutions, forged Ed25519 keys, wrong curves, tampering, expired/future proposals and non-review purposes. Key material is **never accepted from the signed request itself**: the verifier takes an independently supplied candidate key. Production pinning/enrollment of that key is not available and must be verified separately.

`EnterpriseOwnerProofStoredIntentTest` submits a genuine design-review artifact into the existing H2/JPA deny-only ledger, loads the persisted scope, signs a test-only proof, verifies the bytes and confirms that `EnterpriseDesignReviewIntakeService.tryAuthorize` still records **DENIED** for that same artifact.

Every inspection result sets:
`actualOwnerAuthenticated=false`,
`approvalGranted=false`,
`nonceConsumed=false`,
`executionReleased=false`,
`replayProtectionCertified=false`.

The same signed proof may pass **cryptographic inspection** twice because nonce consumption has intentionally not been integrated. That is not a replay-safety pass: this preflight cannot be used as an approval token or executable capability, even when a valid signature is supplied.

## Security architecture decisions

**Why we cannot trust a header:** In `aetheris-platform`, the existing gateway JWT filter verifies its own bearer token and replaces forwarded `X-Aetheris-User-*` headers. The orchestrator still must defend against direct bypass and forged headers, so forwarded role/email headers alone do **not** prove an authenticated owner. This new service does not accept those headers and is not a Spring Bean or HTTP endpoint.

**Why Ed25519:** Independent signing keys and a purpose-bound binary-free canonical challenge reduce confusion with generic gateway bearer tokens and do not require new packages or paid infrastructure. Real key enrollment, device binding, public-key trust anchor management and revocation are required before any identity assertion can be made.

**Why no action authorization yet:** The Stage45 13-blocker suite is not integrated into this PR stack, and the owner-PC has not been physically certified. There is no trusted browser/desktop confirmation, authenticated principal, durable atomic nonce consumption, positive approval ledger or executable action consumer.

## Actual tests and CI

- A focused set of pure JUnit cryptographic tests generates ephemeral Ed25519 signing keys to check valid proof bytes, attacker keys, wrong curves, altered scope/hashes, stale/future/overlong validity, malformed nonce/signature, wrong purpose and identical repeated submissions.
- Spring/H2 tests bind the challenge to a **real persisted** design intent, then confirm the existing deny-only audit trail remains authoritative.
- Ubuntu + Windows Java tests, complete Java orchestrator regression and explicit no-activation source checks are required in GitHub Actions.
- Static code is not an owner enrollment certificate, a production database migration, a secure enclave key store or a valid Stage45 pilot.

## Promotion blocker / next implementation

**E2.2B-2C** must deliver (1) trusted owner and device enrollment with key custody and verified session identity, (2) PostgreSQL atomic nonce reservation/consumption with race/restart tests, (3) project-scoped approval policy and independent audit custody, (4) direct-orchestrator bypass denial, (5) real Stage45 release gate execution, and (6) owner PC certification. Only then can positive approvals be considered, separately from real worker permissions.

Stack: E0 PR #102 → E1 #104 → E2.1 #106 → E2.2A #107 → E2.2B-1 #109 → PostgreSQL contract #110 → this draft. Protected main remains unchanged.

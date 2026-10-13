# Runtime-verifiable account identity — isolated preparatory implementation

**Status:** `SOURCE_VERIFIED_NOT_WIRED` (a real signature verifier with synthetic
JUnit tests; **NOT** an authenticated HTTP controller or SSE subscriber).

The platform identity issuer and gateway baseline at platform source
`57ad3e0e602efa6feb275328254151b959a3499b` use JJWT **0.12.6** with
HMAC-SHA256 for 32-byte signing keys. Access tokens include:

- `sub`: account email
- `uid`: **numeric IdentityAccount.id / Java Long** (not UUID)
- `role`: `ADMIN`, `DEVELOPER` or `API_CONSUMER`
- `scopes`: list of strings including `orchestrator:read` for reads,
  `orchestrator:write` for modifying operations
- `iss`: `aetheris-identity` **required by this stacked source contract**
- `aud`: `aetheris-api` **required by this stacked source contract**
- `iat` and `exp`

A previous draft used owner UUIDs; **task and mission owner IDs were corrected
to Long/BIGINT on PR #25 before building this verifier**. Project IDs remain
UUIDs. Repository queries and owner policies use these native types, and
legacy ownerless records remain inaccessible to authenticated owner readers.

## Implementation

`RuntimeAccessTokenVerifier` is an isolated native-Java class (no
`@Component`, Tauri command, servlet filter or HTTP endpoint). It must
receive an **explicit, private, nondefault JWT signing secret**; it does not
read forged `X-Aetheris-*` identity headers or fetch keys from HTTP. It
verifies JWS signature, issuer, API audience and algorithm, expiration/issued-at times, a positive
numeric UID, valid role, bounded scope strings and an exact required
orchestrator read/write scope. It returns a small in-process
`VerifiedPrincipal` with numeric account ID and scoped permissions — never
the raw token. Invalid tokens return a coarse non-logging rejection.

Synthetic JUnit tests check two distinct signed identities, token tampering,
wrong signing key, missing/incorrect claims, unsigned input, expired/future
tokens, wrong scope, weak/default secret and fail-closed HTTP quarantine.

## Explicit remaining blockers

1. Platform draft [#228](https://github.com/teldigi5-wq/aetheris-platform/pull/228)
   now **proposes** issuing `iss=aetheris-identity` and `aud=aetheris-api`
   and requiring both in the gateway. This native verifier requires the same
   source-defined claims. None are in deployed old-format tokens by default.
   Old-format tokens will be rejected after activation, so coordinated
   identity/gateway/runtime releases, re-login and refresh-session UX review
   are mandatory. There is deliberately **no legacy unsigned/claimless
   compatibility mode**. Signer secret distribution and asymmetric-signing
   migration are separate security reviews.
2. No production credential material is embedded in this repository,
   configured for the runtime, injected into client JavaScript or logged.
   The verifier constructor is **not wired** to HTTP; task, mission and live
   event APIs remain quarantined by PR #24.
3. A future server-side request principal resolver must authenticate every
   request *before* deriving owner/project context. It must block direct
   runtime-port bypass and untrusted identity headers.
4. Authoritative task and mission creation must persist verified owner IDs.
   Every query, command and event subscription (including indirect task APIs)
   must enforce owner/project checks. Raw SSE messages, transcripts and
   arbitrary JSON are not identity proofs.
5. Re-enable only vetted per-owner APIs after cross-account integration
   certification, database migration review, SSE subscriber isolation,
   reconnect and token-expiry tests, plus actual owner-PC validation.

There is no automatic permission upgrade, paid provider, live agent
execution, deployment or protected-main merge in this change.

Security tracking: https://github.com/teldigi5-wq/aetheris-ai-runtime/issues/23

Platform live client remains blocked:
https://github.com/teldigi5-wq/aetheris-platform/pull/227

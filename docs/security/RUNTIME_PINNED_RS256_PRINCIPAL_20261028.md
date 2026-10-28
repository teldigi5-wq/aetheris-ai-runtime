# Runtime Identity RS256 principal verification — 28 October 2026

**Standalone security prerequisite, not route activation. Stage45 NO_GO.**

## Inherited verified state

- [AI Runtime security draft PR #205](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/205) disabled global task SSE broadcasts and returned 404 for ownerless event feeds, 9/9 CI successful.
- [AI Runtime security draft PR #207](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/207) added highest-precedence HTTP containment for **all** task and mission endpoints. Exact-head 9/9 CI, 7/7 real Java tests.
- Neither approach knows the *verified* owning tenant/project of existing persisted tasks. No authenticated principal has been integrated with task and mission persistence. Do not remove either lockdown.

## This stage

A **new standalone Java verifier**, `RuntimePinnedPrincipalVerifier`, consumes an Identity-issued RS256 JWT with an operator-pinned **RSA 2048+ public key**. No Spring `@Component`, filter or bean means it is **not reachable via any HTTP endpoint** and cannot bypass the lockdown. No signing private key, HMAC secret, JWKS URL, network request, delegated tool authority, customer data or runtime credentials are added.

The independent verifier requires:
- Exact `Bearer ` envelope (bounded 8192 chars); RS256 asymmetric signature against a **single pinned** public key; no `alg=none`, HS256 or JOSE-header key discovery.
- Exact signed pinned `kid`, issuer and **single** dedicated `aetheris-orchestrator-api` audience. Unlike Gateway `orchestrator:read` scope alone, a verified principal UID still gives **no object ownership**.
- Bounded signed subject, positive integer signed Identity `uid`, supported Identity roles (`ADMIN`, `DEVELOPER`, `API_CONSUMER`) with strictly enforceable role/scopes ceiling. No unknown/duplicate/extra privilege scopes.
- Signed issued/expiration times, bounded maximum lifetime (24 h), 30 s future-issue tolerance, expired/future tokens denied.
- Immutable `VerifiedPrincipal` returned on success; no tenant or project ownership is inferred from the token.

CI constructs fresh ephemeral RSA signing keypairs in memory, tests **real RS256 signed JWTs**, a foreign RSA signer, incorrect `kid`, issuer/audiences, HS256 confusion, malformed UID, fraudulent role/scope elevation, future/expired/overlong tokens, invalid PEM/weak keys, and malformed authorization header. No production private key is used. The old 404 early task/mission filter and event broadcast lockdown continue to be tested in the same exact-head workflow.

## Still required to unlock customer-facing runtime

1. Authenticate the real Identity issuer and validate the dedicated service audience with operator-managed public-key rotation, revocation and consistent session state.
2. Create a reviewed **authoritative owner/tenant registry** and versioned schema migration; quarantine historical ownerless tasks and missions. **Identity `uid` is not equivalent to User service DB primary key**, nor does a signed role/scope establish task ownership.
3. Enforce repository-level access on task, mission, history, mutation and SSE subscriptions using that owner/tenant binding, with real two-tenant negative tests on both Gateway and direct runtime ports. Never trust forwarded owner headers.
4. Validate encrypted transport between actual services, consent, approvals, key management, rollback and production operational attestations before staged activation.

**This only adds an independently testable parsing/security prerequisite; it does not implement point 1 as a deployed authentication filter, or points 2–4.** The public task and mission APIs continue to answer 404 until the real identity/ownership model is complete. Do not merge/activate solely because CI is green.

All nine independent Stage45 production release attestations remain open. Keep draft/unmerged, protected main unchanged, no physical PC claims or production deployment. **Stage45 NO_GO.**

# Runtime Security #23 — independent direct-port signed-bearer HTTP admission

**9 November 2026 • REVIEW-ONLY DRAFT • Stage 45 NO_GO • Runtime #23 OPEN**

## Precise continuation and dependencies

This is the next genuinely unfinished milestone following owner-bound read/history [draft #294](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/294) at `f32d1a9d1493560e22f87a938553f9318ae8ae67`. It does not redo the prior PostgreSQL signer/epoch INSERT race in #293, owner-scoped read query in #294, or platform Gateway transport tests.

Runtime protected `main` currently includes the independent merged PR #292 at `5e3b495ccbbd4eebc88b2a943b0a7f292c7c40f2`. Stacked review branches have diverged from it; **never blanket-merge or activate** this ancestry.

## New actual servlet implementation

The `RuntimeSignedIdentityHttpFilter` is a real Jakarta Servlet `Filter` (not a Spring component). It is intentionally not registered by application configuration; no public Task/Mission HTTP API is activated. It enforces, for whichever route is explicitly registered by an approved future integration:

- Exactly one HTTP `Authorization: Bearer` header; no anonymous/default identity.
- Operator-pinned independent RS256 signature, exact `kid`, Identity issuer, single intended audience, signed `uid`, valid time window and well-formed signed `jti` via the already reviewed Runtime verifier.
- A separately injected synchronous `SessionAuthority` gate on **every request**. The safe default denies all. A missing/unregistered/revoked session returns 401; a checker error returns a non-descriptive 503. The gate must eventually be supplied by operator-approved **authoritative** session/signer/epoch registries, not untrusted headers or local token caches.
- Appropriate signed operation scope (403 if a registered verified principal lacks the required scope).
- Rejection of spoofable `X-Aetheris-*` and common user-forwarding identity headers, even when a valid bearer is present.
- Short-lived, strictly per-request typed verified-principal attribute; no raw JWT or claims forwarded as network-visible identity headers. Generic non-cacheable responses, no token, registry or signer details in errors.

**Security limits:** successful HTTP *authentication preflight* is not object-level authorization, proof of current tenant/project membership, cross-primary fencing, or a durable revocation transaction held for the lifetime of execution. Resource operations must recheck session/membership/fence in the same database statement as access (the separate review-only task-read probe illustrates this but uses disposable registries). No async/SSE grant semantics are certified. The real Identity session authority and enrollment writers are **not connected** by this milestone.

## Real HTTP test evidence — verified for code-and-test checkpoint

`RuntimeDirectPortBearerSocketTest` boots actual embedded Tomcat on random loopback TCP port using an isolated Spring Boot test-only application. It registers this filter **only** on `/__runtime_auth_probe` (no product endpoint), with ephemeral RSA signers and a disposable in-memory test-only session stub. Tests cover:

1. Two independently signed identities accepted only with registered test session; revocation denies the same bearer.
2. Anonymous, forged, unregistered, wrong signer/kid/issuer/audience, expired and missing JTI requests denied.
3. Spoofed identity headers and insufficient signed scope denied; real Task HTTP still unconditional 404 despite valid bearer.
4. Simulated session authority unavailable => fail closed; the unconfigured safe-default filter admits nothing.
5. Duplicate HTTP Authorization headers denied.

The initial code-and-test checkpoint `7909c10d69e86b7ce3d1a50f6d1faf528dc61dbc` passed [GitHub Actions #42568779427](https://github.com/teldigi5-wq/aetheris-ai-runtime/actions/runs/42568779427), **COMPLETED SUCCESS with both jobs** on 9 November 2026. Real loopback-TCP Tomcat **5/5** socket tests passed; the full Java 21 orchestrator suite reported **377/377** tests with zero failures/errors/skips. A separate real disposable PostgreSQL16 job reported **11/11** tests with zero failures/errors/skips and executed its mandatory no-skip and fixture cleanup checks. The source-level Task/Mission quarantine and global SSE checks remained green. These are **CI-tested** facts, not actual HTTPS, mTLS, PostgreSQL-backed HTTP authentication, deployed identity authority, hardware acceptance or production proof. Any new executable-code commit requires a fresh exact-head CI result.

## Next release-blocking work

1. Operator-approve versioned PostgreSQL authority schemas, strictly separated restricted DB roles, session enrollment/logout and signer/epoch revocation writers, and rollback/backup plan. Existing synthetic fixtures must not be renamed into production authorities.
2. Provide and verify a real authoritative SessionAuthority implementation plus trust-configuration/key rotation and authenticated gateway-to-runtime channel (mTLS or independently verified end-user token where appropriate), including two-account direct-port TLS and database integration.
3. Define and test exact resource-level admission/ownerless migration, mission attachment/history, transaction expiry and concurrency policy. **Only then** review selective Task/Mission HTTP unquarantine. Per-owner SSE/replay follows later.

**Runtime #23 OPEN · Platform #251 OPEN · Stage 45 NO_GO · XERJ HOLD · Owner-PC BLOCKED_PENDING_HARDWARE.** The historic Platform Stage 34/34 is unchanged; no Stage 35.

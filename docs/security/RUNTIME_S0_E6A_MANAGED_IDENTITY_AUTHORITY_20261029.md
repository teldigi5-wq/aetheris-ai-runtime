# S0-E6A — production-created pinned Identity verification authority, 29 October 2026

**Stage45 NO_GO; DRAFT and UNMERGED; no customer API/mission/task activation.**

## Verified ancestry

This candidate is stacked on [Runtime draft PR #221](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/221) @ immutable `26a749fc4c49c91ab08b1b33aaf69196a3be9488` with [35/35 Java tests successful](https://github.com/teldigi5-wq/aetheris-ai-runtime/actions/runs/40882742119). S0-E5 enumerated 403 Spring MVC method-and-route entries and marked all currently `DENY_404`. That is an inventory, not access certification. Parent S0-E4 registers the real blanket `/api` and `/actuator` 404 quarantine; if deployed it would block legitimate API access. S0-E6A does not remove it.

## New real bean — independent cryptographic trust, no route grants

`RuntimePinnedIdentityAuthority` is a **real production-created Spring `@Component`** using the existing no-network `RuntimePinnedPrincipalVerifier` from S0-B. It obtains **only operator-pinned configuration**, never incoming JWT header URLs, per-request custom keys, spoofed gateway headers or unauthenticated user attributes. Exact configuration properties (not present by default):

- `aetheris.runtime.identity.public-key-pem-base64`: base64 encoding of an operator-controlled PEM RSA public key (not a private key; do not put secret keys in Git).
- `aetheris.runtime.identity.key-id`: strict exact JWT `kid`.
- `aetheris.runtime.identity.issuer`: strict exact trusted Identity issuer.
- `aetheris.runtime.identity.audience`: single exact Runtime service audience.

Configuration behavior: with **all four properties absent**, `isConfigured()=false` and every attempt to `requireVerifiedPrincipal` throws. With **any incomplete or malformed set**, application construction fails closed, not with a permissive default. With a valid set, RSA public key parsing is pinned with no network, and the verifier checks RS256 signature, key ID, issuer, single audience, subject, UID, issued/expiry, role ceilings and scopes, returning only its sealed `VerifiedPrincipal` type. The public key's base64 envelope has a conservative maximum input length. Key material and JWT claims are not echoed in configuration errors.

**Important security boundary:** This authenticates JWT **identity only when called**; it is **not wired to production HTTP filters** in this stage and does not check whether a user's Identity account or session was revoked after issuance. Identity alone does not confer tenant/project membership, authorization to execute agent tools, task ownership, SSE replay or any API permission. It does not validate authenticated Gateway/Runtime mTLS workload identity. It must never be used as a generic allow-all admission mechanism.

## CI proof plan

`RuntimeFullAppPinnedIdentityAuthorityTest` boots the genuine full `AetherisOrchestratorApplication` on disposable H2 JPA `create-drop` with real 127.0.0.1 random-port HTTP and all external execution disabled. A JUnit `@DynamicPropertySource` injects an **ephemeral RSA 2048-bit signing key public half only**, pinned key ID, issuer and audience into the **real Spring-created bean**. Tests accept a correctly signed token and reject forged signer, altered key ID, issuer, audience, multi-audience, expiry, unapproved role scope, malformed bearer and false-header claims; separate construction checks verify absent/partial/malformed operator config fail closed. A real HTTP client proves that valid, invalid and missing tokens still receive the **original HTTP 404** for Task/Mission/Agents/Connectors/Models/Actuator, and no synthetic DB Task rows change.

The dedicated exact-head GitHub Actions run also executes the predecessor full route-policy inventory, full production quarantine, synthetic Spring Boot/Tomcat JWT and event lockdown suites. Only after finished **SUCCESS** and exact SHA can S0-E6A be considered verified.

## Remaining mandatory work and no-go

- **E6 remains IN PROGRESS, not completed**: real direct-port production HTTP resource-server registration and route-by-route admission/denial, safe availability restoration, comprehensive route ownership, impersonation resistance, proper 401/403/404 semantics and session revocation still need separate independent tests. Parent #220 deliberately blocks every business API and actuator, which is an operational availability issue.
- **E7–E11 planned**: revocation/rotation; authenticated Gateway-to-Runtime mTLS; production PostgreSQL/Flyway owner and tenant authority (current production `spring.jpa.hibernate.ddl-auto=update` persists); scoped Task/Mission and SSE; operational rollout, physical owner-PC acceptance and release certification. Thus the count remains **6 planned major engineering work packages including E6**, not 5.
- **All 9 Stage45 independent release gates are NOT CERTIFIED**; none is closed simply by passing a synthetic CI test. Runtime [issue #23](https://github.com/teldigi5-wq/aetheris-ai-runtime/issues/23) and Platform [issue #251](https://github.com/teldigi5-wq/aetheris-platform/issues/251) stay OPEN; current approved main runtime pinned elsewhere remains unchanged; physical test `BLOCKED_PENDING_HARDWARE`.
- No protected-main merge, CI bypass, public endpoint, customer API unlock, Task/Mission access, global SSE reintroduction, secret, cloud bill, live migration or production deployment.

**Stage45 NO_GO.**

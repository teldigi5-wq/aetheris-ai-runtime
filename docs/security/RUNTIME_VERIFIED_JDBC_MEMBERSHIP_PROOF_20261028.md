# Verified Identity principal to disposable JDBC membership read — 28 October 2026

**Stacked security S0 engineering candidate, not a production feature. Stage45 NO_GO.**

## Previous evidence

AI Runtime [draft #208](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/208) proved independent pinned RS256 Identity principal verification; [draft #209](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/209) tested additive owner/tenant/project schema in disposable PostgreSQL16; [draft #210](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/210) tested membership-based read and revocation in real SQL. No external task/mission/SSE API is permitted: #205/#207 enforce containment. Parent work is all draft/unmerged and Stage45 NO_GO.

## New actual proof

Two independent security concerns are now connected, still only as a **standalone offline test**:

1. The previously public `RuntimePinnedPrincipalVerifier.VerifiedPrincipal` record had a public canonical constructor. A caller could **directly construct** an apparently "verified" principal object without a signed JWT. Replace it with a `final` nested class having a **private** constructor, accessible only by `RuntimePinnedPrincipalVerifier` after verification of operator-pinned RSA public key, `kid`, issuer, dedicated audience, UID and role/scope restrictions. Its immutable `Set.copyOf` scoped privileges are retained; tests assert **zero public constructors**. This strengthens the Java type boundary but does not substitute for HTTP authentication.
2. The new `RuntimeVerifiedTaskReadProbe` accepts only that verified principal, a server-provided JDBC `Connection`, requested tenant and project selectors, and a task UUID. It requires `orchestrator:read` and uses **parameterized `PreparedStatement` SQL** that combines the signed principal's UID with task owner UID, tenant/project, valid owner timestamp and live/active tenant/project membership status. Tenant/project strings alone never grant access. The query has no write capability or global cached membership.
3. The `RuntimeVerifiedTaskReadProbeTest` CI suite signs **real ephemeral RSA JWTs** and opens a **real PostgreSQL16 JDBC** connection to localhost with synthetic owner/membership tables. Tests cover Alice and Bob different UIDs, Alice's distinct tenant memberships, tenant/project selectors, historical ownerless rows, revoked members, revoked/expired membership, read-scope denial, invalid signature rejection and private constructor enforcement. Each transaction and savepoint rolls back; CI independently verifies there are no permanent task/membership tables.

This is a substantial step beyond passing caller-supplied UID strings to SQL, but it is not a complete SaaS resource-server. It runs outside the Spring HTTP runtime, on disposable synthetic tables. It has no `@Component`, controller or service injection and cannot reopen the task/mission API. It does not authorize writes, user-invited delegation, missions, SSE, revocation of **the Identity access token**, key rotation or horizontal-scale subscription cleanup. A malicious/compromised application could still misuse database connections; the eventual production system needs independent JDBC privilege and tenant registry review.

## Exact remaining critical gates

A legitimate production implementation requires a separately approved versioned database migration reconciled with current Hibernate `ddl-auto=update`, ownerless legacy task migration/quarantine policy, durable authoritative tenant and project membership governance, independently authenticated runtime HTTP resource-server chain and service-to-service mTLS, transactional scoped repository **writes** and mission operations, session/identity revocation and verified tenant/project changes, concurrent write/revoke tests, multi-instance SSE and two-user end-to-end Gateway/direct-port test matrix.

**This branch includes no deployment action, production secret, customer data or JPA schema update. All ownerless task and mission HTTP routes stay 404 in the candidate and Stage45 NO_GO; all nine independent release attestations remain outstanding.**

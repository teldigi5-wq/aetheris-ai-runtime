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

## 4 November 2026 — deny stale snapshot authorization (draft only)

The standalone SQL authorization probe now requires PostgreSQL transaction
isolation `read committed` within the same parameterized SQL statement via
`current_setting('transaction_isolation')`. Even with fresh expiry time,
REPEATABLE READ or SERIALIZABLE can retain previously committed membership
rows after a concurrent revocation; the policy now denies those snapshots.
READ UNCOMMITTED is intentionally also denied instead of relying on its
PostgreSQL READ COMMITTED implementation equivalence.

New disposable PostgreSQL16 JDBC regression tests compare an allowed
READ COMMITTED session with matching synthetic owner/task/tenant/project
TEMP rows against denied REPEATABLE READ, SERIALIZABLE and READ UNCOMMITTED
sessions with the same matching rows. All sessions roll back and close;
the source-level workflow gate requires the exact SQL isolation condition.

No runtime controller, HTTP route, SSE, migration, deploy, external permission
or persistent customer data is introduced. This guard does not prove
concurrent-revocation race ordering, immediate cancellation of an in-flight
read, token-session revocation, or production trust boundaries. Runtime
issue #23 stays OPEN; Stage45 NO_GO.

## 4 November 2026 — two committed connection revocations (draft-only)

The previous `READ COMMITTED` isolation test did not prove a concurrent
transaction's *committed* membership update became visible in an already open
reader transaction. This addition uses genuine independent PostgreSQL16 JDBC
reader and writer connections, one signed synthetic owner and a distinct denied
identity. The writer updates first the tenant membership and then project
membership; the reader demonstrates uncommitted updates remain invisible,
committed revocations deny the next read, and later committed reinstatements
restore only the valid signed owner.

PostgreSQL temporary tables cannot be shared between sessions, so this test
creates a uniquely named transient schema with three synthetic tables only
inside the disposable `ownerlab_disposable` CI database. The schema is
explicitly dropped in a `finally` block and the test queries `pg_namespace`
to prove the schema is absent afterwards. The job's existing final check
requires no persistent task/membership tables, and the HTTP quarantine is
unchanged.

This is a real two-connection sequential commit/recheck test, **not** proof
of instantaneous revocation for an already-running query, session/token
revocation, tenant registry authority, safe API activation, or a production
release. No production DB, real user data, long-lived schema migration,
protected-main merge or deployment. Runtime issue #23 remains OPEN.
Stage45 NO_GO.

# Aetheris × Syntra S0-D — signed task planning write and membership revocation race

**29 October 2026 · Stage45 NO_GO · review-only DRAFT, UNMERGED.**

## Source/dependency checkpoint

Builds exclusively on [Runtime PR #213](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/213) successful exact-head [CI #40864484544](https://github.com/teldigi5-wq/aetheris-ai-runtime/actions/runs/40864484544). Earlier [PR #211](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/211) sealed the independently RS256-verified `RuntimePinnedPrincipalVerifier.VerifiedPrincipal`, but its JDBC read probe still targets synthetic `tenant_memberships/project_memberships`. [#212](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/212) added disposable canonical-style `registry_*` relationships; #213 rehearsed the proposed review-only V1/V2 SQL candidates on synthetic fresh/historical schemas.

## Narrow code change

`RuntimeVerifiedTaskPlanningProbe` is a **non-Spring, non-HTTP, non-injectable test-stage JDBC component**. It accepts only a verifier-sealed principal, explicit READ_COMMITTED non-autocommit JDBC transaction, tenant/project selectors and task ID/version. It requires signed `orchestrator:write` scope and active, unrevoked, unexpired tenant and project memberships with writable member roles in **the new registry schema**, not the old #211 synthetic names. It enforces ACTIVE tenant and project states and excludes VIEWER permission.

The probe locks exactly the requested tenant, project and principal membership rows with PostgreSQL `SELECT ... FOR SHARE OF tenant,project,tm,pm`, keeping authorization locks until caller COMMIT/ROLLBACK. Only then does a parameterized, exact-owner/tenant/project/verified-binding/expected-version `UPDATE` offer **one** narrow compare-and-set: `QUEUED → PLANNING`, `version=version+1`. It does **not** implement mission writes, task creation, arbitrary transitions, delegation, STOP, automatic execution, authorization sessions or user-facing APIs. It does not commit/rollback, cache memberships or accept raw Identity UID arguments.

All customer-facing Task/Mission routes still receive unconditional 404 from `OwnerlessTaskMissionHttpFilter`, global task SSE remains absent, and the test-stage probe is not injected into a controller or application service.

## Exact real test design (do not credit before successful CI)

The separate mandatory `runtime-s0-jdbc-planning-race.yml` workflow creates a fresh, fixed-name **disposable PostgreSQL16** CI database with generated-from-repository review-only V1/V2 schema candidate. It executes actual Maven/JUnit tests with real ephemeral RS256-signed tokens and separate JDBC database connections.

Tests assert cross-UID, cross-tenant, cross-project, ownerless and guessed-ID denial; wrong signed scope, VIEWER-only project membership and an attacker-signed token cannot authorize planning; invalid selectors and autocommit attempts fail; version CAS prevents repeated transition and caller ROLLBACK restores state; revoked/expired project membership and suspended tenant deny; a fully committed revocation before planning denies with no task change; and a concurrent revocation waits on the genuine planning authorization transaction's database row locks, then denies subsequent operations.

The exact-head CI also asserts `@Component/@Bean` absent, no raw header trust/HTTP activation, preserved early 404/task event containment and unchanged `ddl-auto=update` migration blocker. Synthetic DB remains disposable. This is **not** a production JPA/Flyway integration or full transaction isolation proof. A rogue DB role could still alter tables directly; application-level roles, session JWT revocation/expiry rechecks, true Identity-to-tenant authority, real owner transfer policies, idempotency, multi-resource lock ordering and mission/SSE isolation remain unimplemented.

## Release barriers

1. Implement independently authoritative tenant account and membership provisioning governed by Platform Identity. Signed numeric Identity UID is **not** UserService users.id.
2. Replace `spring.jpa.hibernate.ddl-auto=update` with formally reviewed versioned migrations and `validate`, after checking real schema baselines, permissioned backups/restores, migration operator roles and backward-compatible rollout. Review the independent old #211 JDBC-table names and new registry naming.
3. Design and test full service-to-service identity/workload authentication, token/session revocation and per-request verified principal for direct runtime ports before removing HTTP lockdown.
4. Enforce consistent sorted locking across every task/mission mutation and membership revocation/expiry. A read-committed row-lock proof does **not** prove every production transaction, time-of-check/time-of-use, unbounded transaction duration or pause/STOP policy.
5. Add full negative write/mission attachment, idempotency, concurrent updates, SSE replay/cleanup/multi-instance, comprehensive regression and operator acceptance.
6. Keep Runtime security issue #23 and Platform issue #251 OPEN; the platform [draft #346](https://github.com/teldigi5-wq/aetheris-platform/pull/346) does not establish end-to-end runtime trust. All **nine independent Stage45 production release attestations remain unfulfilled**, physical owner PC `BLOCKED_PENDING_HARDWARE`.

**Stage45 NO_GO**. No production deployment, merge to protected main, real migration, customer data, external API activation, paid service, or platform certification SHA promotion.

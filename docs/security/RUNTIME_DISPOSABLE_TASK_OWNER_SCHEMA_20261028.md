# Real PostgreSQL16 disposable owner/tenant schema trial — 28 October 2026

**Review only. NOT an installed migration. Stage45 NO_GO.**

## Why this work exists

The standalone `RuntimePinnedPrincipalVerifier` in draft [PR #208](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/208) independently verifies an Identity-issued RS256 principal, but its `uid` and role/scopes do **not** prove which task, mission or tenant it owns. TaskEntity currently lacks persisted owner/tenant/project fields, while the runtime still has `spring.jpa.hibernate.ddl-auto=update` — unapproved mapping changes could mutate production schema. HTTP task/mission/SSE endpoints remain locked in drafts #205/#207.

## This narrow candidate

This PR adds **review-only SQL inside `tests/security/sql`** (not a Spring/Flyway migration directory, and not included in deployables):

- Additive, initially nullable `owner_identity_uid bigint`, `owner_tenant_key varchar(96)`, optional `owner_project_key varchar(96)`, `owner_verified_at timestamptz` to a **synthetic** PostgreSQL 16 `aetheris_tasks` table.
- An explicit all-or-none `CHECK` constraint forbids partial identity bindings, invalid UIDs, malformed tenant/project keys and missing verified timestamps; it also avoids SQL three-valued `UNKNOWN` acceptance by separately testing `owner_identity_uid IS NOT NULL`.
- A partial tenant/owner/recency index supports future **fully scoped query predicates**. The index alone is NOT row-level security, and no authenticated tenant registry currently populates it.
- Run in one `BEGIN`/`ROLLBACK` transaction against **ephemeral Postgres 16 service in GitHub Actions**, using only static CI-synthetic database credentials. One historic `ownerless` row remains NULL. Three newly created synthetic records emulate same Identity UID across different tenants and distinct owner UID in one tenant. Queries prove only matching `identity UID + tenant + project` selects the intended object, and ownerless legacy data remains invisible in scoped queries.
- Negative inserts deliberately attempt UID-only binding, null UID plus tenant (SQL UNKNOWN bypass), uid zero, absent timestamp, uppercase tenant and uppercase project. All invalid partial bindings must be rejected. Rollback prevents persistence even in the disposable CI database, and final CI queries confirm no permanent task table was created.

**Do not execute the candidate schema on any actual runtime or production database.** This is NOT a production migration or a copy of the entire actual Hibernate-managed schema. There is no JPA entity mapping, no Flyway production version, no real authenticated tenant directory, no actual task owner grant, no event-replay unlock, and no ownerless data backfill. In particular, the statement that a query is scoped is only true when its parameters derive from independently verified Identity **and authoritative tenant/project membership**, not caller claims.

## What is still required

1. Obtain DBA/owner approval for a properly versioned migration, compatibility with existing JPA `ddl-auto=update`, backup/rollback and real DB schema drift; quarantine historical ownerless tasks with a documented manual reconciliation process.
2. Integrate `RuntimePinnedPrincipalVerifier` with actual runtime resource-server auth, service audience/key rotation and revocation; securely resolve authoritative tenant membership.
3. Bind verified principal + tenant/project into repository predicates, create/list/get/transitions, mission APIs and event stream lifecycle. Add two-authenticated-user plus multi-tenant real HTTP/DB/SSE integration including forged headers, reconnect and revocation.
4. Reconcile actual Gateway-to-runtime HTTPS/mTLS, protected main review, Stage45 independent nine attestations before production.

**The production user-facing task/mission APIs remain HTTP 404 under draft #207, and SSE remains denied under #205. Stage45 NO_GO.** No production deployment, credentials, migrations, customer data, paid service or physical-PC operation took place.

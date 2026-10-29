# S0-C — review-only versioned migration rehearsal, 29 October 2026

**Stage45 NO_GO. DRAFT, not a deployable migration or production authorization.**

## Baseline and exact scope

This candidate is stacked on AI Runtime [draft PR #212](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/212), exact reviewed head `69f3e382d0150b438269c530403355005c3d5ab8`. The parent successfully tested TEMP PostgreSQL16 registry relationships and immutable task owner binding. This successor rehearses **persisted synthetic schema evolution within disposable CI databases** and a synthetic baseline backup/restore. No real customer schema, migration, or backup is accessed.

The existing runtime `TaskEntity` maps `aetheris_tasks` with UUID id, title, commandText, state, mode, activeAgentId, createdAt, updatedAt and optimistic version. It still lacks owner/tenant/project fields. Its runtime configuration uses `spring.jpa.hibernate.ddl-auto=update`. This stage does **not** change that configuration, the application entity, repository, controllers, or the unconditional ownerless Task/Mission HTTP 404 and global SSE containment.

## Candidate migrations (NON-DEPLOYABLE)

Only in `tests/security/migration-candidates/`, **outside** `orchestrator-service/src/main/resources/db/migration` and outside the application runtime classpath:

- `V1__review_tenant_project_registry.sql`: registry tenants, tenant-qualified projects, separate tenant membership and project membership with foreign keys, roles, status/revocation/expiry and optimistic version fields. They are *candidate tables*, not an authoritative identity provisioning service.
- `V2__review_additive_task_owner_binding.sql`: additive nullable owner UID/tenant/project/verification timestamp on an existing JPA-shaped tasks table. All-or-none constraints, composite FK to registry project membership, partial scoped-read index, and a trigger denying normal UPDATE changes to owner binding. Existing records stay NULL/ownerless. No automatic assignment, no claim based on request headers, no owner transfer permission.

These V1/V2 labels express proposed migration ordering, **not installed Flyway history**. Do not copy them into production migration paths, run them against an existing customer database, or assume the migration version namespace has been selected. The earlier #209 disposable candidate allowed an optional project while this candidate requires a registered project for any bound task; this is a new **proposed** stricter invariant subject to product/legacy compatibility review. The old #209 candidate and this V2 must **never be applied blindly together**.

The #211 `RuntimeVerifiedTaskReadProbe` still queries disposable `tenant_memberships` and `project_memberships` and does **not** query these `registry_*` tables; real repository integration needs an explicit tested adapter, authoritative registry lifecycle and verified Identity-UID mapping. A signed UID is not User service `users.id`; JWT verification does not itself grant tenant/project membership.

## Disposable PostgreSQL16 acceptance proof

The workflow `.github/workflows/runtime-s0-versioned-migration-rehearsal.yml` explicitly creates three new disposable databases:

1. `ownerlab_legacy`: prechange synthetic TaskEntity-shaped table plus **two historical ownerless tasks**, with command content and version values. Creates a pre-migration `pg_dump --format=custom` snapshot.
2. `ownerlab_restore`: restores that **synthetic** dump to a separate empty database; compares original and restored task values without applying migrations to the restore target.
3. `ownerlab_fresh`: an empty prechange TaskEntity-shaped table.

The workflow applies **both review-only forward migration candidate files atomically** using `psql -1 -v ON_ERROR_STOP=1` to legacy/fresh disposable databases, then runs adversarial SQL tests inside `BEGIN/ROLLBACK` without persisting the inserted synthetic tenant/user data. Tests cover foreign keys, owner reassignment denial, partial owner binding, cross-owner/tenant/project scoped SQL predicate, revocation and expiry, normal state update, retained legacy rows and untouched restored snapshot. It checks zero populated registry/task owners after test rollback, all historical records still NULL and the restored pre-migration schema still has no owner columns.

This is a **synthetic forward migration + synthetic restore rehearsal**, not a verified production migration, real JPA/Flyway schema validation, backup/restore of customer data, zero-downtime guarantee, permission model, concurrent schema change, transactional revoke-vs-write race proof, live session revocation or production rollback. The candidate trigger does not authorize INSERTs; future application and least-privileged DB roles must independently enforce those rights.

## Required pre-production decisions / blockers

1. Confirm the canonical Identity UID-to-tenant registry authority, provisioning/invite flows, role changes and account deletion, including ownership transfer and legacy ownerless treatment.
2. Capture **real environment schema fingerprints, migration ownership and production-shaped backups** under explicit operator approval. Plan maintenance/locking budget for `ALTER TABLE`, FK validation and index creation; for large tables review `CREATE INDEX CONCURRENTLY` and separate transactions rather than copying this small synthetic atomic test unchanged.
3. Establish Flyway history and migration baselines safely for existing databases, with an explicit migration operator role distinct from a restricted runtime DML role. Disable Hibernate schema auto-update in a separate coordinated, verified change; `ddl-auto=validate` must pass in the intended deployment mode before enabling mapped fields.
4. Implement real source-owned Entity/Repository changes only after a measured migration plan, tenant membership authority and JWT-to-row binding are independently approved. Validate create/transition/membership revocation under concurrency with row locks or another defensible serializable policy, including multitenant mission attachment and SSE isolation.
5. Keep existing task/mission **HTTP 404** and global task SSE containment. Do not activate any direct port or restore task routes until independently authenticated per-resource verification, platform/workload trust integration and negative tests pass.
6. Reconcile [Platform PR #346](https://github.com/teldigi5-wq/aetheris-platform/pull/346) with runtime issue [#23](https://github.com/teldigi5-wq/aetheris-ai-runtime/issues/23), platform issue [#251](https://github.com/teldigi5-wq/aetheris-platform/issues/251), pinned certified runtime SHA and all independent release attestations.

**NO MERGE to protected main, no deployment, no customer migration, no paid infrastructure, no production approval. Stage45 NO_GO; nine independent release gates remain NOT CERTIFIED; physical owner-PC acceptance BLOCKED_PENDING_HARDWARE.**

# PostgreSQL certification of owner-task commit sequencing (draft)

This stage certifies the *real PostgreSQL behavior* of the new per-task
commit-sequence path from runtime PR #34. It introduces NO live services,
HTTP endpoints, automatic migrations or real owner data.

## Disposable PostgreSQL 16 CI

The separate `PostgreSQL Task Sequence Certification` workflow runs:

1. **Native PostgreSQL transaction tests**: the existing Java
   `VerifiedOwnerCommitSequenceCursorTest` and
   `VerifiedOwnerTaskJournalOutboxTest` under Spring/JPA with a real
   ephemeral PostgreSQL 16 service. Coverage includes same-task concurrent
   `PESSIMISTIC_WRITE` row locking, backdated/same-time ordering,
   rollback, owner isolation, task-owner checks, stale outbox lease claims
   and retries. No H2 emulation of PostgreSQL locking.
2. **Additive migration review**: a second isolated PostgreSQL 16 service
   creates *minimal synthetic pre-migration* task, mission and journal
   tables, applies the SQL migration twice, and checks that the legacy
   owner remains `NULL`, legacy journal entries remain unsequenced, the
   new outbox has no private payload columns, and unique positive
   per-task event sequences are enforced.

All test credentials, UUIDs and account IDs are synthetic, generated in
the CI workflow or local test fixture. No production database or account
is contacted. Both checks run independently and must pass before this PR
may be considered reviewed.

## Migration contract — NOT AUTO-APPLIED

The exact proposed migration is:
`docs/security/sql/postgres-owner-task-sequencing-v1.sql`.

It introduces nullable `BIGINT owner_user_id` (platform identity account
IDs are Java `Long`, not UUID), `UUID owner_project_id`, nullable
`BIGINT last_journal_sequence` on tasks, owner/project fields on
missions, nullable `BIGINT commit_sequence` on event journal rows,
a unique non-NULL `(task_id,commit_sequence)` index and the outbox table.

It does **not** add synthetic owners to legacy rows, fill unknown past
commit orders, expose task history via a new route or change secrets.
New business data remains blocked from other owners via the existing
quarantined API until verified principal/project authorization is complete.

Before any real migration:
- Back up and test on a restored staging snapshot; verify table names,
  existing columns, row volumes and custom index naming.
- Ensure there are no competing/partial migration histories or
  production constraints that would conflict with the reviewed DDL.
- Schedule index creation/validation for a maintenance window; a
  transactional `CREATE INDEX` can block writes on large tables, so this
  dry-run is **not** a zero-downtime migration guarantee.
- Establish a properly versioned schema migration framework. This SQL is
  manually reviewed and intentionally never auto-run at boot.
- Review partially applied indexes and data retention before enabling
  real owner-bound agents or authenticated desktop subscriptions.
- Independently certify multi-worker concurrent claims, outage/reconnect
  replay, durable client acknowledgement, and Windows owner-PC hardware.

## Precise guarantees

For **new events written via the same task-row-locked journal+outbox
transaction**, commit sequences are unique and monotonic per task. A
rollback reverts the task counter, journal and outbox rows. This does not
cover legacy events, writes bypassing that path, foreign data sources,
actual desktop receipt or verified success of agent actions. Current
task/mission/live HTTP quarantine, security issue #23 and protected-main
review requirements remain unchanged.


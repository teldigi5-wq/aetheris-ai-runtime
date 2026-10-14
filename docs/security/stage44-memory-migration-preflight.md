# Stage 44.4 — Fail-closed migration preflight and synthetic PostgreSQL restore

**Status: DRAFT / DO NOT DEPLOY / NO OWNER DATA CHANGED.**
Depends on draft PRs #39–#42. This slice does **not** perform or authorize
a database migration, key retirement, KMS activation, recovery of real
owner memory, backup upload, or inspection of any production dataset.

## Immutable migration-preflight result

`MemoryMigrationPreflight.evaluate(InventoryReport)` evaluates ONLY
the bounded, aggregate report produced by Stage 44.3's internal
read-only header inventory. It has no Spring endpoint, persistence
writer, actor token, startup hook, scheduled job or admin command.
It never decrypts ciphertext or receives source plaintext.

The preflight checks aggregate-accounting consistency, recognized
inventory provenance, scan completeness, legacy v1 dependencies,
malformed encrypted headers, sensitive plaintext flags, encrypted
records inconsistently marked non-sensitive and undisplayed key IDs.
Invalid or synthetic assertions of "READY" are rejected.
**Every result is `BLOCKED_NO_MIGRATION_AUTHORITY`, including an
apparently complete, clean inventory.** The gate always requires
independently verified key custody, owner-bound identity correctness,
actual PostgreSQL backup recovery and separate authorization.
Do not interpret an inventory count as a verified active encryption
key, proof of AES-GCM integrity, or permission to retire any key.

## Disposable PostgreSQL backup/restore CI

`tests/postgresql/verify_memory_snapshot_restore.sh` runs ONLY when
all fixed CI guardrails are present: `GITHUB_ACTIONS=true`,
`AETHERIS_SYNTHETIC_RESTORE_TEST=true`,
`PGHOST=127.0.0.1`, `PGPORT=5432`,
`PGUSER=postgres`, and a strictly named CI fixture source DB.
The script does not accept arbitrary database names or URLs.
Its `pg_dump` and `pg_restore` operate on **synthetic fixture rows
in an isolated, ephemeral GitHub Actions PostgreSQL service**.
It refuses a pre-existing restore database.

Test evidence includes:
- Execute a repeatable-read READ ONLY PostgreSQL transaction.
- Save a logical custom-format backup from the synthetic source.
- Restore it into a separate, newly created CI-only database.
- Compare canonical fingerprints of *all fixture row fields* before
  and after restoration, without printing row payloads.
- Intentionally corrupt a row in the disposable **restored** DB,
  prove the comparison detects it, then verify the source fixture
  never changed.
- Cleanup the temporary dump. Jobs, databases and credentials are
  ephemeral and deliberately not reusable.

**The fixture envelopes are visibly FAKE, NOT cryptographically valid
AES-GCM ciphertext.** This CI checks PostgreSQL data fidelity and
change detection, **not actual backup encryption**, envelope
authentication, full application/schema restoration, key availability
or recovery of real user memory. The actual AES-256-GCM tests remain
in the Stage 43/44 Java test suites. Even after this synthetic
restore test passes, `POSTGRESQL_BACKUP_RESTORE_UNVERIFIED` is a
mandatory preflight blocker for real migrations.

## Remaining release gates

1. Move key custody from temporary environment variables to a reviewed
   external secret manager with rotation, versioning, missing-key
   recovery and no tokens in frontend or logs.
2. Add PostgreSQL-specific `@Transactional` isolation and
   read-only role verification to the actual JPA projection. Verify
   large tables, restart/interrupt behavior, read ceiling and cursor
   stability on an isolated PostgreSQL **application schema**.
3. Provision authorized encrypted backups with independent storage,
   restore a consistent full app snapshot in a separate environment,
   and test confidentiality and ownership after restore.
4. Audit every historical v1 record and immutable owner/project
   identity with independently verified keys; record migrations
   with idempotent transactional CAS and audited rollback.
5. Obtain explicit owner approval for *any* potentially destructive
   or real-data operation, plus separate security review and physical
   Windows hardware validation.

**Do not merge protected main, deploy or activate agents or services as
part of this draft.** The only database writes in this slice are
inside disposable CI synthetic fixture/restore databases.

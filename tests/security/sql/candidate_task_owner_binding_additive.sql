-- REVIEW-ONLY ADDITIVE SCHEMA CANDIDATE, NOT an installed Flyway migration.
-- Run only inside ephemeral disposable CI PostgreSQL16 on a synthetic
-- aetheris_tasks table; never apply to production databases.
--
-- Historical ownerless rows remain NULL and MUST be unreadable by any
-- owner-scoped query until separately approved, audited remediation.
ALTER TABLE aetheris_tasks
  ADD COLUMN owner_identity_uid bigint,
  ADD COLUMN owner_tenant_key varchar(96),
  ADD COLUMN owner_project_key varchar(96),
  ADD COLUMN owner_verified_at timestamp with time zone;

ALTER TABLE aetheris_tasks
  ADD CONSTRAINT aetheris_task_owner_binding_all_or_none
  CHECK (
    (owner_identity_uid IS NULL AND owner_tenant_key IS NULL
      AND owner_project_key IS NULL AND owner_verified_at IS NULL)
    OR
    (owner_identity_uid IS NOT NULL AND owner_identity_uid > 0
      AND owner_tenant_key IS NOT NULL
      AND owner_tenant_key ~ '^[a-z0-9][a-z0-9_-]{0,95}$'
      AND owner_verified_at IS NOT NULL
      AND (owner_project_key IS NULL OR
        owner_project_key ~ '^[a-z0-9][a-z0-9_-]{0,95}$'))
  ) NOT VALID;

ALTER TABLE aetheris_tasks
  VALIDATE CONSTRAINT aetheris_task_owner_binding_all_or_none;

-- Partial index permits index-backed tenant-scoped reads. The index does
-- NOT prove object ownership; repository predicates MUST be independently
-- bound to verified Identity UID and authoritative tenant/project membership.
CREATE INDEX idx_aetheris_tasks_bound_tenant_owner
  ON aetheris_tasks (owner_tenant_key, owner_identity_uid, updated_at DESC)
  WHERE owner_identity_uid IS NOT NULL AND owner_tenant_key IS NOT NULL;

-- Disposable transaction-only PostgreSQL 16 schema and isolation rehearsal.
-- It deliberately makes NO persistent migration and never touches live data.
\set ON_ERROR_STOP on
BEGIN;

-- These columns mirror the current TaskEntity Hibernate naming convention
-- for the fields used by the rehearsal. It is NOT a real production backup.
CREATE TEMP TABLE aetheris_tasks (
  id uuid PRIMARY KEY,
  title varchar(160) NOT NULL,
  command_text varchar(8000) NOT NULL,
  state varchar(32) NOT NULL,
  mode varchar(32) NOT NULL,
  active_agent_id varchar(120),
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL,
  version bigint NOT NULL DEFAULT 0
);

INSERT INTO aetheris_tasks
  (id,title,command_text,state,mode,created_at,updated_at)
VALUES
  ('11111111-1111-4111-8111-111111111111',
   'Legacy ownerless private task','private historical content',
   'QUEUED','BALANCED',now(),now());

-- Review-only proposal is located in same directory as this rehearsal.
\ir candidate_task_owner_binding_additive.sql

DO $assert_ownerless$
BEGIN
  IF (SELECT COUNT(*) FROM aetheris_tasks WHERE owner_identity_uid IS NULL) != 1
      OR (SELECT COUNT(*) FROM aetheris_tasks WHERE owner_tenant_key IS NULL) != 1
      OR (SELECT COUNT(*) FROM aetheris_tasks WHERE owner_verified_at IS NULL) != 1
  THEN
    RAISE EXCEPTION 'Legacy ownerless task must remain quarantined';
  END IF;
END
$assert_ownerless$;

-- New SYNTHETIC tasks are inserted with complete bindings. No backfill of
-- legacy ownerless data is ever performed. Tenant is intentionally distinct
-- from the Identity UID, and a project can further scope authorization.
INSERT INTO aetheris_tasks
  (id,title,command_text,state,mode,created_at,updated_at,
   owner_identity_uid,owner_tenant_key,owner_project_key,owner_verified_at)
VALUES
  ('22222222-2222-4222-8222-222222222222',
   'Alice alpha','synthetic','QUEUED','BALANCED',now(),now(),
   42,'tenant-alpha','project-red',now()),
  ('33333333-3333-4333-8333-333333333333',
   'Alice beta','synthetic','QUEUED','BALANCED',now(),now(),
   42,'tenant-beta','project-red',now()),
  ('44444444-4444-4444-8444-444444444444',
   'Bob alpha','synthetic','QUEUED','BALANCED',now(),now(),
   77,'tenant-alpha','project-blue',now());

DO $assert_tenant_isolation$
DECLARE a_count integer;
BEGIN
  SELECT COUNT(*) INTO a_count
  FROM aetheris_tasks
  WHERE owner_identity_uid = 42 AND owner_tenant_key = 'tenant-alpha'
    AND owner_project_key = 'project-red';
  IF a_count != 1 THEN
    RAISE EXCEPTION 'Verified owner/project scoped query should see exactly one synthetic task';
  END IF;
  SELECT COUNT(*) INTO a_count
  FROM aetheris_tasks
  WHERE owner_identity_uid = 42 AND owner_tenant_key = 'tenant-beta'
    AND owner_project_key = 'project-red';
  IF a_count != 1 THEN
    RAISE EXCEPTION 'Same signed Identity UID in different tenant must remain separate';
  END IF;
  SELECT COUNT(*) INTO a_count
  FROM aetheris_tasks
  WHERE owner_identity_uid = 42 AND owner_tenant_key = 'tenant-alpha'
    AND owner_project_key = 'project-blue';
  IF a_count != 0 THEN
    RAISE EXCEPTION 'A different project must be invisible';
  END IF;
  SELECT COUNT(*) INTO a_count
  FROM aetheris_tasks
  WHERE owner_identity_uid = 77 AND owner_tenant_key = 'tenant-beta';
  IF a_count != 0 THEN
    RAISE EXCEPTION 'A different tenant and UID must be invisible';
  END IF;
  SELECT COUNT(*) INTO a_count
  FROM aetheris_tasks
  WHERE id = '11111111-1111-4111-8111-111111111111'
    AND owner_identity_uid = 42 AND owner_tenant_key = 'tenant-alpha';
  IF a_count != 0 THEN
    RAISE EXCEPTION 'Legacy ownerless record unexpectedly visible';
  END IF;
END
$assert_tenant_isolation$;

-- Invalid partial bindings and malformed tenant/project IDs fail inside
-- independent subtransactions; any unexpected acceptance fails the lab.
DO $assert_constraints$
BEGIN
  BEGIN
    INSERT INTO aetheris_tasks
      (id,title,command_text,state,mode,created_at,updated_at,owner_identity_uid)
      VALUES ('55555555-5555-4555-8555-555555555555',
      'bad','bad','QUEUED','BALANCED',now(),now(),42);
    RAISE EXCEPTION 'Partial UID-only binding was accepted';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO aetheris_tasks
      (id,title,command_text,state,mode,created_at,updated_at,
       owner_identity_uid,owner_tenant_key,owner_verified_at)
      VALUES ('66666666-6666-4666-8666-666666666666',
      'bad','bad','QUEUED','BALANCED',now(),now(),0,'tenant-alpha',now());
    RAISE EXCEPTION 'Zero UID binding was accepted';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO aetheris_tasks
      (id,title,command_text,state,mode,created_at,updated_at,
       owner_identity_uid,owner_tenant_key,owner_verified_at)
      VALUES ('77777777-7777-4777-8777-777777777777',
      'bad','bad','QUEUED','BALANCED',now(),now(),42,'TENANT-TAMPERED',now());
    RAISE EXCEPTION 'Malformed tenant identifier was accepted';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  BEGIN
    INSERT INTO aetheris_tasks
      (id,title,command_text,state,mode,created_at,updated_at,
       owner_identity_uid,owner_tenant_key,owner_project_key,owner_verified_at)
      VALUES ('88888888-8888-4888-8888-888888888888',
      'bad','bad','QUEUED','BALANCED',now(),now(),42,'tenant-alpha','PROJECT-TAMPERED',now());
    RAISE EXCEPTION 'Malformed project identifier was accepted';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
END
$assert_constraints$;

DO $assert_no_invalid_rows$
BEGIN
  IF (SELECT COUNT(*) FROM aetheris_tasks) != 4 THEN
    RAISE EXCEPTION 'Unexpected accepted invalid test rows';
  END IF;
END
$assert_no_invalid_rows$;

-- No changes survive. CI runs only against a throwaway PostgreSQL container.
ROLLBACK;
\echo AETHERIS_DISPOSABLE_PG16_TASK_OWNER_QUARANTINE_AND_TENANT_DENIAL_PASS

-- S0-C disposable-only adversarial assertions after V1/V2.
-- Every synthetic row written by these tests rolls back.
\set ON_ERROR_STOP on
BEGIN;
INSERT INTO registry_tenants(tenant_key,status) VALUES
 ('alpha','ACTIVE'),('beta','ACTIVE');
INSERT INTO registry_projects(tenant_key,project_key,status) VALUES
 ('alpha','red','ACTIVE'),('alpha','blue','ACTIVE'),('beta','red','ACTIVE');
INSERT INTO registry_tenant_memberships
 (tenant_key,identity_uid,member_role,status,valid_until) VALUES
 ('alpha',42,'OWNER','ACTIVE',now()+interval '1 day'),
 ('beta',42,'MEMBER','ACTIVE',now()+interval '1 day'),
 ('alpha',77,'MEMBER','ACTIVE',now()+interval '1 day');
INSERT INTO registry_project_memberships
 (tenant_key,project_key,identity_uid,member_role,status,valid_until) VALUES
 ('alpha','red',42,'ADMIN','ACTIVE',now()+interval '1 day'),
 ('beta','red',42,'MEMBER','ACTIVE',now()+interval '1 day'),
 ('alpha','blue',77,'MEMBER','ACTIVE',now()+interval '1 day');

INSERT INTO aetheris_tasks
 (id,title,command_text,state,mode,created_at,updated_at,version,
  owner_identity_uid,owner_tenant_key,owner_project_key,owner_verified_at)
 VALUES
 ('22222222-2222-4222-8222-222222222222','Synthetic Alice',
  'No commands','QUEUED','BALANCED',now(),now(),0,42,'alpha','red',now()),
 ('33333333-3333-4333-8333-333333333333','Synthetic Alice Beta',
  'No commands','QUEUED','BALANCED',now(),now(),0,42,'beta','red',now()),
 ('44444444-4444-4444-8444-444444444444','Synthetic Bob',
  'No commands','QUEUED','BALANCED',now(),now(),0,77,'alpha','blue',now());

-- A selector by itself grants no read. This is SQL test scaffolding, not
-- authenticated HTTP/JDBC service code or real JWT verification.
CREATE FUNCTION pg_temp.s0_candidate_read(p_uid bigint,p_tenant text,p_project text,p_id uuid)
RETURNS boolean LANGUAGE sql VOLATILE SECURITY INVOKER SET search_path=public,pg_temp AS $query$
 SELECT EXISTS(
  SELECT 1 FROM aetheris_tasks t
  JOIN registry_tenants tenant ON tenant.tenant_key=t.owner_tenant_key
  JOIN registry_projects project ON
    project.tenant_key=t.owner_tenant_key AND project.project_key=t.owner_project_key
  JOIN registry_tenant_memberships tm ON
    tm.tenant_key=t.owner_tenant_key AND tm.identity_uid=t.owner_identity_uid
  JOIN registry_project_memberships pm ON
    pm.tenant_key=t.owner_tenant_key
    AND pm.project_key=t.owner_project_key AND pm.identity_uid=t.owner_identity_uid
  WHERE t.id=p_id AND t.owner_identity_uid=p_uid
    AND t.owner_tenant_key=p_tenant AND t.owner_project_key=p_project
    AND t.owner_verified_at IS NOT NULL
    AND tenant.status='ACTIVE' AND project.status='ACTIVE'
    AND tm.status='ACTIVE' AND tm.revoked_at IS NULL
    AND (tm.valid_until IS NULL OR tm.valid_until > clock_timestamp())
    AND pm.status='ACTIVE' AND pm.revoked_at IS NULL
    AND (pm.valid_until IS NULL OR pm.valid_until > clock_timestamp())
 )
$query$;

DO $checks$
BEGIN
 IF NOT pg_temp.s0_candidate_read(42,'alpha','red',
    '22222222-2222-4222-8222-222222222222')
 OR NOT pg_temp.s0_candidate_read(42,'beta','red',
    '33333333-3333-4333-8333-333333333333')
 OR NOT pg_temp.s0_candidate_read(77,'alpha','blue',
    '44444444-4444-4444-8444-444444444444') THEN
    RAISE EXCEPTION 'registered synthetic owner was rejected';
 END IF;
 IF pg_temp.s0_candidate_read(77,'alpha','red',
    '22222222-2222-4222-8222-222222222222')
 OR pg_temp.s0_candidate_read(42,'beta','red',
    '22222222-2222-4222-8222-222222222222')
 OR pg_temp.s0_candidate_read(42,'alpha','blue',
    '44444444-4444-4444-8444-444444444444')
 OR pg_temp.s0_candidate_read(NULL,'alpha','red',
    '22222222-2222-4222-8222-222222222222')
 OR pg_temp.s0_candidate_read(42,'alpha','red',
    '11111111-1111-4111-8111-111111111111') THEN
    RAISE EXCEPTION 'cross-owner, cross-tenant, or ownerless read';
 END IF;
 IF (SELECT count(*) FROM aetheris_tasks WHERE owner_identity_uid IS NULL
     AND (owner_verified_at IS NOT NULL OR owner_tenant_key IS NOT NULL
          OR owner_project_key IS NOT NULL)) <> 0 THEN
   RAISE EXCEPTION 'historical ownerless tasks unexpectedly bound';
 END IF;
END
$checks$;

-- Normal state updates must keep working while owner binding is immutable.
UPDATE aetheris_tasks SET state='QUEUED',version=version+1
 WHERE id='22222222-2222-4222-8222-222222222222';
DO $positive_update$
BEGIN
 IF (SELECT version FROM aetheris_tasks
   WHERE id='22222222-2222-4222-8222-222222222222') <> 1 THEN
  RAISE EXCEPTION 'ordinary task update blocked';
 END IF;
END
$positive_update$;

DO $negatives$
BEGIN
 BEGIN
  INSERT INTO registry_projects(tenant_key,project_key,status)
   VALUES ('unknown','red','ACTIVE');
  RAISE EXCEPTION 'project with no tenant accepted';
 EXCEPTION WHEN foreign_key_violation THEN NULL; END;
 BEGIN
  INSERT INTO registry_project_memberships
   (tenant_key,project_key,identity_uid,member_role,status)
   VALUES('beta','red',77,'MEMBER','ACTIVE');
  RAISE EXCEPTION 'orphan tenant/project membership accepted';
 EXCEPTION WHEN foreign_key_violation THEN NULL; END;
 BEGIN
  INSERT INTO aetheris_tasks
   (id,title,command_text,state,mode,created_at,updated_at,
    owner_identity_uid,owner_tenant_key,owner_project_key,owner_verified_at)
   VALUES('55555555-5555-4555-8555-555555555555','Bad','No commands',
    'QUEUED','BALANCED',now(),now(),42,'beta','blue',now());
  RAISE EXCEPTION 'nonexistent tenant-qualified project accepted';
 EXCEPTION WHEN foreign_key_violation THEN NULL; END;
 BEGIN
  INSERT INTO aetheris_tasks
   (id,title,command_text,state,mode,created_at,updated_at,
    owner_identity_uid,owner_tenant_key,owner_project_key,owner_verified_at)
   VALUES('66666666-6666-4666-8666-666666666666','Bad','No commands',
    'QUEUED','BALANCED',now(),now(),42,'alpha',NULL,now());
  RAISE EXCEPTION 'partial owner binding accepted';
 EXCEPTION WHEN check_violation THEN NULL; END;
 BEGIN
  UPDATE aetheris_tasks SET owner_identity_uid=77
   WHERE id='22222222-2222-4222-8222-222222222222';
  RAISE EXCEPTION 'unreviewed owner transfer accepted';
 EXCEPTION WHEN check_violation THEN NULL; END;
 BEGIN
  UPDATE aetheris_tasks SET owner_identity_uid=42,
   owner_tenant_key='alpha',owner_project_key='red',owner_verified_at=now()
   WHERE id='11111111-1111-4111-8111-111111111111';
  -- On the empty cohort the UUID does not exist; the legacy cohort must reject.
  -- The legacy-specific assertion is performed separately after migration.
 EXCEPTION WHEN check_violation THEN NULL; END;
 BEGIN
  DELETE FROM registry_project_memberships
   WHERE tenant_key='alpha' AND project_key='red' AND identity_uid=42;
  RAISE EXCEPTION 'hard deleting bound membership accepted';
 EXCEPTION WHEN foreign_key_violation THEN NULL; END;
END
$negatives$;

UPDATE registry_tenant_memberships
 SET status='REVOKED',revoked_at=now(),version=version+1
 WHERE tenant_key='alpha' AND identity_uid=42;
DO $revoke_tenant$
BEGIN
 IF pg_temp.s0_candidate_read(42,'alpha','red',
    '22222222-2222-4222-8222-222222222222')
 OR NOT pg_temp.s0_candidate_read(42,'beta','red',
    '33333333-3333-4333-8333-333333333333') THEN
  RAISE EXCEPTION 'tenant revocation not enforced per tenant';
 END IF;
END
$revoke_tenant$;
UPDATE registry_tenant_memberships
 SET status='ACTIVE',revoked_at=NULL,version=version+1
 WHERE tenant_key='alpha' AND identity_uid=42;
UPDATE registry_project_memberships
 SET status='REVOKED',revoked_at=now(),version=version+1
 WHERE tenant_key='alpha' AND project_key='red' AND identity_uid=42;
DO $revoke_project$
BEGIN
 IF pg_temp.s0_candidate_read(42,'alpha','red',
    '22222222-2222-4222-8222-222222222222') THEN
  RAISE EXCEPTION 'project membership revocation not enforced';
 END IF;
END
$revoke_project$;
UPDATE registry_project_memberships
 SET status='ACTIVE',revoked_at=NULL,valid_until=now()-interval '1 minute'
 WHERE tenant_key='alpha' AND project_key='red' AND identity_uid=42;
DO $expire$
BEGIN
 IF pg_temp.s0_candidate_read(42,'alpha','red',
    '22222222-2222-4222-8222-222222222222') THEN
  RAISE EXCEPTION 'expired project membership not enforced';
 END IF;
END
$expire$;
ROLLBACK;
\echo AETHERIS_S0_VERSIONED_MIGRATION_FK_OWNER_QUARANTINE_REVOCATION_PASS

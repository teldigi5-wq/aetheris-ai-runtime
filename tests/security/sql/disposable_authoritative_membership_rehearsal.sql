-- REVIEW ONLY. Every object is synthetic in a disposable PostgreSQL16 transaction.
-- No claim that a caller-supplied UID/tenant is authenticated: an actual
-- backend MUST bind the UID from its independently verified RS256 principal
-- and tenant/project IDs from an authoritative membership lookup.
\set ON_ERROR_STOP on
BEGIN;
CREATE TEMP TABLE aetheris_tasks (
  id uuid PRIMARY KEY, title varchar(160) NOT NULL,
  command_text varchar(8000) NOT NULL, state varchar(32) NOT NULL,
  mode varchar(32) NOT NULL, active_agent_id varchar(120),
  created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
  version bigint NOT NULL DEFAULT 0
);
\ir candidate_task_owner_binding_additive.sql

CREATE TEMP TABLE tenant_memberships (
  tenant_key varchar(96) NOT NULL
    CHECK (tenant_key ~ '^[a-z0-9][a-z0-9_-]{0,95}$'),
  identity_uid bigint NOT NULL CHECK (identity_uid > 0),
  status varchar(16) NOT NULL CHECK (status IN ('ACTIVE', 'REVOKED')),
  revoked_at timestamptz,
  valid_until timestamptz,
  PRIMARY KEY (tenant_key, identity_uid),
  CHECK ((status = 'ACTIVE' AND revoked_at IS NULL)
      OR (status = 'REVOKED' AND revoked_at IS NOT NULL))
);
CREATE TEMP TABLE project_memberships (
  tenant_key varchar(96) NOT NULL,
  project_key varchar(96) NOT NULL
    CHECK (project_key ~ '^[a-z0-9][a-z0-9_-]{0,95}$'),
  identity_uid bigint NOT NULL CHECK (identity_uid > 0),
  status varchar(16) NOT NULL CHECK (status IN ('ACTIVE', 'REVOKED')),
  revoked_at timestamptz,
  valid_until timestamptz,
  PRIMARY KEY (tenant_key, project_key, identity_uid),
  FOREIGN KEY (tenant_key, identity_uid)
    REFERENCES tenant_memberships (tenant_key, identity_uid),
  CHECK ((status = 'ACTIVE' AND revoked_at IS NULL)
      OR (status = 'REVOKED' AND revoked_at IS NOT NULL))
);

-- Authorized synthetic memberships are persisted separately from each
-- task's creator/owner; a signed UID alone cannot confer tenant membership.
INSERT INTO tenant_memberships(tenant_key, identity_uid, status, valid_until) VALUES
 ('tenant-alpha',42,'ACTIVE',now()+interval '1 day'),
 ('tenant-alpha',77,'ACTIVE',now()+interval '1 day'),
 ('tenant-beta',42,'ACTIVE',now()+interval '1 day'),
 ('tenant-alpha',99,'REVOKED',NULL);
UPDATE tenant_memberships SET revoked_at=now()
 WHERE tenant_key='tenant-alpha' AND identity_uid=99;
INSERT INTO project_memberships(tenant_key,project_key,identity_uid,status,valid_until) VALUES
 ('tenant-alpha','project-red',42,'ACTIVE',now()+interval '1 day'),
 ('tenant-alpha','project-blue',77,'ACTIVE',now()+interval '1 day'),
 ('tenant-beta','project-red',42,'ACTIVE',now()+interval '1 day'),
 ('tenant-alpha','project-red',99,'REVOKED',NULL);
UPDATE project_memberships SET revoked_at=now()
 WHERE tenant_key='tenant-alpha' AND identity_uid=99;

INSERT INTO aetheris_tasks(id,title,command_text,state,mode,created_at,updated_at,
 owner_identity_uid,owner_tenant_key,owner_project_key,owner_verified_at) VALUES
 ('11111111-1111-4111-8111-111111111111','old private task','legacy content','QUEUED','BALANCED',now(),now(),
  NULL,NULL,NULL,NULL),
 ('22222222-2222-4222-8222-222222222222','Alice alpha red','synthetic','QUEUED','BALANCED',now(),now(),
  42,'tenant-alpha','project-red',now()),
 ('33333333-3333-4333-8333-333333333333','Alice beta red','synthetic','QUEUED','BALANCED',now(),now(),
  42,'tenant-beta','project-red',now()),
 ('44444444-4444-4444-8444-444444444444','Bob alpha blue','synthetic','QUEUED','BALANCED',now(),now(),
  77,'tenant-alpha','project-blue',now()),
 ('55555555-5555-4555-8555-555555555555','Revoked Eve alpha','synthetic','QUEUED','BALANCED',now(),now(),
  99,'tenant-alpha','project-red',now());

-- Query contract. The external application MUST supply p_identity_uid
-- derived from a cryptographically verified Identity JWT, never a header.
-- p_tenant and p_project are selectors, never proof of membership.
-- SECURITY INVOKER + pg_temp avoids privilege escalation and any persisted
-- stored procedure. This is a disposable query PROTOTYPE, not a live API.
CREATE FUNCTION pg_temp.can_read_task(
 p_identity_uid bigint,p_tenant text,p_project text,p_task uuid)
RETURNS boolean LANGUAGE sql STABLE SECURITY INVOKER
SET search_path = pg_temp AS $authorized$
 SELECT EXISTS (
  SELECT 1 FROM aetheris_tasks t
  JOIN tenant_memberships tm ON
    tm.tenant_key=t.owner_tenant_key AND tm.identity_uid=p_identity_uid
  JOIN project_memberships pm ON
    pm.tenant_key=t.owner_tenant_key
    AND pm.project_key=t.owner_project_key
    AND pm.identity_uid=p_identity_uid
  WHERE t.id=p_task
    AND t.owner_identity_uid=p_identity_uid
    AND t.owner_tenant_key=p_tenant
    AND t.owner_project_key=p_project
    AND t.owner_verified_at IS NOT NULL
    AND tm.status='ACTIVE' AND tm.revoked_at IS NULL
    AND (tm.valid_until IS NULL OR tm.valid_until>now())
    AND pm.status='ACTIVE' AND pm.revoked_at IS NULL
    AND (pm.valid_until IS NULL OR pm.valid_until>now())
 )
$authorized$;

DO $isolation$
BEGIN
 IF NOT pg_temp.can_read_task(42,'tenant-alpha','project-red',
     '22222222-2222-4222-8222-222222222222') THEN
  RAISE EXCEPTION 'Active Alice alpha project membership denied unexpectedly';
 END IF;
 IF NOT pg_temp.can_read_task(42,'tenant-beta','project-red',
     '33333333-3333-4333-8333-333333333333') THEN
  RAISE EXCEPTION 'Multi-tenant Alice with separate membership denied';
 END IF;
 IF NOT pg_temp.can_read_task(77,'tenant-alpha','project-blue',
     '44444444-4444-4444-8444-444444444444') THEN
  RAISE EXCEPTION 'Bob distinct owner/project denied';
 END IF;
 IF pg_temp.can_read_task(42,'tenant-alpha','project-red',
     '11111111-1111-4111-8111-111111111111') THEN
  RAISE EXCEPTION 'Historic ownerless task leaked';
 END IF;
 IF pg_temp.can_read_task(77,'tenant-alpha','project-red',
     '22222222-2222-4222-8222-222222222222') THEN
  RAISE EXCEPTION 'Different UID accessed Alice task in same tenant';
 END IF;
 IF pg_temp.can_read_task(42,'tenant-alpha','project-blue',
     '44444444-4444-4444-8444-444444444444') THEN
  RAISE EXCEPTION 'Alice project-selector spoof read Bob task';
 END IF;
 IF pg_temp.can_read_task(42,'tenant-beta','project-red',
     '22222222-2222-4222-8222-222222222222') THEN
  RAISE EXCEPTION 'Same signed UID confused tenant boundary';
 END IF;
 IF pg_temp.can_read_task(99,'tenant-alpha','project-red',
     '55555555-5555-4555-8555-555555555555') THEN
  RAISE EXCEPTION 'Revoked tenant/project member accessed owned task';
 END IF;
 IF pg_temp.can_read_task(NULL,'tenant-alpha','project-red',
     '22222222-2222-4222-8222-222222222222') THEN
  RAISE EXCEPTION 'Missing verified principal unexpectedly authorized';
 END IF;
 IF pg_temp.can_read_task(42,NULL,'project-red',
     '22222222-2222-4222-8222-222222222222') THEN
  RAISE EXCEPTION 'Missing tenant unexpectedly authorized';
 END IF;
 IF pg_temp.can_read_task(42,'TENANT-ALPHA','project-red',
     '22222222-2222-4222-8222-222222222222') THEN
  RAISE EXCEPTION 'Malformed client tenant unexpectedly authorized';
 END IF;
END
$isolation$;

-- Membership revocation must take effect on the next scoped SQL read.
UPDATE tenant_memberships
 SET status='REVOKED',revoked_at=now()
 WHERE tenant_key='tenant-alpha' AND identity_uid=42;
DO $revoke_tenant$
BEGIN
 IF pg_temp.can_read_task(42,'tenant-alpha','project-red',
     '22222222-2222-4222-8222-222222222222') THEN
   RAISE EXCEPTION 'Tenant membership revocation failed';
 END IF;
 IF NOT pg_temp.can_read_task(42,'tenant-beta','project-red',
     '33333333-3333-4333-8333-333333333333') THEN
   RAISE EXCEPTION 'Revoking tenant alpha incorrectly revoked beta';
 END IF;
END
$revoke_tenant$;
UPDATE tenant_memberships SET status='ACTIVE',revoked_at=NULL
 WHERE tenant_key='tenant-alpha' AND identity_uid=42;
UPDATE project_memberships SET status='REVOKED',revoked_at=now()
 WHERE tenant_key='tenant-alpha' AND project_key='project-red' AND identity_uid=42;
DO $revoke_project$
BEGIN
 IF pg_temp.can_read_task(42,'tenant-alpha','project-red',
     '22222222-2222-4222-8222-222222222222') THEN
   RAISE EXCEPTION 'Project membership revocation failed';
 END IF;
END
$revoke_project$;
UPDATE project_memberships SET status='ACTIVE',revoked_at=NULL,
 valid_until=now()-interval '1 minute'
 WHERE tenant_key='tenant-alpha' AND project_key='project-red' AND identity_uid=42;
DO $expire_project$
BEGIN
 IF pg_temp.can_read_task(42,'tenant-alpha','project-red',
     '22222222-2222-4222-8222-222222222222') THEN
   RAISE EXCEPTION 'Expired project membership unexpectedly authorized';
 END IF;
END
$expire_project$;

-- Malformed/forged memberships must fail DB-side constraints independently.
DO $negative_bindings$
BEGIN
 BEGIN
  INSERT INTO project_memberships(tenant_key,project_key,identity_uid,status)
    VALUES ('tenant-other','project-red',42,'ACTIVE');
  RAISE EXCEPTION 'Unregistered tenant/project membership accepted';
 EXCEPTION WHEN foreign_key_violation THEN NULL;
 END;
 BEGIN
  INSERT INTO tenant_memberships(tenant_key,identity_uid,status)
    VALUES ('tenant-fake',0,'ACTIVE');
  RAISE EXCEPTION 'Invalid identity UID membership accepted';
 EXCEPTION WHEN check_violation THEN NULL;
 END;
 BEGIN
  INSERT INTO project_memberships(tenant_key,project_key,identity_uid,status)
    VALUES ('tenant-alpha','project-red',42,'SUPERADMIN');
  RAISE EXCEPTION 'Invalid project membership role accepted';
 EXCEPTION WHEN check_violation THEN NULL;
 END;
 BEGIN
  INSERT INTO tenant_memberships(tenant_key,identity_uid,status,revoked_at)
    VALUES ('tenant-fake',88,'ACTIVE',now());
  RAISE EXCEPTION 'Revoked-at timestamp combined with ACTIVE accepted';
 EXCEPTION WHEN check_violation THEN NULL;
 END;
END
$negative_bindings$;

-- Absolutely no schema or membership data is committed to the CI database.
ROLLBACK;
\echo AETHERIS_REAL_PG16_AUTHORITATIVE_MEMBERSHIP_PROTOTYPE_ISOLATION_AND_REVOCATION_PASS

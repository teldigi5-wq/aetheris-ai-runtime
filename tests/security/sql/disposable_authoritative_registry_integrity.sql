-- Security S0 / 29 Oct 2026. REVIEW-ONLY PostgreSQL16 proof.
-- Disposable CI database only; TEMP objects, no migration or application mapping.
-- UID is a synthetic test identity here; real authorization MUST derive it from
-- RuntimePinnedPrincipalVerifier.VerifiedPrincipal, never caller headers/parameters.
\set ON_ERROR_STOP on
BEGIN;

CREATE TEMP TABLE registry_tenants (
  tenant_key varchar(96) PRIMARY KEY
    CHECK (tenant_key ~ '^[a-z0-9][a-z0-9_-]{0,95}$'),
  status varchar(16) NOT NULL CHECK (status IN ('ACTIVE','SUSPENDED','CLOSED')),
  version bigint NOT NULL DEFAULT 0 CHECK (version >= 0)
);
CREATE TEMP TABLE registry_projects (
  tenant_key varchar(96) NOT NULL REFERENCES registry_tenants(tenant_key),
  project_key varchar(96) NOT NULL
    CHECK (project_key ~ '^[a-z0-9][a-z0-9_-]{0,95}$'),
  status varchar(16) NOT NULL CHECK (status IN ('ACTIVE','SUSPENDED','CLOSED')),
  version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
  PRIMARY KEY(tenant_key,project_key)
);
CREATE TEMP TABLE registry_tenant_memberships (
  tenant_key varchar(96) NOT NULL REFERENCES registry_tenants(tenant_key),
  identity_uid bigint NOT NULL CHECK (identity_uid > 0),
  member_role varchar(16) NOT NULL CHECK (member_role IN ('OWNER','ADMIN','MEMBER','VIEWER')),
  status varchar(16) NOT NULL CHECK (status IN ('ACTIVE','REVOKED')),
  revoked_at timestamptz,
  valid_until timestamptz,
  version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
  PRIMARY KEY(tenant_key,identity_uid),
  CONSTRAINT tenant_revoke_consistency CHECK (
    (status='ACTIVE' AND revoked_at IS NULL) OR
    (status='REVOKED' AND revoked_at IS NOT NULL)
  )
);
CREATE TEMP TABLE registry_project_memberships (
  tenant_key varchar(96) NOT NULL,
  project_key varchar(96) NOT NULL,
  identity_uid bigint NOT NULL CHECK (identity_uid > 0),
  member_role varchar(16) NOT NULL CHECK (member_role IN ('ADMIN','MEMBER','VIEWER')),
  status varchar(16) NOT NULL CHECK (status IN ('ACTIVE','REVOKED')),
  revoked_at timestamptz,
  valid_until timestamptz,
  version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
  PRIMARY KEY(tenant_key,project_key,identity_uid),
  FOREIGN KEY(tenant_key,project_key)
    REFERENCES registry_projects(tenant_key,project_key),
  FOREIGN KEY(tenant_key,identity_uid)
    REFERENCES registry_tenant_memberships(tenant_key,identity_uid),
  CONSTRAINT project_revoke_consistency CHECK (
    (status='ACTIVE' AND revoked_at IS NULL) OR
    (status='REVOKED' AND revoked_at IS NOT NULL)
  )
);
CREATE TEMP TABLE registry_tasks (
  id uuid PRIMARY KEY,
  owner_identity_uid bigint,
  owner_tenant_key varchar(96),
  owner_project_key varchar(96),
  owner_verified_at timestamptz,
  CONSTRAINT complete_owner_binding CHECK (
    (owner_identity_uid IS NULL AND owner_tenant_key IS NULL
      AND owner_project_key IS NULL AND owner_verified_at IS NULL)
    OR
    (owner_identity_uid > 0 AND owner_tenant_key IS NOT NULL
      AND owner_project_key IS NOT NULL AND owner_verified_at IS NOT NULL)
  ),
  FOREIGN KEY(owner_tenant_key,owner_project_key,owner_identity_uid)
    REFERENCES registry_project_memberships(tenant_key,project_key,identity_uid)
);
-- The owner binding is immutable in this review-only proof. Historical
-- ownerless rows cannot be silently claimed or reassigned via normal UPDATE.
-- Any future legitimate transfer requires an explicit audited policy/transaction.
CREATE FUNCTION pg_temp.deny_owner_binding_update() RETURNS trigger
LANGUAGE plpgsql SECURITY INVOKER SET search_path=pg_temp AS $guard$
BEGIN
  IF ROW(NEW.owner_identity_uid,NEW.owner_tenant_key,NEW.owner_project_key,NEW.owner_verified_at)
     IS DISTINCT FROM
     ROW(OLD.owner_identity_uid,OLD.owner_tenant_key,OLD.owner_project_key,OLD.owner_verified_at) THEN
    RAISE EXCEPTION 'task owner binding is immutable' USING ERRCODE='23514';
  END IF;
  RETURN NEW;
END
$guard$;
CREATE TRIGGER registry_task_binding_lock BEFORE UPDATE ON registry_tasks
FOR EACH ROW EXECUTE FUNCTION pg_temp.deny_owner_binding_update();

INSERT INTO registry_tenants(tenant_key,status) VALUES
 ('alpha','ACTIVE'),('beta','ACTIVE'),('suspended','SUSPENDED');
INSERT INTO registry_projects(tenant_key,project_key,status) VALUES
 ('alpha','red','ACTIVE'),('alpha','blue','ACTIVE'),
 ('beta','red','ACTIVE'),('suspended','red','ACTIVE');
INSERT INTO registry_tenant_memberships
 (tenant_key,identity_uid,member_role,status,valid_until) VALUES
 ('alpha',42,'OWNER','ACTIVE',now()+interval '1 day'),
 ('beta',42,'MEMBER','ACTIVE',now()+interval '1 day'),
 ('alpha',77,'MEMBER','ACTIVE',now()+interval '1 day'),
 ('suspended',42,'MEMBER','ACTIVE',now()+interval '1 day'),
 ('alpha',99,'VIEWER','ACTIVE',now()-interval '1 minute');
INSERT INTO registry_project_memberships
 (tenant_key,project_key,identity_uid,member_role,status,valid_until) VALUES
 ('alpha','red',42,'ADMIN','ACTIVE',now()+interval '1 day'),
 ('beta','red',42,'MEMBER','ACTIVE',now()+interval '1 day'),
 ('alpha','blue',77,'MEMBER','ACTIVE',now()+interval '1 day'),
 ('suspended','red',42,'MEMBER','ACTIVE',now()+interval '1 day'),
 ('alpha','red',99,'VIEWER','ACTIVE',now()+interval '1 day');
INSERT INTO registry_tasks(id,owner_identity_uid,owner_tenant_key,owner_project_key,owner_verified_at)
 VALUES
 ('11111111-1111-4111-8111-111111111111',NULL,NULL,NULL,NULL),
 ('22222222-2222-4222-8222-222222222222',42,'alpha','red',now()),
 ('33333333-3333-4333-8333-333333333333',42,'beta','red',now()),
 ('44444444-4444-4444-8444-444444444444',77,'alpha','blue',now()),
 ('55555555-5555-4555-8555-555555555555',42,'suspended','red',now()),
 ('66666666-6666-4666-8666-666666666666',99,'alpha','red',now());

-- This query is NOT an API and cannot authenticate UID parameters.
-- A future JDBC resource operation MUST bind UID to the verified principal
-- inside the same transaction and use authorized repository predicates.
CREATE FUNCTION pg_temp.registry_can_read_task(
 p_identity_uid bigint,p_tenant text,p_project text,p_task uuid)
RETURNS boolean LANGUAGE sql VOLATILE SECURITY INVOKER SET search_path=pg_temp AS $read$
 SELECT EXISTS(
  SELECT 1 FROM registry_tasks t
  JOIN registry_tenants tenant ON tenant.tenant_key=t.owner_tenant_key
  JOIN registry_projects project ON project.tenant_key=t.owner_tenant_key
     AND project.project_key=t.owner_project_key
  JOIN registry_tenant_memberships tm ON tm.tenant_key=t.owner_tenant_key
     AND tm.identity_uid=t.owner_identity_uid
  JOIN registry_project_memberships pm ON pm.tenant_key=t.owner_tenant_key
     AND pm.project_key=t.owner_project_key AND pm.identity_uid=t.owner_identity_uid
  WHERE t.id=p_task AND t.owner_identity_uid=p_identity_uid
    AND t.owner_tenant_key=p_tenant AND t.owner_project_key=p_project
    AND t.owner_verified_at IS NOT NULL
    AND tenant.status='ACTIVE' AND project.status='ACTIVE'
    AND tm.status='ACTIVE' AND tm.revoked_at IS NULL
    AND (tm.valid_until IS NULL OR tm.valid_until>clock_timestamp())
    AND pm.status='ACTIVE' AND pm.revoked_at IS NULL
    AND (pm.valid_until IS NULL OR pm.valid_until>clock_timestamp())
 )
$read$;

DO $positive$
BEGIN
 IF NOT pg_temp.registry_can_read_task(42,'alpha','red','22222222-2222-4222-8222-222222222222')
 OR NOT pg_temp.registry_can_read_task(42,'beta','red','33333333-3333-4333-8333-333333333333')
 OR NOT pg_temp.registry_can_read_task(77,'alpha','blue','44444444-4444-4444-8444-444444444444')
 THEN RAISE EXCEPTION 'active independently registered ownership denied'; END IF;
 IF pg_temp.registry_can_read_task(77,'alpha','red','22222222-2222-4222-8222-222222222222')
 OR pg_temp.registry_can_read_task(42,'beta','red','22222222-2222-4222-8222-222222222222')
 OR pg_temp.registry_can_read_task(42,'alpha','blue','44444444-4444-4444-8444-444444444444')
 OR pg_temp.registry_can_read_task(42,'alpha','red','11111111-1111-4111-8111-111111111111')
 OR pg_temp.registry_can_read_task(42,'suspended','red','55555555-5555-4555-8555-555555555555')
 OR pg_temp.registry_can_read_task(99,'alpha','red','66666666-6666-4666-8666-666666666666')
 OR pg_temp.registry_can_read_task(NULL,'alpha','red','22222222-2222-4222-8222-222222222222')
 THEN RAISE EXCEPTION 'cross-boundary, suspended, expired or ownerless read'; END IF;
END
$positive$;

UPDATE registry_projects SET status='SUSPENDED'
 WHERE tenant_key='alpha' AND project_key='red';
DO $deny_project$
BEGIN
 IF pg_temp.registry_can_read_task(42,'alpha','red','22222222-2222-4222-8222-222222222222')
 THEN RAISE EXCEPTION 'suspended project still readable'; END IF;
END
$deny_project$;
UPDATE registry_projects SET status='ACTIVE'
 WHERE tenant_key='alpha' AND project_key='red';
UPDATE registry_tenant_memberships SET status='REVOKED',revoked_at=now(),version=version+1
 WHERE tenant_key='alpha' AND identity_uid=42;
DO $deny_tenant_revoke$
BEGIN
 IF pg_temp.registry_can_read_task(42,'alpha','red','22222222-2222-4222-8222-222222222222')
 OR NOT pg_temp.registry_can_read_task(42,'beta','red','33333333-3333-4333-8333-333333333333')
 THEN RAISE EXCEPTION 'tenant revocation failed isolation'; END IF;
END
$deny_tenant_revoke$;
UPDATE registry_tenant_memberships SET status='ACTIVE',revoked_at=NULL,version=version+1
 WHERE tenant_key='alpha' AND identity_uid=42;
UPDATE registry_project_memberships SET status='REVOKED',revoked_at=now(),version=version+1
 WHERE tenant_key='alpha' AND project_key='red' AND identity_uid=42;
DO $deny_project_revoke$
BEGIN
 IF pg_temp.registry_can_read_task(42,'alpha','red','22222222-2222-4222-8222-222222222222')
 THEN RAISE EXCEPTION 'project revocation failed'; END IF;
END
$deny_project_revoke$;

DO $integrity$
BEGIN
 BEGIN
  INSERT INTO registry_projects VALUES('unknown','red','ACTIVE',0);
  RAISE EXCEPTION 'orphan project accepted';
 EXCEPTION WHEN foreign_key_violation THEN NULL;
 END;
 BEGIN
  INSERT INTO registry_project_memberships
   (tenant_key,project_key,identity_uid,member_role,status)
   VALUES('beta','red',77,'MEMBER','ACTIVE');
  RAISE EXCEPTION 'tenant membership mismatch accepted';
 EXCEPTION WHEN foreign_key_violation THEN NULL;
 END;
 BEGIN
  INSERT INTO registry_project_memberships
   (tenant_key,project_key,identity_uid,member_role,status)
   VALUES('alpha','unknown',42,'MEMBER','ACTIVE');
  RAISE EXCEPTION 'orphan project membership accepted';
 EXCEPTION WHEN foreign_key_violation THEN NULL;
 END;
 BEGIN
  INSERT INTO registry_tasks(id,owner_identity_uid,owner_tenant_key,owner_project_key,owner_verified_at)
   VALUES('77777777-7777-4777-8777-777777777777',42,'beta','blue',now());
  RAISE EXCEPTION 'task bound to foreign project accepted';
 EXCEPTION WHEN foreign_key_violation THEN NULL;
 END;
 BEGIN
  INSERT INTO registry_tasks(id,owner_identity_uid,owner_tenant_key,owner_project_key,owner_verified_at)
   VALUES('88888888-8888-4888-8888-888888888888',42,'alpha',NULL,now());
  RAISE EXCEPTION 'partial owner binding accepted';
 EXCEPTION WHEN check_violation THEN NULL;
 END;
 BEGIN
  INSERT INTO registry_tenant_memberships(tenant_key,identity_uid,member_role,status,revoked_at)
   VALUES('beta',150,'VIEWER','ACTIVE',now());
  RAISE EXCEPTION 'active revoked timestamp accepted';
 EXCEPTION WHEN check_violation THEN NULL;
 END;
 BEGIN
  UPDATE registry_tasks SET owner_identity_uid=77
   WHERE id='22222222-2222-4222-8222-222222222222';
  RAISE EXCEPTION 'task owner transfer accepted silently';
 EXCEPTION WHEN check_violation THEN NULL;
 END;
 BEGIN
  UPDATE registry_tasks SET owner_identity_uid=42,owner_tenant_key='alpha',
   owner_project_key='red',owner_verified_at=now()
   WHERE id='11111111-1111-4111-8111-111111111111';
  RAISE EXCEPTION 'historical ownerless task silently claimed';
 EXCEPTION WHEN check_violation THEN NULL;
 END;
END
$integrity$;

-- No production schema or persistent records survive this CI proof.
ROLLBACK;
\echo AETHERIS_PG16_REGISTRY_PARENT_FK_LIFECYCLE_REVOCATION_IMMUTABLE_OWNER_PASS

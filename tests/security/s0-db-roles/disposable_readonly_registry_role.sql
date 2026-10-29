-- S0-D2: Disposable PostgreSQL16 only; execute as CI database owner/superuser.
-- Never run this fixture in a real/customer DB. It creates one throwaway login
-- with ONLY SELECT privileges necessary for a boolean authorization probe.
\set ON_ERROR_STOP on
CREATE ROLE s0_readonly_runtime WITH LOGIN NOINHERIT NOSUPERUSER
  NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS
  PASSWORD 's0_disposable_role_password_never_production';

-- Prevent PUBLIC inheritance of accidental table/schema privileges.
REVOKE ALL ON DATABASE s0read_disposable FROM PUBLIC;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
REVOKE ALL ON ALL TABLES IN SCHEMA public FROM PUBLIC;
GRANT CONNECT ON DATABASE s0read_disposable TO s0_readonly_runtime;
GRANT USAGE ON SCHEMA public TO s0_readonly_runtime;

INSERT INTO registry_tenants(tenant_key,status) VALUES
 ('alpha','ACTIVE'),('beta','ACTIVE'),('suspended','SUSPENDED');
INSERT INTO registry_projects(tenant_key,project_key,status) VALUES
 ('alpha','red','ACTIVE'),('alpha','blue','ACTIVE'),('beta','red','ACTIVE'),
 ('suspended','red','ACTIVE');
INSERT INTO registry_tenant_memberships
 (tenant_key,identity_uid,member_role,status,revoked_at,valid_until) VALUES
 ('alpha',42,'OWNER','ACTIVE',NULL,now()+interval '1 day'),
 ('beta',42,'MEMBER','ACTIVE',NULL,now()+interval '1 day'),
 ('alpha',77,'MEMBER','ACTIVE',NULL,now()+interval '1 day'),
 ('alpha',88,'VIEWER','ACTIVE',NULL,now()+interval '1 day'),
 ('alpha',99,'MEMBER','REVOKED',now(),NULL),
 ('suspended',42,'MEMBER','ACTIVE',NULL,now()+interval '1 day');
INSERT INTO registry_project_memberships
 (tenant_key,project_key,identity_uid,member_role,status,revoked_at,valid_until) VALUES
 ('alpha','red',42,'ADMIN','ACTIVE',NULL,now()+interval '1 day'),
 ('beta','red',42,'MEMBER','ACTIVE',NULL,now()+interval '1 day'),
 ('alpha','blue',77,'MEMBER','ACTIVE',NULL,now()+interval '1 day'),
 ('alpha','red',88,'VIEWER','ACTIVE',NULL,now()+interval '1 day'),
 ('alpha','red',99,'MEMBER','REVOKED',now(),NULL),
 ('suspended','red',42,'MEMBER','ACTIVE',NULL,now()+interval '1 day');

INSERT INTO aetheris_tasks
 (id,title,command_text,state,mode,created_at,updated_at,version,
  owner_identity_uid,owner_tenant_key,owner_project_key,owner_verified_at)
VALUES
 ('11111111-1111-4111-8111-111111111111','historic','private synthetic payload',
   'QUEUED','BALANCED',now(),now(),0,NULL,NULL,NULL,NULL),
 ('22222222-2222-4222-8222-222222222222','alice','private synthetic payload',
   'QUEUED','BALANCED',now(),now(),0,42,'alpha','red',now()),
 ('33333333-3333-4333-8333-333333333333','alice beta','private synthetic payload',
   'QUEUED','BALANCED',now(),now(),0,42,'beta','red',now()),
 ('44444444-4444-4444-8444-444444444444','bob','private synthetic payload',
   'QUEUED','BALANCED',now(),now(),0,77,'alpha','blue',now()),
 ('55555555-5555-4555-8555-555555555555','revoked','private synthetic payload',
   'QUEUED','BALANCED',now(),now(),0,99,'alpha','red',now()),
 ('66666666-6666-4666-8666-666666666666','suspended','private synthetic payload',
   'QUEUED','BALANCED',now(),now(),0,42,'suspended','red',now()),
 ('88888888-8888-4888-8888-888888888888','viewer','private synthetic payload',
   'QUEUED','BALANCED',now(),now(),0,88,'alpha','red',now());

-- A deliberately restricted, *metadata-only* SELECT grant. A bearer
-- carrying this DB password cannot select task title/command_text, write
-- task state, change membership, run DDL, or acquire elevated roles.
GRANT SELECT (id, owner_identity_uid, owner_tenant_key, owner_project_key,
              owner_verified_at)
  ON aetheris_tasks TO s0_readonly_runtime;
GRANT SELECT (tenant_key, status)
  ON registry_tenants TO s0_readonly_runtime;
GRANT SELECT (tenant_key, project_key, status)
  ON registry_projects TO s0_readonly_runtime;
GRANT SELECT (tenant_key, identity_uid, status, revoked_at, valid_until)
  ON registry_tenant_memberships TO s0_readonly_runtime;
GRANT SELECT (tenant_key, project_key, identity_uid, status,
              revoked_at, valid_until)
  ON registry_project_memberships TO s0_readonly_runtime;

-- PostgreSQL READ grants alone do not enforce ownership. The role can still
-- query allowed metadata columns across tenants directly. Do NOT expose
-- this role to an untrusted tenant or claim DB-enforced row-level isolation.
\echo AETHERIS_S0_RESTRICTED_DB_ROLE_METADATA_ONLY_GRANTED_PASS

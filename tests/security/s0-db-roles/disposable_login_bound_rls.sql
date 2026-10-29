-- S0-D3 disposable-only RLS fixture. Not a production migration or role policy.
-- The per-person database accounts here are deliberately synthetic, and are
-- unsuitable as an automatic production provisioning architecture.
\set ON_ERROR_STOP on

CREATE ROLE s0_rls_alice WITH LOGIN NOINHERIT NOSUPERUSER NOCREATEDB
  NOCREATEROLE NOREPLICATION NOBYPASSRLS
  PASSWORD 'synthetic_ci_alice_not_a_real_secret';
CREATE ROLE s0_rls_bob WITH LOGIN NOINHERIT NOSUPERUSER NOCREATEDB
  NOCREATEROLE NOREPLICATION NOBYPASSRLS
  PASSWORD 'synthetic_ci_bob_not_a_real_secret';

REVOKE ALL ON DATABASE s0rls_disposable FROM PUBLIC;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
REVOKE ALL ON ALL TABLES IN SCHEMA public FROM PUBLIC;
GRANT CONNECT ON DATABASE s0rls_disposable TO s0_rls_alice,s0_rls_bob;
GRANT USAGE ON SCHEMA public TO s0_rls_alice,s0_rls_bob;

-- Protected DB-login -> signed Identity numeric UID mapping. No SELECT, UPDATE
-- or other grants to the restricted roles; only the fixture owner controls it.
CREATE TABLE public.s0_rls_login_identity (
  login_role name PRIMARY KEY,
  identity_uid bigint NOT NULL UNIQUE CHECK (identity_uid > 0)
);
INSERT INTO public.s0_rls_login_identity(login_role,identity_uid)
 VALUES ('s0_rls_alice',42),('s0_rls_bob',77);

INSERT INTO public.registry_tenants(tenant_key,status) VALUES
 ('alpha','ACTIVE'),('beta','ACTIVE'),('suspended','SUSPENDED');
INSERT INTO public.registry_projects(tenant_key,project_key,status) VALUES
 ('alpha','red','ACTIVE'),('alpha','blue','ACTIVE'),('beta','red','ACTIVE'),
 ('suspended','red','ACTIVE');
INSERT INTO public.registry_tenant_memberships
 (tenant_key,identity_uid,member_role,status,revoked_at,valid_until)
 VALUES
 ('alpha',42,'OWNER','ACTIVE',NULL,now()+interval '1 day'),
 ('beta',42,'MEMBER','ACTIVE',NULL,now()+interval '1 day'),
 ('alpha',77,'MEMBER','ACTIVE',NULL,now()+interval '1 day'),
 ('alpha',99,'MEMBER','REVOKED',now(),NULL),
 ('suspended',42,'MEMBER','ACTIVE',NULL,now()+interval '1 day');
INSERT INTO public.registry_project_memberships
 (tenant_key,project_key,identity_uid,member_role,status,revoked_at,valid_until)
 VALUES
 ('alpha','red',42,'ADMIN','ACTIVE',NULL,now()+interval '1 day'),
 ('beta','red',42,'MEMBER','ACTIVE',NULL,now()+interval '1 day'),
 ('alpha','blue',77,'MEMBER','ACTIVE',NULL,now()+interval '1 day'),
 ('alpha','red',99,'MEMBER','REVOKED',now(),NULL),
 ('suspended','red',42,'MEMBER','ACTIVE',NULL,now()+interval '1 day');

INSERT INTO public.aetheris_tasks
 (id,title,command_text,state,mode,created_at,updated_at,version,
  owner_identity_uid,owner_tenant_key,owner_project_key,owner_verified_at)
 VALUES
 ('11111111-1111-4111-8111-111111111111','historic','synthetic secret old',
  'QUEUED','BALANCED',now(),now(),0,NULL,NULL,NULL,NULL),
 ('22222222-2222-4222-8222-222222222222','alice-alpha','synthetic secret alice',
  'QUEUED','BALANCED',now(),now(),0,42,'alpha','red',now()),
 ('33333333-3333-4333-8333-333333333333','alice-beta','synthetic secret alice beta',
  'QUEUED','BALANCED',now(),now(),0,42,'beta','red',now()),
 ('44444444-4444-4444-8444-444444444444','bob','synthetic secret bob',
  'QUEUED','BALANCED',now(),now(),0,77,'alpha','blue',now()),
 ('55555555-5555-4555-8555-555555555555','revoked','synthetic secret revoked',
  'QUEUED','BALANCED',now(),now(),0,99,'alpha','red',now()),
 ('66666666-6666-4666-8666-666666666666','suspended','synthetic secret suspended',
  'QUEUED','BALANCED',now(),now(),0,42,'suspended','red',now());

-- Function owner in disposable CI controls registry; runtime login cannot
-- shadow object names or change mapping. No caller-owned GUC/tenant header is
-- considered proof of identity. session_user is the authenticated DB login,
-- unlike current_user (which becomes the definer in SECURITY DEFINER).
CREATE FUNCTION public.s0_rls_bound_registry_owner(
    p_uid bigint, p_tenant varchar, p_project varchar, p_verified timestamptz)
RETURNS boolean
LANGUAGE sql VOLATILE SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $body$
 SELECT p_verified IS NOT NULL AND EXISTS (
  SELECT 1
  FROM public.s0_rls_login_identity identity
  JOIN public.registry_tenant_memberships tm ON
    tm.identity_uid = identity.identity_uid
  JOIN public.registry_tenants tenant ON tenant.tenant_key = tm.tenant_key
  JOIN public.registry_project_memberships pm ON
    pm.tenant_key = tm.tenant_key
    AND pm.identity_uid = tm.identity_uid
  JOIN public.registry_projects project ON
    project.tenant_key = pm.tenant_key AND project.project_key = pm.project_key
  WHERE identity.login_role = session_user::name
    AND identity.identity_uid = p_uid
    AND tenant.tenant_key = p_tenant AND project.project_key = p_project
    AND tenant.status='ACTIVE' AND project.status='ACTIVE'
    AND tm.status='ACTIVE' AND tm.revoked_at IS NULL
    AND (tm.valid_until IS NULL OR tm.valid_until > clock_timestamp())
    AND pm.status='ACTIVE' AND pm.revoked_at IS NULL
    AND (pm.valid_until IS NULL OR pm.valid_until > clock_timestamp())
 )
$body$;
REVOKE ALL ON FUNCTION public.s0_rls_bound_registry_owner(bigint,varchar,varchar,timestamptz)
 FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.s0_rls_bound_registry_owner(bigint,varchar,varchar,timestamptz)
 TO s0_rls_alice,s0_rls_bob;

ALTER TABLE public.aetheris_tasks ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.aetheris_tasks FORCE ROW LEVEL SECURITY;
CREATE POLICY s0_test_owner_login_rls ON public.aetheris_tasks
 AS PERMISSIVE FOR SELECT TO s0_rls_alice,s0_rls_bob
 USING (public.s0_rls_bound_registry_owner(
   owner_identity_uid,owner_tenant_key,owner_project_key,owner_verified_at));

-- Metadata-only. Database RLS filters every direct SQL query, even if
-- trusted Java is bypassed. Payload text never granted to these logins.
GRANT SELECT(id,owner_identity_uid,owner_tenant_key,owner_project_key,
  owner_verified_at) ON public.aetheris_tasks TO s0_rls_alice,s0_rls_bob;

-- Registry parents/memberships and protected DB identity mapping remain
-- completely inaccessible directly. No DML/DDL/role switching.
\echo AETHERIS_S0_D3_DISPOSABLE_ROLE_BOUND_FORCE_RLS_CREATED_PASS

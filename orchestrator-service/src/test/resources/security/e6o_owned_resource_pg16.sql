-- S0-E6O: disposable PostgreSQL16 object authorization review ONLY.
-- Never a product migration or real Task/Mission schema; no production routes.
-- Requires exact independently reviewed Platform E6J authority to exist.
BEGIN;
CREATE ROLE e6o_exec_owner NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE;
CREATE ROLE e6o_object_reader NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE;
-- The function owner needs only the existing E6J reader function, never
-- direct E6J session rows or enrollment/revoker authority.
GRANT e6j_reader TO e6o_exec_owner;
CREATE SCHEMA e6o_review AUTHORIZATION e6o_exec_owner;
REVOKE ALL ON SCHEMA e6o_review FROM PUBLIC;

CREATE TABLE e6o_review.resource_memberships(
  uid bigint NOT NULL CHECK(uid > 0),
  tenant_key varchar(96) NOT NULL,
  project_key varchar(96) NOT NULL,
  membership_epoch bigint NOT NULL CHECK(membership_epoch >= 0),
  active boolean NOT NULL DEFAULT false,
  PRIMARY KEY(uid,tenant_key,project_key),
  CHECK(length(trim(tenant_key)) > 0 AND length(trim(project_key)) > 0)
);
CREATE TABLE e6o_review.workload_fences(
  fence_id uuid PRIMARY KEY,
  active boolean NOT NULL DEFAULT false,
  fence_epoch bigint NOT NULL CHECK(fence_epoch >= 0)
);
CREATE TABLE e6o_review.bound_resources(
  id uuid PRIMARY KEY,
  kind varchar(8) NOT NULL CHECK(kind IN ('TASK','MISSION')),
  owner_uid bigint,
  tenant_key varchar(96) NOT NULL,
  project_key varchar(96) NOT NULL,
  owner_verified_at timestamptz,
  workload_fence_id uuid NOT NULL REFERENCES e6o_review.workload_fences(fence_id),
  object_label varchar(120) NOT NULL,
  FOREIGN KEY (owner_uid,tenant_key,project_key)
    REFERENCES e6o_review.resource_memberships(uid,tenant_key,project_key),
  CHECK(
    (owner_uid IS NULL AND owner_verified_at IS NULL)
    OR (owner_uid IS NOT NULL AND owner_uid > 0 AND owner_verified_at IS NOT NULL)
  )
);
ALTER TABLE e6o_review.resource_memberships OWNER TO e6o_exec_owner;
ALTER TABLE e6o_review.workload_fences OWNER TO e6o_exec_owner;
ALTER TABLE e6o_review.bound_resources OWNER TO e6o_exec_owner;
REVOKE ALL ON ALL TABLES IN SCHEMA e6o_review FROM PUBLIC;

-- Return resource data in the SAME transaction as authorization. Never
-- return a preflight ALLOW followed by an unguarded SELECT.
CREATE FUNCTION e6o_review.read_bound_resource(
  p_issuer varchar, p_kid varchar, p_jti varchar,
  p_uid bigint, p_iat bigint,
  p_id uuid, p_kind varchar, p_tenant varchar, p_project varchar)
RETURNS varchar
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $e6o$
DECLARE
  v_session_ok boolean;
  v_label varchar;
BEGIN
  IF p_issuer IS NULL OR p_kid IS NULL OR p_jti IS NULL
     OR p_uid IS NULL OR p_uid <= 0 OR p_iat IS NULL OR p_iat <= 0
     OR p_id IS NULL OR p_kind NOT IN ('TASK','MISSION')
     OR p_tenant IS NULL OR p_project IS NULL
     OR length(p_tenant) NOT BETWEEN 1 AND 96
     OR length(p_project) NOT BETWEEN 1 AND 96
     OR btrim(p_tenant) <> p_tenant OR btrim(p_project) <> p_project
  THEN RETURN NULL; END IF;
  -- Both function calls operate inside the caller's ONE READ COMMITTED
  -- transaction. E6J FOR SHARE locks on signer, session and epoch remain
  -- held until caller commits or rolls back.
  SELECT e6j_runtime_auth.check_signed_session(
    p_issuer,p_kid,p_jti,p_uid,p_iat) INTO v_session_ok;
  IF NOT COALESCE(v_session_ok,FALSE) THEN RETURN NULL; END IF;
  SELECT r.object_label INTO v_label
  FROM e6o_review.bound_resources AS r
  JOIN e6o_review.resource_memberships AS m
    ON m.uid=r.owner_uid AND m.tenant_key=r.tenant_key
   AND m.project_key=r.project_key
  JOIN e6o_review.workload_fences AS f
    ON f.fence_id=r.workload_fence_id
  WHERE r.id=p_id AND r.kind=p_kind AND r.owner_uid=p_uid
    AND r.owner_verified_at IS NOT NULL
    AND r.tenant_key=p_tenant AND r.project_key=p_project
    AND m.active AND f.active
  FOR SHARE OF r,m,f;
  RETURN v_label;
END;
$e6o$;
ALTER FUNCTION e6o_review.read_bound_resource(
 varchar,varchar,varchar,bigint,bigint,uuid,varchar,varchar,varchar)
 OWNER TO e6o_exec_owner;
REVOKE ALL ON ALL FUNCTIONS IN SCHEMA e6o_review FROM PUBLIC;
GRANT USAGE ON SCHEMA e6o_review TO e6o_object_reader;
GRANT EXECUTE ON FUNCTION e6o_review.read_bound_resource(
 varchar,varchar,varchar,bigint,bigint,uuid,varchar,varchar,varchar)
 TO e6o_object_reader;
COMMIT;

-- S0-E6T reviewed Mission persistence projection: disposable PostgreSQL16 ONLY.
-- No migration into a production JPA schema, no Spring discovery, no HTTP route.
-- Requires E6J/E6O/E6P/E6Q/E6R/E6S test-only authority and roles.
BEGIN;
CREATE SCHEMA e6t_review AUTHORIZATION e6p_exec_owner;
REVOKE ALL ON SCHEMA e6t_review FROM PUBLIC;

-- An app repository may read only this function, NEVER the old JPA entity's
-- taskIdsCsv or an unscoped findById. This returns real JPA column shapes
-- but excludes objective, legacy taskIdsCsv, secrets, and unbound rows.
CREATE FUNCTION e6t_review.canonical_mission_projection(
 p_issuer varchar,p_kid varchar,p_jti varchar,p_uid bigint,p_iat bigint,
 p_mission uuid,p_tenant varchar,p_project varchar)
RETURNS TABLE(
 mission_id uuid,mission_title varchar,mission_status varchar,
 mission_created_at timestamptz,mission_updated_at timestamptz,
 mission_version bigint)
LANGUAGE plpgsql SECURITY DEFINER
SET search_path=pg_catalog,pg_temp
AS $projection$
DECLARE
 v_signed boolean;
 v_fence uuid;
BEGIN
 IF p_issuer IS NULL OR p_kid IS NULL OR p_jti IS NULL
    OR p_uid IS NULL OR p_uid<=0 OR p_iat IS NULL OR p_iat<=0
    OR p_mission IS NULL OR p_tenant IS NULL OR p_project IS NULL
    OR p_tenant !~ '^[A-Za-z0-9._-]{1,96}$'
    OR p_project !~ '^[A-Za-z0-9._-]{1,96}$'
 THEN RETURN; END IF;

 -- Existing E6J authority MUST be checked INSIDE this same transaction.
 -- This holds signer/JTI/epoch locks until the caller's JDBC commit.
 SELECT e6j_runtime_auth.check_signed_session(
   p_issuer,p_kid,p_jti,p_uid,p_iat) INTO v_signed;
 IF NOT COALESCE(v_signed,FALSE) THEN RETURN; END IF;

 -- Read state and actual persisted owner using the same Mission row whose
 -- owner and status are eventually projected. Excludes dirty CSV, ownerless
 -- historical records, quarantined or frozen cutover decisions.
 SELECT m.owner_workload_fence_id INTO v_fence
 FROM public.aetheris_mission_sessions m
 JOIN e6r_review.mission_cutover d ON d.mission_id=m.id
 WHERE m.id=p_mission AND d.mode='CANONICAL'
   AND m.task_ids_csv=''
   AND m.owner_identity_uid=p_uid
   AND m.owner_verified_at IS NOT NULL
   AND m.owner_tenant_key=p_tenant AND m.owner_project_key=p_project
   AND m.owner_workload_fence_id IS NOT NULL
 FOR SHARE OF m,d;
 IF v_fence IS NULL THEN RETURN; END IF;

 -- E6O-owned function locks current live membership and workload-fence
 -- bindings; the function owner has no direct raw membership update grant.
 IF NOT e6o_review.e6p_locked_membership(
     p_uid,p_tenant,p_project,v_fence) THEN RETURN; END IF;

 RETURN QUERY
 SELECT m.id,m.title::varchar,m.status::varchar,
        m.created_at,m.updated_at,m.version
 FROM public.aetheris_mission_sessions m
 JOIN e6r_review.mission_cutover d ON d.mission_id=m.id
 WHERE m.id=p_mission AND d.mode='CANONICAL' AND m.task_ids_csv=''
   AND m.owner_identity_uid=p_uid
   AND m.owner_verified_at IS NOT NULL
   AND m.owner_tenant_key=p_tenant AND m.owner_project_key=p_project
   AND m.owner_workload_fence_id=v_fence
 FOR SHARE OF m,d;
END;
$projection$;
ALTER FUNCTION e6t_review.canonical_mission_projection(
 varchar,varchar,varchar,bigint,bigint,uuid,varchar,varchar)
 OWNER TO e6p_exec_owner;
REVOKE ALL ON ALL FUNCTIONS IN SCHEMA e6t_review FROM PUBLIC;
GRANT USAGE ON SCHEMA e6t_review TO e6p_reader;
GRANT EXECUTE ON FUNCTION e6t_review.canonical_mission_projection(
 varchar,varchar,varchar,bigint,bigint,uuid,varchar,varchar)
 TO e6p_reader;

-- S0-E6S's per-mission canonical functions remain the only scoped
-- application link/history entrypoints. This new projection is a repository
-- read, not another operator, owner-binding or raw-table authority.
COMMIT;

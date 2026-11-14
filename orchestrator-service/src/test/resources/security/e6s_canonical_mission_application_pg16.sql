-- S0-E6S isolated application-facing canonical Mission SQL boundary.
-- REQUIRES: reviewed E6J/E6O/E6P/E6Q/E6R synthetic CI-only schemas.
-- NO production migration, service activation, or legacy CSV authority.
BEGIN;
CREATE SCHEMA e6s_review AUTHORIZATION e6p_exec_owner;
REVOKE ALL ON SCHEMA e6s_review FROM PUBLIC;

-- CRITICAL CUTOVER: older E6Q functions have no canonical mode gate.
-- Revoke their direct SQL access from the existing scoped reader/writer.
-- Only the function owner may still call them inside a new, mode-gated
-- SECURITY DEFINER wrapper. FROZEN must deny ALL intended app link/history
-- paths, not only E6R's task-id accessor.
REVOKE EXECUTE ON FUNCTION e6q_review.link_owned(
  varchar,varchar,varchar,bigint,bigint,uuid,uuid,varchar,varchar)
  FROM e6p_writer;
REVOKE EXECUTE ON FUNCTION e6q_review.owned_event_headers(
  varchar,varchar,varchar,bigint,bigint,uuid,varchar,varchar,integer)
  FROM e6p_reader;

CREATE FUNCTION e6s_review.canonical_attach(
 p_issuer varchar,p_kid varchar,p_jti varchar,p_uid bigint,p_iat bigint,
 p_mission uuid,p_task uuid,p_tenant varchar,p_project varchar)
RETURNS boolean
LANGUAGE plpgsql SECURITY DEFINER
SET search_path=pg_catalog,pg_temp AS $attach$
DECLARE v_allowed boolean;
BEGIN
 IF p_issuer IS NULL OR p_kid IS NULL OR p_jti IS NULL
    OR p_uid IS NULL OR p_uid<=0 OR p_iat IS NULL OR p_iat<=0
    OR p_mission IS NULL OR p_task IS NULL OR
    p_tenant IS NULL OR p_project IS NULL OR
    p_tenant !~ '^[A-Za-z0-9._-]{1,96}$' OR
    p_project !~ '^[A-Za-z0-9._-]{1,96}$'
 THEN RETURN FALSE; END IF;

 -- Hold the canonical review decision and Mission row across the original
 -- E6Q signed-session + live membership/fence + Task link insertion. Any
 -- operator FREEZE needs an exclusive lock and cannot race this insert.
 SELECT TRUE INTO v_allowed
 FROM e6r_review.mission_cutover d
 JOIN public.aetheris_mission_sessions m ON m.id=d.mission_id
 WHERE d.mission_id=p_mission AND d.mode='CANONICAL'
   AND m.task_ids_csv='' AND m.status='ACTIVE'
   AND m.owner_identity_uid=p_uid AND m.owner_verified_at IS NOT NULL
   AND m.owner_tenant_key=p_tenant AND m.owner_project_key=p_project
   AND m.owner_workload_fence_id IS NOT NULL
 FOR SHARE OF d,m;
 IF NOT COALESCE(v_allowed,FALSE) THEN RETURN FALSE; END IF;
 RETURN COALESCE(e6q_review.link_owned(
   p_issuer,p_kid,p_jti,p_uid,p_iat,p_mission,p_task,
   p_tenant,p_project),FALSE);
END;
$attach$;
ALTER FUNCTION e6s_review.canonical_attach(
 varchar,varchar,varchar,bigint,bigint,uuid,uuid,varchar,varchar)
 OWNER TO e6p_exec_owner;
REVOKE ALL ON ALL FUNCTIONS IN SCHEMA e6s_review FROM PUBLIC;
GRANT USAGE ON SCHEMA e6s_review TO e6p_writer;
GRANT EXECUTE ON FUNCTION e6s_review.canonical_attach(
 varchar,varchar,varchar,bigint,bigint,uuid,uuid,varchar,varchar)
 TO e6p_writer;

-- A separate application-layer event header entrypoint obeys CANONICAL.
-- Direct E6Q reader EXECUTE has been revoked above; unlike the old E6Q
-- implementation, returning headers after FROZEN is not possible for the
-- restricted network reader. No raw event payload, metadata or SSE.
CREATE FUNCTION e6s_review.canonical_event_headers(
 p_issuer varchar,p_kid varchar,p_jti varchar,p_uid bigint,p_iat bigint,
 p_mission uuid,p_tenant varchar,p_project varchar,p_limit integer)
RETURNS TABLE(task_id uuid,event_at timestamptz,event_state varchar)
LANGUAGE plpgsql SECURITY DEFINER
SET search_path=pg_catalog,pg_temp AS $headers$
DECLARE v_allowed boolean;
BEGIN
 IF p_issuer IS NULL OR p_kid IS NULL OR p_jti IS NULL
    OR p_uid IS NULL OR p_uid<=0 OR p_iat IS NULL OR p_iat<=0
    OR p_mission IS NULL OR p_tenant IS NULL OR p_project IS NULL
    OR p_tenant !~ '^[A-Za-z0-9._-]{1,96}$'
    OR p_project !~ '^[A-Za-z0-9._-]{1,96}$'
    OR p_limit IS NULL OR p_limit NOT BETWEEN 1 AND 25
 THEN RETURN; END IF;
 SELECT TRUE INTO v_allowed
 FROM e6r_review.mission_cutover d
 JOIN public.aetheris_mission_sessions m ON m.id=d.mission_id
 WHERE d.mission_id=p_mission AND d.mode='CANONICAL'
   AND m.task_ids_csv='' AND m.owner_identity_uid=p_uid
   AND m.owner_verified_at IS NOT NULL
   AND m.owner_tenant_key=p_tenant AND m.owner_project_key=p_project
 FOR SHARE OF d,m;
 IF NOT COALESCE(v_allowed,FALSE) THEN RETURN; END IF;
 -- The original signed JTI and session/owner/fence locks are taken in this
 -- same calling transaction, and its event projection excludes raw content.
 RETURN QUERY SELECT h.task_id,h.event_at,h.event_state
 FROM e6q_review.owned_event_headers(
   p_issuer,p_kid,p_jti,p_uid,p_iat,p_mission,
   p_tenant,p_project,p_limit) h;
END;
$headers$;
ALTER FUNCTION e6s_review.canonical_event_headers(
 varchar,varchar,varchar,bigint,bigint,uuid,varchar,varchar,integer)
 OWNER TO e6p_exec_owner;
REVOKE ALL ON FUNCTION e6s_review.canonical_event_headers(
 varchar,varchar,varchar,bigint,bigint,uuid,varchar,varchar,integer)
 FROM PUBLIC;
GRANT USAGE ON SCHEMA e6s_review TO e6p_reader;
GRANT EXECUTE ON FUNCTION e6s_review.canonical_event_headers(
 varchar,varchar,varchar,bigint,bigint,uuid,varchar,varchar,integer)
 TO e6p_reader;
COMMIT;

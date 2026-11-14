-- S0-E6R DISPOSABLE review-only Mission CSV -> canonical link safety gate.
-- Requires source-pinned E6J/E6O/E6P/E6Q schemas in an isolated PG16 CI DB.
-- Never backfill legacy CSV, change customer mission data, or enable HTTP.
BEGIN;
CREATE ROLE e6r_operator NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE;
CREATE SCHEMA e6r_review AUTHORIZATION e6p_exec_owner;
REVOKE ALL ON SCHEMA e6r_review FROM PUBLIC;

-- Staged cutover is explicitly per Mission, not a feature flag that silently
-- authorizes existing task_ids_csv. No state ever falls back to old CSV.
CREATE TABLE e6r_review.mission_cutover(
  mission_id uuid PRIMARY KEY REFERENCES public.aetheris_mission_sessions(id),
  mode varchar(16) NOT NULL CHECK(mode IN ('QUARANTINED','CANONICAL','FROZEN')),
  reviewed_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  request_id uuid NOT NULL UNIQUE
);
CREATE TABLE e6r_review.cutover_audit(
  request_id uuid PRIMARY KEY,
  mission_id uuid NOT NULL REFERENCES public.aetheris_mission_sessions(id),
  action varchar(24) NOT NULL CHECK(action IN ('QUARANTINE','PROMOTE','FREEZE')),
  recorded_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
ALTER TABLE e6r_review.mission_cutover OWNER TO e6p_exec_owner;
ALTER TABLE e6r_review.cutover_audit OWNER TO e6p_exec_owner;
REVOKE ALL ON ALL TABLES IN SCHEMA e6r_review FROM PUBLIC;

-- Any Mission ever inspected by this gate is barred from resuming unverified
-- legacy CSV writes; rollback to FROZEN does not remove this guard. Existing
-- historical rows that have not been inspected remain physically unchanged.
CREATE FUNCTION e6r_review.block_legacy_csv_after_review()
RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER
SET search_path = pg_catalog, pg_temp AS $legacy$
BEGIN
  IF NEW.task_ids_csv IS DISTINCT FROM OLD.task_ids_csv
     AND EXISTS(SELECT 1 FROM e6r_review.mission_cutover d
                WHERE d.mission_id=OLD.id)
  THEN
    RAISE EXCEPTION 'Reviewed Mission legacy CSV is frozen'
      USING ERRCODE='42501';
  END IF;
  RETURN NEW;
END;
$legacy$;
ALTER FUNCTION e6r_review.block_legacy_csv_after_review()
OWNER TO e6p_exec_owner;
CREATE TRIGGER e6r_reviewed_mission_csv_is_frozen
BEFORE UPDATE OF task_ids_csv ON public.aetheris_mission_sessions
FOR EACH ROW EXECUTE FUNCTION e6r_review.block_legacy_csv_after_review();

-- Only an operator role can decide promotion/quarantine or freeze an existing
-- canonical Mission. This function does NOT parse/consume any CSV UUIDs.
-- Request IDs are unique and audit writes share exactly the same transaction
-- as cutover state. Reviewer action is synthetic, not real production approval.
CREATE FUNCTION e6r_review.operator_decide(
 p_mission uuid,p_request uuid,p_want_canonical boolean)
RETURNS varchar
LANGUAGE plpgsql SECURITY DEFINER
SET search_path=pg_catalog,pg_temp AS $decide$
DECLARE
 v_mission public.aetheris_mission_sessions%ROWTYPE;
 v_old varchar;
 v_next varchar;
 v_valid boolean;
BEGIN
 IF p_mission IS NULL OR p_request IS NULL OR p_want_canonical IS NULL
 THEN RETURN NULL; END IF;
 SELECT * INTO v_mission FROM public.aetheris_mission_sessions
 WHERE id=p_mission FOR UPDATE;
 IF NOT FOUND THEN RETURN NULL; END IF;
 SELECT mode INTO v_old FROM e6r_review.mission_cutover
 WHERE mission_id=p_mission FOR UPDATE;

 IF v_old IS NOT NULL THEN
   IF v_old='CANONICAL' AND NOT p_want_canonical THEN
     UPDATE e6r_review.mission_cutover
       SET mode='FROZEN',reviewed_at=clock_timestamp(),request_id=p_request
       WHERE mission_id=p_mission;
     INSERT INTO e6r_review.cutover_audit(request_id,mission_id,action)
       VALUES(p_request,p_mission,'FREEZE');
     RETURN 'FROZEN';
   END IF;
   -- Never promote or unfreeze again on this review: require a new,
   -- independently reviewed migration and external governance process.
   RETURN NULL;
 END IF;

 v_valid := v_mission.task_ids_csv = ''
     AND v_mission.owner_identity_uid IS NOT NULL
     AND v_mission.owner_identity_uid > 0
     AND v_mission.owner_verified_at IS NOT NULL
     AND v_mission.owner_tenant_key IS NOT NULL
     AND v_mission.owner_project_key IS NOT NULL
     AND v_mission.owner_workload_fence_id IS NOT NULL
     AND v_mission.status='ACTIVE';

 IF v_valid THEN
   -- Verify no link already points to a mismatched, unverified owner/fence.
   -- Mission FOR UPDATE conflicts with E6Q link_owned's FOR SHARE, blocking
   -- concurrent link additions until the decision transaction commits.
   IF EXISTS(
     SELECT 1 FROM e6q_review.mission_task_links link
     JOIN public.aetheris_tasks t ON t.id=link.task_id
     WHERE link.mission_id=p_mission AND
       (t.owner_identity_uid IS DISTINCT FROM v_mission.owner_identity_uid
        OR t.owner_tenant_key IS DISTINCT FROM v_mission.owner_tenant_key
        OR t.owner_project_key IS DISTINCT FROM v_mission.owner_project_key
        OR t.owner_workload_fence_id
            IS DISTINCT FROM v_mission.owner_workload_fence_id
        OR t.owner_verified_at IS NULL
        OR link.linked_by_uid IS DISTINCT FROM v_mission.owner_identity_uid)
   ) THEN v_valid:=FALSE; END IF;
 END IF;
 IF v_valid THEN
   v_valid:=e6o_review.e6p_locked_membership(
     v_mission.owner_identity_uid,v_mission.owner_tenant_key,
     v_mission.owner_project_key,v_mission.owner_workload_fence_id);
 END IF;
 IF p_want_canonical AND COALESCE(v_valid,FALSE) THEN
   v_next:='CANONICAL';
 ELSE
   v_next:='QUARANTINED';
 END IF;
 INSERT INTO e6r_review.mission_cutover(mission_id,mode,request_id)
 VALUES(p_mission,v_next,p_request);
 INSERT INTO e6r_review.cutover_audit(request_id,mission_id,action)
 VALUES(p_request,p_mission,
        CASE WHEN v_next='CANONICAL' THEN 'PROMOTE' ELSE 'QUARANTINE' END);
 RETURN v_next;
END;
$decide$;
ALTER FUNCTION e6r_review.operator_decide(uuid,uuid,boolean)
OWNER TO e6p_exec_owner;
REVOKE ALL ON ALL FUNCTIONS IN SCHEMA e6r_review FROM PUBLIC;
GRANT USAGE ON SCHEMA e6r_review TO e6r_operator;
GRANT EXECUTE ON FUNCTION e6r_review.operator_decide(uuid,uuid,boolean)
TO e6r_operator;

-- Canonical list accessor. If not explicitly PROMOTED, returns NOTHING.
-- Exact registered Platform JWT, mission owner/tenant/project, current
-- membership/fence, and each linked Task are rechecked under row locks.
-- No legacy CSV read, no event payload and no raw link-table access.
CREATE FUNCTION e6r_review.canonical_task_ids(
 p_issuer varchar,p_kid varchar,p_jti varchar,p_uid bigint,p_iat bigint,
 p_mission uuid,p_tenant varchar,p_project varchar,p_limit integer)
RETURNS TABLE(task_id uuid)
LANGUAGE plpgsql SECURITY DEFINER
SET search_path=pg_catalog,pg_temp AS $canonical$
DECLARE
 v_signed boolean;
 v_fence uuid;
BEGIN
 IF p_issuer IS NULL OR p_kid IS NULL OR p_jti IS NULL
    OR p_uid IS NULL OR p_uid<=0 OR p_iat IS NULL OR p_iat<=0
    OR p_mission IS NULL OR p_tenant IS NULL OR p_project IS NULL
    OR p_tenant !~ '^[A-Za-z0-9._-]{1,96}$'
    OR p_project !~ '^[A-Za-z0-9._-]{1,96}$'
    OR p_limit IS NULL OR p_limit NOT BETWEEN 1 AND 25
 THEN RETURN; END IF;
 SELECT e6j_runtime_auth.check_signed_session(
  p_issuer,p_kid,p_jti,p_uid,p_iat) INTO v_signed;
 IF NOT COALESCE(v_signed,FALSE) THEN RETURN; END IF;

 SELECT m.owner_workload_fence_id INTO v_fence
 FROM public.aetheris_mission_sessions m
 JOIN e6r_review.mission_cutover d ON d.mission_id=m.id
 WHERE m.id=p_mission AND d.mode='CANONICAL'
   AND m.task_ids_csv='' AND m.owner_identity_uid=p_uid
   AND m.owner_verified_at IS NOT NULL
   AND m.owner_tenant_key=p_tenant AND m.owner_project_key=p_project
 FOR SHARE OF m,d;
 IF v_fence IS NULL THEN RETURN; END IF;
 IF NOT e6o_review.e6p_locked_membership(
   p_uid,p_tenant,p_project,v_fence) THEN RETURN; END IF;

 RETURN QUERY
 SELECT link.task_id FROM e6q_review.mission_task_links link
 JOIN public.aetheris_tasks task ON task.id=link.task_id
 WHERE link.mission_id=p_mission AND link.linked_by_uid=p_uid
   AND task.owner_identity_uid=p_uid
   AND task.owner_verified_at IS NOT NULL
   AND task.owner_tenant_key=p_tenant AND task.owner_project_key=p_project
   AND task.owner_workload_fence_id=v_fence
 ORDER BY link.linked_at,link.task_id
 LIMIT p_limit FOR SHARE OF link,task;
END;
$canonical$;
ALTER FUNCTION e6r_review.canonical_task_ids(
 varchar,varchar,varchar,bigint,bigint,uuid,varchar,varchar,integer)
 OWNER TO e6p_exec_owner;
REVOKE ALL ON FUNCTION e6r_review.canonical_task_ids(
 varchar,varchar,varchar,bigint,bigint,uuid,varchar,varchar,integer) FROM PUBLIC;
GRANT USAGE ON SCHEMA e6r_review TO e6p_reader;
GRANT EXECUTE ON FUNCTION e6r_review.canonical_task_ids(
 varchar,varchar,varchar,bigint,bigint,uuid,varchar,varchar,integer)
 TO e6p_reader;

COMMIT;

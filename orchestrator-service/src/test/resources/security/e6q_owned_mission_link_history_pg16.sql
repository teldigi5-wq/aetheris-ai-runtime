-- S0-E6Q disposable additive Mission↔Task link and event-header review.
-- Depends on the exact E6J + E6O + E6P review-only SQL schemas.
-- NEVER a production migration, MissionService replacement or route activation.
BEGIN;
CREATE SCHEMA e6q_review AUTHORIZATION e6p_exec_owner;
REVOKE ALL ON SCHEMA e6q_review FROM PUBLIC;

-- Real TaskEventJournalEntity column names and lengths; synthetic payloads.
-- Historical ownerless data gets an event BEFORE any new owned link exists.
CREATE TABLE public.aetheris_task_events (
  id uuid PRIMARY KEY,
  task_id uuid NOT NULL REFERENCES public.aetheris_tasks(id),
  event_at timestamptz NOT NULL,
  state varchar(32) NOT NULL,
  agent_id varchar(120),
  message varchar(8000) NOT NULL,
  metadata_json varchar(16000) NOT NULL
);
INSERT INTO public.aetheris_task_events
(id,task_id,event_at,state,agent_id,message,metadata_json)
VALUES('eeeeeeee-0000-4000-8000-000000000001',
       'aaaaaaaa-1111-4111-8111-111111111111',now(),'QUEUED',NULL,
       'SECRET HISTORIC MESSAGE NEVER RETURN','{"hidden":"historical-secret"}');
ALTER TABLE public.aetheris_task_events OWNER TO e6p_exec_owner;
REVOKE ALL ON public.aetheris_task_events FROM PUBLIC;

-- Never trust MissionSessionEntity.taskIdsCsv as authority; do not modify it.
-- The separate link is immutable and requires exact owner/fence agreement.
CREATE TABLE e6q_review.mission_task_links(
  mission_id uuid NOT NULL REFERENCES public.aetheris_mission_sessions(id),
  task_id uuid NOT NULL REFERENCES public.aetheris_tasks(id),
  linked_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  linked_by_uid bigint NOT NULL CHECK(linked_by_uid>0),
  PRIMARY KEY(mission_id,task_id)
);
ALTER TABLE e6q_review.mission_task_links OWNER TO e6p_exec_owner;
REVOKE ALL ON ALL TABLES IN SCHEMA e6q_review FROM PUBLIC;

CREATE FUNCTION e6q_review.link_owned(
  p_issuer varchar,p_kid varchar,p_jti varchar,p_uid bigint,p_iat bigint,
  p_mission uuid,p_task uuid,p_tenant varchar,p_project varchar)
RETURNS boolean
LANGUAGE plpgsql SECURITY DEFINER
SET search_path=pg_catalog,pg_temp
AS $link$
DECLARE
  v_session boolean;
  v_fence uuid;
BEGIN
  IF p_issuer IS NULL OR p_kid IS NULL OR p_jti IS NULL OR
     p_uid IS NULL OR p_uid<=0 OR p_iat IS NULL OR p_iat<=0 OR
     p_mission IS NULL OR p_task IS NULL OR
     p_tenant IS NULL OR p_project IS NULL OR
     p_tenant !~ '^[a-zA-Z0-9._-]{1,96}$' OR
     p_project !~ '^[a-zA-Z0-9._-]{1,96}$'
  THEN RETURN FALSE; END IF;

  -- E6J obtains signer/epoch/session FOR SHARE authority locks.
  SELECT e6j_runtime_auth.check_signed_session(
    p_issuer,p_kid,p_jti,p_uid,p_iat) INTO v_session;
  IF NOT COALESCE(v_session,FALSE) THEN RETURN FALSE; END IF;

  -- Atomic object-owner matching. Both must be owned by exactly the same
  -- verified UID, tenant, project and independently bound workload fence.
  -- Unbound historical rows fail the non-NULL owner/verified-time checks.
  SELECT mission.owner_workload_fence_id INTO v_fence
  FROM public.aetheris_mission_sessions mission
  JOIN public.aetheris_tasks task
    ON task.id=p_task
   AND task.owner_identity_uid=mission.owner_identity_uid
   AND task.owner_tenant_key=mission.owner_tenant_key
   AND task.owner_project_key=mission.owner_project_key
   AND task.owner_workload_fence_id=mission.owner_workload_fence_id
  WHERE mission.id=p_mission
    AND mission.owner_identity_uid=p_uid AND task.owner_identity_uid=p_uid
    AND mission.owner_verified_at IS NOT NULL
    AND task.owner_verified_at IS NOT NULL
    AND mission.owner_tenant_key=p_tenant
    AND mission.owner_project_key=p_project
    AND mission.status='ACTIVE'
  FOR SHARE OF mission,task;
  IF v_fence IS NULL THEN RETURN FALSE; END IF;

  -- The E6O owner locks live tenant/project membership, and the approved
  -- fence binding and fence rows until the INSERT transaction commits.
  IF NOT e6o_review.e6p_locked_membership(
      p_uid,p_tenant,p_project,v_fence) THEN RETURN FALSE; END IF;

  INSERT INTO e6q_review.mission_task_links(
    mission_id,task_id,linked_by_uid)
  VALUES(p_mission,p_task,p_uid)
  ON CONFLICT(mission_id,task_id) DO NOTHING;
  RETURN TRUE;
END;
$link$;
ALTER FUNCTION e6q_review.link_owned(
 varchar,varchar,varchar,bigint,bigint,uuid,uuid,varchar,varchar)
 OWNER TO e6p_exec_owner;
REVOKE ALL ON ALL FUNCTIONS IN SCHEMA e6q_review FROM PUBLIC;
GRANT USAGE ON SCHEMA e6q_review TO e6p_writer;
GRANT EXECUTE ON FUNCTION e6q_review.link_owned(
 varchar,varchar,varchar,bigint,bigint,uuid,uuid,varchar,varchar)
 TO e6p_writer;

-- Caller gets only bounded event HEADERS, never event message, metadata
-- JSON, agent identifier, command text or mission objective. This function
-- does not provide SSE, pagination guarantees across inserts or replay.
CREATE FUNCTION e6q_review.owned_event_headers(
  p_issuer varchar,p_kid varchar,p_jti varchar,p_uid bigint,p_iat bigint,
  p_mission uuid,p_tenant varchar,p_project varchar,p_limit integer)
RETURNS TABLE(task_id uuid,event_at timestamptz,event_state varchar)
LANGUAGE plpgsql SECURITY DEFINER
SET search_path=pg_catalog,pg_temp
AS $headers$
DECLARE
 v_session boolean;
 v_fence uuid;
BEGIN
  IF p_issuer IS NULL OR p_kid IS NULL OR p_jti IS NULL OR
     p_uid IS NULL OR p_uid<=0 OR p_iat IS NULL OR p_iat<=0 OR
     p_mission IS NULL OR p_tenant IS NULL OR p_project IS NULL OR
     p_tenant !~ '^[a-zA-Z0-9._-]{1,96}$' OR
     p_project !~ '^[a-zA-Z0-9._-]{1,96}$' OR
     p_limit IS NULL OR p_limit NOT BETWEEN 1 AND 25
  THEN RETURN; END IF;

  SELECT e6j_runtime_auth.check_signed_session(
    p_issuer,p_kid,p_jti,p_uid,p_iat) INTO v_session;
  IF NOT COALESCE(v_session,FALSE) THEN RETURN; END IF;

  SELECT mission.owner_workload_fence_id INTO v_fence
  FROM public.aetheris_mission_sessions mission
  WHERE mission.id=p_mission AND mission.owner_identity_uid=p_uid
    AND mission.owner_verified_at IS NOT NULL
    AND mission.owner_tenant_key=p_tenant
    AND mission.owner_project_key=p_project
  FOR SHARE OF mission;
  IF v_fence IS NULL THEN RETURN; END IF;
  IF NOT e6o_review.e6p_locked_membership(
      p_uid,p_tenant,p_project,v_fence) THEN RETURN; END IF;

  -- Every event and linked Task must belong to the same signed owner, exact
  -- tenant/project and fence. Lock the resource/link/event rows throughout
  -- the single data-returning transaction.
  RETURN QUERY
    SELECT ev.task_id,ev.event_at,ev.state
    FROM e6q_review.mission_task_links link
    JOIN public.aetheris_tasks task ON task.id=link.task_id
    JOIN public.aetheris_task_events ev ON ev.task_id=task.id
    WHERE link.mission_id=p_mission AND link.linked_by_uid=p_uid
      AND task.owner_identity_uid=p_uid AND task.owner_verified_at IS NOT NULL
      AND task.owner_tenant_key=p_tenant
      AND task.owner_project_key=p_project
      AND task.owner_workload_fence_id=v_fence
    ORDER BY ev.event_at DESC,ev.id DESC
    LIMIT p_limit
    FOR SHARE OF link,task,ev;
END;
$headers$;
ALTER FUNCTION e6q_review.owned_event_headers(
 varchar,varchar,varchar,bigint,bigint,uuid,varchar,varchar,integer)
 OWNER TO e6p_exec_owner;
REVOKE ALL ON FUNCTION e6q_review.owned_event_headers(
 varchar,varchar,varchar,bigint,bigint,uuid,varchar,varchar,integer)
 FROM PUBLIC;
GRANT USAGE ON SCHEMA e6q_review TO e6p_reader;
GRANT EXECUTE ON FUNCTION e6q_review.owned_event_headers(
 varchar,varchar,varchar,bigint,bigint,uuid,varchar,varchar,integer)
 TO e6p_reader;
COMMIT;

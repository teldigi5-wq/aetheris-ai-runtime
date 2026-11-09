-- S0-E6P: isolated ACTUAL-NAME / JPA-SHAPE persistence clone.
-- Not a production Flyway migration, never applied to real Task/Mission data.
-- Preserve existing ownerless records: NO AUTOMATIC BACKFILL OR CLAIM.
BEGIN;
CREATE ROLE e6p_exec_owner NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE;
CREATE ROLE e6p_writer NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE;
CREATE ROLE e6p_reader NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE;
GRANT e6j_reader TO e6p_exec_owner;
GRANT USAGE ON SCHEMA e6o_review TO e6p_exec_owner;
GRANT SELECT ON e6o_review.resource_memberships,
                e6o_review.workload_fences TO e6p_exec_owner;
CREATE SCHEMA e6p_review AUTHORIZATION e6p_exec_owner;
REVOKE ALL ON SCHEMA e6p_review FROM PUBLIC;

-- These minimal baseline tables use the real Hibernate entity table/column
-- names, primary key types and enum-as-string layouts. No production service
-- table is created or modified. This models a pre-owner migration database.
CREATE TABLE public.aetheris_tasks (
  id uuid PRIMARY KEY,
  title varchar(160) NOT NULL,
  command_text varchar(8000) NOT NULL,
  state varchar(32) NOT NULL,
  mode varchar(32) NOT NULL,
  active_agent_id varchar(120),
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL,
  version bigint NOT NULL
);
CREATE TABLE public.aetheris_mission_sessions (
  id uuid PRIMARY KEY,
  title varchar(240) NOT NULL,
  objective varchar(8000) NOT NULL,
  status varchar(24) NOT NULL,
  task_ids_csv varchar(8000) NOT NULL,
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL,
  version bigint NOT NULL
);
-- Historical ownerless rows are added BEFORE the additive DDL.
INSERT INTO public.aetheris_tasks
 (id,title,command_text,state,mode,created_at,updated_at,version)
VALUES ('aaaaaaaa-1111-4111-8111-111111111111','historic task',
  'legacy-execution-stays-unbound','QUEUED','BALANCED',now(),now(),0);
INSERT INTO public.aetheris_mission_sessions
 (id,title,objective,status,task_ids_csv,created_at,updated_at,version)
VALUES ('bbbbbbbb-2222-4222-8222-222222222222','historic mission',
 'historical-unverified-object','ACTIVE','',now(),now(),0);

-- Actual columns already exist as read-only JPA mappings for TaskEntity,
-- but MissionSessionEntity has no mapped owner metadata yet. These nullable
-- additions require a separate reviewed migration before real deployment.
ALTER TABLE public.aetheris_tasks
  ADD COLUMN owner_identity_uid bigint,
  ADD COLUMN owner_tenant_key varchar(96),
  ADD COLUMN owner_project_key varchar(96),
  ADD COLUMN owner_verified_at timestamptz;
ALTER TABLE public.aetheris_mission_sessions
  ADD COLUMN owner_identity_uid bigint,
  ADD COLUMN owner_tenant_key varchar(96),
  ADD COLUMN owner_project_key varchar(96),
  ADD COLUMN owner_verified_at timestamptz;
ALTER TABLE public.aetheris_tasks
  ADD CONSTRAINT e6p_task_owner_all_or_none CHECK (
    num_nonnulls(owner_identity_uid,owner_tenant_key,owner_project_key,owner_verified_at)=0
    OR (
      num_nonnulls(owner_identity_uid,owner_tenant_key,owner_project_key,owner_verified_at)=4
      AND owner_identity_uid>0
      AND length(btrim(owner_tenant_key))>0
      AND length(btrim(owner_project_key))>0
    )
  );
ALTER TABLE public.aetheris_mission_sessions
  ADD CONSTRAINT e6p_mission_owner_all_or_none CHECK (
    num_nonnulls(owner_identity_uid,owner_tenant_key,owner_project_key,owner_verified_at)=0
    OR (
      num_nonnulls(owner_identity_uid,owner_tenant_key,owner_project_key,owner_verified_at)=4
      AND owner_identity_uid>0
      AND length(btrim(owner_tenant_key))>0
      AND length(btrim(owner_project_key))>0
    )
  );

-- Review issuer can create new owned rows only through function EXECUTE, and
-- cannot ever update or retroactively claim any historical row.
CREATE FUNCTION e6p_review.no_owner_reassignment()
RETURNS trigger LANGUAGE plpgsql
SET search_path=pg_catalog,pg_temp
AS $guard$
BEGIN
  IF ROW(NEW.owner_identity_uid,NEW.owner_tenant_key,
         NEW.owner_project_key,NEW.owner_verified_at)
     IS DISTINCT FROM
     ROW(OLD.owner_identity_uid,OLD.owner_tenant_key,
         OLD.owner_project_key,OLD.owner_verified_at)
  THEN RAISE EXCEPTION 'Owner binding is immutable';
  END IF;
  RETURN NEW;
END;
$guard$;
CREATE TRIGGER e6p_task_owner_guard
  BEFORE UPDATE ON public.aetheris_tasks
  FOR EACH ROW EXECUTE FUNCTION e6p_review.no_owner_reassignment();
CREATE TRIGGER e6p_mission_owner_guard
  BEFORE UPDATE ON public.aetheris_mission_sessions
  FOR EACH ROW EXECUTE FUNCTION e6p_review.no_owner_reassignment();

ALTER TABLE public.aetheris_tasks OWNER TO e6p_exec_owner;
ALTER TABLE public.aetheris_mission_sessions OWNER TO e6p_exec_owner;
REVOKE ALL ON public.aetheris_tasks,public.aetheris_mission_sessions FROM PUBLIC;

-- Workload-fence association is NOT caller-controlled. Only operator-review
-- fixture authority can populate this independent resource/fence binding.
CREATE TABLE e6p_review.fence_bindings(
  fence_id uuid PRIMARY KEY,
  tenant_key varchar(96) NOT NULL,
  project_key varchar(96) NOT NULL,
  FOREIGN KEY(fence_id) REFERENCES e6o_review.workload_fences(fence_id)
);
ALTER TABLE e6p_review.fence_bindings OWNER TO e6p_exec_owner;
REVOKE ALL ON e6p_review.fence_bindings FROM PUBLIC;

-- Trusted caller passes a *cryptographically verified* signed principal only.
-- The SQL rechecks that exact enrolled JTI + signer + epoch and live
-- tenant/project membership and active bound workload fence before the INSERT.
-- Every lock is held through the INSERT and caller's COMMIT. No partial owner
-- claims, owner reassignment, legacy backfill, or public direct table writes.
CREATE FUNCTION e6p_review.create_owned(
 p_issuer varchar,p_kid varchar,p_jti varchar,p_uid bigint,p_iat bigint,
 p_id uuid,p_kind varchar,p_tenant varchar,p_project varchar,p_fence uuid,
 p_title varchar,p_description varchar
) RETURNS boolean
LANGUAGE plpgsql SECURITY DEFINER
SET search_path=pg_catalog,pg_temp
AS $create$
DECLARE
 v_session boolean;
 v_membership boolean;
BEGIN
 IF p_issuer IS NULL OR p_kid IS NULL OR p_jti IS NULL OR
    p_uid IS NULL OR p_uid <= 0 OR p_iat IS NULL OR p_iat <= 0 OR
    p_id IS NULL OR p_fence IS NULL OR
    p_kind NOT IN ('TASK','MISSION') OR
    p_tenant IS NULL OR p_project IS NULL OR
    p_tenant !~ '^[a-zA-Z0-9._-]{1,96}$' OR
    p_project !~ '^[a-zA-Z0-9._-]{1,96}$' OR
    p_title IS NULL OR length(btrim(p_title))=0 OR
    length(p_title)>(CASE WHEN p_kind='TASK' THEN 160 ELSE 240 END) OR
    p_description IS NULL OR length(btrim(p_description))=0 OR
    length(p_description)>8000
 THEN RETURN FALSE; END IF;
 SELECT e6j_runtime_auth.check_signed_session(
   p_issuer,p_kid,p_jti,p_uid,p_iat) INTO v_session;
 IF NOT COALESCE(v_session,FALSE) THEN RETURN FALSE; END IF;
 SELECT TRUE INTO v_membership
 FROM e6o_review.resource_memberships m
 JOIN e6p_review.fence_bindings b
   ON b.tenant_key=m.tenant_key AND b.project_key=m.project_key
   AND b.fence_id=p_fence
 JOIN e6o_review.workload_fences f
   ON f.fence_id=b.fence_id
 WHERE m.uid=p_uid AND m.tenant_key=p_tenant
   AND m.project_key=p_project AND m.active AND f.active
 FOR SHARE OF m,b,f;
 IF NOT COALESCE(v_membership,FALSE) THEN RETURN FALSE; END IF;
 IF p_kind='TASK' THEN
  INSERT INTO public.aetheris_tasks(
    id,title,command_text,state,mode,created_at,updated_at,version,
    owner_identity_uid,owner_tenant_key,owner_project_key,owner_verified_at)
  VALUES(p_id,p_title,p_description,'QUEUED','BALANCED',now(),now(),0,
    p_uid,p_tenant,p_project,clock_timestamp());
 ELSE
  INSERT INTO public.aetheris_mission_sessions(
    id,title,objective,status,task_ids_csv,created_at,updated_at,version,
    owner_identity_uid,owner_tenant_key,owner_project_key,owner_verified_at)
  VALUES(p_id,p_title,p_description,'ACTIVE','',now(),now(),0,
    p_uid,p_tenant,p_project,clock_timestamp());
 END IF;
 RETURN TRUE;
END;
$create$;
ALTER FUNCTION e6p_review.create_owned(
 varchar,varchar,varchar,bigint,bigint,uuid,varchar,varchar,varchar,uuid,
 varchar,varchar) OWNER TO e6p_exec_owner;
REVOKE ALL ON ALL FUNCTIONS IN SCHEMA e6p_review FROM PUBLIC;
GRANT USAGE ON SCHEMA e6p_review TO e6p_writer;
GRANT EXECUTE ON FUNCTION e6p_review.create_owned(
 varchar,varchar,varchar,bigint,bigint,uuid,varchar,varchar,varchar,uuid,
 varchar,varchar) TO e6p_writer;

-- Read is also function-only: exact current session + live membership and
-- fenced owner in the same statement/transaction as returned persisted label.
CREATE FUNCTION e6p_review.read_owned(
 p_issuer varchar,p_kid varchar,p_jti varchar,p_uid bigint,p_iat bigint,
 p_id uuid,p_kind varchar,p_tenant varchar,p_project varchar)
RETURNS varchar
LANGUAGE plpgsql SECURITY DEFINER
SET search_path=pg_catalog,pg_temp
AS $read$
DECLARE
 v_active boolean;
 v_label varchar;
BEGIN
 SELECT e6j_runtime_auth.check_signed_session(
  p_issuer,p_kid,p_jti,p_uid,p_iat) INTO v_active;
 IF NOT COALESCE(v_active,FALSE) THEN RETURN NULL; END IF;
 IF p_kind='TASK' THEN
  SELECT t.title INTO v_label
  FROM public.aetheris_tasks t
  JOIN e6o_review.resource_memberships m
    ON m.uid=t.owner_identity_uid AND m.tenant_key=t.owner_tenant_key
    AND m.project_key=t.owner_project_key
  JOIN e6p_review.fence_bindings b
    ON b.tenant_key=m.tenant_key AND b.project_key=m.project_key
  JOIN e6o_review.workload_fences f ON f.fence_id=b.fence_id
  WHERE t.id=p_id AND t.owner_identity_uid=p_uid AND m.active AND f.active
    AND t.owner_verified_at IS NOT NULL AND t.owner_tenant_key=p_tenant
    AND t.owner_project_key=p_project
  FOR SHARE OF t,m,b,f;
 ELSIF p_kind='MISSION' THEN
  SELECT t.title INTO v_label
  FROM public.aetheris_mission_sessions t
  JOIN e6o_review.resource_memberships m
    ON m.uid=t.owner_identity_uid AND m.tenant_key=t.owner_tenant_key
    AND m.project_key=t.owner_project_key
  JOIN e6p_review.fence_bindings b
    ON b.tenant_key=m.tenant_key AND b.project_key=m.project_key
  JOIN e6o_review.workload_fences f ON f.fence_id=b.fence_id
  WHERE t.id=p_id AND t.owner_identity_uid=p_uid AND m.active AND f.active
    AND t.owner_verified_at IS NOT NULL AND t.owner_tenant_key=p_tenant
    AND t.owner_project_key=p_project
  FOR SHARE OF t,m,b,f;
 END IF;
 RETURN v_label;
END;
$read$;
ALTER FUNCTION e6p_review.read_owned(
 varchar,varchar,varchar,bigint,bigint,uuid,varchar,varchar)
 OWNER TO e6p_exec_owner;
REVOKE ALL ON FUNCTION e6p_review.read_owned(
 varchar,varchar,varchar,bigint,bigint,uuid,varchar,varchar) FROM PUBLIC;
GRANT USAGE ON SCHEMA e6p_review TO e6p_reader;
GRANT EXECUTE ON FUNCTION e6p_review.read_owned(
 varchar,varchar,varchar,bigint,bigint,uuid,varchar,varchar) TO e6p_reader;

-- Required privilege boundaries never loosen original ownerless quarantine.
COMMIT;

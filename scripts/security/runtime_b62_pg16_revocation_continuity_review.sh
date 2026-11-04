#!/usr/bin/env bash
# REVIEW ONLY. Source after the existing B62 physical standby's resource
# partition/fencing tests and before its coordinator-outage test.
# No real sessions, task data, customer secrets, production paths or API unlock.
# This is deliberately NOT a production failover or revocation implementation.
[[ "${GITHUB_ACTIONS:-}" == true ]] || exit 92
[[ "${AETHERIS_B62_REPLICA_CI_ONLY:-}" == yes ]] || exit 92
[[ "${standby:-}" == aetheris-b62-standby-* ]] || exit 92
[[ "${local_writer:-}" == b62_local_writer ]] || exit 92
[[ "$(standby_sql 'SELECT pg_is_in_recovery()')" == f ]]
[[ "$(coord_write "SELECT endpoint || ':' || term FROM public.leader_fence WHERE authority_key='runtime'")" == "$standby:4" ]]

# Coordinator-side source and resource-side replay state live on DIFFERENT
# PostgreSQL 16 server processes; no distributed transaction is attempted.
coord_write "CREATE TABLE public.b62_revocation_event_source(
 seq bigint PRIMARY KEY CHECK(seq>0), uid bigint NOT NULL CHECK(uid>0));
 INSERT INTO public.b62_revocation_event_source VALUES(1,11),(2,42);" >/dev/null
[[ "$(coord_write "SELECT string_agg(seq::text || ':' || uid::text,',' ORDER BY seq) FROM public.b62_revocation_event_source")" == '1:11,2:42' ]]

standby_sql "BEGIN;
 CREATE TABLE public.b62_replay_gate (
   resource_key text PRIMARY KEY, endpoint text NOT NULL,
   term bigint NOT NULL CHECK(term>0), required_seq bigint NOT NULL CHECK(required_seq>=0),
   applied_seq bigint NOT NULL CHECK(applied_seq>=0), admitted boolean NOT NULL);
 INSERT INTO public.b62_replay_gate VALUES('runtime','$standby',4,2,0,false);
 CREATE TABLE public.b62_replayed_revocations(
   seq bigint PRIMARY KEY CHECK(seq>0), uid bigint NOT NULL CHECK(uid>0));
 CREATE TABLE public.b62_revocation_guarded_actions(
   request_id text PRIMARY KEY, uid bigint NOT NULL, term bigint NOT NULL);
 CREATE FUNCTION public.b62_gate_no_rollback() RETURNS trigger
 LANGUAGE plpgsql SET search_path=pg_catalog,pg_temp AS \$b62_nr\$
 BEGIN
  IF NEW.term < OLD.term OR NEW.required_seq < OLD.required_seq
     OR NEW.applied_seq < OLD.applied_seq THEN
   RAISE EXCEPTION 'B62 review gate monotonicity violation';
  END IF;
  RETURN NEW;
 END \$b62_nr\$;
 CREATE TRIGGER b62_no_regress BEFORE UPDATE ON public.b62_replay_gate
   FOR EACH ROW EXECUTE FUNCTION public.b62_gate_no_rollback();
 CREATE FUNCTION public.b62_apply_review_revocation(p_seq bigint,p_uid bigint)
 RETURNS boolean LANGUAGE plpgsql SET search_path=pg_catalog,pg_temp AS \$b62_apply\$
 DECLARE previous_seq bigint;
 BEGIN
   IF p_seq IS NULL OR p_uid IS NULL OR p_seq<1 OR p_uid<1 THEN RETURN false; END IF;
   SELECT applied_seq INTO previous_seq FROM public.b62_replay_gate
     WHERE resource_key='runtime' FOR UPDATE;
   IF NOT FOUND OR p_seq<>previous_seq+1 THEN RETURN false; END IF;
   INSERT INTO public.b62_replayed_revocations(seq,uid) VALUES(p_seq,p_uid);
   UPDATE public.b62_replay_gate SET applied_seq=p_seq WHERE resource_key='runtime';
   RETURN true;
 END \$b62_apply\$;
 CREATE FUNCTION public.b62_try_guarded_revocation_effect(
   p_term bigint,p_uid bigint,p_request text)
 RETURNS boolean LANGUAGE plpgsql SECURITY DEFINER
 SET search_path=pg_catalog,pg_temp AS \$b62_guard\$
 BEGIN
   IF session_user <> '$local_writer'
      OR current_setting('transaction_isolation') <> 'read committed'
      OR p_term IS NULL OR p_term<1 OR p_uid IS NULL OR p_uid<1
      OR p_request IS NULL OR p_request !~ '^[a-z][a-z0-9-]{0,47}$' THEN
      RETURN false;
   END IF;
   PERFORM 1 FROM public.b62_replay_gate
     WHERE resource_key='runtime' AND endpoint='$standby'
       AND term=p_term AND admitted AND applied_seq >= required_seq
       AND NOT pg_is_in_recovery()
     FOR SHARE;
   IF NOT FOUND THEN RETURN false; END IF;
   IF EXISTS (SELECT 1 FROM public.b62_replayed_revocations WHERE uid=p_uid)
      THEN RETURN false; END IF;
   INSERT INTO public.b62_revocation_guarded_actions(request_id,uid,term)
     VALUES (p_request,p_uid,p_term) ON CONFLICT (request_id) DO NOTHING;
   RETURN FOUND;
 END \$b62_guard\$;
 ALTER FUNCTION public.b62_try_guarded_revocation_effect(bigint,bigint,text)
   OWNER TO $local_owner;
 REVOKE ALL ON public.b62_replay_gate, public.b62_replayed_revocations,
   public.b62_revocation_guarded_actions FROM PUBLIC;
 GRANT SELECT ON public.b62_replay_gate TO $local_owner;
 GRANT UPDATE(applied_seq) ON public.b62_replay_gate TO $local_owner;
 GRANT SELECT ON public.b62_replayed_revocations TO $local_owner;
 GRANT INSERT ON public.b62_revocation_guarded_actions TO $local_owner;
 REVOKE ALL ON FUNCTION public.b62_apply_review_revocation(bigint,bigint)
   FROM PUBLIC;
 REVOKE ALL ON FUNCTION public.b62_try_guarded_revocation_effect(bigint,bigint,text)
   FROM PUBLIC;
 GRANT EXECUTE ON FUNCTION public.b62_try_guarded_revocation_effect(bigint,bigint,text)
   TO $local_writer;
 COMMIT;" >/dev/null

# Admission before replay must fail closed in the protected PostgreSQL
# transaction, even if a mistaken operator flips the admitted bit.
[[ "$(local_call "$standby" "$local_new_pw" \
  "SELECT public.b62_try_guarded_revocation_effect(4,98,'before-admission')")" == f ]]
standby_sql "UPDATE public.b62_replay_gate SET admitted=true WHERE resource_key='runtime';" >/dev/null
[[ "$(local_call "$standby" "$local_new_pw" \
  "SELECT public.b62_try_guarded_revocation_effect(4,98,'before-replay')")" == f ]]
[[ "$(standby_sql 'SELECT public.b62_apply_review_revocation(2,42)')" == f ]]
# UNSAFE NEGATIVE: the disposable trusted owner can replay the correct
# sequence with a WRONG identity. Gapless ordering alone does not prove
# source-event integrity. The transaction is rolled back after asserting
# the wrong UID was accepted; no test state is modified by this witness.
standby_sql "BEGIN;
DO \$b62_mismatch\$
BEGIN
  IF NOT public.b62_apply_review_revocation(1,999) THEN
    RAISE EXCEPTION 'expected incorrect source-identity replay to be accepted';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM public.b62_replayed_revocations
                 WHERE seq=1 AND uid=999) THEN
    RAISE EXCEPTION 'missing mismatched UID counterexample';
  END IF;
END \$b62_mismatch\$;
ROLLBACK;" >/dev/null
[[ "$(standby_sql "SELECT applied_seq FROM public.b62_replay_gate WHERE resource_key='runtime'")" == 0 ]]
[[ "$(standby_sql 'SELECT count(*) FROM public.b62_replayed_revocations')" == 0 ]]
echo AETHERIS_B62_PG16_REPLAY_SOURCE_UID_PROVENANCE_MISSING_NO_GO_PASS

[[ "$(standby_sql 'SELECT public.b62_apply_review_revocation(1,11)')" == t ]]
[[ "$(local_call "$standby" "$local_new_pw" \
  "SELECT public.b62_try_guarded_revocation_effect(4,98,'gap-one')")" == f ]]
[[ "$(standby_sql 'SELECT public.b62_apply_review_revocation(2,42)')" == t ]]
[[ "$(standby_sql 'SELECT public.b62_apply_review_revocation(2,42)')" == f ]]
[[ "$(local_call "$standby" "$local_new_pw" \
  "SELECT public.b62_try_guarded_revocation_effect(4,42,'blocked-subject')")" == f ]]
[[ "$(local_call "$standby" "$local_new_pw" \
  "SELECT public.b62_try_guarded_revocation_effect(4,98,'fresh-subject')")" == t ]]
[[ "$(local_call "$standby" "$local_new_pw" \
  "SELECT public.b62_try_guarded_revocation_effect(4,98,'fresh-subject')")" == f ]]

# Application caller cannot forge readiness or directly append protected data.
if local_call "$standby" "$local_new_pw" \
  "UPDATE public.b62_replay_gate SET applied_seq=999" >/dev/null 2>&1; then
  echo 'FAIL: review-only app writer mutated replay watermark' >&2; exit 1
fi
if local_call "$standby" "$local_new_pw" \
  "INSERT INTO public.b62_revocation_guarded_actions VALUES('forged',98,4)" \
  >/dev/null 2>&1; then
  echo 'FAIL: review-only app writer bypassed effect function' >&2; exit 1
fi
if local_call "$standby" "$local_new_pw" \
  "SELECT public.b62_apply_review_revocation(3,98)" >/dev/null 2>&1; then
  echo 'FAIL: application writer invoked trusted replay function' >&2; exit 1
fi
if standby_sql 'UPDATE public.b62_replay_gate SET applied_seq=1' >/dev/null 2>&1; then
  echo 'FAIL: replay sequence moved backward' >&2; exit 1
fi
[[ "$(standby_sql "SELECT applied_seq FROM public.b62_replay_gate WHERE resource_key='runtime'")" == 2 ]]
echo AETHERIS_B62_PG16_SEPARATE_RESOURCE_GAPLESS_REPLAY_GUARD_PASS

# UNSAFE NEGATIVE: an authenticated revocation committed in the independent
# source DB is invisible to the resource until a trusted barrier is delivered.
# This proves the current lab has NO safe automatic event-to-resource cutover.
coord_write 'INSERT INTO public.b62_revocation_event_source VALUES(3,98);' >/dev/null
[[ "$(local_call "$standby" "$local_new_pw" \
  "SELECT public.b62_try_guarded_revocation_effect(4,98,'unsafe-pre-barrier')")" == t ]]
echo AETHERIS_B62_PG16_SOURCE_ADVANCE_BEFORE_RESOURCE_BARRIER_UNSAFE_ALLOW_NO_GO_PASS

# Manual synthetic barrier delivery. It is NOT trusted automatic consensus,
# not a cryptographic/signed event stream and NOT a production protocol.
standby_sql "UPDATE public.b62_replay_gate
  SET required_seq=3, admitted=false WHERE resource_key='runtime';" >/dev/null
[[ "$(local_call "$standby" "$local_new_pw" \
  "SELECT public.b62_try_guarded_revocation_effect(4,99,'while-catchup')")" == f ]]
[[ "$(standby_sql 'SELECT public.b62_apply_review_revocation(3,98)')" == t ]]
standby_sql "UPDATE public.b62_replay_gate SET admitted=true
  WHERE resource_key='runtime' AND applied_seq=required_seq;" >/dev/null
[[ "$(local_call "$standby" "$local_new_pw" \
  "SELECT public.b62_try_guarded_revocation_effect(4,98,'revoked-after-catchup')")" == f ]]
[[ "$(local_call "$standby" "$local_new_pw" \
  "SELECT public.b62_try_guarded_revocation_effect(4,99,'safe-after-catchup')")" == t ]]
if standby_sql 'UPDATE public.b62_replay_gate SET required_seq=2' >/dev/null 2>&1; then
  echo 'FAIL: required source watermark moved backward' >&2; exit 1
fi
[[ "$(standby_sql 'SELECT count(*) FROM public.b62_revocation_guarded_actions')" == 3 ]]
echo AETHERIS_B62_PG16_SEPARATE_DB_WATERMARK_BARRIER_REPLAY_NO_GO_PASS

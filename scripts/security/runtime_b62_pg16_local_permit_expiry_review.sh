#!/usr/bin/env bash
# Disposable PG16 test only. This DEMONSTRATES a local permit deadline
# bounding NEW authorizations on coordinator outage; it DOES NOT guarantee
# absence of previously authorized writes that commit after expiry.
[[ "$GITHUB_ACTIONS" == true ]] || exit 92
[[ "$AETHERIS_B62_REPLICA_CI_ONLY" == yes ]] || exit 92
[[ "$authority" == aetheris-b62-coordinator-* ]] || exit 92
[[ "$standby" == aetheris-b62-standby-* ]] || exit 92
[[ "$local_writer" == b62_local_writer ]] || exit 92
test "$(standby_sql 'SELECT pg_is_in_recovery()')" = f
test "$(standby_sql "SELECT applied_seq || ':' || required_seq || ':' || admitted::text
 FROM public.b62_replay_gate WHERE resource_key='runtime'")" = '7:7:true'
test "$(coord_write 'SELECT count(*) FROM public.b62_revocation_event_source WHERE seq=8')" = 0

# A separate expiry is introduced AFTER every earlier experiment. The
# SECURITY DEFINER owner can lock the permit row (constrained dummy column),
# but cannot change its deadline or terms. Only this bounded entrypoint is
# granted to the restricted login; the previous unbounded one is revoked.
standby_sql "BEGIN;
 CREATE TABLE public.b62_effect_permit(
   resource_key text PRIMARY KEY,
   term bigint NOT NULL CHECK(term>0),
   expires_at timestamptz NOT NULL,
   lock_guard smallint NOT NULL DEFAULT 0 CHECK(lock_guard=0));
 INSERT INTO public.b62_effect_permit(resource_key,term,expires_at)
   VALUES('runtime',4,clock_timestamp()+interval '7 seconds');
 REVOKE ALL ON public.b62_effect_permit FROM PUBLIC;
 GRANT SELECT ON public.b62_effect_permit TO $local_owner;
 GRANT UPDATE(lock_guard) ON public.b62_effect_permit TO $local_owner;
 CREATE FUNCTION public.b62_try_deadline_bounded_effect(
   p_term bigint,p_uid bigint,p_request text)
 RETURNS boolean LANGUAGE plpgsql SECURITY DEFINER
 SET search_path=pg_catalog,pg_temp AS \$b62_deadline\$
 BEGIN
   IF session_user <> '$local_writer' OR p_term<>4
      OR p_uid IS NULL OR p_uid<1 OR p_request IS NULL THEN
     RETURN false;
   END IF;
   PERFORM 1 FROM public.b62_effect_permit
     WHERE resource_key='runtime' AND term=p_term
       AND expires_at>clock_timestamp()
       AND NOT pg_is_in_recovery()
     FOR SHARE;
   IF NOT FOUND THEN RETURN false; END IF;
   RETURN public.b62_try_guarded_revocation_effect(p_term,p_uid,p_request);
 END \$b62_deadline\$;
 ALTER FUNCTION public.b62_try_deadline_bounded_effect(bigint,bigint,text)
   OWNER TO $local_owner;
 REVOKE ALL ON FUNCTION public.b62_try_deadline_bounded_effect(bigint,bigint,text)
   FROM PUBLIC;
 GRANT EXECUTE ON FUNCTION public.b62_try_deadline_bounded_effect(bigint,bigint,text)
   TO $local_writer;
 REVOKE EXECUTE ON FUNCTION public.b62_try_guarded_revocation_effect(bigint,bigint,text)
   FROM $local_writer;
 COMMIT;" >/dev/null

# The old public SQL entrypoint must be inaccessible to restricted callers.
if local_call "$standby" "$local_new_pw" \
  "SELECT public.b62_try_guarded_revocation_effect(4,108,'legacy-bypass')" >/dev/null 2>&1; then
  echo 'FAIL: restricted writer bypassed deadline using old entrypoint' >&2
  exit 1
fi
if local_call "$standby" "$local_new_pw" \
  "UPDATE public.b62_effect_permit SET expires_at=clock_timestamp()+interval '1 hour'" >/dev/null 2>&1; then
  echo 'FAIL: restricted writer extended permit' >&2
  exit 1
fi
if standby_sql "BEGIN; SET LOCAL ROLE $local_owner;
 UPDATE public.b62_effect_permit SET expires_at=clock_timestamp()+interval '1 hour';" \
 >/dev/null 2>&1; then
  echo 'FAIL: effect function owner extended permit deadline' >&2
  exit 1
fi

# A source revocation COMMITTED but unknown to resource illustrates the
# unavoidable window of a locally cached permit, even before it expires.
coord_write "INSERT INTO public.b62_revocation_event_source VALUES(8,107);" >/dev/null
test "$(coord_write 'SELECT uid FROM public.b62_revocation_event_source WHERE seq=8')" = 107
docker stop "$authority" >/dev/null
test -z "$(docker ps --filter "name=^/$authority$" --format '{{.Names}}')"
test "$(local_call "$standby" "$local_new_pw" \
  "SELECT public.b62_try_deadline_bounded_effect(4,107,'revoked-before-expiry')")" = t
test "$(standby_sql "SELECT count(*) FROM public.b62_revocation_guarded_actions
 WHERE request_id='revoked-before-expiry' AND uid=107")" = 1
echo AETHERIS_B62_PG16_SHORT_LOCAL_PERMIT_STILL_ALLOWS_UNKNOWN_SOURCE_REVOKE_NO_GO_PASS

# POSITIVE COUNTEREXAMPLE against treating statement-time expiry as
# commit-time fencing: the effect is inserted before expiry and commits
# AFTER the permit has expired. This must NOT be released as safe behavior.
effect_log="$(mktemp)"
docker exec -e "PGPASSWORD=$local_new_pw" -e PGCONNECT_TIMEOUT=2 \
 "$standby" psql -X -A -t -w -v ON_ERROR_STOP=1 \
 -h 127.0.0.1 -p 5432 -U "$local_writer" -d "$db" -c \
 "BEGIN;
  SELECT public.b62_try_deadline_bounded_effect(
    4,108,'inflight-expiry-late-commit');
  SELECT pg_sleep(9);
  COMMIT;" > "$effect_log" 2>&1
grep -Fxq t "$effect_log"
grep -Fxq COMMIT "$effect_log"
rm -f "$effect_log"
test "$(standby_sql "SELECT (expires_at<clock_timestamp())::text
 FROM public.b62_effect_permit WHERE resource_key='runtime'")" = true
test "$(standby_sql "SELECT count(*) FROM public.b62_revocation_guarded_actions
 WHERE request_id='inflight-expiry-late-commit' AND uid=108")" = 1
echo AETHERIS_B62_PG16_INFLIGHT_EFFECT_COMMITS_AFTER_LOCAL_PERMIT_EXPIRY_NO_GO_PASS

# At the next statement, an expired permit denies even with local replay
# admitted=true and a previously valid local term; old entrypoint stays shut.
test "$(local_call "$standby" "$local_new_pw" \
 "SELECT public.b62_try_deadline_bounded_effect(4,109,'denied-after-expiry')")" = f
test "$(local_call "$standby" "$local_new_pw" \
 "SELECT public.b62_try_deadline_bounded_effect(4,107,'revoked-no-renewal')")" = f
test "$(standby_sql "SELECT count(*) FROM public.b62_revocation_guarded_actions
 WHERE request_id='denied-after-expiry'")" = 0
echo AETHERIS_B62_PG16_EXPIRED_LOCAL_PERMIT_DENIES_NEW_EFFECTS_ON_OUTAGE_PASS

# The resource remains deny-only until the source recovers, exact sequence
# is replayed, AND the synthetic owner explicitly provides a NEW permit.
standby_sql "UPDATE public.b62_replay_gate
 SET required_seq=8, admitted=false WHERE resource_key='runtime';" >/dev/null
docker start "$authority" >/dev/null
recovered=false
for attempt in $(seq 1 60); do
  if [[ "$(coord_write 'SELECT 1' 2>/dev/null || true)" == 1 ]]; then
    recovered=true
    break
  fi
  sleep 0.25
done
test "$recovered" = true
test "$(standby_sql 'SELECT public.b62_apply_review_revocation(8,107)')" = t
standby_sql "UPDATE public.b62_replay_gate
 SET admitted=true WHERE resource_key='runtime' AND required_seq=8
 AND applied_seq=8;" >/dev/null
test "$(local_call "$standby" "$local_new_pw" \
 "SELECT public.b62_try_deadline_bounded_effect(4,110,'still-expired-after-replay')")" = f
# Operator-controlled renewal is intentionally untrusted in this lab:
# genuine signed lease provenance and globally effective fencing remain absent.
standby_sql "UPDATE public.b62_effect_permit
 SET expires_at=clock_timestamp()+interval '30 seconds'
 WHERE resource_key='runtime' AND term=4;" >/dev/null
test "$(local_call "$standby" "$local_new_pw" \
 "SELECT public.b62_try_deadline_bounded_effect(4,107,'revoked-after-replay')")" = f
test "$(local_call "$standby" "$local_new_pw" \
 "SELECT public.b62_try_deadline_bounded_effect(4,110,'eligible-after-review-renewal')")" = t
echo AETHERIS_B62_PG16_PERMIT_RENEWAL_REQUIRES_REPLAY_BUT_NOT_REAL_AUTHORITY_NO_GO_PASS

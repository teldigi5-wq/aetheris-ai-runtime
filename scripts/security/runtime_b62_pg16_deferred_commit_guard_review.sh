#!/usr/bin/env bash
# Disposable PostgreSQL16 review ONLY: a DEFERRABLE INITIALLY DEFERRED
# constraint trigger checks a resource-local permit near COMMIT.
# This does NOT solve external coordinator revocations, partitioned writable
# former primaries, signed authority distribution, or global linearizability.
[[ "$GITHUB_ACTIONS" == true ]] || exit 92
[[ "$AETHERIS_B62_REPLICA_CI_ONLY" == yes ]] || exit 92
[[ "$standby" == aetheris-b62-standby-* ]] || exit 92
[[ "$local_writer" == b62_local_writer ]] || exit 92
test "$(standby_sql 'SELECT pg_is_in_recovery()')" = f
test "$(standby_sql "SELECT applied_seq || ':' || required_seq || ':' || admitted::text FROM public.b62_replay_gate WHERE resource_key='runtime'")" = '8:8:true'
test "$(coord_write 'SELECT count(*) FROM public.b62_revocation_event_source WHERE seq=9')" = 0

# Trigger belongs to the disposable database owner, not the untrusted writer.
# This is not an approved production RLS/GRANT/trigger authority model.
standby_sql "BEGIN;
 CREATE FUNCTION public.b62_check_effect_at_deferred_commit()
 RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER
 SET search_path=pg_catalog,pg_temp AS \$b62_commit\$
 BEGIN
   PERFORM 1 FROM public.b62_effect_permit p
     JOIN public.b62_replay_gate g ON g.resource_key=p.resource_key
    WHERE p.resource_key='runtime' AND g.endpoint='$standby'
      AND p.term=NEW.term AND g.term=NEW.term
      AND g.admitted AND g.applied_seq>=g.required_seq
      AND p.expires_at>clock_timestamp() AND NOT pg_is_in_recovery()
    FOR SHARE OF p,g;
   IF NOT FOUND OR EXISTS (
     SELECT 1 FROM public.b62_replayed_revocations
      WHERE uid=NEW.uid) THEN
     RAISE EXCEPTION 'B62_COMMIT_GUARD_DENY'
       USING ERRCODE='23514';
   END IF;
   RETURN NEW;
 END \$b62_commit\$;
 REVOKE ALL ON FUNCTION public.b62_check_effect_at_deferred_commit()
   FROM PUBLIC;
 CREATE CONSTRAINT TRIGGER b62_effect_commit_time_guard
   AFTER INSERT ON public.b62_revocation_guarded_actions
   DEFERRABLE INITIALLY DEFERRED
   FOR EACH ROW EXECUTE FUNCTION public.b62_check_effect_at_deferred_commit();
 COMMIT;" >/dev/null
test "$(standby_sql "SELECT count(*) FROM pg_trigger t
 JOIN pg_class c ON c.oid=t.tgrelid
 WHERE c.relname='b62_revocation_guarded_actions'
  AND t.tgname='b62_effect_commit_time_guard'
  AND t.tgdeferrable AND t.tginitdeferred AND t.tgenabled='O'")" = 1

# The already-connected login cannot remove or disable the trigger.
if local_call "$standby" "$local_new_pw" \
 "ALTER TABLE public.b62_revocation_guarded_actions
  DISABLE TRIGGER b62_effect_commit_time_guard" >/dev/null 2>&1; then
 echo 'FAIL: restricted effect caller disabled deferred trigger' >&2; exit 1
fi
if local_call "$standby" "$local_new_pw" \
 "SELECT public.b62_check_effect_at_deferred_commit()" >/dev/null 2>&1; then
 echo 'FAIL: restricted effect caller invoked privileged trigger function' >&2; exit 1
fi

# Commit-time guard denies a pending effect AFTER it passed statement-time
# wrapper authorization. The SELECT output must contain 't' but COMMIT fails.
standby_sql "UPDATE public.b62_effect_permit
 SET expires_at=clock_timestamp()+interval '12 seconds'
 WHERE resource_key='runtime' AND term=4;" >/dev/null
late_log="$(mktemp)"
if docker exec -e "PGPASSWORD=$local_new_pw" -e PGCONNECT_TIMEOUT=2 \
 "$standby" psql -X -A -t -w -v ON_ERROR_STOP=1 \
 -h 127.0.0.1 -p 5432 -U "$local_writer" -d "$db" -c \
 "BEGIN;
  SELECT public.b62_try_deadline_bounded_effect(
    4,112,'deferred-deadline-must-rollback');
  SELECT pg_sleep(14);
  COMMIT;" >"$late_log" 2>&1; then
 echo 'FAIL: transaction committed despite expired resource permit' >&2
 rm -f "$late_log"; exit 1
fi
grep -Fxq t "$late_log"
grep -Fq 'B62_COMMIT_GUARD_DENY' "$late_log"
rm -f "$late_log"
test "$(standby_sql "SELECT count(*) FROM public.b62_revocation_guarded_actions
 WHERE request_id='deferred-deadline-must-rollback'")" = 0
test "$(standby_sql "SELECT CASE WHEN expires_at<clock_timestamp()
 THEN 'expired' ELSE 'fresh' END FROM public.b62_effect_permit
 WHERE resource_key='runtime'")" = expired
echo AETHERIS_B62_PG16_DEFERRED_COMMIT_EXPIRED_PERMIT_ROLLS_BACK_EFFECT_PASS

# An eligible effect must still succeed when the permit and local replay
# watermark are fresh and the per-resource term matches.
standby_sql "UPDATE public.b62_effect_permit
 SET expires_at=clock_timestamp()+interval '30 seconds'
 WHERE resource_key='runtime' AND term=4;" >/dev/null
test "$(local_call "$standby" "$local_new_pw" \
 "SELECT public.b62_try_deadline_bounded_effect(4,113,'deferred-fresh-valid')")" = t
test "$(standby_sql "SELECT count(*) FROM public.b62_revocation_guarded_actions
 WHERE request_id='deferred-fresh-valid' AND uid=113")" = 1
test "$(local_call "$standby" "$local_new_pw" \
 "SELECT public.b62_try_deadline_bounded_effect(4,107,'deferred-revoked-uid')")" = f
echo AETHERIS_B62_PG16_DEFERRED_COMMIT_FRESH_PERMIT_PRESERVES_DENIAL_PASS

# Global negative witness: independently publishing UID 114 revocation in
# another PostgreSQL server does not update our local source watermark.
# The deferred trigger cannot infer information this resource never received.
coord_write 'INSERT INTO public.b62_revocation_event_source VALUES(9,114);' >/dev/null
test "$(coord_write 'SELECT uid FROM public.b62_revocation_event_source WHERE seq=9')" = 114
test "$(standby_sql "SELECT applied_seq || ':' || required_seq FROM public.b62_replay_gate WHERE resource_key='runtime'")" = '8:8'
test "$(local_call "$standby" "$local_new_pw" \
 "SELECT public.b62_try_deadline_bounded_effect(
 4,114,'deferred-unknown-source-revoke')")" = t
test "$(standby_sql "SELECT count(*) FROM public.b62_revocation_guarded_actions
 WHERE request_id='deferred-unknown-source-revoke' AND uid=114")" = 1
echo AETHERIS_B62_PG16_DEFERRED_COMMIT_CANNOT_DETECT_UNREPLAYED_SOURCE_NO_GO_PASS

# Only bounded review recovery: manual local DENY barrier, ordered replay,
# then re-admission for a different UID. Existing committed effect remains.
standby_sql "UPDATE public.b62_replay_gate SET admitted=false,required_seq=9
 WHERE resource_key='runtime' AND applied_seq=8;" >/dev/null
test "$(local_call "$standby" "$local_new_pw" \
 "SELECT public.b62_try_deadline_bounded_effect(4,115,'deferred-while-denied')")" = f
test "$(standby_sql 'SELECT public.b62_apply_review_revocation(9,114)')" = t
standby_sql "UPDATE public.b62_replay_gate SET admitted=true
 WHERE resource_key='runtime' AND required_seq=9 AND applied_seq=9;" >/dev/null
test "$(local_call "$standby" "$local_new_pw" \
 "SELECT public.b62_try_deadline_bounded_effect(4,114,'deferred-revoked-after-replay')")" = f
test "$(local_call "$standby" "$local_new_pw" \
 "SELECT public.b62_try_deadline_bounded_effect(4,115,'deferred-other-allowed')")" = t
test "$(standby_sql "SELECT count(*) FROM public.b62_revocation_guarded_actions
 WHERE request_id='deferred-unknown-source-revoke'")" = 1
echo AETHERIS_B62_PG16_DEFERRED_COMMIT_REPLAY_RECOVERY_ONLY_TEST_PASS

#!/usr/bin/env bash
# Review-only PostgreSQL16 race witness: a source revocation commits in the
# INDEPENDENT coordinator while a resource-local authorized INSERT is pending.
# This is a deliberate NO_GO demonstration, NOT a safe revocation protocol.
[[ "$GITHUB_ACTIONS" == true ]] || exit 92
[[ "$AETHERIS_B62_REPLICA_CI_ONLY" == yes ]] || exit 92
[[ "$standby" == aetheris-b62-standby-* ]] || exit 92
[[ "$authority" == aetheris-b62-coordinator-* ]] || exit 92
[[ "$local_writer" == b62_local_writer ]] || exit 92
test "$(standby_sql 'SELECT pg_is_in_recovery()')" = f
test "$(coord_write "SELECT endpoint || ':' || term FROM public.leader_fence WHERE authority_key='runtime'")" = "$standby:4"
test "$(coord_write 'SELECT count(*) FROM public.b62_revocation_event_source WHERE seq=3 AND uid=98')" = 1
test "$(coord_write 'SELECT count(*) FROM public.b62_revocation_event_source WHERE seq=4')" = 0
test "$(standby_sql "SELECT count(*) FROM public.b62_replayed_revocations WHERE uid=99")" = 0
test "$(standby_sql "SELECT applied_seq || ':' || required_seq || ':' || admitted::text FROM public.b62_replay_gate WHERE resource_key='runtime'")" = '3:3:true'

# The restricted writer executes a permitted effect and DELAYS its COMMIT
# after holding the resource fence's transaction row lock.
race_name="b62-inflight-$suffix"
race_log="$(mktemp)"
docker exec -e "PGPASSWORD=$local_new_pw" -e "PGAPPNAME=$race_name" \
  -e PGCONNECT_TIMEOUT=2 "$standby" psql -X -A -t -w \
  -v ON_ERROR_STOP=1 -h 127.0.0.1 -p 5432 \
  -U "$local_writer" -d "$db" -c \
  "BEGIN;
   SELECT public.b62_try_guarded_revocation_effect(
     4,99,'inflight-after-source-revoke');
   SELECT pg_sleep(10);
   COMMIT;" > "$race_log" 2>&1 &
race_pid=$!
race_sleeping=false
for attempt in $(seq 1 50); do
  if [[ "$(standby_sql "SELECT count(*) FROM pg_stat_activity WHERE application_name='$race_name' AND state='active' AND wait_event='PgSleep'")" == 1 ]]; then
    race_sleeping=true
    break
  fi
  if ! kill -0 "$race_pid" 2>/dev/null; then break; fi
  sleep 0.1
done
if [[ "$race_sleeping" != true ]]; then
  echo "FAIL: transaction did not reach the deterministic pre-COMMIT barrier" >&2
  wait "$race_pid" || true
  rm -f "$race_log"
  exit 1
fi

# Commit the revocation in the OTHER physical PostgreSQL server, before
# allowing the resource transaction to finish. No resource-side barrier
# has been installed, which is precisely the unsafe failure mode.
coord_write "INSERT INTO public.b62_revocation_event_source VALUES(4,99)" >/dev/null
test "$(coord_write 'SELECT count(*) FROM public.b62_revocation_event_source WHERE seq=4 AND uid=99')" = 1
test "$(standby_sql "SELECT count(*) FROM public.b62_revocation_guarded_actions WHERE request_id='inflight-after-source-revoke'")" = 0
if ! wait "$race_pid"; then
  echo "FAIL: authorized in-flight resource transaction failed unexpectedly" >&2
  rm -f "$race_log"
  exit 1
fi
grep -Fxq t "$race_log"
grep -Fxq COMMIT "$race_log"
rm -f "$race_log"
test "$(standby_sql "SELECT count(*) FROM public.b62_revocation_guarded_actions WHERE request_id='inflight-after-source-revoke' AND uid=99 AND term=4")" = 1
test "$(standby_sql "SELECT applied_seq || ':' || required_seq FROM public.b62_replay_gate WHERE resource_key='runtime'")" = '3:3'
echo AETHERIS_B62_PG16_INFLIGHT_EFFECT_COMMITS_AFTER_SOURCE_REVOCATION_NO_GO_PASS

#!/usr/bin/env bash
# TEST-ONLY: prove a reachable PostgreSQL resource can drain active effects
# and install DENY BEFORE the coordinator publishes a revocation event.
# Cannot guarantee fencing of an unreachable/isolated old primary, and is
# NOT a production distributed commit/revocation algorithm.
[[ "$GITHUB_ACTIONS" == true ]] || exit 92
[[ "$AETHERIS_B62_REPLICA_CI_ONLY" == yes ]] || exit 92
[[ "$standby" == aetheris-b62-standby-* ]] || exit 92
test "$(coord_write 'SELECT count(*) FROM public.b62_revocation_event_source WHERE seq=4 AND uid=99')" = 1
test "$(coord_write 'SELECT count(*) FROM public.b62_revocation_event_source WHERE seq=5')" = 0
test "$(standby_sql "SELECT applied_seq || ':' || required_seq || ':' || admitted::text FROM public.b62_replay_gate WHERE resource_key='runtime'")" = '3:3:true'

writer_name="b62-controlled-writer-$suffix"
barrier_name="b62-controlled-barrier-$suffix"
writer_log="$(mktemp)"
barrier_log="$(mktemp)"

# Resource-side effect holds FOR SHARE on b62_replay_gate until COMMIT.
docker exec -e "PGPASSWORD=$local_new_pw" -e "PGAPPNAME=$writer_name" \
  -e PGCONNECT_TIMEOUT=2 "$standby" psql -X -A -t -w \
  -v ON_ERROR_STOP=1 -h 127.0.0.1 -p 5432 \
  -U "$local_writer" -d "$db" -c \
  "BEGIN;
   SELECT public.b62_try_guarded_revocation_effect(
     4,101,'controlled-pre-barrier-effect');
   SELECT pg_sleep(10);
   COMMIT;" > "$writer_log" 2>&1 &
writer_pid=$!

writer_sleeping=false
for attempt in $(seq 1 50); do
  if [[ "$(standby_sql "SELECT count(*) FROM pg_stat_activity WHERE application_name='$writer_name' AND state='active' AND wait_event='PgSleep'")" == 1 ]]; then
    writer_sleeping=true
    break
  fi
  if ! kill -0 "$writer_pid" 2>/dev/null; then break; fi
  sleep 0.1
done
if [[ "$writer_sleeping" != true ]]; then
  echo "FAIL: controlled writer did not reach pre-commit pause" >&2
  wait "$writer_pid" || true
  rm -f "$writer_log" "$barrier_log"
  exit 1
fi

# An ordinary resource UPDATE must wait for the writer's local SHARE lock.
# No coordinator event is published while the local resource still admits.
standby_sql "SET application_name='$barrier_name';
 UPDATE public.b62_replay_gate SET admitted=false, required_seq=5
 WHERE resource_key='runtime' AND required_seq=3
 RETURNING required_seq;" > "$barrier_log" 2>&1 &
barrier_pid=$!

barrier_blocked=false
for attempt in $(seq 1 50); do
  if [[ "$(standby_sql "SELECT count(*) FROM pg_stat_activity WHERE application_name='$barrier_name' AND state='active' AND wait_event_type='Lock'")" == 1 ]]; then
    barrier_blocked=true
    break
  fi
  if ! kill -0 "$barrier_pid" 2>/dev/null; then break; fi
  sleep 0.1
done
if [[ "$barrier_blocked" != true ]]; then
  echo "FAIL: resource barrier did not wait for the authorized transaction" >&2
  wait "$writer_pid" || true
  wait "$barrier_pid" || true
  rm -f "$writer_log" "$barrier_log"
  exit 1
fi
test "$(coord_write 'SELECT count(*) FROM public.b62_revocation_event_source WHERE seq=5')" = 0
# The barrier is blocked; the resource row must still reflect the old state.
test "$(standby_sql "SELECT required_seq || ':' || admitted::text FROM public.b62_replay_gate WHERE resource_key='runtime'")" = '3:true'

wait "$writer_pid"
wait "$barrier_pid"
grep -Fxq t "$writer_log"
grep -Fxq COMMIT "$writer_log"
grep -Fxq 5 "$barrier_log"
rm -f "$writer_log" "$barrier_log"
test "$(standby_sql "SELECT required_seq || ':' || admitted::text FROM public.b62_replay_gate WHERE resource_key='runtime'")" = '5:false'
test "$(standby_sql "SELECT count(*) FROM public.b62_revocation_guarded_actions WHERE request_id='controlled-pre-barrier-effect' AND uid=101")" = 1
test "$(local_call "$standby" "$local_new_pw" "SELECT public.b62_try_guarded_revocation_effect(4,102,'denied-after-local-barrier')")" = f
echo AETHERIS_B62_PG16_RESOURCE_BARRIER_DRAINS_INFLIGHT_TRANSACTION_PASS

# Only AFTER resource-side DENY is committed do we publish the next source
# revocation, replay the missing source events, and re-admit other identities.
coord_write 'INSERT INTO public.b62_revocation_event_source VALUES(5,101);' >/dev/null
test "$(coord_write 'SELECT count(*) FROM public.b62_revocation_event_source WHERE seq=5 AND uid=101')" = 1
test "$(standby_sql 'SELECT public.b62_apply_review_revocation(4,99)')" = t
test "$(standby_sql 'SELECT public.b62_apply_review_revocation(5,101)')" = t
standby_sql "UPDATE public.b62_replay_gate SET admitted=true
 WHERE resource_key='runtime' AND required_seq=5 AND applied_seq=5;" >/dev/null
test "$(local_call "$standby" "$local_new_pw" "SELECT public.b62_try_guarded_revocation_effect(4,99,'blocked-post-barrier-99')")" = f
test "$(local_call "$standby" "$local_new_pw" "SELECT public.b62_try_guarded_revocation_effect(4,101,'blocked-post-barrier-101')")" = f
test "$(local_call "$standby" "$local_new_pw" "SELECT public.b62_try_guarded_revocation_effect(4,102,'allowed-post-barrier-102')")" = t
echo AETHERIS_B62_PG16_REACHABLE_FENCE_BEFORE_SOURCE_PUBLISH_TEST_ONLY_PASS

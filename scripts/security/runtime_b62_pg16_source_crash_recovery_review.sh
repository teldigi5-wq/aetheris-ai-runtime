#!/usr/bin/env bash
# REVIEW-ONLY: coordinator crashes AFTER a resource DENY barrier commits,
# BEFORE source revocation publication. The resource must not re-admit
# until source recovery and full gap-free replay of the pending event.
# This checks cooperative databases only; it is not a global failover protocol.
[[ "$GITHUB_ACTIONS" == true ]] || exit 92
[[ "$AETHERIS_B62_REPLICA_CI_ONLY" == yes ]] || exit 92
[[ "$authority" == aetheris-b62-coordinator-* ]] || exit 92
[[ "$standby" == aetheris-b62-standby-* ]] || exit 92
test "$(standby_sql "SELECT applied_seq || ':' || required_seq || ':' || admitted::text FROM public.b62_replay_gate WHERE resource_key='runtime'")" = '5:5:true'
test "$(coord_write 'SELECT count(*) FROM public.b62_revocation_event_source WHERE seq=6')" = 0

# The resource commits its DENY barrier before the source advances.
standby_sql "UPDATE public.b62_replay_gate
  SET required_seq=6, admitted=false
  WHERE resource_key='runtime' AND applied_seq=5 AND required_seq=5
    AND admitted=true;" >/dev/null
test "$(standby_sql "SELECT applied_seq || ':' || required_seq || ':' || admitted::text FROM public.b62_replay_gate WHERE resource_key='runtime'")" = '5:6:false'
test "$(local_call "$standby" "$local_new_pw" \
  "SELECT public.b62_try_guarded_revocation_effect(4,103,'deny-before-crash')")" = f

# The independent source process is temporarily unavailable. A local
# watermark gap must keep the resource DENY-only, not force it to re-open.
docker stop "$authority" >/dev/null
test -z "$(docker ps --filter "name=^/$authority$" --format '{{.Names}}')"
test "$(local_call "$standby" "$local_new_pw" \
  "SELECT public.b62_try_guarded_revocation_effect(4,103,'deny-while-source-down')")" = f
test "$(standby_sql "SELECT required_seq || ':' || applied_seq FROM public.b62_replay_gate WHERE resource_key='runtime'")" = '6:5'

# Even a mistaken synthetic owner setting admitted=true is insufficient
# when the durable local replay watermark has not reached required_seq.
standby_sql "UPDATE public.b62_replay_gate
  SET admitted=true WHERE resource_key='runtime';" >/dev/null
test "$(local_call "$standby" "$local_new_pw" \
  "SELECT public.b62_try_guarded_revocation_effect(4,103,'deny-with-incorrect-admit')")" = f
standby_sql "UPDATE public.b62_replay_gate
  SET admitted=false WHERE resource_key='runtime';" >/dev/null
echo AETHERIS_B62_PG16_SOURCE_OUTAGE_AFTER_BARRIER_FAIL_CLOSED_PASS

# Recovery must not itself release the barrier. The source has not yet
# published seq=6, so the resource still denies all new effects.
docker start "$authority" >/dev/null
recovered=false
for attempt in $(seq 1 40); do
  if [[ "$(coord_write 'SELECT 1' 2>/dev/null || true)" == 1 ]]; then
    recovered=true
    break
  fi
  sleep 0.25
done
test "$recovered" = true
test "$(coord_write 'SELECT count(*) FROM public.b62_revocation_event_source WHERE seq=6')" = 0
test "$(local_call "$standby" "$local_new_pw" \
  "SELECT public.b62_try_guarded_revocation_effect(4,103,'deny-after-restart')")" = f

# Publish source event AFTER the resource is confirmed closed, then
# replay the exact sequence and allow only an unrevoked identity.
coord_write "INSERT INTO public.b62_revocation_event_source VALUES(6,103);" >/dev/null
test "$(coord_write 'SELECT uid FROM public.b62_revocation_event_source WHERE seq=6')" = 103
test "$(local_call "$standby" "$local_new_pw" \
  "SELECT public.b62_try_guarded_revocation_effect(4,103,'deny-before-replay')")" = f
test "$(standby_sql 'SELECT public.b62_apply_review_revocation(6,103)')" = t
standby_sql "UPDATE public.b62_replay_gate SET admitted=true
  WHERE resource_key='runtime' AND applied_seq=6 AND required_seq=6
    AND admitted=false;" >/dev/null
test "$(standby_sql "SELECT applied_seq || ':' || required_seq || ':' || admitted::text FROM public.b62_replay_gate WHERE resource_key='runtime'")" = '6:6:true'
test "$(local_call "$standby" "$local_new_pw" \
  "SELECT public.b62_try_guarded_revocation_effect(4,103,'revoked-after-replay')")" = f
test "$(local_call "$standby" "$local_new_pw" \
  "SELECT public.b62_try_guarded_revocation_effect(4,104,'eligible-after-recovery')")" = t
echo AETHERIS_B62_PG16_RESTART_REPLAY_BEFORE_READMISSION_TEST_ONLY_PASS

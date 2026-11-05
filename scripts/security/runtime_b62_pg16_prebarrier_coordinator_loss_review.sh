#!/usr/bin/env bash
# NEGATIVE-ONLY disposable PostgreSQL16 witness:
# source revocation COMMITTED, coordinator disappears before protected
# resource learns the event or closes its local admission barrier.
# This is not an accepted protocol, production authorization or safe
# failover; the stale admission below is intentionally a NO_GO result.
[[ "$GITHUB_ACTIONS" == true ]] || exit 92
[[ "$AETHERIS_B62_REPLICA_CI_ONLY" == yes ]] || exit 92
[[ "$authority" == aetheris-b62-coordinator-* ]] || exit 92
[[ "$standby" == aetheris-b62-standby-* ]] || exit 92
[[ "$local_writer" == b62_local_writer ]] || exit 92
test "$(standby_sql 'SELECT pg_is_in_recovery()')" = f
test "$(standby_sql "SELECT applied_seq || ':' || required_seq || ':' || admitted::text
  FROM public.b62_replay_gate WHERE resource_key='runtime'")" = '6:6:true'
test "$(coord_write 'SELECT count(*) FROM public.b62_revocation_event_source WHERE seq=7')" = 0

# Coordinator can authoritatively revoke UID 105; the local resource is
# not fenced and has NOT received required_seq=7.
coord_write 'INSERT INTO public.b62_revocation_event_source VALUES(7,105);' >/dev/null
test "$(coord_write 'SELECT uid FROM public.b62_revocation_event_source WHERE seq=7')" = 105
docker stop "$authority" >/dev/null
test -z "$(docker ps --filter "name=^/$authority$" --format '{{.Names}}')"
test "$(standby_sql "SELECT applied_seq || ':' || required_seq || ':' || admitted::text
  FROM public.b62_replay_gate WHERE resource_key='runtime'")" = '6:6:true'

# EXPECTED UNSAFE WITNESS: even though UID 105 was revoked in the source,
# its old resource-local admission state still allows an effect.
test "$(local_call "$standby" "$local_new_pw" \
 "SELECT public.b62_try_guarded_revocation_effect(
 4,105,'stale-prebarrier-source-outage')")" = t
test "$(standby_sql "SELECT count(*) FROM public.b62_revocation_guarded_actions
 WHERE request_id='stale-prebarrier-source-outage' AND uid=105")" = 1
echo AETHERIS_B62_PG16_COORDINATOR_LOST_BEFORE_RESOURCE_BARRIER_UNSAFE_ALLOW_NO_GO_PASS

# A delayed manual resource barrier can stop further admissions, but
# cannot undo the earlier committed effect or prove immediate revocation.
standby_sql "UPDATE public.b62_replay_gate
 SET required_seq=7, admitted=false
 WHERE resource_key='runtime' AND applied_seq=6 AND required_seq=6;" >/dev/null
test "$(local_call "$standby" "$local_new_pw" \
 "SELECT public.b62_try_guarded_revocation_effect(
 4,105,'denied-after-late-barrier')")" = f
standby_sql "UPDATE public.b62_replay_gate
 SET admitted=true WHERE resource_key='runtime';" >/dev/null
test "$(local_call "$standby" "$local_new_pw" \
 "SELECT public.b62_try_guarded_revocation_effect(
 4,106,'denied-insufficient-watermark')")" = f
standby_sql "UPDATE public.b62_replay_gate
 SET admitted=false WHERE resource_key='runtime';" >/dev/null
echo AETHERIS_B62_PG16_LATE_DENY_BARRIER_CANNOT_REVERSE_STALE_EFFECT_PASS

# Reconnect, replay the source event in order, then allow only unrelated
# identities. This is a restricted recovery demonstration, not consensus.
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
test "$(coord_write 'SELECT uid FROM public.b62_revocation_event_source WHERE seq=7')" = 105
test "$(local_call "$standby" "$local_new_pw" \
 "SELECT public.b62_try_guarded_revocation_effect(
 4,106,'still-denied-until-replay')")" = f
test "$(standby_sql 'SELECT public.b62_apply_review_revocation(7,105)')" = t
standby_sql "UPDATE public.b62_replay_gate SET admitted=true
 WHERE resource_key='runtime' AND applied_seq=7 AND required_seq=7
  AND admitted=false;" >/dev/null
test "$(standby_sql "SELECT applied_seq || ':' || required_seq || ':' || admitted::text
 FROM public.b62_replay_gate WHERE resource_key='runtime'")" = '7:7:true'
test "$(local_call "$standby" "$local_new_pw" \
 "SELECT public.b62_try_guarded_revocation_effect(
 4,105,'revoked-after-source-recovery')")" = f
test "$(local_call "$standby" "$local_new_pw" \
 "SELECT public.b62_try_guarded_revocation_effect(
 4,106,'eligible-after-source-recovery')")" = t
test "$(standby_sql "SELECT count(*) FROM public.b62_revocation_guarded_actions
 WHERE request_id='stale-prebarrier-source-outage' AND uid=105")" = 1
echo AETHERIS_B62_PG16_PREBARRIER_PARTITION_RECOVERY_WITH_IRREVERSIBLE_EFFECT_NO_GO_PASS

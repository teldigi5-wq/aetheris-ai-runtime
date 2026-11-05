#!/usr/bin/env bash
# Two genuine PostgreSQL 16 processes. Entirely disposable, review only.
set -Eeuo pipefail
test "$GITHUB_ACTIONS" = true
test "$AETHERIS_B62_REPLICA_CI_ONLY" = yes
[[ "$GITHUB_RUN_ID" =~ ^[0-9]+$ ]]
[[ "$GITHUB_RUN_ATTEMPT" =~ ^[0-9]+$ ]]
suffix="$GITHUB_RUN_ID-$GITHUB_RUN_ATTEMPT"
network="aetheris-b62-replica-$suffix"
primary="aetheris-b62-primary-$suffix"
standby="aetheris-b62-standby-$suffix"
authority="aetheris-b62-coordinator-$suffix"
dbuser=aetheris_replica_ci
db=aetheris_replica_disposable
replicauser=b62_ci_replication
owner_password="$(openssl rand -hex 24)"
replica_password="$(openssl rand -hex 24)"
held_action_output=""
transition_output=""
detached_action_output=""
cutover_race_dir=""
writer_drain_output=""
writer_drain_error=""
gate_tamper_error=""

cleanup() {
  local code=$?
  trap - EXIT
  docker rm -fv "$authority" "$standby" "$primary" >/dev/null 2>&1 || true
  docker network rm "$network" >/dev/null 2>&1 || true
  [[ -z "$held_action_output" ]] || rm -f -- "$held_action_output"
  [[ -z "$transition_output" ]] || rm -f -- "$transition_output"
  [[ -z "$detached_action_output" ]] || rm -f -- "$detached_action_output"
  [[ -z "$cutover_race_dir" ]] || rm -rf -- "$cutover_race_dir"
  [[ -z "$writer_drain_output" ]] || rm -f -- "$writer_drain_output"
  [[ -z "$writer_drain_error" ]] || rm -f -- "$writer_drain_error"
  [[ -z "$gate_tamper_error" ]] || rm -f -- "$gate_tamper_error"
  if test "$code" -eq 0; then
    echo AETHERIS_B62_PHYSICAL_REPLICA_CONTAINERS_NETWORK_DESTROYED_PASS
  fi
  exit "$code"
}
trap cleanup EXIT

primary_sql() {
  docker exec "$primary" psql -X -A -t -v ON_ERROR_STOP=1 -U "$dbuser" -d "$db" -c "$1"
}
standby_sql() {
  docker exec -e "PGPASSWORD=$owner_password" "$standby" \
    psql -X -A -t -v ON_ERROR_STOP=1 -h 127.0.0.1 -p 5432 -U "$dbuser" -d "$db" -c "$1"
}
docker network create --internal "$network" >/dev/null
docker run -d --name "$primary" --network "$network" \
  -e "POSTGRES_USER=$dbuser" -e "POSTGRES_PASSWORD=$owner_password" \
  -e "POSTGRES_DB=$db" -e "POSTGRES_INITDB_ARGS=--auth-host=scram-sha-256" \
  postgres:16 >/dev/null
ready=false
for attempt in $(seq 1 50); do
  if test "$(primary_sql 'SELECT 1' 2>/dev/null || true)" = 1; then
    ready=true; break
  fi
  sleep 1
done
test "$ready" = true
test "$(primary_sql 'SELECT pg_is_in_recovery()')" = f
test "$(primary_sql 'SHOW wal_level')" = replica

# Replication-only account and an isolated ephemeral Docker bridge.
primary_sql "CREATE ROLE $replicauser LOGIN REPLICATION PASSWORD '$replica_password';" >/dev/null
docker exec -u postgres "$primary" sh -ceu '
  printf "\nhost replication b62_ci_replication 0.0.0.0/0 scram-sha-256\n" >> "$PGDATA/pg_hba.conf"
  pg_ctl -D "$PGDATA" reload >/dev/null
'
primary_sql "CREATE TABLE principal_epochs(
  principal_id bigint PRIMARY KEY, reject_issued_at_or_before_epoch bigint NOT NULL);" >/dev/null
primary_sql "CREATE TABLE blocked_signer_kids(
  issuer text NOT NULL, kid text NOT NULL, PRIMARY KEY(issuer,kid));" >/dev/null
primary_sql "CREATE TABLE synthetic_session_registration(
  issuer text NOT NULL, kid text NOT NULL, jti text NOT NULL,
  uid bigint NOT NULL, iat_seconds bigint NOT NULL,
  state text NOT NULL CHECK(state IN ('ACTIVE','REVOKED')),
  PRIMARY KEY(issuer,kid,jti));" >/dev/null
primary_sql "INSERT INTO synthetic_session_registration VALUES
  ('https://identity.aetheris.example.invalid','b62-standby-review',
   'synthetic-standby-jti',42,1700000000,'ACTIVE');" >/dev/null

docker run -d --name "$standby" --network "$network" \
  --entrypoint sleep postgres:16 1200 >/dev/null
docker exec -u postgres "$standby" mkdir -m 700 /tmp/b62-standby
# Password stored only inside disposable standby auto.conf, destroyed at exit.
docker exec -u postgres "$standby" pg_basebackup \
  --dbname="host=$primary port=5432 user=$replicauser password=$replica_password application_name=b62_review" \
  --pgdata=/tmp/b62-standby --wal-method=stream \
  --write-recovery-conf --checkpoint=fast --no-password
docker exec -u postgres "$standby" \
  pg_ctl -D /tmp/b62-standby \
  -o "-p 5432 -c listen_addresses=127.0.0.1" -w start >/dev/null

ready=false
for attempt in $(seq 1 50); do
  if test "$(standby_sql 'SELECT pg_is_in_recovery()' 2>/dev/null || true)" = t; then
    ready=true; break
  fi
  sleep 1
done
test "$ready" = true
test "$(standby_sql "SELECT state FROM synthetic_session_registration WHERE jti='synthetic-standby-jti'")" = ACTIVE

# This mirrors the Runtime review query (signed UID/JTI are static here,
# not cryptographically checked in this isolated streaming-WAL experiment).
unprotected_sql="SELECT current_setting('transaction_isolation')='read committed'
  AND EXISTS (SELECT 1 FROM synthetic_session_registration active
    WHERE active.issuer='https://identity.aetheris.example.invalid'
      AND active.kid='b62-standby-review'
      AND active.jti='synthetic-standby-jti' AND active.uid=42
      AND active.iat_seconds=1700000000 AND active.state='ACTIVE')
  AND NOT EXISTS (SELECT 1 FROM blocked_signer_kids
    WHERE issuer='https://identity.aetheris.example.invalid'
      AND kid='b62-standby-review')
  AND NOT EXISTS (SELECT 1 FROM principal_epochs
    WHERE principal_id=42 AND 1700000000 <= reject_issued_at_or_before_epoch)"
guarded_sql="$(printf '%s' "$unprotected_sql" | sed 's/^SELECT /SELECT NOT pg_is_in_recovery() AND /')"
test "$(primary_sql "$guarded_sql")" = t
test "$(standby_sql "$unprotected_sql")" = t
test "$(standby_sql "$guarded_sql")" = f
echo AETHERIS_B62_PHYSICAL_STREAMING_STANDBY_RECOVERY_DENIED_PASS

# Pause the physical WAL replay, then revoke session and signing key
# on the PRIMARY. Standby still sees true stale eligibility unguarded.
standby_sql 'SELECT pg_wal_replay_pause()' >/dev/null
paused=false
for attempt in $(seq 1 30); do
  if test "$(standby_sql 'SELECT pg_get_wal_replay_pause_state()')" = paused; then
    paused=true; break
  fi
  sleep 1
done
test "$paused" = true
primary_sql "UPDATE synthetic_session_registration SET state='REVOKED'
  WHERE jti='synthetic-standby-jti';
  INSERT INTO blocked_signer_kids VALUES
  ('https://identity.aetheris.example.invalid','b62-standby-review');" >/dev/null
test "$(primary_sql "SELECT state FROM synthetic_session_registration WHERE jti='synthetic-standby-jti'")" = REVOKED
test "$(standby_sql "SELECT state FROM synthetic_session_registration WHERE jti='synthetic-standby-jti'")" = ACTIVE
test "$(standby_sql "$unprotected_sql")" = t
test "$(standby_sql "$guarded_sql")" = f
test "$(primary_sql "$guarded_sql")" = f
echo AETHERIS_B62_PAUSED_STALE_STANDBY_FALSE_ALLOW_BLOCKED_PASS

standby_sql 'SELECT pg_wal_replay_resume()' >/dev/null
caught_up=false
for attempt in $(seq 1 50); do
  if test "$(standby_sql "SELECT state FROM synthetic_session_registration WHERE jti='synthetic-standby-jti'" 2>/dev/null || true)" = REVOKED &&
     test "$(standby_sql 'SELECT count(*) FROM blocked_signer_kids' 2>/dev/null || true)" = 1; then
    caught_up=true; break
  fi
  sleep 1
done
test "$caught_up" = true
test "$(standby_sql "$guarded_sql")" = f
echo AETHERIS_B62_PHYSICAL_REPLAY_CATCHUP_NO_AUTHORIZATION_PASS

# Additional failover SAFETY COUNTEREXAMPLE: recovery status alone is
# insufficient for leader selection. This deliberately creates a brief
# disposable split-brain, then fences the former primary. NEVER deploy
# this as an operator runbook or production failover controller.
primary_sql "INSERT INTO synthetic_session_registration VALUES
  ('https://identity.aetheris.example.invalid','b62-promotion-review',
   'synthetic-promotion-jti',77,1700000010,'ACTIVE');" >/dev/null
replicated=false
for attempt in $(seq 1 50); do
  if test "$(standby_sql "SELECT state FROM synthetic_session_registration WHERE jti='synthetic-promotion-jti'" 2>/dev/null || true)" = ACTIVE; then
    replicated=true; break
  fi
  sleep 1
done
test "$replicated" = true
promotion_sql="SELECT NOT pg_is_in_recovery()
  AND current_setting('transaction_isolation')='read committed'
  AND EXISTS (SELECT 1 FROM synthetic_session_registration active
    WHERE active.issuer='https://identity.aetheris.example.invalid'
      AND active.kid='b62-promotion-review'
      AND active.jti='synthetic-promotion-jti'
      AND active.uid=77 AND active.iat_seconds=1700000010
      AND active.state='ACTIVE')
  AND NOT EXISTS (SELECT 1 FROM blocked_signer_kids
    WHERE issuer='https://identity.aetheris.example.invalid'
      AND kid='b62-promotion-review')
  AND NOT EXISTS (SELECT 1 FROM principal_epochs
    WHERE principal_id=77 AND 1700000010 <= reject_issued_at_or_before_epoch)"
test "$(primary_sql "$promotion_sql")" = t
test "$(standby_sql "$promotion_sql")" = f

# INTENTIONAL SYNTHETIC SPLIT-BRAIN, RUNNER-INTERNAL ONLY.
# Promotion of a still-streaming standby must not be interpreted as
# automatically fencing or revoking access to the former leader.
docker exec -u postgres "$standby" \
  pg_ctl -D /tmp/b62-standby -w -t 30 promote >/dev/null
test "$(standby_sql 'SELECT pg_is_in_recovery()')" = f
test "$(primary_sql 'SELECT pg_is_in_recovery()')" = f
test "$(standby_sql "$promotion_sql")" = t
test "$(primary_sql "$promotion_sql")" = t

# Revoke only on the NEW promoted primary. The old primary has diverged,
# remains writable, and its primary-only predicate incorrectly ALLOWS.
standby_sql "UPDATE synthetic_session_registration SET state='REVOKED'
  WHERE jti='synthetic-promotion-jti';
  INSERT INTO blocked_signer_kids VALUES
  ('https://identity.aetheris.example.invalid','b62-promotion-review');" >/dev/null
test "$(standby_sql "SELECT state FROM synthetic_session_registration WHERE jti='synthetic-promotion-jti'")" = REVOKED
test "$(primary_sql "SELECT state FROM synthetic_session_registration WHERE jti='synthetic-promotion-jti'")" = ACTIVE
test "$(standby_sql "$promotion_sql")" = f
test "$(primary_sql "$promotion_sql")" = t
echo AETHERIS_B62_SPLIT_BRAIN_OLD_PRIMARY_STALE_ALLOW_DEMONSTRATED_NO_GO_PASS


# Independent coordinator NEGATIVE REHEARSAL (NOT production consensus).
# A third PostgreSQL process stores the review-only endpoint/term/expiry.
# The two reads (leader, then session) are NOT atomic -- TOCTOU remains.
coord_owner=b62_coord_owner
coord_db=b62_coord_disposable
coord_reader=b62_coord_reader
coord_owner_pw="$(openssl rand -hex 24)"
coord_read_pw="$(openssl rand -hex 24)"
docker run -d --name "$authority" --network "$network" \
  -e "POSTGRES_USER=$coord_owner" -e "POSTGRES_PASSWORD=$coord_owner_pw" \
  -e "POSTGRES_DB=$coord_db" \
  -e "POSTGRES_INITDB_ARGS=--auth-host=scram-sha-256" postgres:16 >/dev/null
coord_ready=false
for attempt in $(seq 1 50); do
  if test "$(docker exec "$authority" psql -X -A -t -v ON_ERROR_STOP=1 \
       -U "$coord_owner" -d "$coord_db" -c 'SELECT 1' 2>/dev/null || true)" = 1; then
    coord_ready=true; break
  fi
  sleep 1
done
test "$coord_ready" = true
coord_write() {
  docker exec "$authority" psql -X -A -t -v ON_ERROR_STOP=1 \
    -U "$coord_owner" -d "$coord_db" -c "$1"
}
coord_read() {
  local source_node="$1" sql="$2"
  docker exec -e "PGPASSWORD=$coord_read_pw" -e PGCONNECT_TIMEOUT=2 \
    "$source_node" psql -X -A -t -w -v ON_ERROR_STOP=1 \
      -h "$authority" -p 5432 -U "$coord_reader" -d "$coord_db" -c "$sql"
}
coord_write "CREATE TABLE leader_fence(
    authority_key text PRIMARY KEY, endpoint text NOT NULL,
    term bigint NOT NULL CHECK(term>0), expires_at timestamptz NOT NULL);
  INSERT INTO leader_fence VALUES
    ('runtime','$standby',2,clock_timestamp()+interval '15 minutes');
  CREATE ROLE $coord_reader LOGIN PASSWORD '$coord_read_pw';
  REVOKE ALL ON TABLE leader_fence FROM PUBLIC;
  GRANT SELECT ON TABLE leader_fence TO $coord_reader;" >/dev/null

# This is a client-side *test gate*; no application route uses it.
# Missing/invalid coordination response or wrong term => fail closed.
review_route() {
  local node="$1" generation="$2" decision_sql="$3" leader
  [[ "$generation" =~ ^[1-9][0-9]*$ ]] || return 1
  [[ "$node" == "$primary" || "$node" == "$standby" ]] || return 1
  leader="$(coord_read "$node" \
    "SELECT endpoint || ':' || term::text FROM leader_fence
     WHERE authority_key='runtime' AND NOT pg_is_in_recovery()
       AND expires_at>clock_timestamp()" 2>/dev/null)" || return 1
  [[ "$leader" == "$node:$generation" ]] || return 1
  if [[ "$node" == "$primary" ]]; then
    [[ "$(primary_sql "$decision_sql")" == t ]]
  else
    [[ "$(standby_sql "$decision_sql")" == t ]]
  fi
}
# The old primary's unchanged stale SQL is TRUE, but the third
# coordinator disqualifies its endpoint regardless of pg_is_in_recovery.
test "$(primary_sql "$promotion_sql")" = t
if review_route "$primary" 1 "$promotion_sql" ||
   review_route "$primary" 2 "$promotion_sql"; then
  echo "FAIL: old primary accepted after separate leader term changed" >&2
  exit 1
fi
if review_route "$standby" 2 "$promotion_sql"; then
  echo "FAIL: revoked session allowed on promoted primary" >&2
  exit 1
fi
# A genuinely eligible NEW synthetic session on the promoted node is
# accepted only with a fresh matching term and an unblocked signer.
standby_sql "INSERT INTO synthetic_session_registration VALUES
 ('https://identity.aetheris.example.invalid','b62-new-leader',
  'synthetic-current-session',78,1700000011,'ACTIVE');" >/dev/null
new_sql="SELECT NOT pg_is_in_recovery()
  AND current_setting('transaction_isolation')='read committed'
  AND EXISTS (SELECT 1 FROM synthetic_session_registration
    WHERE issuer='https://identity.aetheris.example.invalid'
      AND kid='b62-new-leader' AND jti='synthetic-current-session'
      AND uid=78 AND iat_seconds=1700000011 AND state='ACTIVE')
  AND NOT EXISTS (SELECT 1 FROM blocked_signer_kids
    WHERE issuer='https://identity.aetheris.example.invalid'
      AND kid='b62-new-leader')
  AND NOT EXISTS (SELECT 1 FROM principal_epochs
    WHERE principal_id=78 AND 1700000011<=reject_issued_at_or_before_epoch)"
review_route "$standby" 2 "$new_sql"
if review_route "$standby" 1 "$new_sql"; then
  echo "FAIL: old leader term accepted" >&2
  exit 1
fi
# A compromised test reader cannot mutate independent leadership.
if coord_read "$standby" "UPDATE leader_fence SET term=1" \
     >/dev/null 2>&1; then
  echo "FAIL: read-only coordinator identity has UPDATE authority" >&2
  exit 1
fi
echo AETHERIS_B62_SEPARATE_LEADER_TERM_DENIES_OLD_PRIMARY_PASS

# Expired lease and missing coordinator are denial events, not an
# excuse to fall back to the old primary or permit cached session state.
coord_write "UPDATE leader_fence SET expires_at=clock_timestamp()-interval '1 second';" >/dev/null
if review_route "$standby" 2 "$new_sql"; then
  echo "FAIL: expired leader term accepted" >&2
  exit 1
fi
coord_write "UPDATE leader_fence SET expires_at=clock_timestamp()+interval '15 minutes';" >/dev/null
review_route "$standby" 2 "$new_sql"
docker stop "$authority" >/dev/null
if review_route "$standby" 2 "$new_sql" ||
   review_route "$primary" 1 "$promotion_sql"; then
  echo "FAIL: coordinator outage fell back to permissive session" >&2
  exit 1
fi
docker start "$authority" >/dev/null
coord_ready=false
for attempt in $(seq 1 50); do
  if test "$(docker exec "$authority" psql -X -A -t -v ON_ERROR_STOP=1 \
       -U "$coord_owner" -d "$coord_db" -c 'SELECT 1' 2>/dev/null || true)" = 1; then
    coord_ready=true; break
  fi
  sleep 1
done
test "$coord_ready" = true
review_route "$standby" 2 "$new_sql"
echo AETHERIS_B62_SEPARATE_LEADER_EXPIRY_AND_OUTAGE_FAIL_CLOSED_PASS

# Synthetic protected-resource write. Independent caller roles can only
# EXECUTE this audited effect function, never mutate either fencing row.
fence_old=b62_fence_old_actor
fence_new=b62_fence_new_actor
fence_owner=b62_fence_exec_owner
fence_old_pw="$(openssl rand -hex 24)"
fence_new_pw="$(openssl rand -hex 24)"
coord_write "BEGIN;
CREATE TABLE public.b62_resource_fence(
  resource_key text PRIMARY KEY, endpoint text NOT NULL,
  term bigint NOT NULL CHECK (term>0));
INSERT INTO public.b62_resource_fence VALUES ('runtime','$standby',2);
-- FOR SHARE requires column-level UPDATE. The only grantable column
-- is constrained to zero and is NOT a term, endpoint or lease.
ALTER TABLE public.leader_fence ADD COLUMN lock_guard smallint NOT NULL
  DEFAULT 0 CHECK(lock_guard=0);
ALTER TABLE public.b62_resource_fence ADD COLUMN lock_guard smallint NOT NULL
  DEFAULT 0 CHECK(lock_guard=0);
CREATE TABLE public.b62_protected_actions(
  request_id text PRIMARY KEY, actor_login text NOT NULL, term bigint NOT NULL);
CREATE ROLE $fence_owner NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE;
CREATE ROLE $fence_old LOGIN PASSWORD '$fence_old_pw';
CREATE ROLE $fence_new LOGIN PASSWORD '$fence_new_pw';
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
REVOKE ALL ON public.b62_resource_fence, public.b62_protected_actions FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO $fence_owner, $fence_old, $fence_new;
GRANT SELECT ON public.leader_fence, public.b62_resource_fence TO $fence_owner;
GRANT UPDATE(lock_guard) ON public.leader_fence TO $fence_owner;
GRANT UPDATE(lock_guard) ON public.b62_resource_fence TO $fence_owner;
GRANT INSERT ON public.b62_protected_actions TO $fence_owner;
CREATE FUNCTION public.b62_try_protected_action(p_term bigint,p_request text)
RETURNS boolean LANGUAGE plpgsql SECURITY DEFINER
SET search_path=pg_catalog,pg_temp
AS \$b62_impl\$
DECLARE expected_endpoint text;
BEGIN
  IF current_setting('transaction_isolation') <> 'read committed'
    OR p_term IS NULL OR p_term < 1 OR p_request IS NULL
    OR p_request !~ '^[a-z][a-z0-9-]{0,47}$' THEN
    RETURN false;
  END IF;
  CASE session_user
    WHEN '$fence_old' THEN expected_endpoint := '$primary';
    WHEN '$fence_new' THEN expected_endpoint := '$standby';
    ELSE RETURN false;
  END CASE;
  -- The protected action and both checked fencing rows share a transaction.
  PERFORM 1 FROM public.leader_fence l
  JOIN public.b62_resource_fence r ON r.resource_key=l.authority_key
  WHERE l.authority_key='runtime' AND l.endpoint=expected_endpoint
    AND r.endpoint=expected_endpoint AND l.term=p_term AND r.term=p_term
    AND l.expires_at>clock_timestamp() AND NOT pg_is_in_recovery()
  FOR SHARE OF l,r;
  IF NOT FOUND THEN RETURN false; END IF;
  INSERT INTO public.b62_protected_actions(request_id,actor_login,term)
  VALUES(p_request,session_user,p_term);
  RETURN true;
END
\$b62_impl\$;
ALTER FUNCTION public.b62_try_protected_action(bigint,text) OWNER TO $fence_owner;
REVOKE ALL ON FUNCTION public.b62_try_protected_action(bigint,text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.b62_try_protected_action(bigint,text)
  TO $fence_old,$fence_new;
COMMIT;" >/dev/null
fence_as() {
  local node="$1" actor="$2" password="$3" statement="$4"
  [[ "$node" == "$primary" || "$node" == "$standby" ]] || return 1
  [[ "$actor" == "$fence_old" || "$actor" == "$fence_new" ]] || return 1
  docker exec -e "PGPASSWORD=$password" -e PGCONNECT_TIMEOUT=2 "$node" \
    psql -X -A -t -w -v ON_ERROR_STOP=1 -h "$authority" -p 5432 \
      -U "$actor" -d "$coord_db" -c "$statement"
}
test "$(fence_as "$standby" "$fence_new" "$fence_new_pw" \
  "SELECT public.b62_try_protected_action(2,'term-two-accepted')")" = t
test "$(fence_as "$primary" "$fence_old" "$fence_old_pw" \
  "SELECT public.b62_try_protected_action(1,'old-term-denied')")" = f
test "$(fence_as "$primary" "$fence_old" "$fence_old_pw" \
  "SELECT public.b62_try_protected_action(2,'wrong-endpoint-denied')")" = f
if fence_as "$standby" "$fence_new" "$fence_new_pw" \
  "INSERT INTO public.b62_protected_actions VALUES('forged','actor',2)" \
  >/dev/null 2>&1; then
  echo "FAIL: action caller has direct journal INSERT" >&2; exit 1
fi
if fence_as "$standby" "$fence_new" "$fence_new_pw" \
  "UPDATE public.b62_resource_fence SET term=1" >/dev/null 2>&1; then
  echo "FAIL: action caller can lower resource fencing term" >&2; exit 1
fi
test "$(coord_write 'SELECT count(*) FROM public.b62_protected_actions')" = 1
# The SECURITY DEFINER owner itself must not possess leadership
# mutation rights; only the synthetic coordinator/admin transitions terms.
# Assert that SET LOCAL ROLE really changed the effective principal,
# or later expected UPDATE failures could pass for the wrong reason.
test "$(coord_write "BEGIN; SET LOCAL ROLE $fence_owner;
  SELECT current_user='$fence_owner';" | tail -n 1)" = t
# SET LOCAL ROLE exercises effective function-owner privileges.
if coord_write "BEGIN; SET LOCAL ROLE $fence_owner;
  UPDATE public.leader_fence SET term=1 WHERE authority_key='runtime';" \
  >/dev/null 2>&1; then
  echo "FAIL: definer owner can change coordinator leadership term" >&2; exit 1
fi
if coord_write "BEGIN; SET LOCAL ROLE $fence_owner;
  UPDATE public.b62_resource_fence SET endpoint='$primary'
  WHERE resource_key='runtime';" >/dev/null 2>&1; then
  echo "FAIL: definer owner can change protected resource endpoint" >&2; exit 1
fi
test "$(coord_write "SELECT term FROM public.leader_fence
  WHERE authority_key='runtime'")" = 2
test "$(coord_write "SELECT endpoint || ':' || term
  FROM public.b62_resource_fence WHERE resource_key='runtime'")" = "$standby:2"
echo AETHERIS_B62_DEFINER_AUTHORITY_COLUMNS_DENIED_PASS

# Two REAL, overlapping PostgreSQL transactions on the coordinator node.
# The restricted actor calls SECURITY DEFINER under an explicit transaction
# and SLEEPS while holding the FOR SHARE leader+resource row locks.
# A separate coordinator writer tries to advance both authority rows.
# Observe PgSleep first; assert failure is a *real lock timeout*.
held_action_output="$(mktemp)"
transition_output="$(mktemp)"
(
  fence_as "$standby" "$fence_new" "$fence_new_pw" \
    "BEGIN;
     SELECT public.b62_try_protected_action(2,'inflight-term-two');
     SELECT pg_sleep(12);
     COMMIT;"
) >"$held_action_output" 2>&1 &
held_pid=$!
held_ready=false
for poll in $(seq 1 80); do
  if test "$(coord_write "SELECT count(*) FROM pg_stat_activity
        WHERE usename='$fence_new' AND state='active'
          AND wait_event='PgSleep'
          AND query LIKE '%inflight-term-two%'")" = 1; then
    held_ready=true
    break
  fi
  if ! kill -0 "$held_pid" 2>/dev/null; then break; fi
  sleep 0.1
done
if test "$held_ready" != true; then
  echo "FAIL: in-flight effect did not reach observed transaction hold" >&2
  exit 1
fi
if coord_write "BEGIN; SET LOCAL lock_timeout='400ms';
  UPDATE public.leader_fence SET term=3
    WHERE authority_key='runtime' AND term=2;
  COMMIT;" > /dev/null 2>"$transition_output"; then
  echo "FAIL: coordinator advanced leadership during protected effect" >&2
  exit 1
fi
if ! grep -Fq 'canceling statement due to lock timeout' \
    "$transition_output"; then
  echo "FAIL: coordinator UPDATE failed for reason other than row lock" >&2
  exit 1
fi
if coord_write "BEGIN; SET LOCAL lock_timeout='400ms';
  UPDATE public.b62_resource_fence SET term=3
    WHERE resource_key='runtime' AND term=2;
  COMMIT;" > /dev/null 2>"$transition_output"; then
  echo "FAIL: resource fence advanced during protected effect" >&2
  exit 1
fi
if ! grep -Fq 'canceling statement due to lock timeout' \
    "$transition_output"; then
  echo "FAIL: resource UPDATE failed for reason other than row lock" >&2
  exit 1
fi
wait "$held_pid"
test "$(coord_write "SELECT actor_login || ':' || term::text
  FROM public.b62_protected_actions
  WHERE request_id='inflight-term-two'")" = "$fence_new:2"
test "$(coord_write 'SELECT count(*) FROM public.b62_protected_actions')" = 2
test "$(coord_write "SELECT term FROM public.leader_fence
  WHERE authority_key='runtime'")" = 2
test "$(coord_write "SELECT term FROM public.b62_resource_fence
  WHERE resource_key='runtime'")" = 2
echo AETHERIS_B62_INFLIGHT_ROW_LOCK_BLOCKS_LEADERSHIP_ADVANCE_PASS

# Deliberate negative-only TOCTOU counterexample, NOT a safe application gate:
# capture an eligible coordinator decision, change leadership, then show
# the previously eligible database still returns ALLOW. A fresh review_route
# call must reject the obsolete leader term. Never emit protected data after
# the cached decision; the old ALLOW is a demonstrated unsafe intermediate.
cached_leader="$(coord_read "$standby" \
  "SELECT endpoint || ':' || term::text FROM leader_fence
   WHERE authority_key='runtime' AND NOT pg_is_in_recovery()
     AND expires_at>clock_timestamp()")"
test "$cached_leader" = "$standby:2"
coord_write "UPDATE leader_fence SET endpoint='$primary', term=3,
  expires_at=clock_timestamp()+interval '15 minutes'
  WHERE authority_key='runtime' AND term=2;" >/dev/null
test "$(coord_write "SELECT term FROM leader_fence WHERE authority_key='runtime'")" = 3
echo AETHERIS_B62_POST_INFLIGHT_LEADERSHIP_TRANSITION_PASS
# This is the UNSAFE result a caller could reuse between two sequential reads.
test "$(standby_sql "$new_sql")" = t
if review_route "$standby" 2 "$new_sql"; then
  echo "FAIL: cached coordinator term admitted after leadership change" >&2
  exit 1
fi
echo AETHERIS_B62_COORDINATOR_TOCTOU_STALE_LOCAL_ALLOW_NO_GO_PASS

# Coordinator alone now names the stale former primary with term 3,
# but the protected resource remains fenced to the promoted node's term 2.
test "$(fence_as "$standby" "$fence_new" "$fence_new_pw" \
  "SELECT public.b62_try_protected_action(2,'stale-after-switch')")" = f
test "$(fence_as "$primary" "$fence_old" "$fence_old_pw" \
  "SELECT public.b62_try_protected_action(3,'old-primary-term-three')")" = f
test "$(coord_write 'SELECT count(*) FROM public.b62_protected_actions')" = 2
# Only the synthetic test owner can atomically advance both rows.
# No real failover controller, runtime route or customer data is involved.
coord_write "BEGIN;
UPDATE public.leader_fence SET endpoint='$standby',term=4,
  expires_at=clock_timestamp()+interval '15 minutes'
  WHERE authority_key='runtime' AND term=3;
UPDATE public.b62_resource_fence SET endpoint='$standby',term=4
  WHERE resource_key='runtime' AND term=2;
COMMIT;" >/dev/null
test "$(fence_as "$standby" "$fence_new" "$fence_new_pw" \
  "SELECT public.b62_try_protected_action(4,'term-four-accepted')")" = t
test "$(fence_as "$standby" "$fence_new" "$fence_new_pw" \
  "SELECT public.b62_try_protected_action(2,'replayed-term-two')")" = f
test "$(fence_as "$primary" "$fence_old" "$fence_old_pw" \
  "SELECT public.b62_try_protected_action(4,'old-primary-term-four')")" = f
if fence_as "$standby" "$fence_new" "$fence_new_pw" \
  "SELECT public.b62_try_protected_action(4,'term-four-accepted')" \
  >/dev/null 2>&1; then
  echo "FAIL: resource accepted a duplicate action request" >&2; exit 1
fi
test "$(coord_write 'SELECT count(*) FROM public.b62_protected_actions')" = 3
echo AETHERIS_B62_RESOURCE_OWNED_MONOTONIC_FENCE_ATOMIC_ACTION_PASS

# Separate physical PostgreSQL resource nodes, NOT the coordinator database.
# This negative test proves precisely where the co-located transaction
# cannot provide end-to-end fencing. Both servers remain disposable.
local_writer=b62_local_writer
local_owner=b62_local_exec_owner
local_old_pw="$(openssl rand -hex 24)"
local_new_pw="$(openssl rand -hex 24)"
local_setup() {
  local node="$1" pinned="$2" password="$3" epoch="$4" sql
  [[ "$node" == "$primary" || "$node" == "$standby" ]] || return 1
  [[ "$pinned" == "$primary" || "$pinned" == "$standby" ]] || return 1
  [[ "$epoch" =~ ^[1-9][0-9]*$ ]] || return 1
  sql="BEGIN;
    CREATE TABLE public.b62_local_fence(
      resource_key text PRIMARY KEY, endpoint text NOT NULL,
      term bigint NOT NULL CHECK(term>0));
    INSERT INTO public.b62_local_fence VALUES ('runtime','$pinned',$epoch);
    ALTER TABLE public.b62_local_fence ADD COLUMN lock_guard smallint NOT NULL
      DEFAULT 0 CHECK(lock_guard=0);
    CREATE TABLE public.b62_local_effects(
      request_id text PRIMARY KEY, term bigint NOT NULL CHECK(term>0));
    -- Actual enforcement lives inside the resource-side SQL function,
    -- NOT in an optional API/shell preflight. OPEN is unsafe while a
    -- different coordinator can advance independently; CLOSE is local.
    CREATE TABLE public.b62_writer_gate(
      resource_key text PRIMARY KEY,
      state text NOT NULL CHECK(state IN ('OPEN','CLOSED')),
      lock_guard smallint NOT NULL DEFAULT 0 CHECK(lock_guard=0));
    INSERT INTO public.b62_writer_gate(resource_key,state)
      VALUES('runtime','OPEN');
    -- Review-only local terminal-state guard. Ordinary administrator DML
    -- cannot reopen, delete or truncate the CLOSED gate. A PostgreSQL
    -- superuser/schema owner can still disable triggers or alter schema:
    -- this is NOT independently authenticated infrastructure fencing.
    CREATE FUNCTION public.b62_gate_transition_guard()
    RETURNS trigger LANGUAGE plpgsql
    SET search_path=pg_catalog,pg_temp
    AS \$b62_gate\$
    BEGIN
      IF TG_OP='DELETE' THEN
        RAISE EXCEPTION 'B62_GATE_DELETE_FORBIDDEN'
          USING ERRCODE='23514';
      END IF;
      IF NEW.resource_key IS DISTINCT FROM OLD.resource_key THEN
        RAISE EXCEPTION 'B62_GATE_RESOURCE_REKEY_FORBIDDEN'
          USING ERRCODE='23514';
      END IF;
      IF OLD.state='CLOSED' AND NEW.state<>'CLOSED' THEN
        RAISE EXCEPTION 'B62_GATE_REOPEN_FORBIDDEN'
          USING ERRCODE='23514';
      END IF;
      RETURN NEW;
    END
    \$b62_gate\$;
    CREATE TRIGGER b62_gate_no_reopen_delete
      BEFORE UPDATE OR DELETE ON public.b62_writer_gate
      FOR EACH ROW EXECUTE FUNCTION public.b62_gate_transition_guard();
    CREATE FUNCTION public.b62_gate_truncate_guard()
    RETURNS trigger LANGUAGE plpgsql
    SET search_path=pg_catalog,pg_temp
    AS \$b62_gate_truncate\$
    BEGIN
      RAISE EXCEPTION 'B62_GATE_TRUNCATE_FORBIDDEN'
        USING ERRCODE='23514';
    END
    \$b62_gate_truncate\$;
    CREATE TRIGGER b62_gate_no_truncate
      BEFORE TRUNCATE ON public.b62_writer_gate
      FOR EACH STATEMENT EXECUTE FUNCTION public.b62_gate_truncate_guard();
    REVOKE ALL ON FUNCTION public.b62_gate_transition_guard() FROM PUBLIC;
    REVOKE ALL ON FUNCTION public.b62_gate_truncate_guard() FROM PUBLIC;
    -- A closure receipt is inserted by a resource-local trigger in the
    -- SAME transaction as OPEN->CLOSED. The UUID is a correlation value,
    -- not a signature, workload authentication, or globally trusted ACK.
    CREATE TABLE public.b62_closure_receipts(
      resource_key text PRIMARY KEY,
      node_identity text NOT NULL,
      fence_term bigint NOT NULL CHECK(fence_term>0),
      receipt_nonce uuid NOT NULL UNIQUE,
      recorded_at timestamptz NOT NULL);
    REVOKE ALL ON public.b62_closure_receipts FROM PUBLIC;
    CREATE FUNCTION public.b62_record_closure_receipt()
    RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER
    SET search_path=pg_catalog,pg_temp
    AS \$b62_receipt\$
    DECLARE observed_node text; observed_term bigint;
    BEGIN
      IF TG_OP<>'UPDATE' OR OLD.state<>'OPEN'
         OR NEW.state<>'CLOSED' OR pg_is_in_recovery() THEN
        RAISE EXCEPTION 'B62_RECEIPT_NOT_CLOSED_PRIMARY'
          USING ERRCODE='23514';
      END IF;
      SELECT f.endpoint,f.term INTO observed_node,observed_term
        FROM public.b62_local_fence f
        WHERE f.resource_key=NEW.resource_key
        FOR SHARE OF f;
      IF NOT FOUND THEN
        RAISE EXCEPTION 'B62_RECEIPT_MISSING_LOCAL_FENCE'
          USING ERRCODE='23514';
      END IF;
      INSERT INTO public.b62_closure_receipts(
        resource_key,node_identity,fence_term,receipt_nonce,recorded_at)
        VALUES(NEW.resource_key,observed_node,observed_term,
          pg_catalog.gen_random_uuid(),clock_timestamp());
      RETURN NEW;
    END
    \$b62_receipt\$;
    CREATE TRIGGER b62_receipt_on_closed
      AFTER UPDATE OF state ON public.b62_writer_gate
      FOR EACH ROW WHEN (OLD.state IS DISTINCT FROM NEW.state)
      EXECUTE FUNCTION public.b62_record_closure_receipt();
    REVOKE ALL ON FUNCTION public.b62_record_closure_receipt() FROM PUBLIC;
    CREATE ROLE $local_owner NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE;
    CREATE ROLE $local_writer LOGIN PASSWORD '$password';
    REVOKE ALL ON public.b62_local_fence,public.b62_local_effects,
      public.b62_writer_gate FROM PUBLIC;
    GRANT USAGE ON SCHEMA public TO $local_owner,$local_writer;
    GRANT SELECT ON public.b62_local_fence TO $local_owner;
    GRANT UPDATE(lock_guard) ON public.b62_local_fence TO $local_owner;
    GRANT SELECT ON public.b62_writer_gate TO $local_owner;
    GRANT UPDATE(lock_guard) ON public.b62_writer_gate TO $local_owner;
    GRANT INSERT ON public.b62_local_effects TO $local_owner;
    CREATE FUNCTION public.b62_local_try_effect(p_term bigint,p_id text)
    RETURNS boolean LANGUAGE plpgsql SECURITY DEFINER
    SET search_path=pg_catalog,pg_temp
    AS \$b62_local\$
    BEGIN
      IF session_user <> '$local_writer'
         OR current_setting('transaction_isolation') <> 'read committed'
         OR p_term IS NULL OR p_term<1
         OR p_id IS NULL OR p_id !~ '^[a-z][a-z0-9-]{0,47}$' THEN
        RETURN false;
      END IF;
      -- Lock the LOCAL write gate and local term in the same transaction
      -- as the effect, so an administrator's CLOSE cannot overtake
      -- an in-flight resource-local operation. This DOES NOT lock any
      -- remote coordinator row or other writable physical primary.
      PERFORM 1 FROM public.b62_local_fence f
      JOIN public.b62_writer_gate g ON g.resource_key=f.resource_key
      WHERE f.resource_key='runtime' AND f.endpoint='$pinned'
        AND f.term=p_term AND g.state='OPEN'
        AND NOT pg_is_in_recovery()
      FOR SHARE OF f,g;
      IF NOT FOUND THEN RETURN false; END IF;
      INSERT INTO public.b62_local_effects VALUES(p_id,p_term);
      RETURN true;
    END
    \$b62_local\$;
    ALTER FUNCTION public.b62_local_try_effect(bigint,text) OWNER TO $local_owner;
    REVOKE ALL ON FUNCTION public.b62_local_try_effect(bigint,text) FROM PUBLIC;
    GRANT EXECUTE ON FUNCTION public.b62_local_try_effect(bigint,text) TO $local_writer;
    COMMIT;"
  if [[ "$node" == "$primary" ]]; then
    primary_sql "$sql" >/dev/null
  else
    standby_sql "$sql" >/dev/null
  fi
}
local_call() {
  local node="$1" password="$2" statement="$3"
  [[ "$node" == "$primary" || "$node" == "$standby" ]] || return 1
  docker exec -e "PGPASSWORD=$password" -e PGCONNECT_TIMEOUT=2 "$node" \
    psql -X -A -t -w -v ON_ERROR_STOP=1 -h 127.0.0.1 -p 5432 \
      -U "$local_writer" -d "$db" -c "$statement"
}
# These physical servers have diverged after actual pg_ctl promotion.
# No cross-server transaction or coordinator admission exists here.
local_setup "$primary" "$primary" "$local_old_pw" 2
local_setup "$standby" "$standby" "$local_new_pw" 4
test "$(primary_sql 'SELECT pg_is_in_recovery()')" = f
test "$(standby_sql 'SELECT pg_is_in_recovery()')" = f

# UNSAFE counterexample: while the coordinator is at promoted leader term 4,
# the old physical PRIMARY can still append using an obsolete LOCAL term 2.
test "$(coord_write "SELECT endpoint || ':' || term FROM leader_fence
  WHERE authority_key='runtime'")" = "$standby:4"
test "$(local_call "$primary" "$local_old_pw" \
  "SELECT public.b62_local_try_effect(2,'stale-old-local-allow')")" = t
test "$(local_call "$standby" "$local_new_pw" \
  "SELECT public.b62_local_try_effect(4,'new-local-allow')")" = t
test "$(primary_sql 'SELECT count(*) FROM public.b62_local_effects')" = 1
test "$(standby_sql 'SELECT count(*) FROM public.b62_local_effects')" = 1
# Independently verify the effective role switches on BOTH writable
# physical databases before expecting protected-column UPDATE failure.
test "$(primary_sql "BEGIN; SET LOCAL ROLE $local_owner;
  SELECT current_user='$local_owner';" | tail -n 1)" = t
test "$(standby_sql "BEGIN; SET LOCAL ROLE $local_owner;
  SELECT current_user='$local_owner';" | tail -n 1)" = t
# Independently verify definer identities cannot mint/swap authority fields
# on EITHER writable primary even though FOR SHARE remains permitted.
if primary_sql "BEGIN; SET LOCAL ROLE $local_owner;
  UPDATE public.b62_local_fence SET term=5 WHERE resource_key='runtime';" \
  >/dev/null 2>&1; then
  echo "FAIL: old resource function owner can mint leadership term" >&2; exit 1
fi
if standby_sql "BEGIN; SET LOCAL ROLE $local_owner;
  UPDATE public.b62_local_fence SET endpoint='$primary'
  WHERE resource_key='runtime';" >/dev/null 2>&1; then
  echo "FAIL: promoted resource function owner can swap endpoint" >&2; exit 1
fi
test "$(primary_sql "SELECT term FROM public.b62_local_fence
  WHERE resource_key='runtime'")" = 2
test "$(standby_sql "SELECT term FROM public.b62_local_fence
  WHERE resource_key='runtime'")" = 4
echo AETHERIS_B62_PHYSICAL_LOCAL_DEFINER_TERM_ENDPOINT_DENIED_PASS
echo AETHERIS_B62_SEPARATE_DATABASE_STALE_OLD_RESOURCE_ALLOW_NO_GO_PASS

# Resource-side SQL privileges, role credentials, wrong terms and replay
# are independently denied on BOTH PostgreSQL processes.
if local_call "$standby" "$local_old_pw" \
  "SELECT public.b62_local_try_effect(4,'wrong-resource-password')" \
  >/dev/null 2>&1; then
  echo "FAIL: old resource password authenticated to promoted node" >&2
  exit 1
fi
if local_call "$primary" "$local_old_pw" \
  "UPDATE public.b62_local_fence SET term=1" >/dev/null 2>&1; then
  echo "FAIL: restricted old writer can mutate its local fence" >&2
  exit 1
fi
if local_call "$standby" "$local_new_pw" \
  "INSERT INTO public.b62_local_effects VALUES ('forged-direct',4)" \
  >/dev/null 2>&1; then
  echo "FAIL: restricted promoted writer can bypass its journal function" >&2
  exit 1
fi
test "$(local_call "$primary" "$local_old_pw" \
  "SELECT public.b62_local_try_effect(4,'wrong-old-term')")" = f
test "$(local_call "$standby" "$local_new_pw" \
  "SELECT public.b62_local_try_effect(2,'wrong-new-term')")" = f

# Combined nonproduction B62 replay review: coordinator is still at term 4.
source scripts/security/runtime_b62_pg16_revocation_continuity_review.sh
source scripts/security/runtime_b62_pg16_provenance_authenticity_review.sh
source scripts/security/runtime_b62_pg16_inflight_commit_revocation_review.sh
source scripts/security/runtime_b62_pg16_reachable_prepublication_barrier_review.sh
source scripts/security/runtime_b62_pg16_source_crash_recovery_review.sh
source scripts/security/runtime_b62_pg16_prebarrier_coordinator_loss_review.sh
source scripts/security/runtime_b62_pg16_local_permit_expiry_review.sh
source scripts/security/runtime_b62_pg16_deferred_commit_guard_review.sh

# DISPOSABLE NEGATIVE COUNTEREXAMPLE: a protected effect on an
# INDEPENDENT promoted resource node remains in-flight while the separate
# coordinator advances its term. Same-node FOR SHARE locks cannot serialize
# with a leadership update in a different PostgreSQL server.
detached_action_output="$(mktemp)"
(
  local_call "$standby" "$local_new_pw" \
    "BEGIN;
     SELECT public.b62_local_try_effect(4,'detached-inflight-term-four');
     SELECT pg_sleep(12);
     COMMIT;"
) >"$detached_action_output" 2>&1 &
detached_pid=$!
detached_ready=false
for poll in $(seq 1 80); do
  if test "$(standby_sql "SELECT count(*) FROM pg_stat_activity
      WHERE usename='$local_writer' AND datname='$db'
        AND state='active' AND wait_event='PgSleep'
        AND query LIKE '%detached-inflight-term-four%'")" = 1; then
    detached_ready=true
    break
  fi
  if ! kill -0 "$detached_pid" 2>/dev/null; then break; fi
  sleep 0.1
done
if test "$detached_ready" != true; then
  echo "FAIL: detached resource effect was not observed in-flight" >&2
  exit 1
fi
test "$(coord_write "SELECT endpoint || ':' || term::text FROM leader_fence
  WHERE authority_key='runtime'")" = "$standby:4"
# lock_timeout ensures the independent coordinator update cannot
# silently block on the separate resource transaction.
coord_write "BEGIN; SET LOCAL lock_timeout='400ms';
  UPDATE public.leader_fence SET term=5,
    expires_at=clock_timestamp()+interval '15 minutes'
  WHERE authority_key='runtime' AND term=4;
  COMMIT;" >/dev/null
test "$(coord_write "SELECT endpoint || ':' || term::text FROM leader_fence
  WHERE authority_key='runtime'")" = "$standby:5"
test "$(standby_sql "SELECT endpoint || ':' || term::text
  FROM public.b62_local_fence WHERE resource_key='runtime'")" = "$standby:4"
if review_route "$standby" 4 "$new_sql"; then
  echo "FAIL: coordinator still admitted obsolete term four" >&2
  exit 1
fi
# The actual resource transaction nonetheless commits under its stale
# local term; this is a SECURITY FAILURE demonstrator, NEVER a release PASS.
wait "$detached_pid"
grep -Fxq t "$detached_action_output"
test "$(standby_sql "SELECT term FROM public.b62_local_effects
  WHERE request_id='detached-inflight-term-four'")" = 4
test "$(standby_sql 'SELECT count(*) FROM public.b62_local_effects')" = 2
echo AETHERIS_B62_DETACHED_INFLIGHT_STALE_EFFECT_COMMITS_NO_GO_PASS

# Explicit operator-driven synthetic FENCING of the old database:
# this happens AFTER a demonstrated unsafe window and cannot be treated
# as atomic coordinator+resource failover or any safe rollout protocol.
primary_sql "UPDATE public.b62_local_fence
  SET endpoint='$standby',term=4
  WHERE resource_key='runtime' AND term=2;" >/dev/null
test "$(primary_sql "SELECT endpoint || ':' || term FROM public.b62_local_fence
  WHERE resource_key='runtime'")" = "$standby:4"
test "$(local_call "$primary" "$local_old_pw" \
  "SELECT public.b62_local_try_effect(2,'old-fenced-term-two')")" = f
test "$(local_call "$primary" "$local_old_pw" \
  "SELECT public.b62_local_try_effect(4,'old-fenced-term-four')")" = f
if local_call "$standby" "$local_new_pw" \
  "SELECT public.b62_local_try_effect(4,'new-local-allow')" \
  >/dev/null 2>&1; then
  echo "FAIL: replayed new-resource request was accepted" >&2
  exit 1
fi
test "$(standby_sql 'SELECT count(*) FROM public.b62_local_effects')" = 2

# Local fencing survives a real network disconnect on the OLD server.
# docker exec loopback is deliberately a negative-only in-container probe;
# no external callers are enabled and network reachability is NOT proven.
docker network disconnect "$network" "$primary"
test "$(local_call "$primary" "$local_old_pw" \
  "SELECT public.b62_local_try_effect(2,'isolated-stale-term')")" = f
test "$(primary_sql 'SELECT count(*) FROM public.b62_local_effects')" = 1
echo AETHERIS_B62_SEPARATE_DATABASE_OLD_RESOURCE_FENCED_DURING_PARTITION_PASS

# Candidate, TEST-ONLY cutover gate. This shell gate is not a resource
# security boundary: direct SQL still bypasses it, Docker state is not
# authenticated infrastructure fencing evidence, and checks are non-atomic.
# A successful rehearsal MUST NOT activate any protected Runtime API.
review_cutover_effect() {
  local request="$1" leader local_term old_running
  [[ "$request" =~ ^[a-z][a-z0-9-]{0,47}$ ]] || return 1
  # Connection error or missing container must DENY, not imply fencing.
  old_running="$(docker inspect -f '{{.State.Running}}' "$primary" \
    2>/dev/null)" || return 1
  [[ "$old_running" == false ]] || return 1
  leader="$(coord_read "$standby" "SELECT endpoint || ':' || term::text
    FROM leader_fence WHERE authority_key='runtime'
      AND NOT pg_is_in_recovery()
      AND expires_at>clock_timestamp()" 2>/dev/null)" || return 1
  [[ "$leader" == "$standby:5" ]] || return 1
  local_term="$(standby_sql "SELECT endpoint || ':' || term::text
    FROM public.b62_local_fence WHERE resource_key='runtime'
      AND NOT pg_is_in_recovery()" 2>/dev/null)" || return 1
  [[ "$local_term" == "$standby:5" ]] || return 1
  [[ "$(standby_sql 'SELECT pg_is_in_recovery()' 2>/dev/null)" == f ]] || return 1
  # Controlled fixture hook ONLY: pause after all non-atomic checks, before
  # the real resource-side SQL write, so the coordinator may change first.
  # Not an authentication or production fencing mechanism.
  if [[ "$request" == 'race-after-gate-checks' ]]; then
    [[ -n "$cutover_race_dir" && -d "$cutover_race_dir" ]] || return 1
    printf 'checked\n' > "$cutover_race_dir/checked"
    local released=false
    for poll in $(seq 1 100); do
      if [[ -f "$cutover_race_dir/release" ]]; then
        released=true; break
      fi
      sleep 0.1
    done
    [[ "$released" == true ]] || return 1
  fi
  [[ "$(local_call "$standby" "$local_new_pw" \
    "SELECT public.b62_local_try_effect(5,'$request')" 2>/dev/null)" == t ]]
}
# Old node remains RUNNING even when its bridge is disconnected;
# unreachable is deliberately not accepted as fencing acknowledgment.
if review_cutover_effect 'premature-isolated-cutover'; then
  echo "FAIL: cutover admitted while old isolated writer is alive" >&2
  exit 1
fi
test "$(primary_sql 'SELECT pg_is_in_recovery()')" = f
test "$(standby_sql 'SELECT count(*) FROM public.b62_local_effects')" = 2
echo AETHERIS_B62_CUTOVER_OLD_ALIVE_FAIL_CLOSED_PASS

# A local term alone does NOT detect coordinator outage: the promoted
# database still accepts a locally matched synthetic term while its
# authoritative coordinator is unreachable. This is a deliberate
# UNRELEASEABLE fail-open counterexample, not acceptable Runtime behavior.
docker stop "$authority" >/dev/null
test "$(local_call "$standby" "$local_new_pw" \
  "SELECT public.b62_local_try_effect(4,'authority-outage-local-allow')")" = t
test "$(standby_sql 'SELECT count(*) FROM public.b62_local_effects')" = 3
echo AETHERIS_B62_LOCAL_ONLY_FENCE_COORDINATOR_OUTAGE_ALLOW_NO_GO_PASS
if review_cutover_effect 'coordinator-down-cutover'; then
  echo "FAIL: cutover admitted with coordinator unavailable" >&2
  exit 1
fi
echo AETHERIS_B62_CUTOVER_COORDINATOR_OUTAGE_FAIL_CLOSED_PASS

# EXTERNAL FENCING EXAMPLE ONLY: stop the old server process/container.
# In real systems, fencing/leases/route control must be independently
# designed, cryptographically authenticated and operationally tested.
docker stop "$primary" >/dev/null
test -z "$(docker ps --filter "name=^/$primary$" --format '{{.Names}}')"
test "$(standby_sql 'SELECT pg_is_in_recovery()')" = f
test "$(standby_sql "$promotion_sql")" = f
echo AETHERIS_B62_DISPOSABLE_OLD_PRIMARY_FENCED_AFTER_PROMOTION_PASS
# Old process is stopped, but coordinator still unavailable. A copied
# "fenced" assertion without a live coordinator cannot grant anything.
if review_cutover_effect 'coordinator-still-down'; then
  echo "FAIL: old shutdown incorrectly bypassed coordinator outage" >&2
  exit 1
fi
# Recover coordinator in this disposable lab; never invent a trusted ACK.
docker start "$authority" >/dev/null
coord_ready=false
for attempt in $(seq 1 50); do
  if test "$(coord_write 'SELECT 1' 2>/dev/null || true)" = 1; then
    coord_ready=true; break
  fi
  sleep 1
done
test "$coord_ready" = true
test "$(coord_write "SELECT endpoint || ':' || term::text
  FROM leader_fence WHERE authority_key='runtime'")" = "$standby:5"
# Still deny: coordinator term 5, new resource still only term 4.
if review_cutover_effect 'before-new-fence-term'; then
  echo "FAIL: cutover admitted stale new-resource term" >&2
  exit 1
fi
echo AETHERIS_B62_CUTOVER_STALE_NEW_TERM_FAIL_CLOSED_PASS
# Only the synthetic disposable operator advances the resource term
# after confirming old process shutdown. This is NOT an atomic cutover.
standby_sql "UPDATE public.b62_local_fence SET term=5
  WHERE resource_key='runtime' AND endpoint='$standby' AND term=4;" >/dev/null
test "$(standby_sql "SELECT endpoint || ':' || term::text
  FROM public.b62_local_fence WHERE resource_key='runtime'")" = "$standby:5"
review_cutover_effect 'rehearsal-post-fence-action'
test "$(standby_sql "SELECT term FROM public.b62_local_effects
  WHERE request_id='rehearsal-post-fence-action'")" = 5
test "$(standby_sql 'SELECT count(*) FROM public.b62_local_effects')" = 4
echo AETHERIS_B62_CUTOVER_ORDERED_OPERATOR_REHEARSAL_PASS
# Negative, deterministic check-then-act experiment on the shell gate:
# complete its admission checks, advance the INDEPENDENT coordinator
# from term 5 to 6, then release the paused resource write. A term-5
# effect is still committed. This is a release-blocking TOCTOU failure.
cutover_race_dir="$(mktemp -d)"
(
  review_cutover_effect 'race-after-gate-checks'
) >"$cutover_race_dir/gate.log" 2>&1 &
race_pid=$!
race_ready=false
for poll in $(seq 1 100); do
  if [[ -s "$cutover_race_dir/checked" ]]; then
    race_ready=true; break
  fi
  if ! kill -0 "$race_pid" 2>/dev/null; then break; fi
  sleep 0.1
done
if [[ "$race_ready" != true ]]; then
  echo "FAIL: cutover gate never reached the observed pre-write barrier" >&2
  exit 1
fi
test "$(coord_write "SELECT endpoint || ':' || term::text FROM leader_fence
  WHERE authority_key='runtime'")" = "$standby:5"
coord_write "BEGIN; SET LOCAL lock_timeout='400ms';
  UPDATE public.leader_fence SET term=6,
    expires_at=clock_timestamp()+interval '15 minutes'
    WHERE authority_key='runtime' AND endpoint='$standby' AND term=5;
  COMMIT;" >/dev/null
test "$(coord_write "SELECT endpoint || ':' || term::text FROM leader_fence
  WHERE authority_key='runtime'")" = "$standby:6"
test "$(standby_sql "SELECT endpoint || ':' || term::text
  FROM public.b62_local_fence WHERE resource_key='runtime'")" = "$standby:5"
touch "$cutover_race_dir/release"
wait "$race_pid"
test "$(standby_sql "SELECT term FROM public.b62_local_effects
  WHERE request_id='race-after-gate-checks'")" = 5
test "$(standby_sql 'SELECT count(*) FROM public.b62_local_effects')" = 5
if review_cutover_effect 'race-fresh-recheck-must-deny'; then
  echo "FAIL: a fresh cutover probe admitted obsolete coordinator term" >&2
  exit 1
fi
echo AETHERIS_B62_CUTOVER_GATE_CHECK_THEN_WRITE_RACE_NO_GO_PASS
# A recovered-but-then-lost coordinator cannot admit a second action.
docker stop "$authority" >/dev/null
if review_cutover_effect 'rehearsal-coordinator-lost-again'; then
  echo "FAIL: cutover retried despite coordinator loss" >&2
  exit 1
fi
test "$(standby_sql 'SELECT count(*) FROM public.b62_local_effects')" = 5
echo AETHERIS_B62_CUTOVER_REPEATED_OUTAGE_FAIL_CLOSED_PASS
# Even after successful ordered cutover, the restricted resource actor
# can BYPASS the shell gate through the separately hosted SQL function.
# Coordinator is DOWN and old credentials are already fenced, yet an
# independent local-only term-5 operation still succeeds: NOT safe.
test "$(local_call "$standby" "$local_new_pw" \
  "SELECT public.b62_local_try_effect(5,'post-cutover-direct-sql-bypass')")" = t
test "$(standby_sql "SELECT term FROM public.b62_local_effects
  WHERE request_id='post-cutover-direct-sql-bypass'")" = 5
test "$(standby_sql 'SELECT count(*) FROM public.b62_local_effects')" = 6
echo AETHERIS_B62_CUTOVER_DIRECT_SQL_BYPASS_OUTAGE_NO_GO_PASS


# Resource-owned LOCAL closure experiment. The earlier fail-open cases
# remain explicit above, where the gate was OPEN. No upstream coordinator
# can force CLOSED across partitions; the operator here acts on the
# independently writable promoted resource via its private SQL admin.
if local_call "$standby" "$local_new_pw" \
  "UPDATE public.b62_writer_gate SET state='CLOSED'
   WHERE resource_key='runtime'" >/dev/null 2>&1; then
  echo "FAIL: restricted effect login can close/reopen its own gate" >&2
  exit 1
fi
if standby_sql "BEGIN; SET LOCAL ROLE $local_owner;
  UPDATE public.b62_writer_gate SET state='CLOSED'
  WHERE resource_key='runtime';" >/dev/null 2>&1; then
  echo "FAIL: SECURITY DEFINER owner can change admission state" >&2
  exit 1
fi
test "$(standby_sql "SELECT state FROM public.b62_writer_gate
  WHERE resource_key='runtime'")" = OPEN
# Real in-flight write must retain the gate-row FOR SHARE lock until
# COMMIT. A competing operator CLOSE must *specifically* time out.
writer_drain_output="$(mktemp)"
writer_drain_error="$(mktemp)"
(
  local_call "$standby" "$local_new_pw" \
    "BEGIN;
     SELECT public.b62_local_try_effect(5,'drain-before-close');
     SELECT pg_sleep(12);
     COMMIT;"
) >"$writer_drain_output" 2>&1 &
drain_pid=$!
drain_ready=false
for poll in $(seq 1 80); do
  if test "$(standby_sql "SELECT count(*) FROM pg_stat_activity
      WHERE usename='$local_writer' AND datname='$db'
        AND state='active' AND wait_event='PgSleep'
        AND query LIKE '%drain-before-close%'")" = 1; then
    drain_ready=true
    break
  fi
  if ! kill -0 "$drain_pid" 2>/dev/null; then break; fi
  sleep 0.1
done
if test "$drain_ready" != true; then
  echo "FAIL: local writer did not reach observed drain hold" >&2
  exit 1
fi
if standby_sql "BEGIN; SET LOCAL lock_timeout='400ms';
  UPDATE public.b62_writer_gate SET state='CLOSED'
    WHERE resource_key='runtime' AND state='OPEN';
  COMMIT;" > /dev/null 2>"$writer_drain_error"; then
  echo "FAIL: local gate CLOSED while active effect held lock" >&2
  exit 1
fi
if ! grep -Fq 'canceling statement due to lock timeout' \
    "$writer_drain_error"; then
  echo "FAIL: gate CLOSE failed for unrelated reason" >&2
  exit 1
fi
wait "$drain_pid"
grep -Fxq t "$writer_drain_output"
test "$(standby_sql "SELECT term FROM public.b62_local_effects
  WHERE request_id='drain-before-close'")" = 5
test "$(standby_sql 'SELECT count(*) FROM public.b62_local_effects')" = 7
echo AETHERIS_B62_RESOURCE_GATE_DRAIN_LOCK_SERIALIZES_PASS
# No receipt is generated by the failed, lock-timed-out CLOSE.
test "$(standby_sql 'SELECT count(*) FROM public.b62_closure_receipts')" = 0
# Deliberately ROLLBACK a permitted close: neither gate nor receipt
# may persist from an uncommitted update.
standby_sql "BEGIN;
  UPDATE public.b62_writer_gate SET state='CLOSED'
    WHERE resource_key='runtime' AND state='OPEN';
  ROLLBACK;" >/dev/null
test "$(standby_sql "SELECT state FROM public.b62_writer_gate
  WHERE resource_key='runtime'")" = OPEN
test "$(standby_sql 'SELECT count(*) FROM public.b62_closure_receipts')" = 0
echo AETHERIS_B62_CLOSURE_RECEIPT_ROLLBACK_ATOMIC_PASS

# The admin can CLOSE only after the in-flight effect commits.
standby_sql "UPDATE public.b62_writer_gate SET state='CLOSED'
  WHERE resource_key='runtime' AND state='OPEN';" >/dev/null
closed_state="$(standby_sql "SELECT state FROM public.b62_writer_gate
  WHERE resource_key='runtime'")"
if test "$closed_state" != CLOSED; then
  echo "FAIL: committed writer gate is not CLOSED" >&2; exit 1
fi
# The committed closure transaction also creates one receipt bound to
# THIS resource's actual local endpoint, epoch and a fresh UUID.
closure_receipt_row="$(standby_sql "SELECT
  resource_key || '|' || node_identity || '|' || fence_term::text
    || '|' || receipt_nonce::text FROM public.b62_closure_receipts
  WHERE resource_key='runtime'")"
IFS='|' read -r ack_resource ack_node ack_term ack_nonce <<< "$closure_receipt_row"
test "$ack_resource" = runtime
test "$ack_node" = "$standby"
test "$ack_term" = 5
[[ "$ack_nonce" =~ ^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$ ]]
test "$(standby_sql 'SELECT count(*) FROM public.b62_closure_receipts')" = 1
# Neither the restricted login nor its separate function owner may
# issue a second record directly under this identity.
if local_call "$standby" "$local_new_pw" \
  "INSERT INTO public.b62_closure_receipts VALUES(
    'forged-resource','$primary',2,pg_catalog.gen_random_uuid(),
    clock_timestamp());" >/dev/null 2>&1; then
  echo "FAIL: restricted writer forged closure receipt" >&2; exit 1
fi
if standby_sql "BEGIN; SET LOCAL ROLE $local_owner;
  INSERT INTO public.b62_closure_receipts VALUES(
    'forged-owner','$primary',2,pg_catalog.gen_random_uuid(),
    clock_timestamp());" >/dev/null 2>&1; then
  echo "FAIL: definer owner forged closure receipt" >&2; exit 1
fi
test "$(standby_sql 'SELECT count(*) FROM public.b62_closure_receipts')" = 1
echo AETHERIS_B62_COMMITTED_CLOSURE_RECEIPT_BOUNDS_NODE_TERM_PASS
# Replaying an unchanged CLOSED-state UPDATE must not emit another
# transition receipt or replace the previously committed UUID. This
# tests event-generation idempotence, NOT replay of a copied remote ACK.
standby_sql "UPDATE public.b62_writer_gate SET state='CLOSED'
  WHERE resource_key='runtime' AND state='CLOSED';" >/dev/null
test "$(standby_sql "SELECT resource_key || '|' || node_identity || '|'
  || fence_term::text || '|' || receipt_nonce::text
  FROM public.b62_closure_receipts WHERE resource_key='runtime'")" \
  = "$closure_receipt_row"
test "$(standby_sql 'SELECT count(*) FROM public.b62_closure_receipts')" = 1
echo AETHERIS_B62_CLOSURE_RECEIPT_DUPLICATE_EVENT_IDEMPOTENT_PASS
# The restricted login retains EXECUTE permission on its SQL function,
# yet its direct SQL call MUST return false once the resource-local
# gate closure is committed. No optional shell admission is involved.
closed_sql_result="$(local_call "$standby" "$local_new_pw" \
  "SELECT public.b62_local_try_effect(5,'direct-after-closed')")"
if test "$closed_sql_result" != f; then
  echo "FAIL: CLOSED local resource admitted restricted SQL effect" >&2; exit 1
fi
if test "$(standby_sql 'SELECT count(*) FROM public.b62_local_effects')" != 7; then
  echo "FAIL: effect journal changed after resource gate CLOSED" >&2; exit 1
fi
if local_call "$standby" "$local_new_pw" \
  "UPDATE public.b62_writer_gate SET state='OPEN'
   WHERE resource_key='runtime'" >/dev/null 2>&1; then
  echo "FAIL: restricted writer can reopen closed resource gate" >&2
  exit 1
fi
test "$(standby_sql "SELECT state FROM public.b62_writer_gate
  WHERE resource_key='runtime'")" = CLOSED
test "$(standby_sql 'SELECT count(*) FROM public.b62_local_effects')" = 7
echo AETHERIS_B62_RESOURCE_LOCAL_GATE_CLOSED_DENIES_DIRECT_SQL_PASS

# The resource gate becomes a terminal local CLOSED state under ordinary
# SQL DML: even the disposable DB operator cannot accidentally reverse
# it through UPDATE, DELETE or TRUNCATE. Check the exact trigger markers
# to distinguish expected denial from an unrelated database error.
gate_tamper_error="$(mktemp)"
if standby_sql "UPDATE public.b62_writer_gate SET state='OPEN'
  WHERE resource_key='runtime' AND state='CLOSED';" \
  > /dev/null 2>"$gate_tamper_error"; then
  echo "FAIL: local CLOSED writer gate reopened via UPDATE" >&2; exit 1
fi
grep -Fq 'B62_GATE_REOPEN_FORBIDDEN' "$gate_tamper_error" ||
  { echo "FAIL: reopen denied for unrelated reason" >&2; exit 1; }
if standby_sql "DELETE FROM public.b62_writer_gate
  WHERE resource_key='runtime';" > /dev/null 2>"$gate_tamper_error"; then
  echo "FAIL: local writer gate deleted" >&2; exit 1
fi
grep -Fq 'B62_GATE_DELETE_FORBIDDEN' "$gate_tamper_error" ||
  { echo "FAIL: deletion denied for unrelated reason" >&2; exit 1; }
if standby_sql "TRUNCATE public.b62_writer_gate;" \
  > /dev/null 2>"$gate_tamper_error"; then
  echo "FAIL: local writer gate truncated" >&2; exit 1
fi
grep -Fq 'B62_GATE_TRUNCATE_FORBIDDEN' "$gate_tamper_error" ||
  { echo "FAIL: truncate denied for unrelated reason" >&2; exit 1; }
test "$(standby_sql "SELECT state FROM public.b62_writer_gate
  WHERE resource_key='runtime'")" = CLOSED
test "$(standby_sql 'SELECT count(*) FROM public.b62_writer_gate')" = 1
test "$(standby_sql 'SELECT count(*) FROM public.b62_local_effects')" = 7
echo AETHERIS_B62_CLOSED_GATE_RESISTS_ADMIN_DML_REOPEN_PASS

# This does NOT prove fail-closed on a coordinator outage while the gate
# remains OPEN, nor power-fence independently writable stale resources.
echo AETHERIS_B62_RESOURCE_GATE_DISTRIBUTED_LEADERSHIP_NO_GO
# A committed local CLOSED row should survive a real PostgreSQL process
# stop/start, including while the independent coordinator is unavailable.
# This proves only persistence on THIS physical disposable resource.
docker exec -u postgres "$standby" pg_ctl \
  -D /tmp/b62-standby -m fast -w -t 30 stop >/dev/null
docker exec -u postgres "$standby" pg_ctl \
  -D /tmp/b62-standby -o "-p 5432 -c listen_addresses=127.0.0.1" \
  -w -t 30 start >/dev/null
test "$(standby_sql 'SELECT pg_is_in_recovery()')" = f
test "$(standby_sql "SELECT state FROM public.b62_writer_gate
  WHERE resource_key='runtime'")" = CLOSED
test "$(local_call "$standby" "$local_new_pw" \
  "SELECT public.b62_local_try_effect(5,'restarted-closed-direct-deny')")" = f
test "$(standby_sql 'SELECT count(*) FROM public.b62_local_effects')" = 7
test -z "$(docker ps --filter "name=^/$authority$" --format '{{.Names}}')"
# Ordinary SQL DML must remain unable to undo closure after process
# restart, not just while the original server process is alive.
if standby_sql "UPDATE public.b62_writer_gate SET state='OPEN'
  WHERE resource_key='runtime';" > /dev/null 2>"$gate_tamper_error"; then
  echo "FAIL: gate reopened after resource restart" >&2; exit 1
fi
grep -Fq 'B62_GATE_REOPEN_FORBIDDEN' "$gate_tamper_error" ||
  { echo "FAIL: persisted trigger denied for unrelated reason" >&2; exit 1; }
test "$(standby_sql 'SELECT count(*) FROM public.b62_local_effects')" = 7
test "$(standby_sql "SELECT state FROM public.b62_writer_gate
  WHERE resource_key='runtime'")" = CLOSED
echo AETHERIS_B62_RESOURCE_CLOSED_PERSISTS_RESTART_AND_OUTAGE_PASS
echo AETHERIS_B62_CLOSED_GATE_REOPEN_AFTER_RESTART_DENIED_PASS
test "$(standby_sql "SELECT resource_key || '|' || node_identity || '|'
  || fence_term::text || '|' || receipt_nonce::text
  FROM public.b62_closure_receipts WHERE resource_key='runtime'")" \
  = "$closure_receipt_row"
test "$(standby_sql 'SELECT count(*) FROM public.b62_closure_receipts')" = 1
echo AETHERIS_B62_CLOSURE_RECEIPT_DURABLE_RESTART_PASS

# NEGATIVE COUNTEREXAMPLE: a closure receipt from the promoted node
# cannot prove that ANOTHER physical primary is irreversibly fenced.
# The old primary was previously stopped and had its local endpoint
# rewritten. A privileged operator can restart and rebind it; the
# promoted node's receipt says NOTHING about that remote authority.
docker start "$primary" >/dev/null
old_ready=false
for attempt in $(seq 1 50); do
  if test "$(primary_sql 'SELECT 1' 2>/dev/null || true)" = 1; then
    old_ready=true; break
  fi
  sleep 0.1
done
test "$old_ready" = true
test "$(primary_sql 'SELECT pg_is_in_recovery()')" = f
test "$(primary_sql "SELECT state FROM public.b62_writer_gate
  WHERE resource_key='runtime'")" = OPEN
test "$(primary_sql 'SELECT count(*) FROM public.b62_closure_receipts')" = 0
test "$(local_call "$primary" "$local_old_pw" \
  "SELECT public.b62_local_try_effect(2,'before-admin-old-rebind')")" = f
primary_sql "UPDATE public.b62_local_fence
  SET endpoint='$primary',term=2
  WHERE resource_key='runtime' AND endpoint='$standby' AND term=4;" >/dev/null
test "$(local_call "$primary" "$local_old_pw" \
  "SELECT public.b62_local_try_effect(2,'unsafe-old-operator-rebind')")" = t
test "$(primary_sql "SELECT term FROM public.b62_local_effects
  WHERE request_id='unsafe-old-operator-rebind'")" = 2
test "$(primary_sql 'SELECT count(*) FROM public.b62_closure_receipts')" = 0
echo AETHERIS_B62_PROMOTED_RECEIPT_CANNOT_FENCE_PRIVILEGED_FAILBACK_NO_GO_PASS

# This never tests or proves global linearizability, quorum or failback.
echo AETHERIS_B62_CLOSED_GATE_GLOBAL_LEADERSHIP_NO_GO
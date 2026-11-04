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

cleanup() {
  local code=$?
  trap - EXIT
  docker rm -fv "$authority" "$standby" "$primary" >/dev/null 2>&1 || true
  docker network rm "$network" >/dev/null 2>&1 || true
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
CREATE TABLE public.b62_protected_actions(
  request_id text PRIMARY KEY, actor_login text NOT NULL, term bigint NOT NULL);
CREATE ROLE $fence_owner NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE;
CREATE ROLE $fence_old LOGIN PASSWORD '$fence_old_pw';
CREATE ROLE $fence_new LOGIN PASSWORD '$fence_new_pw';
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
REVOKE ALL ON public.b62_resource_fence, public.b62_protected_actions FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO $fence_owner, $fence_old, $fence_new;
GRANT SELECT ON public.leader_fence, public.b62_resource_fence TO $fence_owner;
GRANT UPDATE(term) ON public.leader_fence TO $fence_owner;
GRANT UPDATE(term) ON public.b62_resource_fence TO $fence_owner;
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
test "$(coord_write 'SELECT count(*) FROM public.b62_protected_actions')" = 1
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
test "$(coord_write 'SELECT count(*) FROM public.b62_protected_actions')" = 2
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
    CREATE TABLE public.b62_local_effects(
      request_id text PRIMARY KEY, term bigint NOT NULL CHECK(term>0));
    CREATE ROLE $local_owner NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE;
    CREATE ROLE $local_writer LOGIN PASSWORD '$password';
    REVOKE ALL ON public.b62_local_fence,public.b62_local_effects FROM PUBLIC;
    GRANT USAGE ON SCHEMA public TO $local_owner,$local_writer;
    GRANT SELECT ON public.b62_local_fence TO $local_owner;
    GRANT UPDATE(term) ON public.b62_local_fence TO $local_owner;
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
      PERFORM 1 FROM public.b62_local_fence
      WHERE resource_key='runtime' AND endpoint='$pinned'
        AND term=p_term AND NOT pg_is_in_recovery()
      FOR SHARE;
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
test "$(standby_sql 'SELECT count(*) FROM public.b62_local_effects')" = 1

# Local fencing survives a real network disconnect on the OLD server.
# docker exec loopback is deliberately a negative-only in-container probe;
# no external callers are enabled and network reachability is NOT proven.
docker network disconnect "$network" "$primary"
test "$(local_call "$primary" "$local_old_pw" \
  "SELECT public.b62_local_try_effect(2,'isolated-stale-term')")" = f
test "$(primary_sql 'SELECT count(*) FROM public.b62_local_effects')" = 1
echo AETHERIS_B62_SEPARATE_DATABASE_OLD_RESOURCE_FENCED_DURING_PARTITION_PASS

# Disposable PostgreSQL source/replay watermark negative proof (review only).
# This MUST NOT be interpreted as runtime authorization or API admission.
source scripts/security/runtime_b62_pg16_revocation_continuity_review.sh
# Review-only source-event authentication; still no safe distributed cutover.
source scripts/security/runtime_b62_pg16_provenance_authenticity_review.sh
# A revocation source can commit while an uncommitted resource effect remains.
source scripts/security/runtime_b62_pg16_inflight_commit_revocation_review.sh
# Bounded reciprocal proof: reachable resource drains writes before ACK.
source scripts/security/runtime_b62_pg16_reachable_prepublication_barrier_review.sh
# Coordinator fails after the deny barrier, before event publication.
source scripts/security/runtime_b62_pg16_source_crash_recovery_review.sh
# Negative: coordinator outage BEFORE a resource admission barrier.
source scripts/security/runtime_b62_pg16_prebarrier_coordinator_loss_review.sh

# A local term alone does NOT detect coordinator outage: the promoted
# database still accepts a locally matched synthetic term while its
# authoritative coordinator is unreachable. This is a deliberate
# UNRELEASEABLE fail-open counterexample, not acceptable Runtime behavior.
docker stop "$authority" >/dev/null
test "$(local_call "$standby" "$local_new_pw" \
  "SELECT public.b62_local_try_effect(4,'authority-outage-local-allow')")" = t
test "$(standby_sql 'SELECT count(*) FROM public.b62_local_effects')" = 2
echo AETHERIS_B62_LOCAL_ONLY_FENCE_COORDINATOR_OUTAGE_ALLOW_NO_GO_PASS

# EXTERNAL FENCING EXAMPLE ONLY: stop the old server process/container.
# In real systems, fencing/leases/route control must be independently
# designed, cryptographically authenticated and operationally tested.
docker stop "$primary" >/dev/null
test -z "$(docker ps --filter "name=^/$primary$" --format '{{.Names}}')"
test "$(standby_sql 'SELECT pg_is_in_recovery()')" = f
test "$(standby_sql "$promotion_sql")" = f
echo AETHERIS_B62_DISPOSABLE_OLD_PRIMARY_FENCED_AFTER_PROMOTION_PASS

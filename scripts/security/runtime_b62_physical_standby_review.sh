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
dbuser=aetheris_replica_ci
db=aetheris_replica_disposable
replicauser=b62_ci_replication
owner_password="$(openssl rand -hex 24)"
replica_password="$(openssl rand -hex 24)"

cleanup() {
  local code=$?
  trap - EXIT
  docker rm -fv "$standby" "$primary" >/dev/null 2>&1 || true
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
  if docker exec "$primary" pg_isready -U "$dbuser" -d "$db" >/dev/null 2>&1; then
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

# EXTERNAL FENCING EXAMPLE ONLY: stop the old server process/container.
# In real systems, fencing/leases/route control must be independently
# designed, cryptographically authenticated and operationally tested.
docker stop "$primary" >/dev/null
test -z "$(docker ps --filter "name=^/$primary$" --format '{{.Names}}')"
test "$(standby_sql 'SELECT pg_is_in_recovery()')" = f
test "$(standby_sql "$promotion_sql")" = f
echo AETHERIS_B62_DISPOSABLE_OLD_PRIMARY_FENCED_AFTER_PROMOTION_PASS

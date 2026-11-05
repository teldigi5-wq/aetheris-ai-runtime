#!/usr/bin/env bash
# SYNTHETIC REVIEW ONLY: authenticated revocation-event binding in TWO
# disposable PostgreSQL16 processes. This is NOT production event signing,
# resource-fencing, or independent quorum.
test "$GITHUB_ACTIONS" = true
test "$AETHERIS_B62_REPLICA_CI_ONLY" = yes
test "$(standby_sql 'SELECT pg_is_in_recovery()')" = f
test "$(coord_write "SELECT endpoint || ':' || term FROM public.leader_fence WHERE authority_key='runtime'")" = "$standby:4"

# Synthetic HMAC key is never committed, echoed, or granted to the caller.
# Shared-key compromise, origin trust, out-of-order delivery and failover
# remain release blockers. Only the disposable PG16 owner handles setup.
provenance_key="$(openssl rand -hex 32)"
replay_actor=b62_provenance_replay_actor
replay_pw="$(openssl rand -hex 24)"
coord_write "CREATE EXTENSION IF NOT EXISTS pgcrypto;
 CREATE TABLE public.b62_provenance_source_key(key_bytes bytea NOT NULL);
 INSERT INTO public.b62_provenance_source_key VALUES
   (pg_catalog.decode('$provenance_key','hex'));
 CREATE TABLE public.b62_provenance_source(
   seq bigint PRIMARY KEY CHECK(seq>0), uid bigint NOT NULL CHECK(uid>0),
   tag bytea NOT NULL);
 INSERT INTO public.b62_provenance_source(seq,uid,tag)
 SELECT v.seq,v.uid,
   public.hmac(pg_catalog.convert_to(v.seq::text || ':' || v.uid::text,'UTF8'),
     k.key_bytes,'sha256')
 FROM (VALUES(1::bigint,11::bigint),(2::bigint,42::bigint)) v(seq,uid)
 CROSS JOIN public.b62_provenance_source_key k;
 REVOKE ALL ON public.b62_provenance_source_key,
   public.b62_provenance_source FROM PUBLIC;" >/dev/null

standby_sql "BEGIN;
 CREATE EXTENSION IF NOT EXISTS pgcrypto;
 CREATE TABLE public.b62_provenance_verify_key(key_bytes bytea NOT NULL);
 INSERT INTO public.b62_provenance_verify_key VALUES
   (pg_catalog.decode('$provenance_key','hex'));
 CREATE TABLE public.b62_provenance_replay_state(
   resource_key text PRIMARY KEY, last_seq bigint NOT NULL CHECK(last_seq>=0));
 INSERT INTO public.b62_provenance_replay_state VALUES('runtime',0);
 CREATE TABLE public.b62_provenance_receipts(
   seq bigint PRIMARY KEY CHECK(seq>0), uid bigint NOT NULL CHECK(uid>0),
   tag bytea NOT NULL);
 CREATE ROLE $replay_actor LOGIN PASSWORD '$replay_pw'
   NOSUPERUSER NOCREATEDB NOCREATEROLE;
 REVOKE ALL ON public.b62_provenance_verify_key,
   public.b62_provenance_replay_state,public.b62_provenance_receipts FROM PUBLIC;
 GRANT USAGE ON SCHEMA public TO $replay_actor;
 GRANT SELECT ON public.b62_provenance_verify_key TO $local_owner;
 GRANT SELECT ON public.b62_provenance_replay_state TO $local_owner;
 GRANT UPDATE(last_seq) ON public.b62_provenance_replay_state TO $local_owner;
 GRANT INSERT ON public.b62_provenance_receipts TO $local_owner;
 CREATE FUNCTION public.b62_try_authenticated_replay(
   p_seq bigint,p_uid bigint,p_tag text)
 RETURNS boolean LANGUAGE plpgsql SECURITY DEFINER
 SET search_path=pg_catalog,pg_temp AS \$provenance_proof\$
 DECLARE previous bigint; secret bytea; calculated bytea;
 BEGIN
   IF session_user <> '$replay_actor' OR p_seq IS NULL
      OR p_uid IS NULL OR p_seq<1 OR p_uid<1
      OR p_tag IS NULL OR p_tag !~ '^[0-9a-f]{64}$' THEN
     RETURN false;
   END IF;
   SELECT last_seq INTO previous FROM public.b62_provenance_replay_state
     WHERE resource_key='runtime' FOR UPDATE;
   IF NOT FOUND OR p_seq<>previous+1 THEN RETURN false; END IF;
   SELECT key_bytes INTO secret FROM public.b62_provenance_verify_key LIMIT 1;
   IF secret IS NULL THEN RETURN false; END IF;
   calculated := public.hmac(
     pg_catalog.convert_to(p_seq::text || ':' || p_uid::text,'UTF8'),
     secret,'sha256');
   IF calculated <> pg_catalog.decode(p_tag,'hex') THEN RETURN false; END IF;
   INSERT INTO public.b62_provenance_receipts(seq,uid,tag)
     VALUES(p_seq,p_uid,calculated);
   UPDATE public.b62_provenance_replay_state SET last_seq=p_seq
     WHERE resource_key='runtime';
   RETURN true;
 END \$provenance_proof\$;
 ALTER FUNCTION public.b62_try_authenticated_replay(bigint,bigint,text)
   OWNER TO $local_owner;
 REVOKE ALL ON FUNCTION public.b62_try_authenticated_replay(
   bigint,bigint,text) FROM PUBLIC;
 GRANT EXECUTE ON FUNCTION public.b62_try_authenticated_replay(
   bigint,bigint,text) TO $replay_actor;
 COMMIT;" >/dev/null
# Minimize lifetime of test-only key in shell memory.
unset provenance_key

replay_call() {
  local sql="$1"
  docker exec -e "PGPASSWORD=$replay_pw" -e PGCONNECT_TIMEOUT=2 "$standby" \
    psql -X -A -t -w -v ON_ERROR_STOP=1 -h 127.0.0.1 -p 5432 \
      -U "$replay_actor" -d "$db" -c "$sql"
}
tag1="$(coord_write "SELECT pg_catalog.encode(tag,'hex') FROM public.b62_provenance_source WHERE seq=1")"
tag2="$(coord_write "SELECT pg_catalog.encode(tag,'hex') FROM public.b62_provenance_source WHERE seq=2")"
[[ "$tag1" =~ ^[0-9a-f]{64}$ ]] && [[ "$tag2" =~ ^[0-9a-f]{64}$ ]]
test "$(replay_call "SELECT public.b62_try_authenticated_replay(1,999,'$tag1')")" = f
test "$(replay_call "SELECT public.b62_try_authenticated_replay(1,11,'$(printf %064d 0)')")" = f
test "$(replay_call "SELECT public.b62_try_authenticated_replay(1,11,'deadbeef')")" = f
test "$(replay_call "SELECT public.b62_try_authenticated_replay(2,42,'$tag2')")" = f
test "$(standby_sql 'SELECT last_seq FROM public.b62_provenance_replay_state')" = 0
test "$(standby_sql 'SELECT count(*) FROM public.b62_provenance_receipts')" = 0
echo AETHERIS_B62_PG16_PROVENANCE_MISMATCH_FORGED_OUT_OF_ORDER_DENIED_PASS

test "$(replay_call "SELECT public.b62_try_authenticated_replay(1,11,'$tag1')")" = t
test "$(replay_call "SELECT public.b62_try_authenticated_replay(1,11,'$tag1')")" = f
test "$(replay_call "SELECT public.b62_try_authenticated_replay(2,42,'$tag2')")" = t
test "$(standby_sql 'SELECT last_seq FROM public.b62_provenance_replay_state')" = 2
test "$(standby_sql 'SELECT count(*) FROM public.b62_provenance_receipts')" = 2

if replay_call "SELECT key_bytes FROM public.b62_provenance_verify_key" >/dev/null 2>&1; then
  echo 'FAIL: restricted replay caller read private test HMAC key' >&2; exit 1
fi
if replay_call "UPDATE public.b62_provenance_replay_state SET last_seq=0" >/dev/null 2>&1; then
  echo 'FAIL: restricted replay caller changed high-water mark' >&2; exit 1
fi
if replay_call "INSERT INTO public.b62_provenance_receipts VALUES(3,999,'\\x00')" >/dev/null 2>&1; then
  echo 'FAIL: restricted replay caller bypassed verified replay function' >&2; exit 1
fi
test "$(standby_sql 'SELECT last_seq FROM public.b62_provenance_replay_state')" = 2
echo AETHERIS_B62_PG16_AUTHENTICATED_EVENT_REPLAY_TEST_ONLY_PASS

#!/usr/bin/env bash
set -euo pipefail

# INTENTIONALLY CI-ONLY: never accept production URLs or arbitrary DB names.
# This performs destructive operations exclusively on ephemeral fixture DBs.
if [[ "${GITHUB_ACTIONS:-}" != "true" ||
      "${AETHERIS_SYNTHETIC_RESTORE_TEST:-}" != "true" ||
      "${PGHOST:-}" != "127.0.0.1" ||
      "${PGPORT:-}" != "5432" ||
      "${PGUSER:-}" != "postgres" ||
      "${PGDATABASE:-}" != "aetheris_memory_ci_source" ]]; then
  echo "BLOCKED: disposable GitHub Actions PostgreSQL fixture only" >&2
  exit 1
fi

source_db="aetheris_memory_ci_source"
restore_db="aetheris_memory_ci_restore"
temp_dir="$(mktemp -d)"
trap 'rm -rf "$temp_dir"' EXIT

# The GitHub Actions ephemeral service creates only source_db; this test
# creates the distinct restore DB itself. Refuse pre-existing destination.
existing="$(psql -X -qAt -d postgres -c "SELECT count(*) FROM pg_database WHERE datname = '$restore_db'")"
if [[ "$existing" != "0" ]]; then
  echo "BLOCKED: restore database must not pre-exist" >&2
  exit 1
fi

# The schema/data have no sensitive values, keys or real user memory.
psql -X -q -v ON_ERROR_STOP=1 -d "$source_db" \
  -f tests/postgresql/memory_backup_restore_fixture.sql

# Explicit snapshot read-only and repeatable-read smoke test. This verifies
# transactional query behavior, NOT that the application JPA code already
# uses the correct PostgreSQL DB role or a production-grade snapshot.
psql -X -q -v ON_ERROR_STOP=1 -d "$source_db" <<'SQL'
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;
SELECT count(*) FROM public.memory_backup_fixture;
COMMIT;
SQL

# Avoid printing fixture values, user data, secret material or dump bytes.
source_fingerprint="$(psql -X -qAt -d "$source_db" -v ON_ERROR_STOP=1 -c \
  "SELECT count(*)::text || ':' ||
          md5(string_agg(id::text || ':' || owner_id || ':' || coalesce(project_id, '') ||
            ':' || payload || ':' || encrypted::text || ':' || sensitive::text,
            '|' ORDER BY id))
     FROM public.memory_backup_fixture;")"

if [[ "$source_fingerprint" != 4:* ]]; then
  echo "FAIL: fixture rows missing" >&2
  exit 1
fi

pg_dump --format=custom --no-owner --no-privileges \
  --table=public.memory_backup_fixture \
  --file="$temp_dir/synthetic-fixture.dump" "$source_db"
createdb "$restore_db"
pg_restore --no-owner --no-privileges --exit-on-error \
  --dbname="$restore_db" "$temp_dir/synthetic-fixture.dump"

restored_fingerprint="$(psql -X -qAt -d "$restore_db" -v ON_ERROR_STOP=1 -c \
  "SELECT count(*)::text || ':' ||
          md5(string_agg(id::text || ':' || owner_id || ':' || coalesce(project_id, '') ||
            ':' || payload || ':' || encrypted::text || ':' || sensitive::text,
            '|' ORDER BY id))
     FROM public.memory_backup_fixture;")"

if [[ "$source_fingerprint" != "$restored_fingerprint" ]]; then
  echo "FAIL: synthetic PostgreSQL restored rows differ" >&2
  exit 1
fi

# Negative control: prove the fingerprint detects deliberate corruption of
# the RESTORED disposable fixture, never the backup or source DB.
psql -X -q -v ON_ERROR_STOP=1 -d "$restore_db" -c \
  "UPDATE public.memory_backup_fixture
   SET payload = 'SYNTHETIC_CORRUPTED_RESTORE_ONLY'
   WHERE id = '00000000-0000-0000-0000-000000000102';"
tampered_fingerprint="$(psql -X -qAt -d "$restore_db" -v ON_ERROR_STOP=1 -c \
  "SELECT count(*)::text || ':' ||
          md5(string_agg(id::text || ':' || owner_id || ':' || coalesce(project_id, '') ||
            ':' || payload || ':' || encrypted::text || ':' || sensitive::text,
            '|' ORDER BY id))
     FROM public.memory_backup_fixture;")"
if [[ "$tampered_fingerprint" == "$source_fingerprint" ]]; then
  echo "FAIL: corruption of synthetic restored data went undetected" >&2
  exit 1
fi

# The source must be preserved byte-for-byte, including every synthetic
# header, plaintext sentinel and owner/project attribute.
unchanged_source="$(psql -X -qAt -d "$source_db" -v ON_ERROR_STOP=1 -c \
  "SELECT count(*)::text || ':' ||
          md5(string_agg(id::text || ':' || owner_id || ':' || coalesce(project_id, '') ||
            ':' || payload || ':' || encrypted::text || ':' || sensitive::text,
            '|' ORDER BY id))
     FROM public.memory_backup_fixture;")"
if [[ "$unchanged_source" != "$source_fingerprint" ]]; then
  echo "FAIL: source fixture was modified" >&2
  exit 1
fi

echo "PASS: synthetic PostgreSQL logical dump/restore round-trip; tamper control detected"
echo "NOTE: not encrypted-backup certification, AEAD authenticity validation or real data recovery"

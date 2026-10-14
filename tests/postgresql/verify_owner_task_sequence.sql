-- Run after applying the migration TWICE in a disposable PostgreSQL 16 DB.
DO $checks$
DECLARE
  expected_columns integer;
  actual_columns integer;
BEGIN
  SELECT count(*) INTO expected_columns
    FROM information_schema.columns
   WHERE table_schema = 'public'
     AND table_name = 'aetheris_tasks'
     AND column_name IN ('owner_user_id','owner_project_id','last_journal_sequence');
  IF expected_columns <> 3 THEN RAISE EXCEPTION 'missing task ownership/sequence columns'; END IF;

  SELECT count(*) INTO actual_columns
    FROM information_schema.columns
   WHERE table_schema = 'public'
     AND table_name = 'aetheris_mission_sessions'
     AND column_name IN ('owner_user_id','owner_project_id');
  IF actual_columns <> 2 THEN RAISE EXCEPTION 'missing mission owner columns'; END IF;

  SELECT count(*) INTO actual_columns
    FROM information_schema.columns
   WHERE table_schema = 'public'
     AND table_name = 'aetheris_task_events'
     AND column_name = 'commit_sequence';
  IF actual_columns <> 1 THEN RAISE EXCEPTION 'missing event sequence column'; END IF;

  IF EXISTS (
    SELECT 1 FROM aetheris_tasks
     WHERE id = '11111111-1111-4111-8111-111111111111'
       AND (owner_user_id IS NOT NULL OR last_journal_sequence IS NOT NULL)
  ) THEN RAISE EXCEPTION 'legacy task was assigned a fake owner or sequence'; END IF;

  IF NOT EXISTS (
    SELECT 1 FROM aetheris_task_events
     WHERE id = '33333333-3333-4333-8333-333333333333'
       AND commit_sequence IS NULL
  ) THEN RAISE EXCEPTION 'legacy event changed during migration'; END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_indexes
     WHERE tablename = 'aetheris_task_events'
       AND indexname = 'idx_aetheris_task_event_sequence'
       AND indexdef LIKE '%UNIQUE INDEX%'
  ) THEN RAISE EXCEPTION 'missing unique sequence index'; END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_indexes
     WHERE tablename = 'aetheris_task_events'
       AND indexname = 'idx_aetheris_task_event_sequence'
       AND indexdef LIKE '%WHERE (commit_sequence IS NOT NULL)%'
  ) THEN RAISE EXCEPTION 'sequence uniqueness does not exclude legacy NULL rows'; END IF;

  SELECT count(*) INTO actual_columns
    FROM information_schema.columns
   WHERE table_schema = 'public'
     AND table_name = 'aetheris_task_journal_outbox'
     AND column_name IN ('journal_event_id','task_id','state','created_at',
                         'next_attempt_at','lease_until','lease_token','attempts','version');
  IF actual_columns <> 9 THEN RAISE EXCEPTION 'outbox schema incomplete'; END IF;

  IF EXISTS (
    SELECT 1 FROM information_schema.columns
     WHERE table_name = 'aetheris_task_journal_outbox'
       AND column_name IN ('token','access_token','secret','command_text',
                           'message','metadata_json','owner_email')
  ) THEN RAISE EXCEPTION 'private payload column found in outbox'; END IF;
END;
$checks$;

-- Positive sequenced record; legacy NULLs can still be present.
INSERT INTO aetheris_task_events (id,task_id,event_at,commit_sequence)
VALUES ('44444444-4444-4444-8444-444444444444',
        '11111111-1111-4111-8111-111111111111',
        '2026-10-13T00:00:00Z', 1);

-- Verify unique index rejects same task/sequence without aborting tests.
DO $duplicates$
BEGIN
  BEGIN
    INSERT INTO aetheris_task_events (id,task_id,event_at,commit_sequence)
    VALUES ('55555555-5555-4555-8555-555555555555',
            '11111111-1111-4111-8111-111111111111',
            '2026-10-12T00:00:00Z', 1);
    RAISE EXCEPTION 'duplicate sequence unexpectedly allowed';
  EXCEPTION WHEN unique_violation THEN
    NULL; -- expected
  END;
END;
$duplicates$;

-- Invalid zero values are prohibited even though legacy NULL is allowed.
DO $bounds$
BEGIN
  BEGIN
    UPDATE aetheris_tasks SET last_journal_sequence = 0
     WHERE id = '11111111-1111-4111-8111-111111111111';
    RAISE EXCEPTION 'invalid task sequence accepted';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
  BEGIN
    UPDATE aetheris_task_events SET commit_sequence = 0
     WHERE id = '44444444-4444-4444-8444-444444444444';
    RAISE EXCEPTION 'invalid event sequence accepted';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
END;
$bounds$;

SELECT 'MIGRATION_REPLAY_AND_CONSTRAINTS_PASS' AS verification;

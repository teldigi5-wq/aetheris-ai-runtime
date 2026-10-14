-- Disposable PostgreSQL verification after v1 then v2 applied twice.
DO $check$
DECLARE column_count integer;
BEGIN
  SELECT count(*) INTO column_count FROM information_schema.columns
   WHERE table_schema = 'public'
     AND table_name = 'aetheris_owner_task_checkpoints'
     AND column_name IN ('id','owner_user_id','task_id','last_sequence',
                         'last_event_id','created_at','updated_at','version');
  IF column_count <> 8 THEN RAISE EXCEPTION 'checkpoint schema incomplete'; END IF;
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
     WHERE table_name = 'aetheris_owner_task_checkpoints'
       AND column_name IN ('access_token','jwt','refresh_token','email',
                           'command_text','message','metadata_json','secret')
  ) THEN RAISE EXCEPTION 'sensitive data field in checkpoint schema'; END IF;
  IF NOT EXISTS (
    SELECT 1 FROM pg_indexes
     WHERE tablename = 'aetheris_owner_task_checkpoints'
       AND indexname = 'idx_aetheris_checkpoint_owner_task'
  ) THEN RAISE EXCEPTION 'missing owner/task query index'; END IF;
END;
$check$;

INSERT INTO aetheris_owner_task_checkpoints
    (id,owner_user_id,task_id,created_at,updated_at)
VALUES ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
        101,'11111111-1111-4111-8111-111111111111',
        now(),now());
DO $integrity$
BEGIN
  BEGIN
    UPDATE aetheris_owner_task_checkpoints SET last_sequence = -1
      WHERE id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
    RAISE EXCEPTION 'negative last sequence accepted';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
  BEGIN
    UPDATE aetheris_owner_task_checkpoints SET owner_user_id = 0
      WHERE id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
    RAISE EXCEPTION 'invalid checkpoint owner accepted';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
END;
$integrity$;

DO $legacy$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM aetheris_tasks
    WHERE id = '11111111-1111-4111-8111-111111111111'
      AND owner_user_id IS NULL
  ) THEN RAISE EXCEPTION 'legacy ownership was modified'; END IF;
END;
$legacy$;
SELECT 'OWNER_CHECKPOINT_MIGRATION_PASSED' AS verification;

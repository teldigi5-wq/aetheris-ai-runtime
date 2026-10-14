-- Review-only additive migration for the owner-specific, client-declared
-- task-delivery acknowledgement checkpoint. NEVER auto-apply in production.
-- The checkpoint UUID is not authorization: signed JWT + durable task owner
-- are verified again before each replay and acknowledgement.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';

CREATE TABLE IF NOT EXISTS aetheris_owner_task_checkpoints (
  id UUID PRIMARY KEY,
  owner_user_id BIGINT NOT NULL,
  task_id UUID NOT NULL,
  last_sequence BIGINT NOT NULL DEFAULT 0,
  last_event_id UUID,
  created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
  updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT ck_aetheris_checkpoint_owner_positive CHECK (owner_user_id > 0),
  CONSTRAINT ck_aetheris_checkpoint_sequence_nonnegative CHECK (last_sequence >= 0)
);

CREATE INDEX IF NOT EXISTS idx_aetheris_checkpoint_owner_task
  ON aetheris_owner_task_checkpoints (owner_user_id, task_id);

COMMIT;

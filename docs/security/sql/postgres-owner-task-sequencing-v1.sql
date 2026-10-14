-- REVIEW-ONLY additive migration; NEVER applied automatically.
-- Applies on top of the existing baseline Aetheris orchestrator tables.
-- No owner ID backfills, no role inference and no implicit task permissions.
-- Snapshot/backup and a DBA-reviewed migration window are required.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';

ALTER TABLE aetheris_tasks
  ADD COLUMN IF NOT EXISTS owner_user_id BIGINT,
  ADD COLUMN IF NOT EXISTS owner_project_id UUID,
  ADD COLUMN IF NOT EXISTS last_journal_sequence BIGINT;

ALTER TABLE aetheris_mission_sessions
  ADD COLUMN IF NOT EXISTS owner_user_id BIGINT,
  ADD COLUMN IF NOT EXISTS owner_project_id UUID;

ALTER TABLE aetheris_task_events
  ADD COLUMN IF NOT EXISTS commit_sequence BIGINT;

-- New outbox records contain only pointers and bounded retry/lease state.
-- No JWT, task command, event message, email, or private metadata.
CREATE TABLE IF NOT EXISTS aetheris_task_journal_outbox (
  id UUID PRIMARY KEY,
  journal_event_id UUID NOT NULL,
  task_id UUID NOT NULL,
  state VARCHAR(32) NOT NULL,
  created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
  next_attempt_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
  lease_until TIMESTAMP(6) WITH TIME ZONE,
  lease_token UUID,
  attempts INTEGER NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT uq_aetheris_outbox_journal UNIQUE (journal_event_id),
  CONSTRAINT ck_aetheris_outbox_attempts CHECK (attempts BETWEEN 0 AND 5),
  CONSTRAINT ck_aetheris_outbox_state CHECK (
    state IN ('PENDING', 'IN_FLIGHT', 'HANDOFF_ATTEMPTED', 'PARKED'))
);

CREATE INDEX IF NOT EXISTS idx_aetheris_outbox_ready
  ON aetheris_task_journal_outbox (state, next_attempt_at, lease_until);

CREATE INDEX IF NOT EXISTS idx_aetheris_task_event_cursor
  ON aetheris_task_events (task_id, event_at, id);

-- PostgreSQL permits many NULL legacy rows under a unique composite index.
-- Only new controlled outbox-backed events receive a positive sequence.
CREATE UNIQUE INDEX IF NOT EXISTS idx_aetheris_task_event_sequence
  ON aetheris_task_events (task_id, commit_sequence)
  WHERE commit_sequence IS NOT NULL;

ALTER TABLE aetheris_tasks
  ADD CONSTRAINT ck_aetheris_task_journal_seq_positive
  CHECK (last_journal_sequence IS NULL OR last_journal_sequence >= 1)
  NOT VALID;

ALTER TABLE aetheris_task_events
  ADD CONSTRAINT ck_aetheris_event_commit_seq_positive
  CHECK (commit_sequence IS NULL OR commit_sequence >= 1)
  NOT VALID;

COMMIT;

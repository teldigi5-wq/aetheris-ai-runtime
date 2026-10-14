-- Disposable pre-migration PostgreSQL schema fixture.
-- Represents only the minimum existing parent tables touched by the
-- additive migration. These are TEST tables, not a production export.
CREATE TABLE aetheris_tasks (
  id UUID PRIMARY KEY
);
CREATE TABLE aetheris_mission_sessions (
  id UUID PRIMARY KEY
);
CREATE TABLE aetheris_task_events (
  id UUID PRIMARY KEY,
  task_id UUID NOT NULL,
  event_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
);
INSERT INTO aetheris_tasks (id)
VALUES ('11111111-1111-4111-8111-111111111111');
INSERT INTO aetheris_mission_sessions (id)
VALUES ('22222222-2222-4222-8222-222222222222');
INSERT INTO aetheris_task_events (id,task_id,event_at)
VALUES ('33333333-3333-4333-8333-333333333333',
        '11111111-1111-4111-8111-111111111111',
        '2026-10-13T00:00:00Z');

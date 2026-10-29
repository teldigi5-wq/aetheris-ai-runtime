-- Synthetic pre-migration layout shaped from TaskEntity at PR #212 head;
-- NOT a dump or verified representation of any real/customer database.
CREATE TABLE aetheris_tasks (
 id uuid PRIMARY KEY,
 title varchar(160) NOT NULL,
 command_text varchar(8000) NOT NULL,
 state varchar(32) NOT NULL,
 mode varchar(32) NOT NULL,
 active_agent_id varchar(120),
 created_at timestamptz NOT NULL,
 updated_at timestamptz NOT NULL,
 version bigint NOT NULL DEFAULT 0
);

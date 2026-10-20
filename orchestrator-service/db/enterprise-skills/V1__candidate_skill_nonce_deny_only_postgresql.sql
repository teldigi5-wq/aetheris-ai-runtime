-- E3.9G-6: reviewed PostgreSQL 16 schema for candidate-skill NONCE INSPECTIONS.
-- Apply through a separately authorized migration identity ONLY.
-- This table records read-only evidence; it is NOT human authentication,
-- an approval/permit/token, a model/tool invocation, or a Stage45 release.
-- No automatic purge: nonce uniqueness MUST outlive the full replay horizon.
BEGIN;
CREATE TABLE enterprise_skill_candidate_proof_nonces (
    nonce_hex varchar(32) PRIMARY KEY,
    task_id uuid NOT NULL,
    agent_id varchar(64) NOT NULL,
    skill_id varchar(64) NOT NULL,
    proof_sha256 varchar(64) NOT NULL,
    observed_at timestamp(6) with time zone NOT NULL,
    proof_expires_at timestamp(6) with time zone NOT NULL,
    evidence_state varchar(80) NOT NULL,
    CONSTRAINT e39g6_nonce_128bit_lowercase CHECK (
        nonce_hex ~ '^[0-9a-f]{32}$'
    ),
    CONSTRAINT e39g6_agent_skill_slug CHECK (
        agent_id ~ '^[a-z0-9]+(-[a-z0-9]+)*$'
        AND skill_id ~ '^[a-z0-9]+(-[a-z0-9]+)*$'
    ),
    CONSTRAINT e39g6_proof_digest CHECK (
        proof_sha256 ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT e39g6_evidence_deny_only CHECK (
        evidence_state = 'CANDIDATE_SIGNATURE_INSPECTED_NOT_OWNER_AUTHENTICATED'
    ),
    CONSTRAINT e39g6_expiry_after_inspection CHECK (
        proof_expires_at > observed_at
    ),
    CONSTRAINT e39g6_max_remaining_proof_lifetime CHECK (
        proof_expires_at <= observed_at + interval '5 minutes'
    )
);

-- Migration owner alone can modify schema. Runtime identity needs ONLY INSERT
-- and selected SELECT for nonces; no UPDATE, DELETE, TRUNCATE, REFERENCES,
-- or CREATE/ALTER permissions. Grant through reviewed deployment provisioning.
REVOKE ALL ON TABLE enterprise_skill_candidate_proof_nonces FROM PUBLIC;
COMMIT;

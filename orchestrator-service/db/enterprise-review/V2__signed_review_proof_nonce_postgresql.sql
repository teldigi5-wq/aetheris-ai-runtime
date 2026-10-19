-- Aetheris E2.2B-2C. Nonce is a witnessed signature inspection, not authorization.
-- Requires V1 deny-only schema; explicit reviewed opt-in, never auto-deployed.
BEGIN;
CREATE TABLE enterprise_review_proof_nonces (
    nonce_hex varchar(32) PRIMARY KEY,
    intent_id varchar(36) NOT NULL,
    project_id varchar(80) NOT NULL,
    proof_sha256 varchar(64) NOT NULL,
    observed_at timestamp(6) with time zone NOT NULL,
    proof_expires_at timestamp(6) with time zone NOT NULL,
    evidence_state varchar(64) NOT NULL,
    CONSTRAINT e22c_global_nonce_hex CHECK (nonce_hex ~ '^[0-9a-f]{32}$'),
    CONSTRAINT e22c_nonce_project CHECK (project_id ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT e22c_nonce_digest CHECK (proof_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT e22c_nonce_expiry CHECK (proof_expires_at > observed_at),
    CONSTRAINT e22c_nonce_not_authority CHECK (
        evidence_state = 'SIGNED_BYTES_SEEN_OWNER_UNVERIFIED'
    ),
    CONSTRAINT e22c_nonce_intent_fk FOREIGN KEY (intent_id)
        REFERENCES enterprise_design_review_intents(id) ON DELETE RESTRICT
);
CREATE INDEX e22c_nonce_intent_lookup ON enterprise_review_proof_nonces (intent_id);

-- All columns carry insertion-time evidence; mutations are prohibited to a
-- future dedicated runtime DB role, enforced via least-privilege grants
-- separately. Migration owner and superuser remain privileged.
REVOKE ALL ON enterprise_review_proof_nonces FROM PUBLIC;
COMMIT;

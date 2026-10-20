-- E3.9G-6: disposable CI-only PostgreSQL 16 DDL constraint checks.
-- All rows rolled back. No user data, production permissions or approval.
BEGIN;
DO $$
DECLARE
    at_time timestamptz := date_trunc('microseconds', clock_timestamp());
BEGIN
    INSERT INTO enterprise_skill_candidate_proof_nonces (
        nonce_hex, task_id, agent_id, skill_id, proof_sha256,
        observed_at, proof_expires_at, evidence_state
    ) VALUES (
        repeat('a', 32), '64b3452c-0647-4ad9-a7cb-f707f257ddcb'::uuid,
        'frontend-engineer', 'animate', repeat('b', 64),
        at_time, at_time + interval '2 minutes',
        'CANDIDATE_SIGNATURE_INSPECTED_NOT_OWNER_AUTHENTICATED'
    );
    IF (SELECT count(*) FROM enterprise_skill_candidate_proof_nonces
            WHERE nonce_hex = repeat('a',32)) <> 1 THEN
        RAISE EXCEPTION 'E39G6_EXPECTED_ONLY_ONE_PERSISTED_NONCE';
    END IF;

    BEGIN
        INSERT INTO enterprise_skill_candidate_proof_nonces (
            nonce_hex, task_id, agent_id, skill_id, proof_sha256,
            observed_at, proof_expires_at, evidence_state
        ) VALUES (
            repeat('a', 32), '45755360-1781-4dde-b12b-f5a48bb75cb0'::uuid,
            'frontend-engineer', 'animate', repeat('c', 64),
            at_time, at_time + interval '2 minutes',
            'CANDIDATE_SIGNATURE_INSPECTED_NOT_OWNER_AUTHENTICATED'
        );
        RAISE EXCEPTION 'E39G6_DUPLICATE_NONCE_WAS_ACCEPTED';
    EXCEPTION WHEN unique_violation THEN NULL; END;

    BEGIN
        INSERT INTO enterprise_skill_candidate_proof_nonces (
            nonce_hex, task_id, agent_id, skill_id, proof_sha256,
            observed_at, proof_expires_at, evidence_state
        ) VALUES (
            repeat('A',32), gen_random_uuid(), 'frontend-engineer',
            'animate', repeat('b',64), at_time, at_time + interval '2 minutes',
            'CANDIDATE_SIGNATURE_INSPECTED_NOT_OWNER_AUTHENTICATED'
        );
        RAISE EXCEPTION 'E39G6_UPPERCASE_NONCE_WAS_ACCEPTED';
    EXCEPTION WHEN check_violation THEN NULL; END;

    BEGIN
        INSERT INTO enterprise_skill_candidate_proof_nonces (
            nonce_hex, task_id, agent_id, skill_id, proof_sha256,
            observed_at, proof_expires_at, evidence_state
        ) VALUES (
            repeat('c',32), gen_random_uuid(), 'frontend/engineer',
            'animate', repeat('b',64), at_time, at_time + interval '2 minutes',
            'CANDIDATE_SIGNATURE_INSPECTED_NOT_OWNER_AUTHENTICATED'
        );
        RAISE EXCEPTION 'E39G6_UNSAFE_AGENT_ID_WAS_ACCEPTED';
    EXCEPTION WHEN check_violation THEN NULL; END;

    BEGIN
        INSERT INTO enterprise_skill_candidate_proof_nonces (
            nonce_hex, task_id, agent_id, skill_id, proof_sha256,
            observed_at, proof_expires_at, evidence_state
        ) VALUES (
            repeat('d',32), gen_random_uuid(), 'frontend-engineer',
            'animate', repeat('b',64), at_time, at_time + interval '2 minutes',
            'SKILL_OWNER_APPROVED'
        );
        RAISE EXCEPTION 'E39G6_POSITIVE_AUTHORITY_WAS_ACCEPTED';
    EXCEPTION WHEN check_violation THEN NULL; END;

    BEGIN
        INSERT INTO enterprise_skill_candidate_proof_nonces (
            nonce_hex, task_id, agent_id, skill_id, proof_sha256,
            observed_at, proof_expires_at, evidence_state
        ) VALUES (
            repeat('e',32), gen_random_uuid(), 'frontend-engineer',
            'animate', repeat('b',64), at_time, at_time - interval '1 second',
            'CANDIDATE_SIGNATURE_INSPECTED_NOT_OWNER_AUTHENTICATED'
        );
        RAISE EXCEPTION 'E39G6_EXPIRED_PROOF_WAS_ACCEPTED';
    EXCEPTION WHEN check_violation THEN NULL; END;

    BEGIN
        INSERT INTO enterprise_skill_candidate_proof_nonces (
            nonce_hex, task_id, agent_id, skill_id, proof_sha256,
            observed_at, proof_expires_at, evidence_state
        ) VALUES (
            repeat('f',32), gen_random_uuid(), 'frontend-engineer',
            'animate', repeat('b',64), at_time, at_time + interval '30 minutes',
            'CANDIDATE_SIGNATURE_INSPECTED_NOT_OWNER_AUTHENTICATED'
        );
        RAISE EXCEPTION 'E39G6_TOO_LONG_EXPIRY_WAS_ACCEPTED';
    EXCEPTION WHEN check_violation THEN NULL; END;

    BEGIN
        INSERT INTO enterprise_skill_candidate_proof_nonces (
            nonce_hex, task_id, agent_id, skill_id, proof_sha256,
            observed_at, proof_expires_at, evidence_state
        ) VALUES (
            repeat('1',32), gen_random_uuid(), 'frontend-engineer',
            'animate', repeat('Z',64), at_time, at_time + interval '2 minutes',
            'CANDIDATE_SIGNATURE_INSPECTED_NOT_OWNER_AUTHENTICATED'
        );
        RAISE EXCEPTION 'E39G6_INVALID_PROOF_DIGEST_WAS_ACCEPTED';
    EXCEPTION WHEN check_violation THEN NULL; END;
    RAISE NOTICE 'E39G6_POSTGRES_REQUIRED_CONSTRAINTS_AND_GLOBAL_NONCE_PK_PASS';
END $$;
ROLLBACK;

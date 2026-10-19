\set ON_ERROR_STOP on
-- Run after V1 + V2 migrations, Spring JPA test, and V1 SQL adversarial tests.
INSERT INTO enterprise_design_review_intents
 (id,project_id,company_id,division_id,brief_digest,storyboard_digest,
  manifest_digest,issued_at,expires_at,status)
VALUES
 ('00000000-0000-4000-8000-000000000010',
  'cinematic-laptop','aetheris-immersive','scroll-experience-engineering',
  repeat('a',64),repeat('b',64),repeat('c',64),
  '2026-10-19T09:00:00Z','2026-10-19T09:15:00Z',
  'PENDING_OWNER_AUTHENTICATION_UNAVAILABLE'),
 ('00000000-0000-4000-8000-000000000011',
  'different-project','aetheris-software','web-frontend-engineering',
  repeat('a',64),repeat('b',64),repeat('c',64),
  '2026-10-19T09:00:00Z','2026-10-19T09:15:00Z',
  'PENDING_OWNER_AUTHENTICATION_UNAVAILABLE');

INSERT INTO enterprise_review_proof_nonces
 (nonce_hex,intent_id,project_id,proof_sha256,observed_at,proof_expires_at,evidence_state)
VALUES (repeat('1',32),'00000000-0000-4000-8000-000000000010',
 'cinematic-laptop',repeat('a',64),
 '2026-10-19T09:01:00Z','2026-10-19T09:03:00Z',
 'SIGNED_BYTES_SEEN_OWNER_UNVERIFIED');

DO $test$
BEGIN
  -- Global replay rejection, even when attacker targets a DIFFERENT intent.
  BEGIN
    INSERT INTO enterprise_review_proof_nonces
      (nonce_hex,intent_id,project_id,proof_sha256,observed_at,proof_expires_at,evidence_state)
    VALUES (repeat('1',32),'00000000-0000-4000-8000-000000000011',
       'different-project',repeat('b',64),
       '2026-10-19T09:01:00Z','2026-10-19T09:03:00Z',
       'SIGNED_BYTES_SEEN_OWNER_UNVERIFIED');
    RAISE EXCEPTION 'replayed nonce accepted';
  EXCEPTION WHEN unique_violation THEN NULL;
  END;
  BEGIN
    INSERT INTO enterprise_review_proof_nonces
      (nonce_hex,intent_id,project_id,proof_sha256,observed_at,proof_expires_at,evidence_state)
    VALUES (repeat('2',32),'00000000-0000-4000-8000-000000000099',
       'different-project',repeat('b',64),
       '2026-10-19T09:01:00Z','2026-10-19T09:03:00Z',
       'SIGNED_BYTES_SEEN_OWNER_UNVERIFIED');
    RAISE EXCEPTION 'orphan nonce accepted';
  EXCEPTION WHEN foreign_key_violation THEN NULL;
  END;
  BEGIN
    INSERT INTO enterprise_review_proof_nonces
      (nonce_hex,intent_id,project_id,proof_sha256,observed_at,proof_expires_at,evidence_state)
    VALUES (repeat('3',32),'00000000-0000-4000-8000-000000000010',
       'cinematic-laptop',repeat('B',64),
       '2026-10-19T09:01:00Z','2026-10-19T09:03:00Z',
       'SIGNED_BYTES_SEEN_OWNER_UNVERIFIED');
    RAISE EXCEPTION 'uppercase nonce digest accepted';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
  BEGIN
    INSERT INTO enterprise_review_proof_nonces
      (nonce_hex,intent_id,project_id,proof_sha256,observed_at,proof_expires_at,evidence_state)
    VALUES (repeat('4',32),'00000000-0000-4000-8000-000000000010',
       'cinematic-laptop',repeat('a',64),
       '2026-10-19T09:03:00Z','2026-10-19T09:01:00Z',
       'SIGNED_BYTES_SEEN_OWNER_UNVERIFIED');
    RAISE EXCEPTION 'expired nonce record accepted';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
  BEGIN
    INSERT INTO enterprise_review_proof_nonces
      (nonce_hex,intent_id,project_id,proof_sha256,observed_at,proof_expires_at,evidence_state)
    VALUES (repeat('5',32),'00000000-0000-4000-8000-000000000010',
       'cinematic-laptop',repeat('a',64),
       '2026-10-19T09:01:00Z','2026-10-19T09:03:00Z','APPROVED');
    RAISE EXCEPTION 'fake approval accepted';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
  IF (SELECT count(*) FROM enterprise_review_proof_nonces) <> 1 THEN
    RAISE EXCEPTION 'unexpected nonce insert persisted despite constraint rejection';
  END IF;
  IF EXISTS (
    SELECT 1 FROM pg_class c CROSS JOIN LATERAL
      aclexplode(coalesce(c.relacl,acldefault('r',c.relowner))) acl
    WHERE c.oid='enterprise_review_proof_nonces'::regclass
      AND acl.grantee=0 AND acl.privilege_type IN ('UPDATE','DELETE','TRUNCATE')
  ) THEN
    RAISE EXCEPTION 'PUBLIC has forbidden proof-evidence mutation privileges';
  END IF;
END
$test$;
\echo E22C_POSTGRES_GLOBAL_REPLAY_AND_DENY_ONLY_CONSTRAINTS_PASS

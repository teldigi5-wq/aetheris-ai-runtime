\set ON_ERROR_STOP on
-- Real PostgreSQL integrity and denial-only acceptance tests.
INSERT INTO enterprise_design_review_intents
  (id, project_id, company_id, division_id, brief_digest,
   storyboard_digest, manifest_digest, issued_at, expires_at, status)
VALUES
  ('00000000-0000-4000-8000-000000000001',
   'cinematic-laptop', 'aetheris-immersive', 'scroll-experience-engineering',
   repeat('a',64), repeat('b',64), repeat('c',64),
   '2026-10-19 00:00:00+00', '2026-10-19 00:15:00+00',
   'PENDING_OWNER_AUTHENTICATION_UNAVAILABLE');

INSERT INTO enterprise_design_review_denials (intent_id, reason, occurred_at)
VALUES ('00000000-0000-4000-8000-000000000001',
        'E22B_OWNER_AUTHENTICATION_NOT_CONNECTED', '2026-10-19 00:01:00+00');

DO $e22b$
BEGIN
  -- No-positive-approval invariant enforced by DB, not the model.
  BEGIN
    UPDATE enterprise_design_review_intents
      SET status='APPROVED' WHERE id='00000000-0000-4000-8000-000000000001';
    RAISE EXCEPTION 'approved status unexpectedly accepted';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
  BEGIN
    INSERT INTO enterprise_design_review_intents
      (id, project_id, company_id, division_id, brief_digest,
       storyboard_digest, manifest_digest, issued_at, expires_at, status)
    VALUES ('00000000-0000-4000-8000-000000000002',
      'cinematic-laptop','aetheris-immersive','scroll-experience-engineering',
      repeat('A',64),repeat('b',64),repeat('c',64),
      '2026-10-19 00:00:00+00','2026-10-19 00:15:00+00',
      'PENDING_OWNER_AUTHENTICATION_UNAVAILABLE');
    RAISE EXCEPTION 'invalid upper-case hash unexpectedly accepted';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
  BEGIN
    INSERT INTO enterprise_design_review_intents
      (id, project_id, company_id, division_id, brief_digest,
       storyboard_digest, manifest_digest, issued_at, expires_at, status)
    VALUES ('00000000-0000-4000-8000-000000000003',
      'cinematic-laptop','aetheris-immersive','scroll-experience-engineering',
      repeat('a',64),repeat('b',64),repeat('c',64),
      '2026-10-19 00:00:00+00','2026-10-19 00:14:59+00',
      'PENDING_OWNER_AUTHENTICATION_UNAVAILABLE');
    RAISE EXCEPTION 'non-15-minute expiry unexpectedly accepted';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
  BEGIN
    INSERT INTO enterprise_design_review_denials
      (intent_id,reason,occurred_at)
    VALUES ('00000000-0000-4000-8000-000000000099',
            'E22B_OWNER_AUTHENTICATION_NOT_CONNECTED','2026-10-19 00:01:00+00');
    RAISE EXCEPTION 'orphan denial unexpectedly accepted';
  EXCEPTION WHEN foreign_key_violation THEN NULL;
  END;
  BEGIN
    INSERT INTO enterprise_design_review_denials
      (intent_id,reason,occurred_at)
    VALUES ('00000000-0000-4000-8000-000000000001',
            'APPROVED','2026-10-19 00:02:00+00');
    RAISE EXCEPTION 'unknown denial reason unexpectedly accepted';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
  IF (SELECT count(*) FROM enterprise_design_review_intents) <> 1 THEN
    RAISE EXCEPTION 'review-intent integrity test unexpectedly persisted bad data';
  END IF;
  IF (SELECT count(*) FROM enterprise_design_review_denials) <> 1 THEN
    RAISE EXCEPTION 'denial integrity test unexpectedly persisted bad data';
  END IF;
END
$e22b$;

-- A restricted app role cannot update/rewrite/delete audit evidence.
DO $e22b$
BEGIN
  -- PostgreSQL PUBLIC is ACL grantee OID 0, not a queryable role name.
  IF EXISTS (
    SELECT 1
    FROM pg_class c
    CROSS JOIN LATERAL aclexplode(
        coalesce(c.relacl, acldefault('r', c.relowner))) entry
    WHERE c.oid IN ('enterprise_design_review_intents'::regclass,
                    'enterprise_design_review_denials'::regclass)
      AND entry.grantee = 0
      AND entry.privilege_type IN ('UPDATE', 'DELETE', 'TRUNCATE')
  ) THEN
    RAISE EXCEPTION 'PUBLIC must not have audit mutation privileges';
  END IF;
END
$e22b$;
\echo E22B_POSTGRES_DENY_ONLY_SCHEMA_AND_CONSTRAINTS_VERIFIED

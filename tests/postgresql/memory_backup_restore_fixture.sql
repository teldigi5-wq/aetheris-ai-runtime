-- SYNTHETIC DATABASE ONLY. THESE ENVELOPES ARE STRUCTURAL PLACEHOLDERS,
-- NOT AUTHENTICATED CIPHERTEXT. Java AEAD tests verify the real cipher.
-- Only applied inside disposable GitHub Actions PostgreSQL source database.
CREATE TABLE public.memory_backup_fixture (
    id uuid PRIMARY KEY,
    owner_id varchar(120) NOT NULL,
    project_id varchar(160),
    payload text NOT NULL,
    encrypted boolean NOT NULL,
    sensitive boolean NOT NULL
);
INSERT INTO public.memory_backup_fixture
    (id, owner_id, project_id, payload, encrypted, sensitive)
VALUES
 ('00000000-0000-0000-0000-000000000101', 'synthetic-alice', 'project-a',
  'enc:v1:abcdefghijklmnop:FAKE_LEGACY_BLOB', true, true),
 ('00000000-0000-0000-0000-000000000102', 'synthetic-alice', 'project-a',
  'enc:v2:A1.testkey.abcdefghijklmnop.FAKE_TAG_NOT_VALID_CIPHERTEXT', true, true),
 ('00000000-0000-0000-0000-000000000103', 'synthetic-bob', 'project-b',
  'enc:v2:A1.oldkey.abcdefghijklmnop.FAKE_TAG_NOT_VALID_CIPHERTEXT', true, true),
 ('00000000-0000-0000-0000-000000000104', 'synthetic-bob', 'project-b',
  'synthetic plaintext leak sentinel', false, true);

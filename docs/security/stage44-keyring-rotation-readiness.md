# Stage 44 Slice 2 — Owner-bound memory keyring and rotation-readiness

**Status: DRAFT / lab-only. Production security gate CLOSED.**
This depends on Stage 44 draft PR #40 and Stage 43 draft PR #39.
No migration, production secret injection, automatic key retirement, key
rotation on user data, backup change, service activation or merge occurs.

## Verified code contract

New `OwnerBoundMemoryKeyring` selects the externally configured **active**
AES-256-GCM data key to encrypt new sensitive `aetheris_memory_records.payload`
rows. Versioned v2 envelopes preserve their key identifier:
`enc:v2:A1.<key-id>.<nonce>.<authenticated-ciphertext>`.
On read, the ring selects **only the matching ID**, authenticates the
record UUID, owner, project and scope using the Stage 44 AAD, and rejects
unknown IDs, bad ciphertext, altered contexts and keys. It never tries
every key as an insecure fallback and never silently returns ciphertext
as plaintext. Existing legacy `enc:v1:` rows still require their
separate legacy master key and an audited migration.

The ring supports one active and up to seven **retained decrypt-only**
keys. The existing 5-argument memory-encryption constructor stays
compatible with PR #40; the new 6-argument constructor and Spring config
accept the retention set. Invalid, duplicate, repeated material, malformed,
missing or oversized key configurations fail at startup. Closing the
ring disables its AEAD operations and erases the Java-owned
`AeadFieldProtector` key copies on a best-effort basis. JVM and
cryptographic-provider memory wiping is not guaranteed.

## Development configuration (not production key custody)

`AETHERIS_MEMORY_OWNER_BOUND_AEAD_ENABLED=false` remains the default.
In an isolated throwaway test environment **only**, explicitly enable it
and supply:

- `AETHERIS_MEMORY_OWNER_BOUND_KEY_ID`: active key ID (1–24 ASCII
  letters, digits, underscores or dashes).
- `AETHERIS_MEMORY_OWNER_BOUND_KEY_B64`: 32-byte independently
  generated active AES data key, encoded as standard Base64.
- `AETHERIS_MEMORY_OWNER_BOUND_PREVIOUS_KEYS`: optional entries
  `old-id:BASE64,older-id:BASE64`; exactly 32 bytes per distinct old
  key, no duplicate IDs or material, maximum eight total keys.

All key values must come from an independent secret manager/process,
not this repository, ChatGPT, committed configs, CI output or web clients.
An environment variable is a **transitional test input**, not a secure
production KMS: it can leak through privileged process introspection,
diagnostics or deployment mistakes. Never include real key values in
issue comments, screen recordings or logs.

## Safe rotation and recovery expectations

1. Independently generate a new active data key with unique ID.
2. Retain the old active key as a decrypt-only key; do not delete
   or overwrite it. Confirm key custody in separate encrypted, access-
   controlled backup/recovery storage before changing any config.
3. After separate deployment security review, a test runtime can
   write with the new active key while still reading older v2 envelopes.
   Existing records are **not** rewritten by this feature.
4. Removing any retained key renders records using it unreadable.
   The service does not scan the database at startup to prove that a
   key can be retired. Never assume deleting the key is safe.
5. A future audited migrator must inventory encrypted rows, write an
   idempotent migration journal, bind immutable owner/project context,
   re-encrypt in transactions with a version/CAS guard, verify backups,
   restore and roll back from a snapshot; then prove no row/backups/
   replicas require retired keys.
6. Recovery testing must cover missing/wrong key, unavailable external
   manager, interrupted migration, corrupt/tag-tampered records,
   restore in a freshly isolated environment and access revocation.

## Explicit limitations

Only sensitive durable-memory **payloads** are covered by this slice.
Memory keys/tags/provenance, all other JPA columns, indexes, embeddings,
WAL, snapshots, replicas, caches, logs, telemetry, agent prompts,
Windows local memory and network transmissions need separate protection.
The legacy v1 format lacks owner/record AAD, and this slice does not
remove that weakness. There is no production-grade external KMS provider,
envelope per-tenant key hierarchy, automatic key retirement, SQL migration,
certified backup/restore, physical-PC validation or real threat-response
service. The exposed application key ID is not a secret.

**Prohibition:** Do not enable this in deployed services, merge protected
branches, or handle actual owner memory during development.

Tests: `OwnerBoundMemoryKeyringTest` proves previous-key read continuity,
new-key-only writes, wrong-key-ID rejection, malformed startup config,
duplicate material rejection, closed-keyring fail-closed and integration
through `MemoryEncryptionService`; full Phase 9 and Stage 44 regression
remains mandatory. Hosted CI success is development evidence, not
independent production certification.

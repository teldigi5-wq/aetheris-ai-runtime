# Stage 44 (slice 1) — Owner-bound durable-memory AEAD

**Status: DRAFT SECURITY PILOT; default OFF; NOT a complete at-rest
encryption rollout, migration, rotation or production certification.**

This depends on Stage 43 draft PR #39. The new implementation uses the
reviewed `AeadFieldProtector` for new *sensitive* `aetheris_memory_records.payload`
writes, with a fresh random AES-256-GCM nonce per encryption. AAD uses
length-prefixed, version/domain-separated record UUID, owner ID, memory
scope and project ID. Reading a v2 envelope requires the original row
identity; swapping encrypted payloads between rows, owners, projects
or scopes fails authentication. The durable-memory CRUD service supplies
the row context on both writes and reads, and JPA tests verify the actual
persisted column does not contain sensitive plaintext.

## Explicit isolated pilot only

- `AETHERIS_MEMORY_OWNER_BOUND_AEAD_ENABLED=false` is the safe
  default; **no existing deployments or records change** just by merging.
- For a disposable, locally secured test database only, provision
  `AETHERIS_MEMORY_OWNER_BOUND_AEAD_ENABLED=true`,
  `AETHERIS_MEMORY_OWNER_BOUND_KEY_ID=<reviewed-id>` (1–24 safe
  ASCII characters), and `AETHERIS_MEMORY_OWNER_BOUND_KEY_B64`
  (externally generated exactly 32 random bytes, Base64-encoded).
  Do not use the illustrative unit-test keys in any real environment.
- The application fails startup when enabled without a valid external
  key or key ID. There is no default, self-generated, committed or
  recoverable fallback key. Key material MUST NOT be exposed to
  a frontend, logs, source control, build artifacts or agents.
- Older `enc:v1:` envelopes remain readable **only when** the
  separately provided `aetheris.memory.master-key-b64` legacy key is
  present. The old format does not authenticate owner/record context.
  It remains a risk until an independently reviewed migration.
- The key ID appears in each `enc:v2:A1.<key-id>.<nonce>.<ciphertext>`
  envelope, but this slice supports only one active v2 key. Changing it
  without first migrating/reencrypting every row would make old v2
  ciphertext inaccessible. **Do not rotate keys or enable production
  without a keyring and tested recovery process.**

## Not covered by this change

Non-sensitive memory remains plaintext by design; record metadata,
keys, tags, provenance, embeddings, SQL WAL, indexes, replicas,
database snapshots, backups, JPA debug logs, telemetry, external
agents/LLM requests and all other entity tables are **not protected
by this field-encryption slice**. It does not migrate existing rows.
The `encrypted` and `sensitive` flags are still application/JPA
metadata; malicious DB mutation or authorized model/tool disclosure
is outside the field-encryption guarantee. There is no tenant-isolated
key derivation, key custody/rotation manager or audit-certified KMS.

## Required next slices before activation

1. Inventory sensitive payloads, audit every memory API and establish
   owner/project immutable identity independent of caller input.
2. Build a versioned external-key-provider/keyring with authorization,
   separate legacy/v2 decrypt paths, revocation and offline recovery.
3. Stage a resumable, per-row audited migration from v1 to v2 with
   strict metadata and owner verification. Run it only on a snapshot
   with verified encrypted backups and rollback, NEVER on live owner
   data during development.
4. Add cross-account, replay, key rotation, crash-recovery, lost-key,
   backup/restore and PostgreSQL migration tests, plus review query
   indexes, retention and redaction of logs.
5. Complete independent threat modelling, Windows secure storage and
   deployment security review before any release.

Evidence: `OwnerBoundMemoryAeadTest` checks cross-owner/project/scope/
record swaps, tampering, missing key, legacy compatibility and close.
`OwnerBoundMemoryPersistenceIntegrationTest` exercises real Hibernate
JPA writes, same-owner row swaps and reading historic v1 envelopes.
`Phase9MemoryProductionIntegrationTest` provides legacy regression.
These are development security tests, not penetration-test certification.

**Production gate: CLOSED.** Do not merge, deploy, expose routes,
enable raw agent execution or change owner data as part of this PR.

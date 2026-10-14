# Stage 44.3 — Read-only encrypted-memory inventory (DRAFT)

**Release and migration gates CLOSED.** This is an implementation for a
future, manually approved inspection of an isolated PostgreSQL snapshot.
The code adds **no API route, CLI command, scheduler, startup trigger,
migration, database writer, automatic key change or data export**.
Current tests use synthetic fixtures and H2 only. No live owner data
has been inspected.

## Purpose and trust boundary

Before attempting the existing `enc:v1:` to authenticated `enc:v2:`
migration, determine the **header-only** distribution of stored
`aetheris_memory_records` payload formats and syntactically declared
key IDs. Never infer cryptographic validity from a prefix.

`MemoryEnvelopeInventoryService.inspect()` reads through a Spring
`@Transactional(readOnly=true, isolation=REPEATABLE_READ)` operation.
Its JPA repository query projects ONLY:
- ephemeral row UUID (pagination ordering only; never in reports);
- `encrypted` and `sensitive` flags;
- **up to 96 header characters of encrypted payloads only**, plus
  their ciphertext character lengths. For non-encrypted payloads,
  the SQL `CASE` projection returns `null` for both payload fields.

It does not request unencrypted memory text, names, owner/project
IDs, tags, provenance, raw encryption keys, original ciphertext,
authentication tags or full documents. Raw row-level data remains
internal to the ephemeral scan; the emitted report contains aggregate
counts and **syntactically constrained key IDs** only. Even key IDs
could be operationally sensitive metadata: restrict report recipients.

Read-only does not equate to authorization. The service is an internal
Spring bean without a route or external trigger. Connecting it later
requires independent local admin authorization, audited invocation,
no public web/agent path and access to an isolated test snapshot.

## Guardrails

- A hard ceiling of **5,000 rows** and **100 records per page**;
  pagination is ordered by immutable UUID within one intended snapshot.
  Reports mark `complete=false` when a further page is known to exist.
  Never interpret a partial report as a whole-DB inventory.
- Up to 16 distinct v2 header key IDs are reported; additional records
  increment `undisplayedKeyIdRecords`.
- Classification: `enc:v1:` legacy headers, syntactically parseable
  `enc:v2:A1.<key-id>.<nonce>.` headers, other/invalid encrypted headers,
  sensitive rows with `encrypted=false`, encrypted rows with
  `sensitive=false`, and other unencrypted rows.
- Malformed headers, inconsistent flags or v1 dependencies are
  migration blockers, never auto-repaired.
- Output status is **always** `INVENTORY_ONLY_NOT_MIGRATION_READY`.
  Even a complete, seemingly healthy report is not authorization for
  rotating/removing keys or migrating real user data.
- The code does **not** authenticate GCM tags, verify ownership,
  discover hidden ciphertext in rows marked unencrypted, validate
  actual key availability, verify restore capability, or certify the
  database transaction isolation on PostgreSQL.

## Required before a migration implementation

1. Owner-authorized offline PostgreSQL snapshot, immutable backup,
   separate encrypted restore test and strict no-production access.
2. PostgreSQL-specific isolation and read-only DB-role validation,
   measurable table size/resource ceilings and pagination stability.
3. Verified immutable record/owner/project identity and separate
   keyring-custody test for every key ID and legacy v1 key dependency.
4. A carefully reviewed dry-run planner with per-row **non-sensitive**
   migration state and explicit abort controls; no automatic
   re-encryption on application startup.
5. In a separate PR, a transactional, idempotent, resumable migrator
   that uses version/CAS semantics, verifies authenticated decrypted
   data without logging it, and has tested rollback from an encrypted
   backup. A change of `encrypted`/`sensitive` flags must not bypass
   owner policy or conceal plaintext.
6. Independent validation of WAL, replicas, caches, RAG indexes,
   backups, audit logs, Windows native secrets, endpoint auth and
   physical Windows workstation before deployment.

### Test evidence

- `MemoryEnvelopeInventoryServiceTest`: count classification,
  no repo writer invocations, 5,000-row partial-scan handling,
  bounded key-ID disclosure.
- `MemoryEnvelopeInventoryJpaIntegrationTest`: real Spring JPA
  projection with wholly synthetic encrypted and unencrypted
  records, unchanged raw JPA rows and no owner/content disclosures.
- All previous Stage 43/44 security suites and the complete
  orchestrator regression must pass at the exact PR HEAD.

**No key-retirement clearance or certification can be derived from
this document or the inventory report alone.**

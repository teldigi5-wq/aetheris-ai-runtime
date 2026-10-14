# Durable owner-specific delivery acknowledgements (review-only)

**Activation:** `BLOCKED_OWNER_AUTH_NOT_VERIFIED`. No live HTTP, SSE,
desktop IPC, background scheduler or external agent actions added.

This draft prepares authenticated reconnect/resume for Syntra Mission
Control without equating an untrusted client acknowledgement with actual
desktop receipt or successful task execution.

## Authenticated state and isolation

`VerifiedOwnerDeliveryCheckpoint` is deliberately **not a Spring
component/controller**. A future audited server adapter must supply a
genuinely signed identity JWT and keep the owner-sensitive HTTP routes
quarantined until full security review.

- `open(jwt, task)` verifies JWT HS256, `iss`, `aud`,
  `orchestrator:read`, token expiration and numeric account ID, then
  checks persisted task ownership. It mints a random checkpoint UUID
  server-side and writes `owner_user_id`, `task_id`, last sequence
  (initially zero), last acknowledged event UUID and timestamps.
- `resume(jwt, checkpoint, pageSize)` **revalidates the signed JWT**,
  durable checkpoint ownership and current task ownership, and returns
  at most 100 sequenced journal updates with one extra record for
  `hasMore`. It uses the DB commit sequence, not event timestamp.
- `acknowledge(jwt, checkpoint, eventUUID)` checks JWT, durable owner,
  task owner, and that the event belongs to exactly that task. It holds
  a **PESSIMISTIC_WRITE checkpoint row lock in a new transaction**.
  Only the next exact sequence is accepted; a repeated ACK of the same
  last event is idempotent. Jump-ahead, older, foreign-task and unknown
  event IDs cannot skip pending work.

The UI-facing event projection includes event ID, task ID, commit
sequence, event timestamp, state, validated agent ID and
`PERSISTED_JOURNAL_NOT_VERIFIED_OUTCOME`. No private event message,
metadata, raw command, account email, JWT or token is loaded into
the response.

Checkpoint statuses explicitly use
`CLIENT_DECLARED_ACK_NOT_ATTESTED` and
`OUTBOX_SEQUENCED_ONLY_ACK_NOT_ATTESTED`. This ensures that an ACK
only records the caller's declaration. It is NOT cryptographic proof
that the desktop rendered the update, that a person saw it, or that
the agent performed the work.

## Limits

- Checkpoints cover **new controlled outbox-sequenced journal events**.
  Legacy/unsequenced history is excluded rather than made up.
- Checkpoint IDs are random selectors, **not credentials**. A verified
  account still needs task ownership for every operation.
- There is no trusted device registration/session identity, checkpoint
  expiration policy, global retention/garbage collection, durable client
  receipt signature, native desktop vault binding, or production SSE.
- A disconnected client can resume from its last *declared* position;
  an event acknowledged before actual rendering may be skipped by that
  client after a crash. Runtime duplicate delivery can occur before ACK,
  and the client must deduplicate using event UUIDs.
- Concurrent resumption may re-read events; bounded idempotent ACKs
  prevent backward progress. An owner with access to a known event UUID
  could acknowledge it without actual delivery; this is an intentional
  non-attested receipt model pending trusted client protocol design.

## Certification and migration

`docs/security/sql/postgres-owner-delivery-checkpoint-v2.sql` is a
**review-only** additive migration that must follow the prior v1 outbox
migration. PostgreSQL 16 CI runs two identical migration applications
on disposable legacy database fixtures, checks owner/sequence
constraints and verifies the table has no private fields.

`VerifiedOwnerDeliveryCheckpointTest` runs on PostgreSQL 16 and H2
to exercise persisted replay across new service instances, concurrent
ACK locks, idempotent last ACK, out-of-order skips, two signed accounts,
foreign event IDs, wrong JWT recipients, legacy/project refusal,
malformed agent IDs and privacy. No production data is accessed.

Remaining blockers: staging schema migration on a restored full
database, distributed revocation and credential rotations, server-side
authenticated HTTP request principals, owner-filtered SSE/mission
subscriptions across all indirect routes, trusted native desktop
integration, user-visible delivery evidence, failover/retention testing,
and physical owner Windows PC certification.

Protected `main`, production and installed runtime remain unchanged.
Security issue #23 remains open.

# Runtime-verifiable account identity — isolated preparatory implementation

**Status:** `SOURCE_VERIFIED_NOT_WIRED` (a real signature verifier with synthetic
JUnit tests; **NOT** an authenticated HTTP controller or SSE subscriber).

The platform identity issuer and gateway baseline at platform source
`57ad3e0e602efa6feb275328254151b959a3499b` use JJWT **0.12.6** with
HMAC-SHA256 for 32-byte signing keys. Access tokens include:

- `sub`: account email
- `uid`: **numeric IdentityAccount.id / Java Long** (not UUID)
- `role`: `ADMIN`, `DEVELOPER` or `API_CONSUMER`
- `scopes`: list of strings including `orchestrator:read` for reads,
  `orchestrator:write` for modifying operations
- `iss`: `aetheris-identity` **required by this stacked source contract**
- `aud`: `aetheris-api` **required by this stacked source contract**
- `iat` and `exp`

A previous draft used owner UUIDs; **task and mission owner IDs were corrected
to Long/BIGINT on PR #25 before building this verifier**. Project IDs remain
UUIDs. Repository queries and owner policies use these native types, and
legacy ownerless records remain inaccessible to authenticated owner readers.

## Implementation

`RuntimeAccessTokenVerifier` is an isolated native-Java class (no
`@Component`, Tauri command, servlet filter or HTTP endpoint). It must
receive an **explicit, private, nondefault JWT signing secret**; it does not
read forged `X-Aetheris-*` identity headers or fetch keys from HTTP. It
verifies JWS signature, issuer, API audience and algorithm, expiration/issued-at times, a positive
numeric UID, valid role, bounded scope strings and an exact required
orchestrator read/write scope. It returns a small in-process
`VerifiedPrincipal` with numeric account ID and scoped permissions — never
the raw token. Invalid tokens return a coarse non-logging rejection.

Synthetic JUnit tests check two distinct signed identities, token tampering,
wrong signing key, missing/incorrect claims, unsigned input, expired/future
tokens, wrong scope, weak/default secret and fail-closed HTTP quarantine.

## Signed owner-scoped task status reader (stacked, still internal)

`VerifiedOwnerTaskRead` combines the independently signed
`RuntimeAccessTokenVerifier` with JPA task owner lookups. It is an
**unregistered Java component, not a Spring controller**. Before returning a
task it checks a fresh JWT signature/issuer/audience/read scope and compares
the immutable numeric owner from the database to that verified token's
`IdentityAccount.id`.

Until project-membership identity is cryptographically bound and audited,
only **owner-global tasks with `owner_project_id IS NULL`** can be returned.
Project-scoped tasks are denied, even to a user with the correct owner ID.
Legacy ownerless and other-user rows remain inaccessible. `recent()` uses
the owner/global-project indexed repository query and caps results at 50.
A cross-account request and missing ID both return the same coarse
`TASK_NOT_AVAILABLE` message (not a leaky owner/ID explanation).

The returned `TaskStatusView` exposes only task ID, state, agent ID and
update timestamp; private `commandText`, task messages, attachment content,
email, account ID and ownership metadata are never serialized into the DTO.
Database-backed tests use two synthetic signed account IDs, other-project
records and ownerless legacy tasks to check both list and lookup isolation,
including wrong audience, missing read scope and malformed tokens.

**Still blocked:** No new HTTP endpoint, servlet principal context, live SSE
subscription, Tauri credential bridge, host action, agent execution or
release activation. Existing global/task/mission route quarantine remains
active. This deliberately provides a proven building block, not a claim that
the Syntra desktop can read live user tasks.

## Signed owner-scoped task event timeline (stacked and offline-only)

`VerifiedOwnerTaskTimeline` is an independent Java read component combining
the real `TaskRepository`, persisted `TaskEventJournalService` and signed
`RuntimeAccessTokenVerifier`. It checks the signed access token's
`orchestrator:read` scope, issuer/audience, expiration and numeric account ID
**before querying any task's journal**. It then requires exact equality of
the stored task owner to the verified account ID. Only owner-global
(`owner_project_id IS NULL`) resources are eligible, as verified
project membership has not been implemented.

Two-account JPA tests verify that owner A cannot list owner B's event
history, even with a known task UUID. Ownerless legacy rows, project-bound
tasks, missing IDs, invalid JWTs, wrong audience and missing read scope
are all denied. Histories are capped to the last **100 entries**, and every
entry is projected to only task UUID, timestamp, state, validated agent ID
and explicit `provenance` label. Raw event messages, metadata, private
commands, tokens, account emails and task ownership details are never in
the returned DTO. Events with malformed agent identifiers discard the
identifier.

`PERSISTED_JOURNAL_NOT_VERIFIED_OUTCOME` means that an event was stored
in the journal, **not** that a real-world tool action was proven successful.
`UNTRUSTED_EVENT_ARGUMENT` is attached to externally supplied event
candidates. Neither type may be promoted into a claim of verified execution
without separate evidence or authorization.

No new HTTP route, SSE subscription, background agent execution, Windows
desktop IPC permission, trusted speaker control or production authentication
activation is included here. The global event feed and task/mission HTTP
endpoints remain quarantined by #24. This code is a reviewable foundation
for a future native authenticated read-only event stream after verified
owner/project routing, protection from direct-port access and reconnect/
revocation checks.

## Prepared owner-filtered journal event relay (internal only)

`VerifiedOwnerTaskEventRelay` is a new **unregistered, non-HTTP** Java
component for a future governed live-delivery adapter. It does not
subscribe to `TaskEventStreamService.subscribeAll()`, register a servlet
route or expose native desktop credentials. The previously identified
ownerless aggregate SSE broadcast remains quarantined by runtime #24.

On subscription, the relay checks an independently verified HS256 JWT
signature, expected issuer and audience, positive numeric owner ID, access
expiry and `orchestrator:read` scope, then fetches the selected task and
checks the stored task owner **before** registering a callback. Legacy
ownerless, other-account and project-scoped tasks are all refused.
The lease retains only the verified owner ID, selected task ID,
expiration, last-seen event time and a trusted internal callback; it does
**not retain the raw token**.

The caller-facing `onJournalChanged(taskId)` method accepts only a
task UUID, never a raw event, message or metadata. It reads the existing
journal itself, rechecks that the task exists and belongs to the leased
owner, and delivers only minimal `taskId/timestamp/state/agentId`
projections with `PERSISTED_JOURNAL_NOT_VERIFIED_OUTCOME` provenance.
A journal state is **not proof** that work happened or was verified.
The relay does not itself check cryptographic event signatures or evidence
attestations. Callback failures close subscriptions without exposing
exception messages. No replay occurs on a fresh subscription; prior
history remains available only from the separate signed history reader.

Boundaries: **32 total subscriptions per relay instance**, a **60-second
maximum lease**, and **at most 100 events per dispatch** from a bounded
200-event journal lookup. Expired leases, deleted/foreign tasks and
explicit owner revocation stop delivery. This in-memory design is
not cluster-ready and offers no persistence, distributed revocation,
connection resumption or guarantee of lossless delivery.

Two-account DB/JWT tests cover foreign-owner/project denial, expired
leases, explicit owner revocation, private-field redaction, invalid agent
IDs, duplicate replay suppression, no historical replay on subscribe,
subscriber capacity, task deletion and rejected callback exceptions.

### Requirements to activate future production live SSE

- Replace the unsafe global stream and direct task paths with
  server-verified owner/project subscription policies, including all
  indirect mission/approval/event routes. Current project membership
  remains unverified, so project-bound reads stay blocked.
- Bind the event relay only to a trusted **post-commit** journal
  notification; otherwise uncommitted/rolled-back task events could
  be announced. Test ordering/reconnect boundaries and stable event IDs.
- Add HTTPS/mTLS or independently verified runtime HTTP principals,
  trusted gateway-to-runtime access, logout/token revocation propagation,
  globally bounded subscriber budgets and multi-instance routing.
- Use an audited native owner-only client session before exposing a
  streaming route. Test two real authenticated identities, foreign task
  IDs, token replay/expiry, direct runtime port and app-background stops.
- Do not call this class a live SSE feature until actual authorized
  source-to-client delivery and PC hardware tests are complete.

## Committed-event notification boundary — isolated staging slice

`OwnerTaskPostCommitGate` and `VerifiedOwnerPostCommitJournalBridge` are
**standalone, non-Spring-registered Java classes**. They prepare an internal
handoff from the real task journal to the owner-filtered event relay. They
do not expose a new API, activate the client, or alter existing task
publication.

After an internal, trusted caller has appended a journal record **in the
same active Spring transaction**, it may stage only a task UUID. The gate
registers a Spring `TransactionSynchronization`, coalesces duplicate task
IDs within the current transaction, caps pending task IDs at **128**, and
notifies its callback **only** if the transaction completes with
`STATUS_COMMITTED`. Invalid IDs, no actual transaction, rollback and
capacity overflow do not produce callbacks. No messages, metadata,
credentials, raw JWTs or source events are passed to the callback.

The bridge runs the next owner-relay read in a separate
`PROPAGATION_REQUIRES_NEW`, read-only database transaction after the
original transaction commits. This is essential: the relay must query
**committed** journal and task rows, not perform owner checks against a
transaction that could still roll back. The existing relay independently
checks the stored task owner again and sanitizes the event output.

Real `SpringBootTest` + `TransactionTemplate` + JPA/H2 tests prove:
- No subscriber sees event updates before commit;
- A rolled-back journal append triggers no notification and leaves no
  persisted history row;
- Two distinct signed owners receive only their own committed updates;
- Duplicate task IDs in one transaction result in a single callback,
  while multiple events for the same task still appear correctly;
- Calls outside an actual transaction and null task IDs fail closed;
- A per-transaction 128-task cap is enforced and cleared after completion;
- Callback failures cannot turn a committed journal write into a false
  caller-observed rollback, and callback exceptions disclose no secrets.

**Remaining boundary:** This is not a durable message outbox. A process
crash between commit and callback (or an unavailable callback) can lose
notifications. No replay guarantee, cluster delivery, dedup across
restarts or external owner revocation integration is claimed. Before
production, implement a durable outbox or recovery/replay proof, review
callback backpressure and post-commit thread handling, and certify
transaction isolation across two real accounts and multiple runtime
instances.

Importantly, the current `TaskEventStreamService.publish()` still has
its pre-existing unscoped event delivery path. We did **not** modify
that service or wire the relay into it, because the unsafe external
`/api/orchestrator/tasks*`, `/missions*` and `/live*` endpoints remain
quarantined by PR #24. The new components cannot be activated until a
separately reviewed owner-safe publisher and transport are in place.
No physical owner-PC or production data tests were performed.

## Durable journal outbox (database recovery foundation, NOT LIVE)

The standalone `VerifiedOwnerTaskJournalOutbox` builds on the commit-only
work from PR #31. Its durable JPA table
`aetheris_task_journal_outbox` saves **only** a journal-event UUID, task UUID,
outbox UUID, status, timestamps, bounded attempts, an opaque lease nonce
and version for concurrency control. Raw event messages, model outputs,
private commands, owner email, credentials and metadata are **not**
stored in the outbox table.

`TaskEventJournalService.appendReturningId` is a source-compatible
addition that returns the exact persisted journal-event UUID as a stable
idempotency key; its existing `append` callers still work unchanged.
`appendInCurrentTransaction` *requires* an existing active Spring
transaction and rejects ownerless/project-scoped tasks. A reviewed internal
caller can write the journal event plus its durable outbox row in that
same transaction. Rollback removes **both**, while successful commit leaves
a pending record that survives an application-worker restart.

A manual worker (not registered or scheduled) claims one eligible row
under a database **pessimistic row lock**, commits the claim, then may
perform a narrowly scoped internal callback with **only the task UUID**.
Each claim has a fresh nonce, a **30-second lease**, and a maximum of five
attempts. An expired in-flight claim can be reclaimed by a new worker
instance; a stale former claim cannot acknowledge an event after nonce
rotation. Callback errors cause bounded backoff (up to 60 seconds),
followed by a `PARKED` state for manual review. Successful callback
invocation is recorded as `HANDOFF_ATTEMPTED`, **not** evidence that any
desktop client, SSE connection or external agent received an event.
There is no always-running worker, new HTTP route or owner-authorization
shortcut.

Real H2/JPA + Spring transaction tests exercise:
- Atomic journal and outbox commit, and no committed rows after rollback;
- Refusal of no-transaction/ownerless/project-scoped journal staging;
- Resume/reclaim after constructing a new worker object with the same DB;
- Lease-nonce fencing against stale acknowledgements;
- Bounded callback-error retries, exponential backoff and dead-letter parking;
- Isolated updates through the already-tested signed two-owner task relay;
- Private payload exclusion from the outbox row and projected updates.

### Remaining production constraints

This is **NOT** a complete enterprise outbox dispatch service. Database
migrations, PostgreSQL claim/lock contention tests, parallel-worker race
tests, an explicit durable-outbox health dashboard, parked-item review,
batch processing, and bounded polling/backpressure must be reviewed and
validated before deployment. A crash *after callback but before ack* may
repeat an internal handoff: delivery is **at-least-once attempted**, not
exactly once. The existing relay's timestamp-based cursor and 200-event
journal limit may still miss identical-timestamp events or long offline
periods; add journal-UUID cursors and audited pagination/replay before
claiming reliable per-event end-to-end history.

The old `TaskEventStreamService.publish` still directly sends to an
unfiltered shared SSE broadcaster. This new outbox is intentionally **not
wired** to that path. Its row statuses are not task-work verification
evidence. Any activation requires cryptographically verified HTTP
principals, signed owner-scoped routes, project authorization, migration,
multi-instance delivery tests, token expiry/logout propagation and the
owner's actual Windows-PC validation. Protected main remains unchanged.

## Stable journal-event-ID cursor for bounded owner-only catch-up

`VerifiedOwnerJournalCursor` is an **isolated, non-HTTP Java reader** for
owner-scoped task history pagination. It verifies the signed JWT signature,
`iss`/`aud`, current `orchestrator:read` scope and numeric account identity,
then checks the durable `TaskEntity.ownerUserId` **before** querying task
journal rows. Project-scoped and legacy ownerless tasks remain denied.

Existing `TaskEventJournalService.history()` still deliberately returns
only the latest 200 records. The new reader uses a **database keyset query**
ordered by (`eventAt`, persisted journal UUID), fetching at most
`requestedLimit+1` rows, with `1 <= requestedLimit <= 100`.
The cursor is an *event UUID* supplied by the previous page. Before using
it, the server resolves that event ID **inside the already authorized task**
and derives its timestamp from the database: callers cannot provide a
fabricated event timestamp or use another task's cursor.

The query selects into a JPA **projection** containing only journal event
UUID, task UUID, timestamp, state and agent ID. It never retrieves raw
message or serialized metadata on this path. Returned updates sanitize
agent IDs and label recorded states with
`PERSISTED_JOURNAL_NOT_VERIFIED_OUTCOME`. Cursor UUIDs grant no access;
owner authorization is rechecked on **every page**. The new composite
`task_id,event_at,id` index is a proposed schema/migration addition,
**not an applied production migration**.

Dedicated H2/JPA tests recover **275 historical events** beyond the legacy
200-entry window, paginate 24 events sharing exactly the same timestamp
without collapsing them, reject foreign/unknown event cursors, validate
two independently signed accounts, exclude project/ownerless records,
enforce size bounds and prove no private messages/metadata appear in
the response. Full orchestrator regression remains mandatory.

### Explicit remaining correctness limitations

The ordering key is the event's `eventAt`, **not an append-only database
commit sequence**. If a *new* record is inserted later with a backdated
timestamp (or with the same timestamp and a lower UUID than the previous
cursor), keyset continuation can miss it. The cursor also does not encode
a snapshot high-water mark or durable client acknowledgement. Therefore
this stage cannot claim **lossless, exact-once** live replay or
multi-instance SSE delivery. Before production, introduce a committed,
monotonic database sequence (or a transactionally ordered per-task
log/outbox key), snapshot/watermark semantics, consumer acknowledgements,
and PostgreSQL concurrency/retention/migration testing.

No HTTP route, scheduler, ownerless global SSE subscription, native client
authentication or Windows-PC integration was activated in this slice.
The server-side owner-sensitive API quarantine from PR #24 remains
intact, and issue #23 remains open.

## Per-task transaction-serialized journal ordering (stacked draft)

This slice adds an **opt-in per-task commit-serialized sequence** for *new*
journal events written through `VerifiedOwnerTaskJournalOutbox.appendInCurrentTransaction`.
It addresses the earlier timestamp/UUID cursor weakness for that **specific**
controlled writer path. It does not silently rewrite or backfill legacy
journal rows.

- `TaskEntity.lastJournalSequence` is nullable for old task rows. The new
  writer loads the *existing* task with `PESSIMISTIC_WRITE` and keeps the
  database row lock until the caller's outer transaction commits/rolls back.
  It allocates the next positive sequence inside that transaction; journal
  event, outbox record and counter mutation commit or roll back **together**.
  Two transactions writing the **same task** cannot commit assigned sequence
  `N+1` while `N` remains uncommitted, because the second transaction
  must first acquire that task's database row lock.
- `TaskEventJournalEntity.commitSequence` is nullable. Only the controlled
  writer populates it. A unique composite index on
  `(task_id,commit_sequence)` prevents duplicates; existing
  `TaskEventJournalService.append()` still creates legacy unsequenced rows.
- `VerifiedOwnerCommitSequenceCursor` is a new **unregistered, non-HTTP**
  read-only Java component. It verifies the signed owner JWT/role scope and
  persisted owner ID *before* accessing journal data. It reads only
  `commitSequence > lastVerifiedEventSequence` with page size 1..100,
  ordered by sequence, and validates that every supplied cursor event UUID
  belongs to the **same** already-authorized task.
- The JPA projection deliberately does not fetch event message/metadata,
  commands, credentials or private owner details. Invalid agent identifiers
  are redacted and events are labeled
  `PERSISTED_JOURNAL_NOT_VERIFIED_OUTCOME`. Responses explicitly say
  `OUTBOX_SEQUENCED_ONLY_LEGACY_NOT_INCLUDED`.

Tests exercise same-task concurrent Spring/H2 transactions under the row
lock, backwards timestamps, duplicate event times, rollback without
sequence-number gaps, legacy exclusion, foreign-owner/project cursor
rejection, wrong audience/scope and the existing full orchestrator suite.

### Still NOT lossless end-to-end

This ordering applies **only** when all same-task writers use the controlled
atomic journal+outbox path and the storage engine honors the per-task row
lock. Historical unsequenced events do not appear in these pages, and
external/bypass writers can still create unsequenced events. No HTTP,
SSE, desktop, outbox worker or native command is activated.

Before a release: review PostgreSQL **additive** migrations for
`aetheris_tasks.last_journal_sequence BIGINT NULL`,
`aetheris_task_events.commit_sequence BIGINT NULL` and the partial
(unique on non-null) `(task_id,commit_sequence)` index. Prove
multi-worker/concurrent PostgreSQL commit ordering and benchmark lock
contention. Ensure retention, durable receiver acknowledgement, full
event-UUID replay, owner/project identity checks on all indirect paths,
native key storage, multi-instance recovery and STOP/revocation are
complete. Application DB auto-DDL is **not** a safe migration plan.
The runtime owner-sensitive HTTP quarantine (#24) stays in place; no
protected main merge or owner-PC certification is claimed.

## Explicit remaining blockers

1. Platform draft [#228](https://github.com/teldigi5-wq/aetheris-platform/pull/228)
   now **proposes** issuing `iss=aetheris-identity` and `aud=aetheris-api`
   and requiring both in the gateway. This native verifier requires the same
   source-defined claims. None are in deployed old-format tokens by default.
   Old-format tokens will be rejected after activation, so coordinated
   identity/gateway/runtime releases, re-login and refresh-session UX review
   are mandatory. There is deliberately **no legacy unsigned/claimless
   compatibility mode**. Signer secret distribution and asymmetric-signing
   migration are separate security reviews.
2. No production credential material is embedded in this repository,
   configured for the runtime, injected into client JavaScript or logged.
   The verifier constructor is **not wired** to HTTP; task, mission and live
   event APIs remain quarantined by PR #24.
3. A future server-side request principal resolver must authenticate every
   request *before* deriving owner/project context. It must block direct
   runtime-port bypass and untrusted identity headers.
4. Authoritative task and mission creation must persist verified owner IDs.
   Every query, command and event subscription (including indirect task APIs)
   must enforce owner/project checks. Raw SSE messages, transcripts and
   arbitrary JSON are not identity proofs.
5. Re-enable only vetted per-owner APIs after cross-account integration
   certification, database migration review, SSE subscriber isolation,
   reconnect and token-expiry tests, plus actual owner-PC validation.

There is no automatic permission upgrade, paid provider, live agent
execution, deployment or protected-main merge in this change.

Security tracking: https://github.com/teldigi5-wq/aetheris-ai-runtime/issues/23

Platform live client remains blocked:
https://github.com/teldigi5-wq/aetheris-platform/pull/227

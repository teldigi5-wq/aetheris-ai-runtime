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

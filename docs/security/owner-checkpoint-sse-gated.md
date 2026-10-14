# Default-off owner-checkpoint SSE — opt-in backend integration

**Release status: BLOCKED_PENDING_OWNER_AUTH_AND_HARDWARE. No production deployment.**

This draft adds a *genuine* Spring MVC `SseEmitter` transport for sequenced,
sanitized task journal records, distinct from the unsafe historical global
`TaskEventStreamService.subscribeAll()`. The new transport is NOT enabled
in default runtime configuration and cannot be used by Syntra desktop yet.

## Conditions and protocol

Both `AETHERIS_OWNER_CHECKPOINT_HTTP_ENABLED=true` **and**
`AETHERIS_OWNER_STREAM_HTTP_ENABLED=true` are required. Both default
to **false**. A strong, explicitly provisioned `AETHERIS_JWT_SECRET` and
paired issuer/audience are mandatory or the opted-in component fails
startup. Do **not** opt in on deployed or Internet-accessible runtimes.

An eligible, already-created owner-global checkpoint can be streamed at:

`GET /api/orchestrator/owner-checkpoints/{checkpointId}/stream`

The caller must supply `Authorization: Bearer <signed platform JWT>`
with `orchestrator:read`. **No token is accepted in the URL**, cookies,
forwarded identity headers, CORS shortcuts, `Last-Event-ID` or a device
provided owner ID. Standard browser `EventSource` does not support a
custom Authorization header, so a future Windows native Rust transport
would need an audited authenticated HTTP streaming client; never move JWTs
to query strings as a workaround.

The runtime validates signed HS256 issuer/audience/expiry/scope, verifies
checkpoint owner ID and DB task owner **before** starting a connection,
and rechecks token expiration, current checkpoint and stored task owner
**on every poll** (1 second cadence). It **does not store the raw bearer
JWT** in the stream session. The session holds only a verified in-process
principal; this is not a substitute for real distributed revocation.

The transport uses at most **16 connections per runtime instance**,
**two daemon polling threads**, at most **20 records per poll**, and a
maximum **20-second session lease** by default (configuration range
2–30 seconds), further capped by signed JWT expiry. Disconnect/error,
timeout, expiration or component shutdown closes the emitter and cancels
its polling task. `SseEmitter` sends event name `task-recorded`, event ID
equal to the durable journal UUID and JSON containing only:
`eventId`, `taskId`, committed per-task sequence, timestamp, state,
validated agent ID and `PERSISTED_JOURNAL_NOT_VERIFIED_OUTCOME`.

A connection-local sequence position avoids resending already-emitted
frames every second. **The durable checkpoint never advances on SSE
send**: only the separately signed, owner-bound and write-scoped
`POST .../{checkpointId}/ack` call persists client-declared receipt.
When reconnecting, a client receives still-unacknowledged events again.
A client's `Last-Event-ID` does **not** override the durable server ACK
or let the caller jump ahead. Event delivery is **at-least-once attempted**,
not exactly-once receipt and not proof of any AI agent action.

Existing `/api/orchestrator/tasks*`, `/missions*`, `/live*` remain
unconditionally blocked by the original quarantine filter (#24).
Other task/mission/approval paths have NOT been proven safe. The opt-in
SSE is under the separately authenticated checkpoint HTTP namespace only.

## Tested, but NOT certified for activation

Spring MockMvc async SSE integration tests, PostgreSQL signed two-account
task isolation, live event redaction, missing/wrong scope/auth, session
expiry, default-disabled handler absence and old-route quarantine run in
CI, with the complete orchestrator regression suite.

Still required before any rollout:

- Multi-tenant/project authorization across all indirect task, mission,
  event, approval and workflow routes; server identity and session
  revocation that handles immediate logout/emergency STOP across JVMs.
- Secure gateway-to-runtime transport/network isolation and origin limits,
  per-owner/per-IP rate limits, TLS, connection admission budgets and
  DoS analysis; this first draft has a **per-instance** budget only.
- Client protocol with safe checkpoint open, native Windows credential
  vault, reconnect, abort signal, acknowledgement-after-render semantics,
  SLI/metrics, test with two genuine accounts and real runtime process.
- Polling overhead/connection lifetime/load testing, multi-instance
  routing and durable outbox dispatch tests using a full staged migration.
- Windows owner hardware testing. Protected main/release remain unmerged.

The signed JWT HMAC signing key is shared in the current architecture
and has an outstanding asymmetric issuer/verifier hardening plan. This
draft makes no claim of resolving that trust assumption.

Security issue #23 remains open and this feature must stay disabled.

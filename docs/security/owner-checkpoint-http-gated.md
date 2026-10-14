# Gated owner-only checkpoint HTTP boundary — security review draft

**Default state: DISABLED.** This change adds the first real HTTP adapter
for the *already-reviewed* durable task checkpoint component. It is **not**
live agent streaming and is **not** a production release decision.

## Surface

The class `OwnerCheckpointHttpController` is registered by Spring only
when `aetheris.owner-checkpoint-http.enabled=true` (default **false**).
An enabling runtime also **requires** a strong, explicitly provisioned
`aetheris.jwt.secret` (normally the protected `AETHERIS_JWT_SECRET`).
An empty/missing key or the old insecure default key makes registration
fail closed during startup. The JWT issuer and API audience must match
the paired platform token contract (#229), which has not been deployed.

Under `/api/orchestrator/owner-checkpoints`, the review-only endpoints are:

| Operation | HTTP route | Required signed JWT permission |
| --- | --- | --- |
| Mint owner-bound checkpoint | POST `/tasks/{taskId}` | `orchestrator:write` |
| Resume sanitized sequenced records | GET `/{checkpointId}/events?limit=50` | `orchestrator:read` |
| Advance one contiguous event ACK | POST `/{checkpointId}/ack` | `orchestrator:write` |

The signed `Authorization: Bearer <JWT>` is revalidated on every request:
HS256, expected issuer/audience, positive numeric `uid`, expiration,
recognized role and required permission. The controller intentionally
ignores spoofed `X-Aetheris-User-Id`, `X-Aetheris-Owner-Id`, cookies and
query-string identities. Checkpoint and journal ownership are derived from
DB rows and checked *again* for every operation.

A successful replay contains only journal event UUID, task UUID,
commit sequence, timestamp, state, sanitized agent ID, and **unverified
journal provenance**. Neither task command, event message/metadata,
email, JWT, nor secret is returned. REST responses use no-store/nosniff.
Foreign-owner and unknown task/cursor return the same coarse 404, invalid
bearers return 401, skipped ACKs return 409, and repeated exact ACKs are
idempotent. Client acknowledgements are **declarations**, not proof that
the desktop rendered the update or that an agent action succeeded.

## Quarantine remains unchanged

The older /api/orchestrator/tasks*, /missions* and /live* routes are still
blocked unconditionally by `OwnerSensitiveApiQuarantineFilter`, including
when a valid signed token is supplied. This draft adds NO SSE, WebSocket,
public dashboard proxy, desktop token storage, task creation or tool
execution. Enabling this limited HTTP adapter does not lift any other
quarantines and does not authenticate other runtime endpoints.

No default credentials are embedded in production configuration or code.
Only CI tests use synthetic signing keys.

## Testing

Two `@SpringBootTest` / MockMvc suites exercise separate runtime contexts:
1. Flag absent/false: no checkpoint route handler is registered; original
   task/mission/live quarantine still returns 403.
2. Explicitly enabled with synthetic signed JWTs: two-account separation,
   forged-header refusal, bad bearer/audience, read-versus-write scope,
   redacted owner sequenced history, one-at-a-time ACK, duplicate ACK,
   conflict on a skipped ACK and old-route quarantine.

PostgreSQL 16 CI also executes existing real lock, migration and durable
checkpoint tests, followed by full orchestrator regression.

## Explicit release blockers

- This flag **must remain false** in all actual deployed configurations
  until network isolation / authenticated direct-runtime port exposure,
  signed issuer+gateway coordination, per-owner rate limits, checkpoint
  quotas, retention, revocation, anti-replay/session controls, robust
  request validation and full cross-project authorization are reviewed.
- Existing JWT HMAC sharing between issuer and runtime is still a
  signing-key risk. Service-specific asymmetrically verified keys or
  mTLS/service principal design should be considered before release.
- Legacy/ownerless or project-scoped task records remain inaccessible.
  Full mission/approval/event indirect-route isolation is unresolved.
- Client ACK is not a trusted receipt attestation. A malicious authorized
  session can declare an ACK before rendering. Real desktop reconnect,
  STOP/logout, short-lived session limits and device-private Windows
  credential storage remain unavailable until owner PC access resumes.
- The PostgreSQL test migration is a disposable-schema dry run, not
  a production snapshot migration. All PRs are draft-only and protected
  main remains unchanged.

Security issue #23 stays OPEN. Default **false** is the required posture.

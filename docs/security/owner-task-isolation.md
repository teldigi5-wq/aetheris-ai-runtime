# Runtime task/mission owner-isolation gate (interim quarantine)

**Status:** `BLOCKED_OWNER_AUTH_NOT_VERIFIED` — no live Syntra mission subscriptions.
**Hardware:** `BLOCKED_PENDING_HARDWARE`. No production activation or release claim.

## Source-level finding

As of runtime main `65a6262717adcd52ac8d8a16ed6f223e299fd74d`, the
`OperationsStreamController` used `TaskEventStreamService.subscribeAll()` and
broadcast task events to every subscription. `TaskEntity` and
`MissionSessionEntity` lack a durable authenticated owner/project identity;
the task/mission controllers checked existence rather than caller ownership.
Even when traffic arrives through the platform gateway, JWT scope
`orchestrator:read` alone does not establish *which* task belongs to whom.
A caller can also potentially reach the runtime service directly depending
on deployment. This is a source-level exposure risk, **not proof of any actual
account compromise or exfiltration**.

## Interim runtime behavior

`OwnerSensitiveApiQuarantineFilter` executes before controllers. Until a
verified, server-side authenticated principal and owner-binding migration are
implemented, it returns a generic `403` JSON error and `Cache-Control:
no-store` for **every HTTP method** on:

- `/api/orchestrator/tasks...` including create, status, history, replay and SSE
- `/api/orchestrator/missions...` including messages, task attachment and events
- `/api/orchestrator/live...` including the global SSE feed

No header, spoofed `X-Aetheris-User-Id`, apparent JWT, loopback address,
proxy flag or JavaScript argument can enable these endpoints in this slice.
Some unrelated orchestrator APIs may reference task or mission IDs; this
quarantine is **not a complete audit of every indirect resource route**.

These blocks intentionally affect existing HTTP consumers. Existing internal
Java `TaskService`, `MissionService`, scheduler, planner and test workflows
retain their logic. This draft must not be merged without acknowledging
the endpoint compatibility change, full runtime certification, and a
reviewed rollout plan. Do **not** remove the quarantine merely to make the
old HTTP contract available again.

## Why not accept gateway identity headers immediately?

The external runtime has no established cryptographic trust for headers from
a direct HTTP client. Header names that the gateway sets are trivial to forge
at an untrusted runtime port. An authenticated session is not established by
string equality with a claimed owner, nor by a bearer token that the runtime
has not validated.

## Definition of done to replace this quarantine

1. Define a canonical owner identity from the platform identity service and a
   trusted propagation mechanism (runtime-validating JWT issuer/key and audience,
   or mTLS/restricted service-to-service gateway proof). Reject all direct
   unverified requests and spoofed forwarded headers.
2. Add authoritative `ownerId` and project/tenant context to new tasks and
   missions. Preserve existing task APIs internally but treat legacy ownerless
   rows as **not visible to any external owner** until explicitly migrated.
3. Enforce owner and project checks server-side for all reads, creates, writes,
   task/mission membership, history and SSE subscriptions; include
   per-task authorization for event replay. Global subscriptions must either
   be disabled for owner sessions or filtered to owner-specific channels.
4. Protect indirect task references via approval, work queue, planning,
   checkpoint, logging and attachment endpoints before lifting the interim
   quarantine.
5. Prove access isolation between two distinct authenticated principals,
   including direct runtime-port access, spoofed identity headers, token
   expiration/revocation, subscriber cleanup, malicious task IDs, reconnect
   replay and private event metadata.
6. Keep full CI, Tauri native-client credential boundaries, and target-PC
   validation gates intact. Enable only reviewed owner-scoped read paths.
   No paid provider or live trading authority is created.

## Verification

- `OwnerSensitiveApiQuarantineFilterTest`: all sensitive route families,
  multiple HTTP methods, double encoding, forged identity and bearer headers,
  and unaffected unrelated paths.
- `OwnerSensitiveApiQuarantineHttpTest`: real Spring `MockMvc` layer
  verifies direct runtime denials and health endpoint availability.
- Targeted GitHub Actions: `Runtime Owner Sensitive API Quarantine`.
- Existing `AI Runtime Build` on a PR to `main` must independently pass.

Related client fail-closed draft: https://github.com/teldigi5-wq/aetheris-platform/pull/227

Tracking issue: https://github.com/teldigi5-wq/aetheris-ai-runtime/issues/23

**Important:** This is an interim *protection*, not the complete verified
multi-tenant ownership feature. Do not mark issue #23 resolved from this alone.

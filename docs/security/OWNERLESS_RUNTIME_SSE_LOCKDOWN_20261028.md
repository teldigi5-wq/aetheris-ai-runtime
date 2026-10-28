# Ownerless runtime task event exposure lockdown — 28 October 2026

**Draft, security response to runtime issue #23. Stage45 NO_GO.**

## Directly inspected condition

At the certified AI Runtime main, there is no durable `TaskEntity.ownerId`
or `projectId`. `OperationsStreamController` originally exposed
`/api/orchestrator/live/events` via unscoped `subscribeAll()`, which
forwarded all task events from the `TaskEventStreamService.globalEmitters`
collection. `TaskController` allowed lookup/history/replay based only on
knowledge of a task UUID. An operation scope such as `orchestrator:read`,
a caller-supplied UUID or an `X-Aetheris-*` HTTP header is not proof
of task ownership. These are source findings, not evidence of exploitation.

## This review-only containment

- The **unscoped global SSE broadcast and subscription API are removed**
  from `TaskEventStreamService`. Task event journaling and internal
  publishing to per-task subscribers are kept intact for existing internal
  state machinery.
- The global `/api/orchestrator/live/events`, per-task
  `/{id}/events/history` and `/{id}/events` HTTP routes all call an
  unconditional fail-closed `TaskEventExposureGate` **before any task
  lookup, journal history read, replay or subscriber allocation**.
- The gate returns a generic HTTP **404**, concealing even whether a task
  identifier exists. **No environment variable, forged identity header,
  admin role, query parameter or UI toggle can override it.**
- Real Spring MVC tests invoke all three routes with two synthetic
  purported owners and two UUIDs, deliberately forged credentials and
  headers, and assert 404 with **zero interactions** with task or event
  services. An internal test verifies journal persistence continues and
  that the global subscription interface is absent.
- Dedicated exact-head CI checks route/gate ordering, lack of global
  emitters and source bypasses, runs the real MVC tests, and deliberately
  preserves Stage45 NO_GO.

This is **containment, not an owner-authenticated event service**.
It intentionally disables external event streaming and history pending
a proper durable owner/project authorization model. It does **not**
resolve the rest of issue #23: task list/item reads, creation, transition,
mission attachment, other consumer routes and persisted ownership
still need independent resource-server authentication and tenant isolation.

Before restoring any event subscription, complete: immutable authenticated
user/tenant identity, explicit ownership fields and migration behavior
for ownerless historic tasks, authorized per-task and per-project reads,
safe reconnect/replay and revocation, authenticated Gateway and direct
runtime port checks, two-user overlapping lifecycle isolation, bounded
subscription cleanup, audit events and independent real integration tests.

No production deployment, branch merge, schema migration, secrets,
hardware action or customer data was performed here. Keep DRAFT,
unmerged. **Stage45 NO_GO; all nine independent release attestations remain.**

# Ownerless Task and Mission HTTP access containment — 28 October 2026

**Review only, stacked on AI Runtime #205. Stage45 NO_GO.**

## Verified problem and intended temporary response

Runtime [issue #23](https://github.com/teldigi5-wq/aetheris-ai-runtime/issues/23) documents that task records have no durable `ownerId`, `projectId`, or independently verified user/tenant principal. The direct-runtime HTTP endpoints `/api/orchestrator/tasks` and `/api/orchestrator/missions` expose creation, listings, individual task/mission data, transitions, messages and event attachment. Gateway scopes or `X-Verified-Owner` caller headers cannot establish object ownership.

Previous stacked [PR #205](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/205) removed the global SSE fan-out and denied event history/replay at the HTTP controller. This follow-up adds an unconditional early-request **Spring-registered `OncePerRequestFilter`** before any MVC controller method for both full route prefixes:

- `/api/orchestrator/tasks` and all child paths, with optional semicolon matrix parameters;
- `/api/orchestrator/missions` and all child paths.

All HTTP verbs on those paths return generic **404**, before authentication claims, task lookup, repository queries, notification SSE subscriptions, backend task/mission dispatch or event replay. No username, owner ID, Bearer text, trust header, cookie, route query parameter or environment flag grants an exception. No task existence is disclosed. The filter checks both servlet path and context-relative request URI; unrelated health/agent paths are unchanged. It is a temporary **containment lockdown**, not authenticated per-tenant access.

## Independent test and regression evidence required

A real Spring MockMvc setup with the *actual TaskController and MissionController* plus the early HTTP filter simulates two distinct claimed owner identities and task UUIDs. It asserts no reads, listings, mutations, task attachments or mission event access succeed, and **zero mock service interactions** occur, including with forged identity/forwarded certificate headers. Additional tests verify path boundaries, context paths, unconditional production component registration and that unrelated routes are not incorrectly blocked.

The dedicated exact-head GitHub Actions workflow must run actual Java/Maven tests including inherited #205 SSE/journal tests. Source-level contracts verify 404 before filter-chain continuation; check for forbidden header/env bypasses; validate no global SSE subscriber methods; explicitly keep Stage45 NO_GO.

## Release and engineering truth boundary

This disables **external task and mission HTTP APIs** until the service can authenticate callers and bind task/mission objects to durable owner/project identities. It does **not** deliver a real working multi-tenant SaaS or an OAuth/JWT authorization service. Internal scheduled task execution, journal writing and local model planning paths are deliberately not modified here. Other runtime APIs are not covered; separate security review is needed. This is not a substitute for network isolation of direct runtime ports or global Spring Security hardening.

To restore these routes, a later independently reviewed stack must create true tenant/owner/project persistence with migrations (legacy ownerless tasks quarantined), independently verified trusted caller identities, ownership-scoped task/mission reads and writes, SSE subscriber isolation and revocation, and two-tenant adversarial tests across real Gateway and direct-runtime ports. All nine separate Stage45 production attestations remain pending.

**Protected main unchanged; no production deployment, no migrations, no customer data, no credential changes, PR DRAFT/unmerged. Stage45 NO_GO.**

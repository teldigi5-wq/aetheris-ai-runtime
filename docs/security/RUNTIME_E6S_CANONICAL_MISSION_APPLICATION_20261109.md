# S0-E6S — one canonical Mission application contract and legacy SQL bypass denial

**9 November 2026 | REVIEW ONLY | DRAFT STACK | Stage45 NO_GO | No customer API activation**

## Exact continuation

This is a child of [Runtime draft PR #301](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/301), pinned immutable parent `b43f48d20f7d9aee11abdbd6e22c1d58b06f513c`. Prior E6R already established a synthetic per-Mission `QUARANTINED / CANONICAL / FROZEN` cutover and immutable legacy CSV fence, but its new canonical Task-ID reader **did not remove the older E6Q SQL permissions** that independently allowed Task links and event header reads. This was a real future integration bypass risk, although those review-only functions were never exposed by product routes.

Existing `MissionService` has separate operations `create/recent/attach/status/message/messages/taskEvents`. Its `attach` still updates `MissionSessionEntity.taskIdsCsv` and `taskEvents` still uses that CSV to obtain unrestricted `TaskEventStreamService.history`; it does not use independently verified workload/owner-scoped access. The original `OwnerlessTaskMissionHttpFilter` still hard-404s Task/Mission HTTP. **This milestone does not wire that unsafe service to a public endpoint or mistake a JWT preflight for resource authorization.**

## Concrete source changes

1. [`e6s_canonical_mission_application_pg16.sql`](../../orchestrator-service/src/test/resources/security/e6s_canonical_mission_application_pg16.sql) applies only after pinned E6J/E6O/E6P/E6Q/E6R test schemas to a new disposable PostgreSQL16 Actions database. It **REVOKES** the old direct `e6q_review.link_owned` EXECUTE from `e6p_writer` and old `e6q_review.owned_event_headers` EXECUTE from `e6p_reader`. Earlier Java E6Q review adapters remain compiled, but calls by either actual restricted login now fail with database permission denial. The original E6R canonical-task-id function is preserved because it already checks FROZEN.
2. New `e6s_review.canonical_attach` is a restricted SECURITY DEFINER wrapper owned by the existing non-superuser database function owner. It locks both the Mission and CANONICAL cutover decision `FOR SHARE` within the same transaction as invoking the already source-pinned E6Q signed-JTI/session/owner/membership/workload-fence Task link function. It checks verified owner UID, tenant, project, nonempty owner binding, empty CSV and ACTIVE Mission, and rejects non-CANONICAL/FROZEN/QUARANTINED state before any link write. An operator FREEZE requires a conflicting row lock, so it cannot slip past the in-flight link.
3. New `e6s_review.canonical_event_headers` similarly locks the CANONICAL cutover row and Mission row, then invokes the existing signed JTI, owner, tenant/project and workload-fenced event-header function within **one** read transaction. It returns at most 25 event header tuples and never exposes raw event messages, agent IDs, metadata, Task commands, Mission objectives or unguarded SSE.
4. [`RuntimeE6SCanonicalMissionApplication`](../../orchestrator-service/src/main/java/io/aetheris/orchestrator/security/RuntimeE6SCanonicalMissionApplication.java) is a typed, unregistered Java application-facing contract for three operations: `attach(...)`, `taskIds(...)`, `eventHeaders(...)`. It does **not** require or hold an operator/DDL credential. It takes only the original independently verified `RuntimePinnedPrincipalVerifier.VerifiedPrincipal`, checks its current read/write scope, uses two separate limited SQL network-login DataSources, parameterized SQL, an explicit writable READ COMMITTED transaction on the primary, a three-second statement timeout and fail-closed exceptions. It has **no** Spring `@Service`, bean, servlet, REST controller, listener, background worker, auto-migration, event stream or direct table fallback.
5. New source-pinned CI verifies both **negative role grants** (old E6Q SQL functions inaccessible) and positive E6S function-only permissions, while also requiring the original Platform-issued JWT/enrollment and full Runtime Java regression suite. There is no copy of real production bearer material, database credentials or signing keys.

## Ten actual PostgreSQL16 acceptance tests

`RuntimeE6SCanonicalMissionApplicationPg16IT` reuses the immutable real Platform #415 RSA token producer and exact E6J session-registry SQL blob `fcf0e61c24ab89b61f10b6d2e454742a4b0420b0`, plus the accepted E6P/Q/R disposable data model. All tests run with separate non-superuser application reader, writer, operator and revoker PostgreSQL LOGINs. The ten mandatory tests prove:

1. An unpromoted Mission cannot be attached or read through E6S, and the old E6Q Java reader/writer calls now fail with SQL permission denied.
2. Explicit operator promotion enables only the canonical application interface; duplicate Task attachment stays deduplicated, CSV remains empty, and event headers contain no message/metadata/agent/command data.
3. Cross-user, cross-tenant, cross-project, read-only, incorrectly bound Task, malformed limits and cryptographically signed **but unenrolled** JTI cannot read or link.
4. Raw Task event and Mission link table SELECT is denied to application SQL logins; no application reader credential can call the operator promotion function.
5. Historical ownerless Mission CSV is quarantined, preserved exactly, never imported as a Task link, and cannot be edited after review.
6. A newly owned Mission with a dirty CSV is quarantined, never converted to live authority.
7. Renaming/removing the canonical event SQL function forces a checked SQLException, **not** a silent fallback to legacy E6Q/CSV history.
8. A concurrent uncommitted tenant membership revocation prevents stale event headers, failing closed on a real PostgreSQL row-lock timeout; the fixture-only rollback restores visibility.
9. Freezing a CANONICAL Mission blocks **all three** E6S app operations; the old E6Q reader/writer paths remain unavailable even while raw Task link rows are retained. CSV edits and automatic re-promotion stay forbidden.
10. A separate actual signed Platform JTI revocation denies Task IDs, event headers and additional links for another still-CANONICAL Mission.

Existing source code for E6P/E6Q/E6R is **not** duplicated, and the original public Task/Mission filter and SSE disabled state remain unchanged.

## Verification

**First complete code/test checkpoint:** [GitHub Actions #42609921169](https://github.com/teldigi5-wq/aetheris-ai-runtime/actions/runs/42609921169), commit `7de2d5eb166e09ec69d4735fc9f110d31c241f48`, **COMPLETED SUCCESS 2/2 jobs** on 9 November 2026. Actual immutable Platform Identity signed-JTI producer: **1/1**; E6S real PostgreSQL16 cases: **10/10**; full Runtime Java21 regressions: **377/377**. Total 388 passing tests, zero errors/failures/skips. Job logs independently inspected. Final documentation/README head requires a **new exact-head CI run** before it can be called fully verified.

## What this does *not* prove

- It is a **source-reviewed, unregistered application adapter**, not activation or replacement of the actual `MissionService`, `MissionSessionEntity`, `MissionSessionRepository` or `TaskEventJournalService`. The original CSV methods and raw event service still exist in the codebase but are blocked from protected external routes by the unchanged HTTP filter. No production application authentication integration can be inferred.
- It uses a synthetic, fresh PostgreSQL16 schema matching relevant actual JPA table shapes, not a real customer schema migration. Earlier test roles are password authenticated on isolated loopback; production PKI/mTLS and approved real service credentials are not established by this milestone.
- It does **not** disable all possible SQL operations owned by database superusers or the separate E6P data-owner function. It specifically closes the E6Q Task-link/event-header **restricted role** bypass through database ACLs, while preserving signed owner-checked canonical Task-ID reads.
- Bounded event headers are not a durable cursor/pagination proof, stable multi-replica snapshot, global SSE, Task execution, event payload authorization or lifecycle transition governance. No real ownerless customer record has been quarantined or migrated. No performance benchmark or owner-PC attestation has been performed.

**Next distinct milestone S0-E6T:** a real Mission repository/model integration preview on an isolated staging clone with approved schema evolution, owner-bound service transaction boundaries and explicit no-CSV mode, plus independently verified client TLS/mTLS and operational recovery before any route can be enabled. A new branch should stack from the exact E6S PR head, not repeat E6J–E6S.

**Runtime #23 OPEN · Platform #251 OPEN · Stage45 NO_GO · XERJ HOLD · owner-PC BLOCKED_PENDING_HARDWARE · historical Platform 34/34 COMPLETE, no Stage35.**

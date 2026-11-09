# S0-E6T — canonical Mission repository and service-layer preview

**9 November 2026 · review-only · isolated PostgreSQL 16 · Stage 45 NO_GO**

## Exact parent and gap addressed

New child of Runtime [draft PR #302](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/302) at immutable parent `62fa1c98016c09af8b678f3d8505e451af7e5b4b`. Prior E6S proved canonical-only Task/Mission linking, Task IDs, event headers and **revoked the prior E6Q direct SQL link/history grants**. E6R already provided operator-mode `CANONICAL / QUARANTINED / FROZEN` gates and fail-closed legacy CSV freeze. E6P already proved actual-*shaped* persisted Task and Mission ownership with immutable bindings. E6T does not duplicate those completed stages.

The remaining gap was an **actual Mission table-derived metadata/read service contract** that uses the same signed JTI, tenant/project, Mission owner and cutover authorization. The current production-registered `MissionService` still uses `MissionSessionRepository.findById`, `MissionSessionEntity.getTaskIds()`, `taskIdsCsv` and a generic event-history lookup. Those operations have no current resource-level authorization for external clients, so `OwnerlessTaskMissionHttpFilter` must remain unconditional 404.

## Concrete implementation

- [`e6t_canonical_mission_projection_pg16.sql`](../../orchestrator-service/src/test/resources/security/e6t_canonical_mission_projection_pg16.sql) adds a **disposable** `e6t_review.canonical_mission_projection` function. The function owner is the existing non-superuser E6P function owner, and only `e6p_reader` receives EXECUTE. It checks exact independently verified signed Identity issuer/kid/JTI/UID/iat with existing E6J session/signer/epoch authority. It locks current CANONICAL state and exactly owned Mission record; rejects nonempty CSV and missing verified owner bindings. The existing E6O-owned function locks live tenant/project membership and active workload fence. Only then does it return the persisted `id`, `title`, status, timestamps and version, **from the same PostgreSQL READ COMMITTED transaction**. It never emits Mission objective, taskIdsCsv, ownerless history or global event-stream URLs.
- [`RuntimeE6TCanonicalMissionRepository`](../../orchestrator-service/src/main/java/io/aetheris/orchestrator/mission/RuntimeE6TCanonicalMissionRepository.java) is an **unregistered, non-JPA repository prototype** that projects the actual MissionSessionEntity column shapes into immutable `MissionProjection(id,title,MissionStatus,createdAt,updatedAt,version)`. It only accepts the existing sealed, independently RS256-verified `VerifiedPrincipal`, checks live `orchestrator:read` scope and canonical tenant/project inputs, then invokes the restricted database function via parameterized JDBC, explicit writable PRIMARY READ COMMITTED transaction, three-second SQL timeout and fail-closed exception behavior. It rejects ambiguous/null/unrecognized enum rows. It does not call `MissionSessionRepository`, `findById`, `getTaskIds` or any raw Mission table SELECT. The SQL reader cannot directly read Mission, events or other tenant tables.
- [`RuntimeE6TCanonicalMissionService`](../../orchestrator-service/src/main/java/io/aetheris/orchestrator/mission/RuntimeE6TCanonicalMissionService.java) composes the new repository with the already tested `RuntimeE6SCanonicalMissionApplication`. Its **separate** typed methods are `describe`, `attach`, `taskIds`, and `eventHeaders`. Every operation performs independently verified canonical signed-session, Mission/Task ownership, tenant/project and live workload-fence checks. It has **no** Spring `@Service` or bean, controller, migration, event-stream listener, operator credential or automatic route activation. This is an application-layer implementation candidate, not a replacement for the existing registered Mission service.
- The new CI executes the existing pinned original Platform Identity JWT producer and shared PostgreSQL16 E6J–E6S test contracts, then creates the E6T projection with independent **negative raw-table and negative writer EXECUTE** assertions. Test-only SQL and private JWT fixture files are destroyed after the run; existing product hard-404 and direct-port tests remain mandatory.

### Ten real signed-JTI / PostgreSQL integration tests

1. Unpromoted and historical ownerless Missions are not visible through the new service and cannot be linked/read before cutover.
2. Explicit synthetic operator promotion admits only the persisted, typed Mission metadata subset (no objective, CSV or event URL).
3. The service can add a canonical Task link and retrieve bounded Task IDs plus header-only events without updating legacy CSV or exposing raw event content.
4. Wrong user/tenant/project, read-only consumer, wrong Task ownership and cryptographically valid but **not enrolled** JTI are rejected across the service operations.
5. Historical ownerless and dirty owned Mission CSV are quarantined and retained without becoming an authorization or task-link source.
6. A restricted application database reader cannot directly SELECT Mission objectives/CSV or Task event payloads; the writer cannot call the new Mission projection.
7. Temporarily removing the new canonical function causes a SQL failure, never a fallback to ownerless JPA `findById`. Restoring the isolated function restores permitted access.
8. A concurrent uncommitted membership revocation holds the PostgreSQL authority row lock; the repository fails closed on the three-second timeout rather than leaking a stale Mission record.
9. Operator FROZEN blocks metadata, Task IDs, event history and link writes. Prior E6Q preview functions remain SQL-permission-denied.
10. Revoking the original enrolled Platform signed JTI also denies all new service operations for an otherwise still-CANONICAL Mission, despite unchanged physical stored data.

## Evidence

Initial full source/test checkpoint: [Actions #42613244867](https://github.com/teldigi5-wq/aetheris-ai-runtime/actions/runs/42613244867) at `27d3f4aecae7a7d64ee1a2987661c222d710b015`, **COMPLETED SUCCESS 2/2 jobs**. The original pinned Platform issuer/enrollment **1/1**, new E6T real PostgreSQL16 cases **10/10**, full Runtime Java21 regressions **377/377**, zero failures/errors/skips (388 tests). Independent raw job logs checked. A follow-up CI workflow edit tightened the static non-registration grep to match real annotation *declarations* rather than text in Javadoc; that code-head run [#42613316802](https://github.com/teldigi5-wq/aetheris-ai-runtime/actions/runs/42613316802) also completed SUCCESS 2/2 jobs at `22ed09b6131289494442944a4b195dd783f34bc2`.

**Any later README/documentation PR head requires its own exact-head CI check before being called verified.**

## Critical exclusions, next real integration

- **Not real Spring Data JPA ownership mapping or a production migration.** Existing MissionSessionEntity fields and Hibernate configuration are unchanged; E6T reads a *disposable PostgreSQL16 clone* shaped after the JPA table. It does not prove deployment of new Mission ownership columns or an established production data migration tool.
- Original `MissionService` and its internal CSV operations still exist, but Task/Mission product HTTP remains **unconditionally 404**, and global SSE stays disabled. Any real route exposure must first retire or constrain all original ownerless operations, prove actual tenant/project/workload-bound service authentication and ensure one canonical authorization source for messages, status transitions, events, attachments and task execution.
- `describe`, `taskIds`, and `eventHeaders` are **independent SQL transactions**. Combining their outputs is not an atomic multi-collection snapshot. A future bounded aggregate response must be checked in a single SQL transaction if consistent cross-resource snapshot semantics are required.
- Separate network DB logins in disposable CI use test-only credentials, not real PKI. No true Gateway→Runtime or Runtime→Postgres mTLS issuance/rotation, customer data migration, JPA owner-binding release, replicated-session or restored-token anti-rollback drill, production backup, deployment/canary, operator approval quorum or owner-PC hardware attestations exist in this change.
- No protected `main` merge, production endpoint enablement, customer secret, automatic schema migration, Task status update, Mission message, agent execution, or live event stream.

**Next distinct S0-E6U:** integrate a staging-only canonical service under deliberate *test-only* Spring wiring against the full JPA schema clone, then prove missing/incorrect migration state fails startup closed; produce an owner-approved real schema and PKI rollout plan before any customer route is touched. A staging deployment must have a true, auditable security boundary and rollback path; do not simply enable the old `MissionService`.

**Runtime Security #23 OPEN · Platform Security #251 OPEN · Stage45 NO_GO · XERJ HOLD · owner-PC BLOCKED_PENDING_HARDWARE · historical Platform blueprint 34/34 COMPLETE (no Stage35).**

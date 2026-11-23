# S0-E6Q — owned Mission↔Task link and privacy-bounded event headers

**9 November 2026 | REVIEW ONLY · no activation · Stage45 NO_GO**

## Recovered exact checkpoint: new work, not an E6P repeat

This change is stacked exclusively on [Runtime draft PR #299](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/299), **immutable parent** `e4aaf374fe7350de71c47173998a87b465fb7059`. E6P had already proven immutable signed-JTI Task/Mission creation on disposable, JPA-shaped PostgreSQL16 tables with ownerless records preserved and separate function-only reader/writer accounts. E6O had already proven atomic Task/Mission resource reads. The source-pinned Platform Identity producer from Platform draft PR #415 at `53d55135181ef89d3ff402f46b835d4b3a0f8ac5` and exact E6J schema blob `fcf0e61c24ab89b61f10b6d2e454742a4b0420b0` remain the unchanged accepted cryptographic/session-source contracts. No restart, rebase, merge or rewrite of completed stages.

The missing authorization gap is not another JWT verifier. `MissionService.attach` currently checks merely that the Task exists, then mutates `MissionSessionEntity.taskIdsCsv`; `taskEvents` trusts the CSV and calls `events.history(taskId)`. The existing `TaskEventJournalEntity` persists `aetheris_task_events` with message, agent ID and metadata JSON. These application service methods are **not adequate per-owner, tenant, project, workload-fence, session or revocation enforcement for exposed routes**. The real product Task/Mission HTTP remains quarantined by `OwnerlessTaskMissionHttpFilter`; global SSE stays disabled.

## Actual S0-E6Q code

1. `orchestrator-service/src/test/resources/security/e6q_owned_mission_link_history_pg16.sql` creates a disposable review-only `e6q_review.mission_task_links` table linking the actual-shaped Task/Mission UUID keys. The schema also creates a test-only `public.aetheris_task_events` with JPA-equivalent event field names, lengths and UUID relationships; a pre-existing ownerless Task event is inserted with private message/metadata. All raw event and link tables are owned by the E6P restricted database function owner, and neither test writer nor reader login can SELECT or UPDATE them.
2. `e6q_review.link_owned(?,?,?,?,?,?,?,?,?)` is a `SECURITY DEFINER` function whose owner is the already reviewed E6P non-LOGIN function owner. It checks the **exact independently verified Platform JWT issuer/kid/JTI/UID/iat** with existing E6J session/signer/epoch authority, verifies both persisted Task and Mission owner UID, tenant/project and **identical workload-fence ID**, requires verified non-null ownership and active Mission state, then calls the existing E6O-owned row-locking membership/fence checker **in the same READ COMMITTED transaction as the junction-table insertion**. It locks mission/task, signer/session/epoch and membership/fence authority until commit. An existing authorized link can be retried idempotently; cross-owner, cross-tenant, cross-project, unowned and incorrectly fenced pairs cannot be linked.
3. `e6q_review.owned_event_headers(?,?,?,?,?,?,?,?,?)` requires current exact session and live owner/tenant/project/fence authority, joins only authorized persisted Mission links and Task events, and returns at most **25 rows** of `task_id`, `event_at`, `event_state` in a stable descending timestamp/event-ID order. It returns no raw event `message`, `agent_id`, `metadata_json`, Task `command_text`, or Mission `objective`; resource/link/event rows are locked through the read transaction. No unrestricted TaskEventJournalService call or unfiltered SSE fallback exists.
4. `RuntimeE6QMissionLinkHistory` is an unregistered, unannotated Java adapter consuming the original sealed RS256 `VerifiedPrincipal`, requiring `orchestrator:write` to link and `orchestrator:read` to fetch event headers. It uses independent function-only network-authenticated PostgreSQL writer/reader `DataSource` accounts, prepared SQL, explicit writable READ COMMITTED transactions, three-second query timeouts and generic fail-closed SQL exceptions. It never creates an HTTP route, Spring service, task execution, mission status update, task event, global listener or automatic migration.

**Important compatibility limit:** The new authority-controlled link table is NOT synchronized with the existing `MissionSessionEntity.taskIdsCsv`. This is intentional: making the original CSV a permission grant or simultaneously driving the existing unguarded `MissionService` would introduce an unsafe dual-source-of-truth. A future reviewed production data-model migration and staged read/write cutover must establish a canonical link model and backward-compatibility policy; this PR does not claim actual application linkage or live history availability.

## Real source-pinned Platform JWT + PostgreSQL16 acceptance

The new `RuntimeE6QSignedMissionLinkEventPg16IT` runs only with `GITHUB_ACTIONS=true`, the isolated `aetheris_e6j_disposable` database and `AETHERIS_E6Q_MUST_RUN=ONLY_DISPOSABLE_E6Q_PG16`. It obtains two actual signed and independently enrolled RS256/JTI Platform JWTs from the original immutable Platform test producer. The DEVELOPER can write; the API_CONSUMER is read-only. A disposable test administrator seeds operator-only membership/fence bindings, genuinely owned other-user fixtures and ownerless historical events, never exposing an administration route.

Eight mandatory tests:

1. Authorized signed owner links exact matching Task+Mission; repeated authorized link remains one row; original `task_ids_csv` stays unchanged and is never treated as authority.
2. Rejects cross-owner, cross-tenant, cross-project, wrongly paired and historical ownerless Task/Mission links.
3. Rejects read-only user, valid Platform-signed but **unenrolled** JTI, and the original legacy Platform HMAC token.
4. Separate SQL reader and writer LOGIN accounts cannot SELECT raw link or event tables; the reader cannot invoke the link writer function.
5. History returns only authorized Task event headers, enforces a hard row cap, denies wrong tenant, consumer's access to another user's Mission and legacy Mission; does not expose any raw message, agent, metadata or command text.
6. Deactivated workload fence immediately denies new links and event reads; terminal Mission status denies new linking. The test administrator restores only disposable fixture data.
7. Concurrent uncommitted membership revocation holds the authority row lock; a live event read fails closed with a bounded SQL timeout rather than releasing stale event data. A rollback of that test-only revocation restores authorized visibility.
8. Actual separate SQL revoker revokes the Platform signed session; prior allowed event history and any further linking are denied even though owned Task/Mission rows and event data still exist.

The workflow also checks for absence of new Spring components/controllers, preserves the unconditional Task/Mission HTTP 404 and original direct-port security suite, forbids unreviewed application migration directories and always deletes synthetic JWT files and temporary roles/schemas. It runs the full orchestrator Maven regressions separately.

## Verified code-only checkpoint

[Actions #42603390500](https://github.com/teldigi5-wq/aetheris-ai-runtime/actions/runs/42603390500) — implementation head `4a7ef81baf7cef14995d4e61a978b2ed2fa5def8`: **COMPLETED SUCCESS 2/2 jobs**. Original source-pinned Platform issuer/enrollment **1/1**, new real PG16 link/history **8/8**, full Runtime Java21 **377/377**, all zero failures/errors/skips. CI XML no-skip assertions and row-privilege/quarantine checks passed. Subsequent documentation/README SHA is **not automatically covered** by this run and requires an exact-head rerun.

## Release exclusions and next distinct S0-E6R

- The proof uses disposable actual-shaped tables and isolated JDBC, **not** approved customer database migrations, live `MissionService`/TaskEventJournalService integration or the real HTTP Task/Mission/SSE routes. No real Task–Mission `task_ids_csv` migration/backfill, no raw event-message authorization, no Task execution, agent delegation, mission lifecycle transitions or durable multi-tenant event replay exists.
- E6Q proof is **direct JDBC/PostgreSQL**, not real end-to-end Gateway→Runtime mTLS; E6N verified separately only ephemeral PostgreSQL client certificates, not the installed Runtime Java DataSource. External production certificate custody, role rotation, DB primary/replica authority and recovered-session anti-replay remain gated.
- No approved release/production migration, ownerless historical audit/backfill policy, external immutable events, independently validated event ordering at scale, durable user-facing pagination/cursors, or owner-PC/Stage45 release attestations. This is NOT an external privacy compliance certificate.
- **Next S0-E6R:** independently review a canonical Mission–Task relational migration and staged consistency/rollback strategy, then wire actual owner-bound application APIs, bounded event pagination and SSE only after a verified real mTLS workload boundary and tenant ownership gate. Keep old product routes hard-404 until proof is independently accepted; do not repeat E6J–E6Q or invent a Stage35.

**Runtime Security #23 OPEN · Platform Security #251 OPEN · Stage45 NO_GO · XERJ HOLD · owner-PC BLOCKED_PENDING_HARDWARE · Platform historical roadmap 34/34 COMPLETE.**

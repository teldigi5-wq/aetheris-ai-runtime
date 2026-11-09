# Runtime Security #23 — signed-session owner-bound task and event reads

**9 November 2026 • DRAFT REVIEW ONLY • NOT HTTP-ACTIVATED • Stage 45 NO_GO**

## Exact dependency

This continuation is stacked on [Runtime PR #293](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/293) at SHA 2c0bc1c53e3502625a78e751b46ff1ef0dbfcbe0 and its signed task-write/mandatory signer/epoch authority dependency. It does **not** repeat or reopen the signer/epoch INSERT race. Runtime protected main contains independent merged image-publication/dependency fixes at SHA 5e3b495ccbbd4eebc88b2a943b0a7f292c7c40f2. The review ancestry diverges from main; do not blanket-merge this stack or erase PR #292.

## Actual new implementation

The new non-Spring, non-injectable Java class is:

orchestrator-service/src/main/java/io/aetheris/orchestrator/security/RuntimeVerifiedTaskReadProbe.java

No existing controller, filter, public endpoint, SSE subscription, dispatcher or migration is changed.

- **findOne:** reads one task's bounded ID/title/state/mode/update-time summary.
- **recent:** owner+tenant+project-scoped listing, limit 1–50.
- **history:** owner+tenant+project-scoped task event **headers only**, keyset paginated by (event_at,id), limit 1–100. Event messages, command text and raw metadata are intentionally **not returned**.

Every SQL statement obtains principal UID/issuer/kid/JTI/issued-at from the existing independently verified, operator-pinned RS256 principal type. It requires a signed read scope, token freshness, separately signed workload-fence match, ACTIVE exact signed session, active signer authority row, principal epoch cutoff, active tenant/project/membership rows, and exact task owner binding with non-null verified timestamp. Required authority rows are locked FOR SHARE in the **same PostgreSQL16 statement** as the task/event selector. Missing or revoked authorities and ownerless tasks fail closed. Tenant/project parameters may only narrow scope.

The database connection must be in an explicit writable READ COMMITTED transaction; replica/split-brain correctness is not certified. Caller must roll back on failures. Task/Mission HTTP remains unconditionally 404-quarantined, and unrestricted global SSE remains disabled.

## New required executable evidence — pending exact-head CI

The existing disposable PostgreSQL16 signed-session fixture now also defines a test-only aetheris_task_events table. Three additional JDBC tests cover:

1. Two separately signed identities **in the same project**, owner-scoped lookup/listing, cross-owner/tenant/project denial, legacy ownerless quarantine, bounded event-history keyset pages and exclusion of private metadata.
2. Missing/revoked session, signer, epoch and project membership, with insufficient scope and workload mismatch denial.
3. Session revocation UPDATE blocked by a concurrent authorized task-read transaction (expected PostgreSQL SQLSTATE 57014), followed by denial after committed revocation.

The dedicated PostgreSQL CI gate requires **at least 11 tests, zero failures/errors/skips** (8 existing plus 3 new); the full Java suite and explicit non-activation check remain mandatory. This is not evidence of PASS until the exact-head jobs and reports are inspected.

## Remaining blockers and next milestone

No approved production authority migrations/writers, real Identity session revocation source, verified Gateway-to-Runtime transport, HTTP resource server, direct-port two-account HTTPS proof, mission ownership, event-payload redaction approval, per-owner SSE, reconnect/replay lifecycle, independent split-brain fencing or physical PC/hardware acceptance exists as a result of this change.

Next: verify the real PostgreSQL test result and review the diff, then plan restricted production registry custody and actual independent Runtime HTTP authentication before unquarantining any routes. SSE remains subsequent to verified owner-bound reads/history.

**Runtime #23 OPEN • Platform #251 OPEN • Stage 45 NO_GO • XERJ HOLD • BLOCKED_PENDING_HARDWARE.** Historical Platform Stage 34/34 remains completed; this is security-issue progression, not Stage 35.

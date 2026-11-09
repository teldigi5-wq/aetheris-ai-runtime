# Runtime Security #23 — real PostgreSQL16-backed direct-port HTTP session preflight

**9 November 2026 — DRAFT REVIEW ONLY — NOT DEPLOYED — Stage 45 NO_GO**

## Next milestone, not a restart

This branch stacks exactly on [PR #295](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/295), the separately reviewed unregistered direct-port RS256 servlet filter. It does **not** repeat signer/epoch SQL protections in #293, task-owner read/history in #294, or the in-memory HTTP socket tests in #295. Runtime protected `main` and Platform `main` are not changed.

## New functionality

`RuntimePg16ReviewSessionAuthority` implements the existing `RuntimeSignedIdentityHttpFilter.SessionAuthority` interface using **actual PostgreSQL JDBC**, not a memory map.

A unique short-lived, explicit writable READ COMMITTED PostgreSQL transaction is opened for each HTTP preflight; a single prepared SQL statement `JOIN`s the **existing review fixture** `synthetic_session_registration`, `signer_key_states` and `principal_epochs`. It requires exact signed `issuer`, `kid`, `jti`, `uid` and issued-at seconds; active session and signer; and an issued-at strictly **newer** than the principal epoch cutoff. The required rows are `FOR SHARE`-locked against concurrent revocation UPDATE/DELETE until statement+commit. The SQL rejects a PostgreSQL replica via `NOT pg_is_in_recovery()` and read-only transactions. Absence/revocation returns false, while database failures propagate to the HTTP filter's non-descriptive **503**, not an allow or stale cache. Query timeout is three seconds to bound lock waits.

The class has **no `@Component`/`@Bean` auto-registration**. No production database migration, identity writer, gateway change, controller, Task/Mission route, or SSE is activated. Despite real PostgreSQL, the referenced relations are **disposable synthetic review contracts**, not a certified production session registry. This component must not be wired to live product routes.

## Required new executable evidence — pending exact-head CI

The separate `RuntimePg16ReviewSessionAuthoritySocketJdbcIT` boots isolated embedded Tomcat on **real loopback TCP**, creates/drops a fresh random PostgreSQL16 review schema, uses ephemeral RS256 credentials, and connects the real servlet filter to the actual JDBC authority adapter *only* at `/__pg_review_bearer_probe`. Acceptance tests cover:

1. Two separate signed users, denied before session enrollment, admitted after exact enrollment, one-user revocation and hard 404 on the existing Task route.
2. Live signer revocation and principal-epoch cutoff denial.
3. Exact UID and JWT issue-time row binding; no JWT-shaped trust.
4. Deliberately unavailable registry yields generic 503 and recovery after restore.
5. Concurrent uncommitted session-revocation UPDATE blocks the `FOR SHARE` preflight until bounded query timeout; never authorizes the stale ACTIVE row. Committed revocation subsequently returns 401.

The workflow must run **all existing 11** disposable session/task PostgreSQL tests plus **at least 5** socket-to-real-PostgreSQL tests, with **zero failures/errors/skips**, while preserving the full Java 21 suite, its separate five in-memory direct-socket regressions, unconditional 404 Task/Mission quarantine and disabled global SSE. The integration class ends `JdbcIT` to avoid requiring a live database for default `mvn test`; it is explicitly selected in PG16 CI. **Do not claim PASS until exact-head CI reports are checked.**

## Explicit limitations / next stage

Successful HTTP preflight is **not** a durable authorization across the handler: these row locks commit before the handler runs. Every future resource query MUST recheck exact session, signer, epoch, memberships, owner and workload fence **within its own resource database transaction**, as the separate #294 read probe already demonstrates for disposable fixtures. The boolean alone does not authorize a task/mission. The active-primary check does **not** prove split-brain safety.

Still missing: reviewed, versioned real authority migrations, minimum-privilege read/write roles, ownership/custody of Identity session enroll/revoke and signer/epoch writes, reliable primary/replica topology, key rotation operations, endpoint TLS/mTLS and direct two-account proof, reviewed runtime identity service integration, historic ownerless policy, mission owner linkage, per-owner SSE/history payload handling, cross-repo integration acceptance, and owner-PC hardware evidence.

**Runtime Security #23 OPEN · Platform #251 OPEN · Stage 45 NO_GO · XERJ HOLD · BLOCKED_PENDING_HARDWARE.** Platform's historic Stage 34/34 complete milestone is unchanged.

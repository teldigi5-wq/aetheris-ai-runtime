# Runtime strict signed revocation facts — draft-only, NO_GO

The isolated Runtime pinned RS256 verifier now exposes an explicit
`verifyBearerForRevocation` entry: it first checks the real RSA signature,
operator-pinned kid, exact issuer and audience, signed UID, role/scope
and time window. It then fails closed if the genuine signed JWT has no
bounded `jti` (16–128 safe printable characters). A sealed
`VerifiedRevocationFacts` type privately retains the signed UID, issuer,
key ID, token ID and issue time. Reading these facts after token expiry
fails closed, even if the Java principal object was retained.

This is **not** a live B62 revocation adapter or an allow decision.
The preexisting `verifyBearer` path remains unchanged for already tested
synthetic task/mission/JDBC containment; callers cannot infer that it
performs session revocation. A future production authorization path must
FIRST obtain revocation-bound signed facts and THEN consult an independently
authenticated, primary-consistent read-only B62 store, deny stale/failed
readers, enforce signer blocks and the principal epoch, and separately
enforce tenant/object ownership. The current filters still quarantine
all uncertified /api, /actuator, task, mission and SSE requests.

An actual-RSA JUnit test verifies missing signed jti denial, correct
signed provenance for an independently minted UUIDv4 token, and denial
of retained provenance after the trusted clock advances beyond expiry.
The mandatory exact-head security CI covers this with the existing real
PostgreSQL16, direct HTTP, route inventory and cleanup matrix.

Runtime issue #23 and Platform issue #251 remain OPEN; protected main
and W5-L stay unchanged; Stage45 and all nine production attestations NO_GO.
No live token exchange, production session revocation, migration, customer
HTTP API, deployment or model execution is authorized.

## 4 November 2026 — B62 signed-IAT cutoff equality corrected in synthetic PG16

The earlier synthetic Runtime/JDBC revocation SQL used
`s.iat_seconds >= epoch.min_iat_seconds`, allowing a token issued exactly at
the principal's emergency cutoff. The B62-G reference requires JWT issue
times **at or before** the emergency cutoff to be denied. This prototype
now uses strictly **`s.iat_seconds > epoch.min_iat_seconds`**, still
after genuine pinned RSA verification and sealed signed-token facts.

A new disposable PostgreSQL16 test issues two *independently signed* and
registered JWTs for the same synthetic UID at issue-second `T` and
`T+1`, verifies both before cutoff, commits a monotonic principal cutoff
at **exactly T** through a separate writer, and requires the retained
`T` token to be denied, `T+1` to remain accepted and a separate UID to
remain unaffected. It also checks the same cutoff cannot be rewritten by
an identical update. CI asserts the exact strict SQL predicate and runs
the full existing JWT, signed-session, HTTP quarantine and PG16 suites.
No production authority or API is enabled.

**Scope:** This is only a test fixture emulating parts of B62, not its
production schema, privileged emergency writer, signing-key block
retention, online primary reader or actual user session logout.
The signed `jti` seam is not a revocation verdict and does not replace
tenant/resource ownership. Protected main unchanged, issue #23/#251 open,
Stage45 NO_GO and nine release attestations still outstanding.

## 4 November 2026 — combined emergency-block and session-registration denial

A further review-only PostgreSQL16 regression proves a dangerous distinction:
Platform B62-G1's emergency `principal_epochs` and `blocked_signer_kids`
tables contain **no per-`jti` active session registry**. The absence of an
emergency block must not be mistaken for active-session authorization.

The disposable Runtime B62 compatibility test now creates an explicitly
**separate synthetic session registration** table. A new single-statement,
READ COMMITTED boolean reference requires *all* of: a matching verified
issuer, key ID, `jti`, UID, signed issuance time and ACTIVE registration;
no blocked signing key; and issuance strictly **after** any principal
emergency cutoff. It tests missing registration, different signed JTI,
committed individual-session revocation, unchanged B62-only result after
session logout, inclusive principal cutoff, unrelated user isolation,
signer block and closed connection. The test is not an Identity schema,
deployed revocation service, API permission, production migration or
proof of cross-service operational consistency. Its disposable CI role
is not certified as a production least-privilege reader. No route opens.

This demonstrates only a **synthetic two-authority composition**.
Real admission additionally requires independently approved resource,
tenant and project ownership, authenticated primary consistency, bounded
timeouts, audited writer separation, rollout/rollback and human review.
Issue #23/#251 OPEN, Stage45 NO_GO with nine outstanding attestations.

## 4 November 2026 — independent least-privilege PostgreSQL reader rehearsal

The earlier B62 synthetic compatibility test used JDBC `setReadOnly(true)`
on `ownerlab_ci`, which is also the disposable PostgreSQL service
administrator. That did **not** establish server-side least privilege.
This review-only revision creates one random ephemeral PostgreSQL LOGIN
role `ownerlab_b62r_*`, grants it schema USAGE and SELECT on exactly the
two B62-like emergency tables and the **separate** synthetic session
registration table, and makes all B62 negative/positive decisions
execute through that independently authenticated network login.

A new PostgreSQL16 test connects *without* JDBC read-only enabled and
requires the server to reject INSERT, UPDATE, DELETE, and schema CREATE
attempts with SQLSTATE `42501`. It also verifies the distinct authenticated
session identity, schema USAGE, granted SELECT and absence of mutation
privileges. The ordinary decision connection separately enforces
READ COMMITTED + read-only transaction semantics. The test tears down the
synthetic schema **and the ephemeral role**, and CI checks that neither
`ownerlab_b62_*` schemas nor `ownerlab_b62r_*` roles remain.

**Strict limitations:** The random credential is CI-only, not a production
secret, certificate, SPIFFE principal, Vault-managed workload account or
cross-service mTLS integration. B62-G1 still has no real active-session
registry, and this composition uses temporary owner-created tables in a
single disposable PG16 instance. Positive synthetic decisions do not grant
Runtime routes, resource ownership, tenant access or live authority.
An independently reviewed, production primary-consistent read-only service,
security migrations, grant management, bounded timeouts, signer blocks,
audit, stale-replica protections and safe rollback remain outstanding.
Runtime issue #23 and Platform #251 OPEN; Stage45 NO_GO.

## 4 November 2026 — bounded disposable read-timeout negative proof

A real PostgreSQL16 failure-recovery rehearsal now configures the temporary
independently authenticated `ownerlab_b62r_*` LOGIN with a **server-enforced**
`statement_timeout = 1500ms` on newly created connections. The review-only
reader uses explicit `connectTimeout=3` and `socketTimeout=3` JDBC
parameters, and its B62 emergency and composed session checks set
`PreparedStatement.setQueryTimeout(2)`. These are bounded synthetic test
defaults, **not production-derived latency SLOs**.

A new regression registers a genuine signed synthetic session, proves the
restricted reader can initially evaluate it, then deliberately executes
`SELECT pg_sleep(3)` on the same read-only, READ COMMITTED transaction.
PostgreSQL must return SQLSTATE `57014` from the role-level timeout, and
both the composed session decision and B62 emergency-only predicate must
**DENY while that transaction is aborted**. An explicit rollback then
allows a fresh primary-backed statement to evaluate the same still-active
session without any optimistic allow-cache. The temporary reader login
and synthetic schema are still removed and checked at the end of CI.

This is an actual disposable PostgreSQL timeout test, **not a network outage,
replica freshness, TLS workload identity or permission-revocation race test**.
No real Runtime authorization route consumes this code. A production
primary-read path still needs a reviewed operational timeout budget,
authenticated workload transport, transaction lifecycle and safe
availability/rollback plan. Stage45 NO_GO; issues #23/#251 remain open.

## 4 November 2026 — committed SELECT revocation on established reader login

An additional disposable PostgreSQL16 test now checks the boundary between
a previously successful signed-session read and a subsequently committed ACL
change. The real independently authenticated `ownerlab_b62r_*` connection
stays open while the owner connection revokes SELECT first on the **separate**
synthetic session-registration table and then on both B62-like emergency
tables. The next READ COMMITTED decision must DENY for the affected table,
and direct table reads must fail with **PostgreSQL SQLSTATE 42501**. The
emergency-only predicate remains readable when only session-table SELECT
is revoked, confirming the decisions consult separate authorities.

After restoring only the three original SELECT grants, a **new** SQL
statement through the same open reader may evaluate the still-active
synthetic session again; no cached allow is accepted. The test restores
the disposable grants in a finally block and inherits role/schema cleanup.

**Explicit limitation:** The test ends an earlier transaction before
the grant change; it does not demonstrate cancellation of an in-flight
query, long-running snapshot ACL propagation, PostgreSQL replicas, network
partition or a production authenticated revocation service. Its result
must not be used to admit any Runtime task, mission or SSE route.
Issues #23/#251 OPEN, Stage45 NO_GO; no production schema, main merge,
deployment or real inference authorization.

## 4 November 2026 — require PostgreSQL primary and fresh read transaction

Review-only emergency B62 and synthetic session registration predicates
now include **`AND NOT pg_is_in_recovery()`** in the same SQL statement
that verifies `READ COMMITTED`, signer block, principal cutoff, and
signed-session state. On a PostgreSQL hot standby, this predicate must
fail closed rather than accepting a potentially stale revocation answer.

A separate disposable PG16 test checks that the actual restricted login
is connected to a primary (`pg_is_in_recovery() = false`), correctly
evaluates a signed registered session on `READ COMMITTED`, and denies
both predicates when the real JDBC transaction is switched to
`REPEATABLE READ`. All other negative tests, schema/role cleanup and
HTTP quarantine remain unchanged.

**Limits:** There is no second PostgreSQL node in this CI. This confirms
the query-level primary-only guard and actual snapshot isolation rejection,
**not** a real standby failover, stale-replica network routing, split-brain,
read-after-write consistency across services or in-flight cancellation test.
No production reader or customer API is activated. Stage45 NO_GO; issues
#23 and #251 remain open.

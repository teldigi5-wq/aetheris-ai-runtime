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

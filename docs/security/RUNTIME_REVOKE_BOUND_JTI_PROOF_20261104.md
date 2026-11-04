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

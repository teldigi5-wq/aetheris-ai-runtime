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

## 4 November 2026 — single-statement signed session and task-owner composition

A new **non-Spring, review-only** RuntimeVerifiedTaskSessionReadProbe
combines the existing task/tenant/project owner predicate with an exact
signed-JTI ACTIVE session registration, blocked signer check, and B62-G1
inclusive principal issuance cutoff. It checks verified RS256-derived
principal facts (UID, issuer, kid, signed JTI and signed issuance second)
against one **READ COMMITTED**, non-standby PostgreSQL SELECT EXISTS
statement. Each JDBC parameter is bound separately, with an explicit
transaction required and a bounded 2-second **test-only** query timeout.
A failed or unavailable authority returns DENY. A positive result is
**synthetic evidence only**; it is not a production grant.

The disposable ownerlab_ci PostgreSQL regression creates three
**transaction-local TEMP fixtures**, registering exactly one signed
session for one existing owner task. Its positive signed owner read must
coexist with negative checks for no session registration, a second
unregistered JTI of the same user, a different signed UID, wrong
task/project/tenant, legacy ownerless data, malformed selectors and a
signed token without a JTI. A blocked signer must deny even an ACTIVE
session. A principal emergency cutoff equal to the signed issuance
second must deny; issuance strictly after the cutoff can pass. An
individually revoked JTI denies despite an eligible task owner.
Implicit JDBC autocommit is rejected. The fixture is rolled back
without creating permanent task, user, session or policy objects.

**Important boundaries:** The SQL currently names synthetic session
registration and B62 compatibility tables, not an independently
certified Identity service or migrated production schema. The tests
share one disposable PostgreSQL transaction and use a database-owner
CI connection rather than an approved SELECT-only workload identity.
They do not prove consensus, resource-local leader fencing, reliable
cross-service JTI revocation, transactional HTTP/SSE emission, quorum,
physically inaccessible old primaries or production latency/error
budgets. An earlier positive owner-only probe remains an isolated
prototype and MUST NOT be interpreted as a session authorization grant.
The new method name canReadSynthetic is intentionally explicit.

No route/filter/agent is activated and no customer data, deployment,
main branch or real secret is touched. Keep Runtime issue #23,
Platform issue #251, all nine Stage45 attestations and release status
**NO_GO** until independently reviewed.

## 4 November 2026 — resource-local epoch/lease added to synthetic owner+JTI read

The previous review-only composite SQL could return a positive synthetic
decision when the verified owner, project membership and signed session
matched, even if no resource-local leadership state was present. The
non-Spring RuntimeVerifiedTaskSessionReadProbe now requires a matching
**synthetic local resource fence** JOIN in the **same** PostgreSQL SELECT:
one runtime row, endpoint selector, positive monotonic term, ACTIVE state,
an unexpired clock_timestamp()-checked lease, READ COMMITTED, and a
non-recovering PostgreSQL server. Its original UID, owner, project,
membership, signed-JTI ACTIVE registration, signer-block and emergency
cutoff checks remain. Invalid term/endpoint selectors and any missing SQL
authority fail closed. Exactly 15 JDBC parameters are bound.

The disposable ownerlab_ci PostgreSQL test creates a temporary local
fence row inside its existing rollback-only savepoint. It verifies
**DENY** for no fence row, stale requested term, mismatched endpoint,
nonpositive term, explicitly FENCED state, expired lease and missing
or revoked session. The positive case requires all of those synthetic
conditions to match concurrently. The test then changes the LOCAL
fence row to an obsolete endpoint/term. Requests expecting the newer
endpoint/term correctly DENY, but a caller selecting the *obsolete*
endpoint/term still gets a synthetic ALLOW. This is an intentional,
high-priority NEGATIVE counterexample; positive local-only behavior
does not demonstrate current global authority.

**Do not use for real admission.** Endpoint and term are method inputs,
not verified machine identity, signed leader authority or coordinator
attestations. The one-database SELECT does not lock a later task read,
response emission, journal mutation or SSE stream for its entire
lifecycle. Clock-based expiry alone cannot prove a stale old primary has
been physically fenced, and PostgreSQL standby denial does not prove
which writable primary is authoritative. The separate real physical
standby lab demonstrates that local fencing alone accepts stale writes
during an uncoordinated transition or coordinator outage.

There is still no quorum, primary-consistent authenticated authority,
cross-resource fencing contract, independently permissioned production
reader/writer, real Identity/JTI registry, approved migration,
HTTP/SSE/mission authorization integration or independent production
release review. No production bean, route, filter, dispatch, secret,
deployment or protected main is changed. Runtime issue #23,
Platform #251 and all nine release attestations remain open;
**Stage45 NO_GO**.

## 4 November 2026 — independently pinned RS256 synthetic local-fence lease

The review-only single-statement owner+JTI+resource probe previously took
its expected endpoint and leadership term as plain method arguments.
Even correct local SQL cannot authenticate arbitrary caller-selected
endpoint or term. That interface is now **removed**: canReadSynthetic
requires a privately constructed VerifiedLease returned by the separate,
non-Spring RuntimePinnedLocalFenceLeaseVerifier.

The verifier uses an operator-supplied **pinned RSA public key** (minimum
2048-bit modulus) and requires an actual RS256 JWS with the configured
key ID, exact issuer, sole audience, signed purpose and resource
runtime. It validates endpoint syntax, a positive signed term, issued-at
and expiration times, no future issuance, and lifetime no longer than
90 seconds. JWT headers are never used to fetch keys. A previously
verified lease is checked for expiry **again before and after** SQL;
malformed or missing provenance returns DENY. Signed principal expiry
is also rechecked. Production HTTP admission remains untouched.

In the opt-in PostgreSQL16 test, a **separate ephemeral 2048-bit RSA key**
signs synthetic review leases. The existing valid owner/JTI/fence test
uses only verified lease capabilities, plus signature-based negative
tests for forged keys, issuer, audience, kid, purpose, resource, malformed
endpoints, zero term, future issuance, excessive duration, expired
tokens and retained verified-lease expiry. The test intentionally
asserts a signed old-endpoint/old-term lease can still match an obsolete
local fence row. This is **unsafe in a real distributed system**, even
if its RSA signature is authentic.

These are NOT coordinator-signed production leases: the issuer here is
created solely inside disposable CI, and the private signing key never
leaves the synthetic test. A bearer lease is replayable until expiry and
is not bound to an independently authenticated workload, machine,
network or resource process. The implementation has no quorum,
commit-index/epoch freshness attestation, key rotation policy, recovery
path, safe cross-node failover, durable old-primary isolation, or real
resource mutation/HTTP/SSE serialization. A snapshot-based SELECT
cannot itself protect a later response/event/write against a concurrent
leadership change. Do NOT promote the method to a service or treat a
positive synthetic verdict as customer authorization.

No public API is enabled, no protected main is touched, and no
production secrets or deployments are involved. Runtime #23,
Platform #251 and all nine Stage45 attestations remain outstanding.
**Stage45 NO_GO**.

## 4 November 2026 — independently signed synthetic workload/lease subject binding

The existing RSA-verified synthetic resource-fence lease was a transferable
bearer value with no workload subject. The non-Spring
RuntimePinnedWorkloadIdentityVerifier now independently verifies a second
RSA-signed, short-lived workload assertion using a **different, pinned
2048-bit public key** in disposable CI. It enforces RS256, exact signing
key ID, issuer, sole audience, specific runtime purpose and resource,
a normalized signed workload subject, issued-at/expiration and a
90-second maximum assertion lifetime. Its immutable VerifiedWorkload
cannot be publicly constructed, and retained facts fail expiry checks.

The separately signed fence lease now requires a signed workload claim,
and its immutable VerifiedLease checks subject equality and BOTH
assertions' current expiry. The review-only task read prototype
requires a matching VerifiedLease and VerifiedWorkload before running
its existing same-statement task/tenant/project, ACTIVE JTI,
signer/emergency cutoff and local resource-fence SQL predicates. It
rechecks identity freshness **after** SQL. Raw endpoint/term arguments
were already removed; a token that lacks a verified workload match
cannot be used to obtain even a synthetic positive owner-read decision.

The disposable regression signs its local fence lease and workload
assertions with **different, randomly generated test RSA keys**.
It verifies valid same-subject assertions, rejects a validly signed
lease presented with a *different* validly signed workload identity,
rejects missing workload proof and tests forged workload signatures,
wrong issuer/audience/kid/purpose/resource, invalid subjects, future
issue time, expiry, overly long lifetimes and retained-token expiry.
The older physical PostgreSQL split-brain counterexamples remain
part of every draft-head CI run.

**Critical limitations:** matching two independently signed claims is
**not actual workload authentication**. Neither token is bound to a
verified TLS peer, client certificate, transport session, hardware
identity, privileged process, leader lease authority or coordinated
primary. A thief possessing both bearer tokens may replay them before
expiration; a genuinely signed old lease may continue matching an old
resource-local row after failover. This test has no mechanism to revoke
an in-flight response or SSE event, prove resource-linearizable
leadership, safely transport independent authority, or protect
multi-database writes. These are synthetic Java/JDBC proofs only,
not a functioning production Identity/authorization integration.

Both verifiers and the composite probe remain non-bean, are not exposed
through HTTP, and require no real customer tokens or secrets. The
existing early API filter still denies uncertified task, mission, SSE
and actuator routes. Runtime issue #23, Platform issue #251, the
unmerged draft PR and nine Stage45 release attestations remain open.
**Stage45 NO_GO**.

## 4 November 2026 — guarded resource effect in the same SQL statement

The Java synthetic authorization probe previously returned a Boolean
before any protected resource operation; a caller could incorrectly
use that result as a cached check-then-act permission. A **separate,
non-bean** RuntimeSyntheticFencedEffectProbe now uses an explicit
READ COMMITTED writable PostgreSQL transaction and a single
data-modifying WITH statement. It SHARE-locks the local resource-fence
row, checks the signed Identity principal, ACTIVE JTI registration,
live owner/project/tenant memberships, principal epoch and blocked signer
cutoffs, checks the signed workload and signed fence lease against each
other, and conditionally INSERTs a UUID-keyed synthetic effect row.
A duplicate effect identifier produces no second row. The method
**never commits** and returns success only for a pending transaction
effect; the caller must commit or roll back. SQL exceptions propagate
for mandatory caller rollback.

The actual disposable PostgreSQL16 CI test creates
synthetic_guarded_task_effects as a **TEMP table within its rolled-back
savepoint**, never in production. It verifies one positive pending
effect with the expected task, term and workload, and denies
duplicate IDs, another signed owner, unregistered JTI, missing
lease, mismatched signed workload, obsolete local term, ownerless
task, explicit local fence, expired local lease and revoked session.
All effects remain disposable; this does **not** establish a deployed
task mutation or production-reliable journal.

A critical deliberately **unsafe counterexample remains**:
when the local fence row is itself stale, a still-valid signed
old-leader lease and matching workload produce a pending INSERT
into that stale local database. Thus even a one-statement
resource-fenced write cannot establish global authority after
uncoordinated failover. The physical two-database lab independently
demonstrates this wider defect.

Other unresolved gaps: a real service reader/writer authority and
least-privilege privileges, authenticated workload transport, expiry
between statement and transaction commit, authority revocation
propagation, recovery from aborted transactions, durability on
failover, protection against session changes concurrent with a
transaction, robust ownership persistence, mission scope, SSE
emission and real task execution. A row SHARE lock on the local fence
does not lock an external coordinator, and the task/membership
predicates are not independently row-locked against concurrent
revocation. The SQL prototype is neither a reviewed migration nor
a deployed production authorization mechanism.

All protected APIs remain deny-only; no routes, customers, secrets,
protected main, release or deployment are modified. Runtime #23,
Platform #251 and nine Stage45 attestations remain open.
**Stage45 NO_GO**.

## 4 November 2026 — remove leadership-write privilege from disposable effect owners

The real PostgreSQL16 B62 physical-failover lab revealed an unnecessary
authority escalation surface: the non-login SECURITY DEFINER roles that
insert synthetic protected actions also held UPDATE(term) on both the
coordinator and local resource-fence rows. This supported PostgreSQL's
FOR SHARE row-lock privilege requirements but coupled the effect
execution identity to leadership mutation rights.

In the **disposable lab only**, the coordinator leader_fence,
b62_resource_fence and each physical b62_local_fence gain a
CHECK(lock_guard=0) smallint column. The respective effect-function
owners receive UPDATE(lock_guard) solely to retain the minimum
column-level privilege PostgreSQL uses when locking with FOR SHARE.
They no longer receive UPDATE(term), UPDATE(endpoint) or
UPDATE(expires_at). That lock_guard column is explicitly **not**
used to decide leadership or admit an action; it is constrained to
zero and changes to it cannot advance leadership.

Regression probes first prove SET LOCAL ROLE actually changes the
effective principal to each NOLOGIN function owner. They then attempt
actual UPDATE(term) or UPDATE(endpoint) under that effective identity
on the coordinator and **both** physical resource nodes. Every
attempt must fail, and original authority term values are checked.
Existing restricted login tests, valid signed/synthetic actions,
non-replay behavior, stale old-primary ALLOW counterexamples,
coordinator-outage behavior, real physical promotion and cleanup
remain mandatory in the same draft workflow.

**No production privilege migration is proposed.** PostgreSQL's
column-level UPDATE capability on the constrained lock_guard still
permits non-authority writes and potential lock-contention/DoS by an
actor already holding the function-owner identity. Least privilege
for a real system therefore requires a reviewed database role model
and independent owner/signer/failover operator controls. The lab
does not prove separation across independent real infrastructure,
transport authorization, globally linearizable fencing, atomic
failover or eventual event emission. All protected API filters remain
quarantined. Runtime #23, Platform #251 and all nine Stage45
attestations remain open; **Stage45 NO_GO**.

## 4 November 2026 — observed in-flight SQL action versus leadership transition

A new **real PostgreSQL 16 concurrent transaction** experiment in the
disposable B62 physical-standby script tests a protected effect while
a separate coordinator writer attempts to change authority. The
restricted synthetic actor opens an explicit transaction, executes
the existing SECURITY DEFINER protected action for term 2, then
blocks in pg_sleep(12) while retaining the two checked FOR SHARE
locks on the coordinator-local leader_fence and b62_resource_fence
rows. The harness first polls pg_stat_activity for the expected
actor and PgSleep wait event so it never assumes an in-flight
critical section merely because a shell process was launched.

While the first transaction is still open, independent coordinator
UPDATE(term) attempts are made against **both** checked rows with a
400 ms lock timeout. Each must fail **specifically** with PostgreSQL's
"canceling statement due to lock timeout" error; unrelated SQL,
permission or transport errors cannot count as a successful denial.
Only after the in-flight actor commits does CI verify its recorded
effect, the old term still being 2, and the successful existing
transition to term 3. This proves that the co-located row locks
serialize the synthetic effect with competing leader/resource-term
updates on this **single authority PostgreSQL server**.

The test increases the expected synthetic action count to account
for the one additional protected effect. Existing genuine physical
replica promotion, divergent writable-primary, coordinator-outage
and stale local resource ALLOW counterexamples are intentionally
unchanged and mandatory. The recorded action is synthetic and the
entire server/network lab is destroyed after CI.

**Limits and release blockers:** A valid effect can still finish
before the leadership transition; the test does not abort an
already authorized action or prove revocation after authorization.
The coordinator and protected effect share **one database here**,
so row-lock serialization does not fence two independently writable
physical primaries, a disconnected resource or an external event
sink. No production service identity, real task mutation, SSE
stream, actual distributed quorum/leader lease, cross-resource
atomicity, verified client transport, writer migration or
release-level security evidence exists. No protected API route
is enabled. Runtime #23, Platform #251 and all nine Stage45
attestations remain open. **Stage45 NO_GO**.

## 4 November 2026 — real concurrent detached-resource NO_GO proof

The preceding test established a useful, **limited** positive property:
an effect and leadership transition are serialized when both
authority and effect run on **one PostgreSQL database**, holding
FOR SHARE locks on the same two authority rows. This is not the
topology of a real independent resource node.

The new B62 negative experiment uses **separate running PostgreSQL16
processes**: the coordinator holds the authoritative leader_fence
while the physically promoted resource database holds its own
b62_local_fence and b62_local_effects. On the promoted resource,
an independently authenticated disposable SQL writer begins a
transaction, invokes the local SECURITY DEFINER effect function
with signed-transport-independent synthetic term 4, and pauses
inside pg_sleep(12) without committing. The harness observes its
actual PgSleep wait event in that resource's pg_stat_activity before
trying to change leadership. No assumption about scheduling alone
is accepted as a concurrency proof.

While the resource transaction remains open, an administrator
advances the **separate coordinator** from term 4 to term 5, with
a 400 ms lock timeout to reject unexpected blocking. This update
succeeds: the resource-local row lock does not serialize with
the remote leader-fence update. A fresh coordinator-based client
route check correctly rejects stale term 4, yet the detached
resource remains at local term 4. When the resource transaction
commits, CI confirms that the already-authorized old-term action
was recorded with term 4. **This is an intentionally observed
unsafe outcome and must never be interpreted as a release grant.**

The existing negative experiments continue to demonstrate stale
old-primary effects and local writes during coordinator outage.
Expected synthetic per-resource effect counts were updated to
account for the one additional unsafe committed effect. The
disposable containers and network must still be destroyed by CI.

**Engineering implication:** An authenticated or signed lease,
a fail-closed API route, and a SQL row lock on an independent
resource are individually insufficient to prevent stale
in-flight effects during externally coordinated leadership
transitions. A production solution must define a resource
cutover barrier that closes old writers before transferring
authority, verify the barrier across partitions/failover, and
safely handle in-flight work, data durability and downstream
non-database side effects. A proposed protocol needs explicit
failure models and hardware/transport evidence; none is
implemented or claimed by this lab.

No production routes or workers were activated. The PR remains
draft/unmerged, Runtime issue #23 and Platform issue #251 remain
unresolved, and Stage45's outstanding release evidence is not
satisfied. **Stage45 NO_GO.**

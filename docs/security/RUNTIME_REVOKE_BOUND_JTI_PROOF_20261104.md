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

## 5 November 2026 — disposable ordered-cutover admission rehearsal

The previous B62 experiment proved that an in-flight resource transaction
can commit an outdated term-4 effect after a separate coordinator
already advanced to term 5. That is a confirmed *failure of the
prototype's distributed safety assumptions*, not a release pass.

The new negative-first lab introduces a **shell-only review gate** for
synthetic effects. It requires all of these fresh observations before
calling the restricted resource writer: the old disposable PostgreSQL
container exists and reports stopped, the independent coordinator is
reachable and confirms the promoted endpoint at term 5 with an
unexpired lease, and the promoted physical resource is a writable
primary with a matching local term 5. Missing observations, an
unreachable coordinator and mismatched terms fail closed.

Actual disposable PostgreSQL16 CI now verifies the gate denies when
the old primary is merely disconnected from its Docker bridge but
remains running, when the coordinator is stopped, after the old
container stops while the coordinator is still unavailable, and after
coordinator recovery while the new resource still has term 4. An
operator-controlled synthetic local update to term 5 **after**
stopping the old process permits one new, recorded term-5 effect.
Stopping the coordinator a second time denies further gate calls.
Existing physical stale-writer and coordinator-outage fail-open
counterexamples are retained: raw SQL access to the resource is
**not** constrained by the shell review gate.

**This is NOT a verified production cutover barrier.** The gate's
checks are separate operations with race windows; Docker's running
flag is not independently authenticated or durable fencing evidence.
The temporary operator supplies neither a cryptographically bound
fencing acknowledgement nor a quorum certificate. No guarantee
exists that a separate physical writer, hidden replication service,
external sink or downstream effect is stopped; no session-drain,
durable ack journal, lease monotonicity across service restart or
safe authority transition has been constructed. The resource-local
effect function remains directly callable with old credentials;
this rehearsal only models the required deny-first ordering for
a future independent resource-owned implementation.

A production design requires verified ownership and writer
revocation, mandatory enforcement at *every* protected resource,
durable/independently attested fencing acknowledgements, bounded
in-flight effect cancellation or safe completion, authority epoch
persistence, unavailable-coordinator denial, auditable cutover
state transitions and failback/rollback procedures. No Runtime
task/mission/SSE route, customer database or production credential
was enabled or modified. Runtime #23, Platform #251 and nine Stage45
attestations remain outstanding. **Stage45 NO_GO.**

## 5 November 2026 — non-atomic cutover TOCTOU and SQL bypass (NO_GO)

The prior disposable cutover rehearsal provided only a **shell-client**
gate: it checked that the old PostgreSQL container was stopped,
that the coordinator reported promoted resource term 5, and that
the promoted resource-local fence also reported term 5. It then
called an independently accessible protected-effect SQL function.
No step was an atomic, resource-owned, globally authoritative
admission barrier. Passing this rehearsal is *not* a production
authorization verdict.

Two new **real PostgreSQL16 negative tests** independently establish
why these observations are insufficient:

1. **Check/write TOCTOU.** After the shell gate checks all three
   conditions, a CI-only barrier makes it pause *before* its real
   effect call. The test verifies that barrier was reached, then
   advances the separate coordinator from term 5 to term 6.
   The resource still retains local term 5. On releasing the
   barrier, the original gate invocation successfully inserts
   an effect whose stored term is **5**, despite live coordinator
   term **6**. A fresh invocation of the shell gate correctly
   rejects the mismatch, but cannot undo the stale effect.
   CI emits
   AETHERIS_B62_CUTOVER_GATE_CHECK_THEN_WRITE_RACE_NO_GO_PASS.

2. **Unmediated local SQL during coordinator outage.** Following
   the ordered cutover rehearsal, the coordinator is stopped.
   The shell gate refuses another call. Nevertheless the separate
   restricted database actor directly invokes the local
   SECURITY DEFINER effect function at term 5; the effect is
   inserted and is independently counted on the physical resource.
   CI emits
   AETHERIS_B62_CUTOVER_DIRECT_SQL_BYPASS_OUTAGE_NO_GO_PASS.

These tests intentionally create only synthetic database rows in
disposable Docker-isolated CI. The test-only pause hook is keyed
to a single synthetic request ID and cannot activate real Runtime
routes or tenant operations. Both counterexamples remain required
by the physical-standby workflow's source guards. Earlier positive
within-one-database row-lock serialization and prior unsafe detached
in-flight and stale old-primary examples remain intact.

**Minimum design obligations before any release proposal:**
the protected **resource itself**, not an optional shell/API
pre-check, must unconditionally enforce authenticated writer
identity, monotonic non-replayable authority, fenced cutover and
concurrent operation serialization; an independently verifiable
and durable old-writer shutdown/epoch barrier must precede new
admission; coordinator loss, split brain, lease replay and failback
must deny unsafe operations or trigger externally enforced fencing.
A final design must cover all side-effect sinks, event streams,
transaction commit/durability and workload credential revocation,
and supply real cross-node fault-injection evidence. Signatures,
Docker state, local rows and manual checks alone do not satisfy
these obligations. No such production enforcement has been built.

Runtime PR #257 is still a draft review; Runtime issue #23 and
Platform issue #251 remain unresolved, production HTTP/Mission/SSE
remain quarantined, and the nine Stage45 attestations have not
been supplied. **Stage45 NO_GO.**

## 5 November 2026 — resource-enforced local CLOSED gate, transaction drain and restart persistence

The prior shell-only cutover admission was bypassable through the
resource's callable SQL effect function. This new **disposable-only**
PostgreSQL16 experiment adds a resource-local b62_writer_gate table,
created with state OPEN on **each** independently writable physical
resource database. The existing SECURITY DEFINER
b62_local_try_effect function now **must** JOIN that row, require
state OPEN, and acquire FOR SHARE locks on both the local fencing row
and the gate row in the same transaction as its synthetic effect
INSERT. The gate is not an HTTP pre-check or an optional shell policy.

The restricted writer login can only EXECUTE the narrow function;
it has no direct INSERT into the effects table or gate UPDATE.
The separate NOLOGIN function owner receives SELECT and a constrained
UPDATE(lock_guard) permission needed for FOR SHARE, but no permission
to modify admission state, leader endpoint or term. Only the
disposable PostgreSQL operator role can change OPEN to CLOSED.

CI deliberately **retains the earlier unsafe tests while the gate
is OPEN**: split-brain old-primary effect acceptance, stale in-flight
effect commit after remote coordinator advancement, coordinator-loss
local-effect acceptance and optional shell admission bypass.
Adding a local gate does NOT silently make any of these safe.

After those negative examples, a separate physical database writer
opens a transaction, executes one permitted term-5 effect and sleeps
with the gate and local fence locked. The harness observes its real
pg_stat_activity wait event. A competing operator attempts to
close the gate with a 400 ms lock timeout, which must fail
**specifically** on the row lock rather than for an unrelated error.
After the writer's COMMIT, the administrator commits CLOSED.
The same restricted writer's direct SQL function then returns
false and the effects table retains its prior row count. Attempts
to change the gate by the restricted login or the function-definer
identity fail. CI emits:

- AETHERIS_B62_RESOURCE_GATE_DRAIN_LOCK_SERIALIZES_PASS
- AETHERIS_B62_RESOURCE_LOCAL_GATE_CLOSED_DENIES_DIRECT_SQL_PASS

The disposable promoted PostgreSQL16 process is then shut down and
restarted using its **same persisted data directory** with the
independent coordinator still stopped. The committed gate remains
CLOSED, a fresh restricted database session receives false from
the original SQL effect function, and no new journal row appears.
CI emits:

- AETHERIS_B62_RESOURCE_CLOSED_PERSISTS_RESTART_AND_OUTAGE_PASS

**Precisely established property:** while this **specific physical
resource** remains uncompromised and CLOSED, its existing restricted
SQL function does not admit another effect; an administrator cannot
commit the local CLOSED transition while an earlier protected
transaction retains its gate lock; closure persists across the
exercised process restart. This is a bounded local containment and
durability proof, not a globally valid authority certificate.

**Open release blockers:** the old independent physical primary may
still be alive and OPEN; a remote coordinator's term change does
not automatically or atomically close either gate; an independent
resource can still write through the function during coordinator
outage **when the local gate is OPEN**. The operator is not a real
authenticated multi-resource fence controller and supplies no
cryptographically bound proof of old-writer shutdown, no durable
quorum approval, no cross-resource transaction or distributed
revocation, no independent side-effect sink barrier, and no certified
failover or rollback. Administrators can manually reopen this local
gate; that transition is not a validated production protocol.

The lab changes only the existing disposable physical-standby script,
its review workflow and this documentation, with no production schema
migration, live worker, runtime route, key or user database change.
Runtime issue #23, Platform issue #251 and the nine Stage45 release
attestations remain outstanding. **Stage45 NO_GO.**

## 5 November 2026 — terminal local CLOSED gate against DML reopens

The previous B62 physical lab added a resource-enforced local
OPEN/CLOSED writer gate, locked by the restricted SECURITY DEFINER
effect function in the same PostgreSQL transaction as each synthetic
effect. It demonstrated that closing the gate drains existing local
transactions and that a committed CLOSED gate survives an actual
PostgreSQL16 process restart while the coordinator is unavailable.
However, a privileged local database operator could still
accidentally reopen a CLOSED gate at the same local fencing term.

This review-only iteration adds local PostgreSQL trigger guards on
**both** disposable physical resource databases:

- A BEFORE UPDATE OR DELETE row trigger rejects a CLOSED-to-OPEN
  state transition, a resource-key change, and gate-row deletion.
  Each denial uses a distinct, checked SQLSTATE 23514 error marker.
- A BEFORE TRUNCATE statement trigger refuses to clear the gate
  table, even when the operator can normally issue TRUNCATE.
- Trigger-helper EXECUTE permissions are revoked from PUBLIC.
  The existing restricted writer and NOLOGIN effect-function owner
  still cannot update the gate state or authority term.
- After the promoted resource is CLOSED, physical PostgreSQL16 CI
  attempts a genuine operator UPDATE reopen, DELETE, and TRUNCATE;
  each must fail with its **specific expected** trigger error.
  The gate row must remain unique and CLOSED and the effect journal
  must not grow.
- The same resource process is stopped and restarted with the
  independent coordinator still offline. CI then attempts a new
  operator reopen, which must fail with the same terminal-state
  trigger error. A fresh direct SQL effect invocation must also
  return false with no new synthetic effect row.

CI proof markers:

- AETHERIS_B62_CLOSED_GATE_RESISTS_ADMIN_DML_REOPEN_PASS
- AETHERIS_B62_CLOSED_GATE_REOPEN_AFTER_RESTART_DENIED_PASS

These tests prevent **ordinary SQL DML** from reversing local
closure in the disposable prototype. They do NOT defend against
an owner/superuser with DDL privileges disabling triggers, replacing
the table, restoring an old database snapshot, changing the data
directory, or otherwise bypassing this synthetic gate. A terminal
CLOSED state also intentionally cannot be used as an unreviewed
failback mechanism: a real service would require an independent,
authorized new-resource lifecycle and durable cutover evidence.

Critically, the lab still reproduces unsafe writes from a different
physical primary whose gate stays OPEN, stale in-flight operations
after an independent coordinator term change, and direct SQL during
authority outage before the local closure. Terminal local closure
cannot force remote shutdown or establish globally linearizable
leadership. No credential-bound fencing acknowledgement, quorum,
mandatory cross-resource gate controller, production DDL migration,
HTTP/SSE/Mission release or real task effect has been created.
Runtime #23, Platform #251, PR #257 review and the nine Stage45
release attestations remain pending. **Stage45 NO_GO**.

## 5 November 2026 — transactional local closure receipt and false remote-ACK counterexample

The disposable B62 physical PostgreSQL16 lab now creates a
b62_closure_receipts table **on each independent resource**. A
resource-owned AFTER UPDATE trigger observes an OPEN-to-CLOSED
transition on its own b62_writer_gate, selects and SHARE-locks that
resource's local fencing row, and inserts exactly one record of:

- resource_key, observed node_identity and positive fence_term;
- a newly generated UUID receipt_nonce (correlation only);
- recorded_at timestamp.

The receipt insert and gate update happen in **one PostgreSQL
transaction**. The receipt is visible to other transactions only
after commit; neither a failed lock-timeout nor an explicitly
rolled-back closure produces a durable receipt. A repeated,
same-state CLOSED update does not reissue a UUID or insert another
receipt. The restricted resource login and separate NOLOGIN
effect-function owner cannot INSERT a forged receipt: neither
identity is granted receipt-table write permissions.

In the actual disposable PostgreSQL16 regression the promoted node
closes at its observed local term 5. CI verifies the receipt's
resource ID, exact promoted node, term and UUID format; verifies the
single row after closing; then shuts down and restarts the real
PostgreSQL process while the coordinator remains down. The exact
receipt contents, its UUID, and the resource's CLOSED state persist
across that restart. This establishes local transaction atomicity,
event-generation idempotence and storage persistence only.

**Negative failback evidence:** An acknowledgement from the promoted
node is NOT evidence that a different old writable primary remains
fenced. The physical lab restarts the previously stopped, isolated
old primary, confirms its own resource gate is still OPEN and it
has NO closure receipt, and verifies the old fenced endpoint initially
denies the old writer. An intentionally privileged and therefore
unsafe local operator then restores the old endpoint/term; the old
restricted writer successfully records another term-2 effect despite
the promoted node's durable term-5 receipt. CI marks this outcome
AETHERIS_B62_PROMOTED_RECEIPT_CANNOT_FENCE_PRIVILEGED_FAILBACK_NO_GO_PASS.

The local positive markers are:

- AETHERIS_B62_CLOSURE_RECEIPT_ROLLBACK_ATOMIC_PASS
- AETHERIS_B62_COMMITTED_CLOSURE_RECEIPT_BOUNDS_NODE_TERM_PASS
- AETHERIS_B62_CLOSURE_RECEIPT_DUPLICATE_EVENT_IDEMPOTENT_PASS
- AETHERIS_B62_CLOSURE_RECEIPT_DURABLE_RESTART_PASS

**Critical trust limitations:** The node identity is copied from
a local database row; it is NOT mTLS/SPIFFE/hardware attestation.
The receipt is NOT signed, is transferable/replayable as data, and
has NO independently authenticated issuer, remote-writer shutdown
evidence, quorum, replicated monotonic anti-replay journal, cutover
challenge validation or post-commit delivery proof. A privileged
database owner/superuser can alter the schema, disable triggers,
change stored receipt data or restore old snapshots. Resource-local
receipt presence cannot be consumed as a production release token.

Next review-only work must specify an independently authenticated
resource-signing workload, pinned trust roots, exact release
epoch and request/challenge binding, durable one-time consumption,
bounded failure semantics for lost/duplicated ACKs, and a cutover
protocol that demonstrates **ALL old writer paths** are fenced
before assigning new authority. A signed receipt from only one
resource cannot prove remote fencing. Real customer/task effects,
Runtime APIs, Mission/SSE routes, production keys and release
controls remain unchanged. Runtime #23, Platform #251 and nine
Stage45 attestations remain unresolved. **Stage45 NO_GO.**

## 5 November 2026 — separately pinned signed closure ACK and PG16 replay journal

The physical B62 resource gate can commit one *local* closure receipt,
but its row is not a global shutdown or transfer-of-authority
certificate. This iteration adds two production-tree **non-Spring,
review-only** classes; neither is wired to real runtime routes:

1. RuntimePinnedClosureAckVerifier requires a **separate pinned
   >=2048-bit RS256 public key**, exact trusted key ID, issuer,
   sole audience, purpose runtime-resource-closure-review and
   resource runtime. The signature binds the canonical resource-node
   string (also required as subject), positive resource term,
   canonical receipt UUID, and exact challenge UUID. Issued-at
   cannot be in the future, expiration must be in the future and
   signed lifetime must not exceed 90 seconds. A private-constructible
   VerifiedAck must match independently supplied expected node,
   term, receipt and challenge; retained objects recheck expiry.
   Other RSA keys, issuer, audience, key ID, purpose and mismatched
   signed facts are denied.
2. RuntimeSyntheticClosureAckConsumptionProbe accepts only a
   VerifiedAck matching caller-pinned expectations, in an explicit
   writable READ COMMITTED PostgreSQL transaction. One data-modifying
   statement requires a matching *locally recorded* receipt, a
   registered unexpired challenge and a CLOSED local resource gate,
   while locking the matching SQL rows. It inserts a consumption
   record only once, with unique challenge ID and receipt UUID
   constraints. The same JWT/challenge and a newly signed challenge
   for an already-consumed receipt both deny. A post-insert signature
   expiry is treated as a **mandatory rollback exception** rather
   than a false return that could leave an uncommitted effect.
   The function itself never commits or opens an HTTP/API route.

The opt-in Java regression uses independent randomly generated
RSA keys for identity, resource lease, workload and closure ACK.
It runs actual PostgreSQL16 transactions against an isolated,
uniquely named **disposable schema**. Unlike TEMP-only assertions,
the consumption ledger is COMMITTED, then tested from a freshly
opened JDBC connection to establish cross-session replay denial.
The test also verifies gate OPEN denial, wrong node/term/challenge,
mismatched signature authority, scope, signed expiry and lifetime,
and exactly one consumed row. The schema is dropped in a finally
cleanup block, and CI checks there are no leaked b62_ack_ schemas.

**Precisely established:** independent public-key signature and
claim verification, strict signed-to-expected-facts equality,
local CLOSED-gate SQL correlation, and one-time consumption of a
COMMITTED ledger entry across JDBC sessions in the disposable
test. This is *not* proof of a resource's real cryptographic
identity or that a remote sender had possession of the private
key on the resource. The test key is a synthetic CI signer.

**Not established:** robust across-process database restart,
quorum-backed global replay prevention, trusted signing key
custody and rotation, certificate-to-workload binding, challenge
issuance by a protected cutover authority, availability and
delivery guarantees for ACKs, durable revocation across snapshots,
concurrent multi-database cutover, or authority to release any
other physically writable old primary. A previously valid
attestation can be copied and replayed into an entirely
uncoordinated store unless replay protection and cutover
admission are enforced *at every protected resource*. A malicious
privileged database administrator may destroy or rewrite the
local replay ledger. A forged external remote-fencing claim still
cannot be accepted as a reason to open Runtime Task, Mission,
SSE or actuator routes.

All prior real physical-standby counterexamples remain mandatory,
including privileged old-primary failback despite a promoted
node's durable local closure receipt. No production data, schema,
keys, services, protected branches, releases or API gates change.
Runtime issue #23 and Platform issue #251 remain open; PR #257
is review-only. **Stage45 NO_GO.**

## 5 November 2026 — concurrent replay serialization and rollback recovery in real PG16

The prior signed ACK test confirmed a committed consumption journal
remained visible from a new JDBC connection. This iteration tightens
the **same disposable PostgreSQL16 lab** around real concurrency and
failed transactions, using the existing one-time challenge and receipt
rather than changing or recreating a closure receipt.

After committing an observed local CLOSED gate, transaction A consumes
a correctly verified synthetic RS256 acknowledgement, then explicitly
ROLLS BACK. A subsequent use of the **same** immutable signed receipt
and challenge succeeds: an abandoned transaction cannot permanently
burn a claim that never committed.

Transaction A then inserts the pending one-time claim and remains open.
A distinct, authenticated-by-test-fixture JDBC session B invokes
the exact same conditional consumption SQL on another thread. CI first
retrieves B's actual pg_backend_pid() and polls **pg_stat_activity**
until PostgreSQL exposes a **Lock** wait; merely starting two threads
does not count as concurrency evidence. A commits while B is blocked.
B then completes and returns **false**, because the unique challenge
and receipt entry was already committed. A second same-transaction
claim is also denied. The test joins the competing thread in a
bounded interval, checks for SQL exceptions, and closes both JDBC
connections; the surrounding existing test still verifies that
another independently signed challenge cannot reuse a consumed receipt.

The workflow guards now require the observed lock-wait assertions,
rollback-retry case, losing replay result and bounded worker join.
The uniquely named b62_ack_ schema is removed after the test, with
CI checking that no disposable schema remains.

This is evidence for **single-PostgreSQL-database unique-index
serialization and rollback semantics**, not evidence that the
challenge issuer is authenticated, the synthetic public key belongs
to a physically fenced resource, ACK transport is trusted, or a
distributed ledger remains monotonic after failback, restored
snapshots, physical data loss or independent partitions. The
separate B62 physical-standby lab continues to prove privileged
old-primary restart and stale local writer acceptance despite a
different node's durable closure receipt. No real Runtime route,
service task effect or leadership transfer has been activated.
Runtime #23, Platform #251 and Stage45 attestations remain open.
**Stage45 NO_GO.**

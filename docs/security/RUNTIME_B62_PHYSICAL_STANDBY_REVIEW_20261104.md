# B62 actual physical streaming standby — S0 review only

**4 November 2026. DRAFT; Stage45 NO_GO.**

The existing Runtime B62 compatibility test requires PostgreSQL READ COMMITTED
and NOT pg_is_in_recovery() in both synthetic decision statements. Its
previous 80/80 tests only ran on one PostgreSQL primary. This separate CI
workflow now uses **two distinct real PostgreSQL 16 server processes** on
a disposable, internal Docker network with no published host ports.

A replication-only PostgreSQL role runs a physical pg_basebackup with
standby.signal, after which the second node must confirm
pg_is_in_recovery() = true and read an initially ACTIVE synthetic session.
The exact conjunction of session registration, principal epoch and signing
key exclusion is evaluated with and without the primary-only predicate.

The test explicitly pauses real standby WAL replay, commits a synthetic
session revocation and signing-key block on the primary, and checks the
standby still holds stale ACTIVE state. A query without the standby guard
would incorrectly return true; the guarded query **must return false**.
The primary also denies. After WAL replay resumes, both changes must
replicate and all guarded checks continue to deny.

The test uses static synthetic records, **not real signed JWTs or customer
tokens**. Separate real-RSA/JDBC authorization tests continue in the
existing consolidated workflow. Temporary database passwords are generated
per run, only used inside disposable nodes, and not uploaded as artifacts.
EXIT cleanup destroys both containers and the Docker bridge. This does
not exercise production database replicas, multi-region failover, true
mTLS/SPIFFE identity, promotion/split-brain, DNS routing, quorum, network
partition, primary re-election or customer-session revocation.

No Runtime API admission or production Java is changed. Runtime issue #23,
Platform issue #251 and all nine Stage45 release attestations remain
OPEN / NO_GO. Human security and operations acceptance is still required.

## 4 November 2026 — actual promotion / old-primary divergence counterexample

The same disposable two-node CI lab now goes one step further, **without
changing production application code**. After replay catches up, a newly
registered synthetic token for UID 77 is present on BOTH nodes. The actual
`pg_ctl promote -w` command promotes the physical standby **while the old
primary is deliberately left running** for a brief negative-only rehearsal
on the isolated internal Docker network.

The test verifies that BOTH servers report
`pg_is_in_recovery() = false` and initially approve the matching synthetic
session. It then commits the UID 77 session revocation and signing-key
block **only on the newly promoted primary**. The old primary still sees
an ACTIVE session and its `NOT pg_is_in_recovery()` guarded predicate
incorrectly returns TRUE, while the new primary returns FALSE. This is a
real divergent-database *counterexample*: merely verifying that a
connection talks to *some* PostgreSQL primary is **not** sufficient to
prove it talks to the authoritative leader.

The script explicitly fences the old primary container with `docker stop`
and confirms that the promoted database remains available and denies the
revoked record. Cleanup still destroys all nodes and the network.

**This intentionally exposes a possible old-primary stale ALLOW within
a disposable test. It is NOT a safe automated promotion algorithm.** Real
production readiness requires an independently trusted leader/lease or
fencing authority, protected routing to the currently authorized primary,
no permissive fallback, durable revocation recovery semantics, quorum/
split-brain and read-after-write tests, rollback and operator approvals.
An old primary could still incorrectly approve before fencing: therefore
Stage45 remains **NO_GO**. This test cannot be counted as one of the nine
production release attestations. Runtime #23 and Platform #251 remain OPEN.

## 4 November 2026 — separate, failure-closed leader/term test

An isolated **third PostgreSQL16 container** now supplies a disposable
coordinator `leader_fence` record with current endpoint, increasing term
and expiry. A **separate SCRAM-authenticated SELECT-only role** queries this
coordinator from the candidate database container. The lab requires both a
current matching coordinator record and the original SQL decision to be
positive. A stale old-primary record therefore cannot pass the synthetic
routing gate after the coordinator points to the promoted node, despite
the old primary still reporting `pg_is_in_recovery()=false`. The test
also verifies a different currently active session on the promoted node,
denies stale term, prevents unauthorized leader-row writes, and denies
when the coordinator record expires or the coordinator is stopped. Upon
restart a fresh lookup is possible. All three containers and the internal
network are destroyed.

**Not a production solution:** a separate coordinator lookup and session
lookup are two sequential operations with an unresolved time-of-check-to-
time-of-use race. This lab has no quorum, split-brain-proof consensus,
durable hardware fencing, cryptographically bound endpoint identity,
signed leases, mTLS, service networking policy or production failure
domains. It cannot validate data continuity across a failover or prove
that session revocation was durably synchronized. During initial
promotion without coordinator update, stale authorizations remain a
risk. Production must fence or invalidate old authority *before*
resuming authorization on the new leader and deny all uncertain terms.
Keep Stage45 NO_GO and Runtime #23/Platform #251 open.

## 4 November 2026 — atomic resource-owned fencing proof (test-only)

The disposable three-PostgreSQL-container lab now adds a **synthetic**
protected resource within the independent coordinator database. This is
**not** the production Runtime task store, Identity/JWT path, or a
distributed fencing implementation.

The test creates separate coordinator leader_fence and protected-resource
b62_resource_fence rows, a unique-request b62_protected_actions journal,
two SCRAM-authenticated caller roles, and an independent NOLOGIN function
owner. Callers receive function EXECUTE only, not direct table mutation.
The fixed-search-path SECURITY DEFINER function maps session_user to its
known endpoint and requires READ COMMITTED. It locks *both* fencing rows
with SELECT FOR SHARE before appending the protected action inside the
**same PostgreSQL transaction**.

An eligible promoted actor at term 2 succeeds. Old-leader and wrong-term
operations deny. After a coordinator-only switch toward the stale
former primary at term 3, the independent resource fence still pins
the promoted actor to term 2: neither actor can append. The disposable
test owner then atomically updates the coordinator and resource fences to
promoted actor term 4. The new term succeeds; obsolete terms, the former
leader, direct table writes, and a replayed request ID fail. The script
asserts two journal events and destroys all disposable infrastructure.

**Exact corrected-head proof:** 9f732e35a2281c512e9800509c2a293e507d6c1a,
[physical-standby CI #41824507420](https://github.com/teldigi5-wq/aetheris-ai-runtime/actions/runs/41824507420)
SUCCESS, including marker
AETHERIS_B62_RESOURCE_OWNED_MONOTONIC_FENCE_ATOMIC_ACTION_PASS.
The earlier e35aa5c candidate failed shell syntax before database tests;
it is **not** passing evidence and was corrected.

**Limits:** This serializes a coordinator update and synthetic INSERT only
because both fencing records and the effect are co-located in the **same
disposable PostgreSQL database**. It does not establish atomic fencing for
independent resource stores, quorum, consensus, split-brain-proof promotion,
real workload identity/mTLS, production database role ownership, failover
read-after-write, HTTP/SSE emission fencing, or concurrent load testing.
The database owner can bypass test-only restrictions. No production Java,
live endpoint, migration, real secret, customer record or protected main is
changed. Runtime issue #23, Platform #251 and all nine Stage45 release
gates remain open; **Stage45 NO_GO**.

## 4 November 2026 — physically separate resource database fencing limits

The preceding proof uses ONE PostgreSQL coordinator database for its
leader, resource-fence and effect journal. This follow-up extends the **same
disposable three-container CI lab**, after genuine streaming standby
promotion and intentional split-brain, to install a separate synthetic
protected-resource schema on EACH of the two diverged PostgreSQL servers.
No real task, session, customer database, production migration or Runtime
API is used.

Each physical server independently receives a b62_local_fence row, a
unique-request b62_local_effects journal, a NOLOGIN function-owner role,
and a SCRAM-authenticated writer. The writer can EXECUTE a definer
function but cannot UPDATE the fence or directly INSERT the journal.
The resource-local function pins its own endpoint, READ COMMITTED, real
non-standby state, current local term and SELECT FOR SHARE. The definer
has narrowly scoped SELECT, UPDATE(term) (needed for its locking query)
and journal INSERT permissions; the caller has no underlying table DML.
Distinct test-only passwords are used on the two servers.

**Reproduced unsafe case:** the coordinator already names the promoted
server at term 4. The old physical primary, whose independent local
resource row remains at term 2, still accepts a term-2 synthetic write.
This proves that an external coordinator record or the previous
same-database atomic fencing test does NOT by itself fence an independently
running stale resource. The promoted resource independently accepts term 4.

**Bounded local denial:** the test then explicitly updates the OLD
resource row to term 4 with the promoted endpoint. The old database rejects
both term 2 and term 4 calls; an actual Docker network disconnect leaves
this resource-local denial intact when probed inside the container over
loopback. The promoted resource rejects a wrong password, direct
underlying table writes, wrong terms and duplicate request IDs. The
old database's accepting write occurred BEFORE this local update; no
safe automatic transfer or absence of an authorization window is claimed.

**Second unsafe case:** after stopping the independent coordinator,
the promoted physical resource still accepts a locally matched term-4
synthetic action. A resource-local fence that does not independently
verify current authority CANNOT satisfy fail-closed coordinator-outage
policy. Neither stale-local admission nor coordinator-outage admission
is acceptable for production; they are deliberate negative findings.

The exact corrected-head physical workflow
[41826354325](https://github.com/teldigi5-wq/aetheris-ai-runtime/actions/runs/41826354325)
completed SUCCESS at commit eb1832570400e7adbe9b33ef809f5c36b2144ef2
and printed the separate-database stale-old-ALLOW, partition-fenced DENY,
coordinator-outage unsafe-ALLOW and Docker cleanup markers. Earlier
review attempts identified and repaired (a) missing NOLOGIN definer
UPDATE(term) row-lock rights and (b) an existing pg_isready initialization
race by requiring successful SQL SELECT 1 on the specific test databases.
Those failed attempts are NOT green evidence.

**Unresolved:** Real failover requires authoritative fencing of EVERY
prior leader/resource before new authority is admitted, or universal
denial while that cannot be proved. There is no atomic transaction across
these three DB processes; tests intentionally demonstrate the gap.
Durable consensus/quorum, cryptographically authenticated workload and
resource identities, revocation history synchronization, network
fault domains, permissioned operator approval, HTTP/SSE response
lifecycle, fencing on physically unreachable old nodes and actual
application JDBC integration remain unproven. Production authorization,
Runtime issue #23, Platform #251, all nine Stage45 gates and release
readiness remain NO_GO. This is not a deployment or merge.

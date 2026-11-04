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

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

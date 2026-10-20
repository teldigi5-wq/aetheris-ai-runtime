# Enterprise E3.9G-6 — Real PostgreSQL 16 skill-proof replay validation

**Engineering status:** draft/unmerged. Stacked on [PR #159](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/159) exact base `377ab35abee9ed11beb4923f64a117c6a26ef565`. Protected `main` untouched, Stage45 **NO_GO**.

## New independently reviewable database migration

`orchestrator-service/db/enterprise-skills/V1__candidate_skill_nonce_deny_only_postgresql.sql`

The schema creates the **existing JPA entity's** table with `nonce_hex varchar(32) PRIMARY KEY`, `task_id uuid`, 64-character agent/skill slugs, exact lower-case SHA256 digest, UTC timestamp-with-time-zone expiry, and one fixed deny-only evidence state. Database constraints reject invalid 128-bit nonce, malformed agent IDs or digests, positive owner/approval state, expiry in the past and remaining proof lifetime beyond five minutes.

The table has no `approved`, `authorization`, `privileges`, `executable` or `owner_authenticated` columns. It never grants any user or tool authority. `REVOKE ALL ... FROM PUBLIC` prevents public table access; reviewed deployments must assign a separately controlled `SELECT, INSERT` runtime role without `UPDATE`, `DELETE`, `TRUNCATE`, `REFERENCES` or schema modification privileges.

**The migration is create-once, not an idempotent hidden upsert.** Run via a separate administrator/migration identity, never an AI agent. Existing data must not be silently dropped or modified. Do not automatically purge nonce reservations; retention shorter than the maximum future replay horizon would invalidate the uniqueness protection.

## Evidence that real PostgreSQL is exercised

New CI workflow `.github/workflows/runtime-enterprise-e39g6-postgres-skills.yml`:
1. Runs an ephemeral **PostgreSQL 16** GitHub Actions service (no production DB).
2. Applies exact reviewed SQL and a rollback-only constraint suite verifying duplicate refusal, invalid hex/slug/digest, expired, overlong, and forged-positive evidence.
3. Creates a temporary NOLOGIN role with **SELECT+INSERT only** and tests that update/delete/truncate/references remain denied.
4. Snapshots only the reviewed nonce table's PostgreSQL DDL via `pg_dump` before Java runs.
5. Runs the existing **real Java/JPA Ed25519 and six database nonce tests** on PostgreSQL, including concurrent signed-proof replay from fresh verifier objects; never passes the H2 JDBC URL in this job.
6. Snapshots nonce schema again and fails on unexpected Hibernate DDL differences.
7. Runs independent inherited **H2 tests on Windows and Ubuntu**, entire Java 21 orchestrator regression, and exact source skill provenance checks.
8. Leaves all production/owner/Stage45 authority FALSE.

**CI reproducibility note:** The PostgreSQL service currently uses upstream `postgres:16` rather than an immutable image digest; this limits supply-chain reproducibility. Pin a verified registry digest and conduct full deployment/upgrade/restart/failover certification before claiming readiness.

## Current safety and outstanding blockers

- No authenticated human owner exists in this process. Ed25519 verifies a separately supplied/pinned **candidate** signing key, not an enrolled principal.
- `DurableSkillProofInspectionLedger` records **deny-only inspections** using `@Transactional` + `EntityManager.persist` + `flush`, not real execution/consent grants. A database nonce is not a signed execution lease.
- The real skill-advice feature remains **default disabled**; testing explicitly enables it for isolated JPA cases only.
- No external HTTP endpoint, owner workstation, live trading, auto-deploy, model call or runtime tool action is created.
- This PostgreSQL CI provides a disposable-database integration check, not actual deployed multi-host failover, configured runtime least-privilege credentials, restart durability, or operator-authentication certification.
- No website-building quality improvements are claimed. Genuine model benchmarks remain Granite4 3B 2/8, Hermes3 3B 0/8 on previous *public synthetic* browser faults; prior deterministic controls are not AI repairs.

## Next meaningful implementation

Build an **opt-in, model-evaluated web design and defensive audit research harness** that takes the pinned skill references as explicitly untrusted data, calls local free Ollama models under bounded policies, produces reviewed draft proposals only, and measures changes against real independent Playwright accessibility/performance/security requirements. Keep any generated patch blocked until separate deterministic acceptance. E3.9F independently withheld benchmarking and authenticated identity stay open; E4–E9 and Stage45 remain later milestones.

# E2.2B-2A — PostgreSQL deny-only approval-ledger contract

**Open draft, stacked on PR #109. No verified owner principal, no real approval or executor, no production DB mutation, no protected-main merge.**

## Scope
Versioned PostgreSQL contract: `orchestrator-service/db/enterprise-review/V1__design_review_deny_only_postgresql.sql`. This is an **explicit opt-in migration file, NOT auto-applied** to any database by the application. Only disposable PostgreSQL in GitHub Actions executes it. The file's location is intentionally outside Spring's automatic migration conventions.

Two tables:
- `enterprise_design_review_intents` records UUID ID, project/company/division, brief + two asset SHA256 digests, issued timestamp, 15-minute expiry, and **only** `PENDING_OWNER_AUTHENTICATION_UNAVAILABLE`.
- `enterprise_design_review_denials` records strictly allowed refusal reasons, timestamp and FK to a real intent. `ON DELETE RESTRICT` preserves denial history.
- SQL-level CHECKs validate UUID shape, lower-case slug scopes, lower-case SHA256, exact expiry and deny-only status.
- Public SQL privileges are revoked on both tables and the denial identity sequence. This does not remove DBA/migration-owner privileges.

GitHub CI creates a fresh ephemeral `postgres:16` service, applies the schema with `psql -v ON_ERROR_STOP=1`, runs real Spring JPA persistence tests using **CI-only** Hibernate `ddl-auto=update` to bootstrap unrelated legacy JPA tables that do not have migrations on this branch. CI compares the two reviewed tables' columns, constraints and indexes before/after Hibernate's test bootstrap, and then runs the adversarial SQL checks. **This is not a validated whole-service production migration.** Separate Ubuntu + Windows H2 regression and full Java orchestration tests also run.

## Critical security interpretation
Passing this PostgreSQL test verifies a **schema and DB integration contract**, not an operational production database deployment or tamper-proof event log. The runtime app role must be separate from the migration/DBA role. Prior to production, grant only `SELECT` and `INSERT` on the two tables, plus necessary sequence privileges for identity inserts; deny `UPDATE`, `DELETE`, `TRUNCATE` and schema ownership to runtime. Validate least privilege under those actual runtime credentials. Current CI JPA tests use ephemeral admin credentials to allow test teardown; do not reuse those permissions in production.

Data migration prerequisites: PostgreSQL backup/restore dry-run, compatibility audit of any pre-existing tables, production migration tool policy, service-level database encryption and retention rules. Real owner decisions must require a separately authenticated principal and atomic, one-use, action-specific approval record; **none exists in this stage**.

## Authentication review finding
The Aetheris platform gateway's `JwtAuthenticationFilter` validates JWT signatures and overwrites forwarded `X-Aetheris-User-*` headers for certain routes. That is insufficient to grant owner authority to a separately reachable orchestrator. End-to-end work must prove a trusted private gateway-to-service transport or direct downstream token validation, issuer/audience/purpose binding, owner authorization, time limits and replay-resistant action consumption. Headers supplied by callers must be untrusted.

## Dependencies and release gates
E0 PR #102 → E1 #104 → E2.1 #106 → E2.2A #107 → E2.2B-1 #109 → this draft. Do not skip integration reviews. Stage45's 13 release-blocker suite remains unmerged and unexecuted here; production and physical Windows owner-PC are still **NO_GO**.

## Next
E2.2B-2B: investigate platform JWT context and interservice transport; implement verified owner-principal contract with nonce-bound approve/deny audit and atomic consumption after security review. Do not offer an `APPROVED` or executable action outcome until that contract is proven.

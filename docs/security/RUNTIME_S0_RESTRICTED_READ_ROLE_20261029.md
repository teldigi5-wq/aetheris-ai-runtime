# S0-D2 — sealed-principal registry read under a restricted PostgreSQL role

**29 October 2026. Stage45 NO_GO. Review-only / draft / unmerged.**

## Exact prior evidence

Runtime [PR #214](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/214) completed [18/18 Java tests and real two-connection PG16 revoke/write ordering](https://github.com/teldigi5-wq/aetheris-ai-runtime/actions/runs/40867015787) at `ec3321b993b1897cdd1dc54d283585ddb0ae4313`. Its `RuntimeVerifiedTaskPlanningProbe` remains **non-injectable** and was exercised with a **privileged disposable database user**, so it did not establish least-privilege runtime DB access or DB-enforced tenant isolation.

## New independently tested design

1. `RuntimeVerifiedRegistryTaskReadProbe` is a standalone, non-injectable **read-only** JDBC authorization decision. Accepts only the verifier-sealed RS256 `VerifiedPrincipal`, requires `orchestrator:read` plus explicit read-only transaction, and uses a parameterized single `SELECT EXISTS` across `aetheris_tasks` and the **new** `registry_*` parents/memberships. Tenant/project strings select the scope, not identity; signed Identity UID comes only from the verified principal, not a client header, user-service primary key or raw caller UID parameter.
2. The disposable SQL fixture `tests/security/s0-db-roles/disposable_readonly_registry_role.sql` creates a genuine PostgreSQL16 LOGIN role with `NOSUPERUSER/NOCREATEDB/NOCREATEROLE/NOREPLICATION/NOBYPASSRLS/NOINHERIT`, revokes PUBLIC database/schema/table privileges, then grants `CONNECT`, schema `USAGE` and **only column-level SELECT permissions** needed for a boolean authorization decision. It does **not** grant task title, command text, table writes, registry writes, schema DDL, role elevation, or data-modifying procedure authority. The role and its fixed throwaway password are created **only in a fresh GitHub-hosted disposable PostgreSQL16 service**; no service configuration or real secret has been changed.
3. New JUnit suite signs real ephemeral RS256 JWTs and uses that restricted role's actual PostgreSQL network login. Tests allow Alice in two tenant-specific memberships, Bob in a different project, and a read-authorized viewer; deny wrong UID/tenant/project, ownerless legacy, revoked, suspended and guessed tasks, wrong JWT scope and a forged signer. Negative SQLSTATE 42501 checks actively challenge raw payload reads, wildcard SELECT, task updates/deletes/INSERT/TRUNCATE, membership changes, schema CREATE/ALTER and elevated SET ROLE. These are real server denials, not mocked JDBC.
4. CI checks exact SHA, no Spring annotation/HTTP route, unchanged early Task/Mission HTTP 404 and no global SSE, candidate Flyway still outside deployment, actual Java test execution and no mutated seed tasks.

## Critical distinction: not DB-enforced tenant row isolation

This test role can STILL SELECT **allowed metadata columns** from other tenants if compromised, because PostgreSQL table/column GRANTs do not implement row-scoped identity authorization. The Java probe enforces signed principal/tenant predicates **only when called through trusted code**. We **do not** claim RLS enforcement, no metadata exposure under DB credential theft, malicious-SQL resistance, or a complete tenant registry. Keeping the role metadata-only minimizes payload exposure but does not close the DB credential trust boundary. Do not give database credentials to agents, tenants, browsers, or untrusted code; DB access must be mediated by a trusted service with tightly controlled connections.

The earlier #214 write-CAS uses a disposable schema-owner login and **MUST NOT** be switched to this read-only role. Its update would rightly fail due to missing UPDATE grants. A production write design needs separately reviewed, transactional, service-controlled grants/procedures and a trustworthy signed-principal binding; granting arbitrary `UPDATE` to a shared runtime login would allow bypass of the Java object filter. The safer immediate boundary is **READ-ONLY**, rather than claiming premature production write authorization.

## Still blocking production

- Authoritative Identity-to-tenant lifecycle, invitations, role changes, JWT/session revocation and tenant registry management.
- Approved Flyway/JPA mapping, Hibernate `ddl-auto=update` removal with backed-up schema fingerprints, real migration roles and restore from a representative actual baseline; no candidate migration here was installed in production.
- Independent direct-port runtime authentication, mTLS trusted workload identity, signed audience/key rotation, secure read of actual task payload after row-scope verification, hardened DB connection ownership and role policy.
- Row-level safeguards if a DB account is compromised; eventual RLS/session-context design must not treat caller-set GUC values as authenticated identity. Bound write transactions, revoke/expiry races, STOP/mission state machine, task creation and SSE subscriber isolation require separate testing.
- Cross-repository integration with [Platform draft #346](https://github.com/teldigi5-wq/aetheris-platform/pull/346), runtime [issue #23](https://github.com/teldigi5-wq/aetheris-ai-runtime/issues/23) and platform [issue #251](https://github.com/teldigi5-wq/aetheris-platform/issues/251).

**Stage45 NO_GO**. Nine independent production release attestations outstanding, physical PC `BLOCKED_PENDING_HARDWARE`. No main merge, runtime certification SHA update, backend API/SSE enablement, customer data, production deployment, real credentials, paid services or owner PC claim.

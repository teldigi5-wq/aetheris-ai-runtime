# S0 Registry ownership and lifecycle integrity — 29 October 2026

**Status: DRAFT / disposable test candidate only. Stage45 NO_GO.**

## Scope and provenance

Stacked on AI Runtime [draft #211](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/211), exact parent `3080b7a244f26550e4957da32c7faa69d8e46d04`. That PR cryptographically links an Identity RS256 verified UID to parameterized membership JDBC **reads** using ephemeral test tables. This successor adds independent *registry entity relationships* and owner-binding integrity constraints in a fresh disposable PostgreSQL 16 transaction. It neither changes `TaskEntity` nor installs migrations or activates HTTP endpoints.

The platform [draft #346](https://github.com/teldigi5-wq/aetheris-platform/pull/346) proves an actual synthetic HTTPS User servlet + SPIFFE/RSA JWT lab, but does not establish real tenant identity, a full gateway-to-runtime channel, runtime resource server, or customer data. Platform security issue #251 and runtime issue #23 remain OPEN.

## Proposed authoritative registry model (not deployed)

1. `registry_tenants(tenant_key)` is an independent tenant authority, with ACTIVE/SUSPENDED/CLOSED lifecycle, versioning, and validated keys.
2. `registry_projects(tenant_key,project_key)` uses a **composite tenant-qualified** FK to a registered tenant. Project status is independent and fail closed when suspended/closed.
3. `registry_tenant_memberships(tenant_key,identity_uid)` references registered tenants; every UID is a **signed Identity numeric UID**, not a User entity primary key. Role, active/revoked status, revocation timestamp, expiry and optimistic version are explicitly modeled. A signed identity does not create membership.
4. `registry_project_memberships(tenant_key,project_key,identity_uid)` must reference the exact tenant-qualified project **and** an existing tenant membership. An active project membership cannot override an inactive or expired tenant membership.
5. Tasks carry an all-or-none `owner_identity_uid/owner_tenant_key/owner_project_key/owner_verified_at` binding with composite FK to the precise membership. Existing ownerless rows stay NULL, not automatically claimed. A review-only trigger denies unreviewed owner changes or legacy claims; eventual transfer requires a separate audited transaction and approval policy.

The executable **synthetic** read predicate requires independently registered ACTIVE tenant, project, tenant membership, project membership, exact owner UID, requested tenant/project and valid expiry. The parameters in this SQL proof are not authenticated: the actual Java resource operation must source UID *only* from the verifier-sealed `VerifiedPrincipal`, not an HTTP header, request payload or external UID argument. Database role privileges, real identity-to-registry lifecycle sync, transfer governance and token/session revocation are still unimplemented.

## Verification design

`tests/security/sql/disposable_authoritative_registry_integrity.sql` creates **TEMP tables only**, inside BEGIN/ROLLBACK. The workflow `.github/workflows/runtime-authoritative-registry-integrity.yml` uses PostgreSQL 16 with fixed disposable CI credentials, exact-head checkout, source/404/global-SSE security invariants, real psql negative tests and a check for **zero persisted registry tables**.

Synthetic controls: two UIDs, same UID across two tenants, another project, missing owner, suspended tenant/project, expired membership, immediate tenant/project revocation, cross-tenant/project composite FK denial, missing parent references, impossible status/timestamp combination, partial owner binding, direct owner reassignment and historical ownerless takeover. No claim of multiconnection concurrency, isolation/lock serializability, a real migration, production rollback, or end-to-end authorization is made.

## Mandatory dependency before production migration or API unlock

- Decide canonical tenancy authority and Identity/User UID binding; review identity lifecycle and privilege assignments, tenant provisioning/invitations, project ownership, role downgrade, and account deletion.
- Reconcile production schema and existing `spring.jpa.hibernate.ddl-auto=update` (currently present); replace with reviewed versioned migrations and `validate` in a separately approved branch. Test fresh and historical production-shaped backups, non-destructive rollout, rollback/restore, DDL permissions and incremental deployment compatibility.
- Design multi-connection transactional membership **locks** for create/transition/attachment and revoke races, isolation levels and operational idempotency; a same-transaction SQL membership read alone is not a concurrency proof.
- Apply repository-level scoped reads and writes using an independently cryptographically verified principal, authoritative membership and deliberately restricted workload DB identity. Lock down arbitrary `findById` and `findTop50ByOrderByUpdatedAtDesc` for customer-facing paths.
- Independently authenticate external and direct-port requests; only then reconsider the existing unconditional Task/Mission HTTP 404 filter. Independently test scoped SSE, replay, revocation and multi-instance behavior; never restore global `subscribeAll()`.
- Reconcile separate platform draft #346, platform issue #251, full Gateway/Runtime mTLS, token revocation, and exact runtime certification pin through reviewed integration.
- Retain protected main, no production deployment or migration, Stage45 **NO_GO**, nine independent release gates unfulfilled and physical acceptance `BLOCKED_PENDING_HARDWARE`.

This file is an executable *registry integrity proof*, not an authoritative tenant registry service. Any future production migration must not copy these temporary-table names or pretend the synthetic test data are real.

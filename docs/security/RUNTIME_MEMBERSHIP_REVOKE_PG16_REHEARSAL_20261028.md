# S0 — Disposable authoritative tenant and project membership query rehearsal

**28 October 2026. Review only. Stage45 NO_GO. Not a deployed registry.**

## Baseline

Draft PR #208 tests independently signed Identity RS256 `uid`, issuer, audience, role/scope. Draft #209 rehearses a nullable task owner/tenant/project schema in disposable PostgreSQL16. Draft #205 removes unscoped global task event broadcast. Draft #207 blocks all external task and mission HTTP APIs with HTTP 404. None of those draft branches are merged or deployed to production.

## What this candidate proves

A standalone SQL file in `tests/security/sql/disposable_authoritative_membership_rehearsal.sql` runs only against a **throwaway PostgreSQL16 GitHub Actions service**. It creates TEMP synthetic task, tenant-membership and project-membership tables. A membership record has a positive Identity UID, tenant, optional time-bound membership, and ACTIVE/REVOKED state. Project rows have a foreign key to the corresponding (tenant,Identity UID) membership.

It adds a **temporary SECURITY INVOKER read-query prototype** that returns true only when **all** these conditions hold: the task's owner UID matches the JWT-verified UID, the task's tenant and project match the requested selection, an ACTIVE unexpired tenant membership for that same UID exists, and an ACTIVE unexpired project membership exists in that tenant. Every constraint is checked inside the database query, not by comparing untrusted client headers.

Real SQL covers: Alice and Bob distinct UIDs in one tenant, Alice with separate valid memberships in two tenants, project isolation, task legacy-null owner quarantine, missing/malformed selectors, prior revocation, immediate tenant-membership revocation, immediate project revocation, expiry, foreign-key orphan denial and invalid membership state/UID. A transaction **ROLLBACK** removes all synthetic rows and schema. The CI checks that no permanent task or membership tables remain. The previously verified ownerless HTTP lockdown remains enforced in source.

## Critical trust boundary

This exercise **cannot** independently authenticate an application caller and does not claim to. The prototype takes `p_identity_uid` as an input. A real deployed repository method MUST derive it only from `RuntimePinnedPrincipalVerifier.VerifiedPrincipal` after configured JWT and revocation checks, never from `X-Aetheris-*` headers, query strings or caller-supplied JSON. Requested tenant/project keys are selectors, **not proof of membership**. Membership rows in this lab are seeded synthetically; they are not a trusted tenant-registry service or proof of live membership governance.

The SQL predicate is a read-only design prototype, **not PostgreSQL RLS**, not a production stored function, not a mapped JPA entity, not an installed migration and not a service-level resource-server integration. It does not solve task creation/updates, mission attachment, event replay, multi-instance subscriber cleanup, session revocation or transactional authorization concurrency. Backfill and owner binding of historical data require owner/DBA approval, backup/restore verification and separate release certification. Real direct runtime-port isolation, Gateway->Runtime mTLS and identity-to-tenant reconciliation remain mandatory.

## Next engineering stages

1. Review the **authoritative** tenant/project membership lifecycle and who may issue, revoke, rotate and audit it, with real signed principal binding and immutable action evidence.
2. Design an explicitly versioned migration and compare it with current Hibernate `ddl-auto=update`; do not apply until backup/recovery and owner approval.
3. Implement transactional repository-level owner+tenant+project-scoped queries, task/mission creation rules and authorization; prove two signed accounts cannot cross read/write or replay boundaries.
4. Preserve fail-closed external HTTP/mission/SSE routes until all resource-server, persistence, network, revocation and release checks independently pass.

**No production credentials, data, DB changes, model calls, deployment or protected-main merge occurred. All nine Stage45 release attestations remain outstanding. Stage45 NO_GO.**

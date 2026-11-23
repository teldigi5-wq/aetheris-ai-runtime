# Runtime Security #23 — mandatory lockable signer/epoch authority for task INSERT

**9 November 2026 • REVIEW ONLY • Stage45 NO_GO • Runtime Security #23 OPEN**

## Why this is a distinct necessary security fix

The verified [session+fence task creation candidate in PR #286](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/286) previously checked `blocked_signer_kids` and `principal_epochs` using `NOT EXISTS` conditions. When a revocation row did not yet exist, no row could be locked. A concurrent revocation insertion could race with authorization under READ COMMITTED. Other session/fence/membership rows were locked, but those two authorities were not.

This update **replaces absence-as-authorized with mandatory, lockable authority state records**:

- `signer_key_states(issuer,kid,state)` must contain the exact independently verified issuer and signing key ID in `ACTIVE` state.
- `principal_epochs(principal_id,reject_issued_at_or_before_epoch)` must contain the signed Identity UID, with the signed token's issued-at strictly newer than the rejection cutoff.
- Both records are included in the same owner-scoped `INSERT ... SELECT` and explicitly locked with `FOR SHARE OF signer,epoch`, together with existing session, tenant, project, membership and primary-fence locks.
- **No matching authority row means DENY**. `UPDATE state='REVOKED'` or `UPDATE reject_issued_at_or_before_epoch=...` on these existing rows conflicts with an in-flight task insertion until the writer commits/rolls back. New admission after committed revocation denies.
- Signer and epoch writes must use **UPDATE of existing authoritative rows**, never a separate, uncoordinated INSERT/denylist. There is no general revocation-writer implementation in this PR; only disposable CI test writers. A future production schema must enforce row lifecycle and authority custody under a restricted DB role and versioned migration.

## Exact current implementation

The changed Java `RuntimeVerifiedTaskSessionCreationProbe` is **not Spring-injectable**, not a REST/SSE endpoint and not a real scheduled workload. No public API, tenant data, operational token, user session or external system is accessed.

The changed real PostgreSQL16 test fixture seeds exactly one active synthetic signer and epoch rows for two different synthetic Identity UIDs, and includes two new tests:

1. Delete a signer or epoch record in a transaction; task creation **denies by default** instead of interpreting missing rows as unrevoked; rollback and no persistent test changes.
2. After a verified signed task INSERT locks the authority rows, try signer and epoch revocation `UPDATE` statements from a second PostgreSQL connection with finite statement timeout. Both must block (SQLSTATE 57014). After insert commit, real revocations succeed, and the same previously signed identity is denied from creating another task.

Existing six real PostgreSQL session/fence/membership/tenant tests remain, including cross-owner denial, revoked session, wrong workload and primary-fence denial, and session revocation under row lock. CI requires **8/8** non-skipped tests in real disposable PostgreSQL16 and retains full orchestrator Java suite with ownerless task/mission HTTP 404 containment and no global SSE.

## Important security and release limits

This is a **narrow SQL transaction-race correction**, not a solution to the larger issue #23. The authority tables, principals and fence registry are disposable SQL fixtures, not approved production schema or trusted membership service. This proof does not serialize token expiry after admission, signed workload bearer replay, revocation performed through *different/uncoordinated* tables or remote writers, clock/term expiration during commit, absent-row resource-fence creation, cross-primary split-brain or hardware. There is still no mTLS-authenticated workload identity, Spring Security runtime HTTP principal, production tenant task migration, per-owner SSE, mission-ownership policy, owner session logout invalidation or two-account end-to-end HTTPS proof.

**No merge of this stacked branch, no API unquarantine, no deployment, no owner consent claim.** Protected Runtime main now contains independently verified dependency and image-release fixes from #292; this review branch remains based on the old security-prototype lineage. Bring the required review into a carefully curated future integration after authoritative registry design; do not merge stale ancestry blindly. Keep Stage45 **NO_GO**, S-B62-A **BLOCKED_PENDING_HARDWARE**, XERJ **HOLD**.

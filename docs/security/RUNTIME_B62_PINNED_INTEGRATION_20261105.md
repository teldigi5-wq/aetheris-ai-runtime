# B62 integrated replay candidate — pinned immutable parent
**Date:** 2026-11-05 · **Status:** review-only, unmerged, NO_GO

This independent branch is pinned to Runtime PR #257 tested SHA `6caa4df21ac7533a7d4455095d8dc544ea37b5d2`, with three passing exact-commit GitHub Actions suites. It integrates Runtime PR #280 source at `436e744c5ac8dce1734d8a4c7084d096454c317d` from common ancestor `6db35e8885efb46313ebd96bebe3686910630c5d`. The active #257 branch may already contain newer commits; this candidate **is not a latest-head integration and must be refreshed and reverified before merge**. Original branches and PRs are not edited.

Exact Git blobs are reused for eight B62 PG16 revocation/replay lab scripts, synthetic signed write-scope Java implementation and tested JDBC probe, and the original review document. Two shared physical-standby files are combined using non-overlapping source hunks, preserving the three lock-timeout assertions, fail-closed receipt and CLOSED-gate DML guards, duplicate receipt idempotence, real process restart, old-primary unsafe failback NO_GO markers, and B62 replay negative-case witnesses. The three isolated CI workflows are enabled for this candidate branch.

Retain signed `orchestrator:write` requirement, denied read-only synthetic effects, per-owner/tenant scope and unexposed Task/Mission/SSE/actuator routes. Newer #257 prototype pinned signed ACK and SQL consumption do not establish real resource-attested credentials, cross-resource global fencing, quorum, protected failback, or production authority. All unsafe negative proofs remain release blockers, even if a test job succeeds. Runtime Issue #23, Platform Issue #251 OPEN; nine Stage 45 attestations outstanding; XERJ HOLD.

Require actual candidate CI logs and cleanup proof; do not merge or deploy without reconciling newer #257 code, independent review and official release criteria. No production Java endpoint unlock, secrets, migrations or customer data are authorized.

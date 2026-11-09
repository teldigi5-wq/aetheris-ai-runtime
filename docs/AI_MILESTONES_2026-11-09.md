# Milestone and handoff evidence ledger — teldigi5-wq/aetheris-ai-runtime

> Snapshot: 2026-11-09. Proposed on documentation PR #304. All task boxes below are **to be revalidated** against current code; an unchecked box never means a previously completed stage should be repeated.

## Baseline known at snapshot
- [x] Existing README and AI continuation document read from dedicated documentation branch.
- [x] Documentation PR #304 confirmed draft/open at inspection.
- [ ] Full paginated branch/issue/PR/Actions inventory performed for every active branch (NOT certified).
- [ ] Exact-current implementation head and code tests revalidated (not performed by documentation updates).
- [ ] Merge or production release accepted (NOT asserted).

## Milestones from the existing handoff
- [ ] **M1 — revalidate:** Inspect PR #303 and stacked PRs #294–302 at their exact commits and bases; preserve separated Runtime/Platform ownership.
  - Source branch/commit: _check current GitHub_
  - Results and evidence: _no new code tests in documentation-only PR_
  - Decision: **NOT ACCEPTED** without exact-source evidence; preserve any NO_GO.
- [ ] **M2 — revalidate:** Run exact-head PostgreSQL 16 negative tenant/session/revocation/SQL bypass tests and verify all failures fail closed.
  - Source branch/commit: _check current GitHub_
  - Results and evidence: _no new code tests in documentation-only PR_
  - Decision: **NOT ACCEPTED** without exact-source evidence; preserve any NO_GO.
- [ ] **M3 — revalidate:** Verify Platform RS256 JTI → Runtime HTTP integration with controlled operators and explicit deployment origins.
  - Source branch/commit: _check current GitHub_
  - Results and evidence: _no new code tests in documentation-only PR_
  - Decision: **NOT ACCEPTED** without exact-source evidence; preserve any NO_GO.
- [ ] **M4 — revalidate:** Keep Stage 45 NO_GO until genuine multi-brief model results, hardware benchmarks, signed provenance, security and production gates are independently evidenced.
  - Source branch/commit: _check current GitHub_
  - Results and evidence: _no new code tests in documentation-only PR_
  - Decision: **NOT ACCEPTED** without exact-source evidence; preserve any NO_GO.

## Operator checklist
- [ ] Re-read protected main, feature PR base/head, conflicts and review threads.
- [ ] Inspect independent CI/security policy logs for **the exact proposed commit**.
- [ ] Test failure paths and rollback; respect per-project threat model and privacy.
- [ ] Record zero-mandatory-cost route; ask before paid services or production actions.
- [ ] Update README status only after verified upstream change, not based on a draft.

## Evidence template (copy once per real change)
```text
Date (UTC):
Repo / branch / PR / exact source SHA:
Milestone and acceptance condition:
Existing prior evidence (with SHA):
Commands run / environment:
Results (pass/fail/blocked; links/artifact IDs):
Safety checks (secrets, permissions, tenant/data boundaries):
Reviewer / owner authorization:
Merge state / deploy state / real-world QA state:
Rollback path:
Next unique incomplete milestone:
```

**No stage advancement from a docs-only commit.** Previous certified/merged stages are retained; do not restart them solely because checklist boxes are open.

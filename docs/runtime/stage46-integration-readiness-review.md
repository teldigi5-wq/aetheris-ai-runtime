# Stage 46.20 — Integration-readiness review, without merging

**Status: OPEN DRAFT REVIEW ONLY.** No protected-branch merge, deployed integration candidate, real STT/TTS, microphone, firewall change, local installation, privileged OS action or physical owner-PC pilot. All **13 Stage45 release blockers** continue to apply; **production and owner-PC pilot remain NO_GO**, regardless of every CI job passing.

## What is actually being reviewed

Stage 46.19 verified that PRs #72–#84 were stacked open/draft/unmerged, and that the separate unsigned Windows CI fixture hashes matched. Its [PR #85](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/85) is also **OPEN DRAFT UNMERGED** at reviewed HEAD `cf7c02b25f15b91a100c060bc5b35008c0965c8d`, based on PR #84 at `89142d2a47965bb80efc981115159947644c6d1c`. The protected `main` branch was observed at `65a6262717adcd52ac8d8a16ed6f223e299fd74d` when Stage 46.20 started; the CI report separately records the main SHA actually fetched during the test. A changed main SHA is not silently reinterpreted as the old baseline.

### New evidence and tests

**Git integration dry-run against `main`.** The `protected-main-merge-tree-review` job checks out the precise PR HEAD with full history, fetches current `main` read-only and runs `git merge-tree --write-tree --messages refs/remotes/origin/main HEAD`. This simulates a merge in a temporary Git tree object: **no merge commit is made, no branch ref or GitHub state is modified, and nothing is pushed or deployed**. The gate validates the exact HEAD and unchanged main SHA/worktree, distinguishes conflicts from a clean simulation, reports bounded conflict-message examples and aggregate changed path count, and **never returns release authorization**. A conflict (exit code 1) is a valid *detection* result but explicitly marks `merge_conflict_free=false`; the workflow may still pass to preserve an auditable NO_GO finding, not to assert readiness. Fatal Git errors and malformed outputs fail the job.

A clean merge-tree result is only a preliminary tree-level conflict check. It **does not** establish that Java, database, user permissions, APIs, migration scripts, Windows/WSL behavior, runtime architecture, deployment or protected-main CI will work after a real merge. No main-branch branch protection is overridden.

**Read-only live 14-PR chain audit.** `stage46_integration_chain_review.py` reuses Stage 46.19's reviewed snapshot and read-only GitHub APIs for #72–#84, then independently validates PR #85's open/draft/unmerged status, exact target branch and both HEAD/base commit hashes. The new PR must target PR #85's head at the pinned revision. A changed predecessor requires a separate review, never silent self-enrollment. There is no token write permission; CI reads the existing PRs without mutating them.

**Regression and policy coverage.** Added adversarial Python tests for merge-tree conflict, malformed or conflicting output, SHA/ref drift, dirty worktree, unchanged merge/read-only invariant, PR-chain retargeting, merge and fake release grants. CI reruns earlier genuine Windows Job Object, reduced-token and AppContainer synthetic fixtures, read-only WFP layer/snapshot checks, synthetic voice owner/STOP Java tests, full orchestrator/workstation Maven suites and 7 Stage45 production-policy tests. They run against this exact draft HEAD.

## Reviewed integration risks

1. **Long review stack:** Fifteen sequential draft branches (#72–#86 including this stage), even if individually green, remain subject to conflicting integrations, incomplete code-review accountability, GitHub branch-protection rules, future base-branch updates and missing end-to-end user flows. No forced squash/rebase/merge is justified by a passing dry-run alone.
2. **Unsigned/unapproved binaries:** Two Github-hosted builders share a trust domain. No audited signing certificate, independent supply-chain provenance, distribution/rollback package or real PC install.
3. **Unproven WFP denial:** Historical `CLASSIFY_DROP` events were unattributed, and the AppContainer loopback attempt timed out. No real process-specific Windows Filtering Platform network-deny enforcement has been certified. No real firewall policy or audit settings should be changed here.
4. **Voice, owner and STOP:** The current test speech path uses synthetic frames only. Real microphone, streaming STT/TTS, owner-confirmed permissions, reliable STOP supervisor, independent process isolation and hardware latency/barge-in remain unverified.
5. **Database and operating system:** No real PostgreSQL PITR/restore failover, separately protected keys/broker, hardware-backed secrets, immutable external audit, independent penetration test, or Windows owner-PC test is proven by Java/CI assertions.

## Explicit consolidation strategy (not executed)

After a separate human review, first reconcile the oldest failing or outdated requirements and classify each draft PR as **product code, supporting test evidence, documentation or experiment**. Select a minimized, coherent integration scope preserving only genuinely operational functionality, with reproducible tests and no temporary laboratory entry points in production paths. Review migrations, secrets, third-party licenses, OS permission boundaries and startup paths. Then create a separately approved integration candidate branch, run complete branch-protection and cross-platform tests, have it independently security-reviewed, and approve any eventual merge explicitly. **This stage does not create, merge or deploy that integration candidate.**

### Reproduce

```bash
git fetch --no-tags origin +refs/heads/main:refs/remotes/origin/main
python3 tools/security/stage46_integration_merge_review.py --expected-head <exact-stage-46.20-HEAD>
python3 -m unittest discover -s tools/security -p test_stage46_integration_merge_review.py -v
python3 -m unittest discover -s tools/security -p test_stage46_integration_chain_review.py -v
```

Read-only live GitHub validation additionally requires a read-scoped token, the precise PR base branch and its SHA. This only operates in disposable CI and must not be run with write-scoped secrets. Any result with no real merge conflicts still has `decision=NO_GO`, `production_authorized=false` and `physical_owner_pc_pilot_authorized=false`.

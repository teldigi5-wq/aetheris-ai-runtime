# Aetheris B62 — PostgreSQL revocation continuity review (preview only)

**Source:** `teldigi5-wq/aetheris-ai-runtime`, Runtime PR #257.
**Isolated validation branch starts from:** `3fec4692c518c91c2dcb43e1f3bf686fb0f902f7`; do not apply to production.
**Pinned source blobs:** physical script `b87bf499ba7dc248db62da7725993e255433a983`; workflow `b8ac2ae0ae29ed620525b8fce53dfd3b9fc314bc`.
**Status:** v2 isolated draft CI validation candidate (reconciled after newer Runtime JDBC/signed-JTI prototype/CI commits); no production code/migrations or API/SSE route changes; Stage45 **NO_GO**.

## Scope

The patch sources a new bounded shell test inside the existing disposable **three physical PostgreSQL 16 container** lab, after old-primary partition testing and before the deliberate coordinator-outage demonstration. It uses a coordinator-side *synthetic event source* and separate promoted resource PostgreSQL database; it does **not** implement production event replication, quorum or mTLS.

Checks expected at CI execution (not executed in this local environment):

- Resource initially denies before admission and denies when `applied_seq < required_seq` even after a mistaken admission flag update.
- Out-of-order sequence 2-before-1 denies; sequences 1 and 2 replay in order, with revoked subject denied, nonrevoked allowed, and duplicate request ID denied.
- Caller has no direct resource-table writes and no replay function EXECUTE; a monotonicity trigger rejects decreasing resource watermarks.
- **Deliberate NO_GO witness**: coordinator commits source revocation sequence 3 for user 98; before resource barrier delivery, the still-admitted resource accepts a synthetic request by user 98. This is intentionally unsafe and confirms there is **no** reliable cross-database revocation admission algorithm.
- After *manual test-only* barrier application, deny all during catch-up, apply sequence 3, deny revoked 98, permit nonrevoked 99 and reject reducing `required_seq`.

## Concurrent GitHub change reconciliation

At the latest verified PR head, the Runtime JDBC read prototypes gained `NOT pg_is_in_recovery()` and explicit transaction checks. These remain separate, non-bean prototypes, not an authenticated runtime route. Their new tests and source guards passed at their own exact head. The physical standby script and its workflow retain the same Git blob IDs shown above; this preview does not modify those Java sources.

## Threat model and limitations

1. Replayed sequence and UID are synthetic trusted-owner values; no cryptographic event provenance, gap-free source durability, source authority, or real Identity signing is established.
2. A monotonic local watermark does not prove that the resource knows the **latest** source watermark. A new source event may commit between checks, and a disconnected old primary can still execute under obsolete authority.
3. The safeguarded effect and replay-control row are co-located in one **resource** database, but the coordinator source remains a separate database. Atomicity does not extend across them.
4. PostgreSQL syntax and transaction behavior have **not** been executed in this container (Docker and PostgreSQL client/server absent). `bash -n`, static assertions, and the existing 14-test SQLite review are not PG16 execution proof.
5. A database owner can bypass test-only roles and triggers. No API, Task, Mission, SSE, controller, token verification, tenant binding or production service has been enabled.

## Safe application instructions (do not run automatically)

Before considering a change, verify that GitHub Runtime PR #257 **still** has the pinned SHA and exact source blobs. Run `git status --porcelain`, check repository origin, and require a clean worktree. For an approved detached isolated checkout of the correct branch at the exact SHA only, run:

```bash
git apply --check /path/to/aetheris_b62_pg16_revocation_replay_preview.patch
# Then, with maintainer approval for an isolated preview branch:
git apply /path/to/aetheris_b62_pg16_revocation_replay_preview.patch
bash -n scripts/security/runtime_b62_physical_standby_review.sh
bash -n scripts/security/runtime_b62_pg16_revocation_continuity_review.sh
git diff --check
```

CI must run the existing physical-standby workflow in disposable infrastructure, confirm all existing security markers and new negative witnesses, capture actual full logs and cleanup evidence, and complete human review before any merge decision. Even if green, **Stage45 NO_GO**, Runtime #23, Platform #251, and live API/SSE quarantine remain unchanged.

## Additional v2 source-authenticity negative witness

The review-only PostgreSQL replay function accepts `p_seq` and `p_uid` from
its trusted caller and only checks sequence continuity. It does **not**
verify that UID `999` actually appears in the independent coordinator's
source event sequence 1 (which contains UID 11). A transaction-local
negative witness deliberately replays `(1,999)` and validates the wrong
identity is present, then rolls the transaction back. This is a **test
proving a missing authenticity property**, not evidence that an external
unprivileged application user can invoke the function. The application
writer is explicitly denied replay EXECUTE in the existing test.

A production fix must bind event identity and sequence to an authorized,
authenticated, durably ordered source; enforce source signatures/role scope
and replay monotonicity; prevent admission until a trusted committed
revocation barrier is acknowledged by the resource. The current lab
implements NONE of these distributed properties. Stage45 NO_GO.

## Exact-head GitHub verification (NOT this patch)

On commit `47808a5de3433b44b8ee399087e77cdf1bfff046`, the three existing
GitHub Actions jobs completed SUCCESS. Inspected Maven job logs report **83/83**
consolidated Runtime tests and **21/21** restricted-reader tests, zero
failures/errors/skips. The disposable physical-standby workflow also passed,
including the expected stale-old-primary and coordinator-outage **UNSAFE**
counterexamples and container cleanup. The actual v2 replay source-identity
witness above has **never run on GitHub/PostgreSQL**: do not attribute those
existing green checks to this uncommitted patch.


## 5 November 2026 — exact-green-head forward-port (no integration approval)

The eight standalone synthetic PG16 revocation scripts and this review note were carried forward from draft #277 commit `1b1d7fc0cefb55167a5071c6de732315ebadae57` onto **pinned, all-three-checks-green** Runtime #257 commit `e360c58d788d4fa6b0769907575661e5e5e54295`. The runner and workflow are derived from that newer commit, preserving the constrained writer-gate, lock-drain, old-primary, source denial and local cutover-negative checks. The scripts are invoked while the independent coordinator is still at term 4 and before its later transition to term 5. All original PRs remain untouched. Fresh exact-head CI is mandatory; earlier lab CI does not transfer to this new combination.

This is not a deployable revocation protocol. Distributed atomic cutover, network-partition fencing, trustworthy source authority and real HTTP/SSE admission remain unresolved. Stage45 NO_GO; Runtime #23 and Platform #251 OPEN.

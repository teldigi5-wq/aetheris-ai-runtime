# Aetheris E6 / W5-L — PR #275 authorization-readiness review

**Review date:** 2026-11-03 · **Scope:** Targeted, source-based execution and evidence review (not a full independent security audit).

## Verified baseline at review start

- Repository: `teldigi5-wq/aetheris-ai-runtime`; open draft PR **#275**.
- Starting head: `47bb57d4fb46d6848b2f99b4c1fdf224ae69e129` (96 commits, 37 changed files).
- Base branch: `feat/e6-w5i-local-inference-completion-20261102`, base SHA `d1abab54b80af1ec63ede7360869c4d9308441e5`.
- Prior model-free GitHub Actions run `41640085596`: **226 Node + 10 Python tests passed**, simulated Chromium 18 routes and screenshot/source-integrity 18 screenshots.
- PR review count **0**, approval labels **none**. A fresh owner label has **not** been granted.
- Runtime protected main `65a6262717adcd52ac8d8a16ed6f223e299fd74d` and Platform protected main `57ad3e0e602efa6feb275328254151b959a3499b` remain unchanged.

## Cross-workflow execution boundary

| Workflow | Target PR | Trigger and gate | Review disposition |
|---|---|---|---|
| W5-D single-request CPU smoke | #269 | Historical owner label `e6-w5d-cpu-smoke-approved` | Separate PR; **untouched**, not a #275 path |
| W5-J three-brief CPU trial | #275 | Historical `e6-w5j-three-brief-cpu-approved` | **Retired** at job level using unconditional `false &&`; history retained |
| W5-L six-cell paired CPU research | #275 | Fresh `e6-w5l-six-cell-cpu-approved`, owner actor, open draft, exact reviewed SHA, same-repo head/base | **Not approved or executed** |
| W5-J model-free preflight | #275 | Normal pull-request CI | Tested; cannot invoke Ollama in the mock-only pipeline |

The regression in `test_w5l_owner_cpu_gate.mjs` enumerates every checked-out workflow
file and fails if a new label-triggered CPU workflow explicitly targets PR #275
outside the reviewed W5-J/W5-L pair. It also verifies W5-J stays unreachable
and that W5-D still targets PR #269. This is a **scoped static inventory**:
it is not a complete parser or a proof against all possible GitHub Actions
execution paths, dynamically referenced workflows or external automation.

## Controls that have model-free test evidence

1. Exact owner event and fresh live PR recheck; per-request no-cache GitHub
   verification blocks further model calls after label revocation.
2. No GitHub token in model prompts/artifacts; fixed loopback inference endpoint;
   CPU-only request options; response byte budgets, 180-second request timeout,
   55-minute job ceiling and bounded 600-second model pull.
3. Pinned Ollama archive SHA-256; pinned model manifest plus full SHA-256 and
   exact size of five model blobs. **Only synthetic small blobs have been
   exercised in model-free CI.** Integrity does not attest executed weights.
4. Six fixed fictional held-out design cells; sanitized failure receipts
   preserve unknown results as unknown. Timing excludes GitHub approval
   latency but retains the total wall deadline.
5. Model-free Next.js build and mock Chromium accessibility, navigation,
   screenshot, source-integrity and tampering checks.

## Open blockers — NO_GO

- **No genuine W5-L GitHub label-triggered run.** Workflow registration and
  CPU-runtime installation have not been demonstrated with the approved exact head.
- **No fresh owner authorization.** A generic `continue` is not consent to
  cloud runner/model downloads or inference.
- **No independent human PR approval/review** recorded at this checkpoint.
- **No genuine three-brief paired design capture**, verified runtime/weights
  execution, or authenticated model provenance. Model quality is unassessed.
- **Unequal request/token budgets** between compact and staged methods
  (`comparableBudget:false`); only three fictional held-outs; no statistical
  evidence that one method is faster or produces better visual output.
- **No real generated premium website** validated by browser/physical devices,
  human creative review, keyboard+screen-reader testing or field Core Web Vitals.
- Stage 45 platform/runtime release attestations are outstanding; the
  production decision remains **NO_GO** regardless of a green research CI.

## Safe future decision path

Keep PR #275 draft, commit-specific and unmerged. Obtain an actual
independent source/review sign-off first. If the owner explicitly accepts
the scoped ephemeral CPU time, external model download and six design-only
cells for the **current exact head**, they may add the single new W5-L
approval label themselves. Inspect any run's sanitized receipt and log
without promoting unverified claims; cancel a running job when approval
is withdrawn, because between-request checks cannot interrupt an
in-flight request. Do not enable W5-J, reuse historical labels, alter
protected main, deploy, or claim Stage 45 GO.

**This document is a pre-approval risk record, not approval, verification
of real inference, source certification, or a release attestation.**

## W5-L independent artifact-upload admission gate

An additional cross-boundary review identified that the W5-L owner-only
workflow previously uploaded its output directory with `if: always()`
without independently checking the complete saved artifact immediately
before upload. The new `w5l_sanitized_metadata_gate.mjs` is a read-only
admission control. It rejects extra files, symbolic links, files over
40 KB, malformed or noncanonical JSON (including duplicate JSON keys),
source/run identity drift, unauthorized fields and raw model text.

A completed receipt must exactly match the canonical six-cell protocol,
source-pinned observation receipt, summarized comparison and reported
capture count. An interrupted experiment can upload only the canonical
unverifiable-attempt receipt with unknown counts. No result can assert
human approval, genuine provider attestation or production readiness.

Workflow artifact upload now occurs only if this independent schema
step succeeds. The verifier runs even when model inference fails so
legitimate sanitized failure evidence remains recoverable. The
model-free CLI subprocess rehearsal also checks both success and
failure artifacts against the same upload validator.

This is schema and consistency checking, not independent inference
provenance, cryptographic attestation of model execution or a release
decision. It does not authorize the W5-L experiment. Stage 45 NO_GO.

## W5-L re-run and triggering-actor control

GitHub Actions keeps the original `GITHUB_ACTOR` identity and associated
permissions on a workflow re-run, even if another user triggers the
re-run. Consequently, an exact-owner check against that field alone
does not authorize a second attempt or prove the re-run initiator.

The W5-L job now requires `github.run_attempt == '1'` and
`github.triggering_actor == 'teldigi5-wq'`, with a redundant initial
shell check before downloads. The owner CPU CLI requires exact
`GITHUB_RUN_ATTEMPT=1` and `GITHUB_TRIGGERING_ACTOR=teldigi5-wq`
in addition to the original event, exact-commit SHA, current PR label
and no-GPU checks. Re-runs fail closed. A failed CPU run cannot simply
be retried using the original approval: a fresh label event and source
review would be required.

This prevents same-workflow re-runs but does not claim to detect
transient label revocations followed by an immediate re-application
or make model provenance independently attested. Only synthetic
GitHub events and CLI subprocesses are exercised in normal CI.

Stage 45 remains NO_GO; the real CPU run remains unapproved.

## Artifact-upload file-path minimization

The W5-L workflow formerly uploaded the validated evidence directory.
Although the independent upload gate checks that the directory
contains only one JSON file when it runs, a directory-level artifact
path would widen the upload scope if an extra file were added before
the upload action executes. The upload action now names **only**
`target/e6-w5l-owner-cpu-review/paired-design-metadata.json`,
rather than a directory or glob. Normal CI statically verifies the
validation step, required successful outcome and exact allowlisted
upload path. The on-run schema gate remains mandatory.

This narrows publication scope but does not eliminate an adversarial
same-file change between validation and upload; the ephemeral runner
and lack of untrusted concurrent writers remain part of the trust
boundary. Schema passing still does not attest genuine model execution
or confer owner/production approval. Stage 45 remains NO_GO.

## Owner approval refreshed immediately before model-weight pull

The W5-L live owner-label check previously preceded downloading and
extracting the Ollama CPU runtime. If authorization was withdrawn
during that work, model weights could still begin downloading.

The workflow now performs a fresh GitHub PR #275 read after the
loopback Ollama server reports ready and immediately before calling
`ollama pull qwen2.5:0.5b`. The strict owner CLI `live-approval`
validates the returned open draft PR, exact source SHA and approval
label. Revoked approval or failed GitHub reads abort the step **before**
any model-weight download. The read-only GitHub token is scoped to
that workflow step and removed from the Ollama server and pull
process environments using `env -u GH_READ_TOKEN`.

Model-free CI checks the sequence and token exclusion. This reduces
the revocation gap but does **not** interrupt a download already
in flight; withdrawal during the pull requires cancelling the GitHub
Actions job. Additional checks before inference and every individual
model request remain active.

Neither actual model download nor GitHub label-triggered W5-L
execution has been verified. Stage 45 remains NO_GO.

## W5-L exact base-commit authorization

The W5-L reviewer-approved diff depends on both the head and base
commits, not only the head SHA and base branch name. Previously a
base branch update could change the review target while the head
and owner CPU label remained unchanged.

The owner-label workflow condition, event verifier and per-request
live PR verifier now require the base SHA
`d1abab54b80af1ec63ede7360869c4d9308441e5` in addition to the source head, owner and
repository checks. Missing or altered base SHAs fail closed.
Legitimate base movement requires explicit re-review and an
intentional pin update. Simulated tests demonstrate policy behavior
only; no real CPU inference, merge or deployment is authorized.

Stage 45 remains NO_GO.


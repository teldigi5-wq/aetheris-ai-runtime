# E6 W5-D — Owner-local model output integrity and visual acceptance

**Date:** 2 November 2026 · **Branch:** stacked research preview · **Release:** NO_GO

## Exact recovery and source

- Canonical repo: `teldigi5-wq/aetheris-ai-runtime`, not `aetheris-platform`.
- Parent: W5-C **draft PR #268**, verified HEAD `b5972ee65d2dcde9007d099d0985774e5d2e1700`; W5-A and W5-B are separately open stacked drafts. None are merged or production deployed.
- W5-A/B/C CI verified **schema, bounded source, rejection behavior and human-written test doubles**. They did **not** verify real Ollama inference, authenticity of model weights, premium visual results or non-template autonomous site generation.
- The W4-D owner-hardware performance and creative review remains unmeasured, Runtime authorization #23, Platform authorization #251, E6 Task 7/8 and Stage 45 production attestations are independent pending gates.

## This stage's functionality

1. `w5d_trial_integrity.mjs`: independent source-tree integrity verifier for the **completed W5-C local Ollama trial**. Requires the exact three previously unused fictional held-out briefs, three successful W5-C accepted cases, a self-reported localhost provider receipt and exact installed-model digest shape; fails closed for incomplete or rejected trials, missing/unexpected files, symlinks, oversized outputs, altered HTML/CSS, inconsistent SHA-256 files, unexpected routes, executable scripts, external URL references and forged review/release flags.
2. `w5d_owner_browser_review.mjs`: after integrity passes, starts three temporary loopback-only local servers and opens the existing compiled sites in Playwright Chromium at 320×700, 390×844 and 1440×900. Requires genuine local CSS, two-page working navigation, JavaScript-disabled static readability, zero external requests, no page exceptions or overflow over 2 CSS pixels. Stores nine *actual local candidate* screenshots and a report in an **exclusive, private-permission** `target/e6-w5d-local-owner-browser-evidence` folder. These screenshots permit human design review; they **do not constitute design approval**.
3. `test_w5d_trial_integrity.mjs`: offline adversarial tests with a deliberately **human-written fake provider**. Even when a fixture's `providerWasActualLoopbackRequest` field is changed to `true` to test the validator, the validator explicitly leaves `actualWeightsExecutionAttested=false` and `independentModelAuthorshipVerified=false`. Tests cover forged release claims, modified source files, missing output, unexpected files, symlinks, source mismatch and spoofed model metadata.
4. `.github/workflows/runtime-e6-w5d-output-integrity.yml`: read-only pinned SHA CI of inherited W1/W2/W5-A/B/C tests, locked Next.js and fake-provider browser regressions plus W5-D integrity tests. It **never installs, downloads or invokes a model**. A CI success is only evidence that the review gate works. No model files or private transcripts are uploaded by the workflow.

### Explicit decision boundaries

- `providerWasActualLoopbackRequest=true` means **the W5-C script used the normal fetch transport**, not that Ollama executed authentic weights. A malicious local server or forged local report could mimic that response. The installed-model digest is likewise **provider-reported**, not independently attested.
- A generated `.css` file containing source proposed by a live model is **not** unrestricted React, JavaScript or a brand-new visual engine. It remains constrained to W5-C's allowlisted CSS grammar.
- Differences between screenshots do **not** demonstrate premium artistry, copyright originality or brand quality. Human visual comparison and real device measurements must be performed separately.
- Source hashes detect accidental/local tampering relative to the saved receipt but cannot protect against a malicious actor replacing *both* the file and the report. Independent provenance requires a trusted signer, reproducible execution trace and separate review.
- All W5-D reports explicitly keep `independentModelAuthorshipVerified=false`, `visualQualityHumanApproved=false`, `releaseGo=false` and `stage45Go=false`.

## Owner's reproducible WSL2 procedure (actual model test)

Use a **new detached review worktree**. Do not clear previous worktrees/targets or reuse the W5-C mock sites as live-model evidence.

```bash
cd ~/aetheris-ai-runtime
git fetch origin
git worktree add --detach \
  ~/aetheris-runtime-w5d-review-20261102 \
  origin/feat/e6-w5d-owner-model-output-audit-20261102

cd ~/aetheris-runtime-w5d-review-20261102/tools/enterprise/e6_studio/next-studio

node --test test_w5d_trial_integrity.mjs
ollama list
```

**Prerequisites:** Ollama must already be installed locally and running at `127.0.0.1:11434`. Choose an **actually installed, hardware-compatible, license-reviewed model**; this stage neither installs nor downloads one, nor calls paid/cloud APIs. A failed provider response or insufficient model context should be recorded as a rejected attempt, not bypassed.

```bash
# This command performs actual local model requests, under explicit owner opt-in.
# Replace YOUR_INSTALLED_MODEL_TAG with one verified using ollama list.
E6_W5C_ALLOW_LOCAL_INFERENCE=YES \
E6_W5C_MODEL='YOUR_INSTALLED_MODEL_TAG' \
node w5c_local_code_trial.mjs

# Only if all 3 fictional briefs were accepted and the output directory exists:
npm ci --ignore-scripts --no-audit --no-fund
npm install --no-save --no-package-lock --ignore-scripts --no-audit --no-fund playwright@1.55.0
npx playwright install chromium
node w5d_owner_browser_review.mjs
```

Review the write-once local outputs (no remote publishing):
`target/e6-w5c-local-css-research/source-code-trial-report.json` and
`target/e6-w5d-local-owner-browser-evidence/review-report.json`, plus each of the nine PNGs. Save the exact worktree commit, installed runtime/version/digest, review screenshots, rejection codes, browser/OS information and independent creative/accessibility notes before any promotion. Avoid publishing private model transcripts or personally identifying test data.

## Acceptance checklist

| Gate | Acceptance rule | Current status |
|---|---|---|
| W5-D code/static audit CI | Exact PR HEAD with all adversarial and inherited checks green | Pending current-turn CI |
| Real model request | Owner ran fixed loopback Ollama against 3 held-out prompts | **NOT RUN / NOT VERIFIED** |
| 3 compiled output bundles | Complete hashes and files match trusted local receipt | **NOT VERIFIED** |
| Browser visual evidence | 9 local screenshots of genuine candidates with safe navigation | **NOT CAPTURED** |
| Originality and aesthetic quality | Independent human review of content and composition | **NOT APPROVED** |
| Physical GPU, a11y and performance | Owner device plus representative-browser profiling | **NOT MEASURED** |
| Unrestricted AI website code | Isolated, policy-enforced code generation with stronger sandbox | **NOT IMPLEMENTED** |
| Production release | Runtime auth #23, Platform #251, Stage 45 9 attestations | **NO_GO** |

**Stop criterion:** if real model responses are rejected or visual quality is insufficient, keep the draft open, preserve the evidence and revise model prompts/grammar in separate reviewed PRs. Do not loosen sanitizer/CSP/allowed selectors just to inflate a pass count. Never claim a real-model success from the fake CI fixtures. Do not merge or deploy.

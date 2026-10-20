# E3.9C-2B1 — Eight distinct browser-observed focus defects (NO model yet)

**Status: draft.** Builds directly on E3.9C-2A PR #146. This stage intentionally creates a reusable, independently browser-observed test set before adding real language-model evaluations. No model weights are pulled, no language model is queried, no operator computer is touched and no owner/production grants are created.

## Eight actual synthetic source variants, five CSS failure mechanisms

| Case ID | Fixed CSS defect in generated source | Expected real Chromium failure |
|---|---|---|
| `top-40` | focused skip link moves to top = -40px | focused element partly above viewport |
| `top-120` | focused skip link moves to top = -120px | focused element fully above viewport |
| `left-120` | focused skip link has left = -120px | focused element partly outside left viewport |
| `left-240` | focused skip link has left = -240px | focused element fully outside left viewport |
| `opacity-zero` | focused skip link has opacity = 0 | focused but invisible text/link |
| `visibility-hidden` | focused skip link has visibility hidden | first keyboard Tab cannot reach the hidden link |
| `transform-up` | focused skip link has translateY(-180px) | focus-box moves above viewport |
| `transform-left` | focused skip link has translateX(-320px) | focus-box moves offscreen left |

All eight modify exactly ONE existing focused CSS rule in the generated **test website**. They are defined by `e39d_heldout_focus_evaluator.py`, separate from the E3.9C-2A model adviser. Source generated for each variant receives a unique SHA256. Other CSS, the anchor's hidden-at-rest state, all JavaScript, and the generated HTML must remain immutable.

## What CI proves

1. `worker.py` builds the pristine synthetic website in the existing isolated, network-none Docker worker.
2. A known synthetic defect is seeded. `e39d_heldout_browser.mjs` launches **actual Playwright Chromium**, sends the first real **Tab**, and checks the focus target, computed visibility and opacity, DOM bounding-rectangle geometry and JavaScript errors at **390px mobile** and **1280px desktop**. The expected specific browser-failure code must match for BOTH screens. Screenshots and measured DOM receipts are stored.
3. The restoration harness verifies exact pre-failure evidence, checks the generated original stylesheet SHA256 against its manifest, and applies only the deterministic **test-fixture restoration** under `run_locked_worker.sh e39d-restore CASE` using **network-none, read-only root/repository, fixed writable temporary target, 1CPU, 256MiB memory, 64 PIDs, all capabilities dropped, 45-second timeout**.
4. Actual Chromium repeats the first-Tab test on **both** viewports and must observe a visible focused link after restoration. Each case retains source digests, before/after browser receipts, screenshots and worker Docker inspection. The original generated website scroll, responsive/mobile and reduced-motion suite runs once after the test cases.
5. Ubuntu and Windows run strict enum/source-hash/CLI/browser-proof tests; the complete Java orchestrator regression and no-authority/static source check run independently.

## Model-performance honesty

**Zero real AI inference calls are performed in E3.9C-2B1**. Eight distinct synthetic test cases are added, not eight model-generated repairs. The cases are **checked into the repository and thus not externally blinded**, despite being new to the old E3.9C-2A proposer. A future benchmark should keep the model prompt blind to the evaluator's canonical repair and ideally evaluate on a separately sealed independent test set.

Prior genuine benchmark facts remain unchanged: E3.9C-2A: Hermes 3B **1/1** and IBM Granite4 3B **1/1** independently browser-verified safe selector/value edits on the SAME single published keyboard task. Earlier E3.8C Hermes 3B had **1/3** on three distinct known website repairs. Those small experiments do not establish production coding accuracy.

## Follow-on: E3.9C-2B2

Implement category-specific, separately reviewed bounded response adapters for offscreen top/left, transparent, not keyboard-reachable and transformed focus failures. For each of the eight test cases, run genuine Hermes3B and Granite4 3B in separate opted-in disposable CI environments, capture all raw tokens/wall times/digests/refusals, apply only explicitly authorized constrained patches in a network-none worker and require authentic mobile+desktop Chromium after-verification. Maintain true numerators/denominators; do not credit the deterministic restorations as AI repairs. Improve evaluation independence and prompt-injection tests.

Protected `main` remains unchanged. Proposed 13-company/100-division organizational structure, 26 configured agent identities, 20 local model options, six free cloud routing options, 12 research-only model candidates, E4–E9 and Stage45 owner/production certification are unchanged and **not autonomously operational**.

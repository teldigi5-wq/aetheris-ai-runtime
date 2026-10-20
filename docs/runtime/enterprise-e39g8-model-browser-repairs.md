# E3.9G-8 — actual model CSS patches, policy-gated and Chromium-verified

**Engineering status:** draft experimental slice stacked on verified [PR #161](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/161) commit `184b06cde9636cf42f4c5ee8688f86ec8f7f8348`. Tracks [Issue #162](https://github.com/teldigi5-wq/aetheris-ai-runtime/issues/162).

## Goal and experiment boundaries

E3.9G-7 made eight genuine local Ollama calls but scored **static policy eligibility**, NOT repaired website behavior. E3.9G-8 actually attempts to apply the *model's* own proposed declaration if and only if it passes the original **unchanged E3.9E frozen CSS policy** in a network-disabled disposable worker, then runs a **separate real Chromium browser check** with full mobile and desktop evidence.

The four model×case treatments are repeated as two model families (`hermes3:3b`, `granite4:3b`), two already-public synthetic skip-link CSS failures (`top-120`, `opacity-zero`) and two arms (`baseline`, `skill-context`). The treatment contains tiny byte-verifiable extracts from Emil Kowalski MIT `emil-design-eng` and Aetheris-native `aetheris-web-accessibility`; the untrusted skill content is never executable. Two views (390×844, 1280×800), reduced motion, the same real before-browser observation, exact source hashes, and isolated ephemeral Ollama loopback are mandatory.

## What is actually executed, and where

1. **Independent initial fault evidence:** Run `e39d_heldout_focus_evaluator.py seed` and the existing Playwright Chromium `before` on both screen sizes; both must observe the intended failure.
2. **Real local models:** `e39g7_skill_ab_adviser.py` calls genuine pinned Ollama models twice per case; independent `e39g7_skill_ab_score.py` classifies with the original policy. All model responses and prompts are SHA256-bound and stored in a throwaway `target/` directory.
3. **Only sandbox policy can edit:** `run_locked_worker.sh e39g8-apply <fixed-model--case--arm>` runs `e39g8_policy_worker.py` as an unprivileged network-none Docker worker, with read-only source checkout, only target write access, fixed CPU/memory/PID limits and no Linux capabilities. It re-reads model evidence, the two BEFORE browser receipts, *actual* immutable seeded source, original policy outcome and `e39e_model_policy.validate_candidate`, rejecting any discrepancy.
4. **Independent after test:** If the model's patch is accepted, `e39g8_independent_browser.mjs` runs genuine Playwright Tab focus in both viewports against the changed disposable site, emits source and screenshot SHA256, geometry, style visibility, focus state and JS errors. It does **not** know the correct patch bytes or import the source gate. It records model failures rather than force passing them.
5. **Independent score:** `e39g8_independent_score.py` checks all receipts again, reconstructs the candidate under the frozen policy and counts `chromiumVerifiedModelRepair=true` only when the **model's proposal** was actually applied AND both after-browser checks passed. Rejected or abstained model outputs never run the after browser and always get zero repair credit.
6. **Non-model reset:** `e39g8-cleanup` resets the disposable styles to the same original defective source for the next A/B arm. Only after both arms are complete does the existing `e39d-restore` run the reference *fixture-only* recovery, which is **never credited as a model repair**.
7. **Raw evidence** from every model, case, arm, screenshot, CSS receipt and score is uploaded with GitHub Actions artifacts. The model's output is NEVER executed directly, and no private user site or owner laptop is involved.

## Security and truth

- NO changes to the frozen `e39e_model_policy.validate_candidate` or `e39d` baseline browser logic. No evaluator-to-model oracle hints.
- Static acceptance is a prerequisite, never an AI repair claim. No automatic deployment, owner authentication, new tool privileges, paid API, live trading or Stage45 GO.
- The browser runner is a CI test host bound to `127.0.0.1`; it has no credentials or production endpoints. Any changed CSS is under `target/e3-factory/site/styles.css` only, the original repository source is mounted read-only to Docker.
- Synthetic public defect cases are **not** independent externally selected real website tasks; at most they prove these tiny repaired skip-link problems inside the harness. A subsequent blind multi-component, multi-project study is required to assess actual website quality.
- Do not claim UI craft, 3D, motion, security pentest, full WCAG 2.2 AA compliance or reliability from these two focus cases.

## Next steps

Expand actual model repairs to randomized and truly held-out website tasks; add more responsive, animation, accessibility and security findings, ensure screenshot-based review, and integrate a verified human principal before an agent may act on anything outside a disposable test. E3.9F externally curated held-out model evaluation, E4–E9 engineering and separate Stage45 authorization remain outstanding.

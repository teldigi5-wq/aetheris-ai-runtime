# Aetheris E3.9A — New keyboard-accessibility browser defect, fail-closed evaluation

**DRAFT, CI-only. No model inference, owner-PC commands, production or protected main merge.**

## What is now genuinely implemented

The existing model evaluation has six source variations across *scroll, mobile overflow and reduced-motion categories*. This E3.9A increment establishes a **new fourth defect family** involving keyboard navigation, distinct from those previous categories.

- The canonical E3.0 synthetic website includes a visible-on-focus `.skip-link` accessible anchor with `href="#main"`, and a CSS rule `.skip-link:focus{top:12px}`.
- New `e39_keyboard_evaluator.py` requires independently generated pristine E3.0 website bytes and seeds a **single** wrong CSS rule `.skip-link:focus{top:-80px}`. This hides the focused skip link just beyond the viewport while leaving its keyboard focus in place. Its failure is meaningful for keyboard accessibility.
- New `e39_keyboard_browser.mjs` opens a *real* Playwright Chromium at 390px/mobile-size viewport with reduced-motion enabled, sends a genuine **Tab key**, inspects `document.activeElement` and the focused link's actual `getBoundingClientRect()`, checks that the link references the main content, and records screenshots, before/after JSON and JavaScript errors. A passing browser test must show an onscreen focused element.
- New `e39_keyboard_patch_gate.py` handles only an explicitly authorized **synthetic test proposal** for the single CSS literal. It enforces an exact file path, exact category, exact before-source SHA256, actual Chromium failure proof and its SHA256, strict existing-bug grammar, and exact regenerated clean source hash from the independently generated manifest. Unknown additional instructions, path traversal, forged owner-approval fields, stale evidence, multiple replacement spans and unexpected output all fail closed.
- Existing `run_locked_worker.sh` supports `e39-apply` **only** through the same Docker sandbox: network disabled, root read-only, repository mounted read-only, only the fixed target writable, 1 CPU, 256 MiB RAM, 64 PIDs, capabilities dropped, no new privileges, 45-second outer wall-clock timeout. Actual Docker inspect evidence is retained.
- After applying the **fixture-only** patch, Chromium must confirm keyboard visibility; the original complete desktop scroll, mobile-width and reduced-motion browser regression must pass again. Ubuntu/Windows policy tests reject malicious input and preserve inherited E3.8C safety tests. Full Java orchestrator regression remains mandatory.

## Essential distinction: this is NOT an AI model repair

The fixture code generates the exact known test-case proposal under operator-controlled CI, **not** under the Hermes/Qwen model. The proposal exists solely to prove that the new source scope and browser validation are correctly enforced. `modelWasCalled=false` and `ownerApproved=false` in receipts. It is **not** valid to count this as a new language-model success or as a blinded benchmark. The evaluator and policy are both reviewable in this repository; the new family is *previously unseen by E3.8C's proposer*, but **not blinded to the code author or test operator**. No model can access the new category yet.

The E3.8C baseline remains **1/3 real Hermes 3B browser-verified fixes** on old known categories, and E3.8B/E3.5 baseline **0/3** respectively. Green E3.9A CI will verify a new defect contract and patch security, not improved model capability.

## Next step: E3.9B actual two-model comparison

Extend the text-only model advice grammar with *separately reviewed keyboard accessibility repairs*, without automatically letting the model run shell, network or GitHub. Compare two genuine small models (Hermes3 3B and Granite4 3B, or Qwen3.5 4B if hardware appropriate) on held-out synthetic tasks. Require genuine inference and real Chromium before-failing/after-passing receipts, source/response hashes, resource budgets, prompt-injection tests and honest attempts/pass/fail accounting. Use disposable CI only and never count a fixture repair as a model achievement.

**Broader remaining milestones:** E4 model-selection/quality budget, E5 software/website factory, E6 true advanced 3D/GSAP/media pipelines, E7 real bounded inter-company workers, E8 human command center, E9 security/product certification, plus real Stage45 13-blocker physical owner-PC/production release. All remain NO_GO for owner-PC privilege escalation or live production until separately certified.

For the intended Aetheris development laptop, prioritize NVIDIA **GPU VRAM**, 64 GB upgradable system memory, 2 TB SSD, sustained cooling, official warranty and physical repair support. A 16 GB VRAM RTX 5080 laptop is a useful mid-to-high-end personal development target; it does **not** automatically run 30B-120B models entirely on-GPU. Laptop selection is independent of this CI-only feature.

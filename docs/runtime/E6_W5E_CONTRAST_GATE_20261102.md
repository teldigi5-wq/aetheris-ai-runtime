# E6 W5-E — Cross-surface accessible color contrast gate

**2 November 2026 · RESEARCH ONLY · No model inference · No merge/deploy**

## Canonical recovery

- Repository `teldigi5-wq/aetheris-ai-runtime`, primary GitHub account, not the education account.
- W5-E is a stacked child of open draft **W5-D PR #269**, verified exact parent `1aa5876df6b9561d9c83b4f90bda20f034215335`. Do not retarget onto protected `main`.
- W5-D exact-head automated tests were **74/74** source/security plus **24/24** mock-browser scenarios and no real-model download. Optional GitHub CPU smoke is separately label-approved and **has not been authorized**; do not apply its approval label in this stage.
- Runtime `main`: `65a6262717adcd52ac8d8a16ed6f223e299fd74d`, Platform `main`: `57ad3e0e602efa6feb275328254151b959a3499b`; both protected, with Stage45 NO_GO.

## Independently identified correctness defect

The W5-A model-proposal validator checked `ink` on `paper`, `accent` on `paper` and `ink` on `surface`. However the first alternating narrative section has a `surface` background, while the design's `.eyebrow` CSS displays `accent` foreground on **both** backgrounds. Thus some previously accepted palettes rendered low-contrast section headings despite all existing validation passing. Example: `paper #ffffff`, `surface #dddddd`, `ink #111111`, `accent #666666`: accent on paper exceeds 4.5:1, but accent on surface is approximately **4.23:1**, below 4.5.

## W5-E deliverables

1. **Fix** the existing source-bound `w5a_model_design_compiler.mjs`: require `contrast(surface,accent) >= 4.5` alongside its previous three checks, failing closed on model proposals before their HTML or CSS generation. Existing palette formats and model/owner/release policies remain unchanged.
2. **Regression tests** `test_w5e_surface_contrast.mjs`: verify contrast symmetry and extremes; attack the exact formerly accepted unreadable palette, multiple additional near-threshold cases, valid contrasting control, inheritance of the five human-authored fixture palettes, and refusal to bypass palette validation via W5-C generated CSS.
3. **Actual static browser acceptance** `w5e_computed_contrast_browser.mjs`: generate three known human-authored W5-C research fixtures in an isolated write-once target, serve each over loopback Python HTTP, and use real Chromium 320×700, 390×844 and 1440×900 sessions, nine views total. Verify actual `getComputedStyle()` text and background colors for **both** alternating section labels and body copy, at least 4.5:1; no scripts, no external requests, no errors or horizontal overflow.
4. **Exact-head GitHub Actions** `.github/workflows/runtime-e6-w5e-contrast-policy.yml`: carries forward all inherited W1/W2/W5-A/B/C/D security, compiler and model-free browser regressions, locked Next.js static typecheck/build and new contrast tests. Only the simulated human-written fixtures are used: **no Ollama model or GPU is requested or downloaded**, no local borrowed computer resources and no production action.
5. All previews remain separate and write-once in ignored `target/e6-w5e-contrast-browser-test-doubles`, with short-lived CI mock-only artifacts. Protected `main` and every earlier PR remain unchanged.

## Honest gate boundaries

This closes a **specific foreground/background contrast defect** for model-supplied theme tokens used by the current static site. It does **not** establish WCAG 2.2 AA across all text, states, browsers, zoom levels, custom model-written CSS, screen readers or devices; manual assessment remains required. It does not prove premium visual originality, real model authorship or an accepted three-brief Ollama trial.

The user's current WSL2 environment is a **borrowed friend's PC** and must not be used for model installation, downloads, GPU benchmarks or thermal stress. Optional CPU tests on hosted GitHub runners require separate owner approval; the approval label is absent and is not touched by W5-E.

Further milestones: a real held-out model trial on authorized remote/owner CPU after approval, independent design review, eventual GPU/thermal performance on suitable owner hardware, W6 accessibility/security/product evaluation, Runtime auth Issue #23, Platform backend auth Issue #251, nine Stage45 attestations. **All production and security release gates remain NO_GO.**

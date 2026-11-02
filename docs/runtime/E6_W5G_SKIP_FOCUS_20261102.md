# E6 W5-G — Genuine No-JavaScript Skip-Link Focus and Fragment Accessibility

**2 November 2026 · RESEARCH REVIEW ONLY · Stage45 NO_GO**

## Exact source checkpoint

- Canonical: `teldigi5-wq/aetheris-ai-runtime`, child of **W5-F draft PR #271** at verified parent HEAD `556ec269d8116e8964b3ecab4da0f48dce1a08e0`.
- No merges, deployment, paid services, release-control changes, borrowed-PC resource usage or real local-model downloads. Protected Runtime main `65a6262717adcd52ac8d8a16ed6f223e299fd74d` and Platform main `57ad3e0e602efa6feb275328254151b959a3499b` remain untouched.
- W5-F's 86/86 Node checks and 51/51 browser scenarios passed, but its skip-link keyboard check only proved the URL changed to `#main`, **not that actual focus entered the main landmark**. That evidence gap could allow misleading keyboard-accessibility claims.

## Exact corrective change

- `w5a_model_design_compiler.mjs`: generated `<main id="main" tabindex="-1" ...>` provides a real focusable fragment target while **not adding main to sequential Tab order**. No JS, script, analytics, network or extra authority introduced. Both `/` and `/approach/` paths are covered.
- `w5f_accessibility_policy.mjs` and `w5f_browser_accessibility.mjs`: existing W5-F keyboard evidence must include `skipLinkActualFocusOnMain:true`, verified against `document.activeElement === document.querySelector('main#main')`, not only URL hash and scroll; still requires visible first-Tab skip link, focus outline and actual cross-page Enter navigation.
- `w5g_skip_focus_policy.mjs`: enforce exactly one skip link and one `<main id="main" tabindex="-1">`, no duplicate id or model-supplied executable scripts, no falsely certified WCAG/release state. A subsequent Tab can wrap normally when no links follow main; the test rejects getting stuck on main rather than assuming that wrapping to the top is a trap.
- `test_w5g_skip_focus.mjs`: adversarial contract cases attack missing/duplicate/misidentified main landmark, wrong fragment, sequential main `tabindex=0`, hidden or inline-styled main, inserted scripts and fabricated browser focus, WCAG or production claims. The fixtures for W5-A (two pages) and W5-B/C (three projects × two pages) must preserve this correction.
- `w5g_nojs_focus_browser.mjs`: **three entirely human-authored fictional projects × three viewports (320, 390, 1440px) × two routes = 18 actual Chromium journeys**, with JavaScript disabled. It observes Tab first-focus/visible outline, real Enter navigation to `#main`, DOM `activeElement` on `main`, `tabindex=-1`, subsequent Tab moving off main, no outside requests or browser errors, and no horizontal overflow. Isolated write-once `target/e6-w5g-nojs-focus-mocks` and a test-double receipt with all release flags false.
- `.github/workflows/runtime-e6-w5g-nojs-skip-focus.yml`: exact-head read-only CI runs all inherited W1/W2/W5-A/B/C/D/E/F tests, locked Next.js build, 51 inherited browser cases, new W5-G source/contract tests and 18 additional no-JS focus journeys. No Ollama or GPU test is authorized.

## Acceptance meaning

A CI success will show that **the static generated pages support a real keyboard focus transition under the tested Chromium environments**, beyond a bare URL fragment. It cannot certify full WCAG 2.2 AA, actual assistive-technology announcements, every browser, mobile hardware, real 200% OS/browser zoom, human art direction or genuine AI-generated sites. W5-D optional owner-labeled cloud CPU smoke remains untriggered; user has not approved model download. All production release/security gates stay independent and NO_GO.

**Correct continuation:** after exact-head W5-G CI passes, prioritize actual model-output provenance on explicitly authorized compute and manual design/AT evaluation, not endless mock-only stage accumulation. Do not merge/deploy without release approvals.

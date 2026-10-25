# E3.9G-12B4I — Offline trusted negative/positive Chromium controls

**Research checkpoint: 25 October 2026.** Browser proof of the *test harness* only. No new model trial, no independently curated hidden site, no model-authored repair, no hosted application, no security certification.

## Why this exists
The eight genuine Qwen2.5-Coder 7B proposals in [B4D](e39g12b4d-scored-evidence-20261025.md) yielded 0/8 policy-approved/browser-verified repairs. B4E/F characterized denial classes. B4G keeps evaluator references private, and B4H verifies source-quote/receipt shape **without executing anything**. Before any future independently curated and explicitly approved fixture is rendered, the browser measurement itself must prove that it distinguishes known failures from fixed behavior.

## What B4I actually tests
Two **repository-owned trusted** and **public** source-only test fixtures, deliberately authored for the harness, each with a negative and positive HTML reference:

| Trusted case | Deliberately defective page | Fixed positive control | Independent browser assertion |
| --- | --- | --- | --- |
| Focus visibility | Skip link remains above the top of screen after keyboard Tab | Focus places it visibly at 16px | Tab selects .skip and bounding box is fully inside both viewport dimensions |
| Reduced-motion preference | Ticker keeps its CSS animation under `prefers-reduced-motion: reduce` | Ticker disables animation | Computed CSS animationName is `none` under reduced motion |

Real pinned Playwright Chromium verifies **mobile 390×844 and desktop 1280×800**, four comparisons / eight captured images. It checks deliberately broken control false and corresponding reviewed control true, ensures no horizontal document overflow, captures source and screenshot hashes, and fails on any JavaScript page error or network request.

## Isolation and failure modes
- Source comes exclusively from four exact allowlisted HTML fixtures in `tools/enterprise/e3_factory/e39g12b4i/fixtures/`. Static Python tests deny JavaScript, external URLs, package imports, unsanctioned files, forms or inline event handlers.
- Browser tests use `about:blank` and `page.setContent` with **JavaScript disabled**, blocked service workers and `context.route('**/*', route.abort)`. No localhost HTTP server, real browser accounts, credentials, model source, private curator references, live website or deployment.
- Positive controls come from trusted checked-in fixtures, **not the model** and not independent withheld reference checks. CI test success establishes that these specific scripted browser assertions discriminate these two known errors; not that all accessibility/functional issues are detectable or that a generic untrusted page is safe to run.
- The model-facing B4G ZIP and B4H quote-admission evidence are deliberately **not connected to executable browser inputs** by this PR. **Never run arbitrary curated/model HTML on the hosted Actions runner.** A future separate OS/container sandbox with network-none and read-only filesystem must be independently reviewed and explicitly integrated.

## Test evidence and limitations
`runtime-e39g12b4i-offline-browser.yml` checks source/trust-boundary and previous B2/B4G/B4H regressions on Ubuntu and Windows, then runs real Chromium on an ephemeral Ubuntu runner. Any failure invalidates this control. The workflow publishes only short-lived fixture screenshots and `report.json`, with `modelOutputExecuted=false`, `modelRepairSuccessRateAvailable=false`, `productionAuthorized=false`, and `stage45Go=false`.

**Next milestone:** obtain independently reviewed hidden customer-free, licensed challenges; create an evaluator-owned typed policy specific to their semantics, verify browser test rejection/acceptance on *those reviewed sites*, and only then run new model proposals through a truly OS-isolated worker with after-state evidence. Do not infer a model repair win from positive-control success.

**NO_GO:** protected `main`, Stage45, nine runtime release gates, workstation GPU/voice tests, E4–E9 and platform authorization/migration release blockers remain unchanged.

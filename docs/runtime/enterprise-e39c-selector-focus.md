# E3.9C-1 — Selector-scoped CSS repair policy with independent mobile and desktop Chromium

**DRAFT: test-only, no new AI inference, no model-controlled execution, no owner PC, no production.** Stacked on verified E3.9B PR #142. This slice starts E3.9C but does **not** deliver the larger independent 8–12-case dataset proposed in issue #143.

## Engineering motivation: two actual model mistakes

In genuine E3.9B inference, Hermes 3B fabricated an invalid CSS property value and copied the wrong source bytes. IBM Granite 4 3B suggested changing `top:-80px;` to `top:0px;`. That exact old span refers to the **hidden-at-rest** skip link, not the intended focused `.skip-link:focus` declaration. Therefore Granite's observed proposal is **incorrect, not a validated alternative focus repair**; the previous independent validator rejected it correctly. Both models scored **0/1 browser-verified repairs**.

The old E3.9A gate allowed only literal `.skip-link:focus{top:12px}`. E3.9C-1 introduces a more expressive but strictly constrained **selector-specific reviewed repair policy**, so in a later model evaluation a correctly targeted `.skip-link:focus{top:0px}` can be investigated without altering the hidden-at-rest rule.

## Implemented code

- `e39c_selector_policy.py` is a **narrow declarative selector/declaration policy, not a full CSS AST parser or arbitrary CSS diff engine**. It requires the exact selector `.skip-link:focus`, exact property `top`, a full source rule as the `old` span, exactly one new `.skip-link:focus{top:Npx}` declaration, and integer `N` in the reviewed range **0–24**. It rejects negative, fractional, out-of-range, malformed, multi-declaration, comment, URL, `!important`, selector injection and hidden-at-rest proposals.
- Source must be **byte-for-byte** identical to the canonical generated stylesheet except for one already-seeded focus-rule defect. The canonical source digest comes from the *separately generated* `site/manifest.json`. The proposed result must match the separately derived whole-file SHA256. It may not alter an unrelated selector, media query, layout property or repository file. It requires the real before-failed Chromium proof and exact source SHA256.
- A deterministic CI-only `fixture-proposal` for reviewed `top:0px` or `top:12px` exercises the new gate. **The fixture is not an AI proposal**; evidence explicitly marks `modelWasCalled=false`, `modelGeneratedThisPatch=false` and `ownerApproved=false`.
- Only `run_locked_worker.sh e39c-apply` may apply the test fixture in the preexisting **network-none** Docker worker with CPU/memory/PID limits, read-only root and source and a restricted temporary target mount. No language-model tool interface, arbitrary shell commands, remote providers or owner-device rights are added.
- The existing E3.9A Playwright browser proves the deliberately broken `top:-80px` focused link **before** the test repair and verifies the new on-screen focus position at 390px mobile afterwards. A separate `e39c_desktop_browser.mjs` checks a real 1280px desktop Chromium viewport. Evidence includes actual Tab-key focus, geometry, screenshots and page errors.
- A separate job builds and verifies the **untouched canonical website** using the original mobile, scroll and reduced-motion Chromium regression. That regression does not treat the `0px` alternative fixture as canonical source.
- Ubuntu and Windows negative tests cover forged owner claims, duplicate/confused CSS selectors, changed source hashes, modified unrelated CSS, spoofed manifests and malformed replacement syntax. Full Java orchestrator regression and no-authority checks remain mandatory.

## Status and benchmark integrity

The E3.9B real inference benchmark remains **Hermes 3B 0/1 and Granite 4 3B 0/1** on the prior new keyboard defect. This E3.9C-1 slice **does not run either AI model**; CI will be evidence only that a narrowly reviewed alternate CSS rule is behaviorally safe and correctly denied outside scope. It cannot justify a new AI repair count or claim general CSS parsing, or imply that Granite's wrong-selector suggestion was acceptable.

The original 13 proposed companies, 100 proposed divisions, 26 configured non-executing agent identities, 20 candidate local model routes, five named free-cloud model candidates plus one selector, and 12 future research references remain unchanged. User-PC autonomy, Stage45's 13 actual release gates, positive owner trust/action approval and production deployments are still **NO_GO**.

## Following increment, E3.9C-2

Implement a provenance-safe proposal format naming the CSS selector and declaration explicitly, require the model to propose a complete scoped rule (not ambiguous `top:-80px;` snippets), and re-run genuine Hermes and Granite on **multiple independent, separately held-out browser faults**. Build enough independent cases to assess hallucination/abstention patterns; do not blindly widen the patch language or claim representative programming ability. Any actual model fix must pass the same deny-by-default policy **and** fresh Chromium before/after proof.

Issue: <https://github.com/teldigi5-wq/aetheris-ai-runtime/issues/143>. No auto-merge, owner-PC use, paid hosting/API inference or background agents.

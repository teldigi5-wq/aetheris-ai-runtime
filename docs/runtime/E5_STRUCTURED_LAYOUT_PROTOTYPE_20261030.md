# Enterprise E5 — bounded structured-layout prototype (30 October 2026)

**Original enterprise E5 development, not S0 security. Stage45 NO_GO. Draft and unmerged.**

## Verified parent and intent

- Direct parent: [Runtime draft PR #227](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/227) at SHA `3a1cdb01de001fe7c9746cce6d0c4135adf29d69`; [GitHub Actions #41021284699](https://github.com/teldigi5-wq/aetheris-ai-runtime/actions/runs/41021284699) **55/55 Python tests and 3/3 Chromium checks passed**. The original E4→E5 copy trial, independently gated brief and real browser proof exist; the research reference layout remains entirely human-coded.
- This stage addresses the next narrow enterprise limitation: a future model can select **a few meaningfully different visual layout parameters**, not only change hero copy. It **does not** yet run a model, author arbitrary code, generate original site designs, publish a website, or independently verify creative skill.

## New working capability

`tools/enterprise/e3_factory/e5_structured_layout_preview.py` accepts exactly THREE local bounded JSON inputs at fixed, operator-controlled `target/e5` paths: `creative-brief.json`, `copy-proposal.json`, and `layout-proposal.json`. The new layout proposal is an exact version-1 object with three enumerated fields:

```json
{"version":1,"composition":"gallery","palette":"paper","typography":"editorial-serif"}
```

It permits **three** distinct compositions (`split`, `centered`, `gallery`), **three** static pre-authored palettes (`warm-stone`, `charcoal`, `paper`) and **two** typography variants (`editorial-serif`, `modern-sans`), for **18 bounded combinations**. A future untrusted model can supply these tokens but **cannot supply CSS declarations, JavaScript, HTML, URLs, remote assets, executable code, shell commands, custom colors, extra keys, document paths or deployment instructions**. Duplicate JSON fields, wrong types, over-budget proposals and unsupported token values fail before any output.

The compiler first runs **E5 #225's independent exact-brand and bounded headline constraints**, then reuses **E5 #204's fully validated human-built WebGL reference**. Both original `scene.js` and `styles.css` retain their reviewed original Git blob fingerprints. The only modifications are bounded `data-e5-composition/palette/typography` attributes, a local stylesheet reference `e5-layout.css`, and the previously admitted escaped brand/headline. The layout stylesheet's entire contents are **static human-authored trusted code**; nothing from an untrusted candidate becomes executable CSS text.

Different tokens control actual desktop positioning/order/grid columns, title typography and the color system. The last CSS section forces a safe single-column mobile layout, and the existing reduced-motion WebGL/static fallback is unchanged. Focus-visible treatment and text contrast are deliberately included for the light paper option. These are controlled design variations—not original art generation or proof of WCAG 2.2 AA certification.

The output is **one deterministic local ZIP** `target/e5/structured-layout-review.zip` containing exactly the three original website source files, E5 copy proposal, approved fixed layout CSS, inert layout-proposal JSON and a detailed integrity manifest. Source and artifact hashes bind the candidate choices and files. No overwrite is allowed; the output is created with 0600 permissions. The manifest explicitly states `E5_UNTRUSTED_STRUCTURED_LAYOUT_REVIEW_ONLY`, `siteCodeModelAuthored:false`, `independentModelRunVerified:false`, `independentAestheticReviewPassed:false`, `ownerApproved:false`, `deploymentAuthorized:false`, `productionReady:false` and release `NO_GO`.

## Independent verification

`test_e5_structured_layout_preview.py` covers distinct deterministic builds, 18-option limits, source-code pinning and drift rejection, malicious markup/URL/CSS/schema candidates, existing creative-brief requirements, no model or deployment capabilities, and nonoverwriting private output. The real Chromium script `e5_structured_layout_browser.mjs` independently opens three distinct compiled variants (`gallery/paper/editorial-serif`, `split/charcoal/modern-sans`, `centered/warm-stone/editorial-serif`) at desktop, mobile and reduced-motion configurations (**nine browser contexts**). It checks manifest/hash integrity, correct computed background and fonts, gallery desktop ordering, accessible landmarks, no horizontal overflow, reduced-motion static fallback and no remote resource loads or browser errors.

The SHA-pinned `runtime-e5-structured-layout-prototype.yml` reruns all **55 parent E4/E5 tests**, new layout tests, current copy/multi-brief mock evaluation and inherited Chromium tests in addition to the nine new visual-layout contexts. CI contains **no real installed AI model**. It therefore cannot claim the 18 possible compositions were model-generated or independently aesthetically evaluated.

## Remaining enterprise acceptance blockers

E3.9 authentic coding quality/repair on held-out tasks; E4 actual model-inference benchmarks and privacy/cost controls on owner hardware; E5 real model-suggested original design decisions, quality scoring, asset provenance, independent WCAG/performance testing and supervised website generation; enterprise E6 Cinematic Studio; E7 independent agent delegation; E8 Syntra desktop cockpit; E9 integrated resilience and release evidence remain incomplete. Separate S0 security is through S0-E6I in unmerged Platform PR #352 and is NOT the enterprise E6 Cinematic Studio.

All nine Stage45 release gates remain uncertified. This prototype does not create a route, call a cloud model, run code from a model, deploy any site, override protected main or promote a certified Runtime pin. **Stage45 NO_GO.**

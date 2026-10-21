# E3.9G-12B1 — two distinct disposable websites: real Chromium fault/control certification

**Draft, unmerged, Stage45 NO_GO.** Stacked on PR #170 exact verified SHA `31029d78b86f7508fdab2e65957a89ea100c883d`. Tracks issue #169.

## Why this is a needed correction
Through E3.9G-12A, Granite 4 3B, Hermes 3 3B and Qwen2.5-Coder 1.5B were tested mainly against three PUBLIC synthetic fault families from one site. Qwen 1.5B produced six real coding-model completions but no policy-approved functional repair; static source-targeting and separate semantic diagnosis also failed. The next benchmark must **separate evaluator validity and problem diversity from model competence**.

## Two actual separate websites (all data fictitious)
1. **StudyNest — course planner**: a student timetable page with semantic nav, separate cards and a skip-to-main link. Two independently observable faults: mobile grid demands **960px** in a 390px viewport and keyboard-focused skip-link is above the visible viewport. At desktop width the grid correctly fits, but keyboard focus still fails.
2. **CityLine — transit arrivals**: a structurally different dark-themed public information page with an animated arrivals ticker and station search. Two separately observable faults: the ticker still animates under `prefers-reduced-motion: reduce`; untrusted station search text is inserted using `innerHTML` and becomes HTML markup. The safe probe is an inert `<b data-e39g12b-literal>` element; **no JavaScript execution, exploit payload or external destination** is used.

These two sites are independently authored **within this project**, with separate HTML/CSS/JS and different user goals, but **both are author-created synthetic examples**. They are NOT external, independently curated, concealed, blind, naturally occurring websites, production services or a realistic sample of Internet websites.

## Credible test-before-model baseline
The new `e39g12b_two_site_corpus.py` seeds four copies into disposable `target/e3-factory/e39g12b/sites`: each site **broken** and a separately marked **reference control**. Golden reference changes are deterministic, exactly checked and not model-authored. The corpus is seeded ONLY inside the existing 1 CPU/256MiB `--network none`, unprivileged, cap-dropped Docker worker, with repo read-only and disposable target writable. The SHA256 of each source and the positive control are recorded; duplicates, symlinks, source drift and existing output paths are refused.

A separate real Playwright Chromium tester checks 390×844 and 1280×800 (reduced-motion emulated), four site/fault contracts across 8 browser runs (2 sites × broken/reference × mobile/desktop). It records source SHA256, screenshot SHA256, page JS errors, exact typed assertions and source/control identity.

Expected result when certification is valid: 4 **broken** site/view combinations fail at least one relevant contract and 4 **reference-control** site/view combinations pass every contract. StudyNest broken desktop is expected to fit horizontally; that is checked as an important negative control. CityLine demonstrates only DOM markup interpretation using non-executing text and motion preference; it does **NOT** test exploit delivery or constitute a security scan.

## No inflated AI claims
This slice contains **zero real model calls**, **zero model-generated patches**, **zero model after-patch browser tests**, and **zero browser-verified AI repairs**. A valid test harness is NOT evidence that Aetheris repairs either website, nor that skills or coding models improve results. The golden reference source exists in a test-only directory in this stage; **before a future model trial it must be isolated from model-accessible source and prompts**, with a new reviewed typed source policy. This stage does not satisfy the entire issue #169 or E3.9G-12B.

This corpus is for controlled quality evidence only. Do not deploy these deliberately defective pages to the Internet. A demo site intentionally using `innerHTML` on an inert input is a **negative security example**, not a production-safe implementation. No owner account, live website, real transit data, paid API, production deployment, autonomous tools, branch merge, real company operation or Stage45 GO is granted.

## Next slices
- **12B2**: move golden controls into separate evaluator-only CI trust boundary; establish a frozen, adversarially tested per-site single-file typed patch policy before any model can propose changes.
- **12B3**: run approved, same-run licensed coding model and historical baselines on these scenarios; untrusted prompts see broken source and browser BEFORE observations only; count real calls and allow only network-none policy-approved disposable changes.
- **12B4**: separate independently curated, concealed multi-project tasks, repeated trials, reliable success-rate estimates, defensive AppSec checks with genuine authorization, provenance/reproducibility and readiness certification.
- Trusted owner principal and durable scoped action lease remain independently blocked; do not merge into protected main.

# E3.9G-9 — genuine model proposals across three web failure domains

**DRAFT experimental benchmark only**, stacked on verified [PR #163](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/163) exact base `fe9a51894672c920fb211577efb998acae0de3d5`. Protected main never merged. Stage45 NO_GO.

## Why this slice

E3.9G-8 successfully observed one model-generated, policy-approved `opacity:1` website repair in real Chromium, but it tested only two skip-link focus cases. These cases cannot measure responsive layouts, application JavaScript or reduced motion. E3.9G-9 expands the **types** of defects rather than manipulating the old scorer to make performance appear higher.

We reuse THREE existing seeded E3.3 synthetic fixtures, rather than inventing a claim of an externally blinded benchmark:

| Fixture | Website failure | Target file | Browser observation |
| --- | --- | --- | --- |
| `mobile-940` | A CSS mobile min-width causes overflow | `styles.css` | Mobile 390×844 must fail reflow; desktop 1280×800 is a legitimate unaffected control |
| `reduced-boolean` | Browser reduced-motion media preference ignored | `main.js` | Both viewports with prefers-reduced-motion=reduce should use static motion state |
| `scroll-24` | Scroll-driven CSS 3D animation progress stays fixed | `main.js` | Both viewports must show progress >70% and actual 3D transform near 82% scroll |

This covers **layout, motion accessibility, and scroll interaction**, not security pentesting, large-scale visual quality, real business sites, or arbitrary HTML changes.

## Actual A/B design

For each model, exactly three cases and two arms: **six real Ollama model completions per model (12 in total if exact-head CI fully succeeds)**. Models: locally pulled `granite4:3b` and `hermes3:3b` in disposable CI, pinned limited CPU/RAM Ollama container, loopback 127.0.0.1 only, ephemeral weights. External provider/model API is never used; no owner laptop.

- **Control:** real independently measured before-Chromium geometry/state, exact bounded source excerpt, fixed structured old/new literal JSON response protocol, one proposal and same token/temperature budget.
- **Skill arm:** identical input plus non-authoritative exact-source reviewed excerpts from existing `emil-design-eng`, `aetheris-web-accessibility`, and relevant `break-ui` or `animation-performance` and `aetheris-web-performance`. Exact upstream Git blob and pinned source digests checked; no foreign skill can assign tools.
- Arm order counterbalanced for reduced motion, but **N=3 per model** is insufficient to infer statistical significance or claim skill effect.
- The model adviser imports **no gate, scorer, fixture answer or browser oracle** and can make no code edits, shell commands or web calls; only loopback Ollama JSON is allowed.

## Deterministic zero-authority patch workflow

1. Pre-existing fixed E33 defect seeding happens ONLY in the reviewed `run_locked_worker.sh` network-NONE Docker worker, which mounts repository source read-only, writes only to `target/e3-factory`, drops capabilities and disables new privileges.
2. Independent genuine Chromium browser script captures mobile+desktop BEFORE snapshots; requires the correct violated contract, source hash and screenshot hashes.
3. After real model calls, the **independent** `e39g9_multidomain_worker.py` revalidates browser before hashes, model response source hash, approved model/arm/case, exactly one literal code segment and the original E33 bounded grammar. Accept only a patch that produces the **exact original clean source bytes**, and never take open-ended model JavaScript for execution.
4. If accepted, independent Chromium tests both viewports AFTER the model-applied patch. Browser test does not import the model prompt or the original expected replacement.
5. `e39g9_multidomain_score.py` counts success ONLY when the actual model patch was applied and BOTH real browser screens pass. Rejected proposals stay unmodified and earn zero repair credit; fixture reset is separately verified and explicitly **not an AI repair**.
6. Both models store actual raw response hashes, model metadata (self-reported, not cryptographically attested), exact skill source hashes, browser observations, source diffs and screenshot digests in disposable Actions artifacts.

**No claimed skill gains or model scores before exact CI completes.**

## Safety and limitations

- These are **three prior public synthetic site faults** from the preexisting `E3.3` evaluation family, not real-world or independently externally sealed websites. Even browser-passing model patches here prove a very narrow one-literal ability and are not general software engineering.
- The gate is intentionally extremely conservative: a model suggestion which could potentially work but fails the reviewed grammar is rejected. Do **not** loosen it to increase success rates.
- Browser tests cover two exact viewports and one tested interaction per category. No complete WCAG 2.2 audit, visual quality/UX certification, security finding verification, 3D GPU profiling, production performance scores, cross-browser testing or ownership authorization.
- No owner authentication, lease, external repository write, production site, releases, privileged model agent tools, live trading, payments, or Stage45 GO.
- Next milestones: expand to true unseen real websites and more diverse problems, repeated trials and independent ratings, then authenticated agent-consumed skills; separately continue E3.9F truly withheld real-model benchmarks and E4–E9.

QA acceptance requires actual model calls, signed content-limited source provenance, Windows+Ubuntu tests, full Java regressions, real Chromium before/after and published raw evidence. The PR must remain DRAFT and unmerged.

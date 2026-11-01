# Enterprise E6 W2-C — Narrative case-study route compiler (review-only)

Date: 1 November 2026. Exact parent at branch creation: `0f3cdc0cad9c0dc78213ae026efef4c599f18d37`, Runtime stacked PR #258, OPEN/DRAFT/UNMERGED.

## What it is

An incremental upgrade from the working W2-A/B websites. W2-C adds **real linked case-study detail routes** and **scroll-readable editorial chapters**; it is not another color-preset iteration. The implementation stays purely static, deterministic, owner-reviewable and locally testable.

The original fictional Arcloom Objects sample now links from `/studies/` to three actual additional routes:
- `/studies/signal-fold/`
- `/studies/readable-measure/`
- `/studies/sundial-desk/`

Each case-study detail is a first-class nested HTML route with a contextual hero, clear fictional-project disclosure, three substantive chapters (question, proposed approach, what must be verified), accessible chapter links, archive navigation, a contact CTA, and responsive editorial layout.

## Controls and correctness

- Compiler: `tools/enterprise/e6_studio/next-studio/w2c_narrative_compiler.mjs`. Composes the existing `compileCuratedW2Site` after inherited W1/W2 structural, content, asset and CTA validation. New data-only `w2c_arcloom_studies.json` is bounded to existing validated archive items, exact safe slugs, required human-authored hypothetical narratives and explicitly independent verification plans.
- No extra arbitrary CSS, scripts, external URLs, or fetched assets are accepted. Generated markup escapes narrative text and uses conservative resource CSP from the inherited HTML. Every generated artifact gets deterministic SHA-256 evidence; no release, AI authorship, human review, a11y certification or deployment claim becomes true.
- Browser enhancements are **progressive only**. Native anchors remain functional without JS, chapter text is visible by default, IntersectionObserver only updates the chapter navigation's active state, normal browser scrolling is preserved. No smooth-scroll interception, scroll hijack, animation-dependent content or required WebGL.
- CSS story objects remain authored geometry placeholders, **not** Blender models, product photographs, approved media or commercial outcome claims. This is architecture proof, not a premium finished design or real AI authored site.
- Fixed local write-once CLI: `w2c_generate_preview.mjs` emits into `target/e6-w2c-arcloom/`, independent from W2-A and W2-B previews and refuses overwrite or supplied arbitrary paths.
- The W2-C CI (`.github/workflows/runtime-e6-w2c-narrative.yml`) verifies inherited W1/W2A/W2B + W2C unit/security tests, locked inherited Next.js static build, strict fixed preview output and an actual local Chromium navigation/keyboard/no-JS/mobile/reduced-motion matrix. **No CI result is claimed until checked against the exact PR head SHA**.

## Local preview from WSL2

Create a *new* detached worktree based on the eventual W2-C draft branch rather than changing your existing working W2-B preview. From its `tools/enterprise/e6_studio/next-studio` directory:

```bash
node --test test_experience_contract.mjs test_w2_compiler.mjs test_w2b_curated_compiler.mjs test_w2c_narrative_compiler.mjs
node w2c_generate_preview.mjs
python3 -m http.server 8767 --bind 127.0.0.1 --directory target/e6-w2c-arcloom
```

Windows Chrome: `http://127.0.0.1:8767/`, then `/studies/` and any individual study route.

## Remaining capability and risk

- W2-C expands the real layout/content/route engine; it **does not** finish full W2. Independently supplied owner briefs, advanced non-template components, meaningful visual differentiation, authentic media and subjective visual acceptance remain to be delivered.
- W3 licensed original photography/Blender GLB pipeline, W4 deliberate cinematic GSAP/R3F choreography, W5 genuinely generated model code from held-out briefs, W6 independent visual/accessibility/security/physical-device acceptance are separate unfinished gates.
- E3/E4/E5 model-research gaps, E6 Task 7/8, Runtime authorization Issue #23, Platform Issue #251, Stage45 production NO_GO and owner PC limitations remain unaffected.
- **No merge, no production deployment, no release authorization, no user secrets or customer data.**

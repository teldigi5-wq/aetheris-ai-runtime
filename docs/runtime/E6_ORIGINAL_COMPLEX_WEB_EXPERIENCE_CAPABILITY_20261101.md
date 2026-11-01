# Enterprise E6 — Original complex-web experience capability

**1 November 2026 · Research draft, stacked on PR #251 · Stage45 NO_GO.**

## Goal

Aetheris should eventually author, build, test and preview **complete original premium websites**, from editorial agency and apparel studios to genuine multi-scene product experiences. “Heavy” means ambitious authored content and interaction, **not** automatically heavy downloads or GPU load. The current Blender/GLB/R3F/GSAP one-scene research prototype is a useful engine component, **not a complete autonomous website generator**.

## Reference analysis (observed public content only)

| Owner-provided URL | Original Aetheris capability to learn | Not verified |
|---|---|---|
| https://studios.dothanyard.com/ | Brand-experience narrative, featured projects, team and highly differentiated service storytelling | Actual WebGL/GSAP stack, timings, mobile performance |
| https://labs.dothanyard.com/ | Agency service architecture, case studies, pricing/process presentation, responsive conversion journeys | 3D use or actual JavaScript weight |
| https://noolstudios.com/ | Editorial apparel/manufacturing identity, photography, process, image-heavy gallery and inquiry flow | Asset weight, rendering framework and license details |
| https://www.dothanyard.com/ | Campaign showcase, content production, brand story and clear calls to action | Site source code, performance or end-user conversions |

Study patterns, **never copy** source code, proprietary imagery, brand marks, CSS, client logos, case studies, testimonials or layout compositions. Every future site needs original art direction, factual content, rights documentation and owner review.

## Progressive rendering capability levels

1. **Editorial:** semantic HTML/CSS, original typography, authentic images, multi-page content, galleries, real navigation, accessible lead capture. Candidate stack: Astro/static-first or Next.js with minimal client JS.
2. **Cinematic:** editorial base plus purposeful CSS/WAAPI/Motion reveals and conditional GSAP ScrollTrigger. Native scroll by default; Lenis only after independent user/device checks.
3. **Immersive:** conditional, lazy Three/R3F/WebGL scenes using licensed Blender→glTF/GLB assets, measured GPU budgets, adaptive quality, loss recovery and full static/text alternatives. One active WebGL context is an initial planning constraint, not a proven runtime capacity.

Do not load every possible motion/3D library into every build. Select tech based on the experience and target audience.

## W1 — Actual new implementation in this draft

A pure local module at tools/enterprise/e6_studio/next-studio/experience_contract.mjs evaluates a **bounded version-1 original-site plan** (not user prompt text or code). It accepts site profile, project slug, up to twelve local page paths, finite section kinds, motion choices, explicit reduced-motion fallback and asset provenance. It rejects path traversal, extra keys, exotic objects/getters, missing 3D static fallbacks and oversized scene plans.

The plan recommends only needed stack layers and surfaces evidence obligations. It contains no URL fetches, model execution, files, client credentials, shell commands, uploads or deployment. It **cannot generate a website** yet. Successful input sets **all** authorship, browser testing, human review, deployment and Stage45 approvals to false. The 12 Node synthetic tests are under test_experience_contract.mjs.

Provisional planning ceilings (12 pages, 16 sections/page, 4 3D scenes/page) are *limits requiring rearchitecture above them*, not evidence that the current runtime renders those sizes.

## Real delivery roadmap

| Package | What must actually be built | Independent acceptance |
|---|---|---|
| W1 — Bounded experience contract | Strict plan validation, rendering tier and provenance/fallback policy | Unit tests + source review, NO release implication |
| W2 — Working multi-page website compiler | Generated routes, original page sections, navigation, responsive content, real form states | Reproducible build, actual Chromium mobile/desktop, full no-WebGL journeys |
| W3 — Asset production/optimization | Rights ledger, photography/video processing, Blender source, PBR, compressed GLB/KTX2 where justified | Asset provenance+hashes, visual comparison, decoded-memory/transfer data |
| W4 — Cinematic scene orchestration | Shared scroll controller, timeline composition, lazy scenes, disposal, reduced motion, stable pinning | Fast/reverse scroll, resize, focus, browser/GPU/thermal & CLS physical evidence |
| E5/E6-W5 — Authentic AI authoring | Approved local/cloud model proposes original layout and implementation through narrow safe patch gates | Measured held-out briefs across studio, apparel and product types; human independent adjudication; no copy-template reward |
| W6 — End-to-end quality gates | Real multi-page site evaluation, mobile, keyboard, screen reader, content proof, security, SEO, Core Web Vitals | Evidence from actual browsers/devices and explicit owner sign-off |
| E7–E9 integration | Governed operators, signed approval, preview/rollback, production promotion | Authenticated scoped tool actions, independent release attestation |

The current E3/E4/E5 research, E6 one-scene preview and E7/E8/E9 governance remain incomplete. W1 is a **planning foundation**, not equivalent to W2–W6.

## Open pinning checkpoint

On PR #251, native Chrome owner tests showed severe layout shifts during automatic scrolling with default ScrollTrigger pinning, but transform-mode comparison showed only four small shifts/run (max 0.0025; zero severe) in the experimental 360-frame sweep. Root cause, visual quality and cross-browser/mobile/keyboard behavior remain unverified. Never suppress these signals or silently promote the forced transform workaround.

## Design and engineering acceptance

- Brand-specific editorial art direction: convincing typography, real photography, authentic story, truthful client claims; no generic spheres, neon grids or fake customer proof.
- Accessibility: semantic page content independent of JS/WebGL, keyboard/focus/reflow/reduced motion, WCAG 2.2 AA target, static fallback, usable real navigation and contact forms.
- Performance **targets not measurements**: field p75 LCP ≤2.5s, INP ≤200ms, CLS ≤0.1; test actual scene GPU time, VRAM, battery/thermals, transfer and decoding; do not certify by synthetic rAF intervals alone.
- Security: identity and permission boundaries for edits, CSP/privacy, vetted source/media/license, supply chain integrity, no secret disclosure or untrusted model-code execution.
- Cost: no mandatory paid services or paid assets without approval.
- Preview only until explicit user review, production permission and all external Stage45 gates.

**Release state: Task7 unfinished · Task8 unfinished · Stage45 NO_GO. No protected-main merge or deployment.**

# Aetheris E3.9G-12B4C — gstack design/QA ideas and cinematic 3D readiness boundary

**Research + implementation only; DRAFT and unmerged**, stacked on verified [PR #175](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/175), exact base `07e04693fb42f12161f115199c52f66c5ffba655`. Protected main unchanged, Stage45 **NO_GO**.

## Upstream audit: provenance, branch limits and safe integration

Reviewed [garrytan/gstack](https://github.com/garrytan/gstack), **MIT** (Copyright (c) 2026 Garry Tan), protected `main` at exact commit **`b616d6c53dd8fc0d8db210444b44a131b553b83e`**, 4,249 main-tree entries and 536 returned branch entries over 6 paged GitHub branch API requests (pages 1–5 with 100 entries and page 6 with 36, page 7 empty). This is an **inventory**, NOT a manual code audit of all 4,249 items or of each branch’s changes. A small review sample of non-main branch sources was inspected: `garrytan/design-shotgun-security-audit`, `garrytan/browserharness`, `garrytan/security-wave-5` (one requested file absent there) and `capy/qa-only-readonly-optin`. Work-in-progress branches are not pinned release-grade trust anchors.

The new `e39g12b4c_reviewed_gstack_patterns.json` records **12 concrete reviewed upstream main-branch source file paths and blob SHA-1s** from design consultation, plan/design/engineering review, design variants, visual QA, functional browser QA, pre-landing code review, file-scope freeze, destructive command guards, and deterministic browser-skill fixtures. The read-only `e39g12b4c_gstack_pattern_gate.py` verifies all 12 exact paths, source identifiers, fixed 5-stage mappings, Git commit and MIT license, known branch inventory limitations, and **default-deny** capability switches. It blocks auto-activation, external third-party command execution, owner approvals, unattended git merge, real browser sessions/cookies, telemetry and production release.

These are **adapted architectural ideas**; Aetheris **does NOT vendor gstack skill implementations**, run `gstack setup`, launch the gstack browser daemon, install hooks, send telemetry, inherit implicit human approval, or claim new model abilities. Gstack content is lower-trust reference data; its skill frontmatter, tools and commands are NOT instructions to Aetheris or the owner. There are no mandatory subscriptions, browser plugins or paid remote model APIs in this slice.

### Proposed Aetheris cinematic site production lane (not yet autonomous)

| Role / gate | Required evidence and constraints | Current measured state |
|---|---|---|
| Product/art director | Brand brief, imagery rights, reference screenshots, typography/visual system, scene-by-scene storyboard | No completed autonomous art direction benchmark |
| FE cinematic engineer | Independently valid source for a real 3D scene; Three.js / React Three Fiber as *possible* optional stacks, reviewed GSAP/ScrollTrigger licenses and responsive fallbacks | No generated real production 3D scene proven |
| Motion engineer | Scroll progress must drive actual camera/object/material state over multiple timestamps; text/video overlay and scene transition timing; scroll reversible | No audited model-generated scroll-directed 3D implementation |
| Accessibility engineer | Keyboard, semantic headings, focus, reduced-motion fully functioning static alternatives, contrast checks | Existing targeted synthetic tests only |
| Performance engineer | Real mobile GPU/CPU/thermal/frame-time measurements, asset sizes, 2D fallback and graceful WebGL failure | No such measured cross-device scene budget |
| Security engineer | CSP, dependencies, supplied video/model asset licenses, data flow, injections, user approval and path restrictions | Static research/strict source policy only |
| Browser QA | Real Chrome screen captures + video/scroll samples at mobile and desktop with independent reviewer acceptance | 8 controlled simple-page probes; zero model-authored web repairs |
| Release manager | Owner identity/task authorization, preview, manual review, CI supply-chain QA and explicit deployment approval | Release blocked; Stage45 NO_GO |

This staged workflow is modeled from useful gstack patterns but is **not a deployed agent workflow** yet. Successful reference documentation does not imply real code execution or browser control.

## New actual real-browser negative control

`e39g12b4c_cinematic_baseline.mjs` tests the **original two disposable NON-3D public reference websites** (StudyNest course planner and CityLine transit board) in **real headless Playwright Chromium** at **390×844 and 1280×800**. It checks DOM/Canvas WebGL presence, identifiable scroll scene elements, whether any such element changes computed transforms during scrolling, reduced-motion setting, unexpected JS errors and SHA256 of actual screenshots. This is an **honest negative-control observation**: those two fixtures have no 3D scene; neither tells us what a future real cinema-generating model could produce. These tests must never be advertised as certified cinematic 3D creation.

The companion `evaluate_cinematic` Python function is an explicitly **UNTRUSTED RECEIPT preflight**: even fabricated perfect-looking JSON with asserted 3D and 1000 GPU frames can **never certify independent runtime capability**. It requires independently acquired browser evidence, accessibility, full scroll-dependent motion, performance and source provenance before later release approval. Only a future trusted isolated browser harness can produce an independently meaningful verdict.

## Remaining acceptance work

1. **E3.9G-12B4D**: execute original-vs-source-span contemporaneous **real** coding-model A/B (Qwen7B or reviewed equally-licensed alternative), no-checkout proposer, same 4 test cases and safe unchanged B2 one-literal policy; independent Chromium after on only policy-accepted patches; honest success rates / cost accounting.
2. **Cinematic Lane 1**: a deterministic, **human-authored** public-domain procedural WebGL/Three.js + scroll demo under disposable `target`, with 2D reduced-motion fallback, exact source checks and real Chromium. Human-authored reference controls are NOT AI creativity evidence.
3. **Lane 2**: stronger licensed code model generated 3D/scroll code only for constrained template components in a read-only/isolated environment; real independent Chrome visual and scroll tests, no package execution from model text.
4. **Lane 3**: dynamic 3D scene transitions, shader/memory performance and mobile QA under real device budgets; proper attribution/rights of any fonts, video, textures and models.
5. **Lane 4**: independently curated genuinely held-out luxury page tasks; accountable model + agent editing and rollback performance, release auth and preview only with owner explicit approval.

**Capability status:** can advise on design patterns and run synthetic QA, but **CANNOT YET RELIABLY BUILD, TEST, REFINE AND SHIP a premium cinematic 3D website autonomously.** Last Qwen2.5-Coder 7B real browser repair trial was **0/4**, not a demonstration of independent motion design. Do not claim a gstack source reference as a new agent privilege. No owner PC, authentic live website, paid services, protected main merge or Stage45 release.

# Aetheris Enterprise E6 — Professional Cinematic Studio technology architecture

**30 October 2026 · DRAFT · Independent Enterprise E6 (not S0 security E6) · Stage45 NO_GO.**

## Source of truth and predecessor

Direct parent is [E6 draft PR #233](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/233), verified on `200317a9ea170c935cf3517dc60b837b86b73c45`. [CI #41032646672](https://github.com/teldigi5-wq/aetheris-ai-runtime/actions/runs/41032646672) passed 12/12 Python asset/source tests and 9/9 real Chromium positive/negative cases (original WebGL scene and pinned glTF import). The original E3.9–E5 AI model/code stages remain uncompleted; this **separate** E6 research branch must not claim they are completed or override main/release.

## Decision: one primary stack, optional alternatives — not 13 renderers in one bundle

| Layer | Primary choice, implementation and rationale | Alternatives and allowed roles |
| --- | --- | --- |
| Website framework | **Next.js 16.4.3, React 19.3.1, TypeScript 5.9.3** — static App Router research export, typed scene and client-side progressive enhancement | **Astro** for static-first marketing/documentation with React islands; **Webflow** as an optional, separately approved visual authoring workflow. Webflow hosting/account may carry plan limits and is *not* a required expense. No automatic commercial imports or copied Webflow templates |
| 3D rendering | **Three.js 0.186.1 + React Three Fiber 9.8.2** — declarative scene, bounded DPR, demand frames and an original owned binary glTF asset | **OGL** for a custom lightweight WebGL route; **Babylon.js or PlayCanvas** for alternative interactive 3D/application engines, not parallel live renderers; **Spline** only for evaluated designer-exported assets with licensing/provenance checks |
| Scroll narrative | **GSAP 3.15.0 + ScrollTrigger** — one source of scroll progress, pinned scene, camera controls and cleanup | **Motion 14.6.0** for local UI/view transitions, **WAAPI** for tiny non-layout effects; neither competes for the 3D camera timeline. **Lenis 1.3.27** as user-enabled desktop smoothing only; native scroll is default and reduced-motion turns it off. **Theatre.js** for future offline keyframe authoring, not runtime auto-import yet |
| 2D/compositing | Plain CSS for the research page and semantic content | **PixiJS** for approved high-density 2D particle/texture compositions when needed; not an automatic dependency. Canvas2D fallback where a 3D experience adds no user value |
| Asset authoring | Deterministic script creates an exact owned, self-contained **glTF→GLB** research mesh; no third-party downloads. The GLB includes a human-authored **PBR metallic/roughness material** | **Blender** for actual asset production/export, rigs, optimized UVs and baked lighting; human source files and licensing must be preserved. Production asset versions must be separately reviewed, not auto-ingested |
| Compression | Current 36-vertex reference asset deliberately **uncompressed**; its actual output bytes and SHA-256 are recorded | **glTF Transform + Draco or Meshopt** for large assets only after real before/after sizes, decode costs, compatibility and workload tests. **KTX2/BasisU** for appropriate real production texture maps; use normal/roughness/metallic maps only when they serve an approved material |
| Cinematic lighting | Actual Three.js **ACES filmic tone mapping**, directional/ambient lighting and PBR material from original owned GLB | Custom **GLSL** shaders for scoped, reviewed effects; opt-in postprocessing **bloom/DoF/bokeh** only with GPU budgets and visual QA. No default neon, excessive glow or unapproved cinematic stock assets |
| External authoring | No required external platform; CI/tests/build can run on free developer environments | Webflow's GSAP integrations, Spline, PlayCanvas, hosted asset APIs or design marketplace purchases require separate consent, licensing and cost review |

> GSAP currently offers a commercial-friendly no-charge standard license, but Webflow as a product is a separate platform. Revalidate license terms before a public release. Draco and Meshopt optimize **transfer representation** and require decode; they do not directly solve excessive draw calls, shader cost, overdraw or runtime memory. The frequently repeated “50 MB to 1.5 MB” is an illustrative possibility, **not a guaranteed benchmark**.

## What this branch implements now

1. **A standalone Next.js App Router app** under `tools/enterprise/e6_studio/next-studio/`, independent of the Java runtime, gateway, existing E3.9H WebGL proof and previous E6 glTF reference. All framework deps are exact declared versions in the research `package.json` (the final dependency lock still requires review and commit before release). `next.config.ts` outputs a static export and removes framework identification header.
2. A safe **GLB build** (`scripts/build_research_glb.py`): copies only previously validated **owned and human-authored** 36-vertex/864-byte geometry into a glTF 2.0 binary container, with one authored physically based material (metallic 0.64, roughness 0.34). Asserts max GLB budget and exact canonical layout, emits SHA manifest, **never overwrites a different pre-existing asset**. This is **not a Blender export and not compressed**.
3. A client-only **R3F scene** imports the local public GLB through the Three.js GLTFLoader and adjusts camera angles to GSAP's single scroll progress. The Canvas is `frameloop="demand"`, DPR capped at 1.5, no real-time shadows, and applies ACES tone mapping. The larger ~1.2-million-pixel bound from the prior raw-WebGL prototype is **not yet enforced** on this R3F app; next hardware/perf stage must include it or demonstrate an acceptable equivalent.
4. **ScrollTrigger** pins the 3D story only for animation-capable visitors, updating an explicit three-act progression; `gsap.context(...).revert()` cleans up on unmount. Native scrolling is the default, and **Lenis requires active opt-in**; no smooth-touch override. Motion handles small text transitions, WAAPI handles a tiny status entrance. Their ownership scopes do not overlap.
5. **Reduced-motion visitors** receive DOM/static geometry with **no Canvas allocation**. A static silhouette is visible while the 3D model has not loaded or when WebGL is unsupported. Semantic chapters, links, keyboard focus/skip navigation and an explicit NO_GO notice are never inside a Canvas.
6. **Evidence**: Python source-and-asset verification; TypeScript/Next build; actual Chromium on desktop, mobile and reduced motion on the *exported static app*; real scroll progress/reversal and no external resource requests. CI also preserves exact pre-existing E6 WebGL/glTF browser regression evidence. The preview is not deployed to any public site.

## Future E6 gates — do not silently install all technologies

- **E6 asset production**: review Blender .blend source provenance and exports, glTF/GLB structural validation, asset author/license records, normalized polygon/texture budgets and physically correct lighting maps.
- **E6 optimization**: introduce glTF Transform opt-in Meshopt/Draco, KTX2/PBR normal maps and clear before/after network+VRAM/decoder metrics on representative meshes; don't optimize this already-tiny reference simply to claim an impressive compression ratio.
- **E6 cinematic tooling**: controlled Theatre.js keyframe authoring plus GSAP orchestration, staged custom GLSL, optional DoF/bloom; certify screen-reader, reduced-motion, mobile GPU and visual quality for each.
- **E6 production acceptance**: reproducible checked-in lockfile, SBOM and dependency audit, CSP and security response headers for the actual deployment target, owner/device GPU frame-time/thermal benchmarks (including low power), recorded WebGL fallback failure injection, independent WCAG 2.2 AA verification, design review and human owner approval.

## Strict no-go

No customer data or model calls, no paid APIs, no automatic Webflow/Spline asset import, no external/untrusted glTF parsing, no autonomous model-code execution, no deployments or protected main merges. This is not certified original AI visual design, real GPU performance or a finished Cinematic Studio. Enterprise E3.9 coding, E4 model quality, E5 complete website factory, full E6, E7 multi-agent companies, E8 Syntra control and E9 integrated release remain unfinished. Independent S0 security last verified to S0-E6I. **Stage45 0/9 certified. Stage45 NO_GO.**


## E6 dependency-provenance follow-up (30 October 2026, draft)

The exact-head #234 workflow run 41034930896 produced a **provisional** npm
lockfile in its 7-day evidence artifact. This follow-up validates the lockfile
v3 graph, exact top-level manifest equality, official npm HTTPS tarball URLs
and SHA-512 integrity fields, plus compares the provisional lock's SHA-256
against the recorded artifact digest
`efe16b99de61d2042a05fa19f43a89e943ee105cfa65a36112cb67e0d686ee33`.
Changed registry resolution fails closed rather than silently changing tested
dependencies. The workflow runs npm ci with lifecycle scripts disabled, emits
a CycloneDX SBOM, and requires an npm audit without high/critical advisories.

**Not yet achieved:** the lockfile is not committed; the generated provenance
check alone cannot prove reproducible production installation or that registry
integrity metadata is trustworthy. The live audit, full license review, independent
security acceptance, physical GPU testing, Stage45 certification and deployment
remain pending. Preserve the draft stack and commit a reviewed, exact lock in
a separate safe follow-up before any release.

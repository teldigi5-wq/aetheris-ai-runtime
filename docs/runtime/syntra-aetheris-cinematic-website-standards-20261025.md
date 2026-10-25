# Aetheris / Syntra — brand-grounded cinematic website engineering standard
**Date:** 2026-10-25 | **Status:** reviewed first-party advisory guidance in a draft runtime integration branch; NOT an automatic website builder or authorized production agent.
**Origin:** owner decisions in "Explain Smooth Website Technology" (24 October 2026). This document records user requirements, not verified implementation of all visual technology.

## Non-negotiable creative direction
- For *every* company/brand website, first research the real business, products/services, audience, customer journeys, competitors and credible references. Distinguish established facts, mockups, synthetic examples and unverified marketing claims. Inspect the existing brand and preserve working sites, customer data and legitimate user flows.
- Design a singular, modern, refined, premium brand experience with thoughtful photography, real-world materials, light, authentic product/architecture/people, disciplined typography, hierarchy, grid and usable conversions. The "WOW" result should come from authentic art direction and coherent interactive storytelling.
- Do NOT equate luxury or futurism with glowing rings, generic 3D wireframes, floating spheres, gratuitous neon grids/particles, tunnels, arbitrary glassmorphism or generic dashboard mockups. No fake customers, case studies, metrics, security claims or simulated functionality presented as real.
- Establish a visual system and scene-by-scene responsive storyboard before selecting animation libraries. Check copyrights/licenses for fonts, media, models and sound.

## Choose the smallest effective technology stack
| Need | Possible tools | Review obligation |
|---|---|---|
| Content-first UI | semantic HTML, CSS, TypeScript, React, Next.js, reusable tokens, Tailwind if suitable | Clean navigation, maintainability, mobile, search and progressive enhancement |
| Basic interaction | CSS, Web Animations API, Motion/React Spring | Composited transforms/opacity, accessible reveal, no layout thrash |
| Cinematic choreography | GSAP timelines, ScrollTrigger | Measured cost; reversible scenes; ScrollTrigger refresh/cleanup; no blocked content |
| Optional smooth scroll | Lenis | ONLY after testing native touch, keyboard, anchors, history; never hijack mobile scrolling |
| Real 3D | Three.js, R3F, WebGL/WebGPU, GLSL | Believable purposeful interaction, correct lighting/materials, GPU budget, device fallback |
| Offline render/media | Blender or Cinema 4D, frame sequences, optimized video | Rights, media size, realistic assets, static images for failed/unsupported 3D |
| Quality verification | Playwright/real Chromium, DevTools, Lighthouse, accessibility tools | Evidence recorded against exact source/version, multiple viewport and input modes |

Libraries are possible choices, **never mandatory**, and no paid API or tool is assumed. Never load all visual stacks together by default. Production deployment, external URLs, model execution and repository modification require their separate authenticated approval boundaries.

## Animation design standard
Coordinate masked typography reveals, staged editorial headings, subtle staggered content, elegant hover/focus microinteractions, purposeful parallax, video/camera/lighting/material transitions, seamless section/page motion and storytelling chapters. Use pinned scroll segments only if they improve orientation on capable devices. Preserve reverse scroll, fast scroll, navigation changes and resizing. Avoid loops with no user value, motion sickness, fake loading sequences and effects that hide navigation or content. Follow `prefers-reduced-motion`, offer pause when appropriate, and preserve a fully functional text/2D fallback.

## Progressive responsiveness and performance
Review widths **320, 360, 375, 390, 430, 768, 1024, 1280, 1440, 1920** plus portrait/landscape and short-height. Touch scrolling and clicks must be native/reliable on phones. Avoid overflow, overlap, unreadable text, clipping, frozen pinned scenes and heavy full-screen canvases. Use DPR clamps, adaptive scene quality, lazy-loaded media, offscreen animation pauses, GPU resource disposal, low-power fallback, and reduced blur/particle/shadow cost. Quality degrades before interactivity, battery life or local AI model resources.
**Goals, not claims:** 60 fps on documented supported hardware; measured p50/p95 frametime and dropped frames; Web Vitals field p75 LCP <=2.5s, INP <=200ms, CLS <=0.1. Record actual source SHA, hardware, browser, device class, network/cache, repeat runs and raw traces. CI synthetic Chromium checks do not certify real phones, true photorealism or production Core Web Vitals.

## Accessibility, security and delivery
Semantic HTML, visible keyboard focus, labels, navigability, reliable form states, zoom/reflow, screen-reader evaluation where possible, usable 44px touch targets and **WCAG 2.2 AA as a target**. Text, CTAs, key interaction and SEO content cannot depend on animation, canvas/WebGL or JS.
Review authorization, data privacy, OWASP-relevant protections, content security policy, appropriate security headers, external scripts, secrets/client exposure, supply chain, asset licenses, injection/CMS content, safe links, file loading and no fabricated audit claims. Log only safe diagnostic information.
Use a preview-first workflow: verified brief and design rationale -> architecture/risk review -> isolated implementation -> real browser/visual QA across viewport matrix -> performance, accessibility and AppSec evidence -> independent user acceptance -> explicit owner deployment/merge authorization. No production write or agent-run automation follows automatically from this policy.

## Agent responsibilities and boundaries
Our **existing 26-role canonical main-based catalog** has read-only, default-disabled skill references for exactly seven allowed specialist IDs. A separate Stage46 draft expands the organization to 30 roles (four additional Cybersecurity officers); it is not silently merged into main.
- `frontend-engineer` — `aetheris-web-experience`, `aetheris-web-accessibility`, `aetheris-web-performance`: propose screen and motion implementation with fallback and evidence.
- `software-architect` — `aetheris-web-experience`: select honest architecture, libraries and quality tradeoffs.
- `qa-engineer` — `aetheris-web-accessibility` plus existing animation review: independently evaluate journeys, responsive issues and motion.
- `appsec-officer` — `aetheris-security-audit`: browser, delivery, data and agent security gate.
- `security-architect`, `devops-engineer`, `mcp-integration-engineer` retain existing scoped security/supply-chain boundaries; no arbitrary website permissions added.
- **Proposed only:** Aetheris Creative / Aetheris Immersive Technologies design, cinematic art, 3D and rendering teams and CEO/design advisory roles; no agent registration, tool delegation or build capability implied by proposed division names.

These first-party instructions are trusted as reviewed **reference content**, not authorization: `skills/registry.json` stays at 14 skills (6 pinned third-party, 8 local), every `capabilities=[]` and `autoActivate=false`. Existing allowlists and `application.yml` must not change; owner authentication, Stage45 release, model access, filesystem writes and host commands remain blocked unless separately certified. No unreviewed upstream skill scripts are installed or executed.

## Evidence-based acceptance
1. First-party skill registry and canonical 26-agent role bindings validate without metadata drift.
2. Explicit-reference CLI refuses selection without both opt-in and reference-request marker.
3. Three core web skills + defensive security advice articulate the brand, motion, A11y, real performance and OWASP/privacy standards.
4. Draft CI validates guidance and Windows/Ubuntu structural tests. CI success validates the **policy references**, not a produced cinematic website.
5. Later milestone must prove a genuinely functioning site built by an authorized agent: real assets/rights, browser screenshots, accessibility, device performance, security review, task-scoped approvals and preview acceptance. The existing cinematic proof remains NO_GO until that evidence exists.

This change intentionally does **not** touch Aetheris BoundaryLab or any other customer website repositories, protected branches, executable runtimes, API endpoints or live model execution.
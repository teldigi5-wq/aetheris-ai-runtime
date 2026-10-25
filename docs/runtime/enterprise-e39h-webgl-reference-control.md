# E3.9H — isolated WebGL2 procedural reference control (not an AI capability pass)

**Development checkpoint: 2026-10-25.** Initial exact-head controlled-browser reference CI **PASSED 3/3 jobs** at commit `936141eaef34b6c917a4ff2939eecc77f6eb30d7` ([Actions run #40156195539](https://github.com/teldigi5-wq/aetheris-ai-runtime/actions/runs/40156195539)). This validates the reference behavior only; Stage45 **NO_GO**, physical owner PC **BLOCKED_PENDING_HARDWARE**.

## Objective and scope

The existing E3.9G-12B4C browser checks measured two deliberately non-3D pages: a valuable negative control but no evidence that an actual 3D canvas can change with scroll. This separate E3.9H slice adds a self-contained `reference/` **positive test fixture**: a procedural architectural stone volume rendered in real WebGL2, scroll-controlled in both directions, with semantic editorial copy and static alternatives.

These files were authored for this controlled research comparison; **not** established human-approved creative direction, a separately trained model implementation, an agent-authored output, paid client work, or proof of the site's artistic quality.

## Files

- `tools/enterprise/e3_factory/e39h/reference/index.html` — semantic editorial fixture and always-readable descriptive copy.
- `tools/enterprise/e3_factory/e39h/reference/styles.css` — adaptive layout, viewport fallback, focusable anchors, reduced-motion and static media.
- `tools/enterprise/e3_factory/e39h/reference/scene.js` — no-network WebGL2 geometry/shader, progress-driven rotation and redraw, context-lost static alternative; no imported model code, external assets, subscriptions, host commands or privileged APIs.
- `tools/enterprise/e3_factory/e39h_browser_proof.mjs` — actual Chromium 390×844 mobile and 1280×800 desktop checks, three scroll checkpoints each, independent canvas pixel data URL SHA256 changes, reverse-scroll checks, captured screenshots/source evidence, reduced-motion and forced no-WebGL fallbacks. Browser requirement fails closed if actual WebGL2 context or changing rendered pixels is absent.
- `tools/enterprise/e3_factory/e39h/test_policy.py` — static content, local-only asset, inert API and truth-boundary contract.

## Confirmed scoped CI observation

The passing workflow establishes only that a **particular pinned Chromium runner** demonstrated functioning GPU-emulated WebGL2 scene rotation, actual changing canvas pixels, reduced-motion/static fallback, mobile no-horizontal-overflow and no JS page errors. It does NOT establish real phone GPU performance, high artistic quality, true photorealism, zero power usage, production Core Web Vitals, commercial assets, cross-browser compatibility or any AI model's coding ability.

The CLI/web test uses an isolated localhost server with fixed files only. The browser uses software GPU support on ephemeral GitHub Actions Linux; no actual owner device, personal login, cookies, site accounts, uncontrolled shell or public deployment are used. Evidence is stored only as disposable CI artifacts with source hashes.

## Current E3.9H issue #177 acceptance mapping

| Issue #177 acceptance | Effect of this slice | Not yet achieved |
| --- | --- | --- |
| Human-directed licensed reference | Procedural original no-external-assets reference + reproducible source | Independent professional human design approval and curated luxury brief |
| Real 3D scroll motion | Actual WebGL2 shader and scroll/raster evidence, if exact CI passes | Broader GSAP/Three.js scene and advanced camera/light/material animation |
| Mobile/desktop/a11y | Two viewport browser contract, reduced/no-WebGL static fallback and headings | Real device profiling, screen reader/human UX, high-DPI and full WCAG audit |
| Performance | Static size policy, browser screenshots | Real frame-time/VRAM/thermal/CWV benchmark and adaptive budgets |
| Genuine model-produced code | NONE — original controlled fixture | E3.9G-12B4D + independent constrained AI scene/edit trials |
| Held-out independent quality and release | NONE | Separate challenge corpus, provenance/license, owners' approvals and isolated preview |

## Follow-up milestones

1. Verify this exact-head CI succeeds without weakening assertions, and review screenshots and scene code manually.
2. Proceed to **E3.9G-12B4D** independently: original vs source-span A/B on real coding model, original typed patch policy unchanged, independent Chrome after-state, no golden leakage, honest success rates.
3. E3.9H stronger positive control: reviewed purposeful Three.js/GSAP animated real camera/light/shader transitions; test 320/390/768/1280/1920, accessibility, GPU failure/restore, native scroll/reverse, real mobile frame-time.
4. Evaluate actual locally licensed coding-model generated changes strictly in disposable worker, compare separate held-out cinematic briefs and human review; no unsupervised package execution, source overwrite or deployment.
5. Maintain the nine runtime release gates and 13 Stage45 NO_GO blockers until authenticated owner-device evidence is reviewed.

## Safety invariants

`main` remains untouched; no runtime HTTP endpoint, owner role, token, approval, network egress, cloud service, installed model, workstation process, production website or live-money capability is enabled here. A procedural positive control is **not** evidence of autonomous creative agents.

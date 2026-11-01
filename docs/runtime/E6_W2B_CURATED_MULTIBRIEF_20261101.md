# Enterprise E6 W2-B — Curated multi-brief website implementation

**1 November 2026 · Development research, not production · Stage 45 NO_GO.**

## Provenance and branch topology

- Canonical repository: `teldigi5-wq/aetheris-ai-runtime`.
- W1: Runtime draft PR #255, exact parent before W2-A `42ba023e217f98bebbba57983d458b18cf3017e2`.
- W2-A: draft PR #256, source and real Chromium evidence at `1ebd075ac3a215046e7a4b8048783f2d1961832a`.
- W2-B branch: `feat/e6-w2b-curated-multibrief-20261101`, based strictly on W2-A.
- No branch in this stack is merged or production-deployed.

## Goal and bounded delivery

W2-A demonstrated one deterministic multi-page website with human-authored sample content. W2-B must prove the compiler can handle **two independently structured, original, fictional briefs** and can choose different **pre-reviewed static design systems** without accepting arbitrary CSS, JavaScript, URLs, uploaded assets, shell commands, or unsafe filesystem destinations.

The two intentionally distinct sample journeys are:

| Sample | Site profile | Routes | Reviewed visual direction |
|---|---|---|---|
| Morrowline Studio | creative-studio | `/`, `/work`, `/contact` | `mineral-editorial`: paper, dark pine, terracotta geometry |
| Arcloom Objects | product | `/`, `/studies`, `/approach`, `/contact` | `ink-monograph`: graphite, chalk, vermilion |

Both sample businesses, projects, email addresses and case studies are **fictional demonstrations**. No real brands, photographs, customer reviews, product claims, licensed assets or client identities have been imported.

## Technical ownership

- `w2b_curated_compiler.mjs` calls the original W2-A `compileW2Site` for W1 structural/schema/content/link policies. It then selects one of two **fixed, human-authored** CSS extensions, and attaches a bounded `data-art-direction` selector to generated HTML. The original generated HTML and JavaScript are otherwise preserved. Original W2-A source is unchanged.
- `w2b_arcloom_brief.json` stores a four-page product-concept brief, clearly described as fictional. The inherited `w2_demo_brief.json` supplies the independent three-page studio brief.
- `w2b_generate_previews.mjs` supports only `--site=morrowline` or `--site=arcloom`; never accepts arbitrary paths, URL input, templates, or shell operations. Generated outputs are separate from the existing W2-A output, under `target/e6-w2b-{site}`. Refuses overwrite.
- Every generated page remains semantic, accessible without WebGL, linked through local routes and under noindex/no-follow plus a local-resource CSP. Project filters are keyboard-operable; contact is a user-controlled `mailto` draft, **not a backend message sender**.
- The new manifest tracks the selected preset, route set and independently recomputed SHA-256 values. Every release, deployment, physical GPU, real-model authorship and independent human-review flag remains false.
- `test_w2b_curated_compiler.mjs` includes positive two-brief, inherited W2-A compatibility, stable bytes/hashes, invalid/unknown CSS selector, prototype/getter poisoning, cross-route and HTML-injection negatives, no fake asset/3D approval, and release-NO_GO cases.
- `w2b_browser_proof.mjs` runs genuine local generated pages in headless Chromium at desktop, mobile and reduced-motion profiles, separately for both sites. It verifies per-route navigation, current-page attributes, keyboard filters, contact validity, no horizontal overflow, no unwanted external requests and no JS exceptions.
- New read-only exact-head GitHub workflow: `.github/workflows/runtime-e6-w2b-curated-multibrief.yml`. **Do not claim it passes until its run is independently inspected.**

## Native WSL2 local preview

From an isolated verified W2-B clone, using Node 22 and Python 3:

```bash
cd tools/enterprise/e6_studio/next-studio
node --test test_experience_contract.mjs test_w2_compiler.mjs test_w2b_curated_compiler.mjs
node w2b_generate_previews.mjs --site=morrowline
node w2b_generate_previews.mjs --site=arcloom

# One local site at a time, port 8765:
python3 -m http.server 8765 --bind 127.0.0.1 --directory target/e6-w2b-arcloom
# Open http://localhost:8765/ in Windows Chrome
# Ctrl+C to stop, then substitute target/e6-w2b-morrowline to inspect the other design.
```

Generated directories are write-once and deliberately **not** automatically deleted or overwritten. A new clean clone avoids modifying a previous preview.

## Important limits and next work

W2-B proves a **curated deterministic website compiler** can render different briefs and visual directions, not that an LLM authored new code or that the two designs met a professional premium design review. The art is CSS-generated: **no real photography, PBR assets, Blender, generated video, GPU rendering or W3 licensing work** is included.

Remaining: W2-C safe independently supplied briefs and richer content/section capabilities, W2 multi-brief independent visual and screen-reader evaluation, W3 original provenance-tracked asset pipeline, W4 actual scene orchestration, W5 independent held-out real-model authorship and W6 cross-device security/a11y/hardware acceptance.

Other open safety obligations remain separate: E3/E4/E5 research, E6 Task 7/8 hardware/human gates, Runtime issue #23, Platform issue #251 and Stage 45. **No protected-main merge, no public preview deployment, no owner PC agent activation and no production permission.**

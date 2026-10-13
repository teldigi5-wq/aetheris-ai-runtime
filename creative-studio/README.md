# Aetheris Creative Studio — research and read-only skill templates

This PR proposes **five review-only Agent Skills** and **12 quarantined repository references** for UI/UX, motion graphics, interactive 3D, accessibility and media workflows.

## Five proposed skills
- `ui-ux-audit` — user journey, typography, layout, design-system and responsive critique.
- `motion-experience-review` — transition choreography, scroll and micro-motion plans, performance and reduced-motion alternatives.
- `3d-web-experience-review` — physically plausible scene design, realistic Earth, Three.js/R3F budgets, accessible non-WebGL fallback.
- `frontend-quality-review` — UI accessibility and responsive/performance audit recommendations.
- `creative-production-review` — shot/asset/export and provenance planning; image/video remains unwired.

The skills use the existing Aetheris `skills/catalog.json` schema and deterministic allowlist validator. Proposed `design.*.read`, `quality.*.read` and `media.asset-metadata.read` tool labels are **future capabilities**, not installed or callable tools. Existing owner and MCP policy remains authoritative. Templates have no external hosts, write permissions, subprocesses, paid API keys or new execution authorities.

## 12 references, zero installations
Quarantined metadata for **Three.js, React Three Fiber, Motion, GSAP, Storybook, Tailwind CSS, axe-core, Playwright, Blender, Remotion, Figma plugin examples and ffmpeg.wasm** appears in `sources.json`.

- Every source is uninstalled, untrusted, not reviewed for license compatibility, and has no verified commit SHA or network/credential grant.
- GitHub's reported license label is **only metadata**, not permission to bundle code or weights.
- GSAP/Remotion/Blender, npm modules, native FFmpeg binaries, voices, textures, fonts, templates and AI checkpoints can require specific license/usage review.
- An engine being free to download does not establish a free, zero-compute-cost, commercially redistributable product.

## Intended future workflow

Design brief → UX audit → motion/3D plan → accessibility/performance acceptance checklist → owner-approved implementation in an isolated branch → tested build. **Only the read-only planning stages are defined here**. There is no enabled Figma connector, no installed library, no website mutation, no Blender or GPU invocation, no media output, and no physical benchmark.

## Offline verification

```text
python scripts/validate_agent_skills.py
python scripts/validate_creative_studio.py
python -m unittest discover -s tests -p 'test_creative_studio.py' -v
```

Keep this PR stacked on the still-unmerged global extension catalog PR #18. No merge to protected `main` until all checks pass and the maintainer reviews licensing, tool scopes and inter-repository certification. Hardware status: `BLOCKED_PENDING_HARDWARE`.

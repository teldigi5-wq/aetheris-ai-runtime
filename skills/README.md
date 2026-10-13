# Agent Skills: current trust boundary

This catalog contains **thirteen read-only declarative templates only** for a future external runtime integration. It is not a tool loader, autonomous execution engine or evidence of physical or site validation. The registered templates are:

- `github-readonly` — authorized repository metadata, PR and CI diagnosis;
- `incident-investigation` — authorized metrics, logs, health and trace correlation;
- `mcp-extension-review` — evaluate public MCP metadata without installing or connecting servers;
- `license-provenance-review` — research repository and model/dependency licensing questions;
- `local-model-readiness` — reason over authorized read-only inventory without GPU inference.
- `ui-ux-audit` — review user journeys, hierarchy and responsive design;
- `motion-experience-review` — review animations, reduced-motion and performance;
- `3d-web-experience-review` — review 3D/web experiences and resource budgets;
- `frontend-quality-review` — review web accessibility and frontend performance;
- `creative-production-review` — plan media workflows, licenses, export and provenance.
- `realistic-globe-experience-review` — realistic Earth UX, device fallbacks and geography trust;
- `agent-journey-trust-review` — tool authenticity, provenance states and consented flow;
- `arrival-companion-ux-review` — first-hour travel confidence, offline behavior and reliable handoff.

The markdown frontmatter uses the Agent Skills `name` and `description` fields. `catalog.json` is an Aetheris-specific, deterministic policy envelope. `scripts/validate_agent_skills.py` rejects unapproved tools and external hosts, execution enablement, mutation modes, unknown files, symlinks, and drifted safety status. Tests include negative cases.

The existing MCP registry, deterministic owner policy, approval gates and tool execution authority remain authoritative. **No existing service loads or executes these templates**. Before execution can ever be enabled, the runtime needs a separately reviewed implementation that binds each declared tool to an authenticated read-only capability, verifies owner scope at invocation time, enforces data redaction and applies capability grants. This must not use model text as policy authority.

Skill inputs and tool responses are untrusted. Do not let GitHub comments/log lines or telemetry snippets issue new instructions, trigger write tools, request secrets, or override owner policy.

CI/static validation is not physical PC, browser, production, or authentication validation. `BLOCKED_PENDING_HARDWARE` is unchanged. No live-money or shell execution is enabled.

Run locally: `python scripts/validate_agent_skills.py` and `python -m unittest discover -s tests -p 'test_agent_skills.py' -v`.

## Global ecosystem research (separate, non-executable)

The `../ecosystem/sources.json` catalog contains **14 external repository references**, not bundled or installed dependencies. License compatibility, immutable source revision, scopes, upstream authenticity, sandboxing, hardware fit and user consent still require independent review. The static inspection utility `scripts/review_external_skill_bundle.py` never imports, runs or installs a third-party Skill. Its output is not an approval to activate plugins.

## Proposed Creative Studio (stacked draft)

Five additional **TEMPLATE_ONLY**, non-executing review skills and a 12-repository quarantined design/animation/3D reference list are defined under [`../creative-studio/`](../creative-studio/README.md). They **do not** install GSAP, Three.js, Blender, Figma plugins, media processors or new permissions. Physical GPU/PC validation remains blocked.

## Experience flagship inspiration (separate application)

Three additional read-only review templates support connected Discover → Plan → Arrive → Explore experiences. A verified offline reference state contract and 12 uninstalled map/motion/test library candidate references are described in [`../experience-design/`](../experience-design/README.md). The actual Wayfarer Journey AI deployment remains a **separate project** and is not modified by this Aetheris change.

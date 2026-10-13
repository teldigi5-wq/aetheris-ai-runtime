# External design skills — installation handoff (October 13, 2026)

## Current state

This is a **source-verified, offline install preparation**, not an installed or enabled Aetheris capability. The five entries in [design_skills_sources.json](../../ecosystem/design_skills_sources.json) record reviewed upstream *Git commit snapshots*, NOT verified npm or npx package digests. All source text and package installs remain **untrusted until separately reviewed**. No external code, images, executables, binaries, models, remote APIs, or browser sessions were installed or run by this change.

Aetheris runtime, platform policy, credential grants, emergency stop and physical owner-PC boundaries take precedence over anything an external skill says. Never allow a third-party SKILL.md to grant itself tool access, invoke payment/trading operations, or bypass policy. Do not claim a successful installation based on this PR, hosted CI, or repository metadata.

## Five upstream sources

| Candidate | Pinned upstream revision | Purpose |
| --- | --- | --- |
| [Taste Skill](https://github.com/tasteskill/tasteskill) | `37c8c376b92e` | Premium layout, typography and purposeful motion; use only the `design-taste-frontend` skill |
| [Impeccable](https://github.com/pbakaus/impeccable) | `245ad82def45` | Design critique, accessibility and interface polish; may download a standalone engine |
| [Playwright CLI](https://github.com/microsoft/playwright-cli) | `b85c7a736bb4` | Browser-driven functional and screenshot QA; needs Node.js 18+ and a browser |
| [Awesome Design Skills](https://github.com/bergside/awesome-design-skills) | `f631a09b4fcc` | Registry of curated design variants; select individual files, never bulk-install 67 |
| [img2threejs](https://github.com/img2threejs/img2threejs) | `d508b596cb22` | Image-reference to **procedural Three.js code**, not true photogrammetry; Python 3.10+ and a Three.js project / browser-preview host |

Repository SPDX at discovery: MIT, Apache-2.0, Apache-2.0, MIT, Apache-2.0, respectively. This is source metadata, **not a legal review** of dependencies/assets.

## Explicit owner-PC installation steps (not executed)

After getting the workstation back, review each **exact commit** from the JSON lock and confirm the current installer package, its distribution identity, transitive dependencies, any postinstall hooks, script privileges, and licensing **before running** these commands. The commands below follow upstream instructions, but may resolve newer revisions than the pinned research snapshot; never describe them as SHA-locked installs.

Run in a **throwaway project or isolated workspace first**, not the privileged Aetheris production runtime. Avoid unnecessary global installs. Authenticate no external accounts. When using a coding agent, scope browser access to trusted test pages and avoid storing cookies/secrets.

**Taste Skill** (project-scoped Codex/Cursor/Claude skill):
```sh
npx skills add https://github.com/tasteskill/tasteskill --skill "design-taste-frontend"
```

**Impeccable** (interactive, project-scoped selection):
```sh
npx impeccable install
```
Review the installed hook manifest/engine and its first-run binary download before invoking `/impeccable init`.

**Microsoft Playwright CLI** (CLI requires Node.js 18+):
```sh
npm install -g @playwright/cli@latest
playwright-cli --help
playwright-cli install --skills
```
The browser may need additional installation. Do not attach real accounts or production profiles during smoke tests; provide screenshots from an isolated test project.

**Awesome Design Skills** (choose few relevant styles; no wholesale download):
```sh
npx typeui.sh pull professional --dry-run
npx typeui.sh pull premium --dry-run
npx typeui.sh pull glassmorphism --dry-run
npx typeui.sh pull professional
npx typeui.sh pull premium
npx typeui.sh pull glassmorphism
```
Follow registry CLI provider/scope prompts. The resulting style references are alternatives, not directions to combine all aesthetics at once.

**img2threejs** (review installer on first use):
```sh
npx --yes img2threejs@latest install --dry-run
npx --yes img2threejs@latest install --ref v2.0.0
npx --yes img2threejs@latest doctor
```
The base skill and domain plugins are distinct; no image-to-3D reconstruction is proven on the owner's hardware. For accuracy, require reference-vs-browser-render comparisons and clearly label unknown reverse-side geometry.

## What must happen before Aetheris activation

1. Owner acknowledges the exact packages and destinations; verify source-to-package hashes, signatures when available, license terms, and third-party content.
2. Install into an isolated **agent development workspace** with narrowly scoped read/write permissions; scan local bundles with `scripts/review_external_skill_bundle.py` where its document-only rules apply. A plugin with executables requires a different, deeper inspection.
3. Test UI skills on a benign fixture. For Playwright, prove selector/screenshot operation without privileged credentials. For img2threejs, prove generated TypeScript in the actual Three.js project.
4. Require deterministic policy and capability-scoping checks **outside the model**. Do not connect these downloads to owner-computer controls, production browsing, trading or cloud credentials by default.
5. Update source lock, QA evidence and owner-PC truth status via separate reviewed PRs. Until then every item remains **NOT INSTALLED / NOT ENABLED**.

Offline contract check (safe on hosted CI):
```sh
python scripts/validate_design_skills_sources.py
python -m unittest tests.test_design_skills_sources -v
```

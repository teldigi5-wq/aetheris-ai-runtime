# Aetheris E3.9G — governed design and defensive security skills

**Engineering slice, draft. No merge, no production/owner-PC actions, Stage45 NO_GO.**
Stacked on E3.9F draft PR #153, head `d0f298cba1ecc77959580326e3d22bc0d06e645f`.
The baseline model trial remains Granite4 3B **2/8**, Hermes3 3B **0/8** on eight **public synthetic** focus cases. E3.9F runtime-seeded browser-only controls proved 32/32 expected outcomes but **0 model calls**. Adding these skills does not change either model score.

## What has been implemented
1. **Six unmodified skills, pinned to exact upstream Git revision** [emilkowalski/skills](https://github.com/emilkowalski/skills/commit/2d228630ba1446365f614a108cb8252f1feda202) (11 October 2026): `emil-design-eng`, `animate`, `animation-performance`, `review-animations`, `break-ui`, `improve-animations`. Source `SKILL.md` blobs are checked byte-for-byte against their upstream Git blob hashes every validation; MIT license and 2026 Emil Kowalski attribution are retained unmodified.
2. **Eight local Aetheris advisory skills**: `aetheris-web-experience`, `aetheris-web-accessibility`, `aetheris-web-performance`, `aetheris-security-audit`, `aetheris-threat-model`, `aetheris-supply-chain-audit`, `aetheris-code-review`, `aetheris-skills-safety`.
3. A bounded read-only reference registry with strict skill-ID/frontmatter checks, duplicate metadata refusals, path/symlink protection, upstream blob SHA1 commitment against exact upstream commit, full SHA256 digests at load, explicit MIT license, no hidden/unlisted `SKILL.md` payloads, and mandatory **empty tool capability list** plus `autoActivate=false`.
4. No `npx skills add`, network fetch, bundled external scripts, shell execution, agent tool binding, owner credentials, repository write privilege, deployment rights, or paid API is introduced.
5. Runnable `tools/enterprise/skills_registry.py` supports `validate`, `list`, `match <query>`, `show <name>`. **show is clearly labeled untrusted reference**, and no operations interpret skill commands as operator instructions.
6. Windows+Ubuntu adversarial unit tests, retained Python model policy tests, Java service regression, original website real Chromium QA, and no-Stage45-positive-authorization guard added to dedicated CI.

### Important security boundary
Third-party skill content is lower-trust than user/developer policy and is **not** a system instruction. Some vendor skill documents include imperative language or demonstrations of code that would ordinarily affect a UI; **none is permitted to call Aetheris tools or decide permissions**. Explicit approval and an independent capability/patch gate are prerequisites to any future limited skills activation. Downloaded text is pinned but popularity, MIT licensing and matching blobs are not malware-safety certificates. This stage is a **read-only skill discovery and audit resource**, not new working employee agents. No model output can update skills registry or CI policy.

### Operator use (CI/repo only)
```bash
python tools/enterprise/skills_registry.py validate
python tools/enterprise/skills_registry.py match animation
python tools/enterprise/skills_registry.py match security
python tools/enterprise/skills_registry.py show aetheris-web-experience
```
Aetheris runtime does **not yet auto-dispatch any of the 14 skills** to an AI model or act on their output. A later scoped interface should bind only advisory text, origin/revision hashes, max input/output budgets, and signed audit receipts to a selected agent identity; keep approval and worker security independent.

## Delivery phases after this slice (not completed)
- **E3.9F**: Genuine inference on the runtime-seeded browser benchmark, then independently curated multi-domain blind cases; multiple-run estimates and attacker/evaluator robustness.
- **E3.9G-2**: Skills request router and controlled opt-in advisory integration to named configured agent identities; no tool execution by skill content; opt-in tests and provenance receipts.
- **E3.9G-3**: End-to-end website tasks, design reviews, motion and performance budgets, OWASP-style findings with real tests, and robust adversarial prompt-injection/refusal benchmarks.
- **E4**: Bounded multi-model router and quality-driven decisions; no unsupported productivity score claims.
- **E5**: Multi-agent bounded task orchestration, role delegation and reproducible work ledgers.
- **E6**: Governance, review and deterministic approval chains, protected assets and audit.
- **E7**: Portfolio-grade integrated real workflows for web/app/security with real browser+CI evidence.
- **E8**: Load, chaos, reliability, resource and model comparison certification, repeatable evaluation and trust boundaries.
- **E9**: Operational hardening, maintenance and staged readiness; Stage45 separately blocks owner or production actions.

**Counting carefully:** At least **seven high-level milestone tracks** remain including unfinished E3.9F and E4–E9, plus **two explicit E3.9G follow-up engineering slices**, and **Stage45 separate certification**. There is not an independently verified finite number of small commits/days remaining; numerous staged PRs remain draft/unmerged.

No paid API, owner workstation, protected main, owner-level permission, live trading or production deployment is used.

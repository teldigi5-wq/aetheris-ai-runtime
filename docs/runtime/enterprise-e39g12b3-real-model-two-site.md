# E3.9G-12B3 — Real local coding-model proposals across two distinct disposable websites

DRAFT stacked on [#172](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/172), exact base `c3d6f6b18e9f4df27e29db8743a32541edb4237e`. Tracking [issue #169](https://github.com/teldigi5-wq/aetheris-ai-runtime/issues/169). **Stage45 NO_GO**.

## Real trust boundary

GitHub Actions uses separate ephemeral runner jobs:
1. **Producer (repo + evaluator only)**: existing E3.9G-12B2 packer exports a deterministic ZIP containing only the **broken** StudyNest and CityLine HTML and public defect names. The reference-controlled source is not exported.
2. **No-checkout model job**: downloads **only** the 3-member ZIP. There is no `actions/checkout`, no repository, test, golden reference, typed policy module, specialist registry, credential or owner account accessible through the job workspace. An ephemeral loopback-only `ollama/ollama:0.12.10` container, 2 CPU/5 GiB, Linux capabilities dropped and read-only root, pulls exactly **`qwen2.5-coder:1.5b`** (license check: Apache 2.0) and makes **one real `/api/generate` request for each of four site/defect combinations**. It sees complete broken-source HTML and public defect descriptions but no correct replacement. It emits a separate untrusted JSON proposal artifact with raw response, SHA256, token counts, wall time and self-reported model image/digest. **No model executes HTML, edits files, has tools or modifies sites.** Model tags/digests are locally verified as self-reported identity; not signed upstream weights.
3. **Independent evaluator job (checkout, but no Ollama)**: downloads untrusted proposal artifact, re-seeds original broken and developer reference controls, reruns actual Playwright Chromium on the 8 original (site × broken/reference × viewport) cases, then passes each of the four model raw suggestions through the **unchanged E3.9G-12B2 typed source policy** within the cap-dropped `--network none` Python Docker worker. Only approved single-literal CSS/JS replacements are written, to distinct disposable per-fault site copies under `target/e3-factory/e39g12b3/repairs`. Reference variants are never repaired or passed to a model.
4. For policy-accepted changes only, the **separate Playwright verifier** observes behavior on mobile **390×844** and desktop **1280×800** with actual keyboard Tab, overflow, reduced-motion and inert HTML-marker checks. Independent scorer rechecks raw hashes, frozen patch policy, source content SHA, screenshot SHA, browser errors and both viewports. A repair counts only when a **real model-proposed**, **policy-approved**, actually applied patch passes both target browser contracts. Failed or malformed model responses are **negative research results**, not permission to edit and not CI-only errors.
5. Three separate testing jobs: **Windows+Ubuntu adversarial Python** and pinned skill provenance, full Java Maven regression, and no-owner/no-production privileges static CI guard.

## Honest boundaries

Both websites are **public, author-created synthetic examples** (StudyNest course planner, CityLine transit arrival board) and do NOT qualify as independently externally curated, hidden or representative Internet benchmarks. They target two accessibility concerns, responsive overflow and inert unsafe HTML sink (not a real exploit attempt). The model’s browser fix success on these fixtures would be narrowly demonstrated behavior, not generalized web engineering ability. A model-side upload is self-reported evidence verified in an honest CI run, **not cryptographically certified identity or independent attestation of model calls**. GitHub/runner admins still control CI; network access is needed to pull weights. The only network-none environment with ability to mutate code is the limited policy worker.

This milestone introduces neither authenticated owner/task authorization, external tools, website production deployment, subscriptions, live trading nor Stage45 release. Protected `main` never merged or modified.

**Exact-head CI evidence must finish before reporting real model call counts or verified repairs.** Preserve failures, abstentions and positive/negative measurements; never loosen policy simply to improve score.

## Next after this milestone

Issue #169 still requires independently curated, truly held-out multiple projects, broader typography/visual quality/performance and strictly authorized defensive AppSec faults; repeat comparable licensed models and confirm no skill or prompt benefit without sufficient power; improve model semantics without oracle leakage. Trusted principal/session/task lease and production gate remain independently blocked.

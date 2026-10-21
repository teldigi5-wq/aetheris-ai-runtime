# E3.9G-12B2 — evaluator-only references, broken-only model artifact and typed edit gate

**Draft and unmerged.** Parent [PR #171](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/171), verified `61374ccb729018255c01948ba25f1e00e1a0bd2d`. Tracks [issue #169](https://github.com/teldigi5-wq/aetheris-ai-runtime/issues/169). Protected `main` unchanged and Stage45 **NO_GO**.

## Trust boundaries actually established

This milestone establishes **separate disposable CI job workspaces**, not a secure enclave, a secret held-out challenge, or a machine authentication scheme.

The authoritative evaluator job has the complete repository, two synthetic site fixtures, baseline browser results, and developer-authored reference controls. It invokes the existing network-none Docker seeder then `e39g12b2_proposer_bundle.py pack`. The packer checks both broken and reference site SHA256 hashes and writes one deterministic model-facing ZIP with a **strictly enumerated three-file allowlist**:

- `sites/studynest/index.html` — the defective student planner only.
- `sites/cityline/index.html` — the defective transit information site only.
- `proposer-manifest.json` — public observed fault descriptions, broken-site SHA256s, and explicit `referenceVariantsIncluded=false`/`goldenReplacementIncluded=false`.

The exported ZIP does **not** contain reference HTML, gold replacements, repository source code, full evaluation manifest, evaluator controls, privileges or any user/company secrets. ZIP file order and metadata are deterministic; path traversal, symlinks, unexpected extra files, source drift, missing or duplicated archive and size overflow are rejected. Only that ZIP is uploaded as an artifact.

A separate **`no-checkout-model-input-consumer` GitHub Actions job** downloads the ZIP using pinned `actions/download-artifact`. It does **not call `actions/checkout`** and verifies that `.git`, `tools/`, `docs/` and `skills/` are absent. It validates all three ZIP paths, public SHA256 and false approval/model-call flags. Thus a future model process *could* receive only this bundle if it is run in such a no-checkout job. The current PR **does not start Ollama or provide a model process**, and does not prove adversarial confidentiality against a malicious CI action/runner admin or any future step which reintroduces GitHub or network access. Future AI jobs must keep the no-checkout architecture and limit network/credentials.

The repository still contains the developer-authored evaluator reference strings; as a PUBLIC synthetic corpus these are **not truly hidden benchmark answers**. Isolation is practical CI process separation only, not externally supplied blind/held-out tasks. Real independence still requires independently curated nonpublic challenges, strict timing and material/answer pre-registration.

## Independent narrow four-fault repair-scope policy

`e39g12b2_typed_policy.py` is a **pure validation function**, without file-writing, browser access, model calls, subprocess, shell or owner/agent identity. It accepts exactly six keys `site`, `fault`, `operation=replace_one`, `old`, `new` and `sourceSha256`, and validates the original site bytes against a repo-pinned fixture and hash before attempting a single replacement.

| Site | Fault | Allowlisted replacement semantics |
|---|---|---|
| StudyNest | Invisible keyboard skip link | Only `.skip:focus` top position bounded to 0–80px, no other properties or selectors |
| StudyNest | 390px mobile overflow | Only mobile grid `min-width` of 0 or a bounded non-negative px width no larger than 390px |
| CityLine | Reduced-motion ticker | The exact `prefers-reduced-motion:reduce` ticker override must stop animation |
| CityLine | Search input interpreted as HTML | The exact station output must use literal `textContent` or `innerText` instead of `innerHTML` |

The policy refuses wrong sites, wrong owner-shaped fields, duplicate JSON keys, other operations, CSS injections, animation still running, script evaluation/HTML sink retention, source mutation and unbounded edits. It allows a limited range of semantically equivalent candidates (not just the single golden patch), **but future model successes additionally require actual independent browser evaluation**, so policy acceptance alone never counts as a repair.

Dry-run positive-policy samples are **developer-authored test data, not AI-generated edits**. This slice does **not** apply a model's candidate to any site, execute untrusted code, or test model-generated after-browser repair.

## Required CI evidence

- Cross-platform **Windows and Ubuntu** adversarial 4-fault scope, source integrity and ZIP exclusion tests; existing 14 skill-source provenance and specialist allowlists retained.
- Real **Playwright Chromium** 8 broken/reference observations across two sites and mobile+desktop, unchanged from B1, to prove evaluator consistency.
- New distinct **no-checkout consumer job** with archive-only download; no reference material or policy code.
- Full Java 21 orchestrator regression (historical B1 passed 451 tests), plus static no-owner/no-deployment policy gates.
- Exact-head workflow must be green before making a claim of CI isolation. Artifacts may have 14-day retention.

## Remaining work (issue #169 remains OPEN)

**12B3:** define a reviewed, exact-token, network-none model-patch application path that uses the new typed gate, passes **broken-only** model context and zero golden answers, freezes actual model parameters/reproducibility, and independently verifies accepted model changes in both real Chromium viewports. Model outputs/attempts, abstentions, refusals and failures must be counted.

**12B4:** add externally curated and genuinely independently sealed multi-site accessibility/responsive/security challenges with diverse, nonliteral source edits and repeated comparable controlled models. Defensively test any new JS patches for authorization, supply-chain and sandbox bypasses.

**Other blockers:** actual authenticated owner identity and scoped task authorization; no external agent, production website edit, protected-main merge, trading, payment or Stage45 release.

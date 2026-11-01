# E6 W5-B — Held-out evaluation of actual local model-proposed websites

**1 November 2026 · DRAFT RESEARCH ONLY · Stage 45 NO_GO**

## Recovery / strict branch topology

- Canonical repository: `teldigi5-wq/aetheris-ai-runtime`. This child branch is stacked on W5-A **draft PR #266**, immutable verified parent `8028090a8d75ed363eff5f68418719133b972be1`.
- W1–W4-D and W5-A remain independently reviewable and unmerged, with their original isolated preview targets untouched. Runtime protected main `65a6262717adcd52ac8d8a16ed6f223e299fd74d`; Platform protected main `57ad3e0e602efa6feb275328254151b959a3499b`.
- W5-A implemented a guarded localhost Ollama path and a restrictive model-proposed design/copy compiler. **W5-A CI used mock provider outputs; it proved no actual model authorship.** This distinction remains central to W5-B.

## Why W5-B exists

Proving a static two-page compiler accepts JSON is not sufficient to demonstrate that a real model can independently create differentiated, safe, valuable websites from unseen briefs. W5-B creates a **reproducible held-out trial** that can actually query an already-installed local Ollama model, validate each candidate and preserve evidence for human review, without granting the model code execution or publication authority.

## New implementation

- `w5b_heldout_briefs.json`: three *new* fictional, independent evaluation subjects — Quiet Heat Ceramic Library, Afterlight Mapping Atelier, and Paper Migration Journal. These are human-written evaluation prompts, **not model answers**, training examples or real clients. Distinct product, agency and portfolio profiles; no personal data or real-world claims.
- `w5b_local_heldout_runner.mjs`: requires `E6_W5B_ALLOW_LOCAL_INFERENCE=YES`, a syntactically validated installed `E6_W5B_MODEL` and **no CLI arguments or arbitrary paths**. Checks fixed `127.0.0.1:11434/api/version` and `/api/tags` for the exact selected installed model and reported digest before issuing one `/api/generate` request per held-out brief using the already-audited W5-A loopback adapter. Bounded JSON and local response sizes, no redirects, no other network targets, no autonomous correction loop, no credentials/secret capture, no model tools/shell access and no paid service dependency. Ollama service metadata is *self-reported*, **not independent attestation of model weights or execution**.
- `w5b_evaluation_contract.mjs`: reuses W5-A strict schema, explicit safe output paths, static CSP compiler and 4.5:1 palette contrast. Records source/candidate/output SHA256, section/route counts and rejection codes. Heuristically flags suspicious lexical repetition, identical art tokens and achievement language. **These flags cannot certify originality, factuality, design merit or copyright compliance.** Every accepted candidate remains `REQUIRES_HUMAN_REVIEW`; warnings require attention rather than automatic repair or GO.
- Failed provider, schema and content results are recorded as **rejected** without converting unsafe model text into HTML. Each accepted candidate may produce a separate isolated write-once local two-page site; the overall run creates `target/e6-w5b-real-heldout-evaluation/summary.json`. The summary holds only hashes, safe rejection codes and provider-reported model metadata, not prompts, raw model output, tokens or personal data. Repeated writes refuse to overwrite; if a case fails, the CLI returns nonzero while preserving the limited local research report.
- `w5b_simulated_provider.mjs` and `test_w5b_heldout_evaluation.mjs` use manually authored **FAKE** Ollama responses to test API metadata spoofing, installed model validation, exact three-brief completeness, fabricated claims, schema injection, independent site variation, duplicate/copy warnings and safe rejection. A fake transport is *not* a model and its success is *not* inference evidence.
- `w5b_simulated_browser_proof.mjs` exercises three separately compiled human-authored test-double sites on three no-JS Chromium viewport sizes (320/390/1440), with actual root-relative CSS and routes. It does not call a model or prove brand uniqueness or art direction.
- `.github/workflows/runtime-e6-w5b-heldout-eval.yml`: read-only exact-head CI verifies W1/W2/W5-A regressions, W5-B contract/negative tests, Next.js static build and nine simulated browser conditions. It deliberately does **not install Ollama or any model** and cannot award real-model acceptance. Workflow artifacts are temporary and explicitly labeled fake.

## Real-model owner-PC execution (NOT run by CI)

The owner must already have a suitable locally installed Ollama model and sufficient RAM/CPU/GPU. The following is a reproducible opt-in protocol, not evidence of completed inference. Do not download any model without reviewing its license, size and hardware constraints. These commands make no changes to `main`, other worktrees or production.

Use a **new detached W5-B worktree**. Do not delete previous previews or reset user changes to resolve a directory error:

```bash
cd ~/aetheris-ai-runtime
git fetch origin
git worktree add --detach ~/aetheris-runtime-w5b-review-20261101 \
  origin/feat/e6-w5b-real-model-heldout-acceptance-20261101
cd ~/aetheris-runtime-w5b-review-20261101/tools/enterprise/e6_studio/next-studio
node --test test_w5b_heldout_evaluation.mjs
ollama list
# ONLY after selecting an actually installed, licensed, hardware-compatible model:
E6_W5B_ALLOW_LOCAL_INFERENCE=YES E6_W5B_MODEL='YOUR_INSTALLED_MODEL_TAG' \
  node w5b_local_heldout_runner.mjs
```

If Ollama is absent or its local service is stopped, the runner refuses the operation; no remote API is substituted. The only output is in a new local `target/e6-w5b-real-heldout-evaluation` folder. Inspect its `summary.json`, all accepted models' `w5b-candidate-evaluation.json`, generated pages, and their browser screenshots. Results may include rejected candidates and should not be treated as failure of the source-bound safety boundary. **Do not publicly share transcripts or model-generated content without rights and source review.**

## Honest acceptance and next stages

**At PR creation, no actual model-run evidence exists.** A successful GitHub check means only that the runner, contract/negative tests and simulated browser integration are functional. A real-model trial requires an explicit owner-local run plus independent check of installed runtime, model digest, generated proposals, output hashes and results. Even such a trial cannot certify originality, quality, full accessibility or arbitrary AI-authored React/HTML/CSS/JS code.

W5-C must implement **controlled model-authored component/code proposals** with a physically isolated sandbox, deterministic policy review, network restrictions, proven compilation, browser/security regression and independent held-out acceptance before any claim of advanced autonomous website generation. W6 covers expert creative, owner-device, accessibility, performance and security gate approval. Runtime #23 owner/SSE scope, Platform #251 backend authorization, E6 Task7/8 and Stage 45 nine production attestations remain separate **NO_GO**. **No merge, deploy, paid service or privilege escalation.**

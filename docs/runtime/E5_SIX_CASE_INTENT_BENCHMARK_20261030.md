# E5 — Six-case local-model design-intent benchmark (30 October 2026)

**Enterprise AI roadmap E3.9/E4/E5, NOT S0 security. Unmerged draft. Stage45 NO_GO.**

## Verified parent and aim

- Stacked on verified [Runtime PR #230](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/230) @ `600514ed11409331423b0b493562988b5a265c94`, [exact-head Actions #41025044753](https://github.com/teldigi5-wq/aetheris-ai-runtime/actions/runs/41025044753) **81/81 Python tests and 21/21 genuine Chromium contexts PASS**. Parent remained draft and did not run a genuine on-device model.
- Parent permits a user to provide three bounded abstract design preferences under a **fourth** opt-in and computes a transparent, human-authored 0–3 preference-alignment score. Two mock tests passing 3/3 could not establish actual model reliability or consistent behavior over multiple design requests.
- This stage adds a **fixed, six-case, local-only research benchmark**, scoring valid wrong choices and refused cases in the denominator, and explicitly comparing the model against the public deterministic rule-based ceiling. It does not claim hidden held-out performance, aesthetic merit or source-generated creative ability.

## The six public synthetic scenarios

The new `tools/enterprise/e3_factory/e5_six_case_intent_benchmark.py` tests **six fixed cases**, spanning immersive/informational/balanced goals, light/dark contrast and editorial/contemporary typography. Every case uses the *same safe synthetic research brief and copy*, previously checked via the existing E5 creative gate. The model receives **only** the three categorical design-intent values and a finite response-token schema via the inherited, host-locked local-only Ollama adapter. No brand, customer files, credentials, source, original brief/copy, arbitrary text instructions, browser history or private user data are sent.

One fresh `LocalLayoutTrial` object is created **per case**, with maximum one Ollama tag probe + one generate attempt, no retry or cloud fallback, so at most **six proposal attempts** across twelve bounded transport calls. Valid model-returned layout/contrast/type tokens feed only the existing vetted human-authored HTML/CSS/WebGL compiler, not arbitrary model-executed CSS or JavaScript.

Each case reports either a syntactically valid layout with a transparent **0–3 matching score**, or a rejected/unavailable case with **0 points**. This prevents cherry-picking only successful outputs. The report has exactly six submitted cases, valid/refused counts, total possible points **18**, and a **public deterministic static baseline of 18/18** because the authored lookup rule directly implements the same scoring rubric. A model can equal, **not exceed**, that baseline on this toy task. More points do **not** prove independent aesthetic quality, a novel design, contextual understanding, independent held-out generalization, on-device GPU throughput or cryptographic model identity.

## Explicit local use — owner-controlled computer only

The default command does **no network calls and does not access user files**:

```bash
python3 tools/enterprise/e3_factory/e5_six_case_intent_benchmark.py --model qwen2.5-coder:1.5b
```

A deliberate opt-in run requires ALL FIVE separate environment flags:

```bash
AETHERIS_E36_LOCAL_INFERENCE_OPT_IN=yes \
AETHERIS_E4_EXPLICIT_LOCAL_SESSION_OPT_IN=yes \
AETHERIS_E5_LOCAL_LAYOUT_TRIAL_OPT_IN=yes \
AETHERIS_E5_ABSTRACT_DESIGN_INTENT_OPT_IN=yes \
AETHERIS_E5_SIX_CASE_INTENT_BENCHMARK_OPT_IN=yes \
python3 tools/enterprise/e3_factory/e5_six_case_intent_benchmark.py \
  --model qwen2.5-coder:1.5b --run-local
```

High-resource candidates additionally require existing `AETHERIS_E38_HIGH_RESOURCE_MODEL_OPT_IN=yes`. Only a **previously installed, allowlisted localhost Ollama model** is eligible. This does not install Ollama, download any model, procure a cloud API, run on the user's PC remotely, or grant permission to deploy. It does not open a browser, persist source code from the model, make a customer website, or test all agent/desktop workflows.

The sole output is a **private, 0600, exclusive-create** `target/e5/six-case-intent-benchmark.zip`, refusing any pre-existing file or symlink. Its `evaluation.json` records all successes and refusals. Accepted cases contain separately validated `previews/case-XX.zip` plus immutable JSON `receipts/case-XX.json` with SHA-256 hashes, while rejected cases have **no** preview. ZIPs remain only offline research artifacts. Treat any local result as potentially sensitive; do not commit it. The source and model identity are not cryptographically attested by this harness. The ZIP is reproducible when the simulated/model outputs are identical, not necessarily across nondeterministic genuine inference.

## CI acceptance and limitations

- New `test_e5_six_case_intent_benchmark.py` covers plan-only/no network, six unique/coverage-complete public intents, exactly five opt-ins, heavy-model denial, full six-case reproducibility, valid-but-wrong outcomes, malformed model outputs as refused, all-provider-offline denominator `0/18`, no secrets in failure ZIP, and no overwrite/0600 evidence.
- New `runtime-e5-six-case-intent-benchmark.yml` re-runs **all previously validated E4/E5 Python suites**, injects intentionally **MOCKED** local Ollama outputs across all six cases, verifies **5 valid / 1 rejected and 12/18 rule-alignment**, checks the static baseline is 18/18, extracts a known accepted preview and executes **actual Chromium desktop/mobile/reduced-motion** review in addition to all existing E5 browser regression cases.
- CI does **not** install/run a real local LLM or claim measured true model latency, accuracy, originality, creative merit or customer approval. Any true local benchmark remains **BLOCKED_PENDING_OWNER_HARDWARE** until run with opt-ins in the owner environment and independently reviewed.
- A static lookup table attaining 18/18 means a true LLM is **not necessary** to solve this bounded task. The test is a reliability/integration diagnostic, not evidence that Aetheris has reached autonomous AI website design.

## What remains

Enterprise E3.9 real model code-repair on held-out tasks, E4 real installed-model routing/privacy/performance, E5 original model-generated web code + independent aesthetic/WCAG/performance QA, E6 Cinematic Studio, E7 controlled multi-agent enterprises, E8 Syntra cockpit, E9 integrated system release **remain incomplete**. Security S0 through E6I is separate, and Stage45 all **nine** certifications remain uncertified. No live-money agent, unrestricted shell, production API unlock, customer token, protected-main merge, release pin promotion or deployment.

**Stage45 NO_GO.**

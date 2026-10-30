# Enterprise E5 — Abstract design-intent grounding (30 October 2026)

**Original enterprise capability roadmap, NOT security S0. Stage45 NO_GO. Draft only; no main merge, no deployed site.**

## Parent and problem

[Runtime Enterprise PR #229](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/229) @ `fe0deeacfedf387403a0ce7865d4ccaf2ca55c8c`, [CI #41023885398](https://github.com/teldigi5-wq/aetheris-ai-runtime/actions/runs/41023885398) **72/72 Python tests and 15/15 real Chromium checks SUCCESS**, is the direct stacked ancestor. It allowed an explicitly opted-in local model to choose among 18 previously reviewed layout choices, but the model prompt contained **no design direction**. A random valid option therefore had no demonstrable relationship to an intended experience. No live model was run on owner hardware.

This stage adds a very small, **separate operator-supplied and non-sensitive design-intent contract** that can guide model token selection without revealing any creative brief, brand, customer copy, source code, or free-form prompt injection.

## Versioned input and independently explainable heuristic

`tools/enterprise/e3_factory/e5_layout_design_intent.py` validates a strict version-1 JSON object of at most 350 UTF-8 bytes with **exactly** three enum-only preferences and no extra keys:

```json
{"version":1,"layoutGoal":"informational","contrastPreference":"dark","typographicVoice":"contemporary"}
```

- `layoutGoal`: `immersive`, `informational`, `balanced`.
- `contrastPreference`: `light`, `dark`.
- `typographicVoice`: `editorial`, `contemporary`.

An explicitly human-authored research rubric maps these intents to the previously reviewed layout tokens: immersive → gallery, informational → centered, balanced → split; light → paper, dark → charcoal; editorial → editorial-serif, contemporary → modern-sans. The deterministic score counts **0 to 3 preference matches**, always retaining the actual total and individual match booleans. A score of **3/3 does NOT prove design quality, visual coherence, WCAG compliance, performance, originality, factual correctness, model identity or user approval**. The rubric itself has not been validated against user preferences.

The new `read_intent` validator rejects malformed/duplicate-key/oversized JSON, wrong types, Unicode/multiline or arbitrary string instructions, unrecognized enums, extra fields, URLs and markup. The returned SHA-256 is an **integrity link**, not signed provenance or authenticated owner intent.

## Opt-in model pathway

The existing `e5_local_layout_trial.py` remains fully backward-compatible **without** design intent. Calling it without `--run-local` still makes zero model calls and reads no creative brief or design-intent file.

To opt into additional design intent, the operator must supply **all existing three local inference opt-ins**, plus a **fourth explicit** `AETHERIS_E5_ABSTRACT_DESIGN_INTENT_OPT_IN=yes`, and pass `--run-local --with-intent`. Only then is the fixed local file `target/e5/layout-intent.json` read and its exact enum-only contents sent to the existing host-locked localhost Ollama model. This cannot authorize external/cloud inference, installation of missing models, any website source disclosure, paid inference, customer deployment or additional agents. Existing E4 model allowlisting/claimed local installation and heavy-model separate opt-in remain unchanged.

Example on a reviewed **owner-controlled local PC** only:

```bash
python3 tools/enterprise/e3_factory/e5_local_layout_trial.py --model qwen2.5-coder:1.5b

AETHERIS_E36_LOCAL_INFERENCE_OPT_IN=yes \
AETHERIS_E4_EXPLICIT_LOCAL_SESSION_OPT_IN=yes \
AETHERIS_E5_LOCAL_LAYOUT_TRIAL_OPT_IN=yes \
AETHERIS_E5_ABSTRACT_DESIGN_INTENT_OPT_IN=yes \
python3 tools/enterprise/e3_factory/e5_local_layout_trial.py \
  --model qwen2.5-coder:1.5b --run-local --with-intent
```

The local trial validates independent E5 brief and copy **locally before inference**, then sends to the model **only** the finite layout options, their strict response schema and the abstract three-category design intent. No brand, original brief/copy, arbitrary free text, files, browser history, credentials or source code are sent. The model still proposes only the three approved tokens. The **human-authored** E5 compiler produces the output ZIP, while the receipt records the canonical intent SHA-256, actual selected tokens, transparent match score and explicit `humanAestheticReviewPerformed:false`, `ownerApproved:false`, `deploymentAuthorized:false`, `stage45Go:false`. Absent or invalid fourth opt-in fails closed **before any model contact**. Existing no-overwrite 0600 private output rule and default plan-only mode remain.

## Tests, evidence and scope limits

The nine new `test_e5_layout_design_intent.py` cases verify strict enum normalization, duplicate and injection denial, human-defined matching rubric and low-score honesty, failure before network absent the fourth opt-in, independence from brand/copy/source, two genuinely different requested abstract directions under **mocked provider** replies, default plan-only no file/model reads, and private 0600 CLI output. The new exact-head CI workflow also reruns the **72 inherited Python tests** and the existing real browser/reference checks; it builds both an informational/dark/contemporary and an immersive/light/editorial **mock-model-selected layout**, verifies a clear **3/3 human-rubric score** and distinct ZIP hashes, and opens both in **actual Chromium at desktop/mobile/reduced-motion**.

Neither branch CI nor repository source asserts a real Ollama model was installed, queried or benchmarked. This test does **not** certify model taste, model alignment with real users, autonomous original site generation, complete E4/E5 or production deployment.

Remaining: **E3.9** real coding/repair held-out evaluation, **E4** owner-device true model inference and quota/privacy evidence, **E5** authentic original model-authored functional website code, independent accessibility/performance/visual QA and human approval, **E6** enterprise Cinematic Studio (NOT security S0-E6), **E7** controlled multi-agent companies, **E8** Syntra cockpit, **E9** integrated acceptance. Independent **S0 security** last verified through S0-E6I in Platform draft #352. Nine Stage45 release gates remain uncertified and owner PC acceptance pending.

**Stage45 NO_GO**: no production deployment, protected branch merge, source disclosure, client permissions, model writes, live-money operation or release pin promotion.

# Enterprise E4→E5 — local-only layout-token model trial and real visual review

**30 October 2026 · independent ENTERPRISE development stage · Stage45 NO_GO · DRAFT / unmerged.**

## Verified parent

Stacked directly on [Runtime Enterprise draft PR #228](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/228) at immutable `3ab6d3fa6dc7c7f8e5c8e9e19535be1407592a68`. Its [CI #41022426566](https://github.com/teldigi5-wq/aetheris-ai-runtime/actions/runs/41022426566) passed **62/62 Python regressions** and **12/12 real Chromium desktop/mobile/reduced-motion contexts**. Parent code produces 18 finite responsive design variants, but did **not** let any real model propose a choice. The entire inherited enterprise branch stack is draft/unmerged; no changes to protected main or production runtime pin.

## What this development actually adds

1. **Separate E4 task:** `website-layout` is a new explicit admission category in `e4_free_route_admission.py`. Only allowlisted, caller-claimed **installed local Ollama models** can be considered. OpenRouter/cloud routes are **DENIED** with `WEBSITE_LAYOUT_LOCAL_ONLY`, even when a provider claims its model is free. Original `website-copy` remote denial remains unchanged and legacy E4/E3.6 code-repair paths still only permit `scroll/mobile/reduced`. A layout request cannot silently become a model-authored code repair.
2. **No automatic model calls:** `tools/enterprise/e3_factory/e5_local_layout_trial.py` defaults to **plan-only**. Merely importing/running it without `--run-local` does not read research briefs, contact a model, download models, send content externally, generate code or change the source tree.
3. **Three independent, explicit local opt-ins:** To deliberately try an already installed allowlisted model, require all `AETHERIS_E36_LOCAL_INFERENCE_OPT_IN=yes`, `AETHERIS_E4_EXPLICIT_LOCAL_SESSION_OPT_IN=yes` and `AETHERIS_E5_LOCAL_LAYOUT_TRIAL_OPT_IN=yes`. Heavy local models additionally require `AETHERIS_E38_HIGH_RESOURCE_MODEL_OPT_IN=yes`. Opt-in environment flags are *not* authenticated owner identities. A per-instance lock consumes a one-attempt budget **before** any model inventory request; no retry, model pull or cloud fallback.
4. **Strict privacy boundaries:** Before network access, existing E5 independently validates exact brand, permitted short copy and creative brief. During inference, the model receives only the **six bounded enumeration lists/schema** describing composition, palette and typography; **no brand text, customer data, source code, creative brief, copy, credentials, browser history, files, key material or deployment instructions** is included. It can return exactly one JSON object: `version=1`, `composition`, `palette`, `typography`. It cannot return executable CSS or HTML. All untrusted generated data is verified by `e5_structured_layout_preview.read_layout` before calling its **human-authored** trusted finite CSS/WebGL compiler.
5. **Single private review archive:** On accepted trial, `target/e5/local-model-layout-review.zip` contains one nested validated offline `preview.zip` and an immutable JSON review receipt with source-integrity hash and exact enumerated token choice. Exclusive-create 0600 file permissions prevent accidental overwrite. No content is published, committed or applied. The review explicitly says model identity and authorship are not independently verified, source/CSS/JS are human-authored, there is no owner approval or production release.
6. **Failure is not success:** Bad provider envelope, an unknown/high-resource-without-opt-in model, suspicious inventory, missing creative brief, disallowed tokens, duplicate keys, URL/CSS payloads and provider unavailability are refused. No model response is printed in errors. The existing Stage45 release decision remains **NO_GO**.

## Opt-in example on owner computer only

Before considering a real trial, inspect the branch and existing local Ollama model list. Put two **non-sensitive synthetic** research-only JSON inputs at `target/e5/creative-brief.json` and `target/e5/copy-proposal.json`, following [the E5 creative brief schema](E5_CREATIVE_BRIEF_GATE_20261030.md).

```bash
# SAFE DEFAULT — prints a plan and does not access a brief or call Ollama.
python3 tools/enterprise/e3_factory/e5_local_layout_trial.py --model qwen2.5-coder:1.5b

# EXPLICIT LOCAL-ONLY EXPERIMENT, only on a personally controlled reviewed environment:
AETHERIS_E36_LOCAL_INFERENCE_OPT_IN=yes \
AETHERIS_E4_EXPLICIT_LOCAL_SESSION_OPT_IN=yes \
AETHERIS_E5_LOCAL_LAYOUT_TRIAL_OPT_IN=yes \
python3 tools/enterprise/e3_factory/e5_local_layout_trial.py \
  --model qwen2.5-coder:1.5b --run-local
```

There is **no implicit permission** to install Ollama, pull a model, pay for cloud compute, access customer data or run a production site. An installed model ID is an operator-provided observation, not cryptographic provider attestation.

## CI proof boundaries

`test_e5_local_layout_trial.py` exercises task-specific route denial, double/triple opt-in defaults, malformed/fake model responses and inventory, bounded one-shot behavior, private evidence, strict original code hashes, and no code/brief disclosure. `runtime-e5-local-layout-trial.yml` runs **all inherited E4/E5 Python tests**, a fake Ollama response through the actual adapter and trusted website compiler, and real Chromium of the extracted **mocked-model-selected layout at desktop/mobile/reduced-motion**, in addition to the three earlier independent visual variants. CI **does not install or run a real Ollama model**, and cannot prove installed-model quality, visual creativity, autonomous coding, client authorization or product release.

## Remaining enterprise roadmap and independent security

- E3.9: independently evaluated real-model code/repair and held-out creative competence unverified.
- E4: owner-hardware real local inference performance, capacity, privacy/quotas and cloud consent/price assurance incomplete.
- **E5**: the compiler and opt-in local model interface are prototypes. Genuine original AI-generated layouts, supervised repair, asset licensing, measurable WCAG 2.2 AA compliance, UX/aesthetic review, agent handoffs and owner approval are still required.
- **E6** means *Enterprise Cinematic Studio* (Three.js/Blender/GSAP), not Security S0-E6. E6, E7 virtual-company agent delegation, E8 Syntra cockpit and E9 integrated release remain incomplete.
- Separate S0 security last verified through S0-E6I (Platform draft PR #352); all 9 Stage45 independent release gates remain uncertified. Physical owner-PC acceptance remains pending.

**Stage45 NO_GO.** No protected-main merge, actual owner hardware/model benchmark, production deployment, paid service, customer access, live-money action, or release pin promotion.

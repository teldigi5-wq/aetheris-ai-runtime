# E3.7 — Hermes and advanced zero-API-fee local model expansion

**Draft, candidate-only, no model downloaded or run, no owner PC permission, no owner approval or production deployment.** Based on E3.6A PR #130. This slice adds real open-weight **Hermes** model options, not the previously mistyped 'Artemis'.

## Exact tested Ollama tag candidates (11 total)

| Model tag | Intended role | Hardware / status |
|---|---|---|
| `qwen2.5-coder:1.5b` | Existing lightweight code baseline | Prior E3.5 0/3 accepted repairs, not quality-certified |
| `qwen2.5-coder:0.5b` | Very small coding-adapter trial | Previously called in E3.4B, no browser-verified fix |
| `smollm2:135m` | Very small interface proof | Lower quality expected; benchmark first |
| `hermes3:3b` | Nous Hermes structured text and assistant investigation | Ollama library says around 2GB download; not installed or tested here |
| `hermes3:8b` | More capable Hermes generalist/tool-format proposal | Ollama library says about 4.7GB; no hardware benchmark |
| `qwen3.5:2b` | Small open-weight modern multi-purpose model | Not installed or tested |
| `qwen3.5:4b` | Midrange reasoning and coding candidate | Not installed or tested |
| `qwen3.5:9b` | Higher-capability reasoning, more memory | Memory budget must be measured |
| `gemma3:4b` | Multilingual and optionally visual model | Text-only E3 advisor, no vision adapter in this stage |
| `deepseek-r1:7b` | Local reasoning evaluation | No safety/quality performance claimed |
| `qwen3-coder:30b` | Advanced local software engineering reference | Ollama reports approximately **19GB** model download, not suitable for a 6GB GPU alone; optional on bigger hardware |

All 11 are **catalog allowlist entries only**. This code never downloads or auto-installs any model, runs no model by itself, and never selects a fallback without an explicit operator request. Existing `e36_free_model_router.py` requires `AETHERIS_E36_LOCAL_INFERENCE_OPT_IN=yes`, confirms the exact tag is installed with localhost `GET /api/tags`, and only then can request a bounded, text-only local inference. The model's output has no authority or patch/execution permissions.

**Hermes 4 (separately researched, noncallable):**
- `NousResearch/Hermes-4-14B` — verified official Hugging Face open-weight model, **Apache-2.0**, designed for function/JSON-formatted outputs. We have **NOT** verified an Ollama tag, trusted runtime template, local GGUF package or device-memory footprint.
- `NousResearch/Hermes-4-70B` — official model, Llama3-family model license; heavyweight research candidate. No executable model route or hardware feasibility claim.
- Do not infer that a Hugging Face checkpoint is a locally installed and runnable Ollama tag. Both references remain `OPEN_WEIGHT_REFERENCE_NOT_OLLAMA_ROUTABLE` with `localRuntimeValidated=false` in the catalog.

## Existing five OpenRouter free candidates remain unchanged

`openrouter/free`, `qwen/qwen3.8-27b:free`, `google/gemma-4-26b-a4b-it:free`, `minimax/minimax-m3.1-flash:free`, `nvidia/nemotron-3-ultra-550b-a55b:free`. They are cloud *candidates* requiring fresh exact-model zero-price checks, a user API key and separate consent to upload synthetic source snippets. **No live OpenRouter inference has occurred in these E3.6/E3.7 PRs.** Free tiers, model availability, and privacy terms can change.

## Verified primary source references

- Hermes 3 Ollama, including **3B/8B exact tags and file sizes:** <https://ollama.com/library/hermes3>
- Hermes 4 14B model card/license: <https://huggingface.co/NousResearch/Hermes-4-14B>
- Hermes 4 70B official card: <https://huggingface.co/NousResearch/Hermes-4-70B>
- Qwen 3.5 Ollama: <https://ollama.com/library/qwen3.5>
- Qwen3-Coder official Ollama tags and ~19GB 30B listing: <https://ollama.com/library/qwen3-coder>
- Gemma3: <https://ollama.com/library/gemma3>
- DeepSeek R1: <https://ollama.com/library/deepseek-r1>
- OpenRouter current free collection: <https://openrouter.ai/collections/free-models>

## Enterprise inventory verified from checked-in source

The read-only `enterprise/organization-blueprint-v1.json` holds **13 proposed companies/100 proposed divisions**; `orchestrator-service/src/main/resources/application.yml` contains **26 configured catalog agents**. E2's four CEO/design advisory roles are `PROPOSED` and separate from the 26. These labels do not make 130 running processes or autonomous employees. No new agents or tool privileges are introduced in this model catalog increment.

## Acceptance and following stage

GitHub CI must independently verify 11 exact Ollama allowlist tags, the Hermes 4 research-only state, denial for unknown/paid/uninstalled models, Ubuntu/Windows unit tests, original Chromium scroll/mobile/accessibility QA, full Java regression, exact registry count and no-authority gate. Changes remain draft, protected main unchanged.

Next: **E3.8 controlled runtime hardware-fit assessment** on authorized disposable machines only. Choose one Hermes/Qwen local model to install, measure RAM/VRAM, latency, model provenance, prompt injection refusal, strict text-patch gate and real browser performance. OpenRouter external sharing only after explicit operator consent and privacy review. Stage45 owner PC, positive owner approvals, financial tools, cloud production and executable multi-agent organization remain **NO-GO**.

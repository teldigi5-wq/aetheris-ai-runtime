# E3.8A — Open-weight model research expansion and local hardware opt-in

**Status: DRAFT. No model was downloaded, installed, inferred or benchmarked in E3.8A. No owner PC, production deployment or protected-main merge.**

This increment builds on verified E3.7 PR #132. E3.7 defined 11 local Ollama candidate tags and five free OpenRouter routes. This stage adds **nine exact Ollama tags and one named free cloud endpoint** with additional high-resource consent protection.

## Configured E3.8A choices: 26 routes

These are *available routing options in source*, not instances that are installed, running, licensed for every possible downstream use, benchmarked or granted agent permissions.

### Local Ollama candidates (20, exactly)

| Published model tag | Purpose | Deployment caution |
|---|---|---|
| `qwen2.5-coder:1.5b` | Tiny coding baseline | Genuine E3.5 quality baseline 0/3 browser-verified repairs |
| `qwen2.5-coder:0.5b` | Extremely small coding baseline | Prior local inference did not pass repair acceptance |
| `smollm2:135m` | Smoke-test language generation | Not suitable to certify an advanced coding agent |
| `hermes3:3b` | Structured general assistant | Requires user-installed weights |
| `hermes3:8b` | Richer Hermes general reasoning | Installation and memory budget must be measured |
| `qwen3.5:2b` | Local multimodal/reasoning reference | Current E3 adviser uses text only |
| `qwen3.5:4b` | Mid-range analysis and coding | Candidate for small workstation benchmark |
| `qwen3.5:9b` | More capable reasoning | **Second high-resource opt-in** |
| `gemma3:4b` | General assistant and multimodal future | Current code sends text only |
| `deepseek-r1:7b` | Chain of reasoning evaluation | Must benchmark rather than assume |
| `qwen3-coder:30b` | Large coding tasks | Approximately 19GB model package; **second opt-in** |
| `qwen3.5:0.8b` | Very small Qwen 3.5 evaluation | Publisher lists ~1.2GB model, check actual runtime fit |
| `qwen3.5:27b` | Large language/vision reasoning | ~17–20GB published package; **second opt-in** |
| `qwen3.5:35b` | Advanced Qwen mixture-of-experts | ~22GB model package; **second opt-in** |
| `granite4:3b` | Enterprise RAG, extraction, structured tasks | IBM Granite 4 Apache-2.0; local tests required |
| `granite4:7b-a1b-h` | Efficient hybrid enterprise reasoning | Mamba-2 hybrid; runtime compatibility verification |
| `gpt-oss:20b` | Open-weight reasoning, agentic text tasks | ~14GB MXFP4 package and ~16GB system memory estimate; **second opt-in** |
| `gpt-oss:120b` | Research-grade large-scale reasoning | ~65GB package; unsuitable for 6GB GPU alone; **second opt-in** |
| `devstral-small-2:24b` | Codebase and multi-file engineering research | ~15GB; newer Ollama version; **second opt-in** |
| `qwen3-coder-next:q4_K_M` | Advanced agentic coding research | ~52GB package; **second opt-in** |

Model sizes are **published weights/download sizes**, *not* guaranteed peak VRAM/RAM consumption or any performance measurement by Aetheris. Quantization, actual context length and CPU offload can significantly change resource requirements. Running a large model entirely on an RTX4050-class 6GB GPU is not assumed feasible.

### Free cloud routes (6)

- `openrouter/free` — **router selector**, not a named model with a fixed identity.
- `qwen/qwen3.8-27b:free` — free candidate, exact live zero-price preflight required.
- `google/gemma-4-26b-a4b-it:free` — same live price/provider gate.
- `minimax/minimax-m3.1-flash:free` — coding-oriented cloud candidate.
- `nvidia/nemotron-3-ultra-550b-a55b:free` — reasoning cloud candidate; NVIDIA free endpoint has logging terms.
- **NEW** `nvidia/nemotron-3.5-super-vl:free` — published Oct 15, 2026, multimodal visual candidate; existing E3.8 adviser sends **text only**, not images. Free endpoint warns against personal/confidential data and logs prompt content.

All six routes are subject to existing `e36_free_model_router.py` **explicit opt-in to cloud and separate disclosure of source**, operator-supplied key, fixed HTTPS host, exact live **single-model** zero-price verification and no paid fallback. We performed **no new live OpenRouter requests**; free availability and usage caps change.

The **count distinction** is essential: `20 local tags + 5 specifically named free cloud models + 1 free router selector = 26 configured routes`. There are **25 individually named active model candidates** and **one free routing selector**; none of the 26 entries proves installed running AI.

## Added high-resource safety gate

`AETHERIS_E36_LOCAL_INFERENCE_OPT_IN=yes` is always required to call local inference. For the eight explicitly listed high-memory local tags, E3.8 also requires the independent `AETHERIS_E38_HIGH_RESOURCE_MODEL_OPT_IN=yes` switch **before even querying Ollama's installed models**. There is no weight download, model auto-fallback or remote fallback. A user opt-in is not a verified hardware-fit certification; the approved local model must still exist in `GET 127.0.0.1:11434/api/tags`, and a real microbenchmark would need RAM/VRAM, speed and stability evidence. All responses remain untrusted text without filesystem, GitHub, owner-approval, deployment or privileged tool authority.

## Exactly 12 additional models in the future research track, noncallable

The separately checked-in `docs/runtime/enterprise-e38-future-model-research.json` records **two pre-existing research-only Hermes 4 references plus ten newly researched candidates**:

1. `NousResearch/Hermes-4-14B` — verify exact local runtime, license, JSON compliance and memory.
2. `NousResearch/Hermes-4-70B` — license and large-hardware feasibility.
3. `nomic-embed-text` — dedicated **embedding API**, not a chat completion model.
4. `qwen3-embedding:0.6b` — multilingual local embedding and RAG.
5. `granite4:32b-a9b-h` — large enterprise reasoning/RAG.
6. `ministral-3:3b` — small Mistral structured multimodal model, needs runtime checks.
7. `llama4:scout` — huge multimodal model and separate Meta usage/license policy.
8. `qwen3.5:122b` — ~81GB large multimodal model; high-memory research only.
9. `gpt-oss-safeguard:20b` — **separate safety classification**, not trusted action authorization.
10. `qwen3-vl:8b-instruct` — screenshot/UI visual analysis, needs image-aware adapter.
11. `llama3.2-vision:11b` — image assessment, Meta community license.
12. `moondream:1.8b` — edge vision reference, must benchmark accuracy.

**Future target is to *evaluate* 12 more models, not a promise to automatically install or enable all 12.** Some should become specialized RAG/vision/safety adapters, not part of the current one-line website code-repair route. If all qualify after the research gates, that is **37 individually identified model candidates plus the free router selector**. This is a planning ceiling, not 38 executing engines.

## Security, tests, actual benchmarks

E3.8A CI tests (verify exact-head before reporting success): Ubuntu/Windows deny high-memory loads without two opt-ins and deny uninstalled model tags; confirm 20 local +6 cloud candidate route definitions and 12 research-only entries; original Chromium browser full regression, complete Java orchestrator regression, separate no-approval/Stage45 guard.

The measured **real AI coding** baseline is still E3.5's **0 successful Chrome-verified code repairs from 3 real Qwen2.5-Coder 1.5B requests**. E3.3's **6/6** known-fixture heuristic repairs are explicitly rule-based, *not LLM achievements*. Number of configured routes is not a quality benchmark or benchmark improvement.

## Remaining roadmap and exit criteria

- **E3.8B:** Choose one Hermes/Qwen/Granite or GPT-OSS local tag in a *disposable lab* and run real controlled inference with size, total memory, wall time, generated tokens and browser patch acceptance evidence. Only after an explicit operator opt-in.
- **E4:** Proper model selection/ranking, normalized capabilities and budget/error audit.
- **E5:** End-to-end bounded website factory with verified real code output, not decorative mockups.
- **E6:** Blender/Three.js/GSAP advanced immersive experience factory with actual video/3D QA and device performance measurements.
- **E7:** Orchestrate real bounded workers with domain separation, explicit action approvals and replay-safe audit.
- **E8:** Protected web/desktop command center for human review.
- **E9:** Independent production/owner-PC certification, long-term security/load/accessibility tests, Stage45's actual 13-blocker gate.

E4–E9 remain **six major future phases**, plus E3.8B and independent Stage45 certification. Prior effort allowance **~101–220 engineer-days** is a preliminary planning range, not a timeline, guaranteed rate or validated capacity estimate.

Sources used for model-tag verification: <https://ollama.com/library/qwen3.5>, <https://ollama.com/library/granite4>, <https://ollama.com/library/gpt-oss>, <https://ollama.com/library/devstral-small-2>, <https://ollama.com/library/qwen3-coder-next>, <https://openrouter.ai/nvidia/nemotron-3.5-super-vl:free>. For future-only model tags see the `source` URL of each research entry. All newly listed models should undergo current license/provider terms verification at deployment time.

**Safety remains NO_GO:** existing 13 companies and 100 divisions are proposal taxonomy metadata; only 26 configured agent identities exist, not autonomous operators. The E2 four advisory roles are also nonexecuting. Owner authentication, production execution, financial tools, physical workstation commands and real Stage45 release certification have not been granted. Protected `main` remains unchanged; all work is on the draft PR stack.

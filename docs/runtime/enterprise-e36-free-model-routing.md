# E3.6A — Free-only OpenRouter + local Ollama multi-model advisory catalog

**Draft-only, no owner PC, no production, no paid fallback, no real OpenRouter inference in CI.** This builds on verified E3.5 PR #128, which recorded **3 genuine Qwen1.5B model calls but 0/3 independently browser-verified repairs**. No quality score is invented by adding more provider choices.

## Supported provider candidates

| Provider | Exact candidate model ID | Status |
|---|---|---|
| Local Ollama | `qwen2.5-coder:1.5b` | Optional, must actually be installed |
| Local Ollama | `qwen2.5-coder:0.5b` | Optional, must actually be installed |
| Local Ollama | `smollm2:135m` | Optional, must actually be installed |
| OpenRouter | `openrouter/free` | Free-variant routing, **live catalog zero-price check required** |
| OpenRouter | `qwen/qwen3.8-27b:free` | Provider currently lists $0 prompt and completion pricing; live verification required |
| OpenRouter | `google/gemma-4-26b-a4b-it:free` | Provider currently lists $0 prompt and completion pricing; live verification required |
| OpenRouter | `minimax/minimax-m3.1-flash:free` | Currently listed free; coding-oriented, zero-price live verification required |
| OpenRouter | `nvidia/nemotron-3-ultra-550b-a55b:free` | Currently listed free; provider states free endpoint logs data, explicit external-source consent required |
| Artemis | **NO verified model identifier** | Not callable; `artemis` is deliberately rejected |

References checked 20 October 2026:
- <https://openrouter.ai/qwen/qwen3.8-27b:free>
- <https://openrouter.ai/google/gemma-4-26b-a4b-it:free>
- <https://openrouter.ai/minimax/minimax-m3.1-flash:free>
- <https://openrouter.ai/nvidia/nemotron-3-ultra-550b-a55b:free>
- <https://openrouter.ai/openrouter/free>
- <https://openrouter.ai/pricing>
- <https://openrouter.ai/docs/api/api-reference/models/get-model>

OpenRouter's catalog and free availability can change. Its free tier currently documents **50 free-model requests/day and 20/min**, increasing to 1,000/day for qualifying credit purchasers; even free endpoints can reject, limit, retain, or process requests under provider-specific policies. There are no contractual free uptime guarantees. Never call a paid variant silently. No assumption is made that an OpenRouter `Artemis` model exists; if a precise official provider slug becomes verifiable, introduce it through a separately reviewed allowlist change and corresponding safety tests.

## Implemented software

`tools/enterprise/e3_factory/e36_free_model_router.py` is an **actual functional Python stdlib module**:

- `list` prints an offline static catalog. It does not look up live prices or load/download any model.
- `advice ollama <allowed-model> <scroll|mobile|reduced>` can query the installed local model *only* when `AETHERIS_E36_LOCAL_INFERENCE_OPT_IN=yes`. It checks `GET http://127.0.0.1:11434/api/tags` first, then submits a small text-only `/api/generate` request. Does not auto-pull or silently switch to cloud.
- `advice openrouter <allowed-model> <scroll|mobile|reduced>` requires **all** of `AETHERIS_E36_REMOTE_INFERENCE_OPT_IN=yes`, **separate source upload consent** `AETHERIS_E36_EXTERNAL_CODE_DISCLOSURE_OPT_IN=yes`, and an operator-supplied `OPENROUTER_API_KEY`. It uses only HTTPS to `openrouter.ai:443`. It first queries the official live **single-model** endpoint `GET /api/v1/model/{author}/{slug}` for the exact allowlisted model, avoiding the oversized full-model catalog, and requires the **exact selected model ID**, precisely one matching record, and *all three* `prompt`, `completion`, `request` price fields present and numerically zero. Only then can it POST a small text-only chat completion with `max_tokens=180` and no paid tools. If pricing is nonzero, absent, the catalog is missing/invalid, the model is unavailable, the API key is absent, the provider returns a non-200 response/rate limit, or requested model is Artemis/unknown, the router refuses. It never selects a paid fallback.
- All model output is untrusted. Results provide raw bounded text and a SHA256 receipt, classify strict JSON abstentions / short replacement suggestions / invalid formats, report actual model/provider name, and permanently mark **not executed, no GitHub write, no production, no owner approval, no cryptographic model attestation**. This **does not automatically stage or apply a patch**. Any future patch still requires the reviewed E3.4A/E3.3 network-disabled Docker source/grammar/hash gate and independent Chromium QA.
- No dynamic hosts/URLs, redirects, arbitrary user model IDs, imported HTTP clients, shell commands, secrets in emitted evidence, unauthorized source paths or background inference.

**NVIDIA free-endpoint privacy warning:** its model page expressly cautions against sending confidential or personal information and states prompts can be logged for security and product improvement; this integration does not offer private retention guarantees. The external source excerpt is *only* a bounded generated **test fixture**. Nonetheless, a remote provider receives the actual prompt content. **Do not connect sensitive/private repositories** to OpenRouter without a separate explicit privacy review. Use a limited-scope OpenRouter key stored in your own environment or runner secrets, never hard-code or commit an API key.

## CI and security evidence

GitHub Actions does **not** possess OpenRouter credentials and makes **zero OpenRouter inference calls**. Ubuntu/Windows unit tests use mocked provider envelopes to prove complete consent gating, zero-price verification, no paid model/unknown Artemis, no URL redirect, no API-key leakage, no remote fallback, local-only installed-model checks, and read-only return behavior.

The original generated site also undergoes real Chromium interaction/accessibility testing in the existing network-disabled worker; full Java orchestrator regression and static no-owner/no-Stage45 release guard remain required. Green CI certifies these contracts, not the quality or availability of cloud inference.

## Precise limitation and next milestone

This is a **provider-agnostic advisory selector in the E3 factory**, not a live multi-agent company or a new Spring production endpoint. Local and OpenRouter requests are available only upon explicit operator action and valid provider availability. **Actual OpenRouter live inference remains untested** in this PR. The free model router is a candidate option; external data privacy and quota management require deliberate owner setup. Its results are not model-quality scores.

Next E3.6B should add an explicitly consented no-secret-in-logs live OpenRouter `:free` test with a synthetic nonprivate fixture, verify the provider's real model ID/price/rate response and compare actual patch proposals against independent browser tests. Preserve failed/rejected/abstained outcomes and avoid claiming any fixed percentage improvement from tiny samples.

Production and owner PC, Stage45 13-blocker release validation, real authenticated owner grants, financial permissions and protected `main` remain **NO_GO**. All earlier stacked draft PRs remain unmerged and require review in order.

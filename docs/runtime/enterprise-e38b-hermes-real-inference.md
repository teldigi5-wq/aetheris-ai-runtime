# Aetheris E3.8B — Measured Hermes 3B local CPU repair trial

**DRAFT, disposable GitHub CI only.** This stage is a real-inference experiment, **not a production agent release**. The GitHub runner, not the owner's computer, is used. Protected main and existing stacked draft PRs remain unchanged.

## What is implemented

- Reuses the verified E3.5 architecture and independent Chromium/patch gate against the **same three E3.3 synthetic variants**: `scroll-24`, `mobile-940`, `reduced-boolean`.
- `e38b_hermes_inference.py` requires **`AETHERIS_E38B_REAL_MODEL_CI_OPT_IN=yes`** and only the exact allowlisted test-case names. It uses the existing loopback-only, fixed-endpoint stdlib Ollama client. **No mocks** are used in the real-inference job.
- A disposable, digest-pinned Ollama 0.12.10 Docker image downloads exact **`hermes3:3b`** open weights into an ephemeral volume. The model is listed by Ollama at approximately **2.0 GB**; actual downloaded bytes and self-reported model SHA256 are recorded by the trial at runtime. The model container is limited to **2 CPU, 5 GiB memory, 128 PIDs, read-only root, all Linux capabilities dropped, no new privileges, and host binding only 127.0.0.1:11434**. Its network is needed to pull model weights; that network is separate from the **network-none patch worker**. Container snapshots are **instantaneous, not peak memory measurements**.
- Each case *must* fail with the correct independent Chromium browser error **before** model inference. The model receives a bounded source excerpt and corresponding observed browser data. It may only return a short text replacement suggestion or abstain; invalid JSON/extra keys are rejected.
- A suggested replacement is untrusted text. The existing distinct `e34-prepare` and `e33-apply` phases run in separate isolated Docker workers with no network, exact target-file/old/new grammar and SHA256 checks. They may modify only the generated disposable website fixture; no GitHub write, owner grant, agent shell command, or deployment.
- After any permitted patch, **real Chromium** tests the browser state. Failures and abstentions are recorded as failures. Restoring test fixtures before another attempt is **not counted as a model repair**. The original full site Chromium test runs after all three cases.
- New Windows and Ubuntu unit tests cover model name/digest, exact case allowlist, opted-in local loopback access, denial of unsupported browser diagnoses, and lack of privileged execution. Full Java orchestrator regression plus a zero-authority static guard are required.

## Honest benchmark comparison

- Prior E3.5 baseline: **Qwen2.5-Coder 1.5B, 0/3 independently Chromium-verified repairs** despite three genuine CPU model calls.
- Hermes 3B E3.8B result: **PENDING exact-head real model CI**. Do not invent a success rate. The evaluation does not isolate differences in prompt design or inference environment and is **not statistically significant**. The three fault types are known and selected from the same repository, not third-party blinded real-world bugs.
- Model size, token counts, evaluation durations and instantaneous container use are empirical only after the live job completes. No accuracy, price, speed, GPU-fit or reliability claim is made in advance.

## Why this stage matters

This is a practical checkpoint between *advertising 20 available Ollama tags* and demonstrating at least one genuinely working local model. It measures whether Hermes produces valid text proposals, whether the existing safety policy accepts them, and whether a real browser confirms a repair. A failing but correctly contained run is valuable negative evidence.

## Explicit limitations

- No customer's code or secrets are in prompts. No model on the owner's Windows device; no local laptop download.
- No remote OpenRouter request, paid API, automatic model downloads on the owner PC or uncontrolled fallback.
- No Hermes Agent framework or Hermes 4 runtime; this trial uses **Hermes 3B model weights**, not an autonomous tool-execution framework.
- No authentication of the human owner, actionable approval tokens, production deployment, real multi-company agent operating structures, or completed Stage45's independent 13-blocker certification. Those remain **NO_GO**.
- Java/Chromium/source unit tests and Docker boundaries must be verified on the *exact PR head* before treating this experiment as run and measured.

## Official sources

- Hermes3 3B tag and published weight size: <https://ollama.com/library/hermes3/tags>
- Ollama generated-token/duration semantics: <https://github.com/ollama/ollama/blob/main/docs/api/usage.mdx>
- Prior verified real model baseline: <https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/128>
- Previous model-catalog branch: <https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/134>

Follow-on E3.8C: normalize truthful model comparison and usability of structured outputs without bypassing independent source policy or inflating quality benchmarks. E4–E9 and physical Stage45 certification remain planned rather than completed.

# E3.9G-12B4L — Genuine Qwen7B selector-targeting hints, same strict policy

**25 October 2026 — pre-registered controlled follow-up.** Model results must be taken only from completed exact-head CI, not inferred from prompts or tests. Public synthetic fixtures, not a held-out independent benchmark.

## Grounded B4K result we are improving
The B4K genuine two-call Qwen2.5-Coder 7B trial on two developer-authored broken HTML/CSS pages completed **5/5 CI jobs** at SHA `1eb27d3e99f1950f393bfe2718cc95f60a0a712a`. [Actions run #40163813935](https://github.com/teldigi5-wq/aetheris-ai-runtime/actions/runs/40163813935), [PR #195 benchmark evidence](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/195). Both genuine replies satisfied the JSON envelope but failed the unchanged B4J exact-source policy: **0/2 approved and 0/2 browser-verified**.

Exact failure localization from the archived raw model receipt:
- Keyboard-focus repair aimed at general `.skip` base positioning instead of the existing defective focus-state rule `.skip:focus{top:-120px;}`.
- Reduced-motion repair aimed at normal `.ticker` animation, disabling effects in all modes, instead of the existing defective `prefers-reduced-motion` media override.

These are *wrong-target* errors. Do not interpret failed model proposals as browser successes or loosen the safety policy.

## Single-variable intervention in B4L
Change **only model-facing prompt source targeting**: include the exact existing broken CSS rule and instruct the model to quote it verbatim as `old`, and independently propose the minimal `new` correction.

- Focus hint: exact existing `.skip:focus{top:-120px;}` rule; **does not provide** the reviewed `top:16px` correction.
- Reduced-motion hint: exact existing `@media(prefers-reduced-motion:reduce){.ticker{animation:tickerPulse 2s linear infinite;}}` rule; **does not provide** the reviewed `animation:none` correction.

The original defective source, two tasks, model `qwen2.5-coder:7b`, reviewed model digest prefix `dae161e27b0e`, zero-temperature settings, no-checkout inference isolation, five-field proposal schema, original B4J strict validator and real Chromium test remain unchanged. B4K was the *no-hint* comparison, B4L is the *existing-rule source hint* treatment. This is a two-case exploratory comparison; it is neither randomized nor representative of arbitrary websites.

## CI experiment
[New B4L GitHub Actions workflow](../../.github/workflows/runtime-e39g12b4l-source-hints.yml) reuses the proved B4K fixed-stage architecture: Windows/Ubuntu adversarial regressions, exact source-only ZIP, two genuine local Qwen7B completions in no-checkout runner, independent hash/receipt verification, allowlisted patch worker with network-none Docker, and disabled-JS/blocked-network Chromium after-state for accepted cases only.

An exact model-generated correction receives one verified public-fixture repair only after the unchanged literal gate permits it and two independent browser viewports pass. Denied JSON, wrong literal, alternative CSS, missing/incorrect result, and abstentions earn **zero**. A successfully completed CI workflow is **not** a claim of successful model repair. No owner-machine credentials, arbitrary user website edits, external browsing, production deployment or agent permission changes are added.

## Future trustworthy evaluation
After this controlled diagnostic, actual progress beyond E3 requires newly human-curated independent private faults (issue #169), reviewed per-domain security policies, separate reference evaluators and real Chrome QA on genuinely held-out challenges, with enough samples to state uncertainty. The nine release gates, Stage45 and E4–E9 remain NO_GO until separately evidenced.

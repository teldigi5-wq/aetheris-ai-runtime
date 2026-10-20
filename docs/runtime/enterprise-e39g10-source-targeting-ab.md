# E3.9G-10 — source-targeted prompting versus original prompt (real model A/B)

**DRAFT, experimental**. Stacked on [PR #164](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/164) exact base `55bac82e50f193ec1c38328303c4e1cce5f55a08`. Tracks [issue #165](https://github.com/teldigi5-wq/aetheris-ai-runtime/issues/165). Protected main unchanged; Stage45 **NO_GO**.

## Problem and hypothesis

The prior real local Ollama E3.9G-9 cohort made twelve genuine model calls but achieved zero safe/browser-verified repairs in either skills or no-skills arm, across mobile overflow, reduced-motion preference and scroll responsiveness. Raw responses often modified unrelated code or copied browser diagnostic fields into repair JSON. Source-targeting is a **hypothesis** about those failures, not proven by anecdotes.

This experiment compares exactly **two prompt structures** on the SAME model, SAME 3 existing PUBLIC synthetic E3.3 cases, SAME four pinned skills per case, same real Chromium BEFORE evidence, temperature=0, response shape and token budgets:

- `original`: preserve the prior E3.9G-9 **skill-context** prompt byte-for-byte. It receives the broad relevant source excerpt and combined browser observations.
- `targeted`: only reorganizes **which existing source substring is editable** and labels browser observations as read-only. It reports the source **filename** and verbatim existing offending substring extracted independently of the gold fixture. **Neither original correct code, golden replacement, actual patch policy nor test oracle is shown to the model**. The same exact pinned skill excerpt bytes are included.

Each of two disposable CPU Ollama models (`granite4:3b`, `hermes3:3b`) makes two real API calls for each of 3 tasks: **12 total completions** if CI finishes. Prompt order is swapped for one case; no significance/generalization claim from six cases per arm across only two small models and one test site. Previous E3.9G-9 cross-run outputs must NOT be substituted for current actual completions.

## Security boundary and independent credit

The model adviser only calls localhost Ollama after `AETHERIS_E39G10_ACTUAL_TARGETED_PROMPT_EXPLICIT_OPT_IN=yes`. New real source-target parser never reads the frozen repair policy and never imports the scorer. Raw completion/model tag, source SHA256, Chromium before-JSON digests, prompt SHA256, 4-source trust receipts, actual token count and opt-in are recorded per arm.

The **original unchanged** `e39g9_multidomain_worker.permitted` validates each untrusted `old/new` candidate against the already reviewed E3.3 single-literal grammar and exact expected resulting source bytes. The **only mutation** is inside existing network-none Docker worker with read-only repo, writable disposable `target/e3-factory/site`, capped CPU/RAM/PIDs and dropped capabilities. Invalid/unsafe JSON, prompt injection, wrong line, broad edits, JS/tool execution and out-of-scope changes are denied and counted negatively.

The after model patch is independently checked in real Chromium at **390×844** mobile and **1280×800** desktop; browser reads actual runtime scroll progress, reduced motion behavior, responsive overflow, CSS/JS state, page errors, before+after screenshot SHA256. The independent scorer credits browser repair ONLY if actual model response was accepted by unchanged policy AND source bytes exactly match allowed patch AND both browser after checks pass. A rejected patch has **zero after browser tests** and no repair credit.

Every arm is reset to exactly the same broken source between tests. After both are scored a distinct canonical fixture restoration runs, explicitly **not counted as an AI repair**. No production website, GitHub branch merge, laptop, wallet, trader, payment service, security scanner, external principal or privileged agent is involved.

## Interpretations

An improvement in the targeted arm would support a limited claim that better explicit source localization improves these tiny synthetic model repair attempts. It would **NOT** establish an improvement from skill references (both arms receive them), causal generalization across websites, premium visual design competence, multi-agent workflows or production readiness. A zero/negative result must be published unchanged, with exact raw model evidence.

Genuine independent multi-project held-out tasks, model version controls, repeated stochastic trials, accessibility/animation performance measurements, authenticated owner/task leases and E4–E9 + separate Stage45 remain open. Include full Java regressions and Windows/Ubuntu refusal tests in CI.

**No numerical results are claimed until the exact-head GitHub Actions completes.**

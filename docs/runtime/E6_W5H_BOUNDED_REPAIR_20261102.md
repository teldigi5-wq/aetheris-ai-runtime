# E6 W5-H — One Sanitized Retry for Real Local-Model Website Design and CSS

**Date:** 2 November 2026 · **Research only / DRAFT / NO DEPLOY** · Stage 45 NO_GO

## Strict recovery and why this stage matters

Canonical repository: `teldigi5-wq/aetheris-ai-runtime`. Child of W5-G **draft PR #272** at independently verified HEAD `b78981bb0c75699f2efec7c42a3309f3e66d57e2`, not protected `main`. Earlier previews and W1–W5-G branches unchanged. W5-G passed **90/90 contract tests and 69/69 browser scenarios**, all on fake source/test-double websites and with no real Ollama execution.

The actual W5-C runner previously made one design request and one CSS request per fictional held-out brief. Any deterministic schema/contrast/CSS failure rejected the entire case immediately. It also used an outdated design prompt that omitted the W5-E requirement for `accent` text on the alternate `surface` color (minimum 4.5:1), likely wasting model inference if that failure occurred. This work aligns the prompt and gives **one** strictly controlled chance to produce a different safe proposal without changing trust policy.

## Implementation

- `w5a_local_ollama.mjs` prompt now explains **all four** required text contrast pairs: ink-on-paper, accent-on-paper, ink-on-surface and accent-on-surface. The underlying strict validated compiler in `w5a_model_design_compiler.mjs` remains unchanged.
- `w5h_bounded_model_repair.mjs` wraps the existing fixed-`127.0.0.1:11434` Ollama API with a **maximum of two requests per phase**: initial candidate plus **one** safe retry if and only if the first response is parsable and rejected by one explicitly allowlisted deterministic source-design/schema or CSS-source policy code.
- The second prompt contains the original approved brief/design instruction plus **only a known error category** from a static allowlist. **No rejected model content, raw provider response, arbitrary user-supplied error string, secrets, URLs, command paths, stack traces or injected adversarial instructions** are fed back to the model.
- Provider/network errors, HTTP failures, malformed provider envelope, provider wrong-model mismatch, oversize or timeouts are **never retried**. A second invalid response produces `E6_W5H_VALIDATION_REJECTED_AFTER_ONE_SAFE_RETRY`, and the website remains **REJECTED**. Strict W5-A content/CSP and W5-C CSS grammar remain fail-closed. No policies, allowed selectors or token/color requirements are relaxed.
- `w5c_local_code_trial.mjs` uses this wrapper in the **actual owner-opt-in three-held-out-brief code generation path** and records safe hashes of the first and ultimately accepted prompt, attempt counts, first rejection code, `rawRejectedModelOutputSaved:false`, and `deterministicRepairBudgetPerPhase:1` for accepted results. Every authority/production flag remains false.
- `test_w5h_bounded_model_repair.mjs` exercises no-retry first-pass success, one repair from invalid schema, no replay of untrusted output, two invalid proposals fail, HTTP failure no retry, CSS injection repair, repeated malicious CSS fail, original three-brief happy path and a permanently unsafe mock CSS case; all are **HUMAN-AUTHORED FAKE PROVIDER RESPONSES**.
- `test_w5c_css_code_gate.mjs` keeps the inherited injection-negative case fail-closed, now expecting the explicit exhausted-repair reason instead of a first-pass W5-C reason.
- `.github/workflows/runtime-e6-w5h-bounded-model-repair.yml` re-runs inherited W1/W2/W5-A/B/C/D/E/F/G source/security tests, Next.js lock/typecheck/build, browser regression and W5-H negative tests on an exact stacked PR head. **No model download or real Ollama inference is performed by this workflow**.

## Explicit invariants and limits

- Total requests per held-out brief: **up to four** (two attempts for the design and two for CSS). Three briefs maximum. This can increase CPU-time costs *only when a real model trial is separately authorized*. CI fake responses do not consume real model resources.
- This does not automatically improve art direction, original conceptual quality, copyright safety, full page accessibility, W4-D physical GPU performance or Stage45 production readiness. Repairs solve narrowly reproducible **validation** failures, not creative quality.
- Valid but unattractive or fabricated factual claims could still be produced and must undergo human review. Do not claim actual real-model output or website authorship until a genuine local/authorized cloud run is independently checked.
- The current laptop belongs to a friend. Do **not** install Ollama or run GPU/device stress tests on it. Optional W5-D temporary GitHub CPU smoke remains gated by explicit owner approval label on PR #269 and has not run.
- Runtime authorization Issue #23, Platform authorization Issue #251, E6 Task7/8, real-model held-out evaluation, independent design and accessibility acceptance and nine Stage45 attestations remain separate NO_GO gates.

**Next useful step after verified W5-H CI:** obtain approval for the already prepared limited hosted CPU experiment, evaluate actual model replies against held-out briefs and inspect original website screenshots, not create endlessly increasing mock-only test stages. Preserve all draft branches and protected mains.

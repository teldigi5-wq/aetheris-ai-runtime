# E6 W5-I — Local Inference Completion and Phase-Specific Token Budgets

**2 November 2026 · Draft research only · NO_GO**

## Verified parent and scope

Canonical: `teldigi5-wq/aetheris-ai-runtime`, stacked on **W5-H draft PR #273** at exact verified commit `cf1202d1c7b255f8c2f437e691557f504d803f8b`. Earlier W1–W5-H drafts are not merged. Protected Runtime main `65a6262717adcd52ac8d8a16ed6f223e299fd74d` and protected Platform main `57ad3e0e602efa6feb275328254151b959a3499b` are unchanged.

The previous W5-H CI passed 99/99 tests and 69/69 browser mock scenarios. **None of these were genuine model-authored websites.** The optional W5-D CPU model smoke was not approved or triggered, and the current PC belongs to a friend. No Ollama installation, GPU test, production action or paid service is authorized.

## Actual generation issue addressed

Ollama's published `/api/generate` schema exposes `done`, optional `done_reason`, optional `eval_count` and `options.num_predict`, a maximum number of output tokens. A `done:true` response may still have `done_reason:length`, meaning it reached an output limit. Prior W5-A accepted any `done:true` envelope and used the **same 1,500-token budget** for a full two-page JSON design and short CSS.

This could cause an incorrectly accepted "finished" response (if its JSON parsed) or a failure due to a constrained design output budget. The model remains strictly bounded; this stage does not assert a particular model can generate two complete pages.

## Reviewable changes

- `w5a_local_ollama.mjs`: a closed two-value `design|css` inference phase policy. Design may generate at most **3,000 tokens** with `num_ctx:6144`; restricted CSS at most **900 tokens** with `num_ctx:3072`. All values are owned by source code, not the model, prompt, environment or request caller. Existing fixed localhost host, bounded response bytes, 180-second timeout, strict JSON schema, no scripts and disabled remote inference remain.
- Provider envelopes with present `done_reason` other than `stop` are **rejected**, even if `done:true` and response JSON parses. Present `eval_count` must be a positive integer within its phase budget (one token tolerance); unexpected/malformed counts fail closed. For older Ollama responses lacking optional metadata, the adapter explicitly records `providerStopReasonVerified:false`, without claiming termination proof.
- `w5h_bounded_model_repair.mjs`: safely binds `phase:design` or `phase:css` to the corresponding request. A provider-level length-stop error is **not repaired or retried**; only preexisting allowlisted W5-H schema/CSS validation categories receive the single sanitized repair. Exposes the response's reported stop status and bounded phase tokens.
- `w5c_local_code_trial.mjs`: accepted held-out site receipts now disclose per-phase limits, reported `done_reason`, reported `eval_count` when present, and whether stop metadata was available. **No new production or authorship authority** is recorded.
- `test_w5i_inference_completion.mjs`: nine offline test groups covering normal/legacy Ollama envelopes, unknown phase refusal before network access, explicit truncated result rejection, token counts over budget, strict no-retry on truncation and all three mock held-out cases.
- `.github/workflows/runtime-e6-w5i-local-inference-completion.yml`: exact-head read-only CI re-runs all inherited W1–W5-H tests, locked Next static build and 69 inherited actual-browser tests. W5-I tests use **human-authored fake responses only**. No Ollama installation/model invocation.

## Limits and decision gates

Longer design generation can increase CPU time and memory consumption when a **future real-model run is separately approved**, but 180s per-request timeout, 48KB provider response cap and max two attempts per phase remain. This is a *capability and correctness improvement*, **not tested successful real-model website generation**.

The W5-D owner-label cloud CPU smoke remains *separate*, single-response only, explicitly authorized if ever triggered, and does not prove all three actual held-out websites. Physical GPU/thermal tests, expert visual quality, human screen-reader and full WCAG evaluation, Runtime auth #23, Platform auth #251, E6 Task7/8 and Stage45 nine attestations remain outstanding. No branch changes to protected main, PR merges or production deploys.

**After W5-I passes:** the remaining meaningful evidence gap is genuine licensed model inference on an authorized CPU/cloud runner and human-quality evaluation; do not substitute more simulated-browser passes for that evidence.

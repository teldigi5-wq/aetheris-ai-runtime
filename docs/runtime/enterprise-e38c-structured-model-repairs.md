# E3.8C — Diagnose genuine Hermes + Qwen repair failures; test structured output without loosening the validator

**Draft, test-only, entirely disposable GitHub-hosted CPU execution.** Protected `main`, actual owner PC, positive human approvals, cloud inference and production remain **NO_GO**.

## Grounded diagnosis — recovered raw real-model artifacts (not mocked)

Baseline files: [Hermes E3.8B run 39357083133](https://github.com/teldigi5-wq/aetheris-ai-runtime/actions/runs/39357083133), artifact `e38b-hermes-3b-three-case-real-inference-evidence`, ID `12235717225`; [Qwen E3.5 run 39348692647](https://github.com/teldigi5-wq/aetheris-ai-runtime/actions/runs/39348692647), artifact `e35-qwen-1p5b-three-case-real-inference-evidence`, ID `12231783776`. Those are bounded **real** local inference responses, not manually fabricated fixtures.

| Case | Hermes3 3B real response | Independent result | Qwen2.5-Coder 1.5B prior response |
|---|---|---|---|
| Scroll | Proposed to replace a two-line span so that normalized progress becomes 24 instead of a live fraction | Policy **REJECTED**; incorrect semantics | Modified `Math.min/Math.max` clamping formula with an unsafe multiplier; **REJECTED** |
| Mobile | Proposed replacing `min-width:940px` with `min-width:390px` | Policy **REJECTED**; still forces a root minimum width | Selected an unrelated footer style; **INVALID** text length/format |
| Reduced motion | Returned invalid JSON keys `old:` and `new:`, and invented `window.deviceMotion` | **INVALID**, no patch | Proposed `Boolean(false)` → `false`, retaining a constant false condition; **REJECTED** |

These are **real code-suggestion errors**, not infrastructure failures. Changing the policy to accept them would reduce correctness. Therefore **E3.3 independent category grammar and canonical SHA256 remain unchanged**. These examples inform *general* semantic guidance; neither fixture's original replacement text nor evaluator case table is fed to the model.

## Implemented actual E3.8C improvement

- New `e38c_structured_inference.py` is adapted from the exact-head E3.8B genuine Hermes3B CPU trial. It uses Ollama's **structured JSON schema** instead of a loose JSON-only format, with `operation` constrained to `replace` or `abstain` and `old`/`new` bounded to 160 characters; extra properties prohibited. The separate `e34_model_adviser.strict_json` parser remains the final strict JSON shape check. **Schema assistance does not guarantee correctness or eliminate model hallucinations.**
- The revised prompt explicitly limits a suggestion to one contiguous exact source span and accepts `new=""` for deleting an invalid CSS rule. General browser-domain guidance says scroll progress should remain live/normalized, fixed root widths do not provide fluid mobile layouts, and reduced-motion preferences must use existing in-scope media-query state. It never supplies the evaluator's canonical expected replacement line or known-good source SHA256.
- `PROMPT_VERSION=E38C_SEMANTIC_NO_GOLDEN_PATCH_V1` and SHA256 of the structured schema are retained with each actual inference. No retries (`retryCount=0`), no model self-selection, no auto-install, no remote paid calls.
- `e38c_evaluator.py` is a separate unmodified-equivalent adaptation of the E3.8B **browser-only** scorer. It credits a model repair ONLY when the independent network-disabled E3.3/E3.4A patch grammar and SHA256 pass, followed by the real Chromium browser after-test. Invalid, abstained, policy-rejected, and fixture-restored outcomes are failures, not successful AI code repairs.
- New Ubuntu/Windows tests verify prompt generality, absence of literal expected replacement lines in representative excerpts, strong input/hash/model provenance, explicit opt-in, strict schema, malicious extra fields, duplicate JSON keys and no direct execution. Java suite, browser QA and owner-denial gate are required.
- The same publicly checked-in `scroll-24`, `mobile-940`, `reduced-boolean` variants permit comparison with E3.8B. This is an **exploratory, tiny nonblind benchmark**, not independent statistically meaningful software engineering performance evidence.

## Test environment

The disposable CI runner pulls **real `hermes3:3b`** weights (published Ollama package about 2 GB) into an ephemeral Ollama container. The model service is fixed at localhost:11434, with inspected **2 CPU, 5 GiB RAM cap, 128 PIDs**, read-only root, no added capabilities, no new privileges. Its weights are downloaded to an ephemeral volume. Any patch application is in the **separate `--network none`, fixed-path, read-only-root, 1 CPU, 256 MiB** E3 worker. Real Chromium browser QA is outside that patch container. A Docker `stats --no-stream` snapshot is **not peak memory usage**. All model prompts are synthetic test code, not owner secrets.

## Acceptance

The real E3.8C result is **unknown before the new exact-head CI run**. It may be 0/3. Compare real attempts, model outputs, schema/abstention classes, policy-rejected count, independent browser-verified count, wall time, self-reported generated tokens and reported model digest against E3.8B's **0/3**. Do not count CI green as model success.

No change in agency: 13 proposed companies/100 proposed divisions, 26 configured agents, 20 candidate local tags and six free-cloud routes remain planning/registry metadata. Hermes Agent framework, owner-PC actions, real owner authentication, production and Stage45's 13 release checks are still not activated.

**Next milestone:** Build an independent, new source-fault family (not one of the known six fixtures) with actual Chromium evidence, strict scope and reproducible metric history; then trial a second small language model only after consent and measured hardware fit.

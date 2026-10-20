# Aetheris E3.9F — original transcripts, typed evidence and withheld-set readiness

**Status: draft, experimental, no production or Stage45 certification.**
Stacked on draft PR #150 commit `891e7059b47c46a9b46843efdade1d5d2a4dee3d`.
The baseline run is [39369258085](https://github.com/teldigi5-wq/aetheris-ai-runtime/actions/runs/39369258085)
(7/7 CI jobs passed). No owner's device was used.

## Baseline: actual, not extrapolated

The source-of-truth ZIP artifacts, downloaded and inspected on 20 October 2026,
are pinned by **exact ZIP SHA256** in `e39f_failure_taxonomy.py`:
- Hermes3 3B: artifact 12240847334, eight real Ollama calls, 0/8 browser-verified, four abstentions, four rejected; 129 output tokens, 168268ms total calls.
- Granite4 3B: artifact 12240632502, eight real Ollama calls, 1/8 browser-verified (`opacity-zero`), seven rejected; 173 output tokens, 169944ms total calls.
- Each artifact includes raw response, before-Chromium evidence for mobile and desktop, score, cleanup, SHA256 receipts and model details. Cleanup is never credited as a model repair.

| Case | Hermes raw response diagnosis | Granite raw response diagnosis |
| --- | --- | --- |
| top-40 | abstain | `top=-40`: E39E_CSS_PIXEL_VALUE_OUT_OF_BOUND |
| top-120 | abstain | `top=-120`: E39E_CSS_PIXEL_VALUE_OUT_OF_BOUND |
| left-120 | `left=-120px`: E39E_CSS_PIXEL_VALUE_OUT_OF_BOUND | `left=-120`: E39E_CSS_PIXEL_VALUE_OUT_OF_BOUND |
| left-240 | `left=-390px`: E39E_CSS_PIXEL_VALUE_OUT_OF_BOUND | `left=-240`: E39E_CSS_PIXEL_VALUE_OUT_OF_BOUND |
| opacity-zero | abstain | `opacity=1`: E39F_REAL_CHROMIUM_VERIFIED |
| visibility-hidden | abstain | `opacity=0`: E39E_WRONG_DECLARATION_FOR_BROWSER_DEFECT |
| transform-up | `translateY(0)`: E39E_FOCUS_TRANSFORM_INVALID | `top=12px`: E39E_WRONG_DECLARATION_FOR_BROWSER_DEFECT |
| transform-left | `translateX(-150px)`: E39E_FOCUS_TRANSFORM_INVALID | `top=12`: E39E_WRONG_DECLARATION_FOR_BROWSER_DEFECT |

All 16 responses are syntactically valid JSON. Zero invalid syntax, but 11
validator refusals and four correctly counted abstentions. Model quality has
NOT improved just because output was syntactically structured.

## This slice's actual implementation

1. `e39f_failure_taxonomy.py`: read-only offline replay of the 16 original
   model transcripts, requiring the two exact GitHub ZIP digests and source,
   before-browser and response SHA256s. Uses the **unchanged independent
   E39E validator**, and fails on false browser repair credit. No patches run.
   Example: `python tools/enterprise/e3_factory/e39f_failure_taxonomy.py
   hermes-original.zip granite-original.zip > audited-baseline.json`.
   Historical artifacts expire 3 November 2026; retain digest-verified copies
   externally if reproducibility after retention is required.
2. `e39f_typed_observation.py`: strictly validates a single
   `.skip-link:focus` rule, numeric geometry and enumerated computed styles
   for both Chromium viewport measurements. Rejects arbitrary browser or CSS
   instruction strings, NaN, duplicate declarations and extra selectors.
3. Genuine model adviser now receives these typed observations and the exact
   defective focused rule, not arbitrary nearby CSS text or the case label.
   New prompt version is bound at the **independent** policy boundary; it is
   a distinct experiment, not a retroactive reinterpretation of the V1 run.
4. `e39e_model_policy.py` adds deterministic sanitized refusal `reasonCode`
   to every score, rechecking the same restricted value and selector rules.
   Neither approval scope nor allowed values change.
5. Windows and Ubuntu adversarial tests, two genuine opt-in disposable CPU
   Ollama model runners, independent network-none Docker patch worker,
   original-site Chromium QA, full Java regression and Stage45 NO_GO guard
   remain in the existing workflow.

## What is deliberately NOT certified or delivered by this slice

**The E3.9D eight published cases are not a sealed/independently withheld
benchmark.** Their exact defect families and permissible values are in the
public repository. Typed prompting on those same cases is a legitimate
paired protocol comparison, *not* a blinded evaluation. E3.9F cannot claim
sealed generalization until an independently curated/private seeded
multi-domain corpus with no model-side oracle access is actually executed
and checked by real Chromium. Any future results must show that distinction.

Other blockers: private seeded challenge provenance and immutable evaluator,
attack/refusal test corpus, sample-size and repeated-run uncertainty, CPU
budget for stronger models, explicit model license review and Stage45's
separate unresolved owner/production certification.

### Future model candidates — only evaluated after the held-out protocol

- `hermes3:8b` — a candidate comparison, not yet downloaded or benchmarked.
- `granite4:7b-a1b-h` — candidate hybrid MoE profile, compatibility and
  resource needs must be verified on disposable CI before making claims.
- `qwen3-coder:30b` — Ollama lists a ~19 GB quantized download; this exceeds
  the existing 5 GiB CPU model container, so it is **not** added to this CI
  matrix or advertised as an on-GPU 16 GB solution.
- A smaller Qwen coder/instruct variant can be shortlisted separately after
  confirming release/license/tag and deterministic resource caps.
No mandatory paid APIs, no owner's laptop, no owner identity attestations,
no autonomous companies, no financial trading, no merge and no deployment.

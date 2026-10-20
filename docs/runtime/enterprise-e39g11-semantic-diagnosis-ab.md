# E3.9G-11 — genuine local semantic diagnosis -> proposal versus targeted direct repair

**DRAFT, unmerged**, stacked on [PR #166](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/166), exact base `ceecec77b781ef94b7ce28923a93aa356f21cffa`; tracks [issue #167](https://github.com/teldigi5-wq/aetheris-ai-runtime/issues/167). Protected main unchanged. Stage45 **NO_GO**.

## Motivation

G9 performed 12 genuine local model inferences and found 0/3 repairs on each prompt arm for each model. G10 source-targeting made 12 more real inferences and often selected the right existing source line, but generated wrong semantics (e.g. increased minimum mobile width, changed a motion-preference toggle to another constant, changed stuck scroll progress to another constant). **No website repair gain was demonstrated**.

This controlled E3.9G-11 experiment tests a different hypothesis: that requiring a model to first state observed **root cause and required browser behavior in bounded natural language** may improve its subsequent replacement semantics.

## Paired trial and counts

Two local, genuine CPU Ollama model tags: `granite4:3b`, `hermes3:3b`. Three **public synthetic pre-existing functional faults** on one disposable generated site: `mobile-940`, `reduced-boolean`, `scroll-24`. Both arms get the exact same four pinned, hash-checked low-trust website-design/accessibility/performance skills and real pre-repair Chromium mobile/desktop observations.

- `targeted`: original exact E3.9G-10 source-target prompt, **one genuine Ollama model call** per task.
- `semantic`: first a new, independent bounded JSON diagnosis call containing `rootCause` and `desiredBehavior` only (up to 220 chars each); then a separately counted Ollama proposal call with **same targeted prompt + untrusted diagnosed behavior**. **Two genuine Ollama calls** per task; diagnosis cannot authorize tools or patch the site.

The experiment requests **18 actual local model calls** in total if the GitHub Actions model jobs complete: 2 models × 3 cases × (1 targeted + 2 semantic). The receipt stores every proposal response digest, prompt digest, diagnosis and token counts and separately itemizes the extra diagnosis calls. There is no advantage granted for more calls without measured browser repairs. Time/resource cost is higher in the semantic arm; do not compare raw repair scores as if compute budgets were equal.

Both arms use the identical fixed JSON literal-replace output grammar, temperature zero, reviewed OCI Ollama container, CPU/RAM/PID budgets, loopback-only access, explicit opt-in `AETHERIS_E39G11_REAL_DIAGNOSIS_EXPLICIT_OPT_IN=yes`, and identical known synthetic browser faults. One case reverses order.

## Security and actual independent evaluator

Crucially, **the original E3.9G-9 `e39g9_multidomain_worker.permitted` is UNMODIFIED**. All proposal text is untrusted. A new `e39g11_diagnosis_gate.py` calls the same frozen one-literal syntax, exact canonical-byte postcondition and a model receipt verifier, in the same locked **`--network none`** Docker worker. It permits **ONLY** the original reviewed source replacement in the throwaway `target/e3-factory/site`; all other model-generated edits are denied. It has no API route, no owner credentials, and does not execute model-supplied code.

An independent `e39g11_diagnosis_browser.mjs` checks real web page overflow, reduced-motion behavior and scroll responsiveness on real Chromium **390×844 and 1280×800** after **only** an approved model patch. The independently sourced scorer checks both actual screenshot/source SHA256s and original source policy, credits model repair **ONLY** when the model's proposal was applied and **both browser after verdicts passed**. Rejected responses receive zero browser-repair credit and no after-model patch browser run. Deterministic resets between arms and final clean fixture restoration are explicitly non-model work.

Windows+Ubuntu adversarial unit tests reject duplicate-key diagnosis JSON, out-of-scope file/model/arm, injected tools/JS, wrong static motion behavior and bad signatures/skill provenance. The full Java 21 regression and existing model/skill policy checks run in the same exact-head PR CI.

## Remaining shortcomings

This is **not an independently curated hidden multi-project benchmark**. Models have only small public synthetic instances and may memorize patterns. There is no basis for statistical significance, broad semantic understanding, visual design/style excellence, advanced UI performance, AppSec coverage or autonomous use on the owner's real projects. More free coding models and cost-matched treatment trials are separate planned work.

**No production-site changes, human-owner authentication, paid remote model, privileged tool calling, main merge, Stage45 certification or live trading**. Model diagnostics are lower-trust data and not authority.

No model-result scores should be asserted until exact-head GitHub Actions completes.

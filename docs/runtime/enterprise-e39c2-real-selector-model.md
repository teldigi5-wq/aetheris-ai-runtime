# E3.9C-2A — Genuine Hermes and Granite selector-aware CSS advice, strict offline policy

**DRAFT. This adds a real-model trial of the new E3.9C-1 selector repair boundary on the EXISTING one synthetic keyboard defect. It does not yet deliver 8–12 independently held-out faults.** No owner-PC use, protected `main` changes, production, paid API, real owner grants, or Stage45 release.

## Grounded baseline

E3.9B genuine `hermes3:3b` and `granite4:3b` both produced one text repair on the offscreen focused skip link. **Both were rejected and earned 0/1 actual browser-verified repairs**. Granite's `old="top:-80px;"` selected the *hidden-at-rest* selector instead of the focused-link rule. Its rejection was correct. E3.9C-1 subsequently proved that the reviewed **focused** selector `.skip-link:focus{top:0px}` and original `top:12px` both restore keyboard visibility at 390px mobile and 1280px desktop, through deterministic fixtures **without model calls**.

## Implemented E3.9C-2A

- `e39c2_selector_adviser.py` emits **one genuine, consented local Ollama request** to `hermes3:3b` or `granite4:3b` per separately isolated runner. Requires `AETHERIS_E39C2_REAL_MODEL_CI_OPT_IN=yes`, verifies the actual installed local model digest and size, and uses a bounded real before-browser-observed CSS source excerpt. It **never imports the evaluator, gold patch or independent policy** and cannot access a tool API.
- New schema is **structured selection, not raw source string replacement**: either `{"operation":"abstain"}`, or `{"operation":"replace","selector":".skip-link:focus","property":"top","topPx":12}` (illustrative response, *not* a supplied gold value). The model is instructed to choose an on-screen nonnegative offset; independent validator is the only source of security policy.
- The model response is untrusted: exact JSON duplicate-key rejection, no owner/command/path fields, no booleans disguised as integers. Raw model output, SHA256 of prompt/source/before-browser/response, prompt schema/version, self-reported model digest, real tokens and elapsed wall-time are recorded. A model may abstain or fail strict formatting.
- `e39c2_model_policy.py` runs **only in the existing network-none Docker e39c2-apply worker**. It parses the response independently, confirms selected focused selector, computes one full-rule replacement, invokes the previously verified `e39c_selector_policy.candidate_result` to require the exact broken source, manifest SHA256, reviewed value 0–24px and immutable unrelated stylesheet bytes. **No model can issue shell, file paths or GitHub actions.**
- `e39c2_score.py` credits a model repair only if **both actual Chromium focused-link after-tests pass** (390px mobile and 1280px desktop) plus the source and receipt checks. Rejected, invalid and abstained requests never change the original broken site. Browser errors or off-screen links do not receive repair credit.
- Ubuntu and Windows mocked-envelope negative unit tests (not counted as inference), full Java regression, pristine original CSS3D website Chromium check, and separate no-owner/no-production/Stage45 gate are required.

## True experimental limits

The two actual model trials target one **previously published synthetic defect**, not a blinded or representative codebase. Earlier genuine Hermes E3.8C result of **1/3** browser-verified repairs was on different categories and cannot be directly compared as an overall model ranking. CI matrix green means experiment completed securely, not that either model repaired the fault. **Actual E3.9C-2A model repair results are pending exact-head real CI and inspection**.

This stage does not add new trained weights or model routing entries: Aetheris still has **20 local model candidates, 5 specifically named free-cloud model candidates and 1 free-model selector, plus 12 separate future research references**. Proposed 13 companies /100 divisions and 26 catalog agents remain metadata, not executing autonomous workers.

## Next increment E3.9C-2B

Implement additional truly independent browser-verified keyboard/responsive defects and an evaluator withheld from the model prompt, a comprehensive source-span/selector parser, adversarial prompt injection trials and honest multiple-case model comparisons before asserting engineering competency. Real owner-PC or production activation stays **NO_GO**.

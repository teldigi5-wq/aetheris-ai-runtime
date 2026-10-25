# E3.9G-12B4H — Private Evaluator Model Proposal Admission

**Draft protocol, 25 October 2026.** No real new model completions, independent human-curated faults, applied code, security approval, Chromium after-state or production certification.

## Verified continuation
The E3.9G-12B4D eight-call Qwen2.5-Coder 7B trial produced **0/8 accepted repairs** on known synthetic fixtures. B4E/F classified failures. B4G separates broken-only model input from private reference solutions using a salted commitment outside the repository. B4H adds the next independent EVALUATOR-side input boundary.

## Fixed trust boundaries
1. Curator keeps `manifest.json`, per-case `broken.html`, `reference.html` and the PRIVATE sealed hash file in a separate directory *outside* this repo. Neither local model nor model-hosted CI job can access these files.
2. B4G publishes only its single-member broken-source ZIP, salt-free sealed commitment, source identifiers and SHA digests. `B4H` independently calls `B4G.verify` to verify the current curator seal *before* inspecting model output.
3. Model outputs are **untrusted receipt claims**, not proof that inference occurred. A separate JSON envelope must contain exact `evidenceClass`, original bundle SHA-256, human-readable unverified model tag, count and one row per case. Each row has exact case ID, original SHA, raw JSON model response, raw-response SHA; duplicate/unknown cases or forged owner permissions abort.
4. Allowed response format for quote *inspection* is the B2-style six-field proposal: `site`, `fault`, `operation`, `old`, `new`, `sourceSha256`. The engine checks exact case/defect/source hash, unique existing old literal, bounded single-line new literal, and that `operation=replace_one`. Abstentions and invalid responses count as failures, not accepted fixes.
5. **CRITICAL:** even a quote-matching response is classified **`QUOTE_MATCH_REVIEW_REQUIRED`** — NEVER accepted, applied or browser-tested by this slice. An arbitrary `new` value may still be malicious JavaScript, ineffective CSS, an invalid access-control change or broken HTML. Only a future independent site-specific typed policy, sandboxed application and real Chromium post-state may certify a model repair.

## CLI — local-only, after real separate curation exists

```bash
python tools/enterprise/e3_factory/e39g12b4h_proposal_admission.py admit \
  /private-curator /tmp/b4g-public-model.zip /tmp/untrusted-model-replies.json \
  /tmp/b4h-admission-only.json
```

Private curator folder must remain outside the repository; the source-only bundle, untrusted model replies and report must also stay outside the curator and git checkout. Admission report contains **only case identifiers and refusal categories**, not original source or model reply text. Report is created with exclusive 0600 mode and cannot overwrite previous results. The program does not run shell commands, JavaScript, HTML, model inference, package installers, external websites, browser accounts or filesystem source mutations.

## Threat model and adversarial CI
Tests cover unauthorized owner flag, synthetic API key/secret contamination boundaries, duplicated/unknown case IDs, missing/duplicate JSON keys, forged raw output/source digests, reference-salt tamper, non-unique or mismatched quotes, harmless abstentions, and the important case where an evil `new="<script>..."` still receives **no execution or approval**. The latter only qualifies for further manual review and must be rejected by the future typed expression policy.

Cross-platform CI checks are limited to **offline synthetic fixtures** and B2/B4G provenance regressions. Passing CI certifies these exact guardrail tests, NOT external curator independence, successful repair correctness or installed AI autonomy.

## Next actual development
- Obtain human-independent, licensed, originally withheld fault cases and independent reference evaluation without exposing any golden answers to model prompts.
- Create separately reviewed per-fault typed rules and real mobile/desktop Chromium after-state for this new domain. Do not reuse E3.9G-12B2 fixed two-site rules on incompatible new cases.
- Run real local code models under no-checkout resource boundaries with every attempt counted, and score browser-verified model-authored repairs.
- Keep Stage45 13 blockers, nine release gates, and E4–E9 as incomplete until their separate acceptance evidence exists.

**Protected main, owner-PC devices, hosted customers, deployments, payments, live money and model-tool permissions are unchanged.**

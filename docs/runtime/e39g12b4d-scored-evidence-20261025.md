# E3.9G-12B4D — Exact genuine-model paired A/B result

**Evidence:** [GitHub Actions run #40157740781](https://github.com/teldigi5-wq/aetheris-ai-runtime/actions/runs/40157740781), source commit `24a9db93989c3cb0400cdbd1c97e4b030aa5f235`, 25 October 2026. Six jobs succeeded including Windows/Ubuntu adversarial contract checks, Java regressions, source-only prompt isolation, eight genuine local Qwen2.5-Coder 7B model calls and independently executed network-none source policy/scoring.

## Real results — do not credit CI green as AI success

| Arm | Genuine model responses | Frozen B2 policy-accepted source edits | Browser-verified AI repairs |
| --- | ---: | ---: | ---: |
| Original (full broken source) | 4 | **0** | **0/4** |
| Observed existing source span (no repair answer) | 4 | **0** | **0/4** |
| **Total** | **8** | **0** | **0/8** |

Every model-proposed change was rejected by the existing unchanged one-literal patch policy. No changed model-authored website was permitted to run an after-repair browser evaluation. The negative outcome is an observed model limitation, not an evaluator exception or proof that the entire application is broken.

CI output marker `B4D_INDEPENDENT_TWO_ARM_RESULTS` confirms `improvementVerified:false`, `modelOutputExecuted:false`, `realAfterBrowserViewports:0` for both arms, `genuinelyHeldOut:false`, `stage45Go:false` and `autonomousWebsiteFactoryCertified:false`. The accepted 8-call self-reported model receipt SHA256 was `0a00cc5ce9acadfe9867e850670783154526a689ed33dc78d7aa354a23aba998`; the source-only prompt ZIP SHA256 was `76f6f7f2c58a0e48a4d41bf25277c4f5a6657e582134ecbca33cebdfc096a321`. These are integrity bindings, not signed supply-chain or model attestation.

## Meaning and next research

Merely pointing Qwen2.5-Coder 7B at the exact defective declaration did **not** improve source target and safe repair generation across four known developer-authored site defects. Do not relax `e39g12b2_typed_policy.validate`, invent a benchmark win, credit golden restores or declare E3.9G complete. Next investigate error taxonomy per raw response, exact byte-span selection and constrained output mechanics; then run independently curated, larger, less-public fault corpora, multiple repeats/models and real Chrome after-state. Separate source targeting from actual semantic correctness. Keep all owner/agent privileges disabled until release gates pass.

**Not a production release:** no authorized host actions, real workstation model, owner sessions, external websites or independent held-out generalization. Stage45 and nine release gates remain NO_GO. This evidence note contains no code or benchmark-policy changes and should not consume another 8-call inference run merely to record an already verified result.

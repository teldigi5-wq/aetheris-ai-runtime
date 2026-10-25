# E3.9G-12B4D — genuine paired source-span vs original coding-model trial

**Status:** New isolated draft development on 25 October 2026. No production, customer website, owner workstation, privileged agent, live money or Stage45/46 activation. All claims of real-model improvement must come from exact-head successful CI and its independently verified artifacts, never the presence of a workflow.

## Continuation point and why this is needed

The existing [E3.9G-12B4A](enterprise-e39g12b4a-qwen7b-pilot.md) Apache 2.0 Qwen2.5-Coder 7B pilot made four genuine calls and measured **0/4 policy-approved and 0/4 browser-verified** repairs. [E3.9G-12B4B](enterprise-e39g12b4b-repair-failure-analysis.md) determined that the model's four JSON replies targeted the wrong existing source text, and built deterministic source-only observations **without replacement answers**. [B4C](enterprise-e39g12b4c-gstack-cinematic-readiness.md) introduced a cinematic readiness NO_GO. E3.9H now has a separate original WebGL2 browser positive control [PR #187](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/187), but no autonomous model-authored 3D site.

**This slice actually compares** original-vs-existing-source-span guidance within the *same* genuine local-model run, while preserving all original B2 typed source guard behavior. Neither the model nor the proposer job has the evaluator's solutions.

## Pre-registered benchmark design

| Constraint | Implementation |
| --- | --- |
| Model | Reviewed Ollama `qwen2.5-coder:7b` (Apache 2.0). Fixed Q4_K_M published digest prefix `dae161e27b0e` is checked at runtime, but is not a signed independent model provenance proof |
| Corpus | Same two original **public developer-authored synthetic** sites, four known faults (keyboard skip focus, mobile overflow, reduced motion, unsafe HTML sink). NOT a blind external benchmark |
| Treatments | `original` exact defective source and fault; `source-span` adds one independently extracted existing source span, **no proposed replacement**. Otherwise same prompt, model, seed, tokenizer, temperature 0, JSON format, thread and context budget |
| Assignment | Four within-task pairs (8 total real model calls) with frozen ABBA pair ordering, reducing but not eliminating warmup/cache/order effects |
| Isolation | Source/hints generated under source-checked GitHub runner, exported as a **single-member prompt ZIP**. True model inference job has **NO checkout, golden or evaluator** and a disposable fixed image with loopback-only Ollama, 4 CPUs, 10GiB, PID 128, no capabilities, read-only container root. Weights removed after trial |
| Model response | Every attempted reply, prompt-response SHA256, source hash, token count and wall-clock is recorded as **untrusted** evidence, not proof of signature, owner consent or tool authorization |
| Approval gate | Independent checked-out evaluator **reconstructs the exact ZIP** and validates every prompt/receipt, then adapts only source-safe replies into the existing `e39g12b4a_policy_worker.py` compatibility structure. **The original E3.9G-12B2 `frozen.validate` one-literal typed policy is unchanged** |
| Browser | Only policy-applied changes are served to Playwright Chromium at 390x844 and 1280x800, original keyboard/layout/reduced-motion/input-sanitization assertions, no custom model checker. Rejections earn zero verified browser repairs, not synthetic-success screenshots |
| Audit | Per-arm existing B4A independent scorer and a separate two-arm summary (accepted patches, genuine after-Chromium repairs, viewport counts), raw artifacts and source digests. Failures remain visible; no score floor or fixture tuning |

## Security/provenance and truth boundaries

- The model is a proposer, not a tool-authorizing agent. No shell command, browser, package installer, worktree checkout, secrets, client project or production API is accessible in the no-checkout model step.
- The evaluator runs the **same** disposable `run_locked_worker.sh e39g12b4a-apply` network-none, no-new-privileges typed patcher for accepted/rejected proposals and verifies source hashes and real after-browser evidence. It does not promote the model's decisions to authority.
- The **two public known websites do not prove genuine held-out generalization**. Even an apparent improvement here is task-specific, small-n and vulnerable to model prior familiarity or prompt-order effects. A genuinely independent corpus and repeated samples remain required to close [issue #169](https://github.com/teldigi5-wq/aetheris-ai-runtime/issues/169).
- GitHub Actions success can mean the sandbox and scorer passed while model repair quality remained 0/4 per arm. Report both outcomes separately, never as 'agent passed 100%'.
- Local-first and no required paid model API, but GitHub Actions minutes and runner model downloads can consume repository quotas. Confirm the account's free allowance before making this a recurring benchmark.
- Do not merge ancestors independently or promote protected `main`; development branch stacks on PR #187 and #186, themselves stacked on runtime security PR #185. Owner-PC status `BLOCKED_PENDING_HARDWARE`; Stage45 13 safety blockers and nine release gates remain unclosed.

## Definition of done for this narrow experiment

1. Exact source/head CI passes Windows/Ubuntu prompt/authority policy tests, model no-checkout actual inference, trusted two-arm network-none typed patch+browser scoring and Java regression.
2. Model eight genuine generations, digests, attempts, token/time counts and whether actual policy-accepted browser repairs improved are in inspected downloadable CI artifacts.
3. Correctly report any zero-result, failures, external runner/weight download errors or inconclusive evidence. Never mark B4D complete from pre-registration only.
4. Next: enlarge independent held-out corpus; separate source locator from author/tester; benchmark better freely licensed model candidates under hardware budgets. E3.9H genuine model-generated cinematic 3D is a later independent capability gate.

**NO_GO:** unattended engineering worker, runtime host commands, commercial design release, owner PC activation, autonomous external browsing, production changes, platform security promotion, unrestricted tools and real-money use.

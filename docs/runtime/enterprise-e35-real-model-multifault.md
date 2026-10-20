# E3.5 — Real 1.5B CPU code-model advice on three browser-observed website defects

**DRAFT / CI-ONLY / NO owner PC or production. This document describes the proposed test. Actual outcomes must be inserted from an exact-head GitHub Actions run, not inferred from the workflow.**

## Why this increment exists

E3.4B PR #126 logged three **genuine** CPU inference requests across SmolLM2 135M and Qwen2.5-Coder 0.5B. All yielded unacceptable output or the wrong fix: **zero of three** browser-confirmed repairs. GitHub Actions passed its security and evidence checks, not model programming ability. E3.5 is a scoped follow-on trial with a somewhat stronger local model, not a fake “agent” launch.

## Actually implemented

- `e35_real_model_trial.py` is a strictly **explicit-opt-in** stdlib Python adapter for the official Ollama `qwen2.5-coder:1.5b` model. The 1.5B quantized tag is approximately **986 MB** per Ollama's published model library. It accepts only the three trial IDs `scroll-24`, `mobile-940`, `reduced-boolean`. It independently reads the actual current generated site and observed Chromium failure via the existing strict E3.3 scope validator. The evaluator's test-case replacements or canonical-good files are **not imported** into the model prompt construction.
- On a disposable Ubuntu CI runner the workflow pulls an already previously verified pinned Ollama Docker image, starts a real CPU Ollama service bound to **127.0.0.1**, caps it to 2 CPU cores, 4 GiB RAM, 128 processes, read-only root, no new privileges/capabilities, and pulls public 1.5B weights into an ephemeral model volume. **The model-service container requires outbound access for downloading weights; only the separate patch worker is `--network none`.** GitHub-hosted CI can consume free-plan minutes and network quota; no subscriptions or user-paid API keys are needed.
- Before every inference, a genuine Playwright Chromium run **must observe** the particular seeded error. The model receives a limited source excerpt with real browser observations, not evaluator repair strings. It can return either strict JSON abstention or a single short replacement suggestion. Model responses and self-reported model tag/digest, generated token count, durations, exact hashes, elapsed wall time, and schema outcomes are recorded. Model identity is not cryptographically attested.
- Real text suggestions are **not executed**. They may only be staged by the inherited network-disabled E3.4A Docker `e34-prepare` worker and separately checked/applied by the E3.3 `e33-apply` worker with exact allowlisted paths, source/base/after SHA256, strict category grammar and original canonical file hash. Accepted proposals get an **independent real Chromium after-test**. Failed, refused, abstained, malformed, and successful cases are counted separately.
- An evaluator saves one report per case and verifies the source was never modified by a refused proposal. For a failed case, it may reset the artificial defect back to the pre-verified site solely to run the next independent scenario; **this fixture cleanup is explicitly NOT credited as model repair**.
- End-of-job browser QA independently retests scroll, reverse scroll, mobile viewport, reduced motion, browser console, and exact generated-source checksums.

## Honest evaluation design

The three cases are purposefully selected from the earlier checked-in E3.3 evaluator. The model never sees the fixture table, but **this is not a third-party, genuinely blind, unseen-real-world benchmark**. We do not alter the E3.3 policy to accept arbitrary generated code merely to improve the model success number. A result of zero approved fixes is valid security evidence and must remain recorded as zero.

One model is tested on three artificially seeded cases. Even 3/3 would **not** imply general autonomous software-development competence. The model API's wall-clock and token fields are measured/reported, not guaranteed hardware-level timing promises or payment prices.

## Required checks

1. Ubuntu and Windows adversarial unit tests: explicit opt-in, single localhost transport, fixed case names, observed browser fault consistency, public model digest/size sanity and no privileged operations.
2. Actual CI CPU inference **three times** with nonzero returned token counts and model metadata, **without mock inference fallback**.
3. Independent Chromium before/after tests; deny all unauthorized or wrong patches, retain artifact evidence including malformed answers and refused proposals.
4. Inspect model Docker resource settings. Keep website build/patch in the existing `--network none`, one CPU, 256 MiB, fixed-writable-target worker. Never run a generated shell command.
5. Full Java orchestrator regression and independent static no-activation check.
6. Review exact-head PR before marking verified. Do not merge to protected `main` or deploy.

## Explicit outstanding security blockers

This is **not** owner authentication, trusted key enrollment, action-specific positive grant, unsupervised production autonomy, a completed Blender/Three.js/GSAP website, or physical Windows PC certification. Stage45's true 13-blocker release suite is absent/unexecuted on these stacked PRs and remains **NO_GO**. The 13-company/100-division proposal is still registry metadata, not dozens of operating AI workers.

## Further roadmap

After this experiment, improve policy-scoped *independently verified* patch coverage for other bug families and design a genuinely blind benchmark owned by a separate evaluator. Compare baseline model configurations with honest failure accounting, then introduce local-model routing and larger cinematic web production only inside separately certified isolated environments.

Stack: E0 #102 → E1 #104 → E2.1 #106 → E2.2A #107 → #109 → #110 → #112 → #113 → #114 → E3.0 #116 → E3.1 #118 → E3.2 #120 → E3.3 #122 → E3.4A #124 → E3.4B #126 → **this E3.5 draft**.

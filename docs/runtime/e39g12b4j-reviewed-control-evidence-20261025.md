# E3.9G-12B4J — Reviewed literal patch, network-none worker and real browser proof

**25 October 2026 — source-controlled synthetic research fixture only, NOT an AI repair benchmark or production release.**

## What this actually advances
Prior B4I PR #193 demonstrated that real Chromium distinguishes broken/fixed keyboard-focus and reduced-motion test pages at mobile/desktop widths. B4J now demonstrates a **controlled end-to-end bridge**: a trusted fixed patch proposal must pass an immutable reviewed source-literal gate, be written inside the existing CPU/RAM/PID-bounded **network-none, read-only Docker worker**, then match the entire known-safe positive fixture byte-for-byte before real Chromium loads it.

The earlier Qwen2.5-Coder 7B eight-call experiment remains **0/8 policy-accepted and browser-verified repairs**. B4J's two proposals are developer-authored trusted test controls, **NOT model outputs or externally held-out faults**; passing browser checks must never increase model quality metrics.

## Scoped implementation
- `tools/enterprise/e3_factory/e39g12b4j_typed_control_worker.py`: only `focus` and `motion` case IDs, exact 5-field JSON, source SHA256 and unique reviewed CSS old/new literals. Final entire HTML bytes must match the normalized reviewed B4I positive HTML. Rejects arbitrary JS/CSS, spoofed hash, unknown sites, extra keys, replay and any reference drift. Creates only fixed names under ephemeral `target/e3-factory/e39g12b4j/repairs/`.
- `run_locked_worker.sh`: one additional allowlisted phase `e39g12b4j-apply focus|motion`; uses EXISTING Docker container with network none, all capabilities dropped, no-new-privileges, read-only repo mount/root, restricted writable target, CPU 1, RAM 256 MB, PID 64 and 45 second timeout. This does **not** generalize to arbitrary websites or grants.
- `tools/enterprise/e3_factory/e39g12b4j_independent_browser.mjs`: before rendering, checks entire output equals reviewed normalized source and SHA-based worker evidence. Then uses actual Chromium at 390×844 and 1280×800 with JavaScript disabled, network blocked and no service workers. Rechecks focus and reduced-motion accessibility behaviors and records four trusted screenshots/hashes. A changed/unsafe HTML byte fails closed BEFORE rendering.
- `test_e39g12b4j_typed_control_worker.py`: reject wrong case, spoofed source, malicious new HTML, extra permissions, duplicate source targets and changed references.
- `runtime-e39g12b4j-network-none-patch.yml`: Ubuntu/Windows source/policy checks plus Ubuntu actual bounded Docker/Chromium integration. Only short-lived synthetic artifacts are uploaded.

## Security and quality limits
A candidate that matches an existing fixed positive reference is not a general semantic correctness engine, because the positive answer is hardcoded and authored by the developer. There are only two trusted fixtures. No model inference, outside curated tasks, real customer website code, user sessions, production deployment, scripts, privileged tools or live money are involved. The immutable B2 policy and B4H quote-admission semantics remain unchanged.

The public GitHub repository necessarily contains all these fixture answers. Therefore **this cannot be scored as a withheld benchmark**.

### Next genuinely independent gate
A human-independent, licensed 4–12 case corpus must first be curated away from the model, and a case-specific reviewed typed policy and separate network-none browser worker must be approved for those new cases. Then actual locally run coding models can be measured against hidden tests (all calls, accepted edits, after-browser evidence, latency, regressions, uncertainty). Stage45's 13 NO_GO items, the nine release gates, owner PC hardware validation, and E4–E9 remain outstanding.

**Status is evidence-limited: success in this PR is controlled-policy-to-browser infrastructure evidence only, not autonomous developer competency.**

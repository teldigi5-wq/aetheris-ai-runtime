# E3.9G-12B4K — Real model-proposed fixes on two public reviewed fixtures

**Development checkpoint: 25 October 2026.** This first integrates genuine model output with the fixed network-disabled worker and Chromium after-state. **Do not infer passing model repairs until this exact PR CI and independently scored evidence have completed.**

## Goal and original baseline

The previous Qwen2.5-Coder 7B eight-call repair benchmark on two synthetic websites gave 0/8 security-policy-accepted/browser-verified repairs. B4J then demonstrated a source-locked network-none patch worker and browser verification with two **human-authored trusted** corrections. B4K tests whether the actual model, not the developer, independently proposes these exact source edits and whether they survive the unchanged strict policy.

Two deliberate limitations:
- The focus and motion problems are **public, checked-in synthetic fixtures**. Even if a model passes, this is **not an independently withheld** benchmark and cannot prove production-ready code generation.
- The accepted edits are narrow, byte-exact reference strings. This tests model response fidelity and the isolated end-to-end path, **not open-ended website generation**.

## Four independent CI stages

| Job | What it verifies | What it does NOT imply |
| --- | --- | --- |
| Windows + Ubuntu policy tests | Original B2/B4J strict literal rules, 14 default-off skills, model input/receipt and injection adversarial cases | No newly demonstrated model competency |
| Public source package | Exact B4I negative HTML only; no B4J rule source or positive references in model artifact | No independent curators |
| No-checkout Qwen2.5-Coder 7B | Reviewed Apache model tag/digest prefix, genuine two inference calls, bounded loopback Ollama, generated-token/latency counts, SHA-linked untrusted responses | No model access to a shell, evaluator, reference answer or owner PC; model digest self-reported, not a signed supply-chain attestation |
| Independent worker/browser | Recreates exact prompt ZIP, checks all model replies, passes only properly formatted edits to B4J's *unchanged* exact CSS rule validator inside restricted Docker, invokes JS-disabled/no-network mobile+desktop Chromium only if accepted | No arbitrary model code, privileges, customer sites or production release |

The runner image is SHA-pinned as in the successful B4D trial, the model is `qwen2.5-coder:7b` with the previously inspected digest prefix `dae161e27b0e`. Inference is kept in a **no-Git-checkout CI job**. The separate evaluator verifies input and output hashes; all attempts, including schema denials and patch-policy denials, count in the denominator.

An exact model-generated proposal can receive a **public synthetic fixture browser-verified** score only if it passes immutable B4J byte-for-byte reviewed HTML checking and real Chromium viewport checks. Zero successful model proposals is a valid experiment outcome; test jobs can be green even when all model proposals are denied.

## Security and authority boundaries

- The model cannot run arbitrary shell scripts, browse websites, download plugins, access owner files, choose fixture paths, or deploy. Untrusted HTML is supplied as read-only data, never as system policy.
- Only `focus` and `motion` can enter `run_locked_worker.sh e39g12b4k-apply`. Docker enforces `--network none`, read-only root/repository, one CPU, 256MB, no Linux capabilities, low PID limit, a narrow ephemeral writable output volume and a hard timeout.
- The patch worker imports the previous `e39g12b4j_typed_control_worker.review` **without weakening it**. Other CSS, HTML, JavaScript, owner permission strings or source mismatches are rejected without creating `index.html`.
- Chromium only receives the entire byte-exact verified reviewed fixture after security validation, with JavaScript disabled, network requests blocked, and zero external navigation or client accounts.
- Model-claimed lineage is recorded as **not independently signed**. The GitHub job log provides inference operation evidence, and a hash-bound receipt is independent input for the evaluator. The project still has no general owner approval or execution authority.

## Acceptance and next steps

1. Run all exact-head CI jobs successfully with real model-call evidence, counted attempts and browser score; preserve failures rather than changing allowlist to manufacture success.
2. Commit a result note with exact attempt counts, policy approvals, real browser confirmations and source SHA identifiers. **Do not label these cases held-out**.
3. Next obtain separately human-curated unseen, licensed repair cases with hidden reference and independent typed rules, and repeat this same secure model-worker-browser process; expand model comparisons with defensible sample size.
4. Only then consider E3 reliability threshold, owner hardware verification, E4–E9 and independent nine-gate production certification.

**Stage45 NO_GO and the unresolved platform security/database/deployment issues remain independent blockers. Protected main and live company/customer websites are not changed by this draft.**

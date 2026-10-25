# E3.9G-12B4N — Bounded CSS alternatives + independent keyboard-focus browser oracle

**25 October 2026. DRAFT.** This is a developer-authored, trusted-public-fixture, deterministic security/quality pipeline. **Zero model calls, zero newly credited AI repairs, no general website-editing rights and no production certification.**

## Verified precondition: B4L + B4M
- [B4L actual Qwen2.5-Coder 7B experiment](e39g12b4l-targeted-model-results-20261025.md): 2 real responses with source-only hints; 1/2 strict-source approved and real-browser verified on two **public** fixtures (reduced-motion). The focus response `.skip:focus{top:0;}` was rejected.
- [B4M real Chromium report](e39g12b4m-focus-indicator-semantics-20261025.md): final GitHub run [40165294091](https://github.com/teldigi5-wq/aetheris-ai-runtime/actions/runs/40165294091), 3/3 jobs passed and 25/25 trusted observations showed that a visible keyboard-focused link at top zero can nevertheless have a **clipped 3px outline + 4px offset**.

The **B4J frozen literal policy is intentionally unchanged**. It continues to accept only `top:16px` for the focus control. Historical B4L outcomes and model denominators are not recalculated.

## B4N architecture: two separate trust gates

1. **Static structural CSS admission.** Only the exact pinned B4I focus-negative source SHA-256 `385d3fac74d775586bb389beb3068e6e34a7fb0e08f8d854c80cb55b433b290b` and old rule `.skip:focus{top:-120px;}` are eligible. `e39g12b4n_focus_semantic_policy.py` requires exact `caseId`, `operation=replace_one`, original `sourceSha256`, `old` and `new` fields. Candidate `new` must match exactly `.skip:focus{top:INTEGERpx;}` with integer 0–64; no other CSS declarations, scripts, URLs, subproperties, file paths, privilege hints or HTML changes are accepted. The exact original source is preserved and the only mutation is a single bounded rule. A structurally eligible candidate is **NOT semantically approved**.
2. **Independent actual browser behavior.** `e39g12b4n_semantic_after_browser.mjs` verifies **the entire candidate file** equals the pinned trusted source with precisely the allowed single CSS replacement *before rendering*. It runs Playwright Chromium with JavaScript disabled, service workers blocked and all network requests aborted. It tests actual keyboard Tab focus, `:focus-visible`, an unclipped outline extending 7px beyond the element, zero horizontal overflow and zero browser page errors at widths 320/390/768/1280/1920. Five trusted variants produce 25 observations.

All output is under ephemeral `target/e3-factory/e39g12b4n`, with short-lived CI evidence screenshots and hashes. Only fixed case IDs `zero`, `six`, `eight`, `sixteen`, `thirtytwo`, `malicious` can be invoked through the **existing** `run_locked_worker.sh` Docker boundary: `--network none`, read-only root/repo, drop all capabilities, restricted writable target, memory/pid/CPU/time limits and no host site access.

## What the control checks
| Synthetic CSS variant | Structural grammar passes? | Expected real browser result | Why |
| --- | --- | --- | --- |
| `top:0px` | yes | **fail** full outline | 7px focus halo clipped |
| `top:6px` | yes | **fail** full outline | 7px focus halo clipped |
| `top:8px` | yes | pass | 1px clearance beyond 7px halo |
| `top:16px` | yes | pass | reviewed B4J positive placement |
| `top:32px` | yes | pass | safe alternative position on controlled fixture |
| Injected CSS URL / extra declaration | **no** | not rendered | grammar/provenance deny |

**Expected results are hypotheses until exact-head CI proves them.** The later GitHub Actions job must show the actual observed values before they are called successful. The browser does not treat a correct source quote as adequate; the intact focus outline matters. An alternative passing in this public fixture does not create general-purpose authority to change client styles.

## Remaining work before real autonomy
1. Obtain independent human-curated, licensed, *not previously exposed* website fault examples; keep reference solutions and evaluator policy private and separate from model inference (issue #169).
2. Review additional site-specific typed CSS rules, accessible keyboard behavior, safe selector boundaries, SVG/WebGL failover, motion and performance metrics in a dedicated evaluator OS boundary. Avoid treating 0–64px as globally safe!
3. Only after a separate owner/security authorization, run genuine local-model proposals against those unseen examples, count every refusal and independently verify Chromium after-state with uncertainty.
4. Integrate enterprise E4–E9 capabilities after their own gates; do not claim Stage45 GO or that any of the nine release conditions are closed.

**No model, customer site, protected-main branch, production environment, authorization, JWT, secrets or identity permissions changed.**

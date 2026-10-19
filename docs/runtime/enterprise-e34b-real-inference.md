# E3.4B — Real opt-in local SmolLM2 inference on disposable GitHub CI

**Review-only. This attempts one genuine model inference on a CPU-only disposable GitHub Actions runner, not the user's physical Windows PC.** E3.4A PR #124 remains the CI-verified mock-transport baseline. This slice must not be credited with any real inference until its exact-head CI confirms an actual model runtime, returned generated tokens and an independent browser outcome.

## Controlled real-model evaluation

- GitHub Actions explicitly opts in via `AETHERIS_E34B_REAL_CI_OPT_IN=yes`. The script also requires the command-line flag `--run-real`; both conditions are necessary. There is no automatic owner-device probing.
- The runner starts an official Ollama container by **pinned published image digest**, tag `0.12.10`. It listens on **127.0.0.1:11434 only**, with `--cpus 2`, `--memory 3g`, `--memory-swap 3g`, `--pids-limit 128`, dropped Linux capabilities, read-only root filesystem and `no-new-privileges`. Its ephemeral model volume is destroyed at the end. **The Ollama model-service container has network access while it pulls the open model**; it is not covered by the Python patch-worker's `--network none` claim.
- The disposable container pulls the public `smollm2:135m` model (about 271 MB) and makes it available to the host-local loopback API. CI may fail if the upstream image/model registry is unavailable. Do not fall back silently to mock results.
- After the actual Chromium browser independently records a deliberately seeded scroll failure `scroll-24`, the test-only Python adapter checks `GET /api/tags` for the real installed model name, reported digest and reasonable downloaded size. It posts one **genuine nonstreaming `/api/generate` request** with a short source excerpt, diagnostic and a strict JSON proposal schema. Model call wall-clock is measured independently; Ollama's reported response tokens, prompt tokens and generation duration are retained alongside raw model text and its SHA256.
- The response is **never executed**. If it parses to strict `{"operation":"abstain"}` or `{"operation":"replace","old":"...","new":"..."}`, it may be staged as an untrusted advisory for the existing E3.4A network-disabled Docker `e34-prepare` and E3.3 independent `e33-apply` SHA256+grammar+canonical-output gate. Any invalid response, forbidden patch or abstention remains a recorded **nonrepair** outcome. If an actual patch is accepted, a separate Chromium check and the complete E3.0 browser QA must still pass to count a verified repair.
- The final evidence includes one attempt, actual model token count and time, model tag and **self-reported** digest/size, full output response (bounded), proposal policy result, browser result and all refusals. Model identity is **not cryptographically attested**. No unmeasured model success-rate claim is permitted.

## Boundaries and limitations

This first trial uses a **known source fault family** and is explicitly **not a truly blinded generalization benchmark**. SmolLM2 135M is intentionally tiny; it may fail the JSON format, produce an invalid patch or abstain. A truthful failing outcome is preferable to fabricated AI success. The test does not compare baseline human programmers or claim general-purpose autonomy.

The Ollama inference service and model download operate on disposable CI infrastructure, separate from the network-disabled Python website build/patch workers. The latter retain existing Docker limits: one CPU, 256 MB RAM, 64 PIDs, read-only original repository, fixed writable generated output, no-new-privileges and 45-second wall-clock timeout. CI has Docker permissions on an ephemeral runner and cannot access the user's local operating system.

The test's explicit opt-in authorizes this isolated CI experiment only; it is **not** a verified human owner identity enrollment, agent/production approval, trusted service principal, or permission to deploy a customer website. Stage45's actual 13-blocker suite is still absent and **not executed** on the draft stack. Protected `main` remains unchanged; all historical stacked draft PRs need independent review and integration.

## Independent checks

- Ubuntu and Windows mock-based negative tests for opt-in, model name/digest and size validation, unauthorized network endpoints, nonexistent model, zero generated tokens, false owner authority, abstention and invalid model responses.
- A real Ollama/SmolLM2 inference CI job with strictly verified local model metadata; actual generated token requirement; no simulated fallback.
- Existing source/site, E3.4A patch grammar, and independent browser checks, including **real before failure** and, if a patch is accepted, **real after success**.
- Full Java orchestrator regression; separate no-authority and Stage45-absent guard.
- Public CI artifact uses a bounded response length and SHA256/metrics, with no secrets included in the prompt. These safeguards do not by themselves certify the model service or GPU/host isolation for sensitive data.

## Next release blocker

A genuinely blinded multi-family evaluation outside the source/model context, actual local-user hardware tests under explicit owner consent, revocation-aware owner principal, durable single-action approvals and Stage45/host certification are separate future tasks. This PR must never be merged or deployed automatically.

Chain: E0 #102 → E1 #104 → E2.1 #106 → #107 → #109 → #110 → #112 → #113 → #114 → E3.0 #116 → E3.1 #118 → E3.2 #120 → E3.3 #122 → E3.4A #124 → **E3.4B**.

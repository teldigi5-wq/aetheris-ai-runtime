# E3.4A — Opt-in local-model advisory boundary (not real AI inference in CI)

**Review-only.** E3.3 PR #122 is the verified baseline. The additional capability is an *opt-in adapter* to a locally running Ollama HTTP service, plus untrusted text-proposal staging. No owner-PC session, inference setup, downloaded model, model training, deployment, transaction, owner authorization or production action is performed by GitHub Actions or this PR.

## What is now implemented

`e34_model_adviser.py` has two strictly distinct steps:

**Query (local only, explicit human action required):** `query <scroll|mobile|reduced> <model-name>` is disabled unless the calling environment contains exactly `AETHERIS_E34_EXPLICIT_LOCAL_MODEL_OPT_IN=yes` and the user deliberately invokes the query command. The adapter makes only an HTTP POST to `127.0.0.1:11434/api/generate` via `http.client.HTTPConnection`. It does not accept a URL from prompts or CLI, use proxy variables, follow HTTP redirects, send bearer keys, call cloud services or invoke shell commands. The transport call is hard-limited to 12 seconds, a 36 KB outbound prompt and an 8 KB response (the proposed text is capped at 1,200 bytes); Ollama's `num_predict=170`, `temperature=0` and JSON response format are requested, **not hardware-enforced resource quotas or guarantees of model determinism**. The model name in the envelope is compared against the requested name, but no cryptographically authenticated model identity is claimed. Localhost can still be influenced by a compromised co-resident process, and the prompt includes untrusted repository content and browser diagnostics.

The adapter validates the returned text as strict JSON with no duplicate keys. It permits only `{"operation":"abstain"}` or `{"operation":"replace","old":"...","new":"..."}` with short literal spans. It records a SHA256 fingerprint of the exact response and current source+diagnosis. `ownerApprovalGranted=false`, `modelOutputExecuted=false`, `modelIdentityAttested=false`, `productionPublished=false` regardless of text claims. The response is not treated as a command.

**Prepare (isolated worker):** `prepare <category>` accepts only a small locally present advisory JSON, verifies current source and observed browser report digest, exact response-byte hash and false authority flags, and stages an `E3.3` patch proposal without modifying source. The existing *separate* E3.3 `e33-apply` Docker worker then applies a change only if its exact category grammar, path, before/after SHA256 and original canonical build-manifest digest pass. The model cannot select other paths, write arbitrary code, skip tests or set execution policy; the independent Chromium QA must still run afterward.

A query and response do **not** constitute owner consent. The model has no tool/coding authority; all actual generated-site modifications remain contained to the existing disposable test workspace.

## Verification and what is NOT verified

- CI uses a **fake localhost HTTP server only**, not a real model. Tests verify opt-in refusal, fixed loopback HTTP path, proxy isolation, strict JSON rejection, abstention, digest mismatch and malicious exfiltration/authority instructions. The actual E3.3 grammar gate rejects injected `fetch(...)` suggestions and tests that preparing a valid suggestion does not modify source.
- A separate **real Chromium end-to-end mock-advice exercise** deliberately seeds `scroll-24`, verifies the browser really fails, starts a clearly identified fake Ollama HTTP server bound to `127.0.0.1:11434` on a disposable CI runner, requests its fixed JSON repair, stages it inside the network-disabled Docker worker, applies it only through the independent E3.3 grammar/SHA256 gate, and verifies Chrome now passes. Artifacts label this `fixtureOnly=true`, `realLLMInference=false`. This proves the wiring and browser outcome but is NOT a real AI repair.
- Ubuntu and Windows test the interface using standard-library `unittest`. Existing E3.0–E3.3 patch-security tests and the complete Java orchestrator regression are required. The original E3.0 website generator and Chromium browser QA are independently rerun to detect UI regressions.
- **NO CI performance metric for real LLM repairs exists.** No pretrained model is installed or called, and any fake HTTP fixture results must never count as model accuracy.
- The owner’s actual PC has not been touched, local model availability not checked, hardware fit not measured, and model latency/cost/performance not benchmarked. User-driven local inference must be explicitly enabled in a separate real-device trial.
- Docker in the existing E3 runner retains no worker network, fixed writable target only, 1 CPU, 256 MiB RAM, 64 PIDs, no privileges and 45-second wall-clock. The **separate localhost model-query caller** is outside that no-network container; it is not accurate to claim the *whole workflow* has no network.
- There is still no authenticated owner binding, action-specific positive approval, production publication, real Windows device certification or Stage45 release decision.

## Future manual, explicit opt-in trial (not run here)

A human operator can choose to install and run a local Ollama server and a legitimately downloaded small model. After generating the E3 site and a real failing E3 diagnosis in a **disposable test checkout**, the operator may deliberately run the query step with `AETHERIS_E34_EXPLICIT_LOCAL_MODEL_OPT_IN=yes` and an installed model's exact name. This **only stages an advisory**; it does not apply or publish any change. The operator must separately run the restricted prepare, independent apply gate and Chromium regression. Do not use a production repository or connect sensitive accounts for this trial.

## Next E3.4B milestone

Perform **one genuine local model call** with consent and clear model identification in a disposable lab; record real opt-in, sanitized prompt/response hash, actual latency, valid proposal/abstention, rejected malicious payloads, before/after independent browser results and observed resource budgets. Expand evaluation to truly withheld different bug families rather than E3.3's six openly checked-in cases. Separate owner authentication, durable action grants, and Stage45 certification before any real autonomous execution.

Chain: E0 #102 → E1 #104 → E2.1 #106 → E2.2A #107 → #109 → #110 → #112 → #113 → #114 → E3.0 #116 → E3.1 #118 → E3.2 #120 → E3.3 #122 → **E3.4A**. All remain review-gated drafts, protected `main` unchanged.

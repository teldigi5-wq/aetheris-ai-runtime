# E4 — single-attempt local Ollama proposal session (explicit opt-in)

**27 October 2026 — DEVELOPMENT DRAFT; MODEL WORKLOAD NOT CERTIFIED; NO_GO.**

This is a callable Python integration of E4's zero-cost candidate-policy with the existing E3.6 local Ollama adviser. Unlike E4's admission-only artifact, this wrapper **can actually call a model** on the owner's local Ollama instance when the operator directly calls `LocalProposalSession.propose(model_id, task)`. It never sends source to cloud and does not automatically download models, write code, deploy, or approve a proposal.

Safety boundaries: caller must explicitly set **both** `AETHERIS_E4_EXPLICIT_LOCAL_SESSION_OPT_IN=yes` and the existing `AETHERIS_E36_LOCAL_INFERENCE_OPT_IN=yes`; high-resource models additionally require the existing `AETHERIS_E38_HIGH_RESOURCE_MODEL_OPT_IN=yes`. The model ID must appear in the frozen allowlist and current `/api/tags` response from `127.0.0.1:11434`. The E4 policy confirms that local proposal routing is eligible; the E3.6 delegate rechecks installation, uses bounded JSON completion, and refuses code execution and remote fallback. Only the existing three trusted synthetic task categories (`scroll`, `mobile`, `reduced`) can be sent, never arbitrary customer source.

A Python session instance reserves a **single** attempt under a process lock **before** network access; retries and any failures consume the attempt. This is an in-process experiment budget, not distributed quota or authenticated authorization. No owner authentication, real credentials, real money, external sites, local file writes or production integrations are added. Proposal output is labeled **untrusted** and cannot be automatically edited or accepted. An explicitly invoked local inference may use device CPU/GPU resources and require locally installed open models, with memory/performance not benchmarked.

Linux and Windows CI test the actual runtime wiring with **mocked** Ollama responses, negative permission/paid route/unknown model and missing installation cases, single attempt, no automatic cloud fallback, injected output tampering and stale opt-in. **CI performs zero real model calls.** No claim of successful owner-device inference or agent-generated website is made without separately observed live evidence.

E4 quota, privacy/consent identity, provider availability and performance, paid fallback denial across multiple nodes, end-to-end human controls and E3.9/E5–E9 are not certified. Protected `main` and Stage45 production remain **NO_GO**.

# E3.8 — Nous Research Hermes Agent: **agent framework**, not another LLM

**Security-oriented integration proposal only. Not installed, imported, launched, delegated to, or connected to Aetheris tools.** No permissions, workstation control or production deployment.

## Two different products, different counts

1. **Hermes models**, such as the `hermes3:3b` and `hermes3:8b` Ollama tags, are open-weight text-generation models. They are counted among the **20 local model routes** in E3.8A. Hermes 4 14B/70B remain research-only until serving and hardware validation.
2. **Hermes Agent**, published by Nous Research, is an autonomous agent **software framework/runtime**, not a model checkpoint and not a free OpenRouter model. It exposes its own tool registry for browser, terminal, file manipulation, memory, skills, subagent delegation and scheduled actions. Counting it as a 21st local LLM would be incorrect.

Official sources:
- Framework: <https://github.com/NousResearch/hermes-agent>
- Security model: <https://github.com/NousResearch/hermes-agent/blob/main/website/docs/user-guide/security.md>
- Tools overview: <https://github.com/NousResearch/hermes-agent/blob/main/website/docs/user-guide/features/tools.md>
- Hermes LLM weights: <https://ollama.com/library/hermes3> and <https://huggingface.co/NousResearch/Hermes-4-14B>

## Proposed future purpose in Aetheris

Review it as a possible **pluggable, isolated agent-worker backend** for developer research and independently bounded read-only or test-only workflows, *not* as Aetheris's central owner, executive, cybersecurity officer or automatically activated Windows controller.

Security risks of naive integration:
- **Terminal/filesystem access** could escape the current E3 strictly allowed test website paths.
- **Browser/web fetch** could leak local code or secrets without dual source-sharing consent.
- **Memory and learned skills** are persistent instructions; untrusted tools or source files could modify future behavior.
- **Scheduled/subagent actions** risk running after the original human review, bypassing one-use authorization gates.
- **MCP credentials** must be filtered and audited at every subprocess boundary.
- **Self-approval / autonomous tool escalation** must remain technically impossible.

Hermes documents command approval, user authorization, credential filtering, context scanning and optional isolation, but these documented design layers do **not** certify their safety in Aetheris. They must be independently enforced and verified in our exact deployment.

## Prerequisites before any install or executable use

1. Review precise upstream commit/tag, transitive dependencies, licensing and known advisories. Pin bytes and produce an SBOM.
2. Run only in a disposable offline/test container with no host Windows mounts, no secrets, no outbound network, no privileged Docker socket, no persisted cron/memory and no mutable skill directories.
3. Start with a **no-tools or explicit read-only test-only** interface; never pass Aetheris gateway identity headers, signed owner proof, agent credentials or billing tokens to the worker.
4. Author a strictly typed, one-way mission/proposal protocol with maximum output size, exact file scopes, timeouts and deterministic denial for unexpected actions.
5. Subject any text patch to existing separate E3.3/E3.4A SHA256/grammar and browser QA gates, with no self-grants or direct GitHub write.
6. Test adversarial skill injection, prompt injection in repository files, replay of old approvals, child agent fan-out, network attempts and persistent scheduled tasks; verify actual Docker process and mount policy.
7. Maintain Stage45/owner-PC/prod **NO_GO** until the user explicitly authorizes those stages and actual certification passes.

**Outcome:** Hermes Agent is a legitimate future research option for E4/E7 orchestration and may be very useful after isolation, but **is not installed or running now**, is **not included in the 26 model routes** and does not increase the number of functioning Aetheris agents.

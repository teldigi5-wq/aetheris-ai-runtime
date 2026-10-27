# E4 local model proposal operator CLI — safe planning by default

**27 October 2026 · development draft · not production certified · Stage45 NO_GO**

This CLI makes E4's opt-in local `LocalProposalSession` callable from a terminal. It introduces **no real model test in CI** and does not authenticate the operator. It does not contain cloud API code, a model pull path, deploy operations, a source-code writer, or an automatic model fallback.

### Default safe operator workflow

`python tools/enterprise/e3_factory/e4_local_proposal_cli.py --model qwen2.5-coder:1.5b --task scroll` prints a JSON **PLAN_ONLY_NO_INFERENCE** report without reaching Ollama, installing anything or reading files outside existing synthetic fixtures. The model must match the frozen E3.6 local allowlist; high-resource IDs print an extra-opt-in requirement.

To invoke a **locally installed** model, an operator must *separately* set `AETHERIS_E4_EXPLICIT_LOCAL_SESSION_OPT_IN=yes` and `AETHERIS_E36_LOCAL_INFERENCE_OPT_IN=yes`, ensure Ollama is available on 127.0.0.1:11434 with the exact model installed, then intentionally repeat the CLI command with `--run-local`. Larger models additionally need `AETHERIS_E38_HIGH_RESOURCE_MODEL_OPT_IN=yes`. The existing E4 local session reserves one attempt per invocation before probing installed models and delegating to the E3.6 adviser. The result is labeled an **untrusted proposal**, never applied as a patch.

The CLI reports a generic denial instead of leaking local provider errors or paths. However, real operator execution can consume CPU/GPU resources; no local inference latency, output quality or model availability has been measured here. Each separate CLI process creates a fresh single-attempt session: this is **not** a durable global request quota or spending control. No remote cloud requests are permitted in this path.

Exact-commit GitHub Actions on Linux and Windows prove the default real subprocess dry-plan plus mocked double opt-in, approved task/ID, failed local provider, no source edit or cloud fallback. **CI makes zero real model calls.** E4 broader routing, live model QA, agent results, permissions and E5–E9 remain uncertified. Source branch, protected main and production deployment unchanged.

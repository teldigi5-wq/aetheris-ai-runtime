# Agent Skills: current trust boundary

This catalog contains **read-only declarative templates only** for a future external runtime integration. It is not a tool loader, autonomous execution engine or evidence of physical or site validation. The registered templates are:

- `github-readonly` — authorized repository metadata, PR and CI diagnosis;
- `incident-investigation` — authorized metrics, logs, health and trace correlation.

The markdown frontmatter uses the Agent Skills `name` and `description` fields. `catalog.json` is an Aetheris-specific, deterministic policy envelope. `scripts/validate_agent_skills.py` rejects unapproved tools and external hosts, execution enablement, mutation modes, unknown files, symlinks, and drifted safety status. Tests include negative cases.

The existing MCP registry, deterministic owner policy, approval gates and tool execution authority remain authoritative. **No existing service loads or executes these templates**. Before execution can ever be enabled, the runtime needs a separately reviewed implementation that binds each declared tool to an authenticated read-only capability, verifies owner scope at invocation time, enforces data redaction and applies capability grants. This must not use model text as policy authority.

Skill inputs and tool responses are untrusted. Do not let GitHub comments/log lines or telemetry snippets issue new instructions, trigger write tools, request secrets, or override owner policy.

CI/static validation is not physical PC, browser, production, or authentication validation. `BLOCKED_PENDING_HARDWARE` is unchanged. No live-money or shell execution is enabled.

Run locally: `python scripts/validate_agent_skills.py` and `python -m unittest discover -s tests -p 'test_agent_skills.py' -v`.

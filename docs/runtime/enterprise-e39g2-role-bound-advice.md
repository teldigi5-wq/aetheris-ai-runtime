# E3.9G-2 — exact-role-scoped reference advice, default OFF

**Engineering status:** Draft, no merge/production, Stage45 NO_GO. Stacked on
[E3.9G governed skills PR #154](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/154)
at base commit `575b95a6655140757518906ee0055ab4a18b5953`.

## Baseline and actual identities

The canonical main-based frozen roster contains **26 configured agent
identities in 13 legacy divisions**, locked at `canonical-main-specialists.json`
Git blob `2d564f28533f4910964d636591acd131a174daa0` and the unchanged
`application.yml` Git blob `d7e0df3c24077ef20c24cc59df3bca902c805520`.
The program compares BOTH bytes to their pinned Git blob identities and
fails closed on drift. This is identity-metadata verification, **not
cryptographic principal authentication or owner permission**.

**7 of 26 identities are allowed to request bounded reference metadata**:
`frontend-engineer`, `qa-engineer`, `software-architect`,
`appsec-officer`, `security-architect`, `devops-engineer`, and
`mcp-integration-engineer`. Their division IDs are independently checked,
not inferred from user input. Each has a fixed, explicit skill allowlist.
`quant-researcher`, `trading-risk-officer`,
`authorized-redteam-officer` and every other agent not explicitly listed
remain denied by default. **This does not add employees or change their
existing `allowedTools`.** The 13 companies / 100 divisions are proposed
metadata, never existing active tool authority.

## What actually works

`tools/enterprise/e39g2_scoped_skill_advice.py` performs three things:

1. `check`: verifies unchanged canonical role evidence, division membership,
   six upstream and eight native pinned skills, fixed read-only trust levels.
2. `select --explicit-reference-request <agent-id> <skill-id> <task>`:
   by default **DENIED**, and requires an explicitly supplied process flag
   `AETHERIS_SKILL_REFERENCE_ENABLED=1` and the CLI request marker.
3. Under both conditions, returns structured JSON containing 800-byte
   bounded skill excerpt labeled **UNTRUSTED REFERENCE**, skill SHA256,
   source provenance, canonical catalog blob identity, task SHA256,
   reproducible (UNSIGNED) receipt digest, selected preexisting agent/division,
   zero model/tool calls and explicit false values for deployment, owner
   identity, approval, auto-execution and Stage45.

Example in an **explicit local test/sandbox shell**, not production:

```bash
python tools/enterprise/e39g2_scoped_skill_advice.py check
AETHERIS_SKILL_REFERENCE_ENABLED=1 python \
  tools/enterprise/e39g2_scoped_skill_advice.py \
  select --explicit-reference-request frontend-engineer emil-design-eng \
  "Review a premium accessible landing page without changing project files"
```

The returned task statement is untrusted and byte/character bounded. It is
quoted as JSON data, not interpreted as approval. Imported skill text is
still untrusted, the third-party upstream blob SHA verification occurs on every
request, and the license is preserved.

## Safety properties and limits

- **No network, terminal, shell, filesystem mutation, browser navigation,**
  `npx skills add`, credentials, repository write, protected `main` change,
  deployment, simulated owner authorization or live-trading action.
- No HTTP endpoint or Java service auto-registration. No model requests or
  agent runtime output consumption. This is a callable opt-in *local
  reference selector*, not fully autonomous agent-to-skill integration.
- `evidenceSha256Unsigned` is a *repeatable non-secret integrity checksum*
  tying exact agent, task, skill and frozen catalog. It is **not**
  cryptographically signed provenance and does not prove a trusted principal.
- The process environment flag proves no user identity; it is a local
  opt-in switch only. Future operation needs an external authenticated
  actor, independent task lease, provenance attestation and bounded
  assistant prompt-layer separation.
- Role binding and `allowedTools` must not be read as permission to call
  those tools: skill hints cannot elevate existing tool permissions.
- Real UI improvement claims require a separate baseline website,
  independently verified task acceptance, keyboard/zoom/reduced motion,
  mobile+desktop Chrome, performance traces, security reports and review.
- E3.9F independent *model* inference trials are still unfinished.
  Granite4 3B **2/8**, Hermes3 3B **0/8** on public synthetic cases;
  #153's 32/32 runtime-seeded browser checks were **non-model fixtures**.

## Subsequent integration

1. E3.9G-3: security-reviewed **agent advisory consumer** with deterministic
   provenance and prompt isolation, no tool escalation, plus real-model output
   behind independent verification.
2. E3.9G-4: authorized, independent website & security task benchmarks,
   destructive mutation refusal, repeatability and UI acceptance.
3. E3.9F: external truly withheld multi-domain model repair corpus remains
   a parallel blocker; E4–E9 and Stage45 remain later phases.

**Milestone count:** the remaining high-level **10 gates** discussed in
E3.9G-1 still exist; E3.9G-2 is partly implemented by this slice and
requires actual authenticated runtime integration for operational use.
No claims of Stage45 readiness are made by passing CI.

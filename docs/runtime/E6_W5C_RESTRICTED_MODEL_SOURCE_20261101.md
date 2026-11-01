# Enterprise E6 W5-C — Restricted model-authored CSS source and no-execution boundary

**1 November 2026 · RESEARCH / REVIEW ONLY · Stage 45 NO_GO**

## Correct source and branch lineage

Canonical repo: `teldigi5-wq/aetheris-ai-runtime`. W5-C is a stacked child of W5-B **draft PR #267** at exact independently verified HEAD `e28e4d7dc7e00f330f88a0b3b1ba75cbf8273130`, not Runtime/Platform protected main. Previous W1–W5-B draft branches, local target directories and GitHub PRs are unmerged and must remain unchanged. Runtime main `65a6262717adcd52ac8d8a16ed6f223e299fd74d`, Platform main `57ad3e0e602efa6feb275328254151b959a3499b`. Security Runtime Issue #23, Platform Issue #251, production release nine attestations and E6 Task7/8 are separate.

## Purpose and precise capability boundary

W5-A/B allowed a model to propose content and *preselected* layout tokens. W5-C extends that boundary: a model may propose **actual CSS source-code text**, but only from an extremely small, documented safe language of fixed selectors and fixed declaration/value domains. The compiler parses that text and re-emits canonical CSS, producing a real stylesheet whose rules change browser-computed styles. This is not arbitrary React/JS/HTML generation, an isolated OS-level process sandbox, full autonomy, or a production-ready creative engine. Importing a JavaScript library, fetching an image or using arbitrary selectors is disallowed, not merely discouraged.

### New files

- `w5c_restricted_css_compiler.mjs`: bounded ASCII-only CSS source (80–2400 chars), 3–8 distinct approved selector rules, 1–3 approved properties per rule, exact value grammars, no arbitrary selectors, escapes, at-rules, comments, URL data, JS, event handlers or untrusted paths. Canonical output follows the trusted W5-A stylesheet. Compiles two CSP-protected pages with no JavaScript and a new source/compiled CSS SHA manifest. All owner/art/authorship/release gates remain **false**.
- `w5c_local_code_trial.mjs`: **owner opt-in only**, requires `E6_W5C_ALLOW_LOCAL_INFERENCE=YES` and an installed local `E6_W5C_MODEL`. Uses the fixed 127.0.0.1:11434 Ollama provider and existing installed-model metadata check. For each of the three W5-B fictional held-out briefs, requests an original design proposal, then separately requests model-authored CSS source for that design. Validates both; rejects any invalid or unsafe response without compiling it. Results are isolated, **write-once** in `target/e6-w5c-local-css-research`, include hashes and safe rejection codes but no raw transcripts or credentials. The exact model digest and weights remain self-reported/unattested.
- `w5c_test_doubles.mjs`, `test_w5c_css_code_gate.mjs`: three **human-written mock CSS programs**, negative tests for network/CSS injection, unexpected selectors, hidden animation, unsupported values, bad model envelopes, tampered provider replies and unsupported model names. They are never evidence of actual model inference.
- `w5c_css_browser_proof.mjs`: actual software Chromium checks three mock compiled sites × compact/mobile/desktop viewports with JavaScript disabled and reduced motion. Confirms real local stylesheet response and computed CSS differences, functional routes, no script tags, no unexpected external requests/overflow/errors. Screenshot hashes differing do not establish luxury quality.
- `.github/workflows/runtime-e6-w5c-restricted-css.yml`: exact-head read-only CI, inherited W1/W2 and W5-A/B contract regression, locked Next.js typecheck/static build and browser matrix. CI expressly refuses actual model env flags and **does not run or download Ollama**.

## Owner-only real model trial

W5-B real local Ollama inference is not yet independently verified. W5-C can be tested separately on owner hardware, but passing its tests must not backfill W5-B status or certify a production release. Select an already installed, licensed, resource-appropriate model; no cloud API key or mandatory paid service is needed.

In a **new** detached W5-C review worktree (do not delete existing worktrees):

```bash
cd ~/aetheris-ai-runtime
git fetch origin
git worktree add --detach ~/aetheris-runtime-w5c-preview-20261101 origin/feat/e6-w5c-restricted-model-authored-css-20261101
cd ~/aetheris-runtime-w5c-preview-20261101/tools/enterprise/e6_studio/next-studio
node --test test_w5c_css_code_gate.mjs
ollama list
# Replace only with a model tag actually installed and reviewed on this device:
E6_W5C_ALLOW_LOCAL_INFERENCE=YES E6_W5C_MODEL='YOUR_INSTALLED_MODEL_TAG' node w5c_local_code_trial.mjs
```

Review `target/e6-w5c-local-css-research/source-code-trial-report.json`, plus individual accepted site's CSS, HTML and SHA manifest. Any rejection is an expected fail-closed outcome and should not be bypassed or overwritten. Inspect the local generated site (each case as its own Python localhost root) only after validation. No local preview server needs to be running for GitHub/CI work.

## Non-claims and next acceptance

CI tests verify **the restricted CSS-code interface** with human-authored fake proposals only. No actual model was run; no independent model authorship, novel content, licensing, premium visual identity, full WCAG 2.2 AA, real physical GPU/frame pacing, verified security of unrestricted generated JS/React, source execution sandbox, or production readiness is claimed. W5-D must independently trial a genuine installed model on held-out prompts and evaluate real source outputs and visual quality before more language constructs are allowed. W6 requires independent design, accessibility, performance, licensing and security acceptance. E6 Task7/8, Runtime #23, Platform #251 and Stage45 release gates remain blocked. **No merge, deployment, privilege escalation or production permission is authorized.**

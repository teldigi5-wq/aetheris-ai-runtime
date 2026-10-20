# E3.9G-7 — REAL local-model website design skill A/B research

**DRAFT, experimental**. Stacked on exact verified [#160](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/160) at `865219c19c1b5c1adf542033666b5beec53050c8`. No protected `main` merges, production, owner-PC access, tool execution or Stage45 release.

## Why this research is necessary

Four previous E3.9G slices established a governed 14-skill reference catalog, seven-role/26-agent reference selection, internally task-bound Spring service, Ed25519 candidate-signed preflight, durable H2 and real PostgreSQL nonce refusal. **None demonstrated that an AI agent actually benefits from using the imported design skills.**

This is the first bounded, *genuine real-model* intervention-vs-control measurement for those skills. Unlike prior skill-only QA, it makes Ollama calls with/without skill context. **It does not give models source modification rights.**

## Experiment, provenance and trust

- Two open local models `hermes3:3b` and `granite4:3b`; Ollama pinned Docker container `ollama/ollama:0.12.10@sha256:e8c3d1f6ad16323bc40dc63eff0701d4fc32113f75a86b54b3e836eef8290de6`. CPU 2, RAM 5GiB, read-only root, no capabilities, 127.0.0.1 loopback only, ephemeral runner. OSS model bytes self-identify through Ollama metadata, **not cryptographically attested**. No model weights installed on user's hardware.
- Exactly two preexisting **public synthetic** cases from E3.9D: `top-120`, `opacity-zero`. These are two fault categories from one site, **not external, blind, or representative of all websites**.
- Before model call, original site is seeded and real Chromium at **390×844 and 1280×800** must independently observe its specific keyboard-focus failure. Both arms use precisely the same typed observations, CSS excerpt, schema, temperature=0, max tokens and local model.
- **Control `baseline`**: existing frozen E3.9E prompt (no skills).
- **Treatment `skill-context`**: identical prompt with two small exact verbatim extracts from the source files for `emil-design-eng` and `aetheris-web-accessibility`, validated against the pinned upstream Git blob/verified registry and exact excerpt hashes. They remain clearly labeled **UNTRUSTED DATA** and cannot change agent policy.
- Alternating arm order by case limits, but does not eliminate, warm-cache/order effects. No statistically significant effect can be inferred from 2 trials per model. A follow-up can introduce true counterbalancing/random assignment, multiple models/seeds and withheld websites.
- Two genuine inference requests per case per model: **4 requests per model, 8 total** if the CI genuinely completes and records all results. No response is fabricated, cached as successful, or copied from other models.

## What is genuinely scored — and what is NOT

An independent script parses the actual raw Ollama output and invokes the existing unchanged `e39e_model_policy.validate_candidate` to determine **static CSS-policy eligibility**. Policy returns one of `POLICY_ELIGIBLE_NOT_BROWSER_TESTED`, `POLICY_REJECTED`, `ABSTAIN`, `INVALID`; the receipt preserves hashes, prompt source, self-reported model tokens and timestamps.

**No model patch is written to the website. No model after-repair Chromium result exists, and every model browser-repair score is explicitly zero / untested.** A network-disabled deterministic restoration of the test fixture is run after each completed case, then its real browser after-check verifies the harness; **that restoration is not a model repair and is not credited**.

The A/B outcome is limited to *policy-eligible suggestions*, not corrected websites, user-facing product quality, accessibility certification, premium visual quality, secure code, automation readiness, a measured net benefit from skills, or independent generalization. Model abstaining is preferable to bypassing the deterministic gate. Externally curated multi-domain blind case evaluation is still tracked under #151.

Security constraints: CLI requires exact `AETHERIS_E39G7_ACTUAL_MODEL_SKILL_AB_CI_OPT_IN=yes`, allowlisted model+case only; proposer cannot import evaluator/policy and makes only loopback Ollama calls; scripts do not create a website patch or use shell; `skills/registry.json` must remain non-activating. No HTTP service, owner auth, trading bots or paid API introduced.

## Next credible steps

1. Independently gated **actual model proposal application in the network-disabled disposable fixture** and real after-repair mobile/desktop Chromium checks; never loosen policy to increase acceptance.
2. Expand controlled skill conditions, full verified source citations, multiple runs and independent realistic web and app security tasks; report confidence intervals with sufficient data.
3. Establish genuine authenticated principal/task lease before making any agent actions accessible outside a quarantined CI harness.
4. Preserve real independent E3.9F benchmark, quality-aware routing (E4), multi-agent workflow (E5), governance (E6), real tasks (E7), reliability (E8), hardening (E9) and Stage45 GO/NO-GO.

All CI results must be associated with the **exact PR head**. Nonzero successful policy eligibility is never a substitute for browser verification.

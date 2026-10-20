# E3.9B — Real Hermes 3B versus IBM Granite 4 3B keyboard repair benchmark

**Draft, independent per-model disposable GitHub Actions runners.** Actual scores are unknown until exact-head CI completes. No owner laptop access, production tools, paid cloud, trust enrollment or Stage45 release.

## Question

On a **new fourth synthetic website defect family** introduced in E3.9A, can two genuine small open-weight local models produce a short correct CSS edit that passes Aetheris's separately reviewed security gate **and** real Chromium accessibility behavior?

This is *not* a broad or truly blind benchmark: the source bug family is visible in the repository and the model receives a short excerpt. The evaluation policy's canonical repair is not imported or shown to the model. Model code has no tool interface. One prompt per model and one attempted repair per model are statistically weak but can establish the plumbing.

## Source models

| Role | Exact local Ollama tag | Published package | Publisher / licensing consideration |
|---|---|---|---|
| General assistant baseline | `hermes3:3b` | ~2.0GB | Nous Research Hermes3; Llama3-derived model, review Meta community license |
| Enterprise small-model challenger | `granite4:3b` | ~2.1GB | IBM Granite 4, Apache-2.0 model license |

Verified sources: <https://ollama.com/library/hermes3> and <https://ollama.com/library/granite4:3b>. A 2GB package does not equal 2GB total RAM use. Real container caps are **5 GiB RAM, 2 vCPU, 128 PIDs**. The Ollama image is pinned to the previously exercised digest; weights are pulled only on ephemeral CI runners. Model digest and downloaded size are self-reported by local Ollama and logged, not cryptographically attested by this code.

## Implementation

1. `e39b_real_model_adviser.py` checks `AETHERIS_E39B_REAL_MODEL_CI_OPT_IN=yes`, restricts model name to the two exact tags, confirms actual installed tag + sensible model size and digest via local Ollama, and checks authentic Chromium proof: first Tab focuses a skip link, but bounding rectangle has negative top.
2. It selects a *short local CSS excerpt* with `.skip-link:focus` and the observed viewport geometry, **without opening the evaluator's expected-canonical patch**. The task instruction is general: a keyboard-focusable skip link must be visible once focused while hidden at rest. The user cannot submit arbitrary paths, URLs, hosts, shell commands, model selectors or remote prompts.
3. The model returns strict JSON structured either as `{"operation":"abstain"}` or `{"operation":"replace","old":"...", "new":"..."}`. The raw bounded response is hashed; exact tag/digest/size, wall time, tokens, prompt/source/before-diagnosis hashes, and refusal/invalid classifications are retained. The CPU transport is fixed local loopback, never remote.
4. `e39b_model_patch_gate.py` runs *after* inference in the existing **`--network none`**, unprivileged, read-only-root/source Docker worker, with only disposable `target` writable and 45-second timeout. It validates the model's raw bytes and declared input hashes, extracts only one bounded literal text replacement, and independently invokes **the same unmodified E3.9A `gate.validate` policy**. That gate requires the exact `site/styles.css` scope, one permitted focus correction and the original generated website's SHA256. Wrong CSS semantics, malicious additions, alternate files and owner claims are refused.
5. If and only if policy permits one CSS change, a real Chromium keyboard-after test executes. `e39b_score.py` credits a model repair only when the receipt is genuine to the current workspace **and** Chromium visibly focuses an onscreen skip link with canonical clean source bytes. Invalid, abstained and rejected model outputs receive **zero repairs**, not a false CI failure nor a hidden fixture reset.
6. Each model trial uses an independent GitHub Actions matrix job and private disposable work area. Models do **not** receive one another's prompt or answer; no deterministic fixture proposal is called in either trial. A separate full Java regression, Windows/Ubuntu unit tests and static no-authority check must pass.

## Limits and truthful ratings

These are **two exploratory single-shot model attempts on one published synthetic bug**, not fair estimates of overall coding accuracy, real-world safety, or statistical superiority. Prompt/schema choices and prior model training are uncontrolled. The predecessor E3.8C recorded **1/3** Hermes3B browser-verified fixes on different known scroll/mobile/reduced defects; the rate cannot be directly compared to a different keyboard task. E3.9A's deterministic fixture patch is **0 model-generated fixes** and remains credited only as validation infrastructure.

No autonomous owner action, persistent background agent, real multi-company workers, external provider keys, future Gemini/Claude/GPT model contract or physical Windows workstation interaction are provided by this work. `main` remains protected, Stage45 production/PC certification is NO_GO.

## Exit criteria

Exact-head CI independently proves two genuine nonmock model completions (or documents specific CI failure), actual model response details, before-failing Chromium evidence, any rejected/approved patch decisions, real after-test if accepted, isolated Docker policy and unaltered full Java regression. Honest **0/1 on either model** is acceptable evidence; it does not demonstrate an autonomous employee. Next step E3.9C: build a larger and genuinely sealed set of unseen accessibility and responsive defects, with differential third-party evaluator review and robust latency/quality statistics before prioritizing model routing in E4.

The 13-company/100-division proposed blueprint and 26 configured agent identities remain nonexecuting metadata. Current model catalog remains **20 local model candidates, five named free-cloud candidates and one free router selector**, plus 12 research-only entries. **No catalog count changes** in E3.9B.

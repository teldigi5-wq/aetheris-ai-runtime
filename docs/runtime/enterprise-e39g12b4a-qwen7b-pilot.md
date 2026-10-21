# E3.9G-12B4A — Apache Qwen2.5-Coder 7B coding-model pilot

**DRAFT, stacked on [PR #173](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/173)**, exact parent SHA `19915044aa78108caf411e0bedeb7c7bd937ea05`. Tracks open issue #169. Do not merge protected `main`; Stage45 stays **NO_GO**.

## Why this model, license and resource constraints

Reviewed authoritative [Ollama Qwen2.5-Coder 7B listing](https://ollama.com/library/qwen2.5-coder:7b): **7.62B**, Q4_K_M, **4.7 GB** published manifest prefix **`dae161e27b0e`**, **Apache License 2.0**. [Qwen's original 7B Instruct model card](https://huggingface.co/Qwen/Qwen2.5-Coder-7B-Instruct) independently lists `apache-2.0`. Compared to tested Qwen2.5-Coder 1.5B, 7B is a larger coding-specialist candidate and might better reason about live CSS/JS semantics. That is a hypothesis, not an assumed improvement. Not the Qwen Research License 3B variant.

The ephemeral Ollama container is pinned to reviewed runtime digest `ollama/ollama:0.12.10@sha256:e8c3d1f6ad16323bc40dc63eff0701d4fc32113f75a86b54b3e836eef8290de6`. It is capped at 4 CPU, 10GiB RAM/swap, 128 PIDs, read-only root, all Linux capabilities dropped, `no-new-privileges`, inference API bound to **127.0.0.1**, and local weights removed with its disposable volume at end of run. Only the exact reviewed tag `qwen2.5-coder:7b` is pulled. License text is read back from the **running local Ollama container**. Exact model tag, approximate published size and 12-hex manifest prefix are checked against `/api/tags`; full self-reported manifest hash is saved with every response. A tag+digest prefix is **not** a signed independent attestation of weight files or supplier identity. Avoid claiming immutable model supply-chain provenance.

## Identical task/evaluator scope to 1.5B

Four existing **public self-authored synthetic website** challenges:
- StudyNest offscreen keyboard-focused skip link.
- StudyNest excessive mobile timetable grid width.
- CityLine reduced-motion ticker animation.
- CityLine untrusted station-input HTML sink (a harmless inert markup marker, not a script exploit).

Use exactly the existing B3 prompt, full BROKEN HTML files and non-secret observed fault names, `temperature:0`, `format:json`, and identical four request orders. A fair historical A/B interpretation is limited: B3 1.5B calls were made on a different GitHub Actions run and hardware budget. We do **not** claim same-run model randomization or inference-cost equality.

1. Broken-only ZIP producer runs with repository checkout and existing cap-dropped `--network none` seeder; golden references never exported.
2. **NO-CHECKOUT model job** downloads **only** the exact three-file broken-only ZIP, asserts no `.git/tools/docs/skills`, performs **4 genuine local Ollama generations** if the job succeeds, records raw outputs, hashes, wall-clock, token counts and digest to its **untrusted** output artifact.
3. **Independent evaluator job** with trusted source downloads only the untrusted model output, re-seeds independent real-browser truth, invokes unchanged `e39g12b2_typed_policy.validate` via the original **network-none** 1CPU/256MiB Docker worker using a new 7B-fixed receipt validation wrapper, writes at most one typed patch to an isolated disposable source copy.
4. **Real separate Playwright Chromium** checks both 390×844 mobile and 1280×800 desktop on only **policy-applied** model patches. Independent scorer re-checks source/content SHA256 and screenshot digests, browser JS errors and the exact target behavior. Only policy-approved changes passing **both** real Chromium viewports count as verified model repair. Rejections/abstentions get **zero** patch applications and after-browser checks. No reference restores are counted as AI.
5. Cross-platform Windows+Ubuntu adversarial input, scope and no-owner privilege tests, exact pinned skill provenance, **full Java 21** regressions and dedicated no-production static gate run independently.

## Boundaries and success reporting

A green workflow proves that *the isolation pipeline* worked, NOT that any website was repaired. Publish all 4 raw model attempts, which changes were rejected, how many model-authored patches actually applied, real-after browser counts, and at least basic resource/time accounting. A larger model is **not necessarily better** and may still produce **0/4**.

These two developer-authored public sites are **not externally curated or truly held-out**; the known tester controls remain in evaluator checkout and are inaccessible only to honest no-checkout model job, not to privileged CI admins or researchers viewing the public repo. Evidence is not a production feature, no independently authenticated owner, Stage45 release, local PC, live site, remote paid API or autonomous execution.

E3.9G-12B4 remains **OPEN** beyond this sub-slice: independently curated larger sites, multiple safely licensed coding models, repeat trials, held-out validation and realistic enterprise-scale evidence. No numeric model-results claimed before exact-head CI concludes.

# E6 W5-A — Live local model-proposed design with secure static compiler

**1 November 2026 · DRAFT, NO DEPLOY · Stage45 NO_GO**

## Exact lineage and capabilities

This W5-A work is stacked only on independently verified W4-D draft PR #265, exact parent `3ca98680168cb7f05710b49bff7df5cbb4d9013c`. W1–W4-D research branches remain separate and unmerged. Runtime and Platform protected `main` are unchanged. W4-D documents physical GPU/creative approval as blocked; nothing here overrides it.

W1–W4-C previously created deterministic briefs/templates/curated website assets and a research WebGL scene. **None proved genuine AI model website design/code authorship.** W5-A introduces a *real local-model invocation path* for a **limited, declarative model-proposed website**. The creative model may choose original words, page headings, section order, alignments, palette, visual motif, composition and spacing. A deterministic **security compiler** creates safe HTML/CSS from this untrusted JSON. This is an initial model-authored *design-specification and copy* pipeline, **not arbitrary AI-generated HTML/JavaScript or a full cinematic autonomous builder**. W5-B must expand genuine code authorship under sandboxed checks.

## Sources

- `w5a_fictional_owner_brief.json`: standalone fictional coastal editorial brief, unrelated to Arcloom fixtures or real clients; no private material.
- `w5a_local_ollama.mjs`: opt-in, fixed-host, local-only `http://127.0.0.1:11434/api/generate` Ollama integration. `stream:false` and `format:"json"`, a strict prompt/response budget, model name validation, 180-second timeout, no redirect, no external URLs, no shell/tool calls, no API keys or mandatory paid service. A bounded local model response contains model-reported metadata; **this does NOT cryptographically attest that genuine model weights ran**.
- `w5a_model_design_compiler.mjs`: strict closed input/output schema, 4.5:1 minimum palette contrast, exactly two local routes, no model-supplied source code or untrusted URLs, safe text escaping, fixed reviewed CSS, no JavaScript, model-specific layout/content/tokens, CSP default-deny, `noindex,nofollow`, accessible skip link and keyboard navigation, visible synthetic/fictitious disclosure, no contact data collection. It is impossible for a valid model JSON to select arbitrary filesystem paths, execute code or authorize release.
- `w5a_base.css`: fixed editorial CSS envelope with responsive, reduced-motion and visual motif variants; the model does **not** author executable CSS.
- `test_w5a_model_contract.mjs` and `w5a_test_doubles.mjs`: seven sets of adversarial/isolation tests and two completely human-authored **simulated provider responses**, explicitly distinct in palette/content. These mocks **do not prove AI generation**.
- `w5a_simulated_browser_proof.mjs`: simulated outputs tested at 320, 390, 1440 pixels in real Chromium, no JavaScript, normal navigation, no outside requests or overflow. Each output is stamped `modelCalled:false` and `realModelAuthorshipVerified:false`.
- `.github/workflows/runtime-e6-w5a-model-design.yml`: exact-head read-only CI; validates inherited W1/W2 contracts/Next static build plus W5-A security tests and simulated-browser integration. CI deliberately never starts Ollama, downloads a model, uses provider credentials or claims real model inference. W4-D remains an independently verified parent checkpoint; this workflow does not repeat its expensive Blender runs.

## Actual local model invocation — owner hardware required

Ollama can use downloaded open-weights models but local compute/storage requirements depend on model size. Choose a model actually supported by your hardware. Installing, downloading, launching and running that model are **not completed by GitHub CI or this PR**. This is an opt-in procedure, not a claim of successful inference.

On a clean W5-A checkout in Ubuntu or Windows WSL2 after installing Ollama and acquiring a model explicitly chosen for the machine:

```bash
# Separate setup, once, only if you have installed the local model yourself:
ollama list
# Example model string only; choose an actually installed model.
cd tools/enterprise/e6_studio/next-studio
E6_W5A_ENABLE_LOCAL_INFERENCE=YES E6_W5A_MODEL='YOUR_INSTALLED_MODEL_TAG' node w5a_local_ollama.mjs
```

The script refuses execution without an exact explicit opt-in and a model tag. It talks only to `127.0.0.1:11434`; it will not install/download any model or contact a paid cloud service. On accepted model JSON, it creates a **write-once** local `target/e6-w5a-local-model-research` with `/` and `/approach/` plus source hashes. The `local-inference-receipt.json` distinguishes `actualNetworkResponseReceived` from **unverified** `inferenceEngineAttested` and `independentAuthorshipVerified`, both false. It contains no secrets or model output prompts. Repeated output creation fails closed. Do not upload private user briefs or actual model transcripts without approval.

Optional preview, after local generation:

```bash
python3 -m http.server 8774 --bind 127.0.0.1 --directory target/e6-w5a-local-model-research
```

Open `http://127.0.0.1:8774/`, then use native navigation. `Ctrl+C` stops it. No local IP server is needed for continuing GitHub development.

## Non-goals, risks and next engineering stage

This **does not** establish a real model run, since no local Ollama was available in the GitHub CI environment. The model could produce invalid design, copyrighted copy, invented claims or an inaccessible experience: reject bad responses, clearly mark content fictional and require owner/human review. The 4.5:1 token check does not prove full WCAG 2.2 AA. A static compiler with three bounded compositional variations is not equivalent to fully original high-end cinematic website code generation.

**W5-B:** independent held-out briefs, local model runtime fingerprint/inference capture, source-controlled evidence of genuine outputs, structured model-authored component/code proposals in a locked sandbox, automated DOM/visual/security/novelty evaluations, rejection/repair loops and separate real-model acceptance. W6: independent device, creative, accessibility and production security acceptance. E6 Task7/8, Runtime SSE/owner authorization #23, Platform backend authorization #251, Stage45 nine attestations and hardware GO remain unresolved. Do not merge or deploy this review branch.

# E3.9G-12A — Apache-licensed coding-model pilot with genuine Chromium

**Draft research, stacked on PR #168** exact verified head `46ce8cc44107929f95bebd590263bb2a2300c0ba`. Tracked in issue #169. Protected `main` must remain unchanged; **Stage45 NO_GO**.

## Upstream choice and important licensing difference

The direct official [Ollama `qwen2.5-coder:1.5b` model page](https://ollama.com/library/qwen2.5-coder:1.5b) identifies the **1.54B**, roughly **986 MB Q4_K_M** coding model, published manifest prefix **`d7372fd82851`**, embedded **Apache License Version 2.0**. The [upstream Hugging Face Qwen2.5-Coder-1.5B-Instruct card](https://huggingface.co/Qwen/Qwen2.5-Coder-1.5B-Instruct) also declares **Apache-2.0**.

**Do NOT silently substitute `qwen2.5-coder:3b`:** the official [Ollama 3B listing](https://ollama.com/library/qwen2.5-coder:3b) and [upstream model card](https://huggingface.co/Qwen/Qwen2.5-Coder-3B-Instruct) state **Qwen Research License**, not Apache-2.0. Full terms require separate review before using it commercially. Small 1.5B may be weaker than prior Granite/Hermes 3B; its license is the primary motivation here, **not predicted higher quality**.

The full model-manifest SHA256 is queried directly from **actual local** `/api/tags` after the reviewed official Ollama tag is pulled and recorded for every completion. The running Ollama container also checks its local `ollama show --license` output contains **Apache License** and **Version 2.0**. An exact upstream 12-hex digest **prefix** and size bound are enforced; this is **NOT full upstream signed model attestation** and a mutable tag is not a fully immutable supply-chain lock. No private/hardware GPU or user-PC weights installed.

## Pilot and strict scoring

The exact **E3.9G-10** original-vs-source-targeted prompts are reused, with byte-identical prompt-building functions and pinned four-skill excerpts, on the same three **already-public synthetic** website fault cases:
- `mobile-940`: CSS mobile overflow.
- `reduced-boolean`: user reduced motion ignored.
- `scroll-24`: scroll progress stuck at a constant.

One actual coding-model Ollama call per arm × 3 cases = **six genuine attempts if all CI steps finish**, with 5GiB max model-container RAM, 2 CPU, 128 PIDs, no new Linux capabilities, read-only root, loopback 127.0.0.1, explicit opt-in. Raw responses, model digests, source/before-Chromium SHA256, prompt hashes, separately recorded token count and wall time appear as CI artifacts.

**Same immutable repair policy**: `e39g9_multidomain_worker.permitted` remains unchanged. Only `e39g12a_coder_gate.py` inside the existing `run_locked_worker.sh` network-none Docker runner may write disposable site files under `target/e3-factory/site`, and only after original single-literal policy and exact canonical source-byte verification. No web site, repo source, user PC or deployment changed. Rejected code is never executed or given a 'browser repaired' score.

Independent `e39g12a_coder_browser.mjs` tests actual mobile **390×844** and desktop **1280×800** Chromium only after approved patches. An independent `e39g12a_coder_score.py` verifies code source, model response, screenshot hashes, each viewport outcome and JS-error absence before recording a genuine repair. Deterministic cleanup/reset is **never** a model repair.

Run Windows and Ubuntu adversarial checks for model/digest drift, unapproved paths and arm, no owner authority, no prompt/evaluator leakage, exact frozen repair policy. Run complete Java 21 orchestrator regression and retain all existing skill/source checks.

## Precise evidence limitations

This is a **small code-specialist single-model pilot, not the complete E3.9G-12 acceptance**. Existing Granite/Hermes runs from PR #166 and PR #168 are earlier historical cohorts, **not simultaneously randomized matched comparison**; do not claim causal superior performance. These tasks are already public synthetic cases on ONE fixture; they are not independently sealed or representative of real websites, WCAG 2.2 compliance or production security.

To fully close #169 we must add **two independently created disposable websites**, blinded separately curated functional and defensive security faults, separate reviewed typed patch rules for new source files, repeated trials/hardware budget, and genuine cross-site browser repairs. User principal enrollment, authorized action leases and separate Stage45 release remain blocked. Never merge protected main automatically.

Do not claim any model success number until the **exact PR head CI** is completed.

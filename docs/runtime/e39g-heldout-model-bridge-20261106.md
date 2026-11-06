# E3.9G held-out model-only runner — existing B4G to B4H integration

6 November 2026. This is an executable model-input/receipt bridge, NOT a new
roadmap stage or a claim of held-out success. The B4L 1/2 known-public result,
B4N semantic browser controls, and original CI gates remain unchanged.

The new runner takes ONLY the canonical broken-only ZIP from B4G, checks 4–12
cases across at least 2 sites with exact schemas/hashes and reference-field
absence, verifies an already installed local Ollama model's full digest,
and makes one local-only generation call per case. A partial/malformed response
cannot produce a completed receipt. Output is exclusively created with
restricted file permissions and the B4H untrusted-model-receipt schema.

After a genuinely independent human curates licensed private cases, keep the
curator folder, golden references and private salt entirely outside the model
runner. Copy only the B4G broken-only ZIP and this runner plus its B4G helper
to a separate no-checkout worker with OS-level egress limited to local Ollama.
The script does not itself create an OS/network sandbox or prove curatorial
independence. The installed model digest must be pinned by the operator.

Example, from a model-only directory with existing local Ollama:
python e39g_heldout_local_model.py /outside/model.zip /outside/replies.json --model qwen2.5-coder:7b --model-sha256 ACTUAL_64_HEX_DIGEST

Then use the original B4G verify and B4H admit commands in the private
evaluator; only separately approved site-specific typed policies and real
mobile/desktop Chromium after-state can qualify model repairs. No model output
is automatically applied by this runner.

CI tests call a MOCK Ollama and cover denial/receipt interoperability on
Ubuntu and Windows. No real model calls, no private challenges, no independent
human certification, no new model repair successes, no owner approval and no
Stage45 release approval are claimed. E3.9G remains NOT ACCEPTED; Stage45 NO_GO;
S-B62-A physical fencing remains blocked; XERJ HOLD.

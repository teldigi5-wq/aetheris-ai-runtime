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

Native Windows receipt protection: POSIX 0600 does not enforce a Windows DACL. The CLI explicitly refuses to generate private model-output receipts under native Windows. Use WSL2 Ubuntu/Linux with a verified private filesystem and separate OS sandbox, or design and independently test a Windows ACL mechanism before native execution. Cross-platform CI asserts Windows refusal, not a successful private write. The CI steps use bash with set -euo pipefail to propagate every test failure; a previous Windows test failed although GitHub marked the multi-command job successful at Actions #42259732689. That initial green result is NOT valid cross-platform acceptance.


## 7 November 2026 — functional real-model four-case public smoke (not held-out)

A new PR job runs the actual pinned Apache-2.0 Qwen2.5-Coder 7B against
**four known/public developer-authored sources** spanning three research sites:
B4I keyboard focus, B4I reduced motion, CityLine station query HTML injection,
and StudyNest responsive grid overflow. Source bytes are fixed to repository
Git blob hashes. The source-packet builder intentionally uses a PUBLIC dummy
commitment, not B4G's private evaluator seal. The model-only job has NO
checkout and receives only the broken-source bundle plus the model runner.
It starts a pinned, resource-restricted Ollama container on loopback and
verifies the full model digest before four actual generation calls.

A third, separate GitHub job independently reconstructs each public input
and reads the model's untrusted B4H-format receipt. It classifies every reply
with the previously reviewed B4H static quote-intake policy. **No model
patch is applied, no HTML executed, no Chromium after-state credited and
no held-out inference claimed.** The model claim is backed by that CI job's
runtime logs but lacks independent signed model provenance. The public
corpus is NOT an independently curated private challenge, and B4H's
curator-seal-verified 'admit' command is intentionally not invoked.

This demonstrates genuine model-generation pipeline integration, not
the E3.9G independent held-out acceptance that still requires external
licensed human curation, private solution secrecy, owner-reviewed
site-specific typed patch policies and real two-viewport browser scoring.
The historical 1/2 public B4L score remains unchanged. Stage45 NO_GO,
S-B62-A HOLD, XERJ HOLD; no protected-main merge or deployment.

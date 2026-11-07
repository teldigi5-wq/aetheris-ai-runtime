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

## 7 November 2026 — single-variable JSON generation treatment

The first exact-head real-model public run [#42327626109](https://github.com/teldigi5-wq/aetheris-ai-runtime/actions/runs/42327626109) **PASSED 5/5 jobs**: four actual Qwen2.5-Coder 7B responses with full digest `dae161e27b0e90dd1856c8bb3209201fd6736d8eb66298e75ed87571486f4364` from a no-checkout, bounded, loopback model runner, followed by independent static evaluation. **All four were SCHEMA_REJECTED; 0/4 quote-eligible, 0 applied and 0 browser verified.** The raw model replies were surrounded by Markdown fences and two used JSON arrays, despite the six-string-field instruction. A focused inspection also found `top:0` in the focus answer (previous Chromium evidence shows clipping) and the wrong animation target in the reduced-motion answer. No reformatting/stripping of raw model receipts is permitted.

The next pre-registered comparison changes only the Ollama `/api/generate` request field **`format: "json"`** while preserving the four pinned source files, prompt text, model name and full digest, temperature, token limit, B4H static admission, no-checkout model role and zero applied patches. Mocked model-call tests now require this JSON-format flag. Do not treat a JSON-eligible quote as a validated CSS/HTML repair: independent typed policies and real Chromium remain mandatory. There is no independently private held-out corpus. Stage45 NO_GO / XERJ HOLD.


## Verified 7 November 2026 run reconciliation — no held-out acceptance

This dated addendum updates the evidence trail; it does not overwrite the earlier experiment results or create a new stage. It reflects the reviewed E3.9G draft PR #283 head \`7c299ad62b39ef12ae36f1036689d15fded73edf\` and the actual GitHub Actions job logs at that exact commit. Both protected main branches remained on their historical pinned commits at the time of this review.

- [Full E3.9G model bridge workflow #42331508563](https://github.com/teldigi5-wq/aetheris-ai-runtime/actions/runs/42331508563): **completed / success, 5/5 jobs**. A no-checkout, loopback-only, resource-limited Ollama model job logged four actual Qwen2.5-Coder 7B calls with pinned full model digest \`dae161e27b0e90dd1856c8bb3209201fd6736d8eb66298e75ed87571486f4364\`; separate static intake classified **three QUOTE_MATCH_REVIEW_REQUIRED** and **one LITERAL_BOUNDARY_REJECTED**. This job credited **zero browser-verified repairs**. These cases are developer-authored, previously public, and not held-out. Its Windows/Linux interoperability tests mock model transport; private Windows receipt creation remains prohibited. Artifact record: \`e39g-public-four-cases-untrusted-static-admission-score\`, SHA-256 archive \`949a7b6b304cbdc1fa61db624363ea63922c51906fb8c45c29c687ff43c59aeb\`.
- [Separate browser review workflow #42331508693](https://github.com/teldigi5-wq/aetheris-ai-runtime/actions/runs/42331508693): **completed / success, 1/1 job**. Real Chromium checked two **known-public** candidate edits at 390px and 1280px, with zero network requests and no page errors. CityLine fixed the station-input \`innerHTML\` sink using \`textContent\`; StudyNest removed the mobile grid minimum-width overflow. The reviewer **declined** the other two proposed edits (focus clipping and incorrect reduced-motion target). Its summary is **two public browser-verified edits from four considered public proposals**, **not 2/4 held-out model success**.
- **Different evidence lineage:** the browser-review script refers to the frozen older [model workflow #42329086279](https://github.com/teldigi5-wq/aetheris-ai-runtime/actions/runs/42329086279) at source commit \`c014aa7052d8a807e5a7dd8787bc0129a7b1213e\`. It has \`modelCallsPerformed:0\` and hard-coded literal edits, while the latest full bridge run has different real inference receipts. Passing the two workflows separately **does not** prove that the new four model outputs were browser-tested or that the frozen edits are mechanically bound to the original immutable model receipt. This linkage must be explicitly verified in a future evaluator step, not assumed from the workflow title or source comment.
- The frozen browser file \`tools/enterprise/e3_factory/e39g_public_model_candidate_browser.mjs\` currently checks exact original-source SHA-256, single-edit reversibility, two viewport behaviors and no network/page errors; it **does not download, hash, re-parse, and validate the referenced original model-output artifact**. A future reviewer should require immutable response-artifact provenance, quote agreement, typed patch-policy admission, and independent model/evaluator custody before crediting inference-to-repair attribution.

**Unfinished existing E3.9G acceptance, in order:** obtain independently human-curated *licensed* private defects spanning distinct sites with proof of defects before repair; keep solutions/expected patches, private salts and evaluator sources inaccessible to the model worker; run a digest-pinned genuine model with bounded OS/network rights and one raw recorded attempt per case; bind each receipt to exact broken-source and model digests; independently admit only narrowly typed patches (never arbitrary scripts/shell); verify before/after behavior at mobile and desktop Chromium including accessibility, reduced-motion and injection negatives; count rejections/abstentions/failures and retain immutable artifacts for an external reviewer. Neither these public cases nor a newly invented local fixture can substitute for independently hidden challenges.

**Decision:** E3.9G HELD-OUT ACCEPTANCE NOT MET. No E4/E6 promotion from these results; S-B62-A physical fencing BLOCKED; Stage45 NO_GO; XERJ HOLD; Runtime #23 and Platform #251 remain open. This is documentation-only reconciliation with no model-policy weakening, new model claim, merge, deployment, or XERJ submission.

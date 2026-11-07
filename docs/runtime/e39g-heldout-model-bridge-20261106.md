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


## 7 November 2026 — latest exact-head CI closure and independent-curator handoff

**This section supersedes the older, time-local claim above that the browser
reviewer does not bind the frozen model receipt.** Do not rewrite the older
run's evidence or treat its independent/public-only scope as changed.

The exact-head draft [PR #283](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/283)
at `54b55830fe1a317ed85aec622c1b465fabdcd21e` has now passed
**both** required workflows:

- [Full model bridge #42371847547](https://github.com/teldigi5-wq/aetheris-ai-runtime/actions/runs/42371847547):
  **SUCCESS, 5/5 jobs**. Linux and Windows contract tests, public source
  packaging, four actual resource-bounded Qwen2.5-Coder 7B model calls under
  the pinned digest, and separate static intake all passed. Static receipt
  classification is **3 `QUOTE_MATCH_REVIEW_REQUIRED`, 1
  `LITERAL_BOUNDARY_REJECTED`, 0 browser repairs credited**. This is
  genuine inference but on *already known public developer-authored* cases.
- [Receipt-bound Chromium #42371847549](https://github.com/teldigi5-wq/aetheris-ai-runtime/actions/runs/42371847549):
  **SUCCESS, 1/1 job**, including **6/6 negative/provenance unit tests**.
  The separate reviewer validates exact frozen raw public model-receipt bytes
  (SHA-256 `a8a2dc8c8aaee525551b7dabda645b1ac3654144ba3d307ec4d4e846612eec54`)
  from [historical public model run #42329086279](https://github.com/teldigi5-wq/aetheris-ai-runtime/actions/runs/42329086279)
  before applying the two frozen, narrowly reviewed edits. Chromium at
  390px and 1280px demonstrated the *broken* CityLine injection behavior
  and the *fixed* text-only behavior; StudyNest's original mobile overflow
  and fixed responsive layout passed. No network requests or page errors.
  The other two public proposals remain rejected. The browser job made
  **zero new model calls** and is **not evidence that the latest model
  bridge receipts were the browser-test inputs**.

The first preview browser run
[#42371654067](https://github.com/teldigi5-wq/aetheris-ai-runtime/actions/runs/42371654067)
**FAILED** due to an unreliable SVG onload probe and same-document script
redeclaration. That failure is not erased: the final commit uses a
network-free SVG animation callback and fresh document navigation. Only
[#42371847549](https://github.com/teldigi5-wq/aetheris-ai-runtime/actions/runs/42371847549)
is the accepted browser-test evidence at the latest exact head.

### Independent human curator: action needed outside this repository

Use the already-reviewed
[B4G curation protocol](e39g12b4g-independent-curation-protocol-20261025.md)
and [B4H evaluator admission](e39g12b4h-private-evaluator-admission-20261025.md).
**No independently human-curated private corpus, external reviewer acceptance,
or genuine blind score is presently evidenced.** Do not fabricate the missing
cases, rename the four public tests, or upload reference files, salts, private
receipts, identities or customer website content.

Before any model inference on private challenges, a genuinely separate human
curator/evaluator should:

1. **Document authorization, originality and provenance offline.** Obtain
   unambiguous usage/reproduction rights for **4–12 previously unseen**
   disposable website defects spanning **at least 2 independent websites**.
   Keep the URL/source-license evidence, human reviewer decision, authored
   dates and pre-registration timestamp in *private* custody. The existing
   `sourceLicense` manifest field is only a **claim**, not a verified license.
   Do not use real users, live customer accounts, third-party cookies,
   unknown copyrighted code, credentials or private data.
2. **Pre-register objective negative and reference tests before inference.**
   Independently observe each broken source's defect with a disposable,
   offline Chromium reproduction at **390px and 1280px where applicable**,
   including keyboard focus geometry, reduced motion, layout and injection
   negatives appropriate to the domain. Approve independently repaired
   reference behavior and its acceptance tests. Fix/freeze prompts, sites,
   cases, test assertions, expected policy and scoring before making model
   calls; timestamp/hashes alone do not prove human independence.
3. **Enforce true custody separation.** Keep
   `manifest.json`, `cases/<id>/broken.html`,
   `cases/<id>/reference.html` and B4G's private evaluator seal in an
   isolated curator-owned directory **outside Git and the model worker's
   filesystem and identity**. Stage and verify the *broken-only* ZIP using
   B4G. Do not let `reference.html`, reference digests, secret salt, grader
   code, or hidden scoring rules cross into the model worker. The current
   B4G/B4H scripts do **not** prove OS isolation or independent authorship.
4. **Run one frozen, bounded real model attempt per case.** The digest-pinned
   local Ollama worker must receive only the B4G broken-only ZIP and required
   minimal runner. Independently enforce OS/network egress restrictions,
   no checkout, no privileged API, no credentials, resource budgets, and
   full digest verification. Preserve every raw answer, abstention, timeout,
   malformed result, attempted source digest and model/cost/time record.
   Native Windows model receipt handling is refused by the current runner:
   use an independently checked Linux private filesystem/isolation boundary.
5. **Evaluate only in the separately trusted evaluator.** Verify the private
   seal with B4G and classify model receipts through B4H. Review *new*
   site/fault-specific typed patch policies and negative/malicious-edit
   tests **before** authorizing any disposable patch application. Quote
   matching is NOT semantic acceptance. Execute only the reviewed fixed
   one-literal operation in a no-network, no-new-privileges test site;
   never shell/JS/model tool requests. Independently confirm source-to-after
   hashes and actual mobile/desktop Chromium behavior, accessibility,
   security and regression assertions, then report **all** attempts.
6. **Obtain external sign-off on the private evidence, not just checksums.**
   The trusted report must distinguish `attempted`, `abstained`,
   `schema denied`, `quote eligible`, `typed-policy admitted`,
   `browser verified` and `regressed`. Count every case in the
   denominator; keep origin/timestamps, immutable raw artifacts and all
   failures. Independent human reviewers must verify custody, license
   scope, before behavior, reference correctness and case secrecy.

**Exact existing gate remains:** E3.9G **HELD-OUT ACCEPTANCE NOT MET**.
The public browser result is **2/4 known-public case edits** (an older
separate model receipt), not new hidden accuracy. The latest full-bridge
static result is **3 review-required / 1 rejected / 0 browser-credited**.
Do not combine those two runs as if they scored the same live inference.
Runtime security #23 OPEN, Platform #251 OPEN, S-B62-A physical fencing
BLOCKED, Stage 45 NO_GO and XERJ HOLD. No protected-main merge, deployment,
owner API authorization, stage renumbering or source contribution.

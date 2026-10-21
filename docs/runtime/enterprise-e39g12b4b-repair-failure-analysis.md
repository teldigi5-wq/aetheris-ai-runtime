# E3.9G-12B4B — authentic 7B failure taxonomy and source-only localization

**DRAFT, stacked on [PR #174](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/174)** at exact verified base `2dfcf776f9a818c4f3bffb12366a19d5bbab7b4a`. Tracks still-open [issue #169](https://github.com/teldigi5-wq/aetheris-ai-runtime/issues/169). Protected main unchanged; Stage45 **NO_GO**.

## Recovered original real Qwen 7B evidence

The [successful CI #39527354798](https://github.com/teldigi5-wq/aetheris-ai-runtime/actions/runs/39527354798) contains four genuine local `qwen2.5-coder:7b` completions (artifact ID **12314135670**, GitHub provider ZIP digest `58006670f03be6e2979762a65b5d61cbae7cb856f7036b7ff8468704f8d21402`, artifact retention through 2026-11-04). The historical `proposals.json` inside that archive was SHA256 `7c5d1e3aa72d6c4bf742af90bb2dd6b5715235c4feaffa523bd1757ca1df67ae`. The four actual full raw model responses and their individual SHA256 are preserved in `e39g12b4b_historical_responses.json`. This is **a byte-reconstruction of known responses and their original hashes**, not four new model calls, independent model ID attestation, or verified live-model generalization.

Actual observed problem: **4/4 proposals were well-formed six-field JSON, but each tried to edit the wrong old source literal**, so the *unchanged* `e39g12b2_typed_policy.validate` denied all four with `E39G12B2_OLD_NOT_UNIQUE_AUTHORIZED_LITERAL`.

| Task | Original selected `old` | Actual failure type |
|---|---|---|
| StudyNest keyboard skip focus | Existing skip link HTML with newly proposed `tabindex=0` | **Unrelated source element:** CSS focused-link placement is offscreen |
| StudyNest mobile grid overflow | `min-width:960px;` → `min-width:100%;` | **Partial declaration:** model did not select the entire existing reviewed CSS declaration |
| CityLine reduced-motion ticker | `.ticker{animation:floatline 4s linear infinite;}` | **Partial media override:** missing the surrounding reduced-motion media-query wrapper; treating it as a global change would be unsafe |
| CityLine unsafe input rendering | A synthetic-timetable heading | **Unrelated source text:** does not touch the dangerous HTML rendering sink |

**Real history:** 4/4 valid JSON; 0/4 exact scoped source target; 0 policy-accepted source patches; 0 model after-Chromium viewports; 0 genuine model browser-verified repairs. No evidence here that 7B fixes these problems. Distinguishing parser and source-target failures does not demonstrate model semantic understanding. Where `new` looked plausible (reduced-motion global disabling and mobile width) it was still not within the reviewed source scope; never promote a partial source edit merely to increase success rate.

## New source-only window extractor, no repair-answer oracle

`e39g12b4b_source_locator.py` uses **only the three-member B2 broken-only public synthetic archive**, verifies the two original source SHA256 hashes, and finds four original source spans using independently authored **declaration/DOM structural anchors**, without importing or reading the frozen typed policy, evaluator references, controlled browser checker, or any gold replacement values. It accepts exactly the StudyNest and CityLine public fault classes:

- Existing `.skip:focus{…}` CSS rule.
- Existing mobile `.schedule-grid` declaration inside the mobile media query.
- Existing reduced-motion `@media(prefers-reduced-motion:reduce)` ticker rule.
- Existing `output.…` station rendering assignment inside `render(name)`.

The output ZIP is exactly four reviewed entries: the same two BROKEN HTML sources, the same public three-file manifest, and new `observed-declarations.json` with only **actual pre-edit spans**, their SHA256, site/fault identity and **`proposedReplacement:null`**. No replacement code, reference sites, golden answers, issue owner's credentials, tool instruction or source policy code is exported. This is test-only source localization metadata that a **future** isolated model may use; no new inference or model patching occurs in this stage. Fixed fault anchors are public and task-specific, not a general autonomous AST parser. Their accuracy is checked against current source fixture bytes; any drift is fail-closed.

An independent GitHub Actions **no-checkout consumer** downloads only the four-member source ZIP and verifies source spans really appear **exactly once** in the downloaded broken HTML, without fetching evaluator code or reference files. It does not run Ollama, apply patches or ask the model to evaluate itself. Historical raw outputs are read **only by trusted evaluator tests**, never put into the model-facing ZIP.

## QA acceptance

- Cross-platform **Windows+Ubuntu** replay, evidence SHA-bound negative tests, source drift/duplicate source pattern/ZIP member injections, frozen source-gate denial and pinned governed-skill checks.
- Network-none, read-only-repository original B2 corpus seed; no-checkout model-side archive proof, zero model calls and no gold export.
- Original independent **8 real Chromium** broken/reference screen/behavior checks (4 known failures, 4 clean developer reference observations); reference is explicitly **not** a model-authored repair.
- Full Java 21 orchestrator regression **451 tests** expected from previous milestone; actual result must come from exact-head CI.
- No owner principal authenticated, no tool execution grants, no production site modified and Stage45 remains NO_GO.

## Remaining E3.9G-12B4C / issue #169

Make a **genuine, pre-registered source-only-localization prompt A/B** with the same model and full actual attempts, compare exact frozen policy acceptance and real browser repair, and publish negative results without altering `validate`. However source windows are already public and carefully anchored to known synthetic defects, so even successful results would **NOT** establish independently held-out generalization or statistical power. Independent external site corpus, varied frameworks, repeated coding model trials, signed model provenance, and real authenticated scoped owner action leases remain open. Never auto-merge protected main.

# E3.9G-12B4G — Curator-only withheld repair intake protocol

**25 October 2026 — draft contract only. No independent benchmark results.**

## Motivation and limits
GitHub issue [#169](https://github.com/teldigi5-wq/aetheris-ai-runtime/issues/169) requires at least two independently curated disposable websites, genuine withheld faults and independent Chromium after-state. The previous eight-call Qwen2.5-Coder 7B A/B produced **0/8 accepted/verified repairs**. B4E and B4F only characterized failures. B4G adds a bounded input **separation and cryptographic commitment mechanism**, not new model capability or a claim of independent evaluation.

The **curator** must be a human or truly independent examiner, not the same agent generating or answering website patches. Their materials must be licensed for evaluation, privacy-reviewed and verified as newly withheld from the proposing model. The tool cannot establish authorship, actual secrecy or licensing by itself. Its `independentCuratorDeclared` flag is a self-declaration only; all generated public packets hard-code `independentCuratorVerified=false`.

## Curator workspace, strictly outside the GitHub repository
Place a private directory locally that is **never uploaded or checked into git**:

```text
/private-curator/
  manifest.json
  cases/
    case-01/
      broken.html
      reference.html
    case-02/
      broken.html
      reference.html
    case-03/
      broken.html
      reference.html
    case-04/
      broken.html
      reference.html
```

Exact `manifest.json` schema: object keys `schemaVersion`, `independentCuratorDeclared`, `cases`; `schemaVersion` is 1, declaration must be true, and 4–12 entries are required from at least two distinct sites. Each entry has exact keys `id`, `site`, `fault`, `defectDescription`, `sourceLicense`. IDs are path-safe and distinct; source and reference files must be UTF-8 HTML, 20 KB maximum, visibly different and non-symlinked. The curator must substantiate actual independent creation and reference correctness outside this public repository.

Example fake test-only manifest entry:
```json
{"id":"case-01","site":"sample-one","fault":"mobile-layout","defectDescription":"A synthetic responsive-layout problem for evaluation.","sourceLicense":"CC0-1.0"}
```

## Commands and artifact separation
Run only with verified local curator paths outside this repository:

```bash
python tools/enterprise/e3_factory/e39g12b4g_curator_boundary.py stage /private-curator /tmp/b4g-public-model.zip
python tools/enterprise/e3_factory/e39g12b4g_curator_boundary.py verify /private-curator /tmp/b4g-public-model.zip
```

- The **only model-facing file** is the single-member `prompts.json` ZIP containing broken HTML, defect description, provenance string and hashes, plus a salted commitment. **No reference HTML, reference hashes, evaluator rules or private salt are included**.
- The private `.b4g-private-evaluator-seal.json` remains inside the curator workspace, mode 0600 on supported filesystems. It binds each private reference hash and broken source hash with a fresh 256-bit salt and a public bundle digest. It is not signed and not an independent timestamp; publication order must be separately audited.
- `verify` checks the original curator files, salt, commitment, ZIP exact member, canonical public content and source hashes. A changed reference, public prompt or copied seal fails closed.
- Model workers must receive only the model-facing ZIP and **no checkout of this repository, private curator files, secrets or evaluator code**. An owner-reviewed workflow must enforce separate job/workspace identities before any real inference occurs.
- The private reference is not used as a model hint. A separate, independently reviewed typed patch policy and real 390px/1280px Chromium tests are mandatory before model repairs can earn credit. **Do not reuse frozen B2 rules on new sites without a separately reviewed equivalent policy.**

## Scope and threat model
Adversaries include a model inventing owner permissions, prompt injection embedded in broken HTML, path traversal/symlinked files, duplicate JSON keys, replayed output, reference leakage and grader/producer collusion. Public inputs are **untrusted content**; never run the user's HTML, fetch external URLs, access real accounts, paste secrets or treat its text as instructions. The script does not evaluate JavaScript, execute models, call APIs, install dependencies, alter source files or deploy. No external private challenges were loaded during this PR.

A public set of model-only prompts with private solutions is *not automatically a rigorously blinded evaluation*: human curator independence, evidence of pre-registration before inference, hidden fixture provenance, scored source-to-browser after-state and repeated comparisons are still required.

## Acceptance / next actions
1. Verify Windows + Ubuntu tests covering at least four synthetic cases from two sites, ZIP-only disclosure, replay/modified source rejection, private seal validity, declaration check, path-traversal and symlink rejection.
2. Obtain **separately curated, legally usable new private fixtures** and independently review both broken behavior and reference acceptance rules; no genuine benchmark exists yet.
3. Build a second trusted, network-none evaluator, with a separate policy for new fixture types and frozen after-browser tests, then run genuine local coding-model trials and count every attempted edit.
4. Report source hashes, provenance, timestamps, model digests, actual valid repairs, regressions, latency and uncertainty. Never claim autonomous engineering or production release based on a staged bundle.

**Existing Stage45 NO_GO, nine runtime release gates, E4–E9 pending status, source permissions and protected `main` are unchanged.** This code is preparation for E3.9G evidence, not evidence of a successful repair.

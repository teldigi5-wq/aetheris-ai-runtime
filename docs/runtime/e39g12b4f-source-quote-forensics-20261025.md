# E3.9G-12B4F — source-literal forensics and semantic-gate separation

**Status: DRAFT research.** Reuses genuine prior Qwen2.5-Coder 7B evidence; **no new model inference**, no model repairs, no changed gate, no production agent authority. The existing B4D and B4E work passed their scoped CI but did not prove autonomous coding competence.

## Why this small slice matters

Real benchmark [#188](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/188) yielded zero approved repairs from 8 model calls. [B4E #189](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/189) classified policy denial reasons. B4F separately inspects whether a response is structurally valid, identifies the correct site/fault, retains the original SHA-256 and quotes *exactly once* the allowed original source literal. It then explicitly stops before considering semantics or granting permission.

The B2 policy's authorized `old` literals and fixed validation are consumed **read-only, evaluator-side only**. Nothing here is imported into the no-checkout model runner or trusted as a standalone repair policy.

## True baseline: known public synthetic fixtures only

| Arm | Real calls | B4D policy passes | B4D browser-verified repairs |
| --- | ---: | ---: | ---: |
| Original | 4 | 0 | 0 |
| Existing source-span hint | 4 | 0 | 0 |

Example observed failures in raw source-locked CI evidence: the original mobile answer replaced `min-width:960px;` while the authorized full target was a larger layout declaration; the original keyboard answer added `tabindex` to the link instead of fixing the hidden focus CSS; the source-span keyboard answer retained an off-screen top value; the source-span reduced-motion reply assigned a CSS declaration as the `fault` ID; and the source-span unsafe-text response changed the sink but failed the exact allowed expression. None represents a passing repair.

## Files and execution isolation

- `tools/enterprise/e3_factory/e39g12b4f_quote_diagnostics.py` — offline read-only ZIP inspection; first runs B4E artifact provenance, then verifies model receipt/patch/source identity, raw response SHA256, exact 6-field schema, correct case, unchanged signed-out permissions, literal uniqueness and whether `old` matches the B2 canonical literal. Prints classifications only, **never raw model content, source edits or an approval decision**.
- `test_e39g12b4f_quote_diagnostics.py` — fully synthetic cases, explicit 0/8 acceptance invariant, tampered source/reply hashes, nonexistent evidence, forged owner flag, fake policy pass and malformed response checks.
- `.github/workflows/runtime-e39g12b4f-source-quote.yml` — Windows and Ubuntu unit/policy regression; does not download or generate a model and requires no cloud model key or paid API.

To inspect an **already downloaded** CI artifact locally (do not upload sensitive code or tokens):

```bash
python tools/enterprise/e3_factory/e39g12b4f_quote_diagnostics.py /path/to/b4d-independent-browser-compare.zip
```

## What must follow

1. Validate actual B4D source-quote diagnostics and preserve the 0/8 independently scored repairs as a **negative baseline**. No instruction-tuning or golden test edits to make the model appear better.
2. Design a new blinded input/evaluator separation: an independent human-curated, previously unseen corpus stored in the evaluator workspace, with *broken-only public-safe source* sent to the model. A new synthetic test added to this public repo is not automatically independently held out.
3. Trial schema-constrained model output and exact-source targeting with fixed budgets; change **only the proposer/prompt** in an isolated branch, not E3.9G-12B2 frozen acceptance rules. Independent Chromium after-state remains the only repair credit.
4. Keep E3.9H 3D benchmark/model-authored scenes, E4–E9, owner device Execution Phase A and 9 release gates distinct. No host execution, web publication or real money authorization.

**Explicit NO_GO:** Platform authorization and database migration issues still need integration; Stage45 retains 13 blockers. This slice is an improvement to forensic evidence, not a model accuracy gain or a release.

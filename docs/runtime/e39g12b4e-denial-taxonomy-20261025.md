# E3.9G-12B4E — read-only forensic classification of real Qwen7B repair failures

**Checkpoint:** 25 October 2026. Stage E3 research remains open. Zero new model calls, no altered patch policy, no genuine new model repair, no released website. Stage45 `NO_GO` and owner-PC `BLOCKED_PENDING_HARDWARE` remain in force.

## Traceable basis

Exact source-run evidence: [PR #188](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/188), [Actions #40157740781](https://github.com/teldigi5-wq/aetheris-ai-runtime/actions/runs/40157740781), actual `b4d-independent-browser-compare` artifact 12585189470, model receipt artifact 12584963481. Qwen2.5-Coder 7B produced eight genuine responses. The unchanged network-none B2/B4A gate rejected all eight: **0/4 original vs 0/4 existing-source-span hint**; 0/8 policy-approved and no after-repair browser qualification. The two sites are known synthetic fixtures, not independently held out.

## Exact denial distribution from source CI patch.json, not inferred model competency

| Treatment | Case | Source-policy reason | Category |
| --- | --- | --- | --- |
| Original | StudyNest / keyboard focus | `E39G12B2_OLD_NOT_UNIQUE_AUTHORIZED_LITERAL` | Literal targeting |
| Original | StudyNest / mobile overflow | `E39G12B2_OLD_NOT_UNIQUE_AUTHORIZED_LITERAL` | Literal targeting |
| Original | CityLine / reduced motion | `E39G12B2_UNTRUSTED_SCHEMA_WRONG` | Schema contract |
| Original | CityLine / untrusted text | `E39G12B2_OLD_NOT_UNIQUE_AUTHORIZED_LITERAL` | Literal targeting |
| Source span | StudyNest / keyboard focus | `E39G12B2_FOCUS_NOT_VISIBLE_BOUNDED` | Ineffective visual semantics |
| Source span | StudyNest / mobile overflow | `E39G12B2_OLD_NOT_UNIQUE_AUTHORIZED_LITERAL` | Literal targeting |
| Source span | CityLine / reduced motion | `E39G12B4A_CROSS_FAULT_PROPOSAL_DENIED` | Wrong-fault patch |
| Source span | CityLine / untrusted text | `E39G12B2_DOM_HTML_SINK_NOT_REMOVED` | Incomplete sink cleanup |

Original errors: 3 literal/1 schema. Hint-arm errors: 1 literal/1 ineffective/1 cross-fault/1 incomplete. They are categorical reject reasons, not evidence that any rejected patch was safe to deploy. No patch may be manually 'accepted' by label. Separately extracted raw model responses show that some suggested edits superficially resemble fixes, but the deterministic gate correctly requires the complete semantic invariant: e.g. replacing one `innerHTML` usage is inadequate when forbidden sinks remain. The source-span hint alone does not establish trustworthy code reasoning.

## Newly added reproducible offline classifier

- `tools/enterprise/e3_factory/e39g12b4e_failure_taxonomy.py`: validates local downloaded Actions evidence ZIP; explicitly checks the 8 exact patch paths and B4D comparison provenance; denies duplicate keys, missing case records, unknown error codes, forged owner or stage45 approval, archive path traversal, symlinks and oversized members.
- `test_e39g12b4e_failure_taxonomy.py`: synthetic, deterministic adversarial tests for eight real categories, scored-count mismatch, malicious ZIP entries, missing evidence, invalid authorization flags, wrong fault mapping and deny-only behavior.
- `.github/workflows/runtime-e39g12b4e-denial-taxonomy.yml`: read-only Ubuntu/Windows CI; keeps the 14-skill / role reference manifest validation. It does not rerun the expensive eight-call model benchmark or introduce model/tool execution rights.

### Manual reproducibility

Download the `b4d-independent-browser-compare` artifact from run 40157740781, then locally run:

```bash
python tools/enterprise/e3_factory/e39g12b4e_failure_taxonomy.py path/to/downloaded-artifact.zip
```

The script inspects known JSON evidence only and prints a compact JSON taxonomy; it must NOT extract arbitrary files or evaluate HTML/JavaScript/model responses.

## Next genuine capability development

1. Fix/measure structured response fidelity using strict schema and exact existing source-literal quote checks **without changing** the accepted B2/B4A security policy or golden references. Rejected proposals remain rejected.
2. Design separate observed error targeting (CSS/DOM selector provenance) and actual semantic correctness gates (focus visible, no mobile overflow, reduced motion, all unsafe sinks removed). Avoid conflating accurate location with a repaired behavior.
3. Use independently curated and newly withheld synthetic faults to reduce overfitting, multi-model comparisons, stable timeout/cost budgets and repeated runs; measure accepted + browser-verified repair rates with uncertainty.
4. Only after supervised reliable benchmark results, proceed to E3.9H model-authored cinematic 3D test and later E4–E9. Keep Stage45, authentication, actual owner PC and nine release gates independent.

**Release truth:** B4E is a forensic regression and proposal for improving the next experiment. It is not a model-quality improvement or a completed autonomous coding stage. Do not merge this stacked draft into protected `main` without reconciling ancestors and release requirements.

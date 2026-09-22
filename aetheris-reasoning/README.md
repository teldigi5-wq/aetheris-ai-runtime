# Aetheris Reasoning

Stage 30 introduces deterministic reasoning-control primitives that sit **around** models and agents instead of pretending that one model is infallible.

The package is intentionally dependency-light and local-first. It provides:

- meta-reasoning and route selection;
- uncertainty/confidence assessment;
- verifier/critic checks;
- simulation gating for risky side effects;
- tamper-evident decision ledger records;
- source lineage/trust scoring;
- contradiction and temporal checks;
- owner-rule compilation with conflict detection;
- task dependency graphs;
- change-impact analysis.

This is a control-plane foundation, not a claim that arbitrary causal reasoning or natural-language policy understanding is solved. Unsupported rules are rejected for owner normalization, high-consequence actions escalate, and model inference is treated as lower-trust evidence unless independently verified.

## Run tests

```bash
cd aetheris-reasoning
python -m unittest discover -s tests -v
```

## Design boundary

Stage 30 does **not** execute external side effects by itself. Integrations should call these primitives before tools, deployments, trading execution, or other privileged actions, then record the verified decision in the ledger.

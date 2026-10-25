# Aetheris AI Runtime

Independent AI runtime for the Aetheris platform, including orchestration, reasoning, workstation-agent foundations and quantitative intelligence.

This repository is the **source owner** for runtime components that were extracted from `teldigi5-wq/aetheris-platform`. The platform repository consumes this runtime through explicit contracts and certification references rather than carrying duplicate runtime source.

## Repository boundary

### This repository owns

- `orchestrator-service/` — orchestration, mission/tool coordination and governance foundations;
- `workstation-agent/` — bounded workstation, browser and PC-integration foundations;
- `aetheris-reasoning/` — reasoning, retrieval and verification foundations;
- `aetheris-quant/` — quantitative intelligence and fail-closed trading/risk research;
- runtime-side `architecture/`, `configs/`, `scripts/`, `tests/` and `.github/` certification assets.

### `aetheris-platform` owns

- API gateway and trusted ingress;
- identity, user and audit services;
- dashboard and platform observability;
- Docker/Kubernetes/Helm platform deployment assets;
- cross-repository contracts, integration composition and platform-side evidence/certification references.

The runtime-owned source roots above should **not** be copied back into the platform repository as parallel implementations.

## Components

| Component | Purpose | Truth boundary |
|---|---|---|
| `orchestrator-service` | Orchestration, tool/mission coordination and deterministic governance integration | Eligibility/approval does not prove execution success |
| `workstation-agent` | Bounded workstation/browser/PC automation runtime | Physical owner-PC validation remains `BLOCKED_PENDING_HARDWARE` |
| `aetheris-reasoning` | Reasoning, retrieval and verification foundations | Models cannot override deterministic owner policy |
| `aetheris-quant` | Quantitative intelligence and risk/trading research | Live-money execution is disabled by policy; no live-money authority claim |

## Integration with `aetheris-platform`

At the September 2026 platform promotion, the certified runtime checkpoint was:

`68af39a1115a7330020c18b6e2cb601e66b8f22f`

The platform records that checkpoint in `architecture/ai-runtime-certification-reference.json` and integrates the runtime through:

- `contracts/ai-runtime-boundary.v1.json`;
- `docker-compose.integration-external.yml`;
- `tools/load_certified_ai_runtime.sh`;
- platform-side readiness/evidence workflows.

That SHA is a **certification checkpoint**, not a claim that runtime development must remain permanently fixed there. A later runtime SHA should be promoted only after its applicable runtime suite and the platform integration suite are green and the platform certification reference is deliberately advanced.

## Current enterprise and Syntra development (25 October 2026)

**This repo is in active DRAFT research/hardening, not autonomous production.** The platform's historical 34 / 34 repository specification does not mean the runtime, agents, voice or owner-PC functions are release-certified. There is **no Stage 35**; physical machine validation is separately named Execution Phase A and remains `BLOCKED_PENDING_HARDWARE`.

| Track | Verified checkpoint | Remaining acceptance |
| --- | --- | --- |
| Aetheris/Syntra company metadata | 13 proposed companies/100 divisions; canonical main 26 configured specialists; separate Stage46 draft 30 specialists | Independent identity/delegation/approval certification; not 100 running companies |
| Security | [Worker hardening PR #185](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/185), 7/7 previous head CI passed | Integrate isolation, principal, secrets and real stoppability before agent execution |
| Governed visual-design skills | [PR #186](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/186), 3/3 CI passed | Skill references default-off, no privileged tools; zero autonomous cinematic output certified |
| Current E3.9G coding model experiments | Qwen/Hermes/Granite models tested on disposable Chromium faults; Qwen 7B two-site repairs 0/4 | E3.9G-12B4D genuine paired A/B and held-out independently verified repairs |
| E3.9H cinematic capability | Research/standards and negative control only | Independently reviewed procedural 3D positive control, real Chromium, genuine model-generated scene, mobile GPU and owner QA |
| E4–E9 | Candidate routing, code factory and agent/security foundations | Full model selection, website/immersive creation, coordinators, enterprise cockpit and final certification |
| Stage45/46 and physical Windows | Multiple frozen NO_GO/CI artifacts; Stage46 workstreams differ | 13 Stage45 blockers, actual PC GPU/voice/STOP/permissions and release operator authority |

See [reconciled E3–E9 roadmap](docs/runtime/roadmap-current-e3-through-e9-20261025.md), [nine security release gates](docs/security/aetheris-nine-release-gates-20261021.md), and [cinematic standards](docs/runtime/syntra-aetheris-cinematic-website-standards-20261025.md). These documents are source-of-truth engineering checklists, not permissions to run external sites or deploy.

## Certification and truth boundaries

The certified checkpoint `68af39a1115a7330020c18b6e2cb601e66b8f22f` completed the applicable canonical runtime certification suite. The platform certification reference records that checkpoint as `6_OF_6_SUCCESS`.

Certification is intentionally narrower than a production claim:

- physical owner-PC status remains **`BLOCKED_PENDING_HARDWARE`**;
- hosted CI does not prove GPU, microphone, browser, Windows/WSL2, thermal or sustained-load behavior on the future target PC;
- there is **no production-activation claim**;
- there is **no registry-publication claim**;
- there is **no live-money execution claim**;
- CI and evidence gates must not be weakened or bypassed to advance a runtime reference.

## Governance principle

Models and reasoning workers may propose actions, but deterministic policy, approval and verification boundaries remain authoritative. High-risk, destructive, privileged, public or financial actions require the appropriate policy path; an `ALLOW` decision means only that an action is eligible, not that it executed successfully.

## Extraction provenance

See [`README-EXTRACTION.md`](README-EXTRACTION.md) for the deterministic extraction bundle provenance and the historical source-removal gate that preceded the completed two-repository cutover.

## License

Aetheris AI Runtime is licensed under the **Apache License 2.0**. See [`LICENSE`](LICENSE) for the full terms. Third-party dependencies and incorporated third-party materials remain subject to their respective licenses.

Licensing does not alter the runtime certification boundary: the platform-certified checkpoint remains `68af39a1115a7330020c18b6e2cb601e66b8f22f`, physical owner-PC validation remains `BLOCKED_PENDING_HARDWARE`, and no production-activation, registry-publication or live-money execution claim is implied.

## Related repository

Platform repository: [`teldigi5-wq/aetheris-platform`](https://github.com/teldigi5-wq/aetheris-platform)

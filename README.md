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

## Certification and truth boundaries

The certified checkpoint `68af39a1115a7330020c18b6e2cb601e66b8f22f` completed the applicable canonical runtime certification suite. The platform certification reference records that checkpoint as `6_OF_6_SUCCESS`.

Certification is intentionally narrower than a production claim:

- physical owner-PC status remains **`BLOCKED_PENDING_HARDWARE`**;
- hosted CI does not prove GPU, microphone, browser, Windows/WSL2, thermal or sustained-load behavior on the future target PC;
- there is **no production-activation claim**;
- there is **no registry-publication claim**;
- there is **no live-money execution claim**;
- CI and evidence gates must not be weakened or bypassed to advance a runtime reference.

## Runtime Security #23 — owner-read implementation preview (9 November 2026)

The separately stacked **review-only** candidate for authenticated task lookup, scoped recent listing, and paginated task-event headers is documented in [signed-session owner-read review](docs/security/RUNTIME_OWNER_TASK_READ_HISTORY_20261109.md). The code requires cryptographically verified signed Identity, registered session/JTI, signer/epoch, exact membership and fenced workload **in the same PostgreSQL authorization query**, but runs only against disposable CI registry fixtures. Existing Task/Mission HTTP routes remain fail-closed; global SSE stays disabled. No protected-main merge, production migration, authenticated client activation, deployment or release certification is implied. Platform historical Stage 34/34 remains unchanged; Stage 45 remains NO_GO.

## Runtime Security #23 — direct-port bearer admission (9 November 2026)

A subsequent stacked review-only change adds an **unregistered**, independent [Runtime signed-bearer servlet filter](docs/security/RUNTIME_DIRECT_HTTP_BEARER_20261109.md) and real loopback-TCP Tomcat tests. The filter requires pinned RS256 token verification, signed session provenance, an injected live-session authority decision and an appropriate read/write operation scope; without a separately registered authority, it denies all. This is **not** a production-authorized resource server: actual registry migrations/writers, gateway workload trust, direct-port HTTPS/mTLS and per-object admission are not yet approved. Existing Task/Mission HTTP quarantine and global SSE lockdown remain unchanged. Stage 45 remains NO_GO; Platform Stage 34/34 is historical and unchanged.

## Runtime Security #23 — real PostgreSQL-backed HTTP session review (9 November 2026)

The next stacked **draft-only** implementation connects the separately reviewed signed-bearer HTTP filter to an actual, short-transaction [PostgreSQL16 session/signer/epoch row-locked authority](docs/security/RUNTIME_PG16_HTTP_SESSION_AUTHORITY_20261109.md), proved only through disposable CI registry tables and loopback-TCP tests. This is a real JDBC review integration, **not** a registered production security filter, approved durable Identity session registry, or Task/Mission HTTP activation. Real enrollment/revocation writers, restricted DB roles, mTLS and direct-port acceptance remain pending; ownerless Task/Mission routes stay hard-404 and global SSE stays off. Stage 45 remains NO_GO.

## Runtime Security #23 — real Platform-issued JWT → function-only Runtime HTTP session proof (S0-E6L, 9 November 2026)

A new stacked **draft-only** [S0-E6L source-pinned interop review](docs/security/RUNTIME_PLATFORM_E6L_FUNCTION_SESSION_INTEROP_20261109.md) uses actual pinned Platform PR #415 RS256+JTI issuer and atomic refresh-session registration, real separate PostgreSQL16 function-only reader and revoker LOGINs, and real loopback-TCP embedded Tomcat with the *unchanged* Runtime bearer filter. It has executable two-user enrollment, scope, unregistered/legacy-token denial and live revocation tests. This is the first jointly verified **review registry** shared by both source checkouts; it is **NOT** a production registry migration, authorized Gateway-to-Runtime HTTPS/mTLS, object-ownership permission, customer API activation or Runtime certified-SHA promotion. Task/Mission requests stay hard-404, global SSE disabled, Stage45 NO_GO and owner-PC BLOCKED_PENDING_HARDWARE.

## Security #23 — S0-E6O atomic owned Task/Mission resource-read review (9 November 2026)

The stacked [S0-E6O signed-JTI and owner/tenant/project/workload-fenced resource proof](docs/security/RUNTIME_E6O_ATOMIC_TASK_MISSION_OWNERSHIP_20261109.md) adds an **unregistered** Java adapter and disposable PostgreSQL SECURITY DEFINER read function. It uses real pinned Platform-issued tokens, independent limited SQL login and actual loopback HTTP tests. The signed session, live membership, object owner and active workload fence must all be checked **inside the same database transaction that returns the resource**, not as a separate preflight ALLOW. Historic ownerless records and cross-tenant owners are denied; concurrent membership revocation cannot cause a stale resource read. **This is not a production Task/Mission schema integration, endpoint activation, Task write/SSE authorization, approved PKI deployment or release**. Product Task/Mission routes still hard-404, Stage45 NO_GO and physical PC blocked.

## Runtime Security #23 — S0-E6P actual-shaped Task+Mission persisted ownership review

[Read the S0-E6P persisted owner/writer proof](docs/security/RUNTIME_E6P_TASK_MISSION_OWNER_PERSISTENCE_20261109.md). A new **unregistered** function-only Java writer and disposable PostgreSQL16 additive migration rehearsal preserve historic ownerless Task/Mission records, reject owner reassignment, and allow signed Platform Identity JTI Task/Mission creation only when live tenant/project membership and a trusted workload fence remain active **inside the same INSERT transaction**. Separate reader/writer SQL LOGIN accounts lack raw table permissions, and a concurrent membership revocation fails closed. This advances prior Task-only #209/#284 and E6O synthetic reads without activating Mission JPA ownership, production migrations, Task/Mission product APIs, task-to-mission linking, events or SSE. Stage45 NO_GO, owner-PC hardware acceptance blocked; real routes still hard 404.

## Runtime Security #23 — S0-E6Q signed Mission–Task link and event headers (review only)

[Read the S0-E6Q owner-fenced Mission–Task linking and private event-header review](docs/security/RUNTIME_E6Q_MISSION_LINK_EVENT_BOUNDARY_20261109.md). New **unregistered** SQL/Java code verifies a real Platform-signed and enrolled JTI, exact task/mission owner UID and tenant/project, plus live workload fence **inside the same PostgreSQL16 transaction as each link write or bounded event-header read**. Eight disposable tests verify cross-owner and ownerless denial, role isolation, private-event suppression, concurrent membership revocation, and actual session revocation. The canonical product `MissionSessionEntity.taskIdsCsv` is **not** changed or silently treated as permission. No MissionService/TaskEventJournalService/SSE route activation, migration, deployment, or production credential changes are included. Existing Task/Mission HTTP remains unconditional 404 and Stage45 remains NO_GO.

## Governance principle

Models and reasoning workers may propose actions, but deterministic policy, approval and verification boundaries remain authoritative. High-risk, destructive, privileged, public or financial actions require the appropriate policy path; an `ALLOW` decision means only that an action is eligible, not that it executed successfully.

## Extraction provenance

See [`README-EXTRACTION.md`](README-EXTRACTION.md) for the deterministic extraction bundle provenance and the historical source-removal gate that preceded the completed two-repository cutover.

## License

Aetheris AI Runtime is licensed under the **Apache License 2.0**. See [`LICENSE`](LICENSE) for the full terms. Third-party dependencies and incorporated third-party materials remain subject to their respective licenses.

Licensing does not alter the runtime certification boundary: the platform-certified checkpoint remains `68af39a1115a7330020c18b6e2cb601e66b8f22f`, physical owner-PC validation remains `BLOCKED_PENDING_HARDWARE`, and no production-activation, registry-publication or live-money execution claim is implied.

## Related repository

Platform repository: [`teldigi5-wq/aetheris-platform`](https://github.com/teldigi5-wq/aetheris-platform)

# E1 — Aetheris Enterprise Inactive Company Registry

**2026-10-19 · OPEN DRAFT STAGE · NOT PRODUCTION/OWNER-PC CERTIFIED · NO NEW AUTONOMOUS WORKER AUTHORITY**

## Scope

This is a source-grounded, validated, **read-only internal Spring service**
`EnterpriseOrganizationRegistry` backed by a versioned taxonomy resource
`organization-blueprint-v1.json`. It is **not** a new company process,
agent execution framework, tenant provisioner, public API, permission grant,
scheduler, UI, AI model, or revenue-generating operating business.

The catalog describes **13 proposed companies and exactly 100 globally
unique proposed divisions**. Each company and division is labelled
`PROPOSED`, and division-level `workerVerified=false`.
13 legacy divisions are explicitly assigned to an organizational home,
while the registered agent IDs, tool families, risk classifications,
model preferences, and approval policies remain solely in the existing
`AgentCatalogService`. Existing source file and permissions are unchanged.

## Target company catalog

| Proposed company | Proposed divisions |
|---|---:|
| Aetheris Software | 11 |
| Aetheris Intelligence | 10 |
| Aetheris Cyber Defense | 5 |
| Aetheris Creative | 17 |
| Aetheris Cloud | 5 |
| Aetheris Ventures | 8 |
| Aetheris Finance | 5 |
| Aetheris Industries | 5 |
| Aetheris Academy | 5 |
| Aetheris Experience | 7 |
| Aetheris Science | 5 |
| Aetheris Enterprise Services | 5 |
| Aetheris Immersive Technologies | 12 |
| **Total** | **100** |

The Immersive company includes cinematic websites, 3D modeling, photorealistic
rendering, scroll-based GSAP-style experiences, frame-sequence pipelines,
real-time WebGL, motion graphics, shaders, spatial interaction,
storyboarding, rendering optimization, and visual QA. **None of those
pipelines is claimed operational by this stage.**

## Baseline distinctions

- Protected main: **26 configured specialist IDs across 13 divisions**.
- The current draft security stack: **30 configured specialist IDs**
  across the same 13 divisions, including four proposed/added CISO/crypto/
  detection/threat-hunting identities not yet promoted to protected main.
- The initial enterprise agent target of **54** is a planning count derived
  from 30 staged IDs + 24 proposed unique Immersive roles; it is not a
  count of agents running autonomously.
- Company/division taxonomy metadata **does not increase** the number of
  registered or operational agents.

## Strict governance boundary

- No controller is added. **The present base does not yet establish a
  separately verified authenticated owner-scoped route for this registry.**
  Publishing a new anonymous GET endpoint would be an unsafe shortcut.
- No POST/PUT/DELETE/PATCH, no mutating database operations, no worker
  launching, no command bridge, no financial or model activation.
- The mapping is an **organizational ownership proposal**, not tenant access.
  `group-headquarters` holds Executive Office and independent QA/Release
  governance rather than subordinating approval to a commercial company.
- Mapping completeness is checked from actual configured agents; a missing,
  new or duplicate identity fails closed.
- New catalog identities can be represented if they belong to one of the
  13 recognized legacy divisions, without changing worker verification.
- Unknown company references, duplicate division IDs, missing legacy
  assignments, invalid metadata and non-unique specialist IDs fail closed.
- No claim that assignment grants access to private artifacts; future
  tenant/project authorization must be enforced separately by a
  verified security principal and policy service.
- All 13 Stage45 release blockers and physical owner-PC NO_GO remain intact.

## CI and release acceptance

`EnterpriseOrganizationRegistryTest` must pass on Ubuntu 24.04 and
Windows Server 2022, together with E0's `EnterpriseCatalogBaselineTest`.
The workflow also runs the full Java orchestrator regression and an independent
non-activation check. **The Stage45 release-gate source/tests are NOT present
on the E0 main-based development branch**, so this E1 workflow explicitly
records that Stage45 certification was not executed and must NOT claim its
13 blocker tests passed. A later integration review must execute the real
Stage45 gate from its own audited source before promotion. CI success
establishes only the checks actually executed at an exact SHA; it does not
authorize a merge, desktop activation, financial action or deployment.

## Follow-on stages

**E2** — Introduce independently scoped advisory executive / design roles
after explicit identity, model, tool-policy and review tests. Verify that
CEO recommendations do not grant production, money or security authority.

**E3/E4** — Secure local tool execution, authenticated mission leases,
cancellation, audit, model routing and recovery. Physical Windows
certification is required before owner-machine activation.

**E5/E6** — Build one genuine autonomous software and cinematic 3D website
factory: Blender assets, pre-rendered sequence or R3F/Three.js,
GSAP ScrollTrigger interactions, visual regression, responsive support,
performance measurements, isolated code execution and a draft GitHub PR.

The first real-world benchmark should complete this workflow using verifiable
commits, screenshots, tests and independent evidence. Until then, neither the
13-company structure nor its 100 divisions is an operational enterprise.

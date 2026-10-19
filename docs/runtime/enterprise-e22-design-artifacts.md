# Aetheris E2.2A — Deterministic Cinematic Design Artifact Lab

**OPEN DRAFT · PLANNING ARTIFACTS, NOT A WORKING 3D WEBSITE · NO OWNER APPROVAL OR AGENT EXECUTION**

## What works in this slice

The new `EnterpriseDesignArtifactService` accepts a real validated
`EnterpriseAdvisoryPlanningService.DesignBrief`. It:

1. Reuses the E2.1 company/division scope and canonical catalog checks.
2. Refuses any advisory packet claiming tool-execution, delegation, external
   publication, spending, completed work or granted owner approval.
3. Generates a real, independently viewable standalone 1280×720 SVG
   three-scene storyboard. Titles and project labels are XML-escaped,
   invalid XML code points are removed, and visible content is length-bounded.
4. Generates JSON with the planned hero, scroll-controlled reveal (120
   **planned, not rendered** frames, indices 0–119), semantic reduced-motion
   fallback, six quality gates and seven candidate handoffs.
5. Produces stable SHA-256 fingerprints for exact SVG/JSON UTF-8 bytes.
   These hashes detect accidental modifications; they do **not**
   authenticate a human, prove design quality or authorize publication.
6. Declares `rendered3dAssetsPresent=false`, `websiteBuilt=false`,
   `automatedTestsExecuted=false`, `ownerApprovalGranted=false` in
   the JSON, and `executionAuthorized=false`, `designAgentsExecuted=false`,
   `websiteDelivered=false`, `productionApproved=false` on the bundle.
7. Makes no filesystem writes, model calls, browser calls, external network
   calls, financial actions, user-device actions or repository mutations.

A separate `EnterpriseDesignArtifactVerifier` independently checks
SHA-256 consistency, manifest/project/organization matching, all nonexecution
flags, three-scene motion contract, seven handoffs, and hardens SVG XML
parsing against DTD, external entities, arbitrary elements and unapproved
attributes. Its success status is **VERIFIED_STATIC_ARTIFACT_FORMAT_ONLY**,
never visual-quality certification or owner authentication.

## Real automated QA evidence

- Generated SVG and JSON bytes are written into temporary files and read
  back by an adversarial Java test, then re-verified.
- A dedicated test writes a reproducible review bundle under
  `orchestrator-service/target/e22-design-evidence/`:
  `cinematic-device-storyboard.svg`,
  `cinematic-device-motion-contract.json`, and `SHA256SUMS`.
- GitHub Actions runs focused E2.2, E2.1, E1 and E0 tests on Linux and
  Windows, plus full Java orchestrator regression and no-activation gate.
- The Ubuntu job publishes the actual generated **static** files as a
  short-lived GitHub Actions artifact for human inspection.
- Tests cover deterministic input-to-output mapping, non-executable
  markup, XML escaping, tampered hashes, injected scripts and XXE,
  fake owner approval, false agent completion, unknown organization
  scopes and malformed project briefs.

## Explicitly unfinished

- No actual Blender mesh, photorealistic 3D render, FFmpeg sequence,
  real frame preloading, WebGL, R3F, GSAP, scroll interaction or deployed
  website is built here. Those belong to E5/E6.
- No Figma account, design agent, autonomous designer or real signed
  approval has executed. Existing Design Studio personas remain proposed
  and tool-less.
- Owner authenticated consent, durable non-replayable approvals,
  permission separation and audit-log custody are separate E2.2B work
  and require a reviewed authentication source; never treat SHA256 alone
  as authorization.
- All 13 Stage45 security release blockers still require the actual
  policies to be integrated and independently executed, and physical
  Windows owner-PC certification remains unperformed and NO_GO.

## Source and review

Base stack: E0 PR #102 → E1 PR #104 → E2.1 PR #106.
E2.2A is deliberately stacked as an **OPEN DRAFT** on PR #106.
Existing canonical `main` agents are still 26 across 13 divisions;
the unmerged later security chain has 30. Company count 13 and division
count 100 are proposed organization metadata, not autonomous companies.

## Next implementation gate

E2.2B: implement a real owner-authenticated scoped approval request and
single-use decision protocol, backed by durable audit and a verified
principal source, without exposing model-originated self-approval.
E3: actual sandbox worker with a fixed test-only capability set;
no live host shell or arbitrary execution before device certification.

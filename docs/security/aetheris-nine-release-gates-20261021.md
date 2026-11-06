# Aetheris — Nine release gates (engineering tracker, Oct 21, 2026)

This tracker is a proposed future release plan, **not** a claim of nine
pre-existing roadmap stages or production validation. The original
34/34 repository blueprint is a completed *design/development roadmap*;
physical owner-PC status is BLOCKED_PENDING_HARDWARE.

All nine release gates are presently **NOT CERTIFIED**. Hosted GitHub CI
on open draft PRs only supports limited source-and-test assertions.

| Gate | Acceptance criterion for closure | Current status |
| --- | --- | --- |
| 1. Security PR integration | Review stacked source, reconcile conflicts, carry forward passing tests and preserve protected-main governance | OPEN: Platform PRs #234–#236; Runtime #178–#183 |
| 2. Isolation and egress | Real unprivileged dedicated worker, network-none by default, read-only production mounts, cgroup/PID limits, SSRF/DNS and proxy policy verified | OPEN: host command and MCP restrictions are preliminary only |
| 3. Principal and action authorization | Per-user identity, JWT/session lifecycle, agent tool approval, owner task binding and negative tests | OPEN: multiple draft security PRs and end-to-end tests needed |
| 4. Secrets and state migrations | Real operator-generated secrets, non-destructive DB migration/rotation, backup and restore verified | OPEN: local production secrets and real data validation pending |
| 5. Agent runtime reliability | Durable task recovery, emergency stop, audit, idempotency, bounded worker restarts and rollback in real integration | OPEN |
| 6. Local model inference benchmarking | Real target hardware benchmark and held-out model execution/repair tests with reproducible metrics | OPEN |
| 7. Cinematic Studio capability | Human-authored reference harness, GSAP/3D positive control, actual model-authored scene, real Chromium+perf+accessibility and held-out trials | OPEN: see Runtime issue #177 |
| 8. Owner PC acceptance | Windows, WSL2, GPU/Ollama, thermal/storage, microphones, browser integration and failure-recovery trials | BLOCKED_PENDING_HARDWARE |
| 9. Release authorization | Full integrated supply-chain/security/performance/a11y/provenance, change-management review and explicit release approval | OPEN |

## Next safe sequence
1. Complete draft verification of process-tree timeout/interrupt handling.
2. Review historical risky branches non-destructively; do not force-push or rewrite.
3. Specify and test an **isolated worker protocol** outside the privileged
   orchestrator. Keep host-code execution disabled by default.
4. Build restrictive egress and signed capability grants; preserve model
   untrusted-output boundaries.
5. Certify real end-to-end work only on approved disposable environments;
   repeat on owner PC when available.

**NO_GO** for unattended production, live-money capability, automatic
app deployment or OS-wide command authority until all required gates pass.

## Checkpoint update — 25 October 2026 (no gate closed)

Previous gate statuses above remain **OPEN / NOT CERTIFIED**. The historical tracker is preserved rather than rewritten to suggest release success.

- **Gate 1 (security integration):** platform [PR #262](https://github.com/teldigi5-wq/aetheris-platform/pull/262) passed 29/29 workflow runs on its exact head. Newer reconciled [#264](https://github.com/teldigi5-wq/aetheris-platform/pull/264) initially passed 29/30 due to Maven Central HTTP 502 fetching `objenesis` during a recovery stack build; an unchanged failed-job rerun succeeded and the same candidate SHA now reports 30/30 successful workflow conclusions. Both remain DRAFT, unmerged, with independent production blockers #242/#251/#255/#259 and operational backup/secret tests still pending. Runtime [#185](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/185) passed seven workflows and is DRAFT; [#186](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/186) added read-only cinematic standards and passed 3/3; [#187](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/187) is stacked on #186 for a controlled WebGL2 reference and roadmap. No reconciled main promotion.
- **Gate 2 (isolation):** #185 narrows Docker test output writable scope and adds inspections; a general hostile-model OS-isolated worker and artifact-only mount provenance have NOT been certified. Network, commands and MCP remain default-denied.
- **Gate 6 (genuine AI benchmarks):** E3.9G-12B4D matched real-model source-span A/B trial is still absent; earlier Qwen2.5-Coder 7B controlled two-site repair baseline 0/4. Do not credit a reviewer-authored positive control as model success.
- **Gate 7 (Cinematic Studio):** first [E3.9H reference-control CI](https://github.com/teldigi5-wq/aetheris-ai-runtime/actions/runs/40156195539) passed 3/3 workflow jobs on its initially verified exact commit and proved scroll-dependent real WebGL2 canvas pixels at 390px/1280px, reverse motion, reduced-motion and simulated no-WebGL fallback in hosted Chromium. This does NOT prove independently approved human art direction, GSAP/Three.js production scene quality, model-authored fixes, measured actual phone GPU or autonomous release. [Issue #177](https://github.com/teldigi5-wq/aetheris-ai-runtime/issues/177) remains OPEN.
- **Gates 3–5, 8–9:** owner principal/session authorization, durable real execution/STOP, persistent secrets and DB migrations, physical Windows/voice/GPU tests and final operator authorization remain NOT CERTIFIED.

Canonical enterprise sequence: finish E3.9G-12B4D → advance E3.9H held-out model/3D proof → E4 model routing → E5 supervised website factory → E6 immersive rendering → E7 coordinators → E8 cockpit → E9 independent certification, with Stage45 13 blockers and Execution Phase A maintained as distinct release requirements. See [current runtime roadmap](../runtime/roadmap-current-e3-through-e9-20261025.md).

**No production, live-money or host privileges were granted by this progress.**

## 6 November 2026 — verified cross-repository continuation checkpoint (no gate closed)

All **nine** previously defined Stage45 acceptance gates above remain **NOT CERTIFIED**. This dated append does not change the historical tracker, certify any gate or add an official stage.

- **Gate 1 / security integration:** [Runtime #257](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/257), [#281](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/281), [#282](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/282), and [Platform #321](https://github.com/teldigi5-wq/aetheris-platform/pull/321) remain draft/unmerged; no reconciled protected-main promotion was performed.
- **Gates 2–5:** direct owner/tenant authorization, independent physical resource fencing, secret/migration/restore policy, operator identity, durable STOP/recovery and independently administered revocation are **not** certified. [Runtime issue #23](https://github.com/teldigi5-wq/aetheris-ai-runtime/issues/23) and [Platform issue #251](https://github.com/teldigi5-wq/aetheris-platform/issues/251) remain open.
- **Gates 6–7:** E3.9 held-out genuine model outcomes, E6 genuine Cinematic Studio authorship and physical GPU/browser acceptance remain incomplete. The E5→E6 draft chain [#231](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/231)→[#232](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/232)→[#233](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/233)→[#234](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/234) is not a production release.
- **Gate 8:** owner-PC [Execution Phase A](https://github.com/teldigi5-wq/aetheris-platform/issues/49) remains BLOCKED_PENDING_HARDWARE.
- **Gate 9:** independent release approval absent; Stage45 **NO_GO** and XERJ publication **HOLD**.

The latest [PR #282 physical PG16 witness run #42159092287](https://github.com/teldigi5-wq/aetheris-ai-runtime/actions/runs/42159092287) passed its actual scripted assertions, **including explicit NO_GO proofs**: the witness database owner could reset the witness epoch, and the privileged old primary remained writable after a stale-epoch read denial. The independent-resource fencing vulnerability remains unresolved. The follow-up exact-epoch review commit `d5323fc1e6deba9936beffc3fffc8ddbfb1cf33f` passed [physical PostgreSQL CI #42166201856](https://github.com/teldigi5-wq/aetheris-ai-runtime/actions/runs/42166201856), rejecting a forged future-epoch claim in the **test-only** witness helper. It does **not** authenticate a resource claim, protect against privileged witness rollback, or fence the old primary. The [source-only checks in #281](https://github.com/teldigi5-wq/aetheris-ai-runtime/actions/runs/42156885665) also passed, but do not certify a deployed network. Details: [dated E-series / S0 checkpoint](../runtime/roadmap-current-e3-through-e9-20261025.md).

The original Platform 34/34 repository-blueprint completion statement is retained. S-B62-A (proposed security ADR work package) is **not** the historical Platform B62-A JWT temporal stage or a new E-series stage. This update is a review-only append on an unmerged Runtime draft, not a protected-main change.

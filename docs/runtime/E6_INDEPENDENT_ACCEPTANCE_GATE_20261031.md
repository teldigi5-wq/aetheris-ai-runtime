# Enterprise E6 Task 8 — Independent acceptance evidence gate

**31 October 2026 · Research review readiness only · draft stacked on Runtime PR #244 · Stage45 NO_GO.**

## Verified basis and deliberate blocker

Task 6 Chromium accessibility/WebGL recovery and Task 7 evidence-policy tests are verified on GitHub. **Actual physical GPU measurements, real mobile hardware/thermals, manual assistive-technology review and independent sign-offs are NOT present.** Therefore Task 8 is blocked. A GitHub Actions job can check evidence policy logic but cannot act as the independent reviewer or transform self-reported GPU metadata into hardware verification.

## Nine mandatory review domains

1. `source_and_build_provenance`: exact commit, original Blender `.blend`/GLB and export provenance, reproducible build/source integrity.
2. `dependency_and_licensing_review`: dependency/asset license inventory, supply-chain and terms review, independent attribution review.
3. `physical_desktop_gpu_frame_time`: actual workstation model, real GPU profiler trace and repeatable frame-time measurements (not only browser rAF).
4. `physical_mobile_device_profile`: genuine smartphone/browser devices, responsiveness and representative mobile GPU performance.
5. `sustained_memory_thermals_power`: sustained test on physical hardware with labeled memory/VRAM, temperature and power logs where supported.
6. `manual_accessibility_and_assistive_tech`: manual WCAG 2.2 AA mapping and keyboard, screen-reader, zoom and reduced-motion review.
7. `real_driver_context_loss_and_browser_matrix`: driver/device WebGL loss/restore, Safari, Firefox, Chromium and low-end compatibility review.
8. `human_visual_art_direction`: original-brand fit, user comprehension, contrast, mobile crop, materials/lighting and licensing sign-off.
9. `independent_security_threat_review`: architecture/release threat model, input/output and URL policies, dependencies, CSP/headers, secrets and privacy review.

## Local-only process

Use the committed blank template `docs/runtime/E6_INDEPENDENT_ACCEPTANCE_TEMPLATE_20261031.json`. Copy it into a **non-source-controlled, owner-managed** local directory `target/e6-studio/independent-acceptance/manifest.json` with evidence documents under its `evidence/` folder. Do not add production logs, account tokens, device identifiers, confidential reviewer materials or personal data to GitHub. Review/redact files before sharing. No connector or third-party upload is done by this process.

Each domain starts as `missing` with `files: []` and `reviewer: null`. Replace with `state: "submitted"` **only after** collecting genuine corresponding evidence. A record uses a path such as `evidence/manually-verified-asset-provenance.json` and its real SHA-256, *not* a fabricated placeholder. Text/JSON/CSV/PDF/PNG/JPG/WebP/Markdown are allowed (maximum 20 MiB per file, one to five files per domain). Relative paths cannot contain `..`, symlinks, URL encodings, or escape the case folder; every referenced file must exist and match its SHA-256. Duplicate paths across domains are rejected.

Manifest fields: `repository` must equal `teldigi5-wq/aetheris-ai-runtime`; `exactCommit` and `glbSha256` are actual 40/64-character lowercase SHA digests, `assembledAt` must be a real recent UTC timestamp, and `authorAlias` is an anonymized human review label. The separate human reviewer metadata is recorded under each domain as `{ "alias": "independent_reviewer_alias", "decision": "recommend_review", "reviewedAt": "...UTC timestamp..." }`. The alias must differ from the author, but **an alias alone does not authenticate identity**; reviewers must independently corroborate their roles and evidence authenticity outside this program. `recommend_review` is not an approval.

### CLI

From the repository root (Node.js 22), after creating your private manifest and evidence:

```bash
node tools/enterprise/e6_studio/next-studio/independent_acceptance_cli.mjs
```

Or for a different local private case directory:

```bash
node tools/enterprise/e6_studio/next-studio/independent_acceptance_cli.mjs --case-dir /absolute/path/to/private-case
```

The tool is read-only, performs no network calls and returns exit code **2** when blocked. If every local evidence reference is well formed and matches its hash, the status becomes `EVIDENCE_INVENTORY_READY_FOR_HUMAN_REVIEW`, **never independent acceptance**. In every result, `task7Completed`, `task8Completed`, `stage45Go`, `releaseGo`, `productionDeployed` and other release authority fields stay `false`. A human must still verify that the data represent the correct physical tests, source, people, licenses and true conditions. An accurate SHA is proof of bytes matching a stated reference, **not** proof the reference was independently authenticated.

## CI regression acceptance

GitHub Actions runs Node.js 22 offline synthetic positive/negative tests for a missing inventory, all nine domains, missing/duplicated/substituted or oversized evidence, symlink/traversal denial, independent reviewer separation metadata, wrong repository/digests, stale dates, and malicious release assertions. It also calls the real CLI with no local owner manifest and **requires BLOCKED**. The successful CI attests only that the gate rejects these cases; it cannot assert that E6 hardware, accessibility, artwork, licensing or security were actually accepted.

## Still required

Real local Task 7 hardware measurements (owner action), independent manual Task 8 acceptance evidence and review, release-specific compliance/quality sign-off, explicit owner approval, protected-branch merge review and deployment verification. Until those exist, **E6 Task 8 BLOCKED; Stage45 NO_GO; E7/E8/E9 are not promoted by this change.**

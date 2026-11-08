# E8 — Offline Mission Review board (first actual UI capability)

**8 November 2026. DRAFT, unmerged. Stage45 NO_GO.**

## Delivered functionality

The E8 implementation advances beyond the E7 worker proof with a real, self-contained **interactive offline case investigation dashboard** generated from 1–12 actual locally supplied E7 specialist review records. It is not the existing Stage 8 feature nor a production Mission Control API.

Existing Runtime Task/Mission HTTP endpoints remain quarantined under security issue #23. This feature never reads those endpoints, a production database, an identity provider, organization data or customer materials. It does not provide an authentication UI or let an operator choose a tenant as a shortcut to RBAC. The original 13 companies/100 divisions taxonomy is not treated as operating businesses.

The compiler at `tools/enterprise/e8_cockpit/e8_mission_board.mjs` accepts **only** typed E7 output review evidence in exact `review-<uuid>.json` files. It verifies 4-event hash-chain consistency, allowed worker roles and tool names, byte/sha records, input digest, safety booleans, absence of authenticated owner/GO claims and consistency between denied static checks and final verdict. This is a **consistency check, not a verified signature or trusted source identity**: a malicious source can regenerate a whole self-hashed review. The UI explicitly calls this out.

The browser app shows actual source-dependent counts (total, awaiting review, denied, failed checks); search and filter; case selection; SHA-256 digests, static findings, and the four recorded evidence events. The visual design is sober, readable, responsive, keyboard operable with reduced-motion and visible focus. All text is written using `textContent` in the client. It never fetches remote resources, executes reviewed source, writes original workspaces, or creates approval actions.

## Operator execution

Default does nothing other than emit a plan; no filesystem reads:

    node tools/enterprise/e8_cockpit/e8_mission_board.mjs

To create a local review board, first obtain E7 review records using that **separate, explicit offline review** workflow and place their JSON contents in a private existing directory, named `review-<request UUID>.json` with filenames matching the actual IDs. This E8 input directory must have 1–12 files, no extras, no symlinks and each <= 24 KB. Do not place private task data, API tokens or personal customer records in the input.

Linux/macOS:

    export AETHERIS_E8_OFFLINE_DASHBOARD_OPT_IN=yes
    node tools/enterprise/e8_cockpit/e8_mission_board.mjs \
      --run-local --source-dir /absolute/path/to/e7-evidence \
      --output-dir /absolute/new/private-e8-board

Windows PowerShell: set `$env:AETHERIS_E8_OFFLINE_DASHBOARD_OPT_IN = 'yes'` and provide absolute Windows paths.

The output directory must **not already exist**. The build uses exclusive output creation, writes `index.html`, `styles.css`, `app.js` and SHA-256 `manifest.json`, without loading remote dependencies. On POSIX files use mode 0600. Open `index.html` locally to view read-only investigations. Never publish the artifact to an internet-hosted static site if case hashes or metadata could be sensitive.

## Tests, boundaries and next work

CI runs the new E8 integration/security suite and inherited E7 regression suite on Ubuntu 24.04 and Windows 2022. A separate Ubuntu job generates two genuine E7 worker review receipts from **fictional local websites**, compiles the E8 dashboard and tests desktop (1440px), mobile (390px) and reduced-motion (390px) in actual Chromium: status counters, search/filter, evidence details, keyboard focus, no overflow and no external network. All sources and templates are static; no real missions or accounts are accessed.

This is **not** authenticated Mission Control: no owner JWT, tenant isolation, role-mapped live mission API, signed or externally anchored audit, persistent incident history, authorization to pause/cancel/approve, real SLA telemetry, production authN/authZ, or real user acceptance. No source edits, model inference, deployment, API activation, or production credential operations. The E8 acceptance family remains OPEN, E7 remains draft, Runtime security issue #23 OPEN, Stage45 NO_GO, XERJ HOLD. The next integration after a verified checkpoint is a genuinely authenticated read-only Mission Control backend, following verified tenant/owner source and versioned migrations—not more synthetic dashboard skins.

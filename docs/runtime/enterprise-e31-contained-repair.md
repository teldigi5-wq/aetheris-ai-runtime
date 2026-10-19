# Aetheris E3.1 — CI-only contained fail → diagnose → repair → retest

**DRAFT · bounded deterministic worker only · no AI-model repair · no physical owner-PC execution · no protected-main merge or production deployment**

## What is genuinely new

E3.0 PR #116 produced a working HTML/CSS/JS cinematic site with real browser-proven reversible CSS 3D laptop motion. E3.1 adds one strictly scoped **real failure-detection and deterministic repair loop** to that site.

1. `run_locked_worker.sh build` launches a real disposable Docker container and executes the existing E3.0 Python generator. Only the repository's `target/` directory is writable.
2. `run_locked_worker.sh seed` introduces one carefully specified source fault to **generated** `site/main.js`: it replaces exactly one `const p = progress();` with `const p = 0; // E31_SEEDED_SCROLL_FAULT`. This intentionally makes scroll progress stick at zero.
3. A separate, host-CI **Playwright Chromium** runner navigates the generated site and is required to **fail** with `Late progress not near end`. Other failures (missing browser, network fixture issue, timeout) do not qualify. The JSON error report becomes `repair-evidence/diagnosis.json`.
4. `run_locked_worker.sh repair` verifies a real matching failed browser report and an exact SHA-256 match against the single known seeded change; it restores the original `main.js` bytes. Different file, modified source, fabricated success, missing report or arbitrary patch parameters are rejected.
5. The original E3.0 SHA-256 checksums must match again. The independent real Chromium QA runs again and must pass desktop scroll forward and reverse, mobile viewport and reduced-motion checks.
6. CI captures before/after Playwright JSON, screenshots, original/seeded/repaired source hashes, Docker policy inspections, and a truthful `verified-summary.json` in a short-retention artifact.

**This is actual build-test-repair-retest behavior, not a claim that a generative AI engineer diagnosed unknown bugs.** The seeded fault and exact repair are predetermined and reproducible, and no agent/model chooses commands or writes production files.

## Actual Docker enforced boundaries

All three Python worker phases execute inside ephemeral Docker containers on an **Ubuntu GitHub Actions CI runner only**:

- `--network none`: **worker containers** cannot access any network. The separate Playwright test harness needs network during test dependency installation; the worker does not.
- `--read-only`, repository mounted read-only, only fixed `/workspace/target` mounted read-write.
- `--cpus 1`, `--memory 256m --memory-swap 256m`, `--pids-limit 64`.
- `--cap-drop ALL`, `--security-opt no-new-privileges`, non-root runner UID/GID.
- 16 MB `noexec,nosuid` tmpfs and disabled Python `__pycache__` writes.
- An independent host-CI **45-second wall-clock timeout**, with forced container cleanup.
- Docker `inspect` is checked before execution for the actual limits/mounts/network policy; sanitized reports are preserved per phase.
- Only explicit `build`, `seed`, `repair` commands accepted. Neither worker accepts arbitrary shell commands, source paths, network URLs or model input.

A container's security is bounded by Docker and its CI host/kernel configuration; it is **not** a formally proven secure enclave. GitHub workflow script has Docker control only on disposable runners, never on your physical workstation. Real workload isolation on Windows needs independent device validation.

## QA gates

- Ubuntu/Windows pure-Python adversarial tests reject unauthorized patch drift, missing/different browser diagnosis, repeat fault injection, unsafe target symlinks and unknown CLI phases (symlink test skips on Windows where developer entitlement varies).
- Real Ubuntu Docker fail-repair-retest pipeline, checked against actual emitted browser reports.
- Full Java orchestrator regression.
- Independent source policy/authority guard.
- Attached browser screenshots and reports constitute *generated CI evidence*, not independently reviewed premium visual quality or measured Core Web Vitals.

## Remaining limitations and release blockers

- No human-owner authentication/key enrollment, no action-specific positive approvals, no model-directed code generation or autonomous multi-agent delegation.
- No actual Blender frames, GLTF meshes, Three.js/WebGL scenes, photorealism, deployment or paid service.
- No permission to run shell commands, containers or browser automation on the user's physical PC.
- The enterprise organizations, new advisory design roles and authority system remain unmerged proposed work. Existing identities and permissions are not altered.
- Stage45's actual 13-blocker suite is not available/executed on this stacked E3 branch. Production and owner-device activation remain **NO_GO**.

## Next E3.2

Test a **strictly reviewed diff proposal** from a local model as untrusted content, never a shell command. Compare it with an allowlist of exact files and grammar, run it only in the same disposable isolated CI workspace, and independently verify the browser. Measure success on a frozen unseen fault set with failures and costs included. The external action/owner authorization boundary remains disabled.

Dependency order: E0 #102 → E1 #104 → E2.1 #106 → E2.2A #107 → E2.2B-1 #109 → PostgreSQL #110 → proof #112 → nonce #113 → key trust #114 → real E3.0 site #116 → this E3.1 draft.

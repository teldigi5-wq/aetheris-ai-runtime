# Media Studio hardware-independent delivery roadmap

State on 2026-10-11: **pre-hardware, non-production development**. Certified AI-runtime `main` and Aetheris Platform `main` are unchanged. Do not promote this branch or bypass repository protections. The 12 milestones below are planning units, **not claims of implemented capability**.

| # | Milestone | Current status | Acceptance gate |
|---|---|---|---|
| 1 | Auth and dependency hygiene | PRs #207–#210 pending platform review | All required CI, JWT tenant isolation, locked dependencies and signed boundaries |
| 2 | Optional local image-generation adapter | AI-runtime PR #19 source + mock tests | Run fixed local ComfyUI graph on owner PC with licensed local checkpoint |
| 3 | Syntra Media Studio drafting UI | Platform PR #211 draft | Preserve non-executable state until authenticated media gateway exists |
| 4 | Offline GPU upgrade planning | Included in AI-runtime PR #19 | Deterministic tests, hypothetical-only output, no readiness claims |
| 5 | Authenticated per-owner job submission | Not started | Signed user identity, tenant isolation, idempotency, CSRF/SSRF rules |
| 6 | Local image editing and upscaling | Not started | Separate vetted workflow graphs, controlled input/output files |
| 7 | Local image-to-video and text-to-video | Not started | Reviewed model-specific implementation/license, VRAM & codec tests |
| 8 | GPU memory-aware queue, cancel, recovery | Not started | Bounded queue, atomic reservations, clean cancellation, restart safety |
| 9 | Media storage, provenance, limits, deletion | Not started | Owner-scoped artifacts, checksums, quotas, retention, no secret leaks |
| 10 | Voice and multimodal integration | Existing runtime foundations only | End-to-end authenticated tests and actual device evidence |
| 11 | Skills and agent workflow integration | Runtime skills draft PR #18 | Enforced trust policy, read-only semantics, adversarial tests |
| 12 | Owner-PC hardware certification and release | BLOCKED_PENDING_HARDWARE | Physical GPU/driver/thermal/OS testing and signed deliverables |

## Rules that apply to every milestone

- Owner approval is required before installing models, starting external tool adapters, or performing any writes to a host outside an explicitly authorized local output directory.
- `BLOCKED_PENDING_HARDWARE` is authoritative even if CI passes or the user supplies a theoretical GPU capacity.
- Unknown/free provider claims do not establish checkpoint licensing, model quality, memory fit, speed, or production availability.
- Default `AETHERIS_MEDIA_ENABLED=false`, no paid or cloud fallback. Native Media Studio does not directly call unauthenticated ComfyUI.
- Keep platform authentication, JWT owner mapping, and PR governance constraints intact. Avoid hard-coded self-reported owners.
- Each hardware-dependent workflow gets its own controlled graph, failure tests, model provenance, memory telemetry and graceful out-of-memory handling before activation.
- Until credentials, storage, quotas, auth and health checks are validated, do not advertise this as an image/video-generating desktop application.

## Current no-PC verification

- `python -m media_studio plan --workflow image --hypothetical-vram-mib 6144` — deterministic offline comparison, not a GPU probe.
- `python -m unittest discover -s media_studio/tests -p 'test_*.py' -v` — mock ComfyUI transport and policy tests.
- Dedicated GitHub CI check: `media-studio-unit`.
- Platform media UI check: `media-staging-contract`; Windows installer build must pass separately.
- No new `main` merge or physical-PC verification until the existing tracked security PRs have been reviewed.

## After laptop diagnosis

Before installing any model, collect **actual** GPU name, dedicated VRAM, RAM health, thermal behavior and available disk space from a stable system. Compare local ComfyUI-reported values with independent OS readings. Run only an allowed checkpoint at a conservative workload, measure sustained peak VRAM and output validity, and log failures before increasing resolution or enabling video. Never replace `BLOCKED_PENDING_HARDWARE` with a guessed 'ready' status.

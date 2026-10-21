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

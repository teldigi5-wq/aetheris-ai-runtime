# E3 CI worker writable-mount reduction — 22 October 2026

## Existing boundary and confirmed overbroad permission
The existing `tools/enterprise/e3_factory/run_locked_worker.sh` **already**
uses Docker network=none, a read-only container root filesystem, dropped Linux
capabilities, no-new-privileges, non-root identity, limits on CPU, memory,
PIDs and duration. The fixed-phase CI worker must be preserved and must never
be mistaken for a general owner-approved autonomous agent executor.

**Issue:** it previously gave the fixed worker a read-write bind for the entire
repository `target/` directory. Thus a compromised process could tamper with
other target artifacts outside its own `target/e3-factory/` area.

## This slice
- Keep the entire repo read-only as before.
- Replace the broad `/workspace/target` writable bind with a narrow nested
  `/workspace/target/e3-factory` writable bind. Parent target remains read-only.
- Validate the *actual Docker inspect document* with a testable fail-closed
  policy, including exact mount destinations and source, privileges, namespaces,
  no inherited secret env, no port publication, resource limits and readonly root.
- Unit-test negative inspection contracts on Windows and Ubuntu.
- In hosted Ubuntu CI, execute the existing fixed `build` phase in a real
  disposable Docker container and verify the manifest's CI-only truth claims.

## Explicit limits and follow-up
The repo remains readable by the CI worker including .git; do not mount owner
secrets or untrusted repos. CI still pulls `python:3.12-slim-bookworm` by
**mutable tag**; replacing this with a verified multi-platform digest is a
separate supply-chain hardening slice. The worker is network-none only **inside**
its container; the CI host downloads images before execution. Symlink-race and
Docker daemon/host threats remain. This change is **not** wired to the
privileged orchestrator and does not permit autonomous model-generated code,
owner-PC execution, deployments or production approvals. Keep NO_GO until
isolation and trust-boundary gates are separately certified.

Note: non-root group IDs are required by the new real-inspection validator;
if a supported CI runner legitimately uses root group, choose a non-root
execution group instead of weakening the rule.

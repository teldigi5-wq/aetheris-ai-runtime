# Syntra × Aetheris Stage 45.1 — OCI Process Isolation Lab

**Status: DRAFT, host runtime execution remains OFF by default.**
Depends on unmerged Stage 43 and Stage 44 PRs #39–#43.
This is a **standalone, synthetic Linux CI sandbox proof**, **NOT**
an installed/safe agent executor. It cannot be invoked by the runtime,
an agent, the Mission Control UI, the public web, or any API.

## What was implemented

- `IsolatedOciProbePlan`: non-Spring, **non-executing** Java plan
  for a fixed, digest-pinned Linux/amd64 BusyBox OCI image. No caller-
  supplied executable, image name, environment variable, Docker socket,
  network profile, privileged flag or mutable host mount. Rejects
  symlinked workspace ancestors, symlinked probe files, mount delimiter
  injection and non-Linux execution. Returns immutable Docker argv.
- `IsolatedOciProbePlanTest`: Java assertions that plan includes
  network isolation, all-capability removal, no-new-privileges, fixed
  unprivileged uid/gid, read-only root/workspace, strict CPU/memory/PID
  limits and non-executable scratch tmpfs. Adversarial symlink and
  mount delimiter tests. **Argument checking alone does not prove
  OS enforcement.**
- `tests/sandbox/stage45_oci_isolation_probe.sh`: synthetic GitHub
  Actions-only Docker test; deliberately pulls one digest-pinned image
  before launching a network-disabled workload. Workload confirms
  readable permitted fixture, blocked writes to bound read-only
  workspace, lack of host Docker socket, lack of inherited runner
  environment secret, lack of network routes, zero effective Linux
  capabilities, no-new-privileges and writable/no-exec scratch tmpfs.
  The runner independently proves its original fixture did not change.
- `runtime-stage45-oci-isolation.yml`: three independent jobs:
  targeted sandbox policy regression, actual OCI synthetic enforcement,
  and complete orchestrator Java regression.

## Required security boundary for any future REAL agent executor

A Docker container is **not equivalent to a VM or a guarantee of
containment**. The Docker daemon on hosted GitHub Actions is a
privileged control plane. Container escape, kernel vulnerabilities,
malicious images, runtime bugs, host file-descriptor reuse and external
orchestrator secret leakage remain risks. The CI smoke test does not
validate an adversarial kernel exploit or arbitrary user-provided
models and plugins.

Production must use a **separate, non-admin sandbox worker** (prefer
rootless OCI inside a disposable VM/microVM with host-enforced egress)
rather than allowing an agent or the Java runtime to talk to a
rootful local Docker socket. Additional prerequisites:

1. Enforce identity/owner scoped approvals, immutable task authority,
   cancellation/STOP, tenant isolation, quotas, image signature/SBOM
   and provenance checks BEFORE scheduling an isolated execution.
2. Never mount Docker socket, host credentials, SSH agent, kubeconfig,
   cloud tokens, Windows Credential Manager data, model caches or
   broad user home folders. Workspace should be copied into disposable
   read-only input content and outputs staged for review.
3. Use a strict runner API with fixed command profile, pinned executable
   and signed job envelope; reject flags, paths, images and environment
   requests supplied by LLMs.
4. Apply OS/kernel-enforced CPU, memory, PID, network and filesystem
   policies on a dedicated runner host, plus outbound DNS/proxy and
   metadata-service controls. Validate seccomp/AppArmor/SELinux or
   stronger isolation as applicable; Docker defaults can vary by host.
5. Test process-tree kill (including grandchildren), timeout and STOP
   while tasks are active, system crash/restart cleanup, identity
   revocation, owner isolation, log redaction, secret ingestion,
   content provenance and Windows-to-Linux handoff.
6. Complete physical Windows/WSL compatibility tests when the user's
   PC is available. This CI only covers Linux/amd64 GitHub hosted
   runners and a synthetic image, not Windows desktop execution.

**No production safety claim** can be made from green CI alone.

## Flags and failure mode

Legacy `CommandSandboxService` still supports opt-in raw subprocess
execution for the tightly controlled Stage 4 test fixture. The
deployed default is `aetheris.execution.command-execution-enabled=false`.
This Stage 45.1 work deliberately DOES NOT turn it on, invoke it,
redirect it through Docker, expose a new route, deploy, merge or
perform any Windows host action. In particular the new Java class is
only a plan/probe, not a working general-purpose tool adapter.

CI's `GITHUB_ACTIONS` / `AETHERIS_STAGE45_SYNTHETIC_CI` guard is
**operational defense in depth, not authentication** (process
environment values can be forged). Its script should not be run
against real-data directories or in a privileged shared service.

Existing symlink checks are not sufficient against parent-directory
rename races, hardlinks or Windows reparse points. The new plan checks
paths once and is not a race-free directory-descriptor sandbox.
No raw host execution should be released without a separately
reviewed runner with stronger OS isolation.

**Release gate remains CLOSED.** Do not merge main, deploy to
production, create external exposed ports, enable autonomous terminal
commands or configure real user secrets in CI as part of this PR.

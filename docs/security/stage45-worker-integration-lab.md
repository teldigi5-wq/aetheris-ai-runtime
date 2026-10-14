# Stage 45.3 — Owner-scoped signed-job handoff and synthetic STOP integration

**Status: DRAFT/LAB. Runtime terminal execution and production agent scheduling
remain OFF. This is not a deployed or hardened process executor.**

Depends on the unmerged Stage 45.2 [PR #45] and earlier security PRs
#39–#44. No customer data or owner workstation is accessed or changed.

## What this patch genuinely integrates

`SandboxWorkerLabCoordinator` is a **plain Java, non-Spring, non-HTTP,
non-executing laboratory coordinator**. It joins the Stage 45.2
`SignedSandboxJobGate` to two deliberately injected trust seams:

1. `TrustedAuthority.requireCurrentGrant(taskId)` obtains owner, project,
   worker and approval grant via a **test-owned mapping**, NOT the
   untrusted signed-job fields. A production equivalent must verify
   the actual authenticated owner principal, task state, approved
   action and project/worker binding independently in durable storage.
2. `FixedProfileBackend.launchSyntheticSleep(jobId)` starts **only a
   harmless synthetic sleep profile** in unit-test fakes. A production
   OS-confined worker/backend DOES NOT EXIST in this component; the
   interface does not expose shell args, images, paths, a network
   transport, credentials or environment overrides.
3. A `BooleanSupplier` STOP indicator models the existing
   `TaskControlService.isEmergencyStopActive()` interface. It is a
   test fixture, NOT connected to actual TaskControlService in
   production and NOT subscribed to emergency events.

The coordinator synchronously checks STOP, resolves an independently
trusted context, cryptographically verifies a short-lived Ed25519
job lease and exact owner/task/project/worker/approval identifiers,
then invokes the fixed-profile test backend. Job completion cannot
be recorded while the fake backend reports a live process.
An expired/forged/replayed grant can never launch a workload.

`reconcile()` is an EXPLICIT caller-invoked recheck of STOP and owner
authorization. It terminates synthetic jobs with revoked or missing
grants. `stopAll()` latches an irreversible STOP, blocks new jobs
and asks the backend to kill every known job tree; it **throws and
retains the record if the backend reports still-running work**. This
avoids false success reports, but cannot guarantee kill when a future
backend fails or is disconnected.

Tests use generated test-only Ed25519 keys, fake approvals and in-
memory handles. The earlier Stage 45.2 independent Docker synthetic
process-tree STOP CI proves OS termination in a disposable Linux
container; **this PR does not physically connect those mechanisms**.

## Important unimplemented controls

- **No durable replay protection:** Stage 45.2 nonce/job ID records
  are in-memory and disappear on JVM restart. Distributed isolation,
  global STOP epoch and crash recovery are not implemented.
- **No authenticated external transport or trusted issuer:** the
  coordinator is not an endpoint and never obtains a real owner token,
  trusted approval grant or private signing key.
- **No actual secure worker integration:** the backend is a Java fake
  only. There is no rootless OCI/VM worker, signed job receipt,
  write-safe output staging or sandbox task execution.
- **No proactive emergency STOP listener:** someone must invoke
  `stopAll()` or `reconcile()` for cancellation; polling can be
  delayed and concurrent cross-process STOP is unproven. Production
  must use a separate authoritative durable STOP channel.
- **No rootless process-tree kill verification through this class:**
  the future worker must acknowledge and independently prove OS
  process termination, including grandchildren and unkillable cases.
- **No authorization by simply matching attacker-controlled fields:**
  `TrustedAuthority` MUST be backed by independent server-side
  principal/project/task/approval verification, not an echo of a
  signed request or headers.
- **No external credentials, plugins, Python/Node commands, shell
  scripting, arbitrary model requests, filesystem paths, or
  network connections are exposed.**

## Required next gate

A separate trusted identity/approval service; durable transactional
replay/STOP ledger; authenticated worker-to-controller channel with
versioned protocol; an OS-confined **separate** rootless or VM-isolated
worker with restricted output handoff and real process-tree termination;
cross-owner and restart tests; security review; Windows host/WSL
certification. Do not implement a Java ProcessBuilder-to-Docker-socket
shortcut inside the primary orchestrator.

The original `CommandSandboxService` path remains disabled by default.
There is no deploy, merge, paid infrastructure, production port,
hardware action or real-data migration in this change.

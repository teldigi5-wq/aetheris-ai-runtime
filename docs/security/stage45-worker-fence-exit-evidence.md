# Stage 45.5.5 — Worker-side fencing checks and independently verifiable CI exit evidence

**STATUS: LAB / DRAFT / DO NOT DEPLOY.** Builds on unmerged Stage 45.5.4
PR #51 and all previous unmerged Stage 43–45 review branches.

## What this slice implements

### Separate worker-side authorization decision

`WorkerSideFenceVerifierLab` is a **plain Java, manually instantiated
non-executing verification component**. It is not a controller approval
API, Spring service, running worker, Docker client, or command executor.

Unlike Stage 45.5.4's controller-only database check, it independently
checks all of these against synthetic Spring Data JPA state:

- Ed25519 signature over the entire canonical single-profile job;
- independently resolved task/owner/project/worker/approval identity;
- existing immutable job reservation, matching signed nonce and UUIDs;
- current PostgreSQL/JPA worker fence under a pessimistic DB row lock;
- current STOP epoch equality and unrevoked reservation;
- both signed and stored expiration, checked at time of verification.

A STOP or tampered ownership/grant makes new checks fail closed.
This is **NOT a transferable RUN permit** and is explicitly returned
as `LAB_ONLY_CHECK_NOT_LAUNCH_AUTHORITY`. The verifier currently runs
as a separate Java instance in unit and Docker lifecycle test code,
**not inside the Docker workload or in a separate service identity**.

### Synthetic supervisor-signed exit observations

`SyntheticOciExitReceiptLab` defines a domain-separated, canonical
Ed25519 report binding a job UUID, worker ID, STOP epoch, 64-character
container ID, observer timestamp, Docker exit code, running/PID state,
no-live-container-process-listing observation and revoked DB flag.

The signer refuses obviously incomplete observations. The verifier
pins a reporter public key and independently binds every identifier;
wrong signer, stale timestamp, mismatch, altered fields, incomplete
STOP or still-running process are rejected. The verified report says
ONLY that the **test supervisor signed a synthetic Docker observation**.

The Stage 45.5.4 CI Docker lifecycle test now additionally:
1. Re-verifies the signed synthetic reservation with a separate
   worker-side policy checker before launching the fixed BusyBox image.
2. After committing DB STOP, confirms the worker-side checker DENIES
   the previously accepted job.
3. Runs the existing explicit synthetic container SIGKILL, observes
   stopped PID namespace and validates the Docker exit code (137).
4. Signs those observations using an ephemeral **test supervisor**
   Ed25519 key and verifies the signature and correct job, worker,
   container and STOP epoch, while a wrong key fails.

A new H2 and PostgreSQL 16 test suite covers forged signatures,
invalid grant/owner/project, mismatched nonces, missing reservations,
durable STOP and freshness.

## Serious limitations and blockers

- **The OCI workload still does not check authorization itself.**
  Verifying a DB fence just before the controller calls Docker leaves
  a TOCTOU window between check, launch and runtime. There is no
  independent authenticated worker OS process, no protected database
  credential for such a worker and no durable job-claim/STOP lease
  held atomically across actual launch.
- **Not a cryptographic OS attestation.** An Ed25519 signature proves
  only which key signed an observation, not that the signer observed
  truthful kernel state. This is signed **CI test harness evidence**,
  not an independently trusted production process-exit receipt.
  The supervisor's signing key is generated in the CI test itself.
- A privileged Docker daemon is used on disposable Linux GitHub
  Actions hosts. Rootless OCI/microVM isolation, dedicated non-admin
  service identity, seccomp/LSM review and container-escape resilience
  remain untested on the user's Windows/WSL workstation.
- The API remains **non-executing**, fixed-profile test only and
  default-off for real agent commands. No generic RUN operation,
  application opening, voice control, shell, plugin requests, browsing,
  filesystem modification or autonomous cybersecurity actions were
  enabled.
- No realtime cross-process STOP subscription, process-tree kill
  broker or signed durable exit receipt stored in an external audit
  service. Host crash, lost acknowledgement, DB failover/rollback,
  malicious worker and owner impersonation require more controls.
- JPA lab `create-drop` is for disposable CI only, not a reviewed
  production schema migration.

## Next security gate

Put verified job signature and current STOP epoch checks **inside a
separately isolated, non-admin worker**, with authenticated transport
to a durable control plane and a narrow fixed-profile RUN capability.
Protect key material and database credentials from workloads. Block
a launch if STOP races with admission; continuously fence live work.
Have an independent privileged host supervisor actually kill the
entire process namespace and issue an auditable, independently
verified, revocable exit record. Test cross-owner denial, crash/restart
and Windows/WSL physical hardware.

**NO MERGE, DEPLOYMENT, OWNER DATA, HOST COMMAND EXECUTION OR PRODUCTION
SECURITY CERTIFICATION IS AUTHORIZED BY THIS PR.**

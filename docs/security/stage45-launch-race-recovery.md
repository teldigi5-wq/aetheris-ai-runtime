# Stage 45.5.8 — Launch-time STOP and worker recovery fault injection

**CI-only laboratory. DRAFT. No agent RUN capability, no production deployment,
no protected merge and no physical workstation execution.** Stacked on draft
Stage 45.5.7 PR #54 and the earlier unmerged security PRs.

## Security change: launch is one-shot on an intact local mount

The synthetic Go worker now uses the narrow, persistent, non-root OCI state
volume from Stage 45.5.7 to create a separate **`launch.once`** record
*before* starting its fixed harmless sleep process tree. Creation is
`O_CREAT|O_EXCL|O_NOFOLLOW` with `0600` permissions, file `fsync` and
parent-directory `fsync`. A preexisting `launch.once` or terminal
STOP/DENY marker, including a corrupt or symlinked file, prevents new work
from starting. A crash that occurs after that claim and before child
creation remains denied on restart rather than reviving from an unexpired
ALLOW. There is no automatic rearm or cleanup of permanent records.

A second check of the signed job's Ed25519 lease and STOP epoch now
runs immediately before the launch-once marker. A **disposable CI-only**
named `prestart.hold` file lets tests freeze the guard between its
initial valid ALLOW and the child-spawn decision. The guard revalidates
the signed lease every 50 ms while held, and fails closed if held more
than 5 seconds, if the lease expires, disappears, is tampered with,
or if a valid higher-epoch STOP arrives. A prelaunch STOP is written
as an irreversible `terminal.tombstone` before any fixed sleep children
are launched.

After launch, the worker retains the prior 100 ms signature and
lease-expiration checks, durable terminal marker and cleanup of
the synthetic child process group. Its host-mounted fixture remains
read-only, while the narrowly scoped state directory is the only
additional writable path. The container is UID/GID 65534,
network-disabled, read-only root, cap-drop ALL, no-new-privileges,
CPU 0.5, memory 128 MiB and PID-limit 32. The Docker daemon on CI's
host is still privileged, **not rootless**.

## Adversarial verification in disposable GitHub Actions

Go unit tests exercise prelaunch STOP over an initially valid permit,
signed lease expiry during a hold, irreversible launch claim,
terminal and launch symlink/corrupt state denial.

A real fixed Go binary runs inside a digest-pinned BusyBox OCI
container on ephemeral Linux CI; Spring JPA uses disposable PostgreSQL 16.
Four new explicit-only `InWorkerCancellationFaultsIT` cases cover:

- **STOP during launch:** pause the guard before fixed child creation,
  commit PostgreSQL STOP and job revocation, issue a signed higher-epoch
  STOP, and confirm the OCI worker exits without ever reporting
  `SYNTHETIC_INWORKER_SIGNED_READY`. A later old ALLOW cannot start it.
- **Disconnected publisher:** while harmless sleep is running, remove the
  lease file. The guard must terminate, persist a DENY tombstone and
  reject an old ALLOW on restart.
- **Expired permission:** leave an otherwise valid short-lived signed
  lease unrenewed. The worker must stop and persist DENY rather than
  continue when the issuer is absent; DB reservations may be pending.
- **Abrupt host kill:** kill the synthetic container without first
  delivering STOP. Its persistent `launch.once` must prevent a
  re-created container from using a formerly valid ALLOW. The DB
  may still hold an unrevoked reservation: this is fail-closed
  **local restart prevention**, not global transaction recovery.

The preceding lifecycle integration suite, Go test cases, fixed OCI
plan tests and complete orchestrator Java regression must also pass.

## What this DOES NOT guarantee

- **No race-free distributed launch or instantaneous STOP.** A database
  STOP may commit after the final lease check, or while a child starts;
  the worker only observes the signed update at polling cadence or
  expiry. The CI-only hold validates one reproducible interleaving,
  not every possible race. The controller and worker do not share
  an atomic launch/STOP authority.
- **Privileged-host rollback bypasses the local claim.** A compromised
  host, Docker daemon, privileged operator, or restored/old volume
  snapshot can delete or roll back `launch.once` or STOP records.
  Local fsync protects ordinary process crashes only, not malicious
  storage or full power-loss/HA/failover scenarios.
- **No automatic recovery to running:** the restart policy intentionally
  denies further work after launch, failure or STOP on the same mount.
  New work would require a separately reviewed, securely provisioned
  fresh worker identity and volume, not deleting a marker.
- **No actual publisher authentication against PostgreSQL inside the
  container:** the Go guard verifies signed leases from a synthetic
  CI key rather than obtaining an independently authenticated durable
  STOP epoch from the database or a protected supervisor.
- **No production OS-confined agent execution:** the workload is a
  fixed harmless sleep fixture, not arbitrary shell/model/plugin
  commands, application-opening, browsing, cybersecurity tools or
  user data. There is no rootless/VM host boundary, Windows/WSL PC
  validation, external kernel attestation, live owner approvals,
  production key custody or independent security review.
- **No certification of real process-tree termination:** CI observes
  Docker's synthetic PID namespace state and exit codes, not all
  possible host descendants, and signs no production-grade OS
  attestation. A crashed OS process can leave storage in inconsistent
  or partially written states; those states deny startup but require
  authorized forensic recovery.

## Next security gate

Independently trusted host- or microVM-enforced launch/STOP protocol
with transactional monotonic epochs, durable distributed replay denial,
untrusted issuer and rollback tests, host supervisor forced-kill
timeout, signed external exit auditing, multi-owner concurrency,
Windows/WSL workstation tests, and human security review. Keep
production agent command execution **default OFF** and PRs in draft.

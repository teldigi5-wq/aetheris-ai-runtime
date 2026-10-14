# Stage 45.5.7 — Persistent in-worker STOP/denial tombstone (CI laboratory)

**DRAFT / CI-ONLY / DO NOT DEPLOY.** Stacks on Stage 45.5.6 draft PR #53 and the
unmerged Stage 43–45 security stack.

## What changed

The previous non-root OCI guard verified a short-lived Ed25519-signed ALLOW or
STOP every 100 ms, but had only a *process-local* decision. A container restart
before the signed ALLOW lease expired could replay that lease after a valid STOP
was already observed.

The Go guard now maintains a **persistent, fail-closed terminal tombstone** on
one narrow dedicated OCI bind mount at `/aetheris-state`:

- It checks for ANY existing terminal marker **before checking signed ALLOW**
  and again immediately before spawning fixed synthetic sleep children.
  A marker, corrupt marker, symlink, inaccessible directory or I/O failure
  denies startup; it never attempts to rearm a stopped synthetic worker.
- A verified higher-epoch STOP is written as a terminal marker **before** the
  fixed process-group kill/exit is completed.
- A bad/missing/expired signed lease creates a durable DENY tombstone on the
  synthetic worker. Further attempts to revive that worker from an old valid
  signed ALLOW are denied.
- Marker creation uses Linux `O_CREAT|O_EXCL|O_NOFOLLOW`, fixed `0600`
  permissions, file `Sync`, and parent directory `Sync`. A truncated or
  maliciously replaced marker is not trusted as permission; mere presence
  prevents activation. No API to delete/re-arm tombstones is introduced.
- The read-only root and workspace stay intact. The **only additional
  writable path** in the OCI fixed-profile plan is a separate STOP directory,
  with symlink/mount delimiters checked and world-writable paths refused.
  The disposable CI host owner retains access while synthetic worker group
  65534 can write this directory; key files and application data never enter
  it. Fixture permissions are configured exclusively in test code.
- The fixed, digest-pinned BusyBox non-root container still has network none,
  all capabilities dropped, no-new-privileges, PID 32, 128 MiB memory,
  0.5 CPU and noexec scratch. The normal agent/command runtime has no
  access to the fixture or Docker.

## Test plan

The explicit synthetic PostgreSQL 16 + real Docker integration test checks:

1. A tampered signed lease fails inside the OCI guard **before child launch**;
   the worker writes a durable DENY record.
2. A previously valid ALLOW reissued to the same worker/state directory
   is rejected after that DENY, including after restarting the container.
3. A separate clean synthetic STOP state permits one fixed sleep workload.
   Post-commit PostgreSQL STOP revokes the job, and the CI issuer publishes
   a higher-epoch signed STOP.
4. The OCI worker observes STOP, writes its persistent tombstone, kills
   the fixed child process group and exits. Docker confirms a stopped PID
   namespace and expected exit status.
5. After the container is removed, the test replays a newly signed
   original-epoch ALLOW **within the still-valid lease period**. The
   recreated container must reject it before launching children, based
   solely on its local terminal marker, not the controller.
6. Go unit tests check irreversible marker creation, bad/corrupt marker and
   symlink behavior, missing state, and attempted replacement. Java tests
   verify separate mounts, no world-writable state, no symlink state, and
   fixed OCI isolation flags. Complete Java regression also runs.

## Remaining security limits

**A writable host-mounted directory is NOT an anti-rollback hardware root.**
A privileged CI host/operator or compromised host daemon can delete,
replace, roll back or remount the marker. It is durable only across
**normal process/container restarts on the same still-intact CI host**.
Database restore/failover and host reboot/power-loss persistence were
not independently certified. A filesystem `fsync` is not a guarantee
against faulty storage or malicious administrators.

**STOP cannot be truly atomic with a remote lease publisher or OCI launch.**
If the DB STOP commits but the CI issuer has not delivered its signed STOP,
the worker may continue until its existing short lease expires. If a
malicious controller with issuer signing keys replays a previously valid
ALLOW *before the worker observes a STOP/denial*, the local tombstone
may not exist. Polling at 100 ms is not instantaneous termination, and
the short lease's wall-clock expiry is not a trustworthy monotonic
hardware revocation epoch. No production execution should rely on this.

The worker's local write and STOP are not authenticated by a separate
rootless/VM supervisor. The CI host Docker daemon remains privileged;
only the OCI workload is unprivileged. The fixed script runs harmless
synthetic sleep children. No real owner identity/approval, arbitrary
commands, tools, browsing, desktop application access, confidential
data, production key management, external audit, secure output handoff,
kernel attestation or physical Windows/WSL validation is included.

**Next gate:** independently protected monotonic revocation storage or
trusted host supervisor, real authenticated worker identities,
transactional admission/STOP fencing across launch and active work,
host-enforced kill timeout, rollback/restart fault injection,
rootless/VM isolation and third-party review. Release stays CLOSED,
all security PRs stay OPEN DRAFT, no protected merge/deploy/live use.

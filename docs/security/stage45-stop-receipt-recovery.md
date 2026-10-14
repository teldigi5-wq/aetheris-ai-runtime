# Stage 45.5.3 — Bounded STOP transport and lost-acknowledgement recovery

**DRAFT/LAB ONLY. No autonomous agent commands, no production deployment
and no real owner records.** Builds on Stage 45.5.2 draft PR #49.

## Improvements in this slice

**Bounded local IPC:** `BoundedUnixStopTransport` uses Java NIO
nonblocking Unix-domain-socket operations. A single monotonic deadline
covers connect, complete signed request transmission and the complete
signed receipt response. The lab client is limited to **2.5 seconds**
overall; the lab worker gives each request receive/ack write its own
**1.5-second** deadline. A partial/stalled peer, invalid length,
unexpected EOF or signature cannot become an unbounded blocking read.
No TCP listener, user-chosen socket path or command operation is added.

**Nonce-bound STOP marker:** the STOP-only process now persists
`STOPPED:<epoch>:<nonce>` in a private, CREATE_NEW, force-synced file.
The nonce and epoch are generated in and retained by Stage 45.5.2's
durable database outbox and are present in a controller-signed STOP
request. A malformed, symlinked or permission-weakened marker fails
closed and cannot be treated as a clean worker startup.

**Recovery-only restart:** after a valid marker is present, a new
worker JVM does not rearm or run jobs. It opens the same private
signed Unix STOP-only socket to respond solely to a **freshly signed**
retry carrying the **exact persisted nonce, worker ID and epoch**.
Mismatched/replayed-as-new STOP data are denied. It signs the same
limited marker receipt and exits; the separate controller can then
persist a verified database acknowledgement. A fresh lease permits
a controller to retry after an earlier request expired without altering
the original STOP identity.

**Synthetic test:** an actual separate Java worker process receives
STOP and persists the marker; its signed response is deliberately
discarded before the DB acknowledgement. A separate worker JVM and
fresh coordinator instance subsequently recover the original
outbox receipt. Adversarial tests verify that wrong nonces are denied,
the STOP fence remains latched, and the STOP-only worker processes exit.
A separate malicious partial-frame test enforces the client timeout.

## What these tests do NOT prove

- The receipt confirms a private synthetic STOP **marker**, not that an
  AI agent's process tree or descendants were terminated. There is no
  RUN operation or real agent task. The tests independently inspect
  the **STOP-only lab worker JVM's** process exit, but that observation
  is not a cryptographic attestation of future agent process exits.
- The standalone worker remains under the CI OS user, **not** rootless
  inside a separately secured VM; the test private signing key file
  does not meet production key-custody guarantees.
- A corrupt/partial STOP file (e.g., power loss during marker creation)
  forces denial but currently requires a separately authorized forensic
  recovery procedure. It does **not** silently reinitialize a worker.
- Database STOP and local file STOP remain separate transactions. A
  signing-key loss, database failover/rollback, corruption, lost device,
  forged local filesystem state or broken socket can leave delivery
  pending and cannot justify rearming.
- Worker listen/accept is not a production supervisor, and a connected
  peer could use the available bound per-request time slot. There is no
  authentication of Unix peer UID, global rate limiter, TLS, distributed
  nonce ledger held by worker, signed process-exit record or production
  owner/approval integration.
- Previous Stage 45.5.1 STOP marker format was test-only; this lab
  revision updates the fixture marker to bind its nonce. No migration
  against any deployed files is attempted or authorized.
- Physical Windows/WSL, concurrent workers, crashes at each fsync
  instruction, production PostgreSQL HA and independent security review
  are untested.

## Release blockers

A dedicated non-admin rootless/VM worker with host-side process-tree
isolation and verified termination, authenticated and authorized
owner-bound RUN/STOP operations, crash-consistent durable launch/STOP
epochs, signed independently checked OS exit receipts, secure key
management, robust forced cancellation, rollback/failover and Windows
hardware verification. Until these pass, keep raw ProcessBuilder
execution disabled and leave all security PRs as unmerged drafts.

# Stage 45.5.2 — Durable STOP → authenticated local IPC (laboratory)

**DRAFT, unmerged, default-off.** All tests use generated keys, synthetic
worker identities and disposable H2 or PostgreSQL databases. No production
agent, real owner memory, operating-system task execution or deploy.

## Actual connection added

A plain-Java, manually constructed `DurableStopIpcBridgeLab` joins:
- Stage 45.4 `DurableSandboxAdmissionLab.stopWorker()` for **transactional
  persistent STOP and job reservation revocation**;
- a new `SandboxStopDispatchEntity` persistent outbox, keyed by worker
  and STOP epoch with an immutable random nonce;
- Stage 45.5.1 `SandboxWorkerStopWire` fixed STOP-only Unix-socket
  protocol with Ed25519 signatures and independently pinned worker key.

The bridge **persists the database STOP first**, then creates or loads an
outbox row, and only then attempts signed IPC. On a valid independently
signed receipt whose nonce, worker ID and STOP epoch all match, the
bridge atomically records `acknowledged=true` in the database.
A forged or mismatched receipt is not acknowledged. Disconnection
leaves STOP persisted and delivery pending. A new coordinator instance
using the same database can retry the outbox after the worker returns.

If the process crashes between STOP and outbox creation, the retry
reads the already-stopped fence and constructs its outbox row. STOP is
never rolled back due to IPC failure. A missing fence or fence that
is not already stopped is not a valid retry source.

The response `SIGNED_WORKER_MARKER_ACKNOWLEDGED_NO_OS_EXIT` is
intentionally **not** a claim that any process was killed. The worker
has no job execution operation and signs only the fact that its
synthetic local STOP latch was persisted.

## Tested

- H2/Spring JPA synthetic tests cover durable acknowledgement and
  restart, offline pending→retry, forged signer, valid signature with
  wrong nonce, missing fence, post-commit missing outbox recovery
  and worker separation.
- A real second Linux Java JVM with private Unix socket and synthetic
  key files is initially absent. DB STOP persists; on worker startup
  a **fresh controller** retries, verifies the signed reply and stores
  the acknowledgement. Worker persists its local STOP marker and
  refuses to restart accepting requests.
- An independent GitHub Actions PostgreSQL 16 job runs the same
  synthetic integration tests using the actual PostgreSQL driver.
- Complete Java regression is also required.

## Not safe for operational deployment

- **No autonomous process termination:** DB STOP is a reservation
  fence, and the IPC STOP is a local marker. Neither terminates a
  task or proves that the OS process tree is dead.
- **No single transaction spans database + local socket.** A worker
  may durably latch STOP and crash before its acknowledgement reaches
  the bridge. In this case the outbox stays pending; the current
  standalone worker refuses restart and cannot re-acknowledge.
  This is fail-closed but NOT a complete recovery protocol.
- **No bounded socket-read timeout or hardened async delivery.**
  A malicious or stuck IPC peer can block a lab caller. Do not
  connect to a server request, agent or scheduler.
- **No signed receipt retained for offline audit:** the bridge
  verifies the signed reply and stores an acknowledgement boolean,
  nonce and epoch, not a re-verifiable signature transcript.
- **No OS credential separation:** both laboratory JVMs run under
  synthetic CI user with private key files. Production needs
  isolated service identities/VMs, key escrow/rotation and peer
  credential checks.
- **No TaskControlService event subscription, real owner login,
  project approval verification or worker-to-DB monotonic epoch
  checks** in this bridge. STOP requests are not exposed through an
  API or GUI.
- **No durable request-time nonce/expiry and acknowledgement retry
  for partial frames after a worker has latched**; proper outbox
  delivery semantics, atomic receipt handoff and crash recovery
  require separate design.
- **No production migrations or PostgreSQL HA/failover testing**.
  JPA create-drop occurs exclusively in disposable CI databases.

## Next certification gates

Independent authenticated worker identity and issuer, rootless/VM
execution boundary, explicit approval checks, a persistent monotonic
STOP epoch confirmed by the worker itself before and during fixed
job profiles, process-tree kill acknowledgement, bounded transport
deadlines and crash-consistent receipt replay, real concurrent DB
admission-vs-STOP tests, Windows/WSL owner-PC integration and external
security review.

**No protected merges, owner-data access, paid infrastructure,
command execution or production activation are performed here.**

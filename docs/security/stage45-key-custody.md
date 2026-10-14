# Stage 45.5.14 — Signing-key custody and non-replayable STOP IPC

**REVIEW LAB ONLY · OPEN DRAFT · NO PRODUCTION AGENT EXECUTION.**
Stacks on the verified but unmerged Stage 45.5.13 PR #60, and all
preceding Stage 43–45 reviews.

## Scope and actual improvements

Stage 45.5.12 split the heartbeat verifier (Linux UID 65534) from a
more privileged fixed-target OCI STOP broker, using an Ed25519 STOP
signature plus Linux `SO_PEERCRED`. Stage 45.5.13 added extra Docker
identity/PID checks and bounded failure behavior.

This slice closes narrower *local-key file* and *captured STOP frame*
gaps for the disposable CI profile:

1. **Key file custody.** A dedicated Go helper opens key material via
   `O_RDONLY|O_NOFOLLOW|O_CLOEXEC` and checks the **opened descriptor**
   with `fstat`, including regular-file type, exactly one hard link,
   bounded size, and no group/other-write permissions. The verifier's
   private Ed25519 IPC-signing key must be owned by its effective Linux
   UID, without any group/other permissions. A partial read or invalid
   PKCS#8/Ed25519 encoding is rejected. A public key's DER SHA-256 digest
   must match a separately supplied immutable startup fingerprint;
   a valid but substituted signing key fails. Symlinks, hardlinks,
   over-permissive private keys, and unpinned public keys fail closed.
2. **Fresh per-connection STOP challenge.** The fixed-target broker,
   *after authenticating the local Unix peer's UID 65534*, generates
   a 32-byte CSPRNG challenge and sends its lowercase-hex encoding
   to the verifier. The unprivileged process signs its bounded
   fixed job/worker/container/epoch/STOP-reason transcript **including
   the challenge**. The broker accepts only the exact challenge
   issued on that particular connection, with the already pinned
   verifier public key. A captured earlier signed STOP cannot
   authorize a different connection even within its timestamp window.
3. **Independent pinned identities in the test fixture.** The
   disposable Java publisher records both signing-key fingerprints
   when the job is created and supplies those recorded pins on
   process startup; it does not recompute them after a possible
   key-file replacement. Only ephemeral **synthetic** issuer and
   verifier keys are used, with `0600` private-key file permissions.
4. **No broadening of execution.** Broker only receives fixed
   immutable OCI container identity, job UUID, worker/epoch and a
   short deadline. The only privileged operation remains bounded
   Docker SIGKILL and identity-bound Docker inspection. The
   verifier remains UID 65534 without Docker access. All commands
   come from compiled CI-only code rather than agent instructions.

## Evidence requirements

Three exact-PR-HEAD GitHub Actions jobs:

- Go adversarial key file tests (valid, forged, symlink, hardlink,
  permissive modes), nonce-challenge cross-connection replay
  rejection, existing Docker ID/PID substitution and outage tests.
- Actual two-process Linux CI with separate UID 65534 verifier,
  pinned public keys, challenge-response Unix IPC, fixed digest-
  pinned nonroot BusyBox OCI sleep workloads, and disposable
  PostgreSQL 16: committed signed STOP, silent publisher and
  verifier-absent broker deadman.
- Complete Java orchestrator regression to prove these CI-only
  changes do not enable other agent or host execution paths.

## Explicitly NOT proven

- **Not KMS, TPM or HSM custody.** These are ephemeral test keys
  stored in ordinary process memory and files on the same hosted
  CI machine. The privileged host, Docker daemon or compromised
  fixture publisher can read the verifier key or change both
  the public key and trusted startup fingerprint.
- **Not independent hardened broker identity.** The separate
  verifier runs as a different Linux UID, but the broker still
  uses the host's root-equivalent Docker daemon. There is no
  dedicated microVM, remote attestation, external trust anchor,
  production mTLS principal, or secure hardware key storage.
- **Not complete defense against hostile local users.** Linux
  file owner/mode checks and peer credentials are narrower checks;
  a privileged attacker can manipulate process arguments, storage,
  the kernel or daemon. Parent-directory ancestry and future
  permission changes require dedicated OS service hardening.
- **Not zero-latency STOP or kernel evidence.** The broker must
  still contact a functioning Docker daemon. A failed or killed
  broker cannot guarantee termination; a signature proves
  participation by its test key, not physical isolation or
  termination of arbitrary host descendants.
- **No production execution:** no owner permissions, actual
  voice/desktop tasks, general agent tools, Windows/WSL physical
  testing, real database migration, audit export, deployment
  or protected-branch merge.

## Next stage

Stage 45.5.15 must address append-only security audit and durable
revocation/rollback checks. Beyond that, independent OS/VM-based
service custody and secure owner-approved task execution are
still required. All Stage 43–45 security PRs remain **OPEN DRAFT**;
the production release and actual agent execution gates remain closed.

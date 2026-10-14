# Syntra × Aetheris Security Architecture — Stage 43 Review Gate

**Status: DRAFT / not a production security certification.**
Neither an AI model nor encryption can make a breach mathematically
impossible. The security objective is **defense in depth, least privilege,
small attack surface, demonstrable containment, and recoverability**.
Never advertise “unhackable”, “all data encrypted” or guaranteed secrecy
unless every covered data flow, key, backup and endpoint has been audited
and proven in the target deployment.

## Audited concrete weaknesses and changes

1. `WorkspaceSandboxService` previously checked lexical path containment
   via `normalize().startsWith(root)`. A symlink created *inside* the
   workspace could target a file/parent *outside* it while passing the
   lexical check. This change rejects symlinked path components, checks
   configured root ancestry and uses `NOFOLLOW_LINKS` when opening files.
   Read bytes are bounded at open. Regression tests use an external
   secret fixture and verify the secret remains inaccessible/unchanged.
2. `CommandSandboxService` used an executable-name allowlist containing
   interpreters/build tools such as `node`, `java`, `npm`, and `git`.
   An executable allowlist is **not** an operating-system sandbox.
   `aetheris.execution.command-execution-enabled` now defaults to
   **false**, even when agent policy/approval permits a terminal tool.
   The `workspace-write-enabled` flag also defaults to **false**.
   A trusted user may experimentally opt in only in a **disposable,
   network-restricted lab**; do not activate raw host processes on a
   deployed multi-user runtime. Future executor must use low-privilege
   OS/container isolation, resource limits, no secret mounts, no network
   egress, scoped workspace and explicit owner approval.
3. A new **unregistered** `AeadFieldProtector` implements
   versioned AES-256-GCM authenticated encryption for future
   *record-specific* fields. It requires an externally supplied 32-byte
   key, a key ID and nonempty contextual AAD (owner+project+record+field).
   Each operation uses a fresh 96-bit random nonce and 128-bit tag;
   there is NO default signing or encryption key, plaintext fallback,
   hard-coded production secret or unsafe `ECB` mode. It fails on wrong
   key/owner context, changed ciphertext, unsupported version, missing
   key or excessive field size. In-memory key bytes are scrubbed on
   `close()`; JCA/provider copies cannot be guaranteed to be erased.
   The utility is **NOT CONNECTED** to JPA or Syntra memory yet.
4. The existing Cybersecurity division now includes owner-governed
   CISO, Cryptography Officer, Detection Engineer and Threat Hunter
   roles alongside Security Architect, AppSec, Blue Team, incident
   commander and Authorized Red Team. These are **catalog
   definitions/permissions, not trained skill certifications or live
   operational monitoring**. The red-team agent remains CRITICAL risk,
   owner-approval mandatory and restricted to explicitly authorized
   `security-lab` and `approved-targets` tools—no arbitrary host
   exploitation or Internet targets.

## Encryption and data-protection inventory (required before release)

| Asset / path | Required protection | Verified now |
| --- | --- | --- |
| Native WinCred refresh tokens | Windows Credential Manager user-session isolation; zeroize process copies; native-only access; rotation/revoke; access control | Isolated synthetic WinCred tests only. Real native auth not connected |
| Runtime-to-identity, gateway-to-runtime, PC-to-backend | TLS 1.3 where supported; authenticated host identity, host allowlist, no direct public runtime ports; service trust | **NOT fully certified** |
| User/task/agent text, secrets, embeddings, documents | Per-record AES-256-GCM or validated provider encryption at rest; owner/project AAD; per-tenant key authorization; envelope rotation | **AES utility tested only. Existing JPA fields not migrated** |
| PostgreSQL storage/WAL/replicas/backups | DB volume encryption, backup encryption, strict DB roles, audited restore drills, tested least-privilege credentials | **NOT certified; synthetic CI uses disposable PostgreSQL** |
| Audit logs, telemetry, crash reports and prompt traces | Redaction before persistence and telemetry; no Authorization, JWT, refresh token, model prompts or PII in metrics; restricted retention/access | **NOT comprehensively audited** |
| AI model/RAG indexes and vector stores | Account isolation, encryption and deletion policy; adversarial retrieval tests; prompt-injection filtering | **NOT comprehensively audited** |
| Plugin/MCP/browser/host/agent tools | Scoped short-lived grants; allowlisted egress; malware scanning, signature/provenance; no implicit filesystem/network authority | **Partially gated; full connector/tool audit pending** |
| Client storage/cache | No plaintext tokens in JS/localStorage/browser cookies or analytics; OS secure storage and memory clearing | Native draft prohibits JS token issuance; real desktop not activated |

Do **not** derive encryption keys from account passwords or commit them to
GitHub, Docker files, workflow logs, Vercel env exports, chat messages or
client binaries. Use an external key management system or a reviewed
Windows DPAPI/TPM-backed root with unique scoped data keys and documented
rotation/recovery. Key loss means ciphertext loss; do not add plaintext
recovery paths. Backups and exports need equal protection to production.
Secure transport protects bytes **in motion**, not a compromised process,
privileged administrator or approved but malicious agent.

## Threat model and defensive Cybersecurity division

| Attack / trust boundary | Required detection and prevention |
| --- | --- |
| Symlink/hardlink/TOCTOU sandbox escape | Current symlink checks + default-off writes; replace with OS-enforced immutable sandbox & directory-handle scoped I/O before real execution. Hardlinks, Windows junctions and concurrent parent renames are **not yet race-free** |
| Prompt injection in retrieved docs/web/MCP responses | Treat tool outputs as untrusted; never turn text into capability grants; independent deterministic owner policy, constrained outputs, evidence provenance |
| Cross-owner task/history/access leakage | Signed server-side identity, immutable owner/project DB IDs, per-owner authorization on every indirect route and stream, no shared SSE |
| Malicious agent or external “hacker” tool | Explicit owner-controlled target scope, egress deny by default, segregated labs, critical-action approval, immutable audit evidence, independent kill switch |
| Stolen JWT/refresh/access token | Asymmetric verification / attested session, expiry and server-side revocation, native vault rotation with rollback plan, no HMAC secret on client |
| Dependency/model/plugin supply-chain poisoning | Pin and verify hashes/signatures, SBOM, SCA, SAST, sandboxed updates, signed releases and independent security review |
| Encrypted database compromise | Per-owner envelope key separation, external key provider, principle of least privilege, periodic rotation, restoration/incident tests |
| Incident / ransomware / lost laptop | Remote revoke and STOP, non-administrative runtime, isolated recovery backups, trusted incident runbooks, offline recovery drills |

The cybersecurity division is for **expert-level defensive work**:
threat modelling, secure architecture review, authorized lab
assessments, incident command, detection engineering, malware triage,
cryptography review, hardening, and patch verification. Do not describe
it as a self-credentialled world-class pentester; model proficiency
must be measured with reproducible benchmarks, human review and
scope-limited evaluation. Offensive testing requires **written, scoped
authorization** for exact assets and dates, no persistent access,
no exfiltration, and explicit consent for high-impact activity. The
“CISO” role cannot authorize its own risky actions or override system
policy.

## Required security milestones before any activation

1. **Complete storage inventory and migration**: classify every sensitive
   field, apply authenticated encryption with owner/project AAD,
   external key IDs, KMS-backed rotation, replay/tamper and backup-restore
   tests; migrate existing plaintext rows without losing history.
2. **Authenticated networks**: no direct public runtime ports, TLS and
   service identity between app, identity and runtime; authenticated
   native session verifier without shipping the HS256 signing secret.
3. **Owner and tool audit**: per-task, project, mission, approval,
   browser, plugin, host, webhook, storage, log and SSE isolation.
   Deny arbitrary command interpretation without OS isolation.
4. **Response**: distributed STOP, logout/revocation, incident isolation,
   alerting, rate limits, throttling, audit retention and recovery drills.
5. **Production-like certification**: restore a PostgreSQL staging
   snapshot, real authorized two-user E2E, SAST/SCA/secret scan,
   dedicated adversarial security review, physical owner's Windows
   workstation tests; collect and independently review evidence.

**Current decision:** keep owner-sensitive HTTP quarantines, streaming
flags, command execution and workspace writes default-off. Do not
merge this draft or claim production encryption, SOC automation, AI
hacking expertise, or “zero breach risk” from isolated CI results.

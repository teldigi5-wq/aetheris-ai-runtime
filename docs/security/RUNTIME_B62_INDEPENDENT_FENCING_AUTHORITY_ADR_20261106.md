# ADR B62-001 — Independent resource fencing authority and release admission

**Status:** PROPOSED / SECURITY REVIEW REQUIRED · **Date:** 2026-11-06 · **Owner:** Aetheris Runtime + Platform security
**Applies to:** Runtime Task / Mission / SSE and distributed effects, plus Platform identity/gateway/revocation.
**Not a production specification or approval.** Stage 45 **NO_GO** · XERJ **HOLD**. No live migration or endpoint changes.

## 1. Verified baseline and reason for this ADR

At this checkpoint, the isolated Runtime review branch at `70f5b9df343747365aefd5a142696207fd349160` passed three exact-commit suites: combined JWT/HTTP/PG16 (86/86 Java tests), independently restricted PG16 (21/21), and physical streaming PostgreSQL16 standby/fencing/replay with cleanup. It includes signed `orchestrator:write` restrictions, pinned RS256 synthetic closure ACK verification, one-time SQL challenge consumption, and eight revocation replay labs. Runtime PR #257 latest inspected head `4b80495ffb1c9b6b51f00534ab1f2b94a06169ca` adds evidence documenting physical replay ledger persistence and a privileged ledger reset counterexample. Platform draft PR #321 `0f296079241ca0c498e52ad0bafe259229c10735` includes function-only emergency writer and synthetic mTLS proofs; it is unmerged and **not a deployed distributed authority**.

The existing tests intentionally show:
- A promoted resource's locally committed `CLOSED` receipt **cannot fence a separately writable old primary**.
- A resource administrator can delete/restage a local consumed-ACK journal, allowing reuse despite process-restart durability.
- A signed resource-looking ACK cannot, by itself, prove the physical resource owning a write was successfully denied, or prevent snapshot rollback.
- Existing resource-local controls do not establish actual owner/tenant authorization for Task/Mission/SSE. Runtime issue #23 and Platform issue #251 remain open.

These are **real release blockers**, not just missing compliance documents. No amount of green CI on the current synthetic components may turn them into production authority.

## 2. Trust-domain separation (mandatory)

| Boundary | Responsibility | Insufficient / prohibited evidence |
|---|---|---|
| Identity/Platform | Verify user, tenant, project, API audience, short-lived session JTI and revocation | Gateway headers without independently authenticated transport |
| Independent epoch authority | Issue monotonic per-resource grants and transitions; durably record fenced prior epoch in a failure domain administrators of a workload cannot reset | Same resource database, writable leader's local clock, self-issued signed tokens |
| Resource enforcement | Deny old epoch at **actual mutation/commit boundary**; terminate/deny old sessions; enforce privilege and storage or network fencing independent of stale coordinator | Client-side lease checks, ACK from promoted node only |
| Attestation verifier | Challenge-bound, short-lived, mutually authenticated receipt bound to real resource/incarnation/epoch and its enforced deny state | Valid JWT alone, client-supplied node/term, cached proof |
| Anti-replay/audit | Durable single-consumption record under a separate administrator and rollback-resistant monotonic authority; independent observable history | PostgreSQL local ledger whose owner can DELETE/restore it |
| Admission/release | Keep API/SSE/task effects disabled until required resource-deny evidence, principal binding, human attestations and independent reviews are all verified | A source marker, CI green alone, test-only `NO_GO` witness |

Require each trusted identity to be bound to an allowed resource and operation. Never reuse a review JWT signing key as the service identity key or put production secrets into the public repository.

## 3. Safety invariants for implementation

**I1 — monotonic authority:** for each resource and incarnation, a strictly ordered epoch is issued by an independently administered authority. No successful effect can commit using a prior epoch after the resource has acknowledged enforcement of a newer one. Authority loss or ambiguous epoch means DENY, never local override.

**I2 — real commit-time enforcement:** authorization and resource fencing must hold through the committed effect. A check-then-act lease verification is insufficient. PostgreSQL control must cover actual SQL paths, parallel sessions, alternate DB endpoints, superuser/restore privileges and delayed in-flight commits. If physical owner or admin privileges can bypass SQL fences, an independent infrastructure-level fence is required.

**I3 — old-primary closure before promotion:** no new writer may be admitted unless **every required old-writer capability** is withdrawn or irrevocably rejected at its own resource boundary. A promoted-node closure receipt is not evidence about the old primary. Losing contact, crash, partition, missed quorum or timeout means NO_GO.

**I4 — authentic resource evidence:** each acknowledgement is tied to a challenge from the verifier, a unique resource ID, incarnation, node/term, authority epoch, operation, intended target and expiration. The verifier pins issuer/key/audience/purpose and authenticates transport and resource workload identity independently of the old writer. No resource may attest on behalf of another.

**I5 — replay and rollback denial:** an acknowledgement/challenge/receipt can only authorize one transition; state persists across process restart, snapshot restoration, credential/CA rotation and promotion. If a resource administrator can erase the replay ledger, that ledger cannot be the sole authority. Attestation from a state older than the independent authority's last accepted epoch is always rejected.

**I6 — least privilege and audit:** normal writer cannot grant itself fence or revocation authority, mutate an authority ledger, or approve a promotion. Emergency operations require independent human/machine authorization, bounded scope, separate actor evidence, tamper-evident audit outside resource admin control and rollback-safe idempotency. Platform PR #321's synthetic SQL functions do not implement this end-to-end.

**I7 — admission remains closed:** no real owner Task/Mission/SSE/API activation before Runtime #23 and Platform #251 are individually verified on direct backend ports, including object-level ownership. Never treat `orchestrator:read` alone as task ownership or `orchestrator:write` alone as deployment authorization.

**I8 — key and session retirement:** certificate/key rollover must revoke/retire old live sessions, including already-connected DB/JDBC pools. Merely removing an old CA from the trusted set does not terminate authenticated long-lived connections.

## 4. Proposed no-mandatory-license-cost architecture (NOT provisioned)

Evaluate open-source consensus/epoch authority (e.g. a properly configured 3-member Raft service) **only if three independently administered failure domains exist**. An on-laptop or single-VM triple-node setup is a **development simulation**, not quorum resilience or production availability. Use authenticated mTLS workload identities and resource-scoped keys with explicit rotate/retire lifecycle; evaluate SPIFFE/SPIRE interoperability only after inventorying deployment platforms and operational ownership.

Even with consensus, a reachable stale PostgreSQL superuser can bypass DB-local SQL. Real storage fencing, DB failover management and administrator boundaries must prevent that stale writer from committing. Use explicit resource policy guards, authenticated admission and independent witnesses. If any capability cannot be fenced, remain read-only/unavailable and require human remediation. Do not purchase infrastructure, expose external endpoints or deploy production services without an approved threat model and operator ownership.

## 5. Test and review acceptance matrix (all must be independently reproduced)

| Scenario | Required observation for future GO |
|---|---|
| Two independently writable primaries, network partition | Old primary rejects writes after revocation at its own commit boundary; promoted-primary receipt alone never unlocks |
| Delayed write transaction, stale token/lease | No stale commit after authoritative fence, including restart and in-flight sessions |
| Privileged journal DELETE/restore/snapshot rollback | Independent epoch/replay authority detects rollback and denies promotion; no local ledger bypass |
| Lost quorum, authority outage, stale ACK, forged identity | DENY, no automatic failover or new resource effect |
| Duplicate challenge, new challenge for old receipt, concurrent replay | One-time acceptance only, durable across restart and different authenticated sessions |
| Key/CA rotation and in-flight old DB pool | Old sessions explicitly retire, forbidden claims remain forbidden |
| Direct Runtime/User/Audit backend requests, forged identity | Anonymous/invalid principal denied; cross-owner Task/Mission/SSE never leaks |
| Authorized real owner flow (positive control) | Correct scoped operation succeeds *only after* all independent gates, without granting extra scope |
| Failed migration, rollback and cleanup | State monotonicity preserved or cutover remains blocked; no uncontrolled writable old primary |

Review evidence must include exact commit SHAs, hashes, real log traces, negative assertions, independent operator observation, fault-injection scripts, environment teardown, and limitations. Tests on a single CI host are not independent hardware or organizational attestations. Require owner-PC validation where previously blocked.

## 6. Staged implementation and release criteria

1. **S-B62-A — Inventory and ADR approval:** independently map real deployment, resource owners, writable endpoints, DB privileges, network/control-plane trust zones, key custody, authority failure domains and which flows are actually deployed. Independent security reviewer approves threat model. **No credentials collected in public docs.**
2. **S-B62-B — Authority interface and resource adapter:** define versioned grant/fence/ack data types and authenticated transport; validate the concrete resource-side commit guard and separate credential domain. Start in disposable, review-only environments. Do not wire to production HTTP/SSE.
3. **S-B62-C — Replay and failback abuse tests:** independent actors/hosts, real DB process/restore faults and privileged rollback attempts; require all matrix denial results and positive owner flow without weakening CI.
4. **S-B62-D — Coordinated Platform/Runtime preview:** separately reviewed PRs, immutable dependency locks, secretless CI, network isolation and rollback rehearsal, external review. No merge to protected main without explicit authorization.
5. **S-B62-E — Release evaluation:** close Runtime #23 and Platform #251 on evidence, obtain all nine Stage45 attestations and any additional release gate, complete owner-PC hardware validation and explicit release authorization. Until then **NO_GO**.

## 7. Decision and non-goals

**Proposed decision:** Do not elevate the current local physical ACK/ledger proofs into production authorization. Implement and test an independently governed epoch and resource-enforced fence **before** exposing Task/Mission/SSE or enabling distributed effects. Preserve all current negative-case CI evidence. This ADR itself changes no implementation and provides no security certification.

**Explicit non-goals:** adding another self-signed ACK, pretending synthetic mTLS is deployed, claiming three local processes form independent consensus, treating a trusted client header as identity, or accepting a green negative witness as a fixed vulnerability.

**Reference states at authoring:** Runtime integrated candidate `70f5b9df343747365aefd5a142696207fd349160`; upstream Runtime #257 `4b80495ffb1c9b6b51f00534ab1f2b94a06169ca`; Platform #321 `0f296079241ca0c498e52ad0bafe259229c10735`; Runtime issue #23 OPEN; Platform issue #251 OPEN. Recheck all refs before further code changes.

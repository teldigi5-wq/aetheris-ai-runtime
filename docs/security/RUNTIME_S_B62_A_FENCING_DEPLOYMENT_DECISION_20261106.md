# S-B62-A — Resource-fencing deployment decision for independent review

**Status: PROPOSED / NOT APPROVED** · **Date: 2026-11-06** · **Parent:** [ADR B62-001](RUNTIME_B62_INDEPENDENT_FENCING_AUTHORITY_ADR_20261106.md) and [current trust-boundary inventory](RUNTIME_B62_TRUST_BOUNDARY_SOURCE_INVENTORY_20261106.md) · **Track:** proposed Runtime S-B62-A ONLY.

This is a source-backed choice record for the S-B62-A **deployment inventory, independent authority and real old-primary fencing** acceptance. It does not introduce S-B62-B, change a runtime API, approve a new leader, replace historical Platform B62-A–G13 or Enterprise E-series roadmaps, or certify release. **Stage45 NO_GO; XERJ HOLD; Execution Phase A BLOCKED_PENDING_HARDWARE.**

## 1. Immutable inputs and observed gaps

| Input checked | Repository/ref and source | Consequence |
| --- | --- | --- |
| Runtime S-B62-A tests | Runtime draft [#282](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/282) at `db5967e282f124f56456e8075693979ad8c0a5ff`; [exact-head real PG16/Java CI](https://github.com/teldigi5-wq/aetheris-ai-runtime/actions/runs/42181145707) | Privileged witness owner can rewind epoch; same CI-host admin can restart a stale writable primary. Real physical fencing **NOT established**. |
| Runtime quarantine parent | [Runtime #257](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/257) `1905026d80d0a6455640fe1237596a38b930533f` | Unmerged draft; never claim review-only 404 filters protect deployed Runtime. |
| Platform default full Compose | [Platform B62-G13 review source](https://github.com/teldigi5-wq/aetheris-platform/blob/test/b62g13-audited-emergency-writer-contract-20261027/docker-compose.yml), Git blob `68531681623ca9f739906fff9dbd55325fd7ef9c` | `orchestrator-service.image` defaults to a mutable image tag containing **older protected-main Runtime SHA** `65a6262717adcd52ac8d8a16ed6f223e299fd74d`; its build context also uses that SHA. The tag is not a cryptographic image digest. **Not** the Runtime #282 candidate. |
| Platform external Compose | [integration-external Compose](https://github.com/teldigi5-wq/aetheris-platform/blob/test/b62g13-audited-emergency-writer-contract-20261027/docker-compose.integration-external.yml), blob `853a3f469671af044c96f81bb11727a063791496` | Demands a supplied Runtime image string but the source does not attest image provenance or independently enforced fencing. Host loopback port `127.0.0.1:8090` does not authorize local processes or container peers. |
| Platform Helm safety and ingress | [safety gate](https://github.com/teldigi5-wq/aetheris-platform/blob/test/b62g13-audited-emergency-writer-contract-20261027/deploy/helm/aetheris/templates/00-safety-gate.yaml) blob `94e42eb6797ecfc7dc426c1e8c0edbac5d483079`; [network policies](https://github.com/teldigi5-wq/aetheris-platform/blob/test/b62g13-audited-emergency-writer-contract-20261027/deploy/helm/aetheris/templates/10-ingress-network-policies.yaml) blob `8bb0c6727b6f8d581a64ea7bd5c531d7bf2fede5` | Nonlocal image references must be digest-pinned, but the listed policies are same-namespace **ingress-only** for selected backends and data stores. They do not prove runtime, egress, CNI or privileged database fencing. |
| Platform source workstream | [Platform #321](https://github.com/teldigi5-wq/aetheris-platform/pull/321) at `0f296079241ca0c498e52ad0bafe259229c10735`; [roadmap draft #377](https://github.com/teldigi5-wq/aetheris-platform/pull/377) at `58a4752e7d91809bd5ac8a402c98c51e9843fe7b` | Function-only emergency revocation and roadmap evidence remain separate drafts; neither establishes an independent remote fencer. |
| Real installed environment | **Not inspected** | No inferred real running images, actual hardware, physical isolation, administrator identities, DNS, BMC, key custody or production trust. |

A source or green test on one review branch must **never** be represented as a green deployed system or a successful physical-fencing action on a different branch.

## 2. Non-negotiable safety properties

1. **Old-writer closure comes first.** No standby promotion, release of new write capability or trusted cutover ACK until every required previous writer is observably denied at its own write/commit or physical/storage boundary, including after administrator restart, existing sessions and partition.
2. **Admin independence is real.** A PostgreSQL superuser/host administrator controlling the old primary must not also be able to reset the only accepted authority, erase the only replay ledger, revoke the external fence or re-enable the old writer. Four containers under one Docker administrator fail this test.
3. **Ambiguity denies.** Lost quorum, witness rollback, failure to authenticate the fencing operator/resource, an unverified image, a disconnected old primary or missing physical observation means **NO PROMOTION / NO NEW EFFECTS**. Availability is deliberately sacrificed rather than granting split-brain writes.
4. **Proof is resource-bound.** ACK/receipt must bind an independently authenticated source identity, resource incarnation, epoch, operation, specific challenge, expiration, remote observation and write-denial status. A signed token, digest or promoted-node assertion alone is insufficient.
5. **Recovery remains controlled.** A stale disk/snapshot or previous leader may not rejoin as writable until independent evidence proves it was rebuilt/rejoined under the current authority. Restarting a stopped container is not durable fencing.
6. **API isolation stays independent.** Runtime [#23](https://github.com/teldigi5-wq/aetheris-ai-runtime/issues/23) and Platform [#251](https://github.com/teldigi5-wq/aetheris-platform/issues/251) require direct-backend owner/tenant authorization regardless of the database failover design.

## 3. Decision options — no automatic implementation

| Candidate | Best possible supported scope | Required / limitations | Decision |
| --- | --- | --- | --- |
| **A. Single writer, no automatic promotion** | No-mandatory-cost local development and isolated recovery rehearsals | A single explicitly controlled PostgreSQL writer, **no standby promotion** while independent old-writer revocation is unavailable; offline operator-reviewed restoration only after the previous writer has been disabled and verified. Fails closed, potentially indefinite outage. Does **not** tolerate a malicious local root administrator. | **Proposed interim policy**, not an approved HA solution or S-B62-A completion. No config changed. |
| **B. Patroni + independently operated etcd quorum + watchdog** | Potential future HA candidate | Separate failure/admin domains and credential custody; DCS majority, explicit demotion policy, `watchdog.mode: required` where appropriate; measure TTL timing and failure windows. Linux software watchdog is **not** independent of a hostile root administrator. Avoid enabling Patroni DCS failsafe mode without explicit partition-safety analysis and review. Requires a physical/storage/network fence that the former primary cannot self-undo. | **Evaluate only** after operator topology exists and independent review accepts a design. |
| **C. Independently administered BMC/power or storage fence** | Candidate for stronger old-primary enforcement | External control plane physically or cryptographically controls old writer's ability to write; old DB/host admin has no restore authority; safe power/storage-fence replay, auditing, operator change control and teardown. May need new hardware or managed services—obtain explicit owner approval before spending. | **Candidate physical enforcement**, not provisioned. |
| **D. Another on-host PostgreSQL witness/SQL trigger/Docker kill** | Disposable negative-test evidence | Same host/DB admin can erase witness, disable triggers or restart former primary; already reproduced. | **Rejected for independent certification**; keep negative CI proof. |

**References for independent reviewer:** [PostgreSQL 16 failover and former-primary fencing](https://www.postgresql.org/docs/16/warm-standby-failover.html); [Patroni watchdog support](https://patroni.readthedocs.io/en/latest/watchdog.html); [Patroni DCS failsafe semantics](https://patroni.readthedocs.io/en/latest/dcs_failsafe_mode.html); [etcd majority/partition failure behavior](https://etcd.io/docs/v3.5/op-guide/failures/). Their descriptions are design inputs, **not** proof that Aetheris runs those products or satisfies their operational assumptions.

## 4. Real-host operator approval matrix (currently unverified)

| Evidence scenario | Required observation for possible future approval |
| --- | --- |
| Inventory: running images, DNS, exposed ports, DB roles, control-plane ownership | Actual immutable image digest and redacted independent-admin/failure-domain mapping, not Compose guesses or a bare commit-looking tag. |
| Old-primary partition / delayed commit | Old primary **cannot commit** an obsolete-epoch write after authorized cutover, including a transaction begun before revocation. |
| Privileged old-primary restart | Host/DB administrator cannot restore write authority by restarting PostgreSQL with the former data volume, restoring a snapshot or rejoining a stale session. |
| Independent authority privileged rollback | DB/host admin rollback and coordinator administrator erasure cannot lower the accepted epoch or forge a resource ACK. |
| DCS majority loss / fencer outage / simultaneous partition | No new promotion or privileged commit while the physical fencing state is ambiguous. |
| Fencing and certificate/key rotation | Existing JDBC sessions retire; revoked old identities, restored keys and stale evidence cannot regrant writes. |
| Positive control (only after negatives) | Correctly scoped owner flow succeeds solely on one current resource, without broad default DB privileges, when security and release gates are independently approved. |
| Incident and recovery | Show separate control of fencing authority, redacted logs, evidence digests tied to exact immutable source, backup/rollback and no inappropriate cutover. |

To run these tests, first obtain explicit **infrastructure owner approval** for fault injection on isolated non-customer hosts. Never run a power-off, STONITH, privileged remote kill, shared-storage detachment or snapshot restoration against production from a CI job or this draft.

## 5. S-B62-A exit and next authorized step

**Still missing:** verified actual topology, proof that an old-primary administrator cannot override resource fencing, independent authority custody/monotonicity under privileged rollback, authenticated resource ACK, physical failure/restore evidence, independent security review of ADR B62-001 and infrastructure-owner approval. A self-reported operator manifest, even one with local SHA-256 matches, returns **NO_GO**; the last Runtime 17+8 Python tests and signed Java regression are not substitute attestations.

**Decision to review:** adopt candidate A only as a documented development availability trade-off; evaluate candidates B/C for real HA, with no automatic promotion or production trust until all physical negative tests pass. **This text is a proposal, not owner consent or reviewer approval.**

**Release boundary:** Runtime S-B62-A **IN PROGRESS**; S-B62-B through S-B62-E **not advanced**; nine release gates **NOT CERTIFIED**; Stage45 **NO_GO**; XERJ **HOLD**; Execution Phase A **BLOCKED_PENDING_HARDWARE**. No protected-main merge, real deployment, Runtime Task/Mission/SSE unquarantine, client permission grant or external publication.


### Owner-machine tools only — non-attesting readiness check

`scripts/security/runtime_b62a_owner_machine_readiness.py` is a safe owner-PC/WSL command-presence preflight. It is deliberately **not** a Docker/host scanner and cannot establish separate hardware or administrative authorities. Run from the Runtime checkout using `python3 scripts/security/runtime_b62a_owner_machine_readiness.py` and expect exit code 1 and JSON `STAGE45_NO_GO`. Do not supply flags, tokens, credentials or host details; the output contains only Booleans. Later physical testing still requires separate owner/operator approval and authorized isolated hardware. Do not treat this as a stage exit criterion.

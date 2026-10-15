# Stage 46.17 — Read-only WFP drop audit and signed-worker release specification

**OPEN DRAFT, SYNTHETIC CI ONLY. No network/firewall changes, real speech models, microphone, installation, autonomous desktop access, or owner-PC pilot. Release decision: NO_GO.**

## Implemented in this stage

**Read-only historical WFP drop-event diagnostic.** The inert native fixture `WindowsSyntheticWfpDropAuditReadOnly.cpp` opens the Base Filtering Engine and creates a strictly bounded network-event enumerator filtered by `FWPM_CONDITION_NET_EVENT_TYPE = CLASSIFY_DROP`. It requests **at most 16 entries**, does not enumerate beyond the first page, and frees all returned memory and handles. The only printed status is **logging disabled**, **snapshot unavailable**, **snapshot empty**, or **uncorrelated drop snapshot present**. It deliberately reads or prints **no event user SID, app ID, local/remote IP, port, timestamp, or unrelated traffic details**. No WFP policies, firewall filters, diagnostic logging settings, event subscriptions or registries are changed.

This diagnostic is intentionally *not* a process-specific network-denial proof. Windows historical events are a snapshot of previously logged events, not a guaranteed live record of the Stage 46.14 synthetic AppContainer's localhost attempt; event collection may be disabled or access-restricted and a historical drop cannot be attributed to Syntra just because its type is `CLASSIFY_DROP`. On unavailable/disabled/empty/uncorrelated findings, the release and physical PC decisions all remain **NO_GO**.

**Independently reviewable signed-worker release checklist.** `stage46-trusted-signed-worker-release-spec.json` is a machine-readable rejection document. It explicitly records **no independently approved reproducible artifact SHA-256, no pinned developer code-signing certificate, no established signing chain or revocation check, no signed dependency manifest, no immutable verified-launch handle, and no approved third-party voice model redistribution license**. This repository/PR cannot self-enroll a signing root or approve a worker. Separate approval and audit will be required before future real speech-engine distribution.

The checklist also records missing per-process AppContainer identity and WFP filter/layer, exact time window, destination/protocol, IPv4/IPv6/DNS proof and reversible transient policy review. The Stage 46.15 reachable localhost **positive control** remains meaningful, but the child connection only timed out; it is not an explicit WFP block event.

**Fail-closed evidence gate.** `tools/security/stage46_wfp_drop_audit_gate.py` accepts only a strict bounded receipt chained to valid Stage 46.16 WFP-layer/Stage 46.15 AppContainer and hash evidence, plus the exact unsigned/unapproved release checklist. It rejects claims of process-correlated network denial, approved signing keys, new firewall rules, owner-PC authorization or production release. A passing synthetic diagnostic always reports `app_specific_drop_verified=false`, `network_egress_certified=false`, `release_provenance_approved=false`, `production_authorized=false`, and `decision=NO_GO`.

18 new adversarial Python tests verify all four legal diagnostic outcomes, contradictory/forged markers, absent process evidence, mutable signer claims, fake network approval, missing prior evidence, unexpected result codes and no-release invariants.

## Future criteria for genuine WFP network denial

A future *separately approved* test must be able to correlate the same disposable synthetic worker **AppContainer/app ID**, the correct **IPv4/IPv6 connection and exact destination port/protocol**, an authenticated event **timestamp within a monotonic bounded test window**, the **kernel classification/drop type with filter and layer ID**, and independent **live positive control**, without logging unrelated user traffic. Logging availability and least-privilege access must be verified; test-specific policy, if ever proposed, must be scoped, audited, reversible and authorized. **This stage explicitly does not perform such a test or change any machine firewall rules.**

## Reproduce on a disposable GitHub-hosted Windows runner

```powershell
pwsh -NoProfile -NonInteractive -File .\workstation-agent\packaging\stage46\Test-SyntraWindowsAppContainerLab.ps1
pwsh -NoProfile -NonInteractive -File .\workstation-agent\packaging\stage46\Test-SyntraWindowsWfpReadOnly.ps1
pwsh -NoProfile -NonInteractive -File .\workstation-agent\packaging\stage46\Test-SyntraWfpDropAuditReadOnly.ps1
python -m unittest discover -s tools/security -p test_stage46_wfp_drop_audit_gate.py -v
```

On Linux, run the policy tests and existing Java regressions:

```bash
python3 -m unittest discover -s tools/security -p test_stage46_wfp_drop_audit_gate.py -v
python3 -m unittest discover -s tools/security -p test_stage45_pre_pilot_gate.py -v
./mvnw -B -f orchestrator-service/pom.xml test
./mvnw -B -f workstation-agent/pom.xml test
```

Microsoft API references: [FwpmNetEventCreateEnumHandle0](https://learn.microsoft.com/en-us/windows/win32/api/fwpmu/nf-fwpmu-fwpmneteventcreateenumhandle0), [FwpmNetEventEnum0](https://learn.microsoft.com/en-us/windows/win32/api/fwpmu/nf-fwpmu-fwpmneteventenum0), [WFP logging](https://learn.microsoft.com/en-us/windows/win32/fwp/logging), and [FWPM_NET_EVENT_CLASSIFY_DROP0](https://learn.microsoft.com/en-us/windows/win32/api/fwpmtypes/ns-fwpmtypes-fwpm_net_event_classify_drop0).

**Safety and truth limits:** WFP event observation is not WFP enforcement. A source-controlled JSON spec is not a signature or a release authorization. Even a synthetic test with logged drops cannot authorize real device access. Full Windows isolation, signed worker provenance, actual speech models, owner identity and Stage 45's **13 production release blockers** remain outstanding. Do not merge or deploy, install on a personal workstation, enable audio capture, change firewalls, or claim a production sandbox.

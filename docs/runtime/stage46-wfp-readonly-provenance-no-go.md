# Stage 46.16 — Windows WFP read-only discovery & release provenance gate

**OPEN DRAFT / SYNTHETIC CI ONLY / NO WFP FILTER MODIFICATIONS / NETWORK EGRESS NOT CERTIFIED / WORKER NOT SIGNED / PRODUCTION & PHYSICAL OWNER-PC PILOT NO_GO.**

## Scope and technical approach

This incremental stage follows Stage 46.15's confirmed reachable control listener, actual Windows AppContainer token and synthetic-file deny ACE, and honest **one-second network connection timeout**. That timeout **does not establish Windows Filtering Platform (WFP) denial**. Instead of inventing an access-denied event or adding risky machine-wide firewall configuration, Stage 46.16 adds a genuinely native, explicitly **read-only** WFP inspection alongside a stricter, externally unapproved worker-release policy.

1. **Native Windows WFP inspection:** `WindowsSyntheticWfpReadOnlyProbe.cpp` uses documented `FwpmEngineOpen0`, `FwpmLayerGetByKey0` on both `FWPM_LAYER_ALE_AUTH_CONNECT_V4` and `FWPM_LAYER_ALE_AUTH_CONNECT_V6`, frees returned layer metadata, and closes the engine. Its process has zero input parameters and opens no sockets. It does **not call any filter/sub-layer/provider add/delete, WFP event-subscription, service configuration, firewall rule, registry setting or network-capture APIs**. Successful execution proves the runner can read the outbound IPv4 and IPv6 connection-filtering layers, **not** that any specific outbound connection was blocked.
2. **Worker trust evaluation:** `stage46_wfp_provenance_gate.py` ingests a bounded nested receipt from the verified Stage 46.15 AppContainer and in-run SHA-256 tamper test, plus the new native WFP read-only evidence. It explicitly separates a stable locally compiled binary from a verifiably signed and independently approved release. There are **zero approved publisher certificate pins** and **zero approved production artifact hash pins** in the source: no real signed distribution package exists. A certificate status of `Valid` or a claim of matching SHA-256 in user-provided JSON **cannot enroll trust anchors**.
3. **Fail-closed diagnostic verdict:** A malformed/forged marker, error exit code, missing ALE v4/v6 layer, missing previous stage's AppContainer evidence, child failure, unexpected input field, hash mismatch or fake approval is rejected. A passing *lab diagnostic* only reports `READ_ONLY_ALE_V4_V6_LAYERS_PRESENT`; `correlated_wfp_denial=false`, `network_egress_certified=false`, `production_authorized=false`, and `decision=NO_GO` remain unchanged. It does not authorize a microphone, model, tool, app launch or production deployment.
4. **Real Windows test, not owner hardware:** The dedicated hosted `windows-2022` runner first reruns Stage 46.15's genuine AppContainer test and unsigned-worker receipt. It then compiles the fixed native WFP probe with MSVC, reads v4/v6 layers with Windows APIs and combines receipts. 18 new adversarial Python unit tests cover release-signing forgeries, missing layer proof, contradictory network markers, unapproved certificate pins, binary mutation, malformed receipts and unauthorized release flags. Other jobs rerun existing restricted-token/Job Object Windows tests, synthetic voice and owner-data isolation Java tests, full JUnit tests and Stage 45's 13-blocker NO_GO policy.

## What is established vs unverified

| Security question | Stage 46.16 evidence | Current decision |
|---|---|---|
| Can the CI runner open WFP and read ALE connection layers? | Verified only when exact-HEAD native Windows CI passes | Read-only diagnostic |
| Was a specific AppContainer connection denied by a WFP rule? | **No correlated classification/blocked-event proof** | UNVERIFIED |
| Are all outgoing IPv4, IPv6, DNS and other channels blocked? | One bounded localhost probe and layer enumeration cannot establish this | UNVERIFIED |
| Is the worker genuine, approved and signed by a pinned publisher? | Same-run hash stability and unsigned test binary; no independently approved publisher/certificate/binary | UNVERIFIED |
| Can a real Syntra microphone, voice engine or desktop agent be enabled? | Missing explicit owner/system authorization and existing 13 blockers | **NO_GO** |

## Reproduce only on disposable CI test environments

On a trusted GitHub-hosted Windows CI runner with MSVC and PowerShell 7:

```powershell
pwsh -NoProfile -NonInteractive -File .\workstation-agent\packaging\stage46\Test-SyntraWindowsAppContainerLab.ps1
pwsh -NoProfile -NonInteractive -File .\workstation-agent\packaging\stage46\Test-SyntraWindowsWfpReadOnly.ps1
python -m unittest discover -s tools/security -p test_stage46_wfp_provenance_gate.py -v
```

Portable Java and security regressions:

```bash
python3 -m unittest discover -s tools/security -p test_stage45_pre_pilot_gate.py -v
python3 -m unittest discover -s tools/security -p test_stage46_worker_trust_evidence.py -v
python3 -m unittest discover -s tools/security -p test_stage46_wfp_provenance_gate.py -v
./mvnw -B -f orchestrator-service/pom.xml test
./mvnw -B -f workstation-agent/pom.xml test
```

## Production prerequisites beyond this stage

A future WFP isolation proof must use an **independently reviewed**, process/AppContainer-identity-specific, strictly time-bounded and automatically reversible enforcement mechanism with correlated kernel block events, a live positive control, demonstrable IPv4/IPv6 scope, reliable failure cleanup and approval for OS firewall mutation. This stage intentionally **does not modify** the hosted Windows firewall or claim such proof. A real worker distribution separately requires a signed, reproducible release, trusted/pinned certificate or equivalent verified provenance, appropriate revocation, content hash manifest, secure process launch that prevents TOCTOU, and owner-approved licensing. None exist yet.

**All existing 13 Stage 45 release blockers remain binding. Earlier draft branches remain open and unmerged. No owner-PC software installation, hardware activation, real model usage, production pilot, app execution or deployment is authorized.**

Microsoft API references: <https://learn.microsoft.com/en-us/windows/win32/api/fwpmu/nf-fwpmu-fwpmengineopen0>, <https://learn.microsoft.com/en-us/windows/win32/api/fwpmu/nf-fwpmu-fwpmlayergetbykey0>, and <https://learn.microsoft.com/en-us/powershell/module/microsoft.powershell.security/get-authenticodesignature>.

# Stage 46.15 — Positive-control network evidence and untrusted worker proof gate

**OPEN DRAFT. SYNTHETIC WINDOWS TEST ONLY. NETWORK EGRESS: NOT CERTIFIED. BINARY PUBLISHER: NOT CERTIFIED. PRODUCTION / PHYSICAL OWNER-PC PILOT: NO_GO.**

## Incremental implementation

This stage builds on Stage 46.14's genuine AppContainer identity, explicit synthetic-file deny ACE, Windows Job Object resource limits and kill-on-close test. It improves **evidence quality and fail-closed interpretation**, without turning the test-only fixture into an active speech engine.

1. The native Windows AppContainer lab now performs a **positive control**: before checking the AppContainer child against an ephemeral `127.0.0.1` listener, the parent must demonstrate that **the very same listener is reachable from a non-AppContainer synthetic client**. It uses a bounded TCP connection attempt, accepts its own control connection and clears the queue before testing the child. A dead listener can no longer be mistaken for an AppContainer networking restriction. The test never contacts external servers, modifies OS firewall policy or enables loopback exemptions.
2. The AppContainer child retains the existing bounded one-second probe: `WSAEACCES` or `WSAENETUNREACH` is reported as **explicit narrow single-listener denial**. A timeout is reported as **NETWORK_POLICY_INCONCLUSIVE**, not verified network egress isolation. An unexpected successful connection or socket failure rejects the test. One controlled loopback test (even with an explicit denial) is **not** proof of comprehensive Internet/network confinement.
3. The Windows CI harness computes **SHA-256** for its fixed compiled test executable, confirms the same hash immediately before and after its run, and verifies an **intentionally tampered copy has a different digest** without executing that copy. `Get-AuthenticodeSignature` obtains the actual fixture signature status; there are **no private signing keys**, and no change is made to the trust stores. A checksum computed in the same build/test job can detect simple mutations, but **cannot attest independent developer identity or a trusted signed release**. The executable is not automatically authorized because its hash matches.
4. New `tools/security/stage46_worker_trust_evidence.py` ingests a bounded JSON receipt of non-sensitive test markers, file hash evidence, signing status and observed network outcome. It strictly validates the schema, positive control, AppContainer token, synthetic file refusal, Job limit evidence, STOP/kill, and output-marker consistency. Its **only release decision is NO_GO**. It separates `EXPLICIT_DENY_SINGLE_LOOPBACK` from `TIMEOUT_INCONCLUSIVE`, and reports unsigned/unknown-publisher fixtures as untrusted. It has **no command execution** path, no production approval API and no real audio devices.
5. New adversarial Python unit tests reject forged release flags, missing/duplicate markers, inconsistent hashes, synthetic tampering, contradictory network markers, child crash, unsupported schema versions and unverified signer claims. Even a hypothetical valid signature or explicit single-loopback denial remains production **NO_GO** until an independently reviewed signer, worker artifact, whole-environment least privilege and 13 existing blockers are addressed.

## Native and cross-platform CI

```powershell
pwsh -NoProfile -NonInteractive -File .\workstation-agent\packaging\stage46\Test-SyntraWindowsAppContainerLab.ps1
```

```bash
python3 -m unittest discover -s tools/security -p test_stage46_worker_trust_evidence.py -v
python3 -m unittest discover -s tools/security -p test_stage45_pre_pilot_gate.py -v
./mvnw -B -f orchestrator-service/pom.xml test
./mvnw -B -f workstation-agent/pom.xml test
```

The new exact-HEAD workflow checks the native Windows AppContainer fixture and hash receipt, existing Windows reduced-token and Job Object lifecycle tests, Linux synthetic voice/STOP/owner authorization tests, Stage 45 immutable 13-blocker policy, full orchestrator and workstation tests, and a conservative no-device/source-policy check.

## Honest scope and required future work

- **Not a signed worker:** The test fixture is locally compiled on GitHub Actions and hash-checked. It is not pinned to an independently approved publisher or signed with a production certificate. Authenticode's `Valid` alone would be insufficient without trusted publisher-chain, expected certificate, verified binary provenance and anti-tamper/runtime-launch controls.
- **Not a certified outbound firewall:** A timeout—even after confirming the listener works—is not a kernel-reported WFP denial. The AppContainer zero-capability token and one loopback listener do not prove every egress path is blocked. A production candidate needs measured Windows Filtering Platform policy at suitable layers, app identity scoping, least-privilege service confinement, supported audit controls, coverage of IPv4/IPv6/DNS/process inheritance, and reliable denial observations. No host firewall settings are changed by this lab.
- **Not a production speech host:** No real microphone, hardware audio capture, downloaded STT/TTS models, AI-generated commands, local owner validation, independent STOP supervisor, production signing, install/update flow or owner-PC test. The native fixture remains separate from all Java voice adapters.
- **Not a release:** CI success confirms the **synthetic test assertions only**, never deployment or production approval. Keep all earlier staged PRs open and unmerged; keep all **13 Stage 45 release blockers** active and Windows-PC pilot **NO_GO**.

External technical references: Microsoft [UWP/AppContainer loopback guidance](https://learn.microsoft.com/en-us/windows/security/operating-system-security/network-security/windows-firewall/troubleshooting-uwp-firewall) and [Get-AuthenticodeSignature](https://learn.microsoft.com/en-us/powershell/module/microsoft.powershell.security/get-authenticodesignature).

**No owner machine modifications or actual audio permissions are authorized.**

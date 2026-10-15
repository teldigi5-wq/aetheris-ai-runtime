# Stage 46.18 — Exact synthetic WFP correlation contract and same-run dual-build verification

**OPEN DRAFT / UNMERGED. SYNTHETIC TESTS ONLY. NO ACTUAL WFP EVENT ATTRIBUTION OR SIGNED RELEASE. PRODUCTION AND PHYSICAL OWNER-PC PILOT: NO_GO.**

## Scope

Stage 46.17 confirmed that historical Windows Filtering Platform `CLASSIFY_DROP` events existed on a hosted CI runner but **could not attribute those events to the Syntra synthetic process**. Prior release specifications correctly recorded no trusted worker signing certificate or independently approved binary manifest. This stage addresses **the code quality of future evidence** and **build reproducibility as a test**, without calling hypothetical evidence real.

### 1. Synthetic-only per-attempt WFP matching contract

`tools/security/stage46_wfp_synthetic_match_contract.py` takes an explicitly marked **SYNTHETIC_NO_REAL_WFP_DATA** fixture with a caller-specified attempt and no more than 16 fabricated `CLASSIFY_DROP` events. A fixture match requires **all** of the following to agree:

- A complete 64-character lowercase SHA-256 representation of the hypothetical AppContainer application's identity (no real app ID or user path).
- **Exact** canonical loopback destination address (`127.0.0.1` or `::1`), protocol TCP, outbound direction, IP version, and ephemeral destination port.
- A strictly bounded, inclusive synthetic relative-time window between 1 and 5,000 ms.
- Exact synthetic **WFP filter ID** and **layer ID**; positive integers with explicit bounds.
- Fixed `CLASSIFY_DROP` type, **SYNTHETIC_FIXTURE_ONLY** source, no duplicate event IDs, and a declared working positive-control listener.

A missing field, type confusion, false kernel-attestation marker, non-loopback destination, ambiguous multiple matching records, duplicate source, invented privacy data, arbitrary event type, or extra release permission fails validation. The allowed outputs are `EXACTLY_ONE_SYNTHETIC_MATCH`, `NO_MATCH`, `AMBIGUOUS_MULTIPLE_MATCHES`, or `REJECTED`. **Even when the synthetic record matches exactly, `real_app_specific_wfp_drop_verified` and `network_egress_certified` remain false and the release verdict remains NO_GO.** No actual Windows WFP traffic or events are imported, captured, or trusted.

### 2. Actual two-build byte consistency check on hosted Windows CI

A separate `WindowsSyntheticReproducibleWorkerProbe.cpp` is a tiny, **inert executable** with exactly one `--self-test` switch that prints a fixed synthetic marker; it does not use a microphone, network, filesystem, models or desktop commands.

`Test-SyntraSyntheticDualBuild.ps1` invokes the same installed Visual Studio 2022 MSVC toolchain on the same hosted Windows runner **twice**, with separate `/Fo` object and `/Fe` executable destinations, explicit `/Brepro` compile and link settings, `/INCREMENTAL:NO` and no debug/PDB paths. It compares SHA-256 digests of both executable outputs, invokes only the first inert self-test, and confirms a **never-executed altered copy** produces a different digest. Windows Authenticode status is checked; the expected test fixture status is `NotSigned`. The test fails on any mismatch, compiler/self-test failure, or unexpectedly signed fixture.

The restricted JSON receipt is evaluated by `tools/security/stage46_synthetic_dual_build_gate.py`. A valid result establishes **same-source, same-toolchain, same-runner byte-for-byte identity** only, **NOT** an independent second-party reproducible build, trusted publisher, secure launch handle, signed dependency manifest, or approved release artifact. There are still **no production signing credentials, public release artifacts or pinned publisher certificate trust roots**.

### 3. Evidence and regression

Adversarial Python tests exercise identity, port/IP version and destination, time window, WFP filter/layer, duplicate/extra markers, fake kernel source, forged network release, differing executable hashes, unsigned-publisher confusion and tampering. The dedicated exact-HEAD GitHub Actions workflow also reruns previously verified synthetic voice STOP/process tests, real Windows Job Object/reduced-token/AppContainer **lab** checks, the read-only WFP audit, complete orchestrator/workstation JUnit regressions and Stage 45's immutable **13 production NO_GO blockers**.

To reproduce the synthetic contract in a checked-out repository:

```bash
python3 -m unittest discover -s tools/security -p test_stage46_wfp_synthetic_match_contract.py -v
python3 -m unittest discover -s tools/security -p test_stage46_synthetic_dual_build_gate.py -v
```

On a **disposable GitHub-hosted Windows CI runner** with Visual Studio 2022 and PowerShell 7:

```powershell
pwsh -NoProfile -NonInteractive -File .\workstation-agent\packaging\stage46\Test-SyntraSyntheticDualBuild.ps1
```

Existing Java and Stage 45 policy commands remain unchanged.

## Remaining security blockers

Real WFP correlation will require actual **authenticated** kernel event provenance with the correct process/app identity, WFP layer/filter, time and destination, a live positive control, complete IPv4/IPv6/DNS coverage and independent safeguards. The historical Stage 46.17 snapshot is **unrelated/unattributed** and cannot satisfy those criteria. A real production worker will separately require source/build attestations from **independent builders**, signed immutable binaries with an approved certificate chain/revocation policy, verified dependency and speech-model licenses, and anti-TOCTOU process startup controls.

No network-deny policy, firewall filter, event subscription or debug mode is enabled here; the user machine is not touched. Real microphone, speech models, owner permissions, OS autonomy, deployment, physical Windows PC pilot, and production release remain **NO_GO** with the same **13 release blockers**. Keep this and all preceding stacked pull requests **OPEN DRAFT and UNMERGED**.

Engineering reference for deterministic MSVC linker behavior: <https://blog.llvm.org/2019/11/deterministic-builds-with-clang-and-lld.html>. The relevant MSVC `/Brepro` flag is currently undocumented, so this CI proves behavior only on the actual toolchain used by the run rather than promising it universally.

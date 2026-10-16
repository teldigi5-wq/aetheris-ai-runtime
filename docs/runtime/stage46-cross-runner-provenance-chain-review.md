# Stage 46.19 — Cross-runner artifact provenance and live draft-chain review

**OPEN DRAFT / UNMERGED / SYNTHETIC ONLY / RELEASE AND OWNER-PC PILOT: NO_GO.**

## Objective and scope

This stage strengthens evidence without extending Syntra's authority or activating a real speech engine. It follows Stage 46.18's identical two MSVC builds on **one** Windows runner, Stage 46.17's uncorrelated historical WFP `CLASSIFY_DROP` snapshot, and the staged review branches. No microphone, model, OS command, real network traffic capture, firewall rule, installation, signed release or production runtime is added.

### Real read-only PR-chain review — 16 October 2026

Direct GitHub inspection confirmed PRs **#72 through #84 inclusive (13 PRs)**, stages **46.6–46.18**, remain **OPEN DRAFT and UNMERGED**. Each PR #73–#84 targets exactly the preceding PR's branch and records a matching predecessor HEAD SHA. PR #72 targets `feature/stage46-synthetic-offline-speech-gate-20261015`, a preexisting predecessor outside the 13-PR manifest.

All 13 recorded HEAD commits also had **completed successful GitHub Actions workflow runs** when inspected:

| PR | Stage | Verified workflow run |
|---|---|---|
| #72 | 46.6 | 38690881839 |
| #73 | 46.7 | 38692703414 |
| #74 | 46.8 | 38693540562 |
| #75 | 46.9 | 38695031638 |
| #76 | 46.10 | 38696574296 |
| #77 | 46.11 | 38697782300 |
| #78 | 46.12 | 38699266019 |
| #79 | 46.13 | 38701239733 |
| #80 | 46.14 | 38704732592 |
| #81 | 46.15 | 38708044828 |
| #82 | 46.16 | 38709547572 |
| #83 | 46.17 | 38711351143 |
| #84 | 46.18 | 38715278616 |

These are **prior passing CI runs, not release approvals or current production deployment**. No main-branch merge or hardware certification is implied.

`docs/runtime/stage46-draft-chain-integrity-manifest.json` records the complete observed base/head refs and full commit hashes for the chain. `tools/security/stage46_pr_chain_audit.py` performs a **live read-only GitHub REST GET** for each PR on every Stage 46.19 review run, verifies its state, draft flag, merge status, exact head/base refs and SHA linkage, and requires the current PR to target precisely the last recorded branch and SHA. The workflow token requests **pull-requests:read + contents:read**, never write permissions, and the script never logs tokens or complete server responses.

Any retargeted, merged, non-draft, missing or updated predecessor PR causes **failure**, requiring explicit manifest review and a refreshed audit. The manifest is only a reviewed snapshot; it cannot approve a release or serve as GitHub's source of truth. This is an intentional conservative review gate; normal future updates to earlier branches require refreshing the manifest after human review.

### Two distinct hosted Windows runners

The new `Build-SyntraSyntheticIndependentRunner.ps1` builds the **same inert synthetic C++ fixture**, from the exact PR commit, on **two distinct `windows-2022` GitHub-hosted jobs**, rather than merely twice within one runner. Each builder uses fixed MSVC compile/link flags (`/Brepro`, `/INCREMENTAL:NO`, no debug output), checks the single inert `--self-test`, verifies SHA-256 remains stable during execution, confirms `Get-AuthenticodeSignature` reports `NotSigned`, and exposes **only the source and executable hashes** as job outputs. No executable or artifact is uploaded or distributed.

A third, Linux-hosted job compares both builders' source SHA-256, executable SHA-256, exact checkout commit, fixed runner roles and unsigned status through `tools/security/stage46_cross_runner_build_gate.py`. Different hashes, forged approvals, malformed data or unavailable results are rejected. This establishes **two GitHub-hosted Windows runner outputs**, not independent **trust domains**: the jobs share GitHub, MSVC, the same repository checkout, CI identity and build configuration. It is **not** a SLSA provenance attestation, code signing, an audited release package, independent builder verification, or TOCTOU-safe launch. No publisher trust roots are enrolled.

### WFP attribution — controlled specification only

Stage 46.18's WFP event matcher remains exclusively `SYNTHETIC_NO_REAL_WFP_DATA`; this stage **does not import live WFP event bodies or modify network policy**. Before any future process-specific network-denial proof can be claimed, a separately authorized protocol must meet all of these:

1. **Identity:** Independently establish the OS AppContainer SID/token and link the exact fixed executable instance to a trusted app ID, pid/process start time and hash without allowing process-path spoofing. Avoid logs of real user identities.
2. **Reproducible isolated attempt:** Use only a disposable synthetic process and ephemeral localhost listener, demonstrate a successful positive-control TCP connection, then record exact source/destination IP version, address/port, TCP direction and bounded time window. IPv4 and IPv6 must be tested separately, with no real Internet destinations.
3. **Authenticated kernel evidence:** Obtain an attributable WFP `CLASSIFY_DROP` record with verified matching app ID, layer/filter ID and timestamp, distinguish a kernel denial from timeout/no logging, and require exactly one unambiguous match. A historical global drop snapshot cannot satisfy this.
4. **Least-privilege and reversibility:** Independently approve any future audit-subscription/enforcement configuration and its exact ACL/policy scope. Verify cleanup and rollback. **Not permitted in this stage.**
5. **Scope limitations:** A single synthetic loopback result cannot certify DNS, IPv6, external network traffic, process descendants, alternate socket APIs or complete production egress denial. Do not grant real device/desktop authority from network evidence.
6. **Independent review:** The sandbox design, source/build attestations, signed worker distribution, revocation, minimum OS permissions, mic/user consent and unresolved **13 Stage45 blockers** must be reviewed independently before a physical Windows owner-PC pilot or production sign-off.

### Security invariants, reproduction and review risks

- **No merged branches.** The 13-PR chain increases review/rebase complexity; staging must remain explicit and carefully validated. Passing all staged PRs individually does not substitute for a tested merged integration candidate.
- **Read-only GitHub only:** The auditing code makes fixed endpoint HTTPS GETs, holds an ephemeral **read-only** GitHub token, does not access general user data, and discloses only numbers/state in its synthetic validation reports.
- **No production authority:** The new evaluator reports `decision=NO_GO`, `production_authorized=false`, `owner_pc_pilot_authorized=false` and `separate_trust_domains_verified=false` even when the two output hashes are identical.
- **No real audio:** Stages 46.7–46.18 remain synthetic/read-only; no actual microphones, speech weights, live VAD, owner voice identification, real desktop OS actions or installed production service exists.
- **No firewall modification:** Stage 46.15's loopback attempt timed out, Stage46.16 read only WFP layer metadata, and Stage46.17's historic drop events were uncorrelated. **Network egress-denial certification remains UNVERIFIED.**

Run the new tests from the repository checkout:

```bash
python3 -m unittest discover -s tools/security -p test_stage46_pr_chain_audit.py -v
python3 -m unittest discover -s tools/security -p test_stage46_cross_runner_build_gate.py -v
```

The live PR audit requires the CI's read-only GitHub token and the current PR base/ref SHA; `Build-SyntraSyntheticIndependentRunner.ps1` is designed only for a GitHub-hosted Windows runner and never installs its compiled test binary.

**Production, real microphone, physical Windows-PC pilot and autonomous OS actions remain NO_GO. All 13 Stage45 blockers remain active. No firewall settings, owner machine, or production deployment are modified.**

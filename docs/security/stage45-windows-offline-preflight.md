# Stage 45.5.17 — Windows/WSL offline workstation readiness gate

**STATUS: OPEN DRAFT / READ-ONLY GATE / DO NOT INSTALL OR ACTIVATE.**

This stage extends the reviewed-but-unmerged Stage 45.5.16 PR #63.
The existing `workstation-agent/packaging/install.ps1` is not called,
modified or certified in this stage. It currently has actions that stop
previous processes, handle DPAPI credentials, create a startup shortcut
and launch the agent. Those actions require **independent manual Windows
device review and owner approval before use**.

## Added workflow

New **separate** scripts under
`workstation-agent/packaging/stage45/`:

- `OfflineReadinessPolicy.psm1`: **pure, non-installing** review-policy
  functions. Require a fixed-schema 15-field review manifest, a complete
  artifact size match, SHA-256 equality with a separately supplied
  **owner-trusted expected digest**, a limited list of existing narrow
  workspaces, and no symlink/reparse ancestors. Reject filesystem/drive
  root and whole user-profile scopes. Unknown manifest fields,
  invalid booleans, enabled autostart, network, shell, desktop actions,
  WSL execution, production activation or unverified-signature claims
  are **denied**.
- `Invoke-OfflineReadiness.ps1`: **read-only** local Windows review,
  PowerShell 7+, Java 21+, current interactive user and integrity
  evidence. Produces a tightly bounded JSON readiness report. No
  password, API key, signing key, owner auth or network request is
  accepted or generated. It never calls the existing installer.
- `New-ReviewBundleCiOnly.ps1`: exclusively on GitHub Actions, copies
  the already-built, previously tested shaded workstation JAR into a
  fresh directory **under RUNNER_TEMP**, creates a review-only JSON
  manifest and marks production/signature approval FALSE. Never
  installs or runs the agent. The manifest SHA computed in CI is
  **local consistency only**, not an independent release signature.
- `Test-OfflineReadiness.ps1`: disposable hosted-runner adversarial
  tests for tampered JAR, wrong/missing out-of-band SHA, activation and
  shell/desktop policy bypass attempts, unsafe manifest assertions,
  missing workspace, filesystem-root grant, reparse/symlink directory,
  and CI bypass denial outside the exact trusted CI environment.

## What CI actually certifies

On disposable **Linux** GitHub Actions only, three exact-HEAD jobs:

1. Parse the PowerShell scripts and run their negative policy tests.
2. Build/test the **existing** Java workstation-agent JAR, create an
   ephemeral CI-only review bundle and check its non-activation result.
3. Run the complete Java orchestrator regression.

No Windows runner, user laptop, installed Java/DPAPI profile, CUDA/
RTX GPU, microphone, Windows Hello, WSL guest, app launch, voice agent,
Windows startup shortcut or actual Windows service is touched.
CI synthetic output **explicitly says**
`CI_SYNTHETIC_NOT_WINDOWS_CERTIFIED`. Windows real-device readiness
can only be claimed after a separately approved owner-run read-only
preflight and independently verified release artifact.

## Manual workstation review (NOT permission to install)

When a future signed artifact and **independently trusted SHA-256** are
available, a Windows owner can inspect the scripts first. Only then
may an owner manually run the **read-only** PowerShell 7 preflight with
a specific existing project folder, for example:

```powershell
pwsh -NoProfile -File .\workstation-agent\packaging\stage45\Invoke-OfflineReadiness.ps1 `
  -BundleDirectory 'D:\Aetheris-Review-Bundle' `
  -ExpectedSha256 '<SHA256_FROM_INDEPENDENTLY_VERIFIED_RELEASE>' `
  -WorkspaceRoots 'D:\Aetheris-Workspace'
```

The result still sets `installed=false`,
`activated=false`, `releaseAuthorized=false`, and
`startupShortcutCreated=false`. A positive report only means the
**limited reviewed inputs** satisfy the conservative review policy.
It does NOT authorize running `install.ps1`. No secret needs to be
entered. The `-SyntheticCI` option is unusable without both exact
GitHub Actions CI environment guards.

## Explicit security and operational gaps

- SHA-256 proves equality to the externally supplied value, not who
  built the JAR. The existing Stage 11 package has no verified
  Authenticode/detached publisher signature or reproducible owner
  trust chain. A CI-generated manifest is NOT a trusted release.
- No current-user Windows ACL audit, privileged service hardening,
  code-signing enforcement, signed trusted boot, WSL distribution
  isolation, Windows Defender/SmartScreen behavior or DPAPI key
  migration testing has been performed.
- The existing host agent has an allowlisted `app.launch` capability.
  This stage does not independently prove safe real applications,
  prompt injection resistance in active desktop workflows, or
  actual process-tree termination. The preflight's
  `desktopActionsAllowed=false` refers ONLY to this review profile;
  it does NOT disable capabilities if someone installs/starts the
  older agent outside this review gate.
- The earlier multi-agent quota, signed STOP witness and OCI broker
  are CI labs, not a fully integrated host security stack; none
  of them is wired into this workstation install path.
- There is **no offline model download** or WSL installation.
  Offline-first means no network is required for REVIEW, not that
  Syntra can already run arbitrary local AI models without Internet.
- Staging plus passing tests does not certify full antivirus,
  endpoint-management, telemetry minimization or owner consent.
  Security testing must include malicious archive/package paths,
  Windows native symlinks/junctions, DLL search paths, runtime app
  allowlist, startup recovery, upgrade rollback, unauthorized
  signed-command submission and actual emergency termination.

## Planned next stages

Stage 45.5.18 is a safe **voice/wake-word/desktop-action consent**
prototype (no real OS control without explicit owner permission and
supervisor integration). Stage 45.5.19 is independent end-to-end
review, attack testing, offline recovery and pre-pilot gate.

No deployment, WSL/Windows installation, protected merge, production
data/keys or autonomous agent command enablement is permitted from
this PR. Keep all Stage 43–45 PRs **OPEN DRAFT**.

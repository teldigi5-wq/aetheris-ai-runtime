# Stage 46.13 — Windows reduced-privilege synthetic child identity lab

**OPEN DRAFT — TEST LAB ONLY — NO ACTUAL MICROPHONE, STT/TTS MODEL, APP EXECUTION, OWNER-PC PILOT OR PRODUCTION RELEASE.**

This stage builds upon the Stage 46.12 real Windows Job Object test host. Stage 46.12 proved kernel resource limits and kill-on-job-close on a fixed inert Java test child, but the child inherited the runner's ordinary user security context. Stage 46.13 adds an **explicit CI-only privilege-reduced token launch path**, preserving the original portable Job Object path and all prior synthetic voice/STOP/regression tests.

## Implementation

`WindowsSyntheticJobLab.RunPrivilegeReduced` uses native Windows APIs to:

1. Query the current process's Windows primary token with `OpenProcessToken` and measure its enabled privileges via `GetTokenInformation(TokenPrivileges)`.
2. Create a reduced token using `CreateRestrictedToken` with `DISABLE_MAX_PRIVILEGE | LUA_TOKEN`. Check the enabled privilege count before using it. This is a **test for privilege reduction**, not independent least-privilege sandboxing.
3. Create the **fixed inert Java synthetic probe** using `CreateProcessAsUserW` with that token and `CREATE_SUSPENDED`, a minimal synthetic environment, no inherited handles, and an exact fixed command. If and only if Windows reports missing launch privilege **1314**, try `CreateProcessWithTokenW` using the **same reduced token**, with the same suspended launch and fixed executable. There is **no fallback** to unrestricted `CreateProcessW`; other failures remain fail-closed.
4. Query the newly created child's process token to confirm it has at most one enabled privilege and has not acquired additional enabled privileges relative to the parent.
5. Assign the still-suspended child to the existing Windows Job Object before resuming. Retain the Stage 46.12 verified resource policy: one active job process, 512 MiB per-process/job committed-memory ceilings, 50% CPU cap, kill-on-job-close and bounded wait.
6. On error, close the job to kill members and explicitly terminate any never-assigned suspended child. Dispose of primary token, reduced token, child process/thread, and synthetic environment allocation.

### CI evidence requirements

The dedicated `windows-2022` test runs fixed, inert child modes under the reduced token: successful exit, explicit test-environment secrecy check, and timeout-terminated stall. It rejects unrecognized modes and preserves Stage 46.12's separate baseline Job Object regression. Linux jobs repeat the previously verified 108 targeted synthetic voice/STOP/worker tests, full orchestrator and workstation agent regression, and all 13 Stage 45 production blockers.

**No CI result should be claimed until this exact-HEAD Windows job actually passes.** Windows CI is not testing the owner's hardware.

## Material security limits

- **Reducing token privileges is not AppContainer confinement.** This stage does **not** enforce network egress denial, special filesystem DACLs, registry isolation, restricted SIDs for every sensitive resource, a separate restricted OS user account, a private desktop, or sandboxed Java classpath.
- `CreateRestrictedToken(DISABLE_MAX_PRIVILEGE | LUA_TOKEN)` does not prove the process lacks the user's ordinary file or network permissions. A child may still access resources the resulting token's SIDs allow. A strict package identity and explicit zero network capabilities would require a separately tested AppContainer or equivalent solution.
- A token privilege audit alone cannot protect against compromised parent process, tampered JDK binaries, unwanted process injection, or malicious native libraries. Full production readiness requires signed binaries, auditable trusted code paths, independently administered OS policy, per-worker filesystem/network allowlists, crash recovery and tests on owner-approved hardware.
- Windows Job Object limits only constrain process/job resource consumption. A 512 MiB commit cap and 50% CPU policy do not guarantee zero native memory leakage, perfect latency, or comprehensive security isolation.
- No real microphone listening, background recording, STT/TTS, acoustic speaker identification, owner authentication, agent commands, OS automation, cloud deployment, installation, model download, merge or hardware pilot takes place.

## Reproduction (GitHub-hosted Windows runner)

```powershell
.\mvnw.cmd -B -f orchestrator-service/pom.xml -DskipTests compile
pwsh -NoProfile -NonInteractive -File .\workstation-agent\packaging\stage46\Test-SyntraWindowsReducedTokenLab.ps1 -ClassesPath .\orchestrator-service\target\classes
pwsh -NoProfile -NonInteractive -File .\workstation-agent\packaging\stage46\Test-SyntraWindowsJobLab.ps1 -ClassesPath .\orchestrator-service\target\classes
```

Keep this lab separate from production paths. **Stage 45 production authorization stays NO_GO with the same 13 release blockers.**

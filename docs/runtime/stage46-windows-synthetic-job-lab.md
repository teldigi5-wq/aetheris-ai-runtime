# Stage 46.12 — Windows synthetic speech worker Job Object containment lab

**OPEN DRAFT / INERT SYNTHETIC CHILD ONLY / NOT A PRODUCTION ENGINE / PHYSICAL WINDOWS-PC PILOT: NO_GO.**

This change adds a deliberately separate, manually invoked Windows CI lab that exercises genuine **Windows Job Object kernel process policies** on a fixed, inert Java 21 fixture. It does **not** change the default Stage 46.10/46.11 runtime path, install a service, integrate a real microphone, download speech weights, or expose another endpoint. It does not alter any release permissions.

## Implementation and scope

`SyntraSyntheticJobProbeMain` is a fixed child class, not an STT, VAD, TTS, AI assistant, or OS action dispatcher. Its only hard-coded lab behaviors are: normal clean exit, intentional nonzero exit, an inert timed stall, a descendant attempt to execute **the same fixed Java fixture**, and a child environment-isolation assertion.

`workstation-agent/packaging/stage46/WindowsSyntheticJobLab.cs` is compiled by an explicit PowerShell CI command and uses Windows kernel32 APIs. In contrast to creating an already running process and trying to assign it later, the lab calls `CreateProcessW(CREATE_SUSPENDED)`, assigns the suspended child to the Job Object, and only then calls `ResumeThread`. If Job configuration or assignment fails, it refuses to resume the child and explicitly terminates an unassigned suspended child. No custom Windows audio devices are opened.

The Job Object policy includes:

| Policy | Configured synthetic lab setting |
|---|---|
| Close behavior | `JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE` |
| Active processes in job | 1 |
| Per-process committed memory | 512 MiB |
| Total job committed memory | 512 MiB |
| CPU consumption | 50% Job Object hard cap |
| JVM managed heap | 64 MiB |
| Test child runtime | 100–5,000 ms wait, then close Job Object |
| Environment | Fresh minimal `SystemRoot` / `WINDIR` block; no inherited application tokens |
| Handles | `bInheritHandles=false` |
| Invocation | Absolute known `java.exe`, precompiled fixed fixture and enumerated modes only |

Before child launch the test **reads the Job Object limits back through the Windows kernel API**, not just from the code that requested them. After completion/timeout, the Job Object handle is closed. The tests require process exit confirmation, test the descendant-denial path under a one-process Job, verify that synthetic placeholder API-token environment variables are not passed to the child, and reject unrecognized modes.

The dedicated `windows-2022` CI job compiles `SyntraSyntheticJobProbeMain`, compiles the C# interop helper through `Add-Type`, runs `Test-SyntraWindowsJobLab.ps1`, and fails on Job Object setup/verification or lifecycle errors. Separate Linux jobs preserve every previously implemented synthetic voice and subprocess security test, the 13-blocker Stage 45 production `NO_GO` verdict, and full Java/workstation regression. No real voice audio is sampled.

## Reproduction

From the repository root on a Windows test host with Java 21 and PowerShell 7:

```powershell
.\mvnw.cmd -B -f orchestrator-service/pom.xml -DskipTests compile
pwsh -NoProfile -NonInteractive -File .\workstation-agent\packaging\stage46\Test-SyntraWindowsJobLab.ps1 -ClassesPath .\orchestrator-service\target\classes
```

**Do not run on a production or real user workstation as part of this review.** The GitHub-hosted Windows runner is used for the lab.

## Security limitations and future steps

- This is **kernel-enforced Job Object containment of a fixed child**, not full least-privilege sandboxing. It does not create a restricted user token, AppContainer, filesystem confinement, network denial, signed worker executable, verified Java classpath provenance, low-integrity token, or trust boundary independent of the parent user. A child can potentially access resources allowed to that OS identity. Hardware microphone permissions remain unverified.
- The limits apply to job members; this is not a general system quota or host-level isolation. `512 MiB` is committed-memory policy, not a verified acoustic-model memory requirement. The 50% CPU cap is not an end-to-end latency guarantee.
- Windows process-start and kill operations are not real-time guarantees. This lab does not expose a production independent STOP supervisor; it demonstrates kernel kill-on-close and timeout in a local CI harness.
- Source-code static checks are defense in depth, not proof against malicious Java classpath content or a compromised build runner. Real deployment requires a signed, minimal worker artifact, independent OS identity and permission boundaries, filesystem/network restrictions, audit, privilege reductions, robust parent crash handling, and owner-authorized Windows testing.
- The previous Stage 46.8 synthetic read-only conversation and Stage 46.10 synthetic PCM worker remain separate. This lab does **not** pretend that the Windows Job fixture performs transcription or that real microphone audio is captured.

**Do not merge, deploy, install on the owner's PC, enable microphones, load real models, execute OS commands, or change Stage 45 production policy.** All 13 release blockers remain active. Production and physical owner-PC piloting remain **NO_GO**.

# Stage 46.14 — Zero-capability Windows AppContainer security lab (synthetic only)

**OPEN DRAFT / RUN ONLY IN WINDOWS GITHUB-HOSTED CI / NO USER PC, REAL SPEECH, AI AUTONOMY OR PRODUCTION RELEASE.**

## Scope

A disposable **native Windows test executable**, `WindowsSyntheticAppContainerLab.cpp`, performs independently testable kernel-mediated security experiments. It is unrelated to Syntra's active Java voice adapters; it cannot read a microphone or speech transcript, load models, accept arbitrary commands, install a service or authorize OS automation. It deliberately does not extend the existing Stage 46.12/46.13 runtime. The experiment is meant to determine whether Windows AppContainer identity and no network capabilities can provide additional separation beyond Job Object resource quotas and the reduced-privilege process token.

Microsoft's documented Win32 AppContainer launch flow uses a package SID in a `SECURITY_CAPABILITIES` process attribute passed to `CreateProcess` with `EXTENDED_STARTUPINFO_PRESENT`. The lab follows this structure; it is not an independent proof of sandbox security beyond observed tests.

## Explicit tests

1. Create a uniquely named **disposable** Windows AppContainer profile using `CreateAppContainerProfile`; delete it after all children exit, including on normal test failure. No persistent app, service or deployment is created. The fixture executable is temporarily granted read/execute access to that AppContainer SID to permit this test; **never grant access to private user files**.
2. Launch the exact same small statically linked native fixture under an AppContainer SID using `STARTUPINFOEXW`, `PROC_THREAD_ATTRIBUTE_SECURITY_CAPABILITIES`, and **zero requested capabilities** (no internetClient, privateNetworkClientServer or shared capabilities). Probe the child's real Windows `TokenIsAppContainer` attribute. Launch is suspended; assign the child to a Job Object before resuming.
3. Create a clearly marked synthetic sentinel file and add an **explicit deny ACE** for this specific AppContainer SID. Probe a read and require `ERROR_ACCESS_DENIED`. This proves the file ACL denies the fixture's SID; it does **not** prove every unmodified owner file is inaccessible by default.
4. Open an ephemeral TCP **loopback-only** listener on the GitHub Windows runner. Test whether the zero-capability AppContainer child is denied connecting to that listener (`WSAEACCES` or `WSAENETUNREACH`). **No external hosts or real web requests.** A successful connection or an ambiguous connection error fails the test instead of claiming network confinement.
5. Test stall termination through `JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE` with a separate child; verify termination. Reuse Stage 46.12's one-process, 512 MiB process/job committed-memory caps, and 50% CPU cap. Validate kernel Job Object limits through `QueryInformationJobObject`.
6. Close every process/thread/Job handle, delete only the synthetic test file, free SID attributes and delete the temporary AppContainer profile. Any failure keeps the test `NO_GO`.

The dedicated native lab is compiled with **Visual Studio 2022 MSVC /MT** by an explicit `windows-2022` CI task and not distributed as an installable artifact. Additional jobs rerun Windows Stage 46.12/46.13 native policy, Linux synthetic voice regression, full Java/workstation regression and unchanged Stage 45 13-blocker production-policy checks.

## Limits / non-certification

AppContainer can restrict access to resources subject to applicable ACLs/capabilities; it is not a guarantee of zero filesystem or network access in arbitrary environments. The ACL fixture includes an intentional explicit deny rule. The loopback check applies only to one test listener and capability set. It does not prove Internet isolation, complete application privacy, native DLL supply chain, device access restrictions, OS security configuration, or all network types. The host parent process still runs with its own permissions; it must be trusted.

The lab does not run a Java STT engine in AppContainer, perform real offline speech recognition, establish secure source attestation, authorize the owner, connect to models, or verify Windows microphone consent. Production requires a signed minimal worker, least-privilege service identity, audited filesystem/registry/network grants, lifetime-based revocation, OS-specific resources, robust denial tests on approved hardware, third-party licenses, and actual integration work.

**Production release and owner-PC pilot are strictly NO_GO. All 13 existing Stage 45 release blockers remain and all previous draft PRs must stay open/unmerged.**

## Reproduce ONLY on a disposable Windows test runner

```powershell
pwsh -NoProfile -NonInteractive -File .\workstation-agent\packaging\stage46\Test-SyntraWindowsAppContainerLab.ps1
```

Results are verified only after the matching exact-HEAD GitHub Actions run succeeds. Never reinterpret source code or an unverified staged PR as a deployed or supported Windows sandbox.

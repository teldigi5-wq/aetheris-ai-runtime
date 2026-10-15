# Stage 46.11 — Synthetic speech host-containment lab

**OPEN DRAFT / SYNTHETIC DATA ONLY / PRODUCTION AND OWNER WINDOWS-PC PILOT: NO_GO.**

This increment preserves the existing Stage 46.10 subprocess and safe `VOICE` / `SILENCE` binary protocol. It introduces an explicitly opt-in **Linux resource-limits lab**, plus a separate **Windows process-portability smoke test**, without enabling physical microphones, speakers, models, AI tools or autonomous desktop execution.

## Engineering changes

`SyntraSyntheticHostLimits` defines two profiles:

1. **PORTABLE_SYNTHETIC_ONLY** — existing Stage 46.10 behavior, unchanged by default. The launched fixed child JVM is not least-privilege sandboxed.
2. **LINUX_RLIMIT_LAB** — test-only factory for the exact existing synthetic worker. It requires Linux with a regular, non-symlink, executable `/usr/bin/prlimit`. If unavailable or on Windows, it **fails closed without falling back**. No shell is invoked; a fixed command prefixes the fixed JVM executable with `/usr/bin/prlimit --cpu=5 --as=4294967296 --nofile=128 --`.

Linux resource constraints: 5 seconds of CPU-time per process, 4 GiB virtual-address reservation ceiling, and 128 open file descriptors. These are **Linux RLIMITs**, not cgroups, filesystem or network sandboxing, real process RSS guarantees or total process-tree quotas. The JVM retains its separate `-Xmx64m` managed-heap cap. The 4 GiB address limit was selected because Java 21 reserves substantial virtual address space, even when the actual managed heap is small. Lower `RLIMIT_AS` values can prevent the JVM from initializing; it is not a claim that resident RAM can reach 4 GiB safely. Worker wall time still uses the original 100–5,000 ms bounded parent wait, with forced termination and irreversible local STOP.

**Linux test evidence:** a real stalled subprocess launched with the lab profile exposes its actual `/proc/<pid>/limits`. A test verifies the CPU, address-space and file-descriptor limits from that kernel-generated file, then triggers STOP and confirms process exit. Other tests verify bounded real child execution, worker timeout, policy validation, opt-in and unsupported-host denial.

**Windows test evidence:** a separate GitHub-hosted `windows-2022` job verifies baseline synthetic Java child process startup, `VOICE`/`SILENCE` responses, forced termination of a stalled child, and explicit denial of the Linux-only resource lab. These tests do not activate any Windows audio device, configure a service, or prove Windows Job Object containment.

## Precise boundaries and remaining gaps

- **This is not a production sandbox.** Both profiles run under the current OS identity and retain inherited user privileges. The Linux profile enforces three kernel RLIMIT values only; it does not provide user namespaces, seccomp, network denial, filesystem confinement, process-tree resource quotas or cgroup membership.
- The Windows portable smoke job has **no Windows Job Object memory, CPU, active-process or kill-on-job-close policy** and cannot be called containment certification.
- A fixed `java.home` child executable and classpath are assumed to be trusted in this test harness; no signed release binary verification or independently vetted voice-engine license review is added.
- Only caller-provided fake PCM is processed. There is no microphone, live speech-model inference, owner identification, authenticated approval, network processing, text command execution, TTS playback, real acoustic VAD or OS automation.
- The existing 13 Stage 45 security release blockers remain binding. GitHub-hosted Windows CI is **not** equivalent to testing on the owner workstation.

## Reproduction

Linux targeted test run:

```sh
test -x /usr/bin/prlimit && test -r /proc/self/limits
./mvnw -B -f orchestrator-service/pom.xml -Dtest=SyntraSyntheticHostLimitsTest,SyntraSyntheticProcessSpeechBoundaryTest,SyntraSyntheticAsyncVoiceBoundaryTest,SyntraVoiceReadOnlyConversationBridgeTest,SyntraOfflineVoiceTurnCoordinatorTest,SyntraOfflineSpeechArtifactReviewTest,SyntraSyntheticAudioGateTest,SyntraSyntheticAudioTaskControlReviewTest,SyntheticVoiceOwnerConsentLabTest,SyntheticVoiceTaskControlReviewTest test
```

Windows synthetic smoke tests (PowerShell, Java 21):

```powershell
.\mvnw.cmd -B -f orchestrator-service/pom.xml -Dtest=SyntraSyntheticWindowsPortabilityTest test
```

Full regression remains `./mvnw -B -f orchestrator-service/pom.xml test`, `./mvnw -B -f workstation-agent/pom.xml test`, and `python3 -m unittest discover -s tools/security -p test_stage45_pre_pilot_gate.py -v`.

**No merges, deployments, owner PC installation or real device activation. All review PRs remain draft; release / owner-PC pilot NO_GO.**

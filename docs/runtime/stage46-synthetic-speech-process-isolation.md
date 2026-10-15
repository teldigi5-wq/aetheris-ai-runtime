# Stage 46.10 — Synthetic speech process boundary (no real hardware)

**OPEN DRAFT / DEVICE OFF / TEST-ONLY PCM / RELEASE & PHYSICAL WINDOWS-PC PILOT: NO_GO.**

## What is implemented

This stage adds a separately launched, explicitly killable Java 21 subprocess for **deterministic analysis of caller-supplied synthetic PCM bytes**. Unlike Stage 46.9's thread-only prototype, stalled test workers can be forcibly terminated at an OS process boundary. This does **not** select, download, load or run a real wake/STT/TTS model.

- `SyntraSyntheticPcmWorkerMain`: self-contained child entry point. It accepts only 3 binary header integers and 640–3,200 bytes of 16 kHz, signed 16-bit little-endian mono **fixture PCM** via stdin. It rejects wrong magic, wrong rate, invalid length and trailing data. It applies a deterministic amplitude threshold and returns exactly `SYNTRA46_10_V1|VOICE` or `SYNTRA46_10_V1|SILENCE`, with a trailing newline; no transcript, prompt, command, file path or owner authority is ever accepted or produced. The PCM buffer is wiped on ordinary completion.
- `SyntraSyntheticProcessSpeechBoundary`: explicit synthetic-session opt-in; no direct device API, recorder, model, Spring service, socket, tool or filesystem output. One in-flight request per instance; caller PCM is privately cloned and always zeroed on return; no growing work queue. The subprocess is started with a **fixed Java executable from java.home**, fixed entry class, fixed fault-mode arguments, a 64 MiB maximum managed Java heap, and an **empty inherited environment** so secrets are not forwarded.
- Binary IPC is capped at 3,200 frame bytes and stdout/stderr is combined into a drain pipe with **128-byte output cap**. Only the exact two permitted response lines are accepted. On malformed output, output flooding, nonzero child exit, STOP or startup failure, the boundary latches closed. Timeout of 100–5,000 ms triggers forced process termination, not simply a cooperative Java thread interrupt. The parent attempts a bounded process wait during cleanup and refuses to restart a failed session.
- `stopNow()` does not take a child audio lock. It latches an atomic STOP and calls `destroyForcibly()` on any currently tracked child. A race where STOP happens just before the child is stored is covered by the immediate post-start STOP check. New frames cannot enter after STOP.
- Package-private fault injection modes used by tests: `STALL`, `MALFORMED`, `FLOOD`, and `FAIL`. These do not select arbitrary binaries, models, hosts or commands.

## Exact test commands

```bash
./mvnw -B -f orchestrator-service/pom.xml -Dtest=SyntraSyntheticProcessSpeechBoundaryTest,SyntraSyntheticAsyncVoiceBoundaryTest,SyntraVoiceReadOnlyConversationBridgeTest,SyntraOfflineVoiceTurnCoordinatorTest,SyntraOfflineSpeechArtifactReviewTest,SyntraSyntheticAudioGateTest,SyntraSyntheticAudioTaskControlReviewTest,SyntheticVoiceOwnerConsentLabTest,SyntheticVoiceTaskControlReviewTest test
./mvnw -B -f orchestrator-service/pom.xml test
./mvnw -B -f workstation-agent/pom.xml test
python3 -m unittest discover -s tools/security -p test_stage45_pre_pilot_gate.py -v
```

Stage 46.10 GitHub Actions (ubuntu-24.04, Java 21) verifies real child JVM spawn and termination with synthetic frames, stop-during-child, concurrency, malformed and overlong output, timeout, early process failure, plus previous 46.x consent/STOP regression, the immutable Stage 45 thirteen-blocker NO_GO policy and full orchestrator/workstation tests.

## Boundary and non-capabilities

**A separate process is not a restricted operating-system sandbox.** The child currently runs as the same OS user, without Windows Job Object or Linux cgroup/job quotas, privilege separation, filesystem-deny rules or independent network-deny policies. `-Xmx64m` limits JVM managed heap, **not all native or resident memory**, OS process count or CPU time. Process startup and kernel termination scheduling are not hard real-time and repeated owner-created instances are not globally rate-limited. Process shutdown is checked in synthetic tests on Linux; Windows actual execution is **not certified**.

This worker does **not** run speech recognition, voice synthesis, model inference, acoustic wake-word detection, actual VAD or real listening. It does not implement an authenticated owner-permission gate, OS microphone access, playback, barge-in, a worker binary signature/license validation, a signed IPC protocol or secure supervisor under an independent account. The earlier Stage 46.8 safe text conversation remains separate; no untrusted audio can issue desktop commands or approve tool use.

A real voice worker would require independently approved OS process policies; supervisor-enforced CPU/RAM/thread quotas and process-tree cleanup; signed/trusted engine binaries and separately reviewed voice-model redistribution licenses; Windows-specific tests with owner approval; independently authenticated consent; and audited IPC. **No owner-PC install, device activation, protected merge, deployment or production use is authorized by passing this stage.**

**Production and owner-PC pilot verdict: NO_GO, with the same 13 Stage 45 release blockers.**

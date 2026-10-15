# Stage 46.6 — Offline Speech Engine Review and Windows Readiness

**REVIEW ONLY • NO MICROPHONE • NO ENGINE START • NO MODEL DOWNLOAD • NO_GO**

This draft extends Stage 46.5 synthetic STT/TTS processing with an
independently hashed **read-only model-artifact integrity reviewer** and
a **non-recording Windows readiness report**. It does **not** select
or run a model, request Windows microphone permission, synthesize real
speech, load GPU weights, expose agent tools or certify deployment.

## Engine candidates for later owner-approved evaluation

| Role | Research candidate | Important limitations |
| --- | --- | --- |
| Wake word | openWakeWord — https://github.com/dscripka/openWakeWord | Engine source is Apache-2.0, but repository pretrained wake models use CC BY-NC-SA 4.0. **Noncommercial restrictions** are a blocker for unrestricted commercial distribution. A separately licensed/custom *Hi Syntra* wake model would require actual training, provenance and acoustic validation; this stage contains no such model. |
| Speech-to-text | whisper.cpp — https://github.com/ggml-org/whisper.cpp | Supports offline CPU, Windows and optional GPU inference. Actual downloaded models and their applicable licenses, hashes, quantization, RAM/VRAM needs and audio benchmarks require independent review. Nothing is installed or invoked here. |
| Text-to-speech | Piper — https://github.com/OHF-Voice/piper1-gpl | Maintained code moved from archived rhasspy/piper to OHF-Voice/piper1-gpl. The current project uses **GPLv3**; individual voice files have **separate licensing requirements**. A restricted voice or binary is not automatically safe to distribute with Syntra. |

**Selection remains unapproved.** The table identifies candidates,
not a final product stack or a claim of commercial licensing. Exact
engine versions, model cards, voice consent/provenance and dependency
licenses must be reviewed before a real installation, especially if
Syntra is later offered commercially.

## Added review code

The pure Java `SyntraOfflineSpeechArtifactReview` class checks
three **already-existing**, owner-supplied local data files corresponding
to WAKE, STT and TTS. It accepts a `ReviewProfile` requiring:
- OFFLINE-only, recording OFF, playback OFF, autostart OFF and network OFF;
- exactly one artifact for each speech role, with unique safe flat
  filenames ending in `.onnx`, `.bin`, `.tflite` or `.json`;
- known positive size no larger than 4 GiB each; file must be regular,
  not symlinked, and no symlink may be present in its parent path;
- SHA-256 from the local manifest **must match a separately supplied
  independently trusted expected hash**, and content bytes must hash
  to that value. A manifest distributed beside a model is *not* an
  independent trust anchor.

No process execution, model loading, network, audio device, local
profile editing or file writes are provided. Success reports
`ARTIFACT_HASH_REVIEW_ONLY`, **not `GO`**; all authorization,
license and hardware certification flags remain FALSE. Tests use
disposable synthetic files with test-owned hashes. Passing CI proves
only the checker, not the safety of a third-party model. A malicious
process replacing the file concurrently or supplying a forged external
expected hash is outside this class's trust guarantee; file integrity
is not an executable sandbox or publisher signature.

The separate PowerShell 7 script at
`workstation-agent/packaging/stage46/Inspect-SyntraVoiceReadiness.ps1`
emits a read-only `NO_GO` JSON status. It checks the OS/Powershell
environment, and if on Windows, Java 21 availability, but **does not
access the microphone, play audio, change Windows privacy controls,
install drivers or invoke WSL**. It explicitly says microphone
permission, STT/TTS engines, independent signatures/licenses and
physical voice latency have NOT been verified. The `-SyntheticCI`
mode requires exact GitHub Actions CI environment guards and says
`CI_SYNTHETIC_LINUX_NOT_WINDOWS`.

After *separately* reviewing this script, the workstation owner can
manually run this **non-recording** status inspection on Windows:

```powershell
pwsh -NoProfile -File .\workstation-agent\packaging\stage46\Inspect-SyntraVoiceReadiness.ps1
```

That is NOT a microphone-permission prompt or approval to install
Ollama, a wake model or a host agent. It returns `NO_GO` even on a
healthy machine.

## Acceptance criteria for a future physical voice pilot

- Obtain genuine consent through Windows microphone privacy and an
  authenticated user UI. Default off; no always-on capture without
  an explicit separate owner opt-in.
- Check build and model signatures, model/voice licenses, exact vendor
  hashes and publisher provenance; use dedicated non-privileged
  processes for microphone/STT/TTS.
- Real offline tests with synthetic speech first, then voluntary
  owner audio samples. Measure wake false positives/false negatives
  and interruptions in silence, room noise and accents; benchmark
  STT/TTS word error and real turn latency.
- Show a visible microphone-active indicator, hardware mute behavior,
  a reliable physical and software STOP, immediate audio/playback
  interruption, no secret or microphone buffer retention, and an
  independent check for network egress.
- Ensure all model transcripts remain untrusted data, never grant
  executable agent/desktop privileges, and cannot bypass owner-signed
  approval or emergency STOP.
- Run platform-specific Windows ACL, device permission, DLL search
  path, app allowlist, anti-rollback and install/uninstall review
  before proposing a real PC pilot.

**Unresolved release security gates remain 13.** The Stage 45
`NO_GO` policy is unchanged, previous branches are stacked drafts,
and no live model, microphone or workstation installation has
occurred in this PR.

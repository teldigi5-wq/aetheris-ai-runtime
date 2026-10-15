# Stage 46.7 — Bounded Offline Voice Turn Lifecycle (synthetic only)

**OPEN DRAFT / DEVICE OFF / OWNER IDENTITY UNVERIFIED / PRODUCTION + PHYSICAL PC PILOT NO_GO.**

This slice adds a functioning **single-session turn coordinator** (SyntraOfflineVoiceTurnCoordinator) atop the existing SpeechToTextAdapter and TextToSpeechAdapter contracts. It is pure Java, not Spring-registered, not an API or Windows service, and not connected to a microphone, speaker, actual VAD, wake model, speech engine, Ollama or workstation tool. Its fixture-only grant is not real authenticated consent. A caller can supply audio bytes, so the synthetic-only requirement is a **testing contract**, not cryptographic audio-provenance enforcement.

## Observable, implemented behavior

- An explicit synthetic session opt-in and currently true **test-fixture** permission are required. Default is disabled; 30-second absolute expiry and maximum 120 frames are enforced. This grants **no** real device access.
- Accepts supplied 16,000 Hz mono signed 16-bit little-endian PCM frames of 640–3,200 bytes (20–100 ms). Test-only amplitude threshold recognizes a potential speech interval after 2 voiced frames followed by 2 quiet frames. This is **not** a real VAD and ignores voice quality, noise, speakers and acoustic echo.
- Keeps at most 32,000 PCM bytes (one second), zeroizes the local buffer and private adapter copy after each completed attempt, discards sub-threshold one-frame impulses, and stops on exceeded memory/frame/time budgets.
- Uses only adapters named ci-synthetic-stt and ci-synthetic-tts; these IDs are guardrails against accidental device integration, not an authenticity guarantee. A fake STT response can acknowledge exact synthetic text wakes **syntra**, **sintra**, **hi syntra**, **hi sintra**, **hey syntra** or **hey sintra**. After wake, exactly **status** or **show system status** can produce a short **non-executing status-preview ticket**. Commands, URLs and prompt injections are ignored and never passed to the workstation layer.
- A response ticket is one-use and invalidated by a newer voiced utterance, revocation, session expiry or STOP. Fake TTS only synthesizes a fixed denial-of-real-control status phrase. Returned audio is wiped and never played or returned to a device.
- Spoken exact STOP, independent STOP-probe failure/activation, permission loss, session expiry, adapter errors and budget breach cause an irreversible **local** stop latch. STOP or permission loss **during a returning** STT/TTS call discards late results. Since callbacks are synchronous, this is not a deadline guarantee or real-time interruption of a hung model process.
- LocalSpeechRuntimePlan.current() continues to report HARDWARE_GATED, with physical VAD, microphone, STT and TTS integrations **false**. No rollout flags, installers, dependencies, model downloads, authorization paths or device settings changed.

## Reproduction and CI

Targeted tests:

    ./mvnw -B -f orchestrator-service/pom.xml -Dtest=SyntraOfflineVoiceTurnCoordinatorTest,SyntraOfflineSpeechArtifactReviewTest,SyntraSyntheticAudioGateTest,SyntraSyntheticAudioTaskControlReviewTest,SyntheticVoiceOwnerConsentLabTest,SyntheticVoiceTaskControlReviewTest test

The Stage 46.7 workflow checks this slice's positive/negative/adversarial behavior, conservative entry-point restrictions, Stage 45's 13-blocker NO_GO gate, and complete orchestrator + workstation regression. The static no-device scan is defense in depth only: it is not a security sandbox or proof that malicious code cannot access devices.

**Nothing in this review licenses redistribution of any speech model or validates real Windows mic privacy, acoustic latency, wake-word accuracy, model security, independently authenticated user presence, playback cancellation, secrets, production use or OS authorization.** A later stage requires separate owner-approved Windows device testing and review of actual engine/model licenses. No PR merge, PC installation, deployment, autonomous action or actual audio-device interaction is authorized by this stage.

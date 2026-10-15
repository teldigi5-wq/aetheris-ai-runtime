# Stage 46.5 — Offline Speech Foundation (synthetic, device-disabled)

**OPEN DRAFT / SYNTHETIC PCM ONLY / PHYSICAL VOICE NOT IMPLEMENTED / PRODUCTION NO_GO.**

This stage implements a **testable security and memory-handling policy** around the existing SpeechToTextAdapter and TextToSpeechAdapter interfaces. It does **not** provide microphone capture, wake-word recognition, VAD, speech-model inference, speaker playback, device permissions, network access, Windows installation, a new Spring endpoint, or a real voice agent.

## Implemented

The plain Java class **SyntraSyntheticAudioGate** is neither a Spring component nor an application/controller/daemon. No code path initiates recording or reads physical microphones. Only tests instantiate it with adapters named exactly **ci-synthetic-stt** and **ci-synthetic-tts**. The input is **caller-supplied fake PCM bytes**, not a device.

- **Recording is disabled by default.** Test callers must explicitly opt in to a short synthetic session and supply a currently true fixture-permission signal. These booleans are NOT OS microphone consent, authenticated owner approval or proof of speaker identity.
- Frames must be 16,000 Hz, 16-bit mono PCM between 640 and 3,200 bytes (20–100 ms) with even length. The session allows at most 120 frames and expires after 30 seconds. No general audio queue, continuous streaming, file output, or raw-audio history exists.
- STT receives a private clone of the fake input frame, wiped in a finally block. Source arrays remain owned by the caller. Only fixed fake transcript phrases (e.g. *hi syntra*, *hi sintra*) produce a **SYNTHETIC_TEXT_WAKE_ONLY** status. Untrusted generated transcripts such as *open editor*, URLs, shell commands, injection attempts, or arbitrary requests are ignored; the prototype cannot approve or execute a desktop action.
- Exact synthetic STOP phrases and an external emergency-STOP probe take priority. A STOP observed during STT or TTS invalidates the session. Unavailable STOP status fails closed. Releasing the global STOP later cannot revive the synthetic session. **This is a local latch, not host-process termination or real acoustic barge-in.**
- A fixed *Syntra synthetic preview ready* phrase may be passed to a fake TTS adapter after fake wake only. There is no speaker connection. Returned synthetic PCM is wiped and never returned to a device. Invalid/oversized synthesized audio is terminal.
- All hardware and privilege reporting remains false: microphone integration, local STT/TTS model loading, speaker output, audio recording, and authorization to execute desktop actions. The original **LocalSpeechRuntimePlan.current()** remains **HARDWARE_GATED**.

## What CI can verify

1. Pure Java synthetic frame cases: opt-in denied by default, permission, malformed PCM, wrong sample rate, frame/time budget, clone erasure, text-level wake, ignored commands, non-playing fake TTS, STT/TTS failure, permission loss, external STOP and no rearm.
2. Narrow test-only bridge to the actual Spring **TaskControlService** STOP flag. Engaging STOP while fake STT runs and releasing the test flag must not revive that session.
3. Existing Java model/consent/STOP regression and unchanged workstation-agent tests, plus the immutable Stage 45 assessment remaining **NO_GO** with all 13 release blockers.

**No real Ollama/STT/TTS model weights, microphone input, speakers or user laptop are used in CI.** These tests cannot certify acoustic latency, sound quality, real-time VAD, identity/permissions, RAM/VRAM demands, user voice recognition, actual playback cancellation or an independently protected host STOP.

## Future separately authorized integration

Real voice work requires an explicit approved Windows device test; OS microphone permission; a selected legally usable offline VAD/wake/STT/TTS engine; bounded models and genuine acoustic accuracy/latency tests; interruption of real playback; and independently authenticated owner consent for sensitive operations. Always-on listening remains OFF by default.

No real owner laptop installation, new background service, microphone, hardware model download, merge, production deployment, or autonomous host action occurs in this PR. All prior security PRs remain OPEN DRAFT and unmerged; the production and physical pilot verdict remains **NO_GO**.

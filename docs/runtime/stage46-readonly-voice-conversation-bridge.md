# Stage 46.8 — Voice-to-Read-Only Conversation Bridge (synthetic, disabled by default)

**OPEN DRAFT / SYNTHETIC TEST-FIXTURE PCM ONLY / NO REAL AUDIO HARDWARE / PRODUCTION NO_GO.**

## Engineering result

This stage connects the Stage 46.7 bounded `SyntraOfflineVoiceTurnCoordinator` to an actual in-memory conversational **read-only response path**. `SyntraVoiceReadOnlyConversationBridge` is a plain Java façade: not registered with Spring, not exposed through an endpoint, not a background task, not a PC or network driver, and not connected to an AI model. No owner PC access, audio device use, source data logging, TTS playback, or installation.

The input remains **caller-supplied synthetic PCM**, 16 kHz signed 16-bit mono, within the existing short frame, utterance, time and count bounds. Fake STT is not a real microphone/wake model and a fixture opt-in is **not** independently authenticated owner consent.

The bridge recognizes existing text-console *status* vocabulary and a narrow *help* request **only after an exact text-level synthetic wake**. `status` or `show system status` derives truthful fields from the existing `LocalSpeechRuntimePlan.current()`: hardware-gated state, microphone/STT/TTS connection flags, and no desktop execution in this preview. `help` or `what can you do` returns fixed, bounded preview capabilities. Wake acknowledgment itself is a fixed non-sensitive phrase.

The coordinator now enforces an explicit pending preview type in addition to its one-use ticket:
- A help ticket cannot synthesize status-only fake TTS, and a status token cannot be consumed as help.
- Ticket consumption precedes projection, preventing reuse or an approval replay.
- New voiced input invalidates unconsumed tickets.
- Permission revocation, synthetic emergency STOP, expiry, adapter faults and budget overflow latch closed. The bridge checks session authorization immediately before returning its read-only text.
- Arbitrary transcripts, URLs, shell requests, prompt injection, desktop requests, and unknown phrases produce **NO_REPLY** and are never forwarded to a model or tools.
- The synthetic PCM copy and utterance buffer are still erased in the existing coordinator; no audio or transcript history is kept in the bridge.

## Reproducible CI

```bash
./mvnw -B -f orchestrator-service/pom.xml -Dtest=SyntraVoiceReadOnlyConversationBridgeTest,SyntraOfflineVoiceTurnCoordinatorTest,SyntraOfflineSpeechArtifactReviewTest,SyntraSyntheticAudioGateTest,SyntraSyntheticAudioTaskControlReviewTest,SyntheticVoiceOwnerConsentLabTest,SyntheticVoiceTaskControlReviewTest test
./mvnw -B -f orchestrator-service/pom.xml test
./mvnw -B -f workstation-agent/pom.xml test
python3 -m unittest discover -s tools/security -p test_stage45_pre_pilot_gate.py -v
```

The four-job PR workflow verifies targeted positive/adversarial tests, source-level guardrails against direct hardware/Spring/network/desktop integration, fixed 13-blocker production NO_GO invariants, and existing complete orchestrator and workstation tests.

## Explicit non-capabilities

- **No real wake word, real STT, actual conversation model, microphone/speaker, continuous listening, acoustic VAD or TTS playback.**
- Synthetic opt-in and static fake adapter names do **not** provide audio provenance, user identity, OS microphone permission or hardware isolation.
- STOP is a local synchronous preview latch. A thread blocked indefinitely inside a fake adapter cannot be interrupted by this preview. Checks immediately before returning text reduce stale results but are not a hardware-level real-time guarantee.
- No AI text may authorize OS operations. Physical pilot and production remain **NO_GO** with 13 release blockers, regardless of passing CI.
- No secret, deployed service, licensing approval, background recording, cloud billing, model weights, physical Windows testing, protected merge or runtime deployment is part of this stage.

**Next authorized work** should address testable, bounded async cancellation and timeout under a separately isolated process, without enabling actual audio devices or bypassing owner confirmation.

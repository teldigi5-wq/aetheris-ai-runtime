# Stage 46.9 — Bounded synthetic async voice cancellation

**OPEN DRAFT / SYNTHETIC AUDIO ONLY / NO DEVICE, MODEL OR DESKTOP EXECUTION / PRODUCTION & WINDOWS PILOT: NO_GO.**

## Incremental functional change

Stage 46.9 adds `SyntraSyntheticAsyncVoiceBoundary` as a single-use, in-memory, Java 21 worker wrapper around the existing Stage 46.8 `SyntraVoiceReadOnlyConversationBridge`. It preserves the same fixed read-only spoken wake/status/help and local STOP semantics; it never forwards model text to OS actions or authenticates an owner. A manually supplied test fixture is required. The worker **never opens microphones, speakers, OS devices, sockets, a model, Spring endpoints, files or workstation tools**.

- The caller-owned synthetic PCM frame is copied once and wiped after the operation, including timeout/cancellation paths; the inner coordinator's private STT copy is wiped when the STT call actually returns.
- Only one task can be accepted at a time with `AtomicBoolean` admission control. A single daemon worker has an `ArrayBlockingQueue<>(1)` bound; a concurrent frame is denied instead of queued. No auto-retry or restarted workers.
- A bounded caller wait of **20–1,000 ms** is enforced by `Future.get(maxWaitMillis, MILLISECONDS)` (not a hard real-time guarantee). Timeout or interruption permanently revokes this instance, cancels the outstanding Future with an interrupt request, and calls `shutdownNow()`.
- `stopNow()` and `close()` set an atomic STOP without waiting for the synchronized synthetic bridge/underlying STT monitor. STOP, independently reported STOP, permission revocation, worker failure, time budget and existing frame/utterance budgets all fail closed.
- The worker and outer caller check STOP and fixture permission before releasing read-only replies, so cooperative late STT completion cannot authorize later actions. A status/help reply is fixed text from the existing `LocalSpeechRuntimePlan.current()`. No transcript logging or TTS playback is introduced.

## Important limitations / future security work

**Java threads cannot forcibly terminate arbitrary native or interrupt-ignoring engines.** `Future.cancel(true)` is cooperative; an uncooperative synthetic STT may retain a daemon thread, its coordinator monitor, and adapter-owned audio buffers after the caller times out. This design bounds **caller waiting and new work admission**, NOT execution time, memory of external engine code, or full erasure if an adapter never returns. These limits prohibit interpreting this as a process sandbox or shipping it as a real voice-engine host.

This is also **not independent authenticated owner permission or audio source attestation**: simple adapter IDs and fixture BooleanSuppliers cannot prove the origin of bytes or who is speaking. A caller has to supply test PCM; no physical devices are connected. Repeated creation of new boundaries could retain multiple blocked daemon threads and is therefore not production-grade isolation. A future production candidate should use owner-authorized OS-level process isolation with measured limits, trusted binaries, an independently controlled emergency stop, crash cleanup, and vendor/model license and real-device reviews.

## Reproduction

```bash
./mvnw -B -f orchestrator-service/pom.xml -Dtest=SyntraSyntheticAsyncVoiceBoundaryTest,SyntraVoiceReadOnlyConversationBridgeTest,SyntraOfflineVoiceTurnCoordinatorTest,SyntraOfflineSpeechArtifactReviewTest,SyntraSyntheticAudioGateTest,SyntraSyntheticAudioTaskControlReviewTest,SyntheticVoiceOwnerConsentLabTest,SyntheticVoiceTaskControlReviewTest test
./mvnw -B -f orchestrator-service/pom.xml test
./mvnw -B -f workstation-agent/pom.xml test
python3 -m unittest discover -s tools/security -p test_stage45_pre_pilot_gate.py -v
```

The exact-HEAD four-job CI workflow checks adversarial timeout/STOP and no-backlog behavior, compile and prior synthetic voice tests, conservative no-device/static integration policy, immutable Stage 45 thirteen-blocker NO_GO and complete Java regressions. The conservative static scan is defense in depth, not sandbox proof. The tests do not touch Windows hardware or install speech engines.

**Keep previous draft PRs open and unmerged. No real owner-PC pilot, recording, deployment, protected merges or autonomous application execution is authorized.**

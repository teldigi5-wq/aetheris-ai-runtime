# Stage 45.5.18 — Voice-to-desktop OWNER CONSENT prototype (synthetic text only)

**OPEN DRAFT / NO LIVE AUDIO / NO WINDOWS ACTIONS / RELEASE GATE CLOSED.**

Stacked on Stage 45.5.17 draft PR #64. This is a narrow
non-executing policy experiment and does not integrate a microphone,
wake-word neural detector, STT, TTS, LLM, desktop executor, or
`workstation-agent` command transport.

## Implemented and verified by synthetic Java tests

The plain Java `SyntheticVoiceOwnerConsentLab` is **not a Spring bean,
controller, registered tool, OS command adapter or background service**.
It has no path to actual desktop execution.

1. A separately trusted Java test caller creates an owner-bound
   short-lived text session, never from a voice transcript.
2. Strict ASCII text normalization recognizes only `syntra`,
   `sintra`, `hi syntra`, `hi sintra`, `hey syntra`,
   `hey sintra` as synthetic wake acknowledgments. This is **not**
   audio wake-word detection, voice-biometric identification, recording
   or proof of presence. Session lifetime is 5 minutes.
3. Only three fixed text intents are recognized after wake:
   `show system status`, `open editor`, `open project notes`.
   Each creates a **PREVIEW-ONLY** pending request expiring in 30
   seconds, with a fresh UUID and bound owner/session/intent. All
   unrecognized requests, URLs, shell commands, adversarial code
   strings and arbitrary natural-language prompts are denied.
4. An **independent synthetic owner UI fixture** signs an Ed25519
   permit binding owner, session, request UUID, intent, token UUID and
   tight issued/expiry timestamps (max 20 seconds). The transcript
   **cannot mint an approval**. The policy checks the previously
   trusted public key, single-use token, exact request and owner scope
   and expiration before returning `SIMULATED_ONLY`. Even after a
   valid permit there is **no process, file, browser, desktop or
   workstation-agent call**.
5. `stop`, `stop syntra`, `stop sintra`, `syntra stop`,
   `sintra stop`, `stop everything` and `emergency stop`
   are evaluated before wake/intent parsing for an existing session.
   STOP latches locally and invalidates all synthetic sessions. An
   injected external emergency-STOP probe is checked on every
   proposal and permit; an unavailable probe fails closed.
6. Approval consumption and STOP are synchronized within this
   standalone in-memory lab. Concurrent use of one permit can
   produce **at most one simulated result**, never two.

## Tests

- Wake required before command interpretation.
- Fixed three-intent preview-only mapping; app-open **not executed**.
- Ed25519 forgery and owner, session, request, action substitution denied.
- Expired/duplicate permits and expired sessions denied.
- URL, shell, script, prompt-injection and unrestricted instructions denied.
- STOP before wake, STOP after request, external emergency flag and
  unavailable STOP signal all block further consent.
- Concurrent approval replay allows only one simulation result.
- Existing `LocalSpeechRuntimePlan.current()` continues to report
  microphone, local streaming STT and local TTS **not integrated**.

GitHub CI runs targeted synthetic voice tests, unchanged
`workstation-agent` Java tests and complete orchestrator regression.
No physical device or network connection is exercised.

## Security gaps / why this is NOT finished voice integration

- There is **no live microphone audio input**, VAD, real wake-word
  model, streaming local STT/TTS, acoustic barge-in, or verified
  offline response latency. Typing “Hi Syntra” in a test does
  not demonstrate the user speaking it.
- In-memory UUID sessions and consumed token IDs **do not survive
  process restart** and cannot be trusted for a production replay
  fence. No independently authenticated owner UI, persistent
  auditable approval grant, anti-rollback store or bound physical
  human-presence credential has been deployed. Test signing keys
  live in test JVM memory.
- STOP is a local simulation latch and injected stop probe; it
  **does not call a broker or kill an OS process**. The existing
  `TaskControlService` and signed OCI supervisor are NOT wired
  into the simulator. The prior host security stack is still a
  series of unmerged draft reviews, not one certified deployed path.
- The existing Windows host agent is capable of certain separately
  configured allowlisted operations, including app launches. The
  current lab **never uses it**, and must not be described as a
  guarantee those existing capabilities cannot run elsewhere.
- No real owner speech authorization, remote access, app launch,
  WSL installation, native Windows permissions testing, microphone
  permission, GPU model inference, real-time secret redaction or
  hardware-backed signer protection has been implemented.
- This does not build the complete AI secretary, cybersecurity
  operator or autonomous company agent; those remain independent,
  owner-controlled product-development efforts.

## Next pre-pilot gate: Stage 45.5.19

Before any owner-PC pilot, conduct the full threat model and review
of *combined* agent, memory, supervisor, STOP and workstation paths;
verify no untrusted model instruction can execute as an OS command;
review source signatures, release provenance, owner identity,
rollback-resistant permission storage, durable audit, Windows
native/WSL tests, offline microphone/STT/TTS quality, recovery and
complete STOP process-tree proof.

Keep PRs **OPEN DRAFT**; no merge, deploy, production data, arbitrary
agent execution or host control is authorized by this stage.

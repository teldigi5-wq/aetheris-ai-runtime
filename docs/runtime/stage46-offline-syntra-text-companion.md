# Stage 46.1 — Syntra Local Text Companion (offline, non-executing)

**Status: REVIEW-ONLY, draft PR, not a production pilot.** This is the
first functional *local interaction* slice after the Stage 45.5.19
security-review roadmap. It builds directly on the existing
`SyntheticVoiceOwnerConsentLab` instead of bypassing the reviewed
permission and STOP policy.

## What works now

The repo includes a Java 21 command-line text companion. After compiling
the existing orchestrator-service module, run it **manually** in a local
terminal:

```bash
./mvnw -B -f orchestrator-service/pom.xml -DskipTests compile
java -cp orchestrator-service/target/classes io.aetheris.orchestrator.voice.SyntraLocalTextCompanion
```

This is **not an installer** or daemon, and the repository CI only
runs it in a disposable GitHub Actions shell. It is offered as an
optional, reviewable local console; it has not been run on the user's
physical Windows laptop.

Supported typed input:

- `help`: supported commands, with preview-only limitation.
- `hi syntra` / `hi sintra` (and exact existing wake aliases):
  acknowledge a **typed** wake in a short-lived demo session.
- `status`: truthfully shows `HARDWARE_GATED` because the actual
  microphone, offline STT/TTS and device pipeline are not connected.
- `show system status`, `open editor`, `open project notes`:
  produce an **approval-required preview** after wake. The console
  cannot approve them and cannot execute them. Text input cannot
  mint a signing key or obtain owner identity.
- `stop` / `emergency stop` / `stop everything`:
  permanently latch the **local demo** emergency STOP and deny all
  subsequent proposals during that session.
- `quit` / `exit`: end the console.

No agent tasks, system apps, shell commands, files, network requests,
Windows services, browsers, tool APIs or voice-device access are used.
The local demo label `localDemoNoAuth` is NOT an authenticated person.
The console verifier receives an ephemeral **public-only** Ed25519
verification key. The private half is not retained or exposed as an
owner-approval tool; there is no permit-issuing API. Even existing
separately approved synthetic consent policy yields only
`SIMULATED_ONLY`, never a real OS action.

The console reads bounded text lines to reject exceptionally large
commands, never echoes input into a log or telemetry, and prints
deterministic responses with no model-generated advice. It has no
command execution integration. It ends at EOF and does not register a
background service.

## Testing and limitations

Three GitHub Actions jobs at the exact draft PR HEAD:

1. Java tests for wake-before-intent, denial of unrecognized commands,
   absent signing approval, preview-only fixed actions, emergency STOP,
   externally unavailable STOP and overly long input. The job also
   actually launches this console using a fixed synthetic input stream
   to check its terminal output, NOT to control an OS app.
2. Recheck the Stage 45.5.19 release gate. It MUST remain `NO_GO`
   with 13 remaining security/production blockers; this stage is
   not a waiver of the independent security review.
3. Complete orchestrator Java regression and unchanged workstation
   agent Java regression.

**What still does not work:** actual wake-word listening, a live
microphone or speaker, STT, TTS, local LLM inference, searchable
memory, continuous conversation context, real identity/consent UI,
authenticated agent transport, production STOP propagation,
Windows application launch or trusted executable workload admission.

**Security boundary:** This demonstration cannot itself certify
isolation of previously existing code such as the separate
workstation-agent installer or tool services. It simply does not
call those components. Stage 45 is a stack of unmerged draft changes;
further integration must not silently grant model text OS authority.

## Next meaningful Stage 46 work

The next product engineering steps should focus on a locally hosted
conversation interface that uses an actual offline model (after owner
hardware/version/model-selection approval), independent local STT/TTS
adapters tested with real microphone permissions, durable authenticated
owner action consent, and fully integrated STOP for an isolated,
allowlisted OS runner. Keep each capability behind feature flags and
evidence-based CI and physical hardware gates.

**Do not deploy, merge, run on the owner's laptop, or enable autonomous
desktop operations from this draft.**

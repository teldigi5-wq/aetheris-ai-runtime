# Stage 46.2 — Syntra local-model conversation (explicit opt-in)

**OPEN DRAFT.** This adds a real, **loopback-only HTTP integration** for
a model that the workstation owner has **already installed** in their
own local Ollama runtime. The prior Stage 46.1 text console remains
unchanged by default; no network service is contacted until the user
explicitly opts into a model and types an `ask` question.

## User experience

- **Default**: `SyntraLocalTextCompanion` without arguments does not
  create an HTTP client or call any model and retains the existing
  typed wake, deterministic preview-only action proposals and STOP.
- **Opt-in**: `--local-model qwen3:4b` connects **only** to an already
  running Ollama-compatible server at `127.0.0.1:11434`, with
  redirects forbidden and HTTP proxies disabled. The model ID is a
  strict fixed-length local model-name string, NOT a URL/hostname.
- The first `ask ...` request looks up the exact model ID in the
  loopback server's `/api/tags` installed model inventory. **No
  model is pulled**, no cloud or remote API fallback exists, and
  a missing server/model returns a bounded failure message.
- After typing `hi syntra` or `hi sintra`, `ask explain Java
  interfaces` sends a limited text prompt to Ollama `/api/chat`.
  The response is shown as **UNTRUSTED MODEL TEXT**. It never becomes
  an owner permit, tool invocation, file operation or OS command.
- Existing `open editor`, `open project notes`, system status,
  STOP and `quit` remain strict deterministic controls, not
  model-generated intents. No model can approve those actions.
- Model request constraints: at most 900 prompt characters, at most
  256 generation tokens requested, two short previous dialogue
  exchanges retained in volatile memory, 2-second connection and
  45-second request timeout, 16 KiB max response body, 2400-character
  display limit. The private system instruction, transcript and
  response are never printed to server logs by this adapter. The
  Ollama server itself is a separate local process and may maintain
  its own logs.
- External STOP is checked before submitting a request and again
  after the blocking HTTP call; an observed STOP **suppresses output**.
  This is NOT a guaranteed interrupt of inference already running or
  a kernel-level STOP/epoch proof. It does not kill the Ollama process.
- Opt-in conversation history is in memory only, cleared when the
  adapter closes. No user identity is authenticated.

## Local setup (manual; not executed by this PR)

Prerequisites: Java 21, Maven dependencies already available, a
locally installed Ollama service and a **manually downloaded** model
that you have the rights to run. You decide whether to install or
download any software; this PR performs neither action. Example:

```powershell
# On your own device, only after reviewing this code and choosing a model:
ollama list

# Default Stage 46.1 mode, zero model socket calls:
java -cp orchestrator-service/target/classes io.aetheris.orchestrator.voice.SyntraLocalTextCompanion

# Optional real local Ollama model (model ID must already appear in ollama list):
./mvnw -B -f orchestrator-service/pom.xml -DskipTests compile
./mvnw -B -f orchestrator-service/pom.xml org.codehaus.mojo:exec-maven-plugin:3.5.1:java '-Dexec.mainClass=io.aetheris.orchestrator.voice.SyntraLocalTextCompanion' '-Dexec.args=--local-model qwen3:4b'
```

The Maven exec invocation supplies the existing Spring/Jackson
runtime dependencies, whereas `java -cp target/classes` is sufficient
for the default no-model mode. The first Maven build may require
internet access to fetch build dependencies; **offline inference**
requires those dependencies and Ollama model weights cached beforehand.

`qwen3:4b` is an **example model identifier**, not a claim that it is
installed on the user’s machine or a specific quality/performance
guarantee. Local GPU/CPU VRAM requirements and licensing must be
verified for the selected model. CPU inference can be slower.

## Tests and explicit limitations

GitHub Actions:
1. Uses only a **fake loopback HTTP Ollama server** to test actual
   `java.net.http` transport and JSON parsing: installed model
   inventory, no auto-pull, bounded inputs/responses, history size,
   invalid names, wrong MIME type, redirect/HTTP denial, malformed
   JSON, non-assistant messages, ANSI/control stripping and
   suppression of answers after a simulated STOP. No model weights
   are downloaded or run in GitHub CI.
2. Checks existing `NO_GO` assessment with all 13 unresolved pilot
   release blockers.
3. Full orchestrator and workstation-agent Java regression.

This does **not** yet provide microphone wake detection, local STT/TTS,
voice synthesis, full AI orchestration/agents, real tool calls, owner
authentication, persistent memory, Windows service installation or a
production privacy/security certificate. Local Ollama itself must be
trusted and configured not to use cloud models or external services;
the Java adapter constrains **its own sockets**, not every possible
action an independently running model server may perform. This PR is
not an endorsement of any particular model.

**Do not merge or deploy, install on owner hardware, or enable live
desktop actions through this draft. Production release remains NO_GO.**

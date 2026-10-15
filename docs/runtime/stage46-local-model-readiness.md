# Stage 46.3 — Installed-model verification and honest hardware advisory

**OPEN DRAFT / NOT PRODUCTION CERTIFIED / NO AUTO INSTALL OR MODEL PULL.**

Stage 46.3 extends the Stage 46.2 opt-in Ollama adapter; the original
no-argument console still makes no HTTP request.

## What actually works

Run the existing compiled Java 21 local companion only after the
owner has separately started a trusted Ollama instance and installed a
licensed model. Opt in using an exact local model name:

```powershell
./mvnw -B -f orchestrator-service/pom.xml -DskipTests compile
./mvnw -B -f orchestrator-service/pom.xml org.codehaus.mojo:exec-maven-plugin:3.5.1:java '-Dexec.mainClass=io.aetheris.orchestrator.voice.SyntraLocalTextCompanion' '-Dexec.args=--local-model qwen3:4b'
```

After **typed** `hi syntra` or `hi sintra`:

- `models`: read the local Ollama `/api/tags` metadata and display a
  bounded set of installed model IDs; it never automatically picks one,
  downloads weights, runs inference or calls `/api/chat`.
- `model info`: describe the **explicitly chosen** model ID, declared
  model-file disk bytes and first 12 hex characters of its SHA-256 digest.
  It reads current host physical memory only if Java exposes it.
  The advisory compares **declared model disk size** to currently
  available host RAM as a crude signal, not a loading requirement.
  GPU and VRAM are **UNVERIFIED**, and even a smaller disk size does
  **not** certify that loading or inference will succeed.
- `ask ...`: before **each** Ollama `/api/chat` request, re-fetches
  the installed model inventory. First accepted response pins the
  selected model's declared SHA-256 digest to the current Java session;
  any later digest change blocks further requests and clears the
  short in-memory dialogue context. This reduces accidental swapping,
  but the separate Ollama process can change between the inventory
  check and chat. It is NOT secure runtime image attestation.

The inventory enforces at most 128 entries, 80-character safe model
IDs, one unique ID each, integral positive declared disk size
(maximum 200 GiB per model), and a lowercase 64-character hex digest.
Missing or malformed metadata fails **closed**; it cannot become an
automatic model-pull signal. The HTTP client still connects only to
`127.0.0.1:11434` with no proxy, redirects, tools, or cloud fallback.

## CI evidence scope

Java test suite uses a **fake** local Ollama HTTP server and synthetic
model metadata. It tests metadata integrity and duplicate refusal,
no inference on read-only inspection, digest swap denial before another
chat, deterministic hardware advisories with known/unknown RAM, and
lack of any load/GPU certification. Existing 46.2 privacy, prompt,
response-size, consent, STOP, offline defaults, and full Java
orchestrator/workstation suites still run.

CI does **not** download weights, load an actual LLM, query an NVIDIA
GPU, benchmark throughput, test a physical Windows/WSL laptop, inspect
CUDA versions, certify cgroup limits, or guarantee that an Ollama server
is unmodified. A malicious local daemon may lie about digest/size or
perform its own network access. In particular, the comparison of model
**disk bytes to host free RAM is only an advisory** and cannot infer
actual RAM, VRAM, KV cache, quantization overhead, offload behavior,
token throughput, or supported context length.

## Required gates before real model testing

Verify actual owner workstation RAM, dedicated GPU VRAM, free disk,
model license and provenance; select an appropriately sized already
installed model; test bounded model loading and offline behavior
locally, including OOM recovery and no unintended external network
calls. Then add owner-permitted local microphone/VAD/STT/TTS,
while keeping generated language outside the actual owner-authorized
execution path.

All Stage 43–46 security reviews remain **unmerged drafts**. No
production keys, workstation installation, owner laptop changes,
desktop actions, merger or deployment are permitted by this stage.
The 13-blocker Stage 45 pilot verdict remains **NO_GO**.

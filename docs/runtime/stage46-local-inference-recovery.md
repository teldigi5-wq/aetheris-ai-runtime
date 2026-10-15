# Stage 46.4 — Bounded local inference, failure recovery and latency evidence

**OPEN DRAFT — synthetic CI evidence only. Production and Windows owner-PC pilot remain NO_GO.**

This slice extends the opt-in loopback Ollama client without enabling tools,
application launching, command execution, untrusted agent workloads,
microphone access, online model downloads or production deployment.

## Operational limits and controls

The existing standalone `SyntraLocalTextCompanion` continues to operate in
its original **no-model/no-socket default mode**. Only explicit
`--local-model EXACT_ALREADY_INSTALLED_MODEL_ID` uses the locally running
Ollama instance on `127.0.0.1:11434`. No other endpoint, proxy, redirect,
cloud fallback, CLI-supplied URL or automatic model pull is supported.

- Inference requests are serialized in this single Java console process.
  The previously established exact-model inventory check and session
  SHA-256 pin run before **every** chat request. A reported model digest
  mismatch now irreversibly latches the **client session**. Even an
  explicit retry reset cannot bypass it; a new reviewed client session
  is required.
- A fixed Ollama `num_ctx=2048` and `num_predict=256` request budget is
  used; chat JSON must fit within **8 KiB**, and response body within
  **16 KiB**. The pre-existing limit of 900 characters per user question
  and short in-memory conversation history still applies. These are
  requested model-side bounds and client payload limits, **not
  independently enforced OS-level RAM/VRAM/cgroup constraints**.
- `keep_alive="0"` asks the Ollama server to unload the selected model
  after a reply to reduce lingering memory residency. The client
  **does not and cannot verify** whether the Ollama process obeyed,
  and loading/reloading may increase reply latency substantially.
- Ollama metadata requests have a **4-second** timeout; chat has the
  fixed **45-second** timeout in the normal CLI (a package-private,
  test-only shorter timeout is used in the CI fake server).
- Request-time local **wall-clock elapsed duration** uses Java's
  monotonic clock around model discovery, local HTTP and response
  validation. Local `model health` reports the last attempt's
  elapsed milliseconds, successful response count, consecutive
  failures, open-circuit state, selected-model identity latch and
  retained context-turn count. There is **no user prompt or model
  reply** in diagnostic output, and no file persistence or telemetry.
  These are local protocol/request timings only, **not real token/s,
  first-token delay, voice turn latency or GPU inference benchmarks**.
- After **three consecutive failed attempts**, a local circuit breaker
  rejects future chat calls **before touching the Ollama socket**. It
  never automatically retries, falls back remotely or mints an owner
  approval. `model reset` is a deliberate local console action
  following typed wake that resets **transient** errors and discards
  in-memory conversation turns, but cannot reset a detected model
  digest substitution or emergency STOP.
- `clear context` deletes the volatile conversation turns without a
  socket request and retains the original pinned model identity.
  Neither this command nor `model reset` starts a model.

After typed wake in explicit opt-in mode, the new commands are
`model health`, `clear context` and `model reset`. The original
`status`, `models`, `model info`, `ask ...`, emergency STOP and
read-only preview actions remain.

## CI evidence scope

The targeted tests invoke the **real Java HTTP client** against a
disposable **fake** Ollama protocol service on loopback. They check:
- fixed context/output budget, unload hint, absence of any `tools`
  field and explicit non-streamed response requirement;
- normal request latencies are reported in milliseconds without
  content; a deliberately slow endpoint hits the test-only
  timeout, yields no conversation-history entry and increments
  the failure count;
- three consecutive 503 responses open a no-network breaker, and
  a manual reset can recover after the fake service becomes healthy;
- model digest replacement permanently blocks further queries
  despite a manual reset and digest reversion;
- clearing context never makes a request and does not confer
  permission to execute desktop actions;
- malformed and unsafe model responses, owner consent, STOP,
  previous default-console behavior and the full regressions.
No actual Ollama model weights, CUDA drivers, Microsoft Windows
hardware, paid infrastructure or microphone services are used.

**Important caveat:** an HTTP request timeout is *not* proof the remote
Ollama process cancelled its generation, unloaded the model or freed
host GPU resources. STOP can discard returned text; it does not
currently kill an inference process tree. A compromised local Ollama
daemon can ignore `keep_alive` or lie about its digest. Production
operation needs OS quotas, privileged broker isolation, authenticated
owner identity, offline speech device testing and an external audit/
STOP authority. Those 13 pilot blockers remain open.

## Next work

An offline live-model run on owner-approved compatible Windows hardware
must separately validate weights/license, model memory residency,
startup warm/cold latency, tokens/s, OOM/driver recovery and no egress.
STT/TTS preparation should start with separate **recording-disabled**
local-only adapters and opt-in microphone/audio-device permissions
before any real voice listening. No such physical execution is part
of this PR.

**PR must stay OPEN DRAFT and unmerged; production NO_GO.**

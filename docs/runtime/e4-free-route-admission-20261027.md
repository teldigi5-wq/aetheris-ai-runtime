# E4 — Zero-mandatory-cost model route admission, advisory only

**27 October 2026 · DRAFT / NOT CERTIFIED / NO_GO.**

The existing E3.6 free-model adviser already has opt-in Ollama and zero-price OpenRouter request paths. This E4 slice adds a **pure, side-effect-free admission policy** without credentials, model execution, downloads or an HTTP client. It accepts an exact route request and **untrusted observation snapshot** to produce a reviewable candidate or a deny-without-fallback decision. It never automatically chooses a different provider, model, paid route or internet endpoint.

For local Ollama routes the candidate must be allowlisted in the frozen E3.6 model vocabulary and present in a caller-supplied installed-model list. Oversized local models require separate high-resource intent. This is not proof of actual installation, available GPU memory or benchmark performance.

For cloud routes both remote-inference and code-disclosure intent must be declared, the provided catalog snapshot must be recent within 180 seconds, and all three token/request price fields must be strictly zero for the exact approved `:free` ID. Since **caller-supplied catalog data is not independently trusted**, the result says `CANDIDATE_REQUIRES_INDEPENDENT_LIVE_ZERO_PRICE_CHECK`, **not** cloud admission. Actual model dispatch would require authenticated owner authorization, fresh provider fetch, quota and fee controls, privacy consent and approved task-specific policy. Runtime owner permissions cannot be forged via JSON booleans.

Pure unit tests cover local and cloud candidate outcomes, stale pricing, malicious model identifiers, absent install proof, invalid booleans/fields, pricing NaN and nonzero fees, high-memory secondary permission, no fallback, no network or actual model calls, and persistent Stage45 NO_GO. Tests run on Linux and Windows at the exact draft SHA.

This begins E4 engineering but **does NOT complete E4**. E3.9H model-authored cinematic website verification, E5–E9, physical owner-machine tests and all nine release gates remain NOT CERTIFIED.

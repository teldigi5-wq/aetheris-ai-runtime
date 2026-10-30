# Enterprise E5 — independent creative-brief constraint review (30 October 2026)

**Development-only draft. This is ENTERPRISE CAPABILITY E5, not S0 security E6. Stage45 NO_GO.**

## Verified ancestor / scope

- Branch starts from [enterprise E5 draft PR #204](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/204), exact SHA `a8ef809e9b7cd9291bfef90e537ab3894dbeb912`. [CI #40668976829](https://github.com/teldigi5-wq/aetheris-ai-runtime/actions/runs/40668976829) successfully validated the existing, isolated copy-preview ZIP and real desktop/mobile/reduced-motion Chromium review. Parent #204 was itself stacked on E5 #203 and E4 #202, **all OPEN/DRAFT/UNMERGED**. No protected `main` merge or Runtime release reference change.
- The original E0–E9 enterprise roadmap remains **E3.9 model-based coding/recovery**, E4 local/cloud routing, **E5 safe website/app creation**, E6 cinematic 3D, E7 multi-agent, E8 Syntra Mission Control, E9 integration/certification. Separate S0-E6I Security PRs do **not** advance the enterprise E6 Cinematic Studio milestone.
- Existing E5 #204 already restricts a candidate to one strictly validated local JSON object, changing only three escaped brand/headline text nodes in a **human-coded** E3.9H reference. It cannot author application code or publish websites.

## New work

`tools/enterprise/e3_factory/e5_creative_brief_gate.py` adds an **independent creative-brief vs candidate compliance gate**. Both inputs are inert, bounded **local-only** files at fixed paths `target/e5/creative-brief.json` and `target/e5/copy-proposal.json`. A brief must be an exact version-1 object:

```json
{"version":1,"brandLabel":"Prototype Atelier","requiredTerms":["quiet","motion"],"forbiddenTerms":["guaranteed","certified"]}
```

- Duplicate JSON fields, extra privileges, invalid types, out-of-range lengths, exotic/mixed-script terms, duplicate/contradictory term sets, HTML/URL/Unicode control characters and oversized input are rejected. Brand must match **exactly** the independently supplied brief; each required ASCII lexical term must appear as a whole word in the two headline lines, and forbidden terms must not appear. **This is strictly lexical validation, not semantic quality or factual truth verification.**
- On success the pre-existing vetted `e5_untrusted_copy_preview.build` function re-validates the original human-written reference and builds the byte-for-byte deterministic ZIP. New evidence stores canonical brief/proposal SHA-256 and actual ZIP SHA-256 with bounded term counts and an explicit `LEXICAL_CONSTRAINT_MATCH_ONLY_NOT_APPROVAL` decision. The archived JavaScript and CSS remain byte-identical to their approved research reference; no custom code or scripts may enter the archive. No model inference or external request occurs.
- Manifest and sidecar continue to deny owner approval and deployment. The sidecar explicitly says **brief authenticity was not verified, candidate copy authenticity was not verified, model authorship is unknown**. A brief alone cannot grant ownership, consent, permission to use branding or publish anything. The source code remains human-authored, not AI-generated.
- The CLI accepts **no source path, network endpoint, deployment flags or additional parameters**; it rejects symlinked input/output in its designated `target/e5` folder. A failed comparison leaves existing artifacts untouched. Review results are written only under local `target/e5`.
- Seven new `unittest` cases assert deterministic build, immutable active code, rejection of brand/term mismatches and prompt-injection-like schema attacks, Unicode control and marker oversize denial, no artifact mutation on refusal and no symlink input acceptance.

## CI and independent browser proof

The exact-head pinned [`runtime-e5-creative-brief-gate.yml`](../../.github/workflows/runtime-e5-creative-brief-gate.yml) runs the original reference tests, original quarantined-copy tests and seven new negative tests, repeats the CLI build to prove ZIP and report bitwise reproducibility, checks independently recomputed SHA-256, and extracts the generated ZIP. It then installs pinned Playwright 1.55.0 and **actually opens the generated website in Chromium** at desktop, mobile and reduced-motion settings. The inherited browser harness verifies visible text, original JS/CSS hashes, no unexpected external requests, no console errors, no horizontal overflow, a single main landmark and reduced-motion static fallback.

All artifacts are **offline review only**, retained briefly and not published. This candidate tests constrained text against constraints; it is **not** autonomous end-to-end website generation or design research.

## Remaining enterprise E stages and independent security work

- **E3.9**: verified real-model code quality on held-out challenges and authentic cinematic/3D model-authored work still incomplete.
- **E4**: actual reliable model selection, budget/consent control, local/cloud routing and benchmarking require more work.
- **E5**: model-produced original websites, brand research, genuine differentiated design, owner approvals, independent WCAG 2.2 AA and performance testing, supervised repair loops, original asset provenance and deployment gates remain to be built.
- **E6**: immersive Cinematic Studio Blender/Three.js/WebGL/GSAP; **E7** collaboration/agents; **E8** Syntra owner desktop; **E9** resilience and product release certification remain incomplete.
- **S0 security track is separate**: the previous latest security checkpoint was Platform PR #352 / S0-E6I, with 58/58 targeted tests but unmerged. The Runtime blanket API/actuator quarantine is draft and would break valid clients if deployed. No ownerless Task/Mission/SSE access is authorized.
- Stage45 nine independent release certifications remain uncertified; owner PC physical acceptance pending. Runtime security issue #23, Platform issue #251 and Platform dashboard dependency issue #240 remain OPEN.

**Stage45 NO_GO**. No production deployment, no force push, no main merge, no privileged account or key use, no cost incurrence, and no assertion of an already running autonomous website factory. Exact-head completed CI is required before considering this substage technically verified.

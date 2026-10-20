# Aetheris E3.9F — ephemeral withheld-parameter Chromium controls

**Status:** engineering slice; not a certified externally blinded benchmark and
not a scored real-model trial. Stacked on verified draft PR #152 at exact head
`20012f915b9049030a1be5d5d5b6b9312a704f3e`. Stage45 is NO_GO.

## Why this exists

The previous E3.9C-2B2 / E3.9F V2 eight CSS cases were all static, publicly
listed and therefore not independently sealed. The V2 score on those cases was
Granite4:3b **2/8**, Hermes3:3b **0/8** browser-verified repairs; both scores
must remain labeled *public synthetic fixture trials*, never general software
engineering quality. A new evaluator must not merely rename those public
cases "sealed."

This change adds **unpredictable per-run test parameters** generated on
disposable CI with `secrets.token_bytes(32)`. HMAC-SHA256 derives unique CSS
offsets for eight fixed accessibility fault families. The source code still
publishes the *family definitions* and the deterministic reference repair;
hence **this is NOT an independent external hidden benchmark**. The private
seed is not included in a model prompt and is only included in the post-test
artifact to allow later reproducibility.

## Execution boundary

1. Existing locked 1 CPU, 256 MiB, 64 PID, read-only-root,
   `--network none` Docker worker generates the original reference website.
2. The harness checks original `styles.css` byte for byte against
   `manifest.json`. If modified unexpectedly, refuse.
3. A random 256-bit CI-only seed is generated and its SHA-256 commitment printed
   **before** the eight unique challenge cases start; the seed/values are
   not checked into the GitHub source tree. The intermediate private file is
   generated only inside the ephemeral CI workspace.
4. One synthetic focus fault at a time is seeded, preserving the entire site
   except a single `.skip-link:focus` CSS rule. Both mobile **390x844** and
   desktop **1280x800** must demonstrate the declared failure in **actual
   Chromium**; keyboard Tab reachability, computed CSS and geometry are captured.
5. A fixed **non-model** deterministic reference restoration runs inside the
   same network-disabled patch worker and earns **zero AI repair credit**.
   Both viewport *after* checks must really pass.
6. Finally rederive the challenge values from the hidden seed, compare the
   HMAC results, and publish a post-test seed reveal only with the final
   artifact. Eight cases × two viewports × two phases = **32 real browser
   verdicts** when CI proves them (not model accuracy).
7. Original website QA, independent negative Python tests (Ubuntu/Windows),
   full Java regression and no-owner/Stage45 authority checks still run.

The data boundary is experimental: the model proposer is **not executed
in this slice**, and the seed is withheld only from model input, not
cryptographically inaccessible to all CI-side Python code. The secret and
evaluator live on the same test runner and cannot qualify as independently
maintained external blind evaluation.

## Forward integration contract — still unimplemented

- Proposer should receive only the typed focused rule plus actual two-viewport
  observations, not the seed, family ID, private manifest or expected patch.
- Independent policy must consume a hash-bound receipt and refuse wrong
  selector/property/value, duplicate keys, arbitrary CSS, negative coordinates,
  Javascript commands and modified unrelated site bytes, and work inside
  the existing `--network none` worker. No automatic oracle fallback.
- Only after actual two-viewport Chromium checks may a model earn credit.
  Reset after scoring must explicitly say `modelGeneratedRepair=false`.
- CI comparison should include baseline Granite/Hermes V2, independent
  multi-domain external withheld corpus, negative no-fault cases, repeated
  trials and uncertainty intervals, before expanding to 8B/14B+ model choices.
- These controls grant **no permissions** for shell, browser actions by models,
  owner PC, live trading, GitHub writes from model output or Stage45 deployment.

## Review commands

Run `python -m unittest discover -s tools/enterprise/e3_factory -p
'test_e39f_runtime_blind.py' -v` for deterministic security invariants.
The workflow `runtime-enterprise-e39f-seeded-browser.yml` runs the full
browser challenge without any local developer hardware or paid model API.

This PR remains draft until exact-head CI and raw artifact scrutiny.

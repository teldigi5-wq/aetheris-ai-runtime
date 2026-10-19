# E3.3 — Held-out source variations and heuristic patch verification

**CI-only engineering prototype. No model inference, no model-generated patches, no owner approval, no real Windows PC commands or production.**

## New implementation

A `heuristic_repair.py` proposer reads only the generated site's current source bytes and an independently produced browser failure report. It does not import the evaluation fixture table or read exact desired source repairs. It tries a limited class-level repair for three problem families:
- Scroll: replace a numeric literal used in `const p = ...` with a call to the existing, explicitly defined `progress()` function.
- Mobile overflow: remove a single `html{min-width:NNNpx}` rule with a width above the responsive 760px breakpoint.
- Reduced motion: restore the existing `motion.matches` expression when an obviously disabled `false` or `Boolean(false)` expression is found.

Unknown patterns lead to **`E33_ABSTAIN_UNRECOGNIZED_DEFECT`**, rather than a speculative write. This is a deterministic rule-based repair heuristic, **not an LLM**, and it does not generalize to arbitrary frontend defects.

A *separate patch gate*, implemented in the apply path, independently validates the submitted JSON schema, exact allowlisted output file, prior file SHA-256, browser-diagnosis SHA-256, single short literal replacement, category-specific restricted code grammar, expected output SHA-256 and the **canonical generated-file SHA-256** from the original build manifest. It rejects injected network calls, executable new source, altered destination paths, extra proposal fields, size violations and symlinked evidence. A passing patch receipt still reports `modelWasRun=false`, `ownerApproved=false` and `productionPublished=false`.

## Distinct evaluator

`e33_evaluator.py` defines **six variants unknown to the proposer implementation**: two numerical scroll regressions, two viewport-width overflows, and two reduced-motion regressions. It generates the fault in the disposable test environment, and a separate Playwright Chromium process must observe the correct defect before the proposer receives its diagnosis. After repair, Chromium must pass, followed by the complete E3.0 end-to-end browser regression.

Important distinction: the fixture table is stored in the same reviewable repository and is **not a cryptographically hidden test set or independent third-party benchmark**. The proposer cannot import it and its heuristics are not keyed by test case IDs; nevertheless it knows the three defect *categories* in advance. Consequently any success rate applies only to these deliberately constructed source variants, not unseen real-world faults or model performance.

## Scope of execution

The worker is launched through the existing E3.1 bounded Docker runner on disposable Ubuntu GitHub CI: **network none, 1 CPU, 256 MiB memory cap, 64 PIDs, read-only root/repository, fixed temporary output, no-new-privileges, all Linux capabilities dropped, non-root, 45-second process wall-clock limit**. Playwright and browser dependency installation run in the separate disposable CI test harness, not inside the network-disabled worker. The runner's actual Docker inspect output is required in QA evidence.

Ubuntu and Windows negative tests check the patch grammar, rejection of traversal, hash tampering, extra instructions, large payloads, symlinked evidence, and explicit abstention for unsupported faults. The complete Java orchestrator regression remains mandatory.

## Truthfulness and remaining gates

- No AI model was used, so **do not call measured success an AI success rate**.
- No true autonomous engineer, arbitrary code-change authority, customer-code access, human owner identity enrollment, positive approvals, model self-approval or deployment is provided.
- The canonical company registry still describes 13 **proposed** companies and 100 **proposed** divisions; these are not running agents.
- Stage45's actual 13-blocker suite is absent from this development stack and **has not** been certified. Production, trading, local user PC and privileged operations remain **NO_GO**.
- Protected `main` and previous unmerged PR stack remain unchanged.

## Next engineering step

Build an actual **local model advisory adapter**, only after verifying resource budgets and isolation. The model may emit text-only proposals, but a separately verified patch gate must treat those outputs as untrusted and run the real browser regression, preserving failure and abstention evidence. For valid generalization claims, freeze a benchmark unavailable to the model and implementation author, add unknown bug families, and report every attempted case.

Stack: E0 #102 → E1 #104 → E2.1 #106 → E2.2A #107 → #109 → #110 → #112 → #113 → #114 → E3.0 #116 → E3.1 #118 → E3.2 #120 → this E3.3 draft.

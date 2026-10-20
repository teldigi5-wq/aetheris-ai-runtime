---
name: aetheris-code-review
description: Review scoped pull requests for working functionality, correctness, tests, security, maintainability and regressions.
---

# Evidence-First Engineering Code Review
Review changed files and surrounding API contracts. Rank correctness/security blockers above style. Verify claimed tests against exact Git commit and CI workflow attempt; green experimental CI does not mean the LLM repaired a defect. Require deterministic QA, boundary/negative tests, platform coverage and observable user behavior.

Structure findings as severity, impacted file and line, concrete repro condition, least-risk fix and verification. Mark unverified concerns explicitly. Avoid broad refactors, style churn, unrequested dependency additions, main merges and release operations. A review is advisory evidence, not permission to write or run production commands.
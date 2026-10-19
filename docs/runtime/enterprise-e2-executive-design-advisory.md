# Aetheris Stage E2 — executive and design advisory handoff

**DRAFT / NON-EXECUTING / NO OWNER APPROVAL IMPLEMENTED / NO AUTONOMOUS EMPLOYEES.**

## Scope implemented
- Proposed CEO and Design Studio roles (Product Design Lead, UX Researcher, Visual Interface Designer) in a versioned config resource.
- All four are intentionally `PROPOSED`, `executionEnabled=false`, `authorityGranted=false`, `approvalCanBeIssued=false`, `allowedTools=[]`. None has been added to the canonical agent catalog.
- A real Spring `EnterpriseAdvisoryPlanningService` with `prepare(DesignBrief)` turns a scoped design project into a structured seven-stage handoff and mandatory independent review checklist.
- Real catalog identities are referenced as planning candidates, not invoked: `executive-planner`, `frontend-engineer`, `qa-engineer`, `project-manager`.
- Proposed design roles may appear in the plan as `PROPOSED_NOT_REGISTERED`; there is no impersonation of configured workers or executable actions.
- Every input project and target company/division is checked against the E1 validated organization snapshot; impossible company/division combinations and invalid inputs fail closed.
- Each normalized brief receives a deterministic SHA-256 digest for change detection. **This is not an authenticated signature, owner approval, tamper-proof evidence store, or execution authorization.**
- All results are internal, immutable, read-only data records. No controller, external HTTP endpoint, database write, scheduled worker, GitHub call, browser automation or production integration.

## Example
```java
var brief = new EnterpriseAdvisoryPlanningService.DesignBrief(
    "rog-laptop-study", "aetheris-immersive", "scroll-experience-engineering",
    "Build a cinematic laptop product showcase using scroll-controlled 3D",
    List.of("Reverse scrubbing", "Reduced-motion fallback", "Independent browser QA")
);
var packet = advisoryPlanningService.prepare(brief);
// Structured planned handoff only; does not execute or authorize agents.
```

The recommended QA checklist includes 3D asset rights, scroll direction and resizing,
reduced-motion accessibility, browser performance, and independent acceptance evidence.

## Governance and statuses
| Item | Status |
|---|---|
| E0 canonical main catalog | 26 existing identities, no changes |
| Later unmerged security draft | 30 identities, not promoted to main |
| E1 company/division target | 13 companies / 100 proposed divisions |
| E2 CEO + Design Studio metadata | 4 proposed advisory roles (not new configured agents) |
| E2 deterministic work-packet generation | Implemented in Java subject to CI |
| E2 model-driven advisory workers | Not implemented |
| Owner-authenticated approval service | Not implemented |
| Actual delegated tool execution | Not implemented |
| Independent running QA workers | Not implemented |
| Physical Windows owner-PC and Stage45 certification | Not executed; NO_GO |

## Verification gates
- JUnit adversarial tests: nonauthorizing roles, deterministic fingerprint,
scope validation, invalid/oversized input, role shadowing, self-approval prevention,
immutability and defined seven-stage handoff.
- Existing E0/E1 workforce and topology tests must remain green on Linux + Windows.
- Full orchestrator Java regression must pass.
- Source-level nonactivation checks must reject unexpected side-effect adapters.
- **Stage45 security checks are not on this E1 base branch**, so E2 does not certify them.
  The actual 13-blocker Stage45 policy must run on its audited integration branch
  before any protected-branch merge or real device/execution activation.

## Next scope — E2.2/E3
1. Real authenticated, durable action-specific owner approval service with expiry,
   target and content binding, replay refusal, immutable review trail and separation
   of approval from agent planning.
2. Independently verified design artifact production (SVG/Figma/3D scene specification)
   with deterministic test fixtures and QA output.
3. Disposable scoped worker execution only after the Stage45 review path is integrated
   and target Windows host containment is physically certified.

Do not upgrade status from `PROPOSED` to `OPERATIONALLY_VERIFIED` based on model
outputs, documents or synthetic tests.

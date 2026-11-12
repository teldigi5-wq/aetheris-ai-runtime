# Aetheris Enterprise E0 — Canonical workforce preservation audit

**Audit snapshot: 2026-10-19. Review-only / draft. No production, local PC, microphone, trading, agent-tool activation, registry or protected-main merge is authorized.**

## Scope and real counts

| Repository reference | Configured identities | Divisions | Cybersecurity | Status |
| --- | ---: | ---: | ---: | --- |
| `aetheris-ai-runtime` protected `main` | **26** | **13** | **5** | Existing canonical released-branch configuration |
| `feature/stage46-company-organization-readonly-20261016` (PR #92) | **30** | **13** | **9** | Open draft stack; not merged into protected main |
| `feature/stage46-paper-recovery-snapshot-fingerprint-20261016` (PR #101) | **30** | **13** | **9** | Latest inspected open draft stack; not merged |
| Enterprise target | **54 initially proposed** | **100 proposed** | Unknown future total | Non-operational design, 13 virtual companies |

The four draft-only security identities added beyond main are:
`chief-information-security-officer`, `cryptography-officer`,
`detection-engineer` and `threat-hunter`. These are **not**
released or independently proven autonomous workers. The 54-agent
planning number = 30 draft identities + 24 proposed Immersive specialist
roles *if all 24 are unique*. It is not a workforce or activation count.

The current main configuration was inspected at
`orchestrator-service/src/main/resources/application.yml`,
blob `d7e0df3c24077ef20c24cc59df3bca902c805520`.
Its 26-ID configuration is frozen in
`orchestrator-service/src/test/resources/enterprise/canonical-main-specialists.json`.
The fixture freezes each ID, division, risk level, preferred model class,
human-approval flag and **complete allowed-tool family list**.
`EnterpriseCatalogBaselineTest` runs through the real Spring-managed
`AgentCatalogService`, compares all 26 records and refuses drift,
duplicates and accidental high-risk approval removal. It does not
endorse an elevated role or grant any new permissions.

**Migration implication:** When the 30-role draft security roster is
considered for main, deliberately update the fixture in the same
human-reviewed change with a four-identity diff, new authority review,
governance regression and exact CI evidence. Never casually update a
fixture just to make a failing test green.

## Existing division → proposed operating home

These assignments are organizational *proposals*, not permission or
worker migrations. Source IDs and tool scopes remain owned by the
existing runtime catalog.

| Existing canonical division | Proposed organizational home |
| --- | --- |
| Executive Office | Group Headquarters |
| Software Engineering | Aetheris Software |
| AI and Data | Aetheris Intelligence |
| DevOps and SRE | Aetheris Cloud |
| QA and Release | Independent shared QA/Release governance |
| Cybersecurity | Aetheris Cyber Defense |
| Tools and Integrations | Aetheris Enterprise Services |
| Computer Operations | Shared platform operations; Cloud/Industries by mission |
| Research Lab | Aetheris Science with shared Intelligence capability |
| Syntra Academy | Aetheris Academy |
| Career Intelligence | Aetheris Academy with Ventures collaboration |
| Business Intelligence | Aetheris Ventures with Intelligence collaboration |
| Financial Intelligence | Aetheris Finance |

A division mapping does not equate to a new tenant, business entity,
role permission, service process, separately trained model or live
company. Cross-company work will require verified scoped task leases,
workspace boundaries and explicit approval.

## Target organizational catalog (NOT IMPLEMENTED)

Plan for **13 virtual companies and up to 100 proposed divisions**:
Aetheris Software, Intelligence, Cyber Defense, Creative, Cloud,
Ventures, Finance, Industries, Academy, Experience, Science,
Enterprise Services, and Immersive Technologies. The latest 3D/motion
expansion adds a distinct Immersive company with 12 visual-engineering
divisions plus a candidate pool of 28 additional specialist areas.
Do **not** duplicate the 13 existing divisions by blindly adding
them to the 100 target divisions; this is a proposed target taxonomy,
not a verified unique operational division count.

Priority demonstration: an autonomous, safe, cinematic product website
using Blender assets, image-sequence/scroll synchronization or real-time
WebGL, actual UI, bounded code execution, independently verified QA,
performance/reduced-motion tests and a draft GitHub PR. No claim of
having passed this benchmark exists yet.

## Findings and gating risks

1. **Stack integration:** PR #92 is stacked on earlier unmerged
   Stage46 work. GitHub comparison on 2026-10-19 showed the PR #92 head
   **518 commits ahead of main**, not 518 released commits. Merging
   without an incremental integration review is high risk.
2. **Headcount accuracy:** main has 26, later draft 30; earlier
   presentation of 30 as the current main workforce is inaccurate.
3. **Agent efficacy:** catalog registrations, workflow skeletons,
   synthetic lab tests and hosted CI are not evidence that 30 workers
   independently execute end-to-end jobs.
4. **Governance:** all applicable Stage45 release blockers remain
   binding on draft work. E0 cannot waive them.
5. **Software factory:** no certified end-to-end autonomous website
   factory, design-to-Blender pipeline, mobile release pipeline or
   physical owner-PC pilot has been demonstrated.

## E0 acceptance checks

- [x] Main catalog inspected, frozen at 26 specialists / 13 divisions.
- [x] Four draft-only cybersecurity identities identified.
- [x] Main ID, division, risk, model, owner approval and tool families locked in a committed fixture and JUnit test.
- [x] Cross-platform CI workflow configured for that test.
- [ ] New workflow has completed with passing CI evidence on the exact E0 PR head.
- [ ] Other draft branches fully reconciled and a secure integration plan approved.
- [ ] All 30 staged identities accepted into main following reviewed governance and appropriate merge gates.
- [ ] E1 company registry design reviewed and implemented in a separate PR.

## Next stage

E1 should introduce an **inactive read-only enterprise registry** with
13 proposed company definitions and explicit `proposed` / `configured`
/ `verified` statuses, sourced from the canonical agent catalog,
without creating roles, adding tool grants, tenant cross-access,
approvals, autonomous runs, API mutation endpoints or production
deployment. E2 can follow only after E1 isolation and governance tests.

**Security principle:** source-of-truth observed catalog ≠ proposed
company organization ≠ remotely or locally operational workforce.

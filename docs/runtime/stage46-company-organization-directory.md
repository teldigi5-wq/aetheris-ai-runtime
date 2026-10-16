# Stage 46.26 — Source-grounded Company Organization Directory

**OPEN DRAFT · NO RELEASE AUTHORITY · NOT AN AUTONOMOUS COMPANY CERTIFICATE.**

## What is implemented today

The existing canonical agent catalog declares **30 specialist identities in 13 divisions**. These include an Executive Office with an Executive Planner and Project Manager, nine Cybersecurity specialists (including an expressly owner-governed CISO, AppSec, Detection Engineering, Threat Hunting, Blue Team, authorized Red Team, Security Architecture, Cryptography and Incident Command), Software Engineering, QA, AI/Data, DevOps, Business Intelligence and other specialized divisions.

That catalog is **not just a pretend org chart**. The repository also implements specialist tool-family authorization (`SpecialistToolAuthorizationService`), governed delegation (`TaskDelegationService`), distinct execution/review/verification requirements (`TaskVerificationService`) and worker specialist-identity binding (`WorkerIdentityBindingService`). Executive briefings and owner-approval escalation exist in `ExecutiveAgentService`. Nevertheless, none of those independently proves every configured specialist operates an external tool, runs its own trained model, executes on a physical owner PC or autonomously completes real jobs.

### Truthful gaps

- `chief-executive-officer` **is not** a canonical registered identity; the Executive Planner is a coordination role, and the Executive Agent service produces bounded briefings from connector-neutral signals. It is not a self-authorizing CEO.
- There is no dedicated registered **Design Studio**, **Product Design Lead**, **UX Researcher**, or **Visual Interface Designer**. Existing Frontend/Desktop Engineers do have UI implementation capabilities; these are not the same as a staffed design department.
- The nine cybersecurity roles are **registered with policy/governance constraints**, not a proven continuously operating SOC or independent security professionals. A CISO agent cannot grant itself authority to conduct offensive testing, accept production risk, or override the owner.

## New working feature

`CompanyOrganizationDirectory` derives a typed organization snapshot from the **actual, configured AgentCatalogService**, not a duplicated marketing JSON list. It includes:

- Counts by real configured division and identity, with only ID, display name, risk level and approval-required metadata (no user data, credentials, task history or private messages).
- Real Executive Planner and CISO registration indicators, and a separate explicit CEO registration flag.
- Four transparent proposed-role checks (CEO, Product Design Lead, UX Researcher and Visual Interface Designer) that show **registered=false** until those canonical identities truly exist. These are not executable jobs, agents, recruitment promises or authority.
- A read-only endpoint `GET /api/orchestrator/company/organization` returning the snapshot with `Cache-Control: no-store`, designed for a future dashboard/desktop company directory. The endpoint has no POST, agent-run, hiring, delegation, tool-grant, network access or other mutation operation.
- Fail-closed detection of duplicate registered specialist IDs and blank identities. No role, tool, permission, owner, approval path or existing agent ID was changed.

`CompanyOrganizationDirectoryTest` verifies the current exact 30/13 counts, all nine defensive-security specialists, missing CEO/design identities, explicit no-release/no-autonomous-employee flags, real GET behavior and rejected POST requests.

## Integration plan — distinct from the directory

**Do not automatically add 20 more catalog personas** just to make a company look large. New operational departments require real functional workflows, bounded model/tool adapters, reliable execution and verification evidence. The most useful next product increments are:

1. Build a **Design Studio** capability based on genuine design-review artifacts and a measurable UX audit, then review whether specialist catalog IDs are justified. Designer jobs should use existing owner-bound tool policy and independent QA.
2. Formally separate a **CEO-level advisory briefing/priority** function from `executive-planner`; the role must never bypass owner approval or make deployments/billing/security decisions autonomously.
3. Expose the read-only company directory to the real Syntra desktop dashboard, with status labels **Configured / Synthetic-only / Operationally verified / Missing**, grounded in actual evidence.
4. Complete existing Stage45 security blockers, local model end-to-end experience, real safe tool integrations and carefully scoped owner-approval before any production or physical PC pilot.

This stage adds **one real read-only feature** while protecting the existing governance boundaries. The directory cannot certify independent agent execution, owner authentication or security operations. Production and physical Windows-owner-PC pilot remain **NO_GO**, and the current draft stack remains unmerged.

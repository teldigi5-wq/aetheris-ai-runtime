# S0-E6C — Adversarial real direct-port denial regression

**29 October 2026 — Stage45 NO_GO, unmerged draft, no release or customer access.**

## Baseline and reason for the stage

This candidate starts from [S0-E6B draft PR #223](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/223) at verified head `d7411b797557060e452a7a8ef34470d96a20268e` with [44/44 JUnit tests green](https://github.com/teldigi5-wq/aetheris-ai-runtime/actions/runs/40886333962).

S0-E6B installed a **real** Spring-scanned deny-only `RuntimeRouteAdmissionPolicy` classification mechanism that has no permit decision; it is NOT wired into the inbound HTTP request path. S0-E6A installed a production Spring-scanned operator-pinned RS256 *verification capability*, but without inbound HTTP authorization. S0-E4 introduced a real unconditional `/api` and `/actuator` 404 quarantine; the original Task/Mission HTTP 404 filter still has higher precedence. These features are all **draft only** and not part of the certified Runtime main or Platform main.

## New independent test

`RuntimeRealDirectPortDenialRegressionTest` starts the **actual full `AetherisOrchestratorApplication`** on `127.0.0.1` with an ephemeral real HTTP port and in-memory H2 `create-drop` JPA. The test imports no synthetic auth filters or test controller registrations and executes no model, cloud, real OS automation, external database or user action.

It exercises **seven HTTP methods** (GET, HEAD, POST, PUT, PATCH, DELETE, OPTIONS) across eleven real/quarantined paths including Tasks, Missions, per-task events, model execution, connectors, live events, Agents and Actuator. It requires HTTP 404 with no-store/nosniff headers for ordinary paths; 77 method-based negative requests must pass and task row counts must remain unchanged. Additional real requests attempt unauthorized caller/forwarded client cert, UID, owner, project, method override, proxy and rewrite headers both with and without forged bearer values. Alternate encoded URI, semicolon path parameter and path-normalization probes must fail closed without successful or redirecting responses, allowing 400/403/404 where Tomcat itself rejects syntax.

The test also verifies the original highest-priority `OwnerlessTaskMissionHttpFilter`, the second real `RuntimeUncertifiedApiHttpFilter`, the real pinned Identity bean unconfigured by default, and the S0-E6B route classification still `QUARANTINED`.

CI runs this test alongside all predecessors: the 403-entry MVC policy inventory, H2 full-application JWT and route tests, earlier Tomcat JWT and Task/SSE lockdown regression tests. An immutable SHA guard also checks no production endpoint grant, no changed filter source or route permission.

## Precisely what this does NOT prove

- This is **real localhost direct-port HTTP testing**, but not an independently deployed gateway/runtime channel or remotely reachable production service. It does **NOT** install a production JWT HTTP filter; the real blanket quarantine denies even legitimate signed clients. A full resource-server with revocation, workload identity and route-specific membership/ownership remains absent.
- This is a bounded regression corpus, not exhaustive fuzzing of every servlet, WebSocket, reverse-proxy, Actuator separate handler mapping or abnormal HTTP parser path. No automatic bypass is inferred from an unexpected status; CI failures demand review.
- The production schema still uses `spring.jpa.hibernate.ddl-auto=update` while tests use disposable H2. Real PostgreSQL ownership, zero-trust service accounts and migration/restore proof remain absent.
- The earlier 403-entry matrix represents method/route records, not 403 authorization-certified endpoints or 403 running autonomous agents. 26 configured specialist identities live in the canonical catalog; 13 proposed companies and 100 proposed organizational divisions do not represent operating entities.
- **E6 remains IN PROGRESS**. Six planned engineering security packages E6–E11 remain including E6, and **all nine Stage45 release gates remain uncertified**. Platform's historical specification 34/34 completion is not SaaS production status. Broader enterprise product capability roadmap E3–E9 and physical Execution Phase A are independent.

This is test-only. The availability-breaking E4 default-deny filter remains unmerged. Do not deploy, merge protected main, change runtime certification pin, unblock Task/Mission/SSE, enable unrestricted host automation, live money or production agent/customer access. Runtime issue #23 and Platform issue #251 remain OPEN; owner-PC verification is `BLOCKED_PENDING_HARDWARE`.

**Stage45 NO_GO. Only exact-head completed CI validates E6C.**

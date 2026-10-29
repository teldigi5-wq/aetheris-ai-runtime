# S0-E4 — real production-component quarantine for uncertified Runtime HTTP endpoints

**29 October 2026 — Stage45 NO_GO. Review-only stacked draft. NO MAIN MERGE OR DEPLOYMENT.**

## Exact starting point

Built on [draft Runtime PR #219](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/219) exact green head `4aaf6eac4dd6f25d6558927f2eb5205033d1e1ec`, [CI #40879012828](https://github.com/teldigi5-wq/aetheris-ai-runtime/actions/runs/40879012828) **27/27 tests passed**. #219 successfully started the complete Orchestrator context and real H2 JPA but its extra deny-all test filter was ONLY registered inside JUnit. Other genuine controllers could still be accessed on a normal startup.

## New real source-level security change

- `RuntimeUncertifiedApiHttpFilter` is an actual **unconditional `@Component`** Spring servlet `OncePerRequestFilter` ordered at `HIGHEST_PRECEDENCE+1`, strictly **after** the existing production `OwnerlessTaskMissionHttpFilter` at HIGHEST_PRECEDENCE. It is **not gated by an environment variable, security profile, JWT claim, owner header, API key, browser cookie or opt-in switch**.
- It checks the mapped servlet path **and** raw context-relative URI with exact path-segment matching, denying every `/api` and `/actuator` URI and nested routes, including paths with semicolon parameters, with HTTP 404, `Cache-Control: no-store` and `X-Content-Type-Options: nosniff`. The original stricter task/mission route blockade keeps running first. Unrelated paths are not intentionally governed by this targeted API/actuator containment: a real path audit checks for actual business MVC routes that escape those prefixes.
- **Availability tradeoff is intentional and release-blocking**: this makes legitimate client API endpoints UNAVAILABLE, including Agents, Connectors, Model Execute, Hosts, live streams, operational endpoints and actuator. This must not be merged or deployed without explicit owner consent, operational planning and a secure, independently reviewed alternative. It is a **quarantine**, not authentication, and cannot restore any API.
- No JWT configuration or bearer parsing is installed by this component. The existing `RuntimePinnedHttpBearerGate` stays **unregistered in production**, and the S0-E3 test-only probe remains solely a test artifact. This change is safer than pretending that verification of a signed token alone solves tenant membership, object ownership, session revocation and authorization of sensitive controllers.

## Verification

`RuntimeProductionApiQuarantineTest` starts the **actual `AetherisOrchestratorApplication`** with in-memory H2 (no production DB), JPA `create-drop` in test only, dispatcher/cloud/remote execution/workspace writes disabled, and real HTTP on 127.0.0.1 random TCP port. It does **not import JUnit-only security registration**. The test asserts that Spring discovers and servlet-registers both genuine security components and preserves earliest Task/Mission filter order. It dynamically reads the **real Spring MVC `RequestMappingHandlerMapping`**, rejects nonframework business controller paths outside the protected `/api/` or `/actuator/` surface and requires >=30 production handler methods. Future routes outside the quarantine MUST fail CI for explicit review.

Actual localhost HTTP requests to Task, Mission, history and SSE, plus other real controllers (Agents, Connectors, Models, Stage operations, overview and Actuator) must all fail 404 regardless of forged bearer/forwarded owner headers, method or target. H2 TaskRepository row count before/after write attempts must be unchanged. Regression suite also covers the full application test-only JWT fixture #219, separate Boot #218, real embedded Tomcat #217, pinned RS256 verifier and existing HTTP/global SSE lockdown.

## Critical security limits and subsequent roadmap

1. **No safe customer API restoration:** The present real quarantine intentionally blocks every API. The next separately reviewed step needs a complete route-by-route access policy with server-authenticated identity, role/scope ceilings, verified tenant/project membership/ownership, authenticated workload mTLS, and revocation-aware sessions. It must be tested against malicious direct-port callers and browsers without weakening the permanent ownerless Task/Mission HTTP 404 prematurely.
2. **Not complete HTTP security:** An unknown handler or resource outside `/api` and `/actuator` might still be reachable; dynamic MVC mapping assertions cover *registered Spring MVC business mappings*, not arbitrary static resources, servlet/filter registrations, WebSockets, unexpected container dispatch or framework errors. These need independent future reviews.
3. **No production schema trust:** Tests use disposable H2, not actual production PostgreSQL. The application still uses `spring.jpa.hibernate.ddl-auto=update`; candidate Flyway migrations, real ownership FKs and a scalable database authorization/credential model are absent from protected main.
4. **No release approval:** Protect both GitHub main branches; stacked PRs remain draft/unmerged, Runtime issue #23 and Platform issue #251 OPEN, nine independent Stage45 attestations NOT CERTIFIED, physical owner PC `BLOCKED_PENDING_HARDWARE`.

**Stage45 NO_GO.** This stage does not create live agents, real credentials, customer traffic, production listener, deployed authenticated routes, SSE subscriptions, app/web front-end availability, real security certification or a protected-main merge. **CI status may only be claimed for its exact completed head SHA after inspecting run logs.**

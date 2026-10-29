# Runtime S0-E6B — deny-only route admission policy scaffold

**29 October 2026 — Stage45 NO_GO. Draft review-only, no protected-main merge, real customer access or production deployment.**

## Parent verified evidence

Stacked exactly on [Runtime draft #222](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/222) @ `3451349852c7ad56c99bb591b74ab4175c4d5bd0`, [exact-head CI #40884256534](https://github.com/teldigi5-wq/aetheris-ai-runtime/actions/runs/40884256534), **40/40 tests successful**. S0-E6A created a real Spring Identity signature verifier component that refuses absent/partial/malformed operator pinning but does not yet enforce signed authentication on real inbound HTTP. Existing S0-E4 real runtime `/api` and `/actuator` 404 quarantine remains; Task/Mission HTTP 404 runs first. S0-E5 enumerated 403 method/route entries, all classified deny-only.

## New production-scanned policy engine (not an HTTP grant)

`RuntimeRouteAdmissionPolicy` is a real Spring `@Component` with an explicitly finite policy result `Verdict.QUARANTINED`. It is **not** a servlet filter, endpoint, Spring Security config, route grant, user account/role permission table or external authority. No `ALLOW` state exists.

`assess(VerifiedPrincipal, requestMethod, requestPath)` classifies paths by exact leading segment into `TASK_MISSION`, `EVENT_REPLAY`, `CONNECTOR_SECRET`, `EXECUTION_CONTROL`, `OPERATOR_MONITORING`, `OTHER_API`, or `UNREVIEWED`. The returned immutable `ReviewDecision` records the conservative missing evidence needed for a future separately approved authorization: live session, authenticated Gateway/workload, tenant membership, durable owner, impactful action approval or private monitoring. Missing verified user adds the separate `VERIFIED_USER` precondition. A valid signed user—including an `ADMIN` with `orchestrator:write`—does not remove object membership or session/workload requirements and **can never convert this verdict to a grant**.

The function currently does not trust method claims or supplied paths as independently canonical, and does not open any routes. Its static categories are **review metadata** rather than an endorsed permission scheme; the pending E6C real HTTP resource server will still require independently authenticated requests and route-specific authorization checks, and might reject more strongly.

## Tests

`RuntimeRouteAdmissionPolicyTest` loads the **actual** production `AetherisOrchestratorApplication` and Spring MVC mapping graph on disposable H2 JPA. It walks all actual business mappings and proves the production-scanned policy yields `QUARANTINED` with nonempty missing identity/session/workload evidence for every route, without any unreviewed path escapes. It also verifies an ephemeral real RS256-signed administrator principal remains quarantined for task/mission/history, event replay, credential/connector, model execution, actuator and unreviewed paths. Negative classification tests reject prefix-confusable paths. No production HTTP filter or Task/Mission/actuator quarantine source is changed.

Dedicated SHA-pinned GitHub Actions runs this test with the predecessor full Boot/real HTTP/Tomcat/RS256/token/route-inventory/SSE-lockdown tests, asserts no permissive verdict or new customer filter wiring, and requires this **Stage45 NO_GO** document.

## Remaining work and deployment limitations

- **E6 is NOT complete.** Actual production HTTP requests are still unconditionally quarantined; this policy is not called in the request filter path. Its possible future use is a reviewed route-admission authorizer, not a blanket allowlist. The attempted direct HTTP integration file was **not accepted by the repository write tool**, so no claim of such an implementation is made in this PR.
- **E7–E11 remain** for live Identity session revocation and key rotation, Gateway-to-Runtime authenticated mTLS/workload identity, production ownership-aware PostgreSQL/Flyway schema and least privilege (normal runtime still uses `spring.jpa.hibernate.ddl-auto=update`), scoped Task/Mission history and SSE with two-user negative tests, and operational release evidence plus physical owner-PC acceptance.
- **Six planned engineering packages E6–E11 remain, including the partially completed E6.** The separate **9 Stage45 release gates remain uncertified (0/9)**. Runtime security [issue #23](https://github.com/teldigi5-wq/aetheris-ai-runtime/issues/23) and Platform [issue #251](https://github.com/teldigi5-wq/aetheris-platform/issues/251) remain OPEN.
- Existing S0-E4 real blanket HTTP quarantine would cause an **availability outage** if deployed. Do not merge or deploy without an explicitly reviewed safe substitute and rollback. Both protected main branches, deployed runtime pin and current live endpoints remain untouched.
- No user data, real credentials, live KeyStore/JWKS calls, secret rotations, customer API grants or cloud costs are introduced.

**Stage45 NO_GO. CI status must be established on this branch's exact completed SHA; parent CI is not its proof.**

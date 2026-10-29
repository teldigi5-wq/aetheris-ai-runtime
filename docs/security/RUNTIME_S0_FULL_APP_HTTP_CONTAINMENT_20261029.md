# S0-E3 — genuine full Orchestrator context under disposable H2, 29 October 2026

**Stage45 NO_GO — only a DRAFT CI test, not a production filter, migration or deployment.**

## Exact parent

Based on [Runtime PR #218](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/218), verified exact head `d8308324c8037c7731f6863121cd80c2557cd1fb` with [22/22 Java tests passing](https://github.com/teldigi5-wq/aetheris-ai-runtime/actions/runs/40875922487). PR #218 only booted a **minimal** Spring Boot app, with no actual controller or JPA bean discovery; prior [#217](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/217) booted independent Tomcat. Neither proved a full Aetheris Runtime context.

## Scope of new independent experiment

`RuntimeFullAppHttpContainmentTest` uses Spring Boot `@SpringBootTest(webEnvironment=RANDOM_PORT, classes=AetherisOrchestratorApplication.class)`. Unlike #218, it actually scans and initializes **the production Orchestrator Spring Boot application**, actual Task/Mission/Agent controllers, TaskService and repository, scheduled service bean definitions and the configured JPA stack. It asserts at least 30 actual controller types from Spring RequestMappingHandlerMapping, rather than a mocked/stubbed set.

In this JUnit only, `src/test/resources/application.properties` is overridden with **in-memory H2 PostgreSQL-compatibility mode**, `spring.jpa.hibernate.ddl-auto=create-drop`, localhost 127.0.0.1 ephemeral TCP port, disabled dispatcher, disabled cloud provider, host simulation-only and disabled workspace writes/commands. The experiment must never connect to a real customer database or permit a real agent to run.

The **production-source `OwnerlessTaskMissionHttpFilter`** is already Spring `@Component` with `@Order(HIGHEST_PRECEDENCE)` and is NOT replaced, excluded, weakened or bypassed. A second `FilterRegistrationBean` containing already-reviewed `RuntimePinnedHttpBearerGate` is defined **only inside the JUnit nested `@TestConfiguration`**, at order HIGHEST_PRECEDENCE+1; this registration does **not** exist on any normal application boot. The single `GET /__s0/auth-probe` endpoint is another test-only class, with 204 and no customer data after successful pinned RS256 JWT with read scope. The JWT verifier rejects forged signature, issuer, audience, KID, expiry and wrong scope as in earlier stages.

Real loopback `java.net.http.HttpClient` calls test multiple **genuine** Runtime controller URLs including `/api/orchestrator/agents`, overview, model execution and actuator health. They must fail 404 with or without a valid token under this **test-only deny-other-routes overlay**. Real Task/Mission root/item/history/events/creation requests must receive the original early 404 and safe cache headers. Thus the test proves actual Spring context load plus a possible fail-closed filter ordering under disposable test conditions, NOT real production direct-port protection.

## Critical security findings and unfulfilled boundaries

- The genuine production application has a wide controller surface beyond Task/Mission. Inspect individual endpoints and register a **production-approved** server-side authentication/authorization boundary before claiming direct-port security. **A test-only deny filter does not protect the real running application.**
- The real runtime currently lacks a Spring-managed `RuntimePinnedHttpBearerGate` or equivalent user resource-server. That component is deliberately not auto-registered; the only test allow route `/__s0/auth-probe` is not a product route.
- The H2 test database uses synthetic `ddl-auto=create-drop` and does **not** validate production PostgreSQL schema, JPA ownership migration or versioned Flyway adoption. **`spring.jpa.hibernate.ddl-auto=update` remains the production configuration**, explicitly blocking release readiness.
- Enabling real tasks/missions requires genuine Identity account/session revocation, workload-to-workload authentication (mTLS/SPIFFE), reliably scoped tenant/project membership, JPA owner FKs and native database least privilege or a trustworthy connection broker. The earlier separate-login RLS tests were synthetic and not a production credential model. Exposing historical ownerless records, SSE, replay, mission attachment or arbitrary agent execution remains prohibited.
- The complete application's schedulers, readiness, container/secret configs, owner PC and network access require independent physical checks. This disposable test is not a full threat model, external attack simulation or production performance benchmark.
- All nine Stage45 independent release attestation gates remain **NOT CERTIFIED**; hardware owner acceptance `BLOCKED_PENDING_HARDWARE`. Runtime security issue #23 and Platform #251 OPEN.

**Stage45 NO_GO.** No protected-main merge, no customer data, no production deployment, no service secret, no live model/cloud execution, no Task/Mission/SSE unlocking, and no cross-repository runtime certification pin update. Test evidence is accepted only after new exact-head CI fully passes; parent CI does not prove this stage.

Next dependency: harden the genuine direct-port servlet security registration in a separately reviewed *deny-by-default*, operator-key-pinned, controlled production configuration that cannot be bypassed with unsigned headers and continues to force Task/Mission 404. That is not delivered by this synthetic CI-only overlay.

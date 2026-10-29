# S0-E2 — real minimal Spring Boot HTTP authentication containment, 29 October 2026

**Stage45 NO_GO. Draft/review-only, unmerged. No production runtime filter registration.**

## Verified parent

This branch is stacked on [Runtime PR #217](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/217) exact verified head `f23e3f9514a516e0a06d4806d0bf13114a93b684`, successful [CI #40874728787](https://github.com/teldigi5-wq/aetheris-ai-runtime/actions/runs/40874728787), **17/17 Java tests**, including real embedded Tomcat loopback HTTP. Its `RuntimePinnedHttpBearerGate` is a standalone, not-Spring-registered filter limited to synthetic `GET /__s0/auth-probe`. The production Task/Mission `OwnerlessTaskMissionHttpFilter` always denies client Task/Mission routes with HTTP 404.

## New S0-E2 isolated Spring Boot test server

`RuntimePinnedSpringBootHttpIntegrationTest` launches a **real Spring Boot 3.3.3 embedded servlet web server on 127.0.0.1 with RANDOM_PORT** via `@SpringBootTest(webEnvironment=RANDOM_PORT)`. It uses a **deliberately minimal test-only `@SpringBootConfiguration`** with selected auto-configuration and explicitly excludes DataSource/Hibernate JPA. It **does not boot or import `AetherisOrchestratorApplication`, scan its controllers/workers, or connect to a production database**. This is a genuine Spring Boot HTTP integration proof, but **not** a full production-app direct-port verification.

The test-only application registers **the exact existing production `OwnerlessTaskMissionHttpFilter` first**, then a synthetic test-only `RuntimePinnedHttpBearerGate` through explicit `FilterRegistrationBean` ordering. The permit route `/__s0/auth-probe` is served by a synthetic `@RestController` declared *inside the JUnit class only*; successful signed `orchestrator:read` produces a blank 204. Synthetic traps are mapped to Task and Mission GET/POST routes, with explicit counters detecting any unwanted handler dispatch. No new test endpoint exists on real Aetheris runtime routes or in main Java source.

The Java `HttpClient` issues real TCP HTTP requests to 127.0.0.1 at the Spring Boot assigned port. Test requests use ephemeral RSA 2048-bit signatures, pin the key ID, issuer, audience and algorithm, reject missing/malformed/wrong-key/wrong-key-ID/wrong-issuer/wrong-audience/multi-audience/expired bearer; reject invalid read scope; reject spoofed `X-Aetheris-Owner`, `X-Forwarded-Client-Cert` and `X-Aetheris-Verified-Uid` as authentication. Even a correctly signed identity cannot enter a Task, Mission, history or task-create handler: it receives early HTTP 404/no-store/nosniff and **trap counter remains zero**. Other paths, including actuator health, also remain closed under this synthetic gate.

CI runs this new real Spring Boot test alongside the predecessor isolated Tomcat, token verifier, existing HTTP lockdown and event-lockdown suites, after immutable SHA/source checks. It asserts no new production Spring security registration, no Task HTTP authorization, no SSE restoration and Stage45 NO_GO.

## Still blocking an actual Runtime resource server

- The genuine **full `AetherisOrchestratorApplication` context, datasource and direct-port listener** have not been started/secured by this test. Test-only `FilterRegistrationBean` wiring is not installed in its application. A real non-customer route requires a separate reviewed production-shape filter registration that keeps task/mission API unconditionally denied.
- The current runtime has many existing controllers whose own authorization needs independent review. This limited synthetic allowlist must never be mistaken for a complete service-level security architecture; do **not** widen it or enable real APIs by generalizing the synthetic gate.
- Real Identity account/session revocation, key rotation/JWKS governance, per-request target audience and workload mTLS/SPIFFE trust, task ownership on actual persistent JPA/Flyway schema, scalable database role/RLS broker, normalized path/canonicalization, replay-resistant SSE, Mission/Task writes, and production rollouts are still outstanding.
- `spring.jpa.hibernate.ddl-auto=update` remains an explicit migration blocker. The platform remains separately represented by draft #346, not certified full Gateway→Runtime trust.
- Runtime security issue #23 and Platform security issue #251 remain open; protected `main` branches and the certified Runtime pin must stay unchanged. All nine Stage45 release attestations remain NOT CERTIFIED and physical owner PC acceptance `BLOCKED_PENDING_HARDWARE`.

**Stage45 NO_GO. No production deployment, real customer access, cloud/paid account provisioning, merge, runtime pin promotion, database migration, service secret or signed owner privilege promotion.**

Next engineering dependency after this test: independently review the actual production Spring Boot context and design a non-customer direct-port security boundary with operator-pinned key, fail-closed startup, workload authentication, session revocation and route allowlisting; task/mission HTTP 404 remains.

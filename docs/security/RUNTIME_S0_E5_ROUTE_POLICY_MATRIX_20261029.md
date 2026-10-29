# Runtime S0-E5 — deny-first route-by-route security review matrix

**29 October 2026 — Stage45 NO_GO. Draft review only, no customer API or production deployment.**

## Verified parent and scope

Base is the verified [Runtime draft PR #220](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/220) at `3678d2f5f6b16eabffbc1ba1dffc9072f1f00693`, [CI #40880457830](https://github.com/teldigi5-wq/aetheris-ai-runtime/actions/runs/40880457830) successful with **32/32 Java tests**. PR #220 adds a **real Spring-managed, unconditional** HTTP 404 quarantine for all `/api` and `/actuator` routes, behind the original Task/Mission ownerless 404 filter. If deployed, that quarantine would make current legitimate APIs and actuator unavailable. Neither that PR nor this successor authorizes deployment or protected-main merge.

## S0-E5 proof: enumerate every real MVC endpoint instead of guessing controller routes

`RuntimeControllerPolicyInventoryTest` boots the **actual `AetherisOrchestratorApplication`** component-scanned Spring Boot service and production security filter beans, using only synthetic in-memory H2/JPA and no live network, cloud or owner PC. It directly introspects the real Spring MVC `RequestMappingHandlerMapping` rather than a hand-written list of URLs. For each registered nonframework business method it captures HTTP verb (or ANY), route pattern, concrete controller class/method, an explicit conservative risk group, current HTTP enforcement `DENY_404` and **prerequisites for a future independently reviewed grant**. It stops CI if a route escapes the quarantine or lacks an explicit review class and prerequisites. The check is intended to detect new controller paths added in later PRs.

GitHub Actions executes this dynamic inventory test alongside the existing real full-app HTTP quarantine, full Spring Boot, Tomcat, signed Identity JWT and global-SSE lockdown regression suites. The matrix is written as `orchestrator-service/target/s0-e5-route-audit/runtime-handler-policy-matrix.tsv`, checked for completeness, no ALLOW/PERMIT/UNLOCKED decisions and hashed SHA-256. A **private** Actions artifact of this review inventory is retained for 7 days to support reviewer inspection; the matrix contains code route names and method identifiers, not tokens, live user records or customer payloads.

### Review class and minimum prerequisites

| Current review group | What reviewers MUST prove before any actual exposure |
|---|---|
| `TASK_MISSION_OWNER_BOUNDARY` | Pinned signed user identity; live nonrevoked session; canonical tenant/project; durable task and mission owner FKs; two-identity negative tests; scoped history and SSE |
| `LIVE_EVENT_AND_REPLAY` | No global task feed; bounded per-tenant/user event replay; publish/subscribe owner scope; multi-instance isolation and disconnect/revoke |
| `EXTERNAL_CREDENTIAL_AND_CONNECTOR` | Server-side trusted caller and workload; provider secret ACL; capability limits; independent owner approval for impactful operations; redacted audit |
| `EXECUTION_AND_CONTROL` | Workload trust, scoped user identity, explicit action approval, no shell execution from untrusted requests, quotas and cancellation |
| `OTHER_UNCERTIFIED_API` | Named controller owner and reviewer; independently enforced signed principal; route role/scope; object-level ownership and negative access tests |
| `OPERATOR_MONITORING` | Private authenticated health/readiness probes, protected metrics and unauthenticated error boundary; documented rollback |

**Every entry stays `DENY_404` now.** These are classification and release prerequisites, NOT grants and not a verified permission model. A title such as "read" does not prove a route is safe; current Runtime scope tokens cannot replace task/project ownership.

The dynamic inventory covers **Spring MVC `RequestMappingHandlerMapping` routes**, excluding the framework's `BasicErrorController` mapping. It does not prove protection for every raw servlet registration, static resource, WebSocket upgrade, health endpoint registered through a separate Actuator handler mapping, container protocol, proxy normalization or downstream service. A separate audit must examine them before Stage45 release gates can close. Paths under `/actuator` are denied by the existing real filter but may be managed via a separate actuator handler mapping; an available `/actuator/health` probe is intentionally not guaranteed, so a production rollout would currently need an alternative safe readiness strategy.

## Review-only engineering sequence after S0-E5

Remaining planned S0 packages: **E6** independently registered direct-port user authentication and route authority; **E7** revocation/key rotation; **E8** workload identity/mTLS; **E9** transactional owner+registry migrations and production tenant data authority; **E10** task/mission operations plus per-user SSE; **E11** operational rollout, adversarial cross-instance verification and release evidence. This is a **6-package planning estimate**, not proof of completion. **9 independent Stage45 release gates remain uncertified**, and the broader Aetheris product roadmap is separate.

## Mandatory no-go

- Keep all Task/Mission HTTP 404, no global SSE or unscoped replay.
- Do not merge or deploy this stacked candidate, expose endpoints, change runtime pin, add secrets or create customer roles.
- Do not call the proposed RLS-per-login prototype a production tenant isolation design.
- `spring.jpa.hibernate.ddl-auto=update` remains on normal runtime; only disposable tests use `create-drop`.
- Runtime [issue #23](https://github.com/teldigi5-wq/aetheris-ai-runtime/issues/23), Platform [issue #251](https://github.com/teldigi5-wq/aetheris-platform/issues/251) remain open.
- Physical owner-PC acceptance remains `BLOCKED_PENDING_HARDWARE`; all **9/9 Stage45 gates remain pending**, production **Stage45 NO_GO**.

This file describes expected tests. Only the exact completed GitHub Actions run on the PR's final immutable SHA can establish that the new matrix actually passed.

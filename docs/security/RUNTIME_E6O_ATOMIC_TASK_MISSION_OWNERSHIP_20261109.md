# S0-E6O — signed-session, tenant, project, owner and workload-fenced resource read

**9 November 2026 | Review-only, unregistered | Stage 45 NO_GO | No product Task/Mission API activation**

## Exact verified lineage

This is a new child of **Runtime [draft PR #297](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/297)** at `816dccdaffb7e5dc1e0a86707dc3829af2ed3b70`. Prior Runtime E6L already proved actual pinned Platform Identity RS256 JWT and JTI registration against real Runtime loopback HTTP and the function-only S0-E6J PostgreSQL16 authority. Platform [draft PR #417](https://github.com/teldigi5-wq/aetheris-platform/pull/417) at `a4c4de273c6cfb29a4a0feee7b3453660ae7b1cf` independently proved a versioned review-only PostgreSQL migration journal and certificate-bound client authentication (E6N). That certificate proof is not automatically installed in Runtime and must not be described as production end-to-end TLS.

The original E6L code already rechecks session legitimacy before HTTP dispatch. **A session preflight is not task/mission ownership.** Prior read-only `TaskEntity` owner columns and scoped `TaskRepository` queries explicitly disclaim live authorization, and the real `OwnerlessTaskMissionHttpFilter` still unconditionally sends 404 for protected product routes. This change does not modify or bypass those classes.

## What S0-E6O actually implements

- `orchestrator-service/src/test/resources/security/e6o_owned_resource_pg16.sql` creates a **disposable synthetic test schema** `e6o_review` with `resource_memberships`, `workload_fences`, `bound_resources`, one non-LOGIN minimum-privilege function owner `e6o_exec_owner` and one non-LOGIN resource reader `e6o_object_reader`. It grants no raw resource-table SELECT/UPDATE to the Runtime reader.
- The sole allowed read boundary is `e6o_review.read_bound_resource(?,?,?,?,?,?,?,?,?)`, an isolated `SECURITY DEFINER` function with schema-qualified SQL and protected `search_path`. It takes only cryptographically verified issuer, kid, JTI, UID, issued-at, requested UUID, resource kind, tenant key and project key. It invokes the existing pinned **real Platform** `e6j_runtime_auth.check_signed_session` function (itself checking active session, signer, epoch, expiration and authoritative primary), then in **the same transaction** joins exact resource owner, live user/project/tenant membership and active workload fence. `FOR SHARE` locks session/signer/epoch, membership, resource and fence rows until the caller commits. Missing/ownerless/revoked/mismatched rows yield no data. The SQL returns the small permitted label only, not a reusable grant or direct table permissions.
- `orchestrator-service/src/main/java/io/aetheris/orchestrator/security/RuntimeE6OScopedResourceRead.java` is **unannotated, unregistered** Java. It consumes the existing verifier's sealed, actual RS256 `VerifiedPrincipal`, checks the `orchestrator:read` ceiling, extracts verified JTI facts and issues a single parameterized call to the function from a fresh, restricted, non-read-only PostgreSQL READ COMMITTED transaction with a three-second timeout. It cannot invent a verified principal from request headers, cannot SELECT the task table directly and fails closed on database errors. It does not read resource data in a second unguarded query.
- `RuntimeE6OAtomicOwnerResourceHttpPg16IT` starts real Spring Boot/Tomcat on a random **127.0.0.1** port in a controlled CI runner. It attaches the unchanged `RuntimeSignedIdentityHttpFilter` only to two test-only read probes. Original production Task/Mission HTTP routes continue to be blocked by `OwnerlessTaskMissionHttpFilter`. Actual Platform tokens are generated from **immutable Platform PR #415** at `53d55135181ef89d3ff402f46b835d4b3a0f8ac5`, via the existing source-pinned `S0E6LPlatformSessionProducerTest`. The E6J SQL is verified against its exact git blob `fcf0e61c24ab89b61f10b6d2e454742a4b0420b0`. No mock-generated bearer replaces this actual Platform producer.
- The SQL review owner only inherits `e6j_reader` EXECUTE on the existing signed-session checker. A distinct network-authenticated Runtime test login `e6l_reader_login` obtains only the two function-reader permissions, not table ownership or issuer/revoker capabilities. The SQL and roles are removed in CI cleanup.

## Seven actual real-HTTP / PostgreSQL security tests

1. Two separately persisted Platform Identity principals have separate registered JTI sessions and can read *only* their bound synthetic Task or Mission label. Real Task/Mission API paths still return **404**.
2. Cross-user, cross-tenant, cross-project, wrong resource kind, historic ownerless record, unregistered signed JWT, real legacy Platform HMAC JWT, forged forwarded identity header, malformed and absent bearer are rejected (404/401).
3. Independent Runtime database reader cannot directly SELECT or UPDATE synthetic resource tables. The permitted function-only read still succeeds.
4. Disabling one resource's workload fence immediately denies that user's resource while another user's owned Mission remains available. A disposable test admin later restores the fixture; real production users are **not** granted a fence-reactivation path.
5. Removing the per-resource authority function yields a generic HTTP **503**, not an unguarded TaskRepository fallback. Restoring the synthetic function makes the test-only read succeed.
6. A separately opened PostgreSQL transaction revokes live membership and holds the update lock. The concurrent HTTP read **times out with 503**, never revealing stale resource data. After the revocation commits, the same signed user gets 404, while the other user's resource remains readable.
7. Revoking the remaining user's signed Platform JTI through the previously scoped real revoker login makes an otherwise owned Mission unreadable via the real HTTP filter (401).

The test names and endpoint labels are intentionally synthetic. The SQL does **not** migrate the real `aetheris_tasks` or Mission tables, and the results do not establish task-write, delete, agent execution, owner reassignment, cross-process SSE, or historical ownerless data migration safety.

## Acceptance proof

**Initial tested implementation head** `49ac5954fb2bc29e8afc7dc0a6aed373c217732b`: [GitHub Actions #42596928489](https://github.com/teldigi5-wq/aetheris-ai-runtime/actions/runs/42596928489) **COMPLETED SUCCESS 2/2 jobs** on 9 November 2026:

- Actual immutable Platform RS256/JTI issuer + refresh/enrollment producer **1/1**, zero failure/error/skip.
- New real isolated Runtime socket + shared PostgreSQL16 authorization proof **7/7**, zero failure/error/skip.
- Full Runtime Java 21 orchestrator regression **377/377**, zero failure/error/skip, including five prior direct-port security tests.
- Actual CI SQL privileges, identity-source pinning, hard-404 quarantine and no-token-artifact cleanup enforced. No customer routes, deployed databases or secrets modified.

The runbook/README commits after this implementation SHA require their own final-head CI success before they can be called verified.

## Release blockers; next distinct milestone

**S0-E6P:** owner-approved production schema and client TLS design, verified owner/tenant/project binding writers and migrations for *actual* Task/Mission persistence (including ownerless-record quarantine, restore/rollback and owner reassignment rules), then atomic Task/Mission writes, transition guards, SSE and multi-node revocation. The current prototype validates a synthetic read function, not the production data model. It cannot be promoted by simply disabling the 404 filter.

Before any staged rollout, establish genuine Gateway→Identity→Runtime mTLS, real Identity session issuer/DB client permissions and protected secrets, external monotonic revocation/restore evidence, owner-PC physical acceptance and independently reviewed change/rollback controls. No automated release or approval is inferred.

**Platform Security #251 OPEN; Runtime Security #23 OPEN; Stage45 NO_GO; XERJ HOLD; owner-PC BLOCKED_PENDING_HARDWARE.** Historical Platform roadmap 34/34 COMPLETE; this is not a Stage35 or production security certification.

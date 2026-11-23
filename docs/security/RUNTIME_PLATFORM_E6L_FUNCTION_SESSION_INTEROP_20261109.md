# S0-E6L — actual Platform-issued token → Runtime direct-port HTTP → shared PostgreSQL16 session authority

**9 November 2026 | REVIEW-ONLY, NO CUSTOMER ROUTES, NO MERGE | Stage 45 NO_GO**

## Exact lineage: implementation, not repeated JWT tests

- Parent Runtime draft [PR #296](https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/296), immutable reviewed head `635095f54aec6fa0016bfa04bd8145f38827fdac`. Existing tests still prove independent signed direct-port HTTP and real PostgreSQL-backed session checks against **older, separate synthetic Runtime tables**. No rewrite or removal of that existing proof.
- Cross-repository issuer/ledger source: actual Platform [DRAFT #415](https://github.com/teldigi5-wq/aetheris-platform/pull/415), **immutable commit `53d55135181ef89d3ff402f46b835d4b3a0f8ac5`**, never floating `main`. Platform PR #415 already tests the real Java `RuntimeAudienceAtomicRegistryExchange` (single-use refresh rotation, persisted Identity account, RS256 JWT + exact JTI registration atomically) against the earlier Platform S0-E6J function-only PostgreSQL schema. The workflow additionally asserts pinned original PostgreSQL SQL Git blob `fcf0e61c24ab89b61f10b6d2e454742a4b0420b0`, and compiles original Identity classes from the exact Platform checkout.
- Both protected `main` branches remain separate and unchanged. No parent draft is merged, rebased or deployed.

## New Runtime capability

`RuntimeE6LFunctionSessionAuthority` is a new **unregistered**, non-Spring Java adapter implementing the existing `RuntimeSignedIdentityHttpFilter.SessionAuthority` after cryptographic JWT verification. Unlike the older `RuntimePg16ReviewSessionAuthority`, the new adapter uses **only**

`SELECT e6j_runtime_auth.check_signed_session(?,?,?,?,?)`

with the existing verifier's exact independently signed issuer, key ID, JTI, persisted UID and issued-at second. It opens a new explicit writable READ COMMITTED database transaction per synchronous request, sets a three-second SQL timeout, requires one non-null boolean function result, commits only after checking, and fails closed on all unavailable/ambiguous/read-only cases. It has no table joins, inserts, session claims from HTTP headers, cache, `SET ROLE`, Spring annotation or production configuration.

The required `DataSource` is independent of the Platform issuer and is **supplied by an operator/test host**. In CI it authenticates as a separate real PostgreSQL LOGIN account `e6l_reader_login` with only inherited `e6j_reader` function-execution privilege. The real PG16 negative tests prove it is denied raw table SELECT and UPDATE. A separate real `e6l_revoker_login` can execute only S0-E6J's revocation API. These are **synthetic CI passwords**, not provisioned real production account credentials, mTLS identity or secure secret custody.

## Source-identical Platform JWT → real Runtime HTTP end-to-end evidence

The exact pinned Platform code is compiled with ephemeral **test-only** `S0E6LPlatformSessionProducerTest` copied byte-identically from this repository into a disposable test directory. This helper uses **actual** Platform Java classes `RuntimeAudienceTokenIssuer`, `RuntimeAudienceAtomicRegistryExchange`, `RefreshTokenService` and PostgreSQL/JPA. It persists two independent synthetic Identity accounts, creates their principal-epoch rows, atomically rotates real one-use refresh credentials and enrolls two actual RS256/JTI sessions into the same disposable `e6j_runtime_auth` registry. It also creates a real Platform-signed but **unregistered** JWT and a real Platform legacy HMAC JWT for negative checks.

An ephemeral RSA private key exists only in the Java producer's memory; only the **public** verification PEM and short-lived synthetic JWT fixtures are written to a CI-owned, 0700 directory with files created **0600 from the outset**, never to the repository, PR comments, logs or Actions artifacts. The workflow unconditionally removes the files, cloned helper, disposable schema and synthetic DB LOGIN roles.

`RuntimeE6LPlatformIssuedSessionSocketJdbcIT` starts actual isolated Spring Boot/Tomcat on **127.0.0.1 random TCP port**, adds the **existing, unchanged real** RuntimeSignedIdentityHttpFilter only to two deliberately test-only read/write probe paths, and uses the **new** function-only PostgreSQL authority adapter with the separate restricted login. Seven mandatory non-skipped real HTTP/PostgreSQL tests verify:

1. Two independently stored Platform Identity users' **actual** RS256/JTI tokens are accepted and their correct signed persistent UIDs recovered. Task/Mission HTTP remains unconditional **404** even with valid bearer.
2. An RS256 token issued by Platform but **never registered** is 401; an actual Platform legacy HMAC token is 401; malformed/missing bearer, forged forwarded user and unauthenticated task calls fail.
3. The independently signed DEVELOPER token has the permitted read/write scope; API_CONSUMER is read-only (write probe **403**), with no implicit task ownership.
4. Runtime's actual independent PostgreSQL reader login cannot directly SELECT or UPDATE authority tables.
5. Removing the authority function causes **generic 503** (no synthetic/direct-table fallback), and restoring it recovers the endpoint.
6. A separate real revoker login permanently revokes one user; its previously valid signed token immediately loses HTTP authority (**401**) while the other user remains valid.
7. A *different real PostgreSQL connection* begins revocation of the second user and holds the row lock; the HTTP request cannot allow the stale ACTIVE row and returns **503** on bounded SQL wait. Once committed, further requests return **401**.

**This is real source-pinned Platform↔Runtime interoperability and real network-authenticated PostgreSQL roles in a disposable GitHub runner.** It does not prove TLS/mTLS, authenticated production Gateway, cross-AZ primary consistency, real owner workstation, or production Identity/Runtime deployment.

## Acceptance evidence

First successful integrated source/test checkpoint after correcting a mistaken fixed **HS256** assertion in the Platform legacy test fixture: **the real Platform JwtService selected HS384 for the synthetic key**. The correct negative contract is **reject every HS-family JWT**, never hardcode one HS algorithm as Platform production policy. This correction did **not** weaken RS256 verification.

[Actions #42579316336](https://github.com/teldigi5-wq/aetheris-ai-runtime/actions/runs/42579316336) completed **SUCCESS, 2/2 jobs**, at source checkpoint `c27003b7d3a30fe6da68645a14c566cb604a0dac`:

- **Actual Platform issuer + atomic enrolment**: **1/1 real JPA/PostgreSQL producer test**, no fail/error/skip.
- **Actual Runtime↔Platform HTTP + PG16**: **7/7 new tests**, zero failures/errors/skips, including different user privileges, separate login revocation, row-lock fail-closed, unregistered/legacy token rejection and hard-404 Task quarantine.
- **Full Runtime Java21 orchestrator regression**: **377/377**, zero failures/errors/skips, including prior 5/5 direct-port tests. Three separate Maven BUILD SUCCESS in total.
- Source-contract, foreign SHA/blob pinning, report XML no-skip and test-fixture teardown checks passed.

**Complete implementation + 0600 file hardening + README/doc review checkpoint:** `fcd705e28ce5116cc17ceab8d13f1bdd77445b67` was independently rerun by both [push Actions #42579790541](https://github.com/teldigi5-wq/aetheris-ai-runtime/actions/runs/42579790541) and [PR Actions #42579874481](https://github.com/teldigi5-wq/aetheris-ai-runtime/actions/runs/42579874481), each **COMPLETED SUCCESS, 2/2 jobs**, with unchanged 1 real Platform producer + 7 real Runtime HTTP/PG16 + 377 Runtime Java regression results, zero failures/errors/skips. This runbook evidence update itself is documentation-only; consult the PR's latest exact-head CI for any later commit.

## Remaining real release blockers — do not retry finished E6L

- **No production registry versioned Flyway migration or restore/rollback certification**; the contract is still review SQL in Platform test resources, not historical production session records.
- **No production network/service identity or secret/PKI custody:** workload mTLS Gateway→Identity→Runtime, key rotation/revocation, unique issuer/revoker grants and durable session lifecycle need independently approved infrastructure and real migration.
- Existing Platform `RuntimeAudienceWorkloadBoundExchange` remains on an earlier standalone exchange and is **NOT** connected to the opt-in enrolled wrapper as a production token endpoint. Even the new HTTP socket tests run on loopback **plain HTTP**, not operator-certificated TLS. Do not expose that test path.
- The HTTP filter is unregistered on Task/Mission, and `OwnerlessTaskMissionHttpFilter` is still unconditional 404. Historical ownerless records, per-tenant/project owner-fenced SQL authorization, workload claims and scoped SSE/history/replay remain blocked. **A successful preflight is NOT object ownership, and locks release before controller work.** The protected Task/Mission APIs and global SSE stay disabled.
- PostgreSQL verifier is callable through true limited LOGIN credentials in CI, but no multi-primary/split-brain, all-node revocation ordering, TLS-to-database, physical-device acceptance or independent release attestations have been proven.
- Platform S0-E6K and Runtime S0-E6L both remain stacked open DRAFT PRs; no production migration, deployment, branch promotion or release certification is implied.

## Next distinct milestone

**S0-E6M — production migration/custody and staged release safety design**: define an operator-approved *versioned* database migration, privilege/role ownership and network-bound client auth, rollout/rollback and backup/restore proofs, a safe Identity exchange call chain anchored to actual Gateway mTLS and provenance, cross-node eventual revocation / availability expectations, then resource-level owner/tenant/mission authorization and owner-PC hardware acceptance. Keep all changes in narrow review PRs and insist on exact-head tests; do not unlock Task/Mission or SSE until those hard gates pass.

**Runtime Security #23 OPEN · Platform #251 OPEN · Stage 45 NO_GO · XERJ HOLD · owner PC BLOCKED_PENDING_HARDWARE · Platform historic blueprint 34/34 complete; no Stage 35.**

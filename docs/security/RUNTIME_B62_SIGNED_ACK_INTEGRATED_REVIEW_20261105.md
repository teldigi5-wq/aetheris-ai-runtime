# Aetheris Runtime — integrated signed ACK / B62 review checkpoint (5 November 2026)

**Status:** TEST-ONLY / DRAFT CANDIDATE. Stage 45 NO_GO; XERJ HOLD.

This is a review-only, two-parent merge **on an isolated test branch**, not a merge of PRs or a write to protected main. It merges prior B62 replay/write-scope candidate `887d5dcb62ea17b04a17ff2a0c3f76af581d505e` with exact-three-CI-green Runtime PR #257 commit `ef2dd5162da91939ad1883f79324eb11465814f9` on common ancestor `6caa4df21ac7533a7d4455095d8dc544ea37b5d2`. The original PR #257 and #280 branch refs remain untouched. Current upstream may advance independently.

The merged consolidated security workflow must retain both the signed `orchestrator:write` check (read-only JTI cannot record synthetic effects) and the pinned closure-ack source guards. The real PostgreSQL test class must retain both the read-vs-write synthetic privilege denials and the separate signed closure ACK challenge, concurrent one-time consumption, committed replay denial, rolled-back retry, and expiry-rollback negative checks. The ACK SQL source and review notes are reused at exact upstream Git blob identity. Earlier eight PG16 replay scripts and physical standby proof remain unmodified from the CI-green candidate.

**Required exact-merge CI:** consolidated JWT/HTTP/PG16 tests; independently restricted PostgreSQL reader; physical standby/replay/fencing with disposable cleanup. An absent or failed check blocks review acceptance. Inspect actual CI logs and ensure none of the deliberately unsafe NO_GO demonstrations was removed. No new production endpoint, tenant schema, secret, deployment, model inference or real customer data is authorized.

**Unresolved:** authentic resource-owned ACK issuance/workload transport, global old-writer fencing, quorum and monotonic authority, durable remote revocation-before-publication, safe failback, HTTP/SSE authorization, Runtime issue #23, Platform issue #251 and nine Stage45 release attestations. A signed ACK or passing negative-case test is not production certification.

# B62 physical replay-ledger integration continuation — 5 November 2026
**Status:** DRAFT / CI LAB ONLY / NO_GO.

This test-branch-only merge joins the previous combined B62 signed-write-scope and ACK candidate `9b5eae6b9acc679723e56b188a9f93e1e2938d5d` with the upstream exact-3-CI-green Runtime physical-ACK-lab commit `c2f68de5039453323aea37a56555a94f391b3783` (source comparison ancestor `ef2dd5162da91939ad1883f79324eb11465814f9`). It is not a main merge, production rollout, live database migration, PR merge or authorization unlock. The original Runtime PR #257 and #280 references are unchanged.

Both inherited physical-standby review files were merged with nonoverlapping hunks. Preserve eight PG16 revocation/replay scripts, all prior fencing, lock-timeout and failback NO_GO guards, new one-time ACK ledger, restart-persistent replay denial, restricted writer inability to erase the ledger and the explicit privileged operator-erasure replay weakness. Preserve the corrected Python static assertion for at least five ACK-consumption calls; never delete test assertions to force CI green.

Required: run and inspect exact merged-commit physical PostgreSQL16, signed JWT/HTTP owner JDBC, and independent restricted-role workflows. The resource-local SQL fixture does not verify a signed ACK; the Java verifier is separate. An admin can still erase the local journal, and independent stale writer paths can still be active. No global quorum, secure credential-bound fencing, cross-resource atomic revocation, deployable Session/Task/SSE admission, real customer data, or production safety certification is established.

Stage45 NO_GO; nine independent attestations outstanding; Runtime issue #23 and Platform issue #251 remain open; XERJ HOLD. All changes review-only and unmerged.

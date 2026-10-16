# Stage 46.32 — Independently presented rollback checkpoint for synthetic paper replay

**OPEN DRAFT · UNMERGED · NONPRODUCTION · NO_GO**

## Prior real code

Stage46.31 recorded a synthetic-paper replay sequence and SHA-256 digest in a JPA database transaction, with row locking. Concurrent submission and fresh service construction were tested. However, restoring an older database would also restore its *older* sequence, and neither the DB row nor its self-contained JPA version establishes that rollback happened.

## New check, strictly bounded by evidence

- The Java `OfflinePaperRollbackCheckpointService` compares the **actual persisted JPA anchor** against a **separately supplied checkpoint** with a fixed synthetic account, versioned canonical ASCII payload, pinned highest sequence/digest, issuance/expiry, explicit NO_GO scope and domain-separated HMAC-SHA256.
- A fixed public CI fixture `risk-contracts/stage46-independent-replay-checkpoint-fixture.json` pins the existing Stage46.30 message #2 (`sequence=2` and its SHA-256 digest). That fixture is outside the mutable database, and Java uses real JPA `PESSIMISTIC_WRITE` row access in a dedicated transaction for a consistent read. **No database update occurs.**
- The service reports separate fail-closed classifications: `DB_ANCHOR_MISSING_OR_RESTORED`, `STALE_DB_RESTORE_DETECTED`, `EQUAL_SEQUENCE_DIFFERENT_DIGEST`, `UNANCHORED_DB_ADVANCE`, `CHECKPOINT_TAG_MISMATCH`, invalid/expired checkpoint, corrupt anchor and unavailable transaction. A match is called `FIXTURE_CHECKPOINT_MATCH`, and still returns `decision=NO_GO`, not a release or trading authorization.
- New Java Spring/JPA integration tests physically delete or replace a newer synthetic DB row with an earlier row to simulate restoration and prove that **the independently supplied fixed checkpoint catches the stale backup**. Other cases test tampered signatures, bad scope, expiry, divergence and verification that the review itself performs no DB writes.
- New Python `offline_replay_checkpoint.py` supplies a pure, non-networking parity validator of the same canonical HMAC and classified cases. It is **not used by the autonomous Quant loop**, has no account store, doesn't load credentials and cannot submit trades.

## Critical trust boundary and unresolved work

**The checkpoint file is a public test fixture and its HMAC key is public.** The actual implementation does **not** independently authenticate an external signer, physically export a checkpoint to external immutable storage, distribute protected signing keys, guarantee externally anchored continuity, prevent an attacker from editing the checkpoint fixture or implement a real recovery action. **No real anti-rollback claim follows from a passing test**: an adversary controlling both DB and fixture could forge matching evidence, or a database could be restored together with an old checkpoint. Production would require independently governed checkpoint custody (e.g. authenticated external write-once storage), real identity and rotation, complete crash/restore and disaster-recovery tests, and explicit owner sign-off.

This stage adds no HTTP/API route, no database schema migration or account mutation, and changes **nothing** in the existing Java paper position, Python Quant testnet, automatic order, credentials, owner-PC, microphone or firewall policies. The Stage45 **13 immutable release blockers** remain NO_GO.

A correct comparison produces **a read-only diagnostic classification**, not a restored database. The only covered account is the inert `paper-default` synthetic fixture. An "ahead-of-checkpoint" DB is deliberately classified as `UNANCHORED_DB_ADVANCE` since a single digest cannot prove ancestry from a previous checkpoint.

## Verification plan

On exact-HEAD GitHub Actions: Python checkpoint and previous risk/testnet authorization tests; Java actual H2/JPA restored/stale/forged checkpoint integration tests, earlier durable replay and cross-language HMAC tests; full orchestrator and workstation regression; hosted Windows synthetic containment; Stage45 7 policy tests and 13 immutable NO_GO blockers. Passing CI certifies **fixture tests only**, not independent external trust or production anti-rollback.

**No merge, deployment, installation, real bot, live order, testnet activation, autonomous desktop task or owner-PC pilot.**

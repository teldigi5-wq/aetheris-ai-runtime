# Stage 46.31 — Durable offline PAPER replay ledger laboratory

**Status: OPEN DRAFT, unmerged. All 13 Stage45 release blockers remain active. Physical owner-PC operation, autonomous testnet trading and live money: NO_GO.**

## Source-verified problem

Stage46.30 passed **offline synthetic HMAC-SHA256 and chained previous-digest validation in Java and Python**, but both verifiers remembered the most recently accepted sequence only in memory. Recreating a verifier could reset its cursor to zero. A production trading account cannot trust this as durable replay protection, even with an authenticated envelope.

## Implemented test-only hardening

This stage deliberately creates **no runtime trading services, HTTP endpoints, broker integrations, Java JPA entity scans or production account/database migrations**.

- `orchestrator-service/src/test/java/io/aetheris/orchestrator/trading/Stage4631OfflineReplayLedgerLab.java` is a **Java test-source-only** ledger backed by an explicitly initialized disposable H2 SQL table, separate from the live paper-account tables.
- `aetheris-quant/tests/durable_offline_paper_replay_lab.py` is a **Python test-folder-only** SQLite ledger. It is never imported by `autonomous.py`, the market scanner, the paper broker or `app/config.py`.
- Both verify Stage46.30's existing **canonical two-state synthetic snapshot fixtures**, time window, fixed inert identity, MAC and SHA256 predecessor, then advance a persisted `(sequence_number, last_digest)` record **atomically**. They use SQL conditional compare-and-set; Python uses `BEGIN IMMEDIATE` to serialize writes. No trading account, position or equity is updated.
- Database setup/GENESIS seeding is **explicit and one-time per disposable test database**. A new verifier instance reuses the durable state instead of silently recreating the cursor or resetting to genesis.
- Tests cover restart continuity, rejecting repeated state/sequence gaps, rejecting tampered money evidence and wrong digest, concurrent consumers with **at most one commit**, missing cursor refusal, and a simulated failure **after SQL update but before commit**, which must roll the whole update back.
- Even a valid committed cursor means only `DURABLE_SYNTHETIC_SEQUENCE_ONLY` or `DURABLE_SYNTHETIC_ONLY`. All `stateApplied`, `testnetOrderAuthorized`, `liveOrderAuthorized`, and `productionAuthorized` flags remain false and `decision=NO_GO`.

The independently persisted Java H2 and Python SQLite ledgers do **not share a physical database**. Matching the same two published reference snapshots establishes deterministic offline parity, not a live cross-runtime account protocol or cross-host replay resistance. The published CI fixture HMAC key is a known test value, **not a trusted producer secret**.

## Security and financial limitations

No owner authentication, broker identity, deployment key management, durable trusted remote producer, real account/position snapshot import, testnet connection, live exchange order cancellation, or real-money transfer authority was added. The synthetic test ledgers are deliberately **in test source directories only** and cannot guard the existing Python autonomous bot, even if their tests pass.

The comparison is atomic only **inside each disposable ledger database**; the entire paper-risk evaluation and actual paper-position write are **not yet one serialized transaction**, and no actual market feed is continuously monitored. It is unsafe to use this result as proof of trading-account or distributed-process correctness.

A later engineering milestone should audit real account-state provenance, clock skew, authenticated producer roles, durable owner-bound secret lifecycle, multi-process H2/PostgreSQL concurrency behavior, and transactional coupling between real state reconciliation, portfolio drawdown and bounded paper-only risk decisions. Before any testnet operation, the Python bot would also need a separately reviewed persistent STOP/latch and exchange reconciliation, and all production security blockers remain.

## CI gates

The exact-HEAD workflow runs Java H2 atomic/restart/replay tests; Python SQLite concurrent/restart/replay tests; prior cross-language synthetic state MAC, paper risk and Quant live-host rejection suites; full Java orchestrator/workstation regression; disposable Windows Job Object/reduced-token/AppContainer labs; and the immutable **13 Stage45 NO_GO blockers**.

The run must explicitly show whether each job passed. A draft PR must not be merged based solely on a design document or a partial test run. No executable artifact is published by the new workflow.

**Production, physical owner-PC, exchange testnet activation and live money remain NO_GO.**

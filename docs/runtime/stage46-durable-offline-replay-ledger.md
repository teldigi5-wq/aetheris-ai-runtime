# Stage 46.31 — Durable synthetic-paper replay ledger

**OPEN DRAFT · UNMERGED · NO_GO · all 13 Stage45 release blockers active.**

## Why this stage exists

Stage46.30 verified matching Java/Python HMAC-SHA256 and hash-chain bytes for two **synthetic** offline account snapshots. Both earlier verifiers stored accepted sequences and digests **in memory only**, so a new instance lost that history. They never imported actual paper balances or submitted broker orders.

This stage adds a **real JPA transaction and database anchor** to test whether an already accepted synthetic message is refused by a fresh Java service instance or simultaneous competitors.

## Concrete functionality

- `OfflinePaperReplayAnchorEntity` stores **only** the fixed synthetic `paper-default` account key, `lastAcceptedSequence`, last canonical SHA-256 message digest, `@Version`, and update timestamp. No paper balances, positions, credentials, testnet state, real owner identity, or privileged switches. `OfflinePaperReplayAnchorRepository.locked` uses **PESSIMISTIC_WRITE** for existing rows.
- `OfflinePaperReplayLedgerService.inspectAndRecord` verifies the prior Stage46.30 canonical envelope, fixed synthetic origin/account/currency, 120-second validity window, domain-separated HMAC and message hash. Only after verifying the public **test-fixture** key does it run a dedicated `TransactionTemplate` database transaction. It then accepts exactly sequence 1 chained to the all-zero genesis digest if no anchor exists, or the next sequence with exact predecessor digest for an existing anchor.
- The database write and sequence/hash comparison happen in the **same transaction**. Concurrent attempts to consume the same sequence are serialized on a row lock; competing initial inserts are constrained by the unique database primary key. Concurrency or persistence exceptions return a strict denial rather than a trade permission.
- All outcomes explicitly contain `decision=NO_GO`; `snapshotApplied=false`, `pythonTestnetOrderAuthorized=false`, `liveOrderAuthorized=false`, `productionAuthorized=false`. The service has **no controller, scheduler, command execution, exchange client, key provider, actual account write or account/position import**.
- New `OfflinePaperReplayLedgerIntegrationTest` exercises real H2/JPA transactions, fresh service construction using the same database, duplicate snapshot denial, out-of-order and forged messages, invalid time/key, simultaneous duplicate next-sequence requests and competing initial writes. These remain deterministic **local/disposable database tests**, not owner-PC or multi-node production certification.

## Important remaining limitations

The public synthetic fixture key is **not authentication of any real person or process**. There is no approved provider identity, key rotation, secret protection, trusted transport, authority to import account state, cross-language database sharing or actual exchange integration. The database table is introduced in a draft code branch; proper schema migration, restore behavior, operational backup, database rollback detection and disaster recovery are **not** certified. A restored/erased older database might forget already accepted sequence numbers; stronger externally anchored anti-rollback evidence is required before production consideration.

This is **atomic compare-and-persist for an inert replay ledger only**. It is *not* atomic read/reconcile/replace of a real Java paper account, Python Quant portfolio, broker balance or open positions. It does not close existing trades, implement an autonomous testnet kill switch or solve broker-order concurrency. The earlier Java paper equity drawdown latch remains separate, and neither ledger is wired to Python Quant automated execution.

## CI acceptance criteria

The exact PR HEAD must pass: new JPA integration tests (including restart-like fresh service and concurrent conflicts), existing Java/Python HMAC fixture and paper-risk tests, the complete orchestrator/workstation Java regressions, real hosted Windows reduced-token/Job Object/AppContainer synthetic regressions, and all **13 Stage45 NO_GO release blockers**. The implementation is not ready unless every required job passes on the same commit.

**No live-money trading, high-profit claim, testnet activation, protected merge, deployment, real microphone, autonomous OS actions, physical owner-PC pilot, or firewall mutation is authorized.**

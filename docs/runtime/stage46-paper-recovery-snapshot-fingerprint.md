# Stage 46.34 — Transaction-scoped PAPER recovery fingerprint, NOT owner approval

**OPEN DRAFT · UNMERGED · PAPER-ONLY · all 13 Stage45 release blockers active.**

## Implemented
The new Java Spring service `PaperRecoverySnapshotFingerprintService` provides a read-only `SERIALIZABLE` transaction-scoped fingerprint of **existing persisted** synthetic paper-account cash, realized P&L, account update timestamp, durable offline replay anchor sequence/hash/version, guarded peak equity/drawdown/halt and mark timestamp, each open position's identifying/side/price/quantity/notional details, and the latest market-equity mark.

Before hashing, it validates that the paper account, drawdown guard, replay row and equity mark exist, values are finite and internally consistent, no drawdown halt is active, the latest market mark is within a strictly bounded **120 seconds** of the supplied assessment time, the account cash matches the mark, equity arithmetic reconciles, open-position count and gross exposure reconcile, and each long/short position has a correct loss-side stop. The position list is sorted by position identifier to avoid order-dependent digests. The witness uses ASCII-friendly fixed field labels, Java `Double.toHexString`, precise `Instant.toString` timestamps, SHA256 and an explicit `STAGE46_34_LOCAL_PAPER_SNAPSHOT_V1` domain; no credentials, real money or exchange calls.

The caller may supply a previously observed lowercase 64-character SHA256 fingerprint. If coherent account state has changed, the next review returns `SNAPSHOT_FINGERPRINT_CHANGED`. Changed or inconsistent financial evidence refuses the review independently of a matching hash. Invalid expected hashes refuse immediately. New Spring/H2/JPA tests exercise stable fingerprints, altered cash, new marked equity, missing anchor/account, latched drawdown, unexpected open positions, stale/future market timestamps, forged fingerprints and no persistent database mutation.

## What this does NOT prove
A **SHA256 fingerprint is public evidence of state agreement, not a MAC, a secret, a signature, an authenticated owner decision, a reference anchored in immutable external custody, or a continuation/commit permission**. Even a matching hash returns `ownerAuthorized=false`, `recoveryApplied=false`, `drawdownReset=false`, `paperOrTestnetOrdersAuthorized=false`, `liveMoneyAuthorized=false`, `productionAuthorized=false`, and `decision=NO_GO`.

The transaction isolates reads on the **disposable test database**; it does not atomically authorize any future mutation, retain locks after return, or guarantee isolation against all existing trading writers that do not participate in this review protocol. The earlier Stage46.33 checkpoint comparator runs in a different transaction and is **not included in the fingerprint's external trust assessment**. A cross-service authenticated transaction, trusted owner identity, protected key lifecycle, atomic conditional apply, full-process restart/restore certification and independent security review remain outstanding.

**No brokerage client, Python Quant autonomous logic, owner PC, microphone, firewall/WFP policy, privileged command, main merge or production deployment changed.** This stage is review-only and cannot restore or change real or paper balances.

## Exact-head verification

GitHub Actions workflow `runtime-stage46-paper-snapshot-fingerprint.yml` executes new JPA tests, prior JPA recovery/replay/drawdown integration tests, complete Java orchestrator and workstation suites, existing Python offline contract and Binance live-host denial, hosted Windows synthetic job/token/AppContainer containment, and all 13 Stage45 release blockers. Tests do not authorize distribution or deployment.

**Next:** require a real independently authenticated owner identity and externally protected audit checkpoint, then design a single transactional *paper-only* conditional state-apply path with independent review before considering any recovery operation. Testnet/live brokerage remains prohibited.
# Stage 46.30 — Offline authenticated PAPER state reconciliation preflight

**Draft, unmerged, test-only. This is not cross-process authentication, a broker session, a production connector or permission to place orders.**

## What is implemented

The existing Aetheris Java PAPER portfolio and Python Quant automation do **not** share authoritative balances, open-position state, a clock/replay journal, a durable transaction boundary or an approved producer signing identity. Stage46.29 only proved seven synthetic sizing scenarios and three drawdown cases matched across two *pure* reference implementations. It explicitly did not demonstrate compatible runtime account reconciliation.

Stage46.30 adds **two independent offline validators** of a precisely constrained synthetic paper-state envelope:

- `OfflinePaperStateReconciliation.java` in the Java orchestrator trading package, with **no Spring registration**, no HTTP controller and no JPA/entity mutations.
- `aetheris-quant/app/offline_paper_state_boundary.py`, with **no import from testnet clients, the live bot, model inference, or runtime settings** and no network I/O.
- `risk-contracts/stage46-offline-paper-state-chain-fixtures.json` with two canonical signed, chained **inert test samples**. The embedded key is expressly **public test data, not a usable secret or owner identity**.
- Native Java JUnit and Python unittest suites use the **same exact HMAC-SHA256 tags and SHA256 previous-message digests**, not unrelated mock assertions.

The canonical ASCII representation is versioned and strictly ordered, with only the following fields: schema, `origin=JAVA_PAPER_SYNTHETIC_ONLY`, `account_id=paper-default`, `currency=USD`, positive monotonic sequence, issued and expiration times, SHA256 hash of the preceding canonical message, currency-minor-unit integers, paper cash/equity/daily realized P&L, boolean halt, and sorted unique position entries. Each position includes a bounded identifier, symbol, side, positive entry/stop/minor-unit amounts, integer micro-units of quantity and a consistency-checked notional.

### Validation and known limitations

The validators reject unrecognized fields or authorities, malformed monetary amounts, duplicate/unsorted positions, wrong-side stops, inconsistent quantity/notional, invalid HMAC, missing sequences, replay within the verifier lifetime, broken predecessor chains, future/expired messages, and expiry windows longer than two minutes.

The HMAC uses a distinct domain prefix, an externally provided secret (minimum 32 bytes), and constant-time tag comparison. Crucially, **the public test fixture key is not suitable for production authentication**, and the validators are intentionally **not wired to any production producer, Java account repository, Python state loader or exchange execution**. There is no externally authenticated user or owner in this feature.

**Replay state exists only in memory.** Restarting the verifier can permit reuse of old messages within their time window. An actual integration must add a trusted durable monotonic sequence and hash anchor, atomic compare-and-set, authenticated key lifecycle, separated trust domains, a validated transport, synchronized equity semantics, and owner-bound authorization. Even a valid envelope is **never applied to an actual trading account** by this stage.

A verified message still returns `decision=NO_GO`, `stateApplied=false`, `pythonQuantTestnetAuthorized=false`, `liveMoneyAuthorized=false`, and `productionAuthorized=false`. The existing Python autonomous trading loop, account, exchange endpoints, credentials, and Java paper account/position services are unchanged. The signed fixture proves only deterministic **offline agreement**, not readiness to trade.

## Validated work and future stage

Run the exact-HEAD workflow to check Java/Python golden-wire parity, adversarial freshness/replay/state tests, Stage46.29 paper risk reference checks, complete Java orchestrator/workstation regressions, hosted Windows reduced-token/Job Object/AppContainer regressions, and all **13 Stage45 production blockers**.

Next, Stage46.31 should address durable replay persistence and atomic read/validate/update semantics with a disposable database in tests, **before** considering any authenticated runtime connection. No automatic or real-money trading should be enabled.

**Protected branch merge, deployment, real owner-PC pilot, live microphone, desktop automation and firewall policy changes remain NO_GO.**

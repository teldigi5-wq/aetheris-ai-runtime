# Stage 46.28 — Durable PAPER equity drawdown latch

**OPEN DRAFT, UNMERGED, not certified for live money, testnet execution or a physical Windows-PC pilot. The 13 Stage45 release blockers remain active.**

## What changes, based on real existing code

Stage46.27 implemented deterministic paper position sizing and a daily risk budget, but it used **realized losses and hypothetical stop-price losses**. It did not record historical peak equity and it could not block future paper positions after a prior **marked-equity drawdown** persisted across separate JVM requests or restart. Stage9 already had `PaperPortfolioAnalyticsService.mark`, multisource price consensus and durable `PaperEquitySnapshotEntity` history, but those snapshots were not connected to an immutable risk halt.

This stage adds `PaperDrawdownPolicy` (pure deterministic math), `PaperDrawdownGuardEntity` (JPA persisted peak/equity/historical maximum drawdown/halted reason/time), `PaperDrawdownGuardRepository` (PESSIMISTIC_WRITE row lock plus optimistic `@Version`), and `PaperDrawdownGuardService` (paper circuit updates and risk precheck).

An equity peak is `max(previous_peak_equity, newly_observed_equity)`. A mark's observed drawdown percentage is `100 * (peak - mark) / peak`. **At or above 8%** the paper circuit latches `halted=true` and blocks **all new Java paper openings**, including the Stage10 paper-order simulator, via the existing `PaperRiskOfficerService.evaluate` path. A new high watermark, profit or later recovery never clears the halt. There is **no unaudited reset API**.

The initial baseline comes from a valid positive paper account starting balance/current cash. That initialization does *not* count as real market valuation (the entity keeps `lastMarketMarkAt=null`). A later realized cash decline below the previously recorded equity is also conservatively evaluated by the entry gate, ensuring that a marked peak cannot be ignored merely because the market mark was not invoked again.

`PaperPortfolioAnalyticsService.mark` uses the same existing multi-source market-consensus prices for every open position, then records a valid mark through the durable circuit before saving the existing equity snapshot. If any required quote or calculation is missing, stale, divergent, non-finite or invalid, the circuit attempts to persist an **invalid-evidence halt** in a separate transaction and rejects the mark instead of silently continuing. This rule only affects Java paper trading and does not call any external exchange.

## Scope and limitations

- **Risk gate, not an automatic exit engine.** A latched halt prevents *new* paper positions; it **does not automatically close existing positions or cancel actual exchange orders**. Existing paper positions are still closeable; recovery requires a separately reviewed owner-authorized procedure, not an implicit midnight reset or account-profit reset.
- **Not continuous market surveillance.** The drawdown check requires an explicit Stage9 mark (or realization of cash losses before an opening). Without regular independent, authenticated, quality-checked market-data collection, an unrealized loss can remain undetected. This stage adds no scheduler, exchange feed or unattended trader.
- **Not a portfolio-wide atomic execution guard.** Existing Java paper positions have race/double-opening risks under concurrent requests; initial circuit-row creation can fail closed on database conflicts, but the entire risk-read/new-position-write path is **not proven serializable** in this stage. It remains a blocker for even supervised testnet integration.
- **Not a guaranteed risk cap.** Stop-price and drawdown mathematics cannot guarantee fills, bounded real losses or profits. Gaps, leverage, fees, slippage, funding, liquidity, data delays, liquidation and correlation can exceed modeled losses. The 8% threshold is a protective software example, not personal investment advice.
- **No link to the Python Binance testnet bot.** Its autonomous mode and code are unchanged. No live-money trading, broker key, testnet order or withdrawal authorization exists in the new circuit.
- **No external mutation API or release.** The existing Stage9 `POST /paper/mark` is unchanged in shape; this stage adds no unauthenticated reset route, no model tool and no privileged process execution. Full security review of existing trading APIs and access controls remains necessary before real-owner use.

## Validation

The exact-HEAD CI must run pure adversarial drawdown tests (invalid marks, historical peak vs starting cash, recovery never resetting a halt), Spring/JPA durable guard tests (transactions, actual `PaperRiskOfficerService` denial), prior Stage10 paper money management, full orchestrator/workstation regression, hosted Windows isolation regression, Python quant testnet-only endpoint checks, and the immutable thirteen Stage45 `NO_GO` policy tests.

**Completion evidence can only be claimed after an exact-commit CI run is green.** The draft branch must stay unmerged; real microphone, desktop execution, user-PC installation, firewall changes, autonomous live-money orders and deployment are all forbidden.

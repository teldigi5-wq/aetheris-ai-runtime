# Stage 46.27 — Deterministic PAPER-only capital protection and position sizing

**Status: OPEN DRAFT, unmerged. Physical owner-PC pilot, live brokerage, production release, model/desktop/voice activation: NO_GO. All 13 Stage45 release blockers remain active.**

## What already exists in the codebase

The canonical organization catalog currently configures **30 specialist IDs in 13 divisions**, including **two Financial Intelligence roles** (Quant Researcher and Trading Risk Officer), **nine defensive Cybersecurity roles**, Executive Planner/Project Manager, software/AI/DevOps/QA, business and other specialists. The new Stage46.26 organization directory reports roles from that real catalog, without falsely claiming the roles are independently operational employees. There is no registered standalone CEO or Design Studio yet.

Trading already has real source code, distinct from verified profitability:

- Java orchestration: paper accounts and positions, owner-accepted analysis, signal generation, backtests, manual/two-source market snapshots, portfolio mark-to-market and correlations, a paper risk officer, Stage10 **simulation-only** orders with fees/funding/slippage and an owner-visible testnet readiness service.
- Python `aetheris-quant`: scanning, strategy analysis, backtesting, paper broker, synthetic/shadow trading, testnet-only Binance adapters, opt-in automatic **testnet** scans and position management. The authenticated testnet execution boundary is gated by explicit local switches and approved host validation. The presence of code does NOT certify exchange connectivity at this revision, owner-device behavior, continuous bot uptime, broker behavior or real-money profits.
- In this stage, **no live money, credential, Binance HTTP adapter or testnet switch is changed**.

## The concrete missing risk-budget gap closed here

Previous `PaperRiskOfficerService` checked the number of positions, leverage, gross/symbol/correlation concentration and historical realized daily losses, but it **did not account for stop-loss risk of already open paper positions against the same remaining daily loss budget**, and the candidate could use recommended notional without capping the *actual* paper simulated fill at an auditable stop-risk budget. The service also lacked strict non-finite price/amount checks, directionally valid stops and explicit fill/analysis drift restrictions.

New pure deterministic `PaperCapitalBudgetService.assess` evaluates **a single accepted synthetic candidate against the current paper account, existing open positions and reviewed trading/paper policy**. This is called from the existing `PaperRiskOfficerService`; both `PaperTradingService.open` (durable paper position) and `Stage10PaperOrderSimulatorService.simulate` (non-durable order simulation) now size using the risk-approved notional rather than recomputing a possibly excessive amount.

### Paper risk-budget model

Let **A** be the paper account's current cash, **B** its starting balance, **L** today's realized paper losses only, **O** the sum of assumed existing open-position stop losses, and **d** the maximum daily-loss fraction. Let **s** be the candidate's stop distance relative to the simulated fill, and **r** the stricter of the strategy's allowed risk and the signal's risk percentage.

`daily_loss_headroom = max(0, B × d − L − O)`

`new_trade_risk_cash = min(A × r, daily_loss_headroom)`

`proposed_notional = min(signal_recommendation, risk_cash / s, A × leverage, remaining_gross_capacity, remaining_symbol_capacity, remaining_group_capacity)`

The existing `TradingRiskPolicyEntity` defaults to **0.5% risk per trade**, **2% maximum daily realized/assumed stop-loss budget**, **3× maximum paper leverage** and **1.5 minimum reward:risk**. These are current software defaults, **not an individualized investment recommendation or guarantee that real losses can be limited to a mathematical stop**. Existing paper policy further limits position count and concentrations. Stop-side and fill/entry checks fail closed, non-finite values and inconsistent open positions block decisions, and risk is not increased after profitable paper trades.

**Critical financial limitation:** This model estimates loss **only if a stop filled at its hypothetical price**. Futures market gaps, volatility, fees, slippage, liquidations, margin calls, outages and correlations can produce much larger losses, including losses beyond simulated limits. It is **NOT** a live brokerage risk engine, signed broker execution guard or a proof of profitability. No automatic order submission or withdrawal authority is added.

## Verification and scope

- New independent pure JUnit `PaperCapitalBudgetServiceTest` exercises capped notional, realized/open risk aggregation, daily shutdown, long/short stop direction, impossible prices, non-finite risk evidence, accepted-signal requirements, gross/concentration limits and absence of exchange authority.
- Existing Stage10 trading integration test is strengthened so any simulated fill must equal the **risk-approved notional multiplied by the requested fill ratio**, and its assumed stop risk must fit within the remaining daily allowance.
- Existing Java/Windows synthetic security and Stage45 NO_GO tests must remain passing on the exact PR commit.
- The new risk-bounded notional only modifies **paper** simulation and paper journal/position construction; actual Binance testnet automation in Python remains separate, not integrated or certified here.
- No owner credential, user-data API or new network endpoint is introduced. Current Stage8 HTTP paper endpoints require separate authenticated-owner HTTP authorization review before any physical/protected deployment. Existing Stage45 sensitive route quarantine still applies to its defined task/mission/live API routes.

## Remaining product milestones (estimates, not a guaranteed schedule)

1. Validate entire paper-trading lifecycle and concurrent double-open race/transaction isolation.
2. Calculate mark-to-market peak-to-trough drawdown, unrealized portfolio risk, gap/slippage tail stress, fees/funding and volatility-adjusted sizing from verified historical datasets; institute irreversible daily kill circuit under owner-confirmed authorization.
3. Reconcile Java paper-risk policy with the Python Quant testnet execution path before any end-to-end testnet money management claim.
4. Independently validate data quality, survivorship/lookahead bias, out-of-sample/walk-forward strategies, risk-adjusted expectancy, drawdown, fees and reliability; **do not claim high profits**.
5. Close existing 13 Stage45 release blockers (identity, OS sandbox, signed supply chain, independent review, real owner-PC readiness), and independently review safe execution controls; live-money trading requires an entirely separate approval and regulatory assessment and is **not planned or authorized here**.

**As implemented: 30 registered specialist configurations, not 30 validated autonomous employees. The CEO and dedicated designer identities remain unregistered. The system supports paper/testing workflows, not safe autonomous high-profit live trading.**

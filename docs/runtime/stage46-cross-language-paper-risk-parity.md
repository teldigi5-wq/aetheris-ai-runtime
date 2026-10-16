# Stage 46.29 — Java ↔ Python paper-risk parity contract and testnet NO_GO

**OPEN DRAFT.** This stage establishes cross-language **synthetic PAPER reference-policy parity**, not live account integration, testnet order authorization, a verified Python trading drawdown stop, or profitability.

## Existing reality

The Spring Boot Java trading services already have deterministic PAPER position-sizing and a durable 8% peak-equity drawdown halt. The Python `aetheris-quant` project has its **own** `Settings` defaults and a separate opt-in Binance **testnet-only** automation pathway in `autonomous.py`. Those execution paths have not shared live portfolio/account positions, a database, credentials, an authenticated state feed or a common, independently verified halt.

From inspected source at Stage46.28:

| Scope | Java PAPER | Python Quant |
|---|---|---|
| Risk per trade default | 0.5% | 0.5% general `risk_per_trade`; **0.25%** autonomous `auto_risk_pct` |
| Daily realized/assumed stop-loss budget default | 2% of starting paper balance | 2% `max_daily_loss` setting, but independent state and risk calculation |
| Maximum open positions default | 3 | 3 general; **1** autonomous `auto_max_positions` |
| Maximum leverage default | 3× | 3 general; **1×** autonomous `auto_leverage` |
| Correlated exposure default | 82% of Java 3× gross-exposure multiple | 82% Python correlation setting, **not proven to have identical exposure semantics** |
| Equity drawdown halt | Persisted 8% peak-to-trough paper latch | No independently verified shared/persisted equivalent for testnet |
| Position stops/execution | Java PAPER simulation; no live orders | Python paper simulation and separately gated signed **testnet-only** client |
| Shared state and authorizations | No | No |

These similarities and differences are not proof that the actual Python testnet bot obeys the Java risk engine. A shared JSON file must never silently enable it.

## What this stage actually adds

1. `risk-contracts/stage46-paper-parity-v1.json` pins a **versioned, auditable fixture-only risk contract**. It includes the Java defaults, Python Quant source-default inventory, seven synthetic PAPER position-budget vectors and three drawdown vectors. All permission fields remain `false`, `scope=CROSS_LANGUAGE_PAPER_PARITY_TEST_ONLY`, `decision=NO_GO`.
2. `aetheris-quant/app/paper_risk_contract.py` implements a **standalone, pure Python counterpart** to the Java paper-limit math. It does not import the trading configuration, testnet broker, sockets, HTTP library, exchange client or runtime loop. It rejects corrupt state and has no path to order submission.
3. `aetheris-quant/tests/test_paper_risk_cross_language_contract.py` compares Python risk results against each JSON reference case, validates drawdown latching, and attacks invalid stops, NaN/Infinity, unauthorized fields, risk increases, forged permissions, duplicate JSON keys and testnet certification lies.
4. `PaperCrossLanguageRiskContractTest.java` calls the **existing real Java** `PaperCapitalBudgetService.assess` and `PaperDrawdownPolicy.review` against the **same file**, not a reimplemented Java algorithm. It also verifies actual Java default money-management policy values and the absence of all order/production authority.
5. `tools/security/stage46_cross_language_risk_readiness.py` inspects the real Python `Settings` source defaults **without importing Pydantic or reading environment secrets**. It checks matching published defaults and records unmapped runtime gaps, even if all fixtures pass. It specifically refuses to assert that the Python testnet execution loop obeys Java PAPER risk or that account states are synchronized.
6. The new CI checks Python (standard library only), Java on JDK21, existing Quant live-origin rejection, all orchestrator/workstation regressions, hosted Windows synthetic containment, and the immutable **13 Stage45 release blockers**.

## What the tests do and do not mean

A passing result means **ten fixed synthetic examples and inspected code defaults align**, not that all instruments, fees, volatility regimes, risk controls, real trades or deployed environment overrides match. Java has configurable database policy values; Python Quant has separate environment-driven defaults and automated-testnet sizing knobs. The risk agreement must be explicitly reviewed again if those diverge.

This stage makes **no change** to Python `autonomous.py`, `binance_testnet.py`, `position_manager.py`, `risk.py`, `config.py` or trade execution. It **does not start or authorize testnet automation**. The pure contract is deliberately **not wired** to exchange execution. This conservative staged integration prevents cross-language parity work from accidentally enabling an incompletely guarded order path.

### Next release gates

Before even a supervised testnet integration could be considered: an authenticated, replay-safe, transactional position/account snapshot interface; consistent equity/fees/funding and stop-loss stress logic; Python durable drawdown latch; a shared fail-closed kill-switch protocol; independently reviewed execution ordering and race protection; real exchange reconnect/reconciliation tests; signed release identity and owner authorization. A passing paper-risk simulation does not guarantee profits or cap actual losses.

## Reproduce on a trusted clone

```bash
python3 tools/security/stage46_cross_language_risk_readiness.py
python3 -m unittest discover -s aetheris-quant/tests -p test_paper_risk_cross_language_contract.py -v
python3 -m unittest discover -s aetheris-quant/tests -p test_execution_boundary.py -v
./mvnw -B -f orchestrator-service/pom.xml -Dtest=PaperCrossLanguageRiskContractTest test
```

**13 Stage45 release blockers remain unresolved; production, live-money orders and the physical owner-PC pilot are NO_GO.** Keep this PR **OPEN, DRAFT, UNMERGED**. No deployment, installation, real money, microphone, AI desktop actions, physical PC or firewall modifications.

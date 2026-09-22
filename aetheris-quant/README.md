# Aetheris Quant v0.7 — Autonomous Binance Futures Testnet Trader

A paper/testnet-first crypto futures research and automation platform. **Live-money execution remains disabled.**

## v0.7 additions
- Autonomous **testnet-only** opportunity scan loop with a separate opt-in switch
- Two-key safety gate: both `ENABLE_TESTNET_EXECUTION=true` and `ENABLE_AUTONOMOUS_TESTNET=true` are required
- Stricter qualification: setup score, multi-timeframe confidence, smart-money agreement, ensemble agreement and spread quality
- Automatic testnet position sizing from stop distance and account risk, with a hard notional cap
- Exchange filter handling from Binance `exchangeInfo` for quantity step size, minimum quantity, minimum notional and price tick size
- Automatic leverage setup, MARKET entry, exchange-side STOP_MARKET and TAKE_PROFIT_MARKET protection
- Duplicate-position and max-position checks
- Execution-quality ranking across the top futures markets
- Automatic Binance time synchronization with midpoint latency correction
- Automatic one-time re-sync/retry when Binance returns timestamp error `-1021`
- Runtime emergency kill switch that stops the autonomous loop and attempts to cancel outstanding testnet orders
- New autonomous console: `http://127.0.0.1:8000/static/autonomous.html`
- Existing all-coins scanner, pattern intelligence, Strategy Arena, backtesting, shadow trader, liquidity/BOS/CHoCH/FVG and portfolio brain retained

## Update an existing Windows clone
```powershell
cd "D:\aetheris-platform"
git pull origin main
cd aetheris-quant
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements.txt
python -m uvicorn app.main:app --reload
```

## Recommended first test
Keep autonomous execution disabled at first:
```env
ENABLE_TESTNET_EXECUTION=true
ENABLE_AUTONOMOUS_TESTNET=false
```
Open the autonomous console and use **Scan only**. This evaluates candidates without placing orders.

Only after reviewing the candidate logic should you explicitly opt in locally:
```env
ENABLE_AUTONOMOUS_TESTNET=true
```
Restart Aetheris, confirm `/api/testnet/status` still reports authenticated, then open the autonomous console.

## Default autonomous limits
```env
AUTO_SCAN_SECONDS=120
AUTO_SCAN_MARKETS=12
AUTO_MIN_SCORE=82
AUTO_MIN_MTF_CONFIDENCE=72
AUTO_MAX_SPREAD_PCT=0.08
AUTO_RISK_PCT=0.0025
AUTO_MAX_NOTIONAL_PCT=0.05
AUTO_LEVERAGE=1
AUTO_MAX_POSITIONS=1
```

These are conservative testnet defaults, not profit guarantees.

## Safety
No model or strategy can identify a guaranteed or zero-risk futures trade. Backtests, shadow results and testnet results can all differ from live markets. Aetheris v0.7 never enables live-money execution and keeps autonomous testnet trading behind a separate local opt-in switch.

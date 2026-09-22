import asyncio
from datetime import datetime, timezone


class AutonomousTestnetTrader:
    def __init__(self, client, settings, market_klines, universe_fn, analyze_fn, mtf_fn, smart_fn, votes_fn):
        self.client = client
        self.settings = settings
        self.klines = market_klines
        self.universe_fn = universe_fn
        self.analyze_fn = analyze_fn
        self.mtf_fn = mtf_fn
        self.smart_fn = smart_fn
        self.votes_fn = votes_fn
        self.running = False
        self.kill_switch = False
        self.task = None
        self.last_scan = None
        self.last_entry_at = None
        self.events = []
        self.candidates = []
        self.consecutive_errors = 0
        self.scan_lock = asyncio.Lock()
        self.scan_in_progress = False
        self.scan_started_at = None
        self.scan_completed = 0
        self.scan_total = 0

    def log(self, event, **data):
        row = {"time": datetime.now(timezone.utc).isoformat(), "event": event, **data}
        self.events.append(row)
        self.events = self.events[-400:]
        return row

    @staticmethod
    def _error_text(exc):
        text = str(exc).strip()
        return text if text else type(exc).__name__

    def _preflight_reasons(self):
        reasons = []
        if not self.client.configured:
            reasons.append("testnet credentials are not configured")
        if not self.client.enabled:
            reasons.append("testnet execution is disabled")
        if not self.settings.enable_autonomous_testnet:
            reasons.append("autonomous testnet switch is disabled")
        if not self.settings.enable_position_manager:
            reasons.append("position manager is disabled")
        if self.settings.manager_validation_mode:
            reasons.append("manager validation mode must be disabled")
        if self.settings.auto_leverage < 1 or self.settings.auto_leverage > self.settings.max_leverage:
            reasons.append("autonomous leverage is outside core leverage limits")
        return reasons

    def _cooldown_remaining(self):
        if not self.last_entry_at:
            return 0
        try:
            last = datetime.fromisoformat(self.last_entry_at)
            elapsed = (datetime.now(timezone.utc) - last).total_seconds()
            return max(0, int(self.settings.auto_entry_cooldown_seconds - elapsed))
        except Exception:
            return 0

    def state(self):
        preflight = self._preflight_reasons()
        return {
            "configured": self.client.configured,
            "execution_enabled": self.client.enabled,
            "autonomous_enabled": self.settings.enable_autonomous_testnet,
            "auto_start_on_boot": self.settings.auto_start_autonomous,
            "running": self.running,
            "kill_switch": self.kill_switch,
            "preflight_ok": not preflight,
            "preflight_reasons": preflight,
            "last_scan": self.last_scan,
            "last_entry_at": self.last_entry_at,
            "cooldown_remaining_seconds": self._cooldown_remaining(),
            "consecutive_errors": self.consecutive_errors,
            "scan_in_progress": self.scan_in_progress,
            "scan_started_at": self.scan_started_at,
            "scan_completed": self.scan_completed,
            "scan_total": self.scan_total,
            "candidates": self.candidates[-25:],
            "events": self.events[-100:],
            "rules": {
                "min_score": self.settings.auto_min_score,
                "min_mtf_confidence": self.settings.auto_min_mtf_confidence,
                "scan_seconds": self.settings.auto_scan_seconds,
                "scan_markets": self.settings.auto_scan_markets,
                "max_spread_pct": self.settings.auto_max_spread_pct,
                "risk_pct": self.settings.auto_risk_pct,
                "max_notional_pct": self.settings.auto_max_notional_pct,
                "leverage": self.settings.auto_leverage,
                "max_positions": self.settings.auto_max_positions,
                "entry_cooldown_seconds": self.settings.auto_entry_cooldown_seconds,
                "max_consecutive_errors": self.settings.auto_max_consecutive_errors,
            },
        }

    async def evaluate(self, symbol):
        frames = await asyncio.gather(*[self.klines(symbol, tf, 250) for tf in ("5m", "15m", "1h", "4h")])
        analyses = {tf: self.analyze_fn(df) for tf, df in zip(("5m", "15m", "1h", "4h"), frames)}
        a = analyses["15m"]
        mtf = self.mtf_fn(analyses)
        smart = self.smart_fn(frames[1])
        votes = self.votes_fn(frames[1])
        book = await self.client.book_ticker(symbol)
        direction = a.get("decision", "WAIT")
        reasons = []
        if direction == "WAIT": reasons.append("base strategy says WAIT")
        if float(a.get("score", 0)) < self.settings.auto_min_score: reasons.append("score below threshold")
        if mtf.get("bias") != direction: reasons.append("MTF bias conflict")
        if float(mtf.get("confidence", 0)) < self.settings.auto_min_mtf_confidence: reasons.append("MTF confidence too low")
        if smart.get("bias") not in (direction, "NEUTRAL"): reasons.append("smart-money bias conflict")
        if book["spread_pct"] > self.settings.auto_max_spread_pct: reasons.append("spread too wide")
        ensemble = votes.get("ensemble") if isinstance(votes, dict) else next((v for v in votes if isinstance(v, dict) and v.get("strategy") == "ensemble"), None)
        if ensemble and ensemble.get("decision") not in (direction, "WAIT"): reasons.append("ensemble conflict")
        approved = not reasons and direction in ("LONG", "SHORT")
        quality = min(100, round(float(a.get("score", 0)) * 0.45 + float(mtf.get("confidence", 0)) * 0.35 + (100 if smart.get("bias") == direction else 65) * 0.15 + max(0, 100 - book["spread_pct"] * 1000) * 0.05, 1))
        return {"symbol": symbol, "approved": approved, "direction": direction, "score": a.get("score", 0), "quality": quality, "mtf": mtf, "smart_bias": smart.get("bias"), "spread_pct": round(book["spread_pct"], 5), "reasons": reasons, "analysis": a}

    async def size_quantity(self, analysis, balance):
        entry = float(analysis["entry"]); sl = float(analysis["stop_loss"])
        stop_pct = abs(entry - sl) / entry
        risk_cash = float(balance) * self.settings.auto_risk_pct
        notional_by_risk = risk_cash / max(stop_pct, 0.001)
        cap = float(balance) * self.settings.auto_max_notional_pct * self.settings.auto_leverage
        notional = min(notional_by_risk, cap)
        return notional / entry, notional, risk_cash

    async def execute_candidate(self, c):
        preflight = self._preflight_reasons()
        if preflight:
            self.log("PREFLIGHT_BLOCK", symbol=c.get("symbol"), reasons=preflight); return None
        cooldown = self._cooldown_remaining()
        if cooldown > 0:
            self.log("COOLDOWN_BLOCK", symbol=c.get("symbol"), remaining_seconds=cooldown); return None
        symbol = c["symbol"]; a = c["analysis"]
        positions = await self.client.positions()
        if any(p.get("symbol") == symbol and abs(float(p.get("positionAmt", 0))) > 0 for p in positions):
            self.log("SKIP", symbol=symbol, reason="position already open"); return None
        if len(positions) >= self.settings.auto_max_positions:
            self.log("SKIP", symbol=symbol, reason="max testnet positions reached"); return None
        acct = await self.client.account()
        balance = float(acct.get("availableBalance") or acct.get("totalWalletBalance") or 0)
        if balance <= 0:
            self.log("SKIP", symbol=symbol, reason="no available testnet balance"); return None
        qty, notional, risk_cash = await self.size_quantity(a, balance)
        qty = await self.client.normalize_quantity(symbol, qty, a["entry"])
        if qty <= 0:
            self.log("SKIP", symbol=symbol, reason="normalized quantity is zero"); return None
        side = "BUY" if c["direction"] == "LONG" else "SELL"
        await self.client.set_leverage(symbol, self.settings.auto_leverage)
        entry = await self.client.market_order(symbol, side, qty)
        self.last_entry_at = datetime.now(timezone.utc).isoformat()
        self.log("TESTNET_ENTRY", symbol=symbol, direction=c["direction"], quantity=qty, notional=round(notional, 2), risk_cash=round(risk_cash, 2), quality=c["quality"], entry_order=entry.get("orderId"), manager_handoff=True)
        return {"entry": entry, "quantity": qty, "manager_handoff": True}

    async def scan_once(self, execute=False):
        if self.kill_switch:
            return {"ok": False, "reason": "kill switch is active", "state": self.state()}
        if self.scan_lock.locked():
            self.log("SCAN_BUSY", reason="a scan is already in progress")
            return {"ok": False, "busy": True, "reason": "scan already in progress", "state": self.state()}
        async with self.scan_lock:
            self.scan_in_progress = True
            self.scan_started_at = datetime.now(timezone.utc).isoformat()
            self.scan_completed = 0; self.scan_total = 0
            try:
                universe = await self.universe_fn()
                rows = [r for r in universe if isinstance(r, dict) and isinstance(r.get("symbol"), str)][: self.settings.auto_scan_markets]
                self.scan_total = len(rows)
                self.log("SCAN_STARTED", markets=self.scan_total, execute=bool(execute))
                sem = asyncio.Semaphore(4)
                async def one(row):
                    symbol = row.get("symbol", "UNKNOWN")
                    async with sem:
                        try:
                            result = await self.evaluate(symbol)
                        except Exception as e:
                            error = self._error_text(e)
                            result = {"symbol": symbol, "approved": False, "direction": "WAIT", "reasons": [error], "error": error, "quality": 0}
                            self.log("SYMBOL_ERROR", symbol=symbol, error=error)
                        finally:
                            self.scan_completed += 1
                        return result
                vals = await asyncio.gather(*[one(r) for r in rows])
                vals = sorted(vals, key=lambda x: x.get("quality", 0), reverse=True)
                self.candidates = [{k: v for k, v in x.items() if k != "analysis"} for x in vals]
                self.last_scan = datetime.now(timezone.utc).isoformat()
                executed = []
                if execute:
                    preflight = self._preflight_reasons()
                    if preflight:
                        self.log("SCAN_ONLY", reason="preflight blocked execution", reasons=preflight)
                    elif self._cooldown_remaining() > 0:
                        self.log("SCAN_ONLY", reason="entry cooldown active", remaining_seconds=self._cooldown_remaining())
                    else:
                        for c in vals:
                            if c.get("approved"):
                                try:
                                    x = await self.execute_candidate(c)
                                    if x: executed.append({"symbol": c["symbol"], "direction": c["direction"], "quality": c["quality"]})
                                except Exception as e:
                                    self.log("EXECUTION_ERROR", symbol=c["symbol"], error=self._error_text(e))
                                if len(executed) >= 1: break
                self.consecutive_errors = 0
                self.log("SCAN", approved=sum(1 for x in vals if x.get("approved")), executed=len(executed), markets=self.scan_total, symbol_errors=sum(1 for x in vals if x.get("error")))
                return {"ok": True, "executed": executed, "candidates": self.candidates, "state": self.state()}
            except Exception as e:
                self.consecutive_errors += 1
                error = self._error_text(e)
                self.log("SCAN_ERROR", error=error, consecutive_errors=self.consecutive_errors)
                if self.consecutive_errors >= self.settings.auto_max_consecutive_errors:
                    self.kill_switch = True
                    self.log("AUTO_FAILSAFE", reason="too many consecutive autonomous scan errors")
                return {"ok": False, "reason": error, "state": self.state()}
            finally:
                self.scan_in_progress = False

    async def loop(self):
        self.running = True
        self.log("AUTONOMOUS_LOOP_START")
        try:
            while not self.kill_switch and self.settings.enable_autonomous_testnet:
                await self.scan_once(execute=True)
                if self.kill_switch: break
                await asyncio.sleep(max(30, self.settings.auto_scan_seconds))
        except asyncio.CancelledError:
            raise
        finally:
            self.running = False
            self.log("AUTONOMOUS_LOOP_STOP")

    def start(self):
        if self.task and not self.task.done(): return False
        preflight = self._preflight_reasons()
        if preflight:
            self.log("START_BLOCKED", reasons=preflight); return False
        self.kill_switch = False
        self.consecutive_errors = 0
        self.task = asyncio.create_task(self.loop())
        return True

    async def stop(self):
        task = self.task
        if task and not task.done():
            task.cancel()
            try:
                await task
            except asyncio.CancelledError:
                pass
        self.task = None
        self.running = False
        self.log("AUTONOMOUS_STOP", kill_switch=False)
        return {"ok": True, "state": self.state()}

    async def kill(self, cancel_orders=True):
        self.kill_switch = True
        if self.task and not self.task.done(): self.task.cancel()
        cancelled = []
        if cancel_orders and self.client.enabled:
            try:
                positions = await self.client.positions()
                for p in positions:
                    sym = p.get("symbol")
                    if sym:
                        try: cancelled.append({"symbol": sym, "result": await self.client.cancel_all(sym)})
                        except Exception as e: cancelled.append({"symbol": sym, "error": self._error_text(e)})
            except Exception as e:
                self.log("KILL_CANCEL_ERROR", error=self._error_text(e))
        self.log("KILL_SWITCH", cancel_orders=cancel_orders)
        return {"ok": True, "cancelled": cancelled, "state": self.state()}

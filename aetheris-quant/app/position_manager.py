import asyncio, math
from datetime import datetime, timezone


class ActivePositionManager:
    """TESTNET-only active position manager.

    Dynamic break-even/trailing exits are software managed. Existing exchange-side
    protection is intentionally left in place as a fail-safe.
    """

    def __init__(self, client, settings, klines_fn, analyze_fn):
        self.client = client
        self.settings = settings
        self.klines = klines_fn
        self.analyze = analyze_fn
        self.running = False
        self.task = None
        self.last_cycle = None
        self.last_error = None
        self.events = []
        self.trades = {}
        self.closed = []

    @staticmethod
    def now():
        return datetime.now(timezone.utc).isoformat()

    def log(self, kind, **data):
        row = {"time": self.now(), "type": kind, **data}
        self.events.append(row)
        self.events = self.events[-500:]
        return row

    def _effective_rules(self):
        validation = bool(self.settings.manager_validation_mode)
        return {
            "validation_mode": validation,
            "cycle_seconds": self.settings.manager_cycle_seconds,
            "fallback_stop_pct": self.settings.manager_validation_stop_pct if validation else self.settings.manager_fallback_stop_pct,
            "tp1_r": self.settings.manager_validation_tp1_r if validation else self.settings.manager_tp1_r,
            "tp2_r": self.settings.manager_validation_tp2_r if validation else self.settings.manager_tp2_r,
            "trail_start_r": self.settings.manager_validation_trail_start_r if validation else self.settings.manager_trail_start_r,
            "trail_distance_r": self.settings.manager_validation_trail_distance_r if validation else self.settings.manager_trail_distance_r,
            "tp1_fraction": self.settings.manager_tp1_fraction,
            "tp2_fraction": self.settings.manager_tp2_fraction,
            "reversal_exit": False if validation else self.settings.manager_reversal_exit,
            "reversal_min_score": self.settings.manager_reversal_min_score,
        }

    def state(self):
        return {
            "running": self.running,
            "enabled": self.settings.enable_position_manager,
            "execution_enabled": self.client.enabled,
            "testnet_only": True,
            "audit_logging": True,
            "last_cycle": self.last_cycle,
            "last_error": self.last_error,
            "rules": self._effective_rules(),
            "positions": list(self.trades.values()),
            "closed": self.closed[-80:],
            "events": self.events[-120:],
        }

    async def _analysis(self, symbol):
        df = await self.klines(symbol, "15m", 250)
        return self.analyze(df)

    async def _new_trade(self, pos):
        symbol = pos.get("symbol")
        qty_signed = float(pos.get("positionAmt", 0) or 0)
        qty = abs(qty_signed)
        entry = float(pos.get("entryPrice", 0) or 0)
        side = "LONG" if qty_signed > 0 else "SHORT"
        rules = self._effective_rules()
        a = None
        try:
            a = await self._analysis(symbol)
        except Exception as e:
            self.log("ANALYSIS_WARNING", symbol=symbol, error=str(e))

        proposed_sl = (a or {}).get("stop_loss")
        if proposed_sl is not None:
            proposed_sl = float(proposed_sl)
        valid_sl = proposed_sl and ((side == "LONG" and proposed_sl < entry) or (side == "SHORT" and proposed_sl > entry))

        if rules["validation_mode"]:
            risk_distance = entry * rules["fallback_stop_pct"]
        else:
            risk_distance = abs(entry - proposed_sl) if valid_sl else entry * rules["fallback_stop_pct"]
            risk_distance = max(risk_distance, entry * 0.001)

        sign = 1 if side == "LONG" else -1
        t = {
            "symbol": symbol,
            "side": side,
            "initial_qty": qty,
            "qty": qty,
            "entry": entry,
            "mark": float(pos.get("markPrice", entry) or entry),
            "unrealized_pnl": float(pos.get("unrealizedProfit", pos.get("unRealizedProfit", 0)) or 0),
            "risk_distance": risk_distance,
            "initial_stop": entry - sign * risk_distance,
            "tp1": entry + sign * risk_distance * rules["tp1_r"],
            "tp2": entry + sign * risk_distance * rules["tp2_r"],
            "break_even": entry,
            "dynamic_stop": entry - sign * risk_distance,
            "best_price": entry,
            "r_multiple": 0.0,
            "tp1_done": False,
            "tp2_done": False,
            "break_even_armed": False,
            "trailing": False,
            "status": "ACTIVE",
            "validation_mode": rules["validation_mode"],
            "opened_seen_at": self.now(),
            "last_action": "ADOPTED",
            "last_trigger_mark": None,
            "last_trigger_r": None,
            "last_execution_mark": None,
        }
        self.trades[symbol] = t
        self.log("POSITION_ADOPTED", symbol=symbol, side=side, qty=qty, entry=entry, risk_distance=round(risk_distance, 8), validation_mode=rules["validation_mode"])
        return t

    async def _safe_partial_quantity(self, symbol, desired_qty, current_qty):
        rules = await self.client.symbol_rules(symbol)
        step = float(rules.get("step_size", 0) or 0)
        min_qty = float(rules.get("min_qty", 0) or 0)
        if step <= 0:
            qty = min(float(desired_qty), float(current_qty))
        else:
            qty = math.floor((min(float(desired_qty), float(current_qty)) + 1e-12) / step) * step
        txt = f"{step:.12f}".rstrip("0") if step > 0 else ""
        decimals = len(txt.split(".")[-1]) if "." in txt else 8
        qty = round(qty, decimals)
        if qty < min_qty or qty <= 0:
            return 0.0, {"step_size": step, "min_qty": min_qty}
        return qty, {"step_size": step, "min_qty": min_qty}

    def _record_trigger(self, t, trigger_mark, trigger_r):
        if trigger_mark is not None:
            t["last_trigger_mark"] = float(trigger_mark)
        if trigger_r is not None:
            t["last_trigger_r"] = float(trigger_r)

    async def _reduce(self, t, fraction, reason, trigger_mark=None, trigger_r=None):
        current = await self.client.positions()
        p = next((x for x in current if x.get("symbol") == t["symbol"]), None)
        if not p:
            return False
        current_qty = abs(float(p.get("positionAmt", 0) or 0))
        target_qty = min(current_qty, max(0.0, t["initial_qty"] * fraction))
        if target_qty <= 0:
            return False
        qty, lot = await self._safe_partial_quantity(t["symbol"], target_qty, current_qty)
        if qty <= 0:
            self.log("PARTIAL_SKIPPED_TOO_SMALL", symbol=t["symbol"], reason=reason, desired_qty=target_qty, current_qty=current_qty, min_qty=lot["min_qty"], step_size=lot["step_size"], trigger_mark=trigger_mark, trigger_r=trigger_r)
            return False
        execution_mark = await self.client.mark_price(t["symbol"])
        close_side = "SELL" if t["side"] == "LONG" else "BUY"
        order = await self.client.market_order(t["symbol"], close_side, qty, reduce_only=True, client_id=f"aeth_pm_{reason.lower()}_{int(datetime.now().timestamp())}")
        t["last_action"] = reason
        t["last_execution_mark"] = execution_mark
        self._record_trigger(t, trigger_mark, trigger_r)
        self.log(reason, symbol=t["symbol"], qty=qty, order_id=order.get("orderId"), trigger_mark=trigger_mark, trigger_r=trigger_r, execution_mark=execution_mark, current_qty_before=current_qty)
        return True

    async def _close_all(self, t, reason, trigger_mark=None, trigger_r=None):
        current = await self.client.positions()
        p = next((x for x in current if x.get("symbol") == t["symbol"]), None)
        if not p:
            return False
        q = abs(float(p.get("positionAmt", 0) or 0))
        if q <= 0:
            return False
        execution_mark = await self.client.mark_price(t["symbol"])
        qty = await self.client.normalize_quantity(t["symbol"], q, execution_mark)
        qty = min(qty, q)
        close_side = "SELL" if t["side"] == "LONG" else "BUY"
        order = await self.client.market_order(t["symbol"], close_side, qty, reduce_only=True, client_id=f"aeth_pm_exit_{int(datetime.now().timestamp())}")
        t["last_action"] = reason
        t["last_execution_mark"] = execution_mark
        self._record_trigger(t, trigger_mark, trigger_r)
        self.log(reason, symbol=t["symbol"], qty=qty, order_id=order.get("orderId"), trigger_mark=trigger_mark, trigger_r=trigger_r, execution_mark=execution_mark)
        return True

    async def _manage(self, pos):
        symbol = pos.get("symbol")
        rules = self._effective_rules()
        t = self.trades.get(symbol) or await self._new_trade(pos)
        qty_signed = float(pos.get("positionAmt", 0) or 0)
        qty = abs(qty_signed)
        mark = float(pos.get("markPrice", 0) or 0)
        if mark <= 0:
            mark = await self.client.mark_price(symbol)
        t["qty"] = qty
        t["mark"] = mark
        t["unrealized_pnl"] = float(pos.get("unrealizedProfit", pos.get("unRealizedProfit", 0)) or 0)

        sign = 1 if t["side"] == "LONG" else -1
        favorable = (mark - t["entry"]) * sign
        t["r_multiple"] = round(favorable / max(t["risk_distance"], 1e-12), 4)
        if (t["side"] == "LONG" and mark > t["best_price"]) or (t["side"] == "SHORT" and mark < t["best_price"]):
            t["best_price"] = mark

        initial_stop_hit = (
            (t["side"] == "LONG" and mark <= t["initial_stop"]) or
            (t["side"] == "SHORT" and mark >= t["initial_stop"])
        )
        if initial_stop_hit and not t["break_even_armed"] and not t["trailing"]:
            await self._close_all(t, "INITIAL_STOP_EXIT", trigger_mark=mark, trigger_r=t["r_multiple"])
            return

        if not t["tp1_done"] and t["r_multiple"] >= rules["tp1_r"]:
            if await self._reduce(t, rules["tp1_fraction"], "TP1_PARTIAL", trigger_mark=mark, trigger_r=t["r_multiple"]):
                t["tp1_done"] = True
                t["break_even_armed"] = True
                t["dynamic_stop"] = t["entry"]
                self.log("BREAK_EVEN_ARMED", symbol=symbol, stop=t["dynamic_stop"], trigger_mark=mark, trigger_r=t["r_multiple"])
                return

        if not t["tp2_done"] and t["r_multiple"] >= rules["tp2_r"]:
            if await self._reduce(t, rules["tp2_fraction"], "TP2_PARTIAL", trigger_mark=mark, trigger_r=t["r_multiple"]):
                t["tp2_done"] = True
                return

        if t["r_multiple"] >= rules["trail_start_r"]:
            just_started = not t["trailing"]
            t["trailing"] = True
            trail = t["best_price"] - sign * t["risk_distance"] * rules["trail_distance_r"]
            if t["side"] == "LONG":
                t["dynamic_stop"] = max(t["dynamic_stop"], trail, t["entry"] if t["break_even_armed"] else t["initial_stop"])
            else:
                t["dynamic_stop"] = min(t["dynamic_stop"], trail, t["entry"] if t["break_even_armed"] else t["initial_stop"])
            if just_started:
                self._record_trigger(t, mark, t["r_multiple"])
                self.log("TRAILING_STARTED", symbol=symbol, dynamic_stop=t["dynamic_stop"], best_price=t["best_price"], trigger_mark=mark, trigger_r=t["r_multiple"])

        stop_hit = (t["side"] == "LONG" and mark <= t["dynamic_stop"]) or (t["side"] == "SHORT" and mark >= t["dynamic_stop"])
        if stop_hit and (t["break_even_armed"] or t["trailing"]):
            await self._close_all(t, "DYNAMIC_STOP_EXIT", trigger_mark=mark, trigger_r=t["r_multiple"])
            return

        if rules["reversal_exit"]:
            try:
                a = await self._analysis(symbol)
                opposite = "SHORT" if t["side"] == "LONG" else "LONG"
                if a.get("decision") == opposite and float(a.get("score", 0)) >= rules["reversal_min_score"]:
                    await self._close_all(t, "REVERSAL_EXIT", trigger_mark=mark, trigger_r=t["r_multiple"])
            except Exception as e:
                self.log("REVERSAL_CHECK_WARNING", symbol=symbol, error=str(e))

    async def cycle(self):
        positions = await self.client.positions()
        active = {p.get("symbol") for p in positions if abs(float(p.get("positionAmt", 0) or 0)) > 0}
        for p in positions:
            if abs(float(p.get("positionAmt", 0) or 0)) > 0:
                await self._manage(p)

        for symbol in list(self.trades):
            if symbol not in active:
                t = self.trades.pop(symbol)
                t["status"] = "CLOSED"
                t["closed_seen_at"] = self.now()
                self.closed.append(t)
                self.closed = self.closed[-200:]
                self.log("POSITION_CLOSED", symbol=symbol, last_action=t.get("last_action"), last_trigger_mark=t.get("last_trigger_mark"), last_trigger_r=t.get("last_trigger_r"), last_execution_mark=t.get("last_execution_mark"))

        self.last_cycle = self.now()
        self.last_error = None
        return self.state()

    async def loop(self):
        self.running = True
        self.log("POSITION_MANAGER_STARTED", validation_mode=bool(self.settings.manager_validation_mode), audit_logging=True)
        try:
            while self.running:
                try:
                    await self.cycle()
                except asyncio.CancelledError:
                    raise
                except Exception as e:
                    self.last_error = str(e)
                    self.log("MANAGER_ERROR", error=str(e))
                await asyncio.sleep(max(3, self.settings.manager_cycle_seconds))
        finally:
            self.running = False
            self.log("POSITION_MANAGER_STOPPED")

    def start(self):
        if self.running or (self.task and not self.task.done()):
            return False
        if not self.settings.enable_position_manager:
            return False
        if not self.client.enabled:
            return False
        self.task = asyncio.create_task(self.loop())
        return True

    async def stop(self):
        self.running = False
        if self.task and not self.task.done():
            self.task.cancel()
            try:
                await self.task
            except BaseException:
                pass
        return self.state()

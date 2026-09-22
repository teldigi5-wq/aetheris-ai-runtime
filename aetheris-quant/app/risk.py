from .config import settings

def size_trade(balance, entry, stop_loss, leverage=1, score=72):
    if not stop_loss or entry == stop_loss: return {"allowed":False,"reason":"No valid stop-loss."}
    leverage = max(1,min(int(leverage),settings.max_leverage))
    quality = max(0.45, min(1.0, (float(score)-55)/30)); effective_risk = settings.risk_per_trade * quality; risk_cash = balance * effective_risk
    stop_pct = abs(entry-stop_loss)/entry
    if stop_pct <= 0 or stop_pct > 0.15: return {"allowed":False,"reason":"Stop distance outside safe engine bounds."}
    raw_notional = risk_cash/stop_pct; max_notional = balance*leverage; notional = min(raw_notional,max_notional); qty = notional/entry
    return {"allowed": True,"risk_cash": round(risk_cash,2),"risk_pct": round(effective_risk*100,3),"configured_max_risk_pct": round(settings.risk_per_trade*100,3),"leverage": leverage,"notional": round(notional,2),"quantity": round(qty,8),"stop_distance_pct": round(stop_pct*100,3)}

def risk_gate(state, analysis, mtf=None):
    if state["daily_pnl"] <= -(state["starting_balance"]*settings.max_daily_loss): return False,"Daily loss limit reached."
    if len(state["positions"]) >= settings.max_open_positions: return False,"Maximum open positions reached."
    if analysis["score"] < settings.min_setup_score: return False,"Setup score below minimum."
    if analysis["decision"] == "WAIT": return False,"No qualified direction."
    if analysis["atr_pct"] > 5: return False,"Extreme volatility filter triggered."
    if mtf and mtf["bias"] not in (analysis["decision"], "MIXED"): return False,"Multi-timeframe bias conflicts with trade direction."
    if mtf and mtf["confidence"] < 20: return False,"Multi-timeframe confidence is too low."
    return True,"Risk gates passed."

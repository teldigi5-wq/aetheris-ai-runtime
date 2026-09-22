import math
import numpy as np
import pandas as pd


def _pivot_indices(series: pd.Series, kind: str = "high", window: int = 3):
    vals = series.to_numpy(dtype=float)
    out = []
    for i in range(window, len(vals)-window):
        local = vals[i-window:i+window+1]
        if kind == "high" and vals[i] >= np.nanmax(local):
            out.append(i)
        elif kind == "low" and vals[i] <= np.nanmin(local):
            out.append(i)
    return out


def liquidity_intelligence(df: pd.DataFrame):
    d = df.tail(180).copy().reset_index(drop=True)
    if len(d) < 30:
        return {"equal_highs":[],"equal_lows":[],"sweeps":[],"zones":[]}
    price = float(d.iloc[-1].close)
    atr = float((d.high-d.low).tail(20).mean())
    tol = max(price*0.0015, atr*0.35)
    hi_idx = _pivot_indices(d.high, "high", 2)
    lo_idx = _pivot_indices(d.low, "low", 2)

    def clusters(indices, col):
        pts=[]
        for idx in indices[-16:]:
            val=float(d.iloc[idx][col])
            matched=None
            for c in pts:
                if abs(c["price"]-val) <= tol:
                    c["count"] += 1
                    c["price"]=(c["price"]+val)/2
                    c["last_index"]=idx
                    matched=c; break
            if not matched:
                pts.append({"price":val,"count":1,"last_index":idx})
        return [c for c in pts if c["count"]>=2]

    eqh=clusters(hi_idx,"high")
    eql=clusters(lo_idx,"low")
    sweeps=[]
    last=d.iloc[-1]
    prev=d.iloc[-2]
    for z in eqh:
        if last.high > z["price"] + tol*0.2 and last.close < z["price"]:
            sweeps.append({"type":"BUY-SIDE LIQUIDITY SWEEP","bias":"SHORT","level":round(z["price"],8)})
        elif prev.high > z["price"] + tol*0.2 and prev.close < z["price"]:
            sweeps.append({"type":"RECENT BUY-SIDE SWEEP","bias":"SHORT","level":round(z["price"],8)})
    for z in eql:
        if last.low < z["price"] - tol*0.2 and last.close > z["price"]:
            sweeps.append({"type":"SELL-SIDE LIQUIDITY SWEEP","bias":"LONG","level":round(z["price"],8)})
        elif prev.low < z["price"] - tol*0.2 and prev.close > z["price"]:
            sweeps.append({"type":"RECENT SELL-SIDE SWEEP","bias":"LONG","level":round(z["price"],8)})

    zones=[]
    for z in eqh:
        zones.append({"type":"BUY_SIDE","price":round(z["price"],8),"touches":z["count"]})
    for z in eql:
        zones.append({"type":"SELL_SIDE","price":round(z["price"],8),"touches":z["count"]})
    zones=sorted(zones,key=lambda x:abs(x["price"]-price))[:8]
    return {
        "equal_highs":[round(x["price"],8) for x in eqh[-4:]],
        "equal_lows":[round(x["price"],8) for x in eql[-4:]],
        "sweeps":sweeps[-5:],
        "zones":zones
    }


def fair_value_gaps(df: pd.DataFrame):
    d=df.tail(160).reset_index(drop=True)
    out=[]
    for i in range(2,len(d)):
        a,b,c=d.iloc[i-2],d.iloc[i-1],d.iloc[i]
        if c.low > a.high:
            out.append({"type":"BULLISH_FVG","low":float(a.high),"high":float(c.low),"index":i,"bias":"LONG"})
        elif c.high < a.low:
            out.append({"type":"BEARISH_FVG","low":float(c.high),"high":float(a.low),"index":i,"bias":"SHORT"})
    price=float(d.iloc[-1].close)
    active=[]
    for g in out[-20:]:
        midpoint=(g["low"]+g["high"])/2
        if abs(midpoint-price)/max(price,1e-12) < 0.08:
            active.append({**g,"low":round(g["low"],8),"high":round(g["high"],8),"distance_pct":round((midpoint/price-1)*100,3)})
    return active[-8:]


def order_blocks(df: pd.DataFrame):
    d=df.tail(160).reset_index(drop=True)
    if len(d)<25: return []
    atr=(d.high-d.low).rolling(14).mean().bfill()
    blocks=[]
    for i in range(3,len(d)-1):
        x=d.iloc[i]; nxt=d.iloc[i+1]
        impulse=abs(nxt.close-nxt.open)
        if impulse < float(atr.iloc[i])*1.25:
            continue
        if x.close < x.open and nxt.close > nxt.open and nxt.close > x.high:
            blocks.append({"type":"BULLISH_ORDER_BLOCK","low":float(x.low),"high":float(x.high),"bias":"LONG","index":i})
        elif x.close > x.open and nxt.close < nxt.open and nxt.close < x.low:
            blocks.append({"type":"BEARISH_ORDER_BLOCK","low":float(x.low),"high":float(x.high),"bias":"SHORT","index":i})
    price=float(d.iloc[-1].close)
    active=[]
    for b in blocks[-16:]:
        mid=(b["low"]+b["high"])/2
        if abs(mid-price)/max(price,1e-12) < .10:
            active.append({**b,"low":round(b["low"],8),"high":round(b["high"],8),"distance_pct":round((mid/price-1)*100,3)})
    return active[-6:]


def market_structure(df: pd.DataFrame):
    d=df.tail(160).reset_index(drop=True)
    hi=_pivot_indices(d.high,"high",2)
    lo=_pivot_indices(d.low,"low",2)
    events=[]
    if len(hi)>=2 and len(lo)>=2:
        h1,h2=float(d.iloc[hi[-2]].high),float(d.iloc[hi[-1]].high)
        l1,l2=float(d.iloc[lo[-2]].low),float(d.iloc[lo[-1]].low)
        trend="BULLISH" if h2>h1 and l2>l1 else "BEARISH" if h2<h1 and l2<l1 else "MIXED"
        last=float(d.iloc[-1].close)
        if last>h2:
            events.append({"type":"BOS_UP","bias":"LONG","level":round(h2,8)})
        if last<l2:
            events.append({"type":"BOS_DOWN","bias":"SHORT","level":round(l2,8)})
        if trend=="BEARISH" and last>h2:
            events.append({"type":"CHOCH_BULLISH","bias":"LONG","level":round(h2,8)})
        if trend=="BULLISH" and last<l2:
            events.append({"type":"CHOCH_BEARISH","bias":"SHORT","level":round(l2,8)})
    else:
        trend="UNKNOWN"
    look=d.tail(80)
    high=float(look.high.max()); low=float(look.low.min()); price=float(d.iloc[-1].close)
    equilibrium=(high+low)/2
    zone="PREMIUM" if price>equilibrium else "DISCOUNT"
    return {"trend":trend,"events":events[-4:],"range_high":round(high,8),"range_low":round(low,8),"equilibrium":round(equilibrium,8),"price_zone":zone}


def smart_money_snapshot(df: pd.DataFrame):
    liq=liquidity_intelligence(df)
    fvg=fair_value_gaps(df)
    ob=order_blocks(df)
    structure=market_structure(df)
    score_long=0; score_short=0; reasons=[]
    for s in liq["sweeps"]:
        if s["bias"]=="LONG": score_long+=18
        else: score_short+=18
        reasons.append(s["type"])
    for e in structure["events"]:
        if e["bias"]=="LONG": score_long+=16
        else: score_short+=16
        reasons.append(e["type"])
    if structure["trend"]=="BULLISH": score_long+=12
    elif structure["trend"]=="BEARISH": score_short+=12
    if structure["price_zone"]=="DISCOUNT": score_long+=5
    else: score_short+=5
    if fvg:
        nearest=min(fvg,key=lambda g:abs(g["distance_pct"]))
        if nearest["bias"]=="LONG": score_long+=8
        else: score_short+=8
    if ob:
        nearest=min(ob,key=lambda g:abs(g["distance_pct"]))
        if nearest["bias"]=="LONG": score_long+=8
        else: score_short+=8
    bias="LONG" if score_long-score_short>=12 else "SHORT" if score_short-score_long>=12 else "NEUTRAL"
    return {
        "bias":bias,"long_score":score_long,"short_score":score_short,
        "liquidity":liq,"fair_value_gaps":fvg,"order_blocks":ob,
        "structure":structure,"reasons":reasons[:8]
    }


def returns_correlation(df_a: pd.DataFrame, df_b: pd.DataFrame):
    a=df_a.close.pct_change().dropna().tail(120).reset_index(drop=True)
    b=df_b.close.pct_change().dropna().tail(120).reset_index(drop=True)
    n=min(len(a),len(b))
    if n<20: return 0.0
    c=float(a.tail(n).corr(b.tail(n)))
    return 0.0 if math.isnan(c) else c

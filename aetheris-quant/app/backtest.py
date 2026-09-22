import math
import pandas as pd
from .indicators import ema, rsi, atr, macd, bollinger, vwap, adx


def strategy_votes(df):
    d=df.copy()
    for n in (9,20,50,200): d[f"ema{n}"]=ema(d["close"],n)
    d["rsi14"]=rsi(d["close"],14)
    d["macd"],d["macd_signal"],d["macd_hist"]=macd(d["close"])
    d["bb_upper"],d["bb_mid"],d["bb_lower"]=bollinger(d["close"])
    d["vwap"]=vwap(d)
    d["adx"],d["plus_di"],d["minus_di"]=adx(d)
    x=d.iloc[-1]; price=float(x.close)
    recent_high=float(d.iloc[-21:-1].high.max()); recent_low=float(d.iloc[-21:-1].low.min())
    mom=(price/float(d.iloc[-5].close)-1)*100
    def pack(ls,ss,reason):
        score=max(ls,ss); decision="WAIT"
        if score>=60 and abs(ls-ss)>=10: decision="LONG" if ls>ss else "SHORT"
        return {"decision":decision,"score":round(score,1),"long_score":round(ls,1),"short_score":round(ss,1),"reason":reason}
    tl=ts=0
    if x.ema20>x.ema50: tl+=30
    else: ts+=30
    if x.ema50>x.ema200: tl+=25
    else: ts+=25
    if x.plus_di>x.minus_di: tl+=15
    else: ts+=15
    if x.adx>=20:
        if tl>ts: tl+=min(20,float(x.adx)/2)
        else: ts+=min(20,float(x.adx)/2)
    trend=pack(tl,ts,"EMA alignment + directional movement + ADX")
    ml=ms=0
    if x.macd_hist>0: ml+=30
    else: ms+=30
    if mom>0.2: ml+=30
    elif mom<-0.2: ms+=30
    if x.rsi14>55: ml+=20
    elif x.rsi14<45: ms+=20
    if price>x.vwap: ml+=15
    else: ms+=15
    momentum_sig=pack(ml,ms,"MACD + short-term momentum + RSI + VWAP")
    rl=rs=0
    if price < x.bb_lower: rl+=45
    if price > x.bb_upper: rs+=45
    if x.rsi14<32: rl+=35
    if x.rsi14>68: rs+=35
    if x.adx<20:
        if rl>rs: rl+=15
        elif rs>rl: rs+=15
    mean_rev=pack(rl,rs,"Bollinger extremes + RSI exhaustion in weak trend")
    bl=bs=0
    if price>recent_high: bl+=60
    if price<recent_low: bs+=60
    volma=d.volume.rolling(20).mean().iloc[-1]; vr=float(x.volume/max(volma,1e-9))
    if vr>=1.2:
        if bl: bl+=25
        if bs: bs+=25
    if x.adx>=22:
        if bl: bl+=15
        if bs: bs+=15
    breakout_sig=pack(bl,bs,"20-candle breakout + volume + ADX")
    raw={"trend":trend,"momentum":momentum_sig,"mean_reversion":mean_rev,"breakout":breakout_sig}
    el=sum(v["long_score"] for v in raw.values())/len(raw); es=sum(v["short_score"] for v in raw.values())/len(raw)
    raw["ensemble"]=pack(el,es,"Equal-weight ensemble of four strategies")
    return raw

def _max_drawdown(equity):
    peak = equity[0]
    worst = 0.0
    for x in equity:
        peak = max(peak, x)
        dd = (peak-x)/peak if peak else 0
        worst = max(worst, dd)
    return worst*100

def run_backtest(df, strategy_name="ensemble", starting_balance=10000.0, fee_rate=0.0004, risk_per_trade=0.005):
    if len(df) < 230:
        return {"error":"Not enough candles for backtest"}
    balance=float(starting_balance)
    equity=[balance]
    trades=[]
    wins=losses=0
    gross_win=gross_loss=0.0

    i=220
    while i < len(df)-4:
        window=df.iloc[:i+1].copy()
        votes=strategy_votes(window)
        sig=votes.get(strategy_name) or votes.get("ensemble")
        direction=sig["decision"]
        score=sig["score"]
        if direction=="WAIT" or score < 65:
            i += 1
            continue

        entry=float(df.iloc[i].close)
        atrv=float((df.iloc[max(0,i-14):i+1].high-df.iloc[max(0,i-14):i+1].low).mean())
        atrv=max(atrv, entry*0.001)
        risk_dist=atrv*1.5
        stop=entry-risk_dist if direction=="LONG" else entry+risk_dist
        target=entry+risk_dist*2.0 if direction=="LONG" else entry-risk_dist*2.0
        risk_cash=balance*risk_per_trade
        qty=risk_cash/risk_dist
        exit_price=float(df.iloc[min(i+4,len(df)-1)].close)
        outcome="TIME"
        for j in range(i+1,min(i+13,len(df))):
            h=float(df.iloc[j].high); l=float(df.iloc[j].low)
            if direction=="LONG":
                if l <= stop: exit_price=stop; outcome="SL"; i=j; break
                if h >= target: exit_price=target; outcome="TP"; i=j; break
            else:
                if h >= stop: exit_price=stop; outcome="SL"; i=j; break
                if l <= target: exit_price=target; outcome="TP"; i=j; break
        mult=1 if direction=="LONG" else -1
        pnl=(exit_price-entry)*qty*mult
        fees=(entry+exit_price)*qty*fee_rate
        pnl-=fees
        balance += pnl
        equity.append(balance)
        if pnl>=0:
            wins+=1; gross_win+=pnl
        else:
            losses+=1; gross_loss+=abs(pnl)
        trades.append({"entry_index":int(i),"side":direction,"entry":round(entry,6),"exit":round(exit_price,6),"pnl":round(pnl,2),"outcome":outcome,"score":round(score,1)})
        i += 1

    n=len(trades)
    net=balance-starting_balance
    win_rate=(wins/n*100) if n else 0
    profit_factor=(gross_win/gross_loss) if gross_loss else (999 if gross_win else 0)
    avg=(net/n) if n else 0
    returns=[]
    for a,b in zip(equity,equity[1:]):
        returns.append((b-a)/a if a else 0)
    sharpe_like=(sum(returns)/len(returns))/(pd.Series(returns).std() or 1) * math.sqrt(max(len(returns),1)) if returns else 0
    return {
        "strategy":strategy_name,"starting_balance":starting_balance,"ending_balance":round(balance,2),"net_pnl":round(net,2),
        "return_pct":round(net/starting_balance*100,2),"trades":n,"wins":wins,"losses":losses,"win_rate":round(win_rate,2),
        "profit_factor":round(profit_factor,2),"expectancy":round(avg,2),"max_drawdown_pct":round(_max_drawdown(equity),2),
        "sharpe_like":round(float(sharpe_like),2),"equity":[round(x,2) for x in equity[-250:]],"recent_trades":trades[-30:]
    }

def strategy_arena(df):
    names=["trend","momentum","mean_reversion","breakout","ensemble"]
    rows=[run_backtest(df,n) for n in names]
    rows=[r for r in rows if not r.get("error")]
    rows.sort(key=lambda r:(r["return_pct"],r["profit_factor"],-r["max_drawdown_pct"]),reverse=True)
    for idx,r in enumerate(rows,1): r["rank"]=idx
    return rows

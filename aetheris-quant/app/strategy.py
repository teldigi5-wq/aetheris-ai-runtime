from .indicators import ema, rsi, atr, macd, bollinger, vwap, adx

def _patterns(d):
    x=d.iloc[-1]; p=d.iloc[-2]; p2=d.iloc[-3] if len(d)>=3 else p; body=abs(x.close-x.open); rng=max(x.high-x.low,1e-12); patterns=[]
    def add(name,bias,strength): patterns.append({"name":name,"bias":bias,"strength":strength})
    if body/rng<0.12: add("Doji","NEUTRAL",45)
    if p.close<p.open and x.close>x.open and x.close>=p.open and x.open<=p.close: add("Bullish Engulfing","LONG",76)
    if p.close>p.open and x.close<x.open and x.open>=p.close and x.close<=p.open: add("Bearish Engulfing","SHORT",76)
    lower_wick=min(x.open,x.close)-x.low; upper_wick=x.high-max(x.open,x.close)
    if lower_wick>max(body*2.2,rng*.45) and upper_wick<rng*.22: add("Bullish Pin Bar / Hammer","LONG",68)
    if upper_wick>max(body*2.2,rng*.45) and lower_wick<rng*.22: add("Bearish Pin Bar / Shooting Star","SHORT",68)
    if x.high<p.high and x.low>p.low: add("Inside Bar","NEUTRAL",52)
    if x.high>p.high and x.low<p.low: add("Outside Bar","NEUTRAL",55)
    if p2.close<p2.open and abs(p.close-p.open)<abs(p2.close-p2.open)*.45 and x.close>x.open and x.close>(p2.open+p2.close)/2: add("Morning Star","LONG",78)
    if p2.close>p2.open and abs(p.close-p.open)<abs(p2.close-p2.open)*.45 and x.close<x.open and x.close<(p2.open+p2.close)/2: add("Evening Star","SHORT",78)
    if all(d.iloc[-i].close>d.iloc[-i].open for i in (1,2,3)): add("Three White Soldiers","LONG",73)
    if all(d.iloc[-i].close<d.iloc[-i].open for i in (1,2,3)): add("Three Black Crows","SHORT",73)
    return sorted(patterns,key=lambda z:z["strength"],reverse=True)[:5]

def _structure_pattern(d):
    look=d.tail(80)
    if len(look)<40: return None
    highs=look["high"].rolling(5,center=True).max(); lows=look["low"].rolling(5,center=True).min(); peaks=look[look["high"].eq(highs)]["high"].tail(3).tolist(); troughs=look[look["low"].eq(lows)]["low"].tail(3).tolist(); price=float(look.iloc[-1].close); atrv=float((look["high"]-look["low"]).tail(20).mean()); tol=max(atrv*1.2,price*.003)
    if len(peaks)>=2 and abs(peaks[-1]-peaks[-2])<=tol and price<min(peaks[-1],peaks[-2])-tol*.4: return "Double Top candidate"
    if len(troughs)>=2 and abs(troughs[-1]-troughs[-2])<=tol and price>max(troughs[-1],troughs[-2])+tol*.4: return "Double Bottom candidate"
    recent=look.tail(20); compression=(recent["high"].max()-recent["low"].min())/max(price,1e-12)
    if compression<.018: return "Volatility Compression / Range"
    if len(peaks)>=3 and peaks[-1]<peaks[-2]<peaks[-3] and len(troughs)>=3 and troughs[-1]>troughs[-2]>troughs[-3]: return "Symmetrical Triangle candidate"
    return None

def _support_resistance(d):
    look=d.tail(min(len(d),100)); recent=d.tail(min(len(d),30)); supports=[float(recent["low"].min()),float(look["low"].quantile(.15))]; resistances=[float(recent["high"].max()),float(look["high"].quantile(.85))]
    return sorted(set(round(v,8) for v in supports)), sorted(set(round(v,8) for v in resistances))

def analyze(df):
    d=df.copy()
    for n in (9,20,50,200): d[f"ema{n}"]=ema(d["close"],n)
    d["rsi14"]=rsi(d["close"],14); d["atr14"]=atr(d,14); d["vol_ma20"]=d["volume"].rolling(20).mean().bfill(); d["macd"],d["macd_signal"],d["macd_hist"]=macd(d["close"]); d["bb_upper"],d["bb_mid"],d["bb_lower"]=bollinger(d["close"]); d["vwap"]=vwap(d); d["adx"],d["plus_di"],d["minus_di"]=adx(d)
    x=d.iloc[-1]; price=float(x.close); av=max(float(x.atr14),price*.0005); long_pts=0.; short_pts=0.; reasons=[]
    if x.ema20>x.ema50: long_pts+=15; reasons.append("EMA20 > EMA50 confirms a bullish intermediate trend.")
    else: short_pts+=15; reasons.append("EMA20 < EMA50 confirms a bearish intermediate trend.")
    if x.ema50>x.ema200: long_pts+=12
    else: short_pts+=12
    if x.ema9>x.ema20: long_pts+=8
    else: short_pts+=8
    if price>x.vwap: long_pts+=8; reasons.append("Price is above VWAP.")
    else: short_pts+=8; reasons.append("Price is below VWAP.")
    if 52<=x.rsi14<=70: long_pts+=12; reasons.append(f"RSI {x.rsi14:.1f} supports bullish momentum.")
    elif 30<=x.rsi14<=48: short_pts+=12; reasons.append(f"RSI {x.rsi14:.1f} supports bearish momentum.")
    elif x.rsi14>=75: short_pts+=3; reasons.append("RSI is stretched; new longs are penalized.")
    elif x.rsi14<=25: long_pts+=3; reasons.append("RSI is stretched; new shorts are penalized.")
    if x.macd_hist>0: long_pts+=10
    else: short_pts+=10
    if x.plus_di>x.minus_di: long_pts+=7
    else: short_pts+=7
    if x.adx>=20:
        if long_pts>short_pts: long_pts+=min(10,float(x.adx)/5)
        else: short_pts+=min(10,float(x.adx)/5)
        reasons.append(f"ADX {x.adx:.1f} indicates usable trend strength.")
    vol_ratio=float(x.volume/max(x.vol_ma20,1e-9))
    if vol_ratio>=1.20:
        if x.close>=x.open: long_pts+=8
        else: short_pts+=8
        reasons.append(f"Volume is {vol_ratio:.2f}x its 20-candle average.")
    momentum=(price/float(d.iloc[-5].close)-1)*100
    if momentum>.20: long_pts+=8
    elif momentum<-.20: short_pts+=8
    patterns=_patterns(d); structure_pattern=_structure_pattern(d)
    for pat in patterns:
        if pat["bias"]=="LONG": long_pts+=pat["strength"]*.08
        elif pat["bias"]=="SHORT": short_pts+=pat["strength"]*.08
    supports,resistances=_support_resistance(d); nearest_support=max([s for s in supports if s<=price],default=min(supports)); nearest_resistance=min([r for r in resistances if r>=price],default=max(resistances))
    recent_high=float(d.iloc[-21:-1]["high"].max()); recent_low=float(d.iloc[-21:-1]["low"].min()); breakout=None
    if price>recent_high: long_pts+=10; breakout="Bullish breakout above recent 20-candle high"; reasons.append(breakout+".")
    elif price<recent_low: short_pts+=10; breakout="Bearish breakdown below recent 20-candle low"; reasons.append(breakout+".")
    long_pts=min(long_pts,100); short_pts=min(short_pts,100); score=max(long_pts,short_pts); decision="WAIT"
    if score>=72 and abs(long_pts-short_pts)>=12: decision="LONG" if long_pts>short_pts else "SHORT"
    if decision=="LONG":
        sl=min(price-1.6*av,price-.4*av); structure_sl=nearest_support-.25*av
        if structure_sl<price: sl=min(sl,structure_sl)
        risk_dist=price-sl; tps=[price+risk_dist*r for r in (1.5,2.5,4.)]
    elif decision=="SHORT":
        sl=max(price+1.6*av,price+.4*av); structure_sl=nearest_resistance+.25*av
        if structure_sl>price: sl=max(sl,structure_sl)
        risk_dist=sl-price; tps=[price-risk_dist*r for r in (1.5,2.5,4.)]
    else: sl=None; tps=[]
    atr_pct=av/price*100; edge=long_pts-short_pts; bull=max(5,min(90,50+edge*.38)); bear=max(5,min(90,50-edge*.38)); sideways=12+max(0,(20-float(x.adx))*.6); total=bull+bear+sideways; probs={"bullish":round(bull/total*100,1),"bearish":round(bear/total*100,1),"sideways":round(sideways/total*100,1)}
    forecasts=[]; direction_bias=(probs["bullish"]-probs["bearish"])/100
    for label,mult in [("15m",.55),("1h",1.),("4h",2.),("24h",4.2)]:
        span=av*mult; center=price+span*direction_bias; forecasts.append({"horizon":label,"bear_case":round(center-span,6),"base_case":round(center,6),"bull_case":round(center+span,6)})
    return {"price":price,"decision":decision,"score":round(score,1),"long_score":round(long_pts,1),"short_score":round(short_pts,1),"strategy":"Adaptive MTF trend + momentum + VWAP + volume + structure","market_regime":"TRENDING" if x.adx>=25 else "DEVELOPING TREND" if x.adx>=18 else "RANGE / WEAK TREND","rsi":round(float(x.rsi14),2),"ema9":round(float(x.ema9),6),"ema20":round(float(x.ema20),6),"ema50":round(float(x.ema50),6),"ema200":round(float(x.ema200),6),"macd":round(float(x.macd),6),"macd_signal":round(float(x.macd_signal),6),"macd_hist":round(float(x.macd_hist),6),"adx":round(float(x.adx),2),"vwap":round(float(x.vwap),6),"bb_upper":round(float(x.bb_upper),6),"bb_mid":round(float(x.bb_mid),6),"bb_lower":round(float(x.bb_lower),6),"atr":round(av,6),"atr_pct":round(atr_pct,3),"volume_ratio":round(vol_ratio,2),"entry":round(price,6),"stop_loss":round(sl,6) if sl else None,"take_profits":[round(v,6) for v in tps],"probabilities":probs,"forecast":forecasts,"patterns":patterns,"support_levels":supports,"resistance_levels":resistances,"breakout":breakout,"structure_pattern":structure_pattern,"reasons":reasons[:8]}

def multi_timeframe(analyses):
    weights={"5m":.15,"15m":.30,"1h":.30,"4h":.25}; signed=0.; agreement={"LONG":0,"SHORT":0,"WAIT":0}; detail={}
    for tf,a in analyses.items():
        w=weights.get(tf,.25); direction_sign=1 if a["long_score"]>a["short_score"] else -1; signed+=direction_sign*a["score"]*w; agreement[a["decision"]]=agreement.get(a["decision"],0)+1; detail[tf]={"decision":a["decision"],"score":a["score"],"rsi":a["rsi"],"adx":a["adx"],"regime":a["market_regime"]}
    bias="LONG" if signed>=18 else "SHORT" if signed<=-18 else "MIXED"; confidence=min(100,abs(signed))
    return {"bias":bias,"confidence":round(confidence,1),"agreement":agreement,"timeframes":detail,"signed_score":round(signed,1)}

from pathlib import Path
import asyncio
from fastapi import FastAPI, HTTPException, Query
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles
from .market import klines, futures_metrics, futures_universe
from .strategy import analyze, multi_timeframe
from .paper import PaperBroker
from .config import settings
from .backtest import run_backtest, strategy_arena, strategy_votes
from .intelligence import smart_money_snapshot, returns_correlation
from .shadow import ShadowTrader
from .binance_testnet import BinanceTestnetClient
from .autonomous import AutonomousTestnetTrader
from .execution_brain import RealtimeExecutionBrain
from .position_manager import ActivePositionManager
from pydantic import BaseModel

app=FastAPI(title="Aetheris Quant",version="1.0.0-rc3")
BASE=Path(__file__).parent
app.mount("/static",StaticFiles(directory=BASE/"static"),name="static")
broker=PaperBroker(settings.starting_balance)
shadow=ShadowTrader()
testnet=BinanceTestnetClient(settings.binance_testnet_api_key, settings.binance_testnet_api_secret, settings.binance_testnet_base, settings.enable_testnet_execution, settings.binance_recv_window)
auto_trader=AutonomousTestnetTrader(testnet, settings, klines, futures_universe, analyze, multi_timeframe, smart_money_snapshot, strategy_votes)
execution_brain=RealtimeExecutionBrain(testnet, settings.binance_testnet_ws_base, settings.execution_reconcile_seconds, settings.execution_keepalive_seconds)
position_manager=ActivePositionManager(testnet, settings, klines, analyze)
TIMEFRAMES=["5m","15m","1h","4h"]
SCAN_SEMAPHORE = asyncio.Semaphore(8)

@app.on_event("startup")
async def startup_tasks():
    if settings.enable_execution_stream and testnet.configured:
        try: await execution_brain.start()
        except Exception as e: execution_brain.log("STARTUP_ERROR",error=str(e))
    if settings.enable_position_manager and settings.enable_testnet_execution and testnet.configured:
        position_manager.start()
    if settings.auto_start_autonomous and settings.enable_autonomous_testnet and settings.enable_testnet_execution and testnet.configured:
        auto_trader.start()

@app.on_event("shutdown")
async def shutdown_tasks():
    try: await auto_trader.stop()
    except Exception: pass
    try: await position_manager.stop()
    except Exception: pass
    try: await execution_brain.stop()
    except Exception: pass

@app.get("/")
async def root():
    return FileResponse(BASE/"static"/"index.html")

async def build_mtf(symbol):
    frames = await asyncio.gather(*[klines(symbol,tf,250) for tf in TIMEFRAMES])
    analyses = {tf:analyze(df) for tf,df in zip(TIMEFRAMES,frames)}
    return analyses, multi_timeframe(analyses)

@app.get("/api/analysis/{symbol}")
async def analysis(symbol:str, interval:str="15m"):
    try:
        symbol=symbol.upper()
        df_task=klines(symbol,interval,250); mtf_task=build_mtf(symbol); metrics_task=futures_metrics(symbol)
        df,(analyses,mtf),metrics=await asyncio.gather(df_task,mtf_task,metrics_task)
        a=analyze(df); a["symbol"]=symbol; a["interval"]=interval; a["multi_timeframe"]=mtf; a["futures_metrics"]=metrics
        a["strategy_votes"]=strategy_votes(df); a["smart_money"]=smart_money_snapshot(df)
        a["candles"]=[{"time":int(r.open_time),"open":float(r.open),"high":float(r.high),"low":float(r.low),"close":float(r.close),"volume":float(r.volume)} for _,r in df.tail(180).iterrows()]
        from .indicators import ema
        e20=ema(df["close"],20); e50=ema(df["close"],50); e200=ema(df["close"],200); start=max(0,len(df)-180)
        a["ema_series"]={"ema20":[{"time":int(df.iloc[i].open_time),"value":float(e20.iloc[i])} for i in range(start,len(df))],"ema50":[{"time":int(df.iloc[i].open_time),"value":float(e50.iloc[i])} for i in range(start,len(df))],"ema200":[{"time":int(df.iloc[i].open_time),"value":float(e200.iloc[i])} for i in range(start,len(df))]}
        broker.monitor(symbol,a["price"]); shadow.update(symbol,a["price"]); return a
    except Exception as e:
        raise HTTPException(502,str(e))

@app.get("/api/universe")
async def universe(q: str = ""):
    rows=await futures_universe(); q=q.strip().upper()
    if q: rows=[r for r in rows if q in r["symbol"]]
    return {"count":len(rows),"coins":rows}

@app.get("/api/scanner")
async def scanner(q: str = "", offset: int = Query(0, ge=0), limit: int = Query(20, ge=5, le=40), sort: str = "volume"):
    universe=await futures_universe(); q=q.strip().upper()
    if q: universe=[r for r in universe if q in r["symbol"]]
    if sort == "gainers": universe=sorted(universe,key=lambda x:x["change_pct"],reverse=True)
    elif sort == "losers": universe=sorted(universe,key=lambda x:x["change_pct"])
    elif sort == "symbol": universe=sorted(universe,key=lambda x:x["symbol"])
    total=len(universe); page=universe[offset:offset+limit]
    async def one(base):
        async with SCAN_SEMAPHORE:
            try:
                df=await klines(base["symbol"],"15m",220); a=analyze(df); strongest=max(a.get("patterns",[]), key=lambda p:p.get("strength",0), default=None)
                return {**base,"decision":a["decision"],"score":a["score"],"rsi":a["rsi"],"adx":a["adx"],"regime":a["market_regime"],"probabilities":a["probabilities"],"pattern": strongest["name"] if strongest else "No strong pattern","pattern_bias": strongest["bias"] if strongest else "NEUTRAL","structure": a.get("structure_pattern") or a.get("breakout") or "—"}
            except Exception as e:
                return {**base,"error":str(e),"pattern":"Unavailable","pattern_bias":"NEUTRAL"}
    out=await asyncio.gather(*[one(r) for r in page]); return {"total":total,"offset":offset,"limit":limit,"coins":out}

@app.post("/api/paper/open/{symbol}")
async def paper_open(symbol:str, leverage:int=1):
    symbol=symbol.upper(); analyses,mtf=await build_mtf(symbol); a=analyses["15m"]; smart=smart_money_snapshot(await klines(symbol,"15m",250))
    if smart["bias"] not in (a["decision"], "NEUTRAL"): return {"ok":False,"reason":"Smart-money/liquidity bias conflicts with trade direction.","smart_money":smart}
    for pos in broker.state["positions"]:
        if pos.get("side") != a["decision"] or pos.get("symbol") == symbol: continue
        try:
            da,db=await asyncio.gather(klines(symbol,"15m",180),klines(pos["symbol"],"15m",180)); corr=returns_correlation(da,db)
            if corr >= settings.max_correlated_exposure: return {"ok":False,"reason":f"Correlation guard: {symbol} is {corr:.2f} correlated with open {pos['symbol']} {pos['side']} position."}
        except Exception: pass
    return broker.open(symbol,a,leverage,mtf)

@app.get("/api/backtest/{symbol}")
async def backtest(symbol:str, interval:str="15m", strategy:str="ensemble", limit:int=1200):
    try:
        symbol=symbol.upper(); limit=max(300,min(limit,1500)); df=await klines(symbol,interval,limit); result=run_backtest(df,strategy); result.update({"symbol":symbol,"interval":interval}); return result
    except Exception as e: raise HTTPException(502,str(e))

@app.get("/api/arena/{symbol}")
async def arena(symbol:str, interval:str="15m", limit:int=1200):
    try:
        symbol=symbol.upper(); df=await klines(symbol,interval,max(300,min(limit,1500))); return {"symbol":symbol,"interval":interval,"strategies":strategy_arena(df)}
    except Exception as e: raise HTTPException(502,str(e))

@app.post("/api/shadow/scan")
async def shadow_scan(limit:int=12):
    universe=(await futures_universe())[:max(5,min(limit,25))]
    async def one(row):
        async with SCAN_SEMAPHORE:
            try:
                df=await klines(row["symbol"],"15m",250); a=analyze(df); smart=smart_money_snapshot(df); frames=await asyncio.gather(*[klines(row["symbol"],tf,220) for tf in TIMEFRAMES]); mtf=multi_timeframe({tf:analyze(x) for tf,x in zip(TIMEFRAMES,frames)}); return shadow.consider(row["symbol"],a,mtf,smart)
            except Exception: return None
    vals=await asyncio.gather(*[one(r) for r in universe]); return {"created":[x for x in vals if x],"shadow":shadow.snapshot()}

@app.get("/api/shadow")
async def shadow_state(): return shadow.snapshot()

@app.get("/api/portfolio/intelligence")
async def portfolio_intelligence():
    positions=broker.state["positions"]; pairs=[]
    for i in range(len(positions)):
        for j in range(i+1,len(positions)):
            a,b=positions[i],positions[j]
            try:
                da,db=await asyncio.gather(klines(a["symbol"],"15m",160),klines(b["symbol"],"15m",160)); pairs.append({"a":a["symbol"],"b":b["symbol"],"correlation":round(returns_correlation(da,db),3),"same_side":a["side"]==b["side"]})
            except Exception: pass
    concentration=sum(float(p.get("notional",0)) for p in positions)/max(broker.state["balance"],1); return {"positions":len(positions),"gross_notional":round(sum(float(p.get("notional",0)) for p in positions),2),"notional_to_equity":round(concentration,3),"correlations":pairs}

@app.get("/api/account")
async def account():
    return {"mode":settings.mode,**broker.state,"risk_rules":{"risk_per_trade_pct":settings.risk_per_trade*100,"max_daily_loss_pct":settings.max_daily_loss*100,"max_open_positions":settings.max_open_positions,"max_leverage":settings.max_leverage,"min_setup_score":settings.min_setup_score}}

@app.get("/api/journal")
async def journal(): return broker.state["journal"][-100:]

class TestnetOrderRequest(BaseModel):
    symbol: str; side: str; quantity: float; leverage: int = 1; stop_loss: float | None = None; take_profit: float | None = None

@app.get("/api/testnet/status")
async def testnet_status(): return await testnet.status()

@app.get("/api/testnet/account")
async def testnet_account():
    try: return await testnet.account()
    except Exception as e: raise HTTPException(400,str(e))

@app.get("/api/testnet/positions")
async def testnet_positions():
    try: return await testnet.positions()
    except Exception as e: raise HTTPException(400,str(e))

@app.get("/api/testnet/open-orders")
async def testnet_open_orders(symbol:str|None=None):
    try: return await testnet.open_orders(symbol.upper() if symbol else None)
    except Exception as e: raise HTTPException(400,str(e))

@app.post("/api/testnet/preview")
async def testnet_preview(req:TestnetOrderRequest):
    close_side="SELL" if req.side.upper()=="BUY" else "BUY"
    return {"execution_enabled":settings.enable_testnet_execution,"symbol":req.symbol.upper(),"entry":{"side":req.side.upper(),"type":"MARKET","quantity":req.quantity,"leverage":min(max(req.leverage,1),settings.max_leverage)},"protection":{"close_side":close_side,"stop_loss":req.stop_loss,"take_profit":req.take_profit},"note":"Preview only; this endpoint never sends an order."}

@app.post("/api/testnet/execute")
async def testnet_execute(req:TestnetOrderRequest):
    try:
        symbol=req.symbol.upper(); side=req.side.upper()
        if side not in ("BUY","SELL"): raise ValueError("side must be BUY or SELL")
        lev=min(max(req.leverage,1),settings.max_leverage); await testnet.set_leverage(symbol,lev); price=await testnet.mark_price(symbol); qty=await testnet.normalize_quantity(symbol,req.quantity,price); entry=await testnet.market_order(symbol,side,qty); close_side="SELL" if side=="BUY" else "BUY"; protection={}
        if req.stop_loss is not None: protection["stop_loss"]=await testnet.conditional_close(symbol,close_side,"STOP_MARKET",req.stop_loss)
        if req.take_profit is not None: protection["take_profit"]=await testnet.conditional_close(symbol,close_side,"TAKE_PROFIT_MARKET",req.take_profit)
        try: await execution_brain.reconcile()
        except Exception: pass
        return {"ok":True,"entry":entry,"protection":protection,"normalized_quantity":qty,"environment":"BINANCE_FUTURES_TESTNET"}
    except Exception as e: raise HTTPException(400,str(e))

@app.delete("/api/testnet/cancel-all/{symbol}")
async def testnet_cancel_all(symbol:str):
    try:
        result=await testnet.cancel_all(symbol)
        try: await execution_brain.reconcile()
        except Exception: pass
        return result
    except Exception as e: raise HTTPException(400,str(e))

@app.get("/api/autonomous/status")
async def autonomous_status(): return auto_trader.state()

@app.post("/api/autonomous/scan-once")
async def autonomous_scan_once(execute:bool=False): return await auto_trader.scan_once(execute=execute)

@app.post("/api/autonomous/start")
async def autonomous_start():
    if not settings.enable_autonomous_testnet: raise HTTPException(400,"ENABLE_AUTONOMOUS_TESTNET is false in .env")
    if not settings.enable_testnet_execution: raise HTTPException(400,"ENABLE_TESTNET_EXECUTION is false in .env")
    if not testnet.configured: raise HTTPException(400,"Binance testnet credentials are not configured")
    started=auto_trader.start(); return {"ok":True,"started":started,"state":auto_trader.state()}

@app.post("/api/autonomous/stop")
async def autonomous_stop(): return await auto_trader.stop()

@app.post("/api/autonomous/kill")
async def autonomous_kill(cancel_orders:bool=True): return await auto_trader.kill(cancel_orders=cancel_orders)

@app.get("/api/execution/status")
async def execution_status(): return execution_brain.state()

@app.get("/api/execution/events")
async def execution_events(): return {"events":execution_brain.events[-150:]}

@app.post("/api/execution/reconcile")
async def execution_reconcile():
    try: return await execution_brain.reconcile()
    except Exception as e: raise HTTPException(400,str(e))

@app.post("/api/execution/start")
async def execution_start():
    try:
        started=await execution_brain.start(); return {"ok":True,"started":started,"state":execution_brain.state()}
    except Exception as e: raise HTTPException(400,str(e))

@app.post("/api/execution/stop")
async def execution_stop(): return await execution_brain.stop()

@app.get("/api/manager/status")
async def manager_status(): return position_manager.state()

@app.post("/api/manager/cycle")
async def manager_cycle():
    try: return await position_manager.cycle()
    except Exception as e: raise HTTPException(400,str(e))

@app.post("/api/manager/start")
async def manager_start():
    if not settings.enable_position_manager: raise HTTPException(400,"ENABLE_POSITION_MANAGER is false in .env")
    if not settings.enable_testnet_execution: raise HTTPException(400,"ENABLE_TESTNET_EXECUTION is false in .env")
    if not testnet.configured: raise HTTPException(400,"Binance testnet credentials are not configured")
    return {"ok":True,"started":position_manager.start(),"state":position_manager.state()}

@app.post("/api/manager/stop")
async def manager_stop(): return await position_manager.stop()

@app.get("/api/health")
def health():
    return {"ok":True,"version":"1.0.0-rc3","mode":settings.mode,"live_trading_enabled":False,"shadow_trading_enabled":True,"testnet_execution_enabled":settings.enable_testnet_execution,"testnet_configured":testnet.configured,"autonomous_testnet_enabled":settings.enable_autonomous_testnet,"autonomous_auto_start":settings.auto_start_autonomous,"autonomous_running":auto_trader.running,"kill_switch":auto_trader.kill_switch,"execution_stream_enabled":settings.enable_execution_stream,"execution_stream_running":execution_brain.running,"execution_stream_connected":execution_brain.connected,"position_manager_enabled":settings.enable_position_manager,"position_manager_running":position_manager.running}

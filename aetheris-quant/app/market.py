import asyncio
import re
import time
import httpx
import pandas as pd
from .config import settings

_UNIVERSE_CACHE = {"ts": 0.0, "rows": []}
_STANDARD_USDT_SYMBOL = re.compile(r"^[A-Z0-9]+USDT$")

async def _get(path, params=None):
    url = f"{settings.binance_fapi_base}{path}"
    async with httpx.AsyncClient(timeout=12) as client:
        r = await client.get(url, params=params or {})
        r.raise_for_status()
        return r.json()

async def klines(symbol="BTCUSDT", interval="15m", limit=250):
    rows = await _get("/fapi/v1/klines", {"symbol": symbol.upper(), "interval": interval, "limit": limit})
    cols = ["open_time","open","high","low","close","volume","close_time","quote_volume","trades","taker_base","taker_quote","ignore"]
    df = pd.DataFrame(rows, columns=cols)
    for c in ["open","high","low","close","volume","quote_volume","taker_base","taker_quote"]:
        df[c] = pd.to_numeric(df[c], errors="coerce")
    return df

async def ticker24(symbol="BTCUSDT"):
    return await _get("/fapi/v1/ticker/24hr", {"symbol": symbol.upper()})

async def futures_metrics(symbol="BTCUSDT"):
    symbol = symbol.upper()
    premium, oi = await asyncio.gather(_get("/fapi/v1/premiumIndex", {"symbol": symbol}), _get("/fapi/v1/openInterest", {"symbol": symbol}))
    return {"mark_price": float(premium.get("markPrice", 0)), "index_price": float(premium.get("indexPrice", 0)), "last_funding_rate": float(premium.get("lastFundingRate", 0)), "next_funding_time": int(premium.get("nextFundingTime", 0)), "open_interest": float(oi.get("openInterest", 0)), "open_interest_time": int(oi.get("time", 0))}

async def futures_universe(force=False):
    now = time.time()
    if not force and _UNIVERSE_CACHE["rows"] and now - _UNIVERSE_CACHE["ts"] < 45:
        return _UNIVERSE_CACHE["rows"]
    exchange, tickers = await asyncio.gather(_get("/fapi/v1/exchangeInfo"), _get("/fapi/v1/ticker/24hr"))
    allowed = {
        s["symbol"]
        for s in exchange.get("symbols", [])
        if s.get("status") == "TRADING"
        and s.get("contractType") == "PERPETUAL"
        and s.get("quoteAsset") == "USDT"
        and isinstance(s.get("symbol"), str)
        and _STANDARD_USDT_SYMBOL.fullmatch(s["symbol"])
    }
    rows = []
    for t in tickers:
        sym = t.get("symbol")
        if sym not in allowed:
            continue
        try:
            price = float(t.get("lastPrice", 0) or 0)
            quote_volume = float(t.get("quoteVolume", 0) or 0)
            if price <= 0 or quote_volume <= 0:
                continue
            rows.append({"symbol": sym, "price": price, "change_pct": float(t.get("priceChangePercent", 0) or 0), "quote_volume": quote_volume, "high": float(t.get("highPrice", 0) or 0), "low": float(t.get("lowPrice", 0) or 0), "trades": int(t.get("count", 0) or 0)})
        except (TypeError, ValueError):
            continue
    rows.sort(key=lambda x: x["quote_volume"], reverse=True)
    _UNIVERSE_CACHE.update({"ts": now, "rows": rows})
    return rows

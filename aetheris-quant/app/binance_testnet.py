import asyncio
import hashlib
import hmac
import math
import time
import uuid
from urllib.parse import urlencode

import httpx


class BinanceTestnetClient:
    def __init__(self, api_key: str, api_secret: str, base_url: str, enabled: bool = False, recv_window: int = 10000, http_retries: int = 3, retry_base_seconds: float = 0.5):
        self.api_key = api_key.strip()
        self.api_secret = api_secret.strip()
        self.base_url = base_url.rstrip("/")
        self.enabled = bool(enabled)
        self.recv_window = int(recv_window)
        self.http_retries = max(0, int(http_retries))
        self.retry_base_seconds = max(0.1, float(retry_base_seconds))
        self.time_offset_ms = 0
        self._exchange_info_cache = None
        self._exchange_info_ts = 0

    @property
    def configured(self):
        return bool(self.api_key and self.api_secret)

    async def _request_with_retry(self, method, url, *, params=None, headers=None, timeout=15):
        last_error = None
        for attempt in range(self.http_retries + 1):
            try:
                async with httpx.AsyncClient(timeout=timeout) as c:
                    r = await c.request(method, url, params=params or {}, headers=headers or {})
                if r.status_code in (418, 429) or 500 <= r.status_code < 600:
                    if attempt < self.http_retries:
                        retry_after = r.headers.get("Retry-After")
                        delay = float(retry_after) if retry_after and retry_after.replace(".", "", 1).isdigit() else self.retry_base_seconds * (2 ** attempt)
                        await asyncio.sleep(min(delay, 8.0))
                        continue
                return r
            except (httpx.RequestError, OSError) as e:
                last_error = e
                if attempt >= self.http_retries:
                    raise
                await asyncio.sleep(min(self.retry_base_seconds * (2 ** attempt), 8.0))
        if last_error:
            raise last_error
        raise RuntimeError("request retry loop exited unexpectedly")

    async def sync_time(self):
        t0 = int(time.time() * 1000)
        r = await self._request_with_retry("GET", f"{self.base_url}/fapi/v1/time", timeout=10)
        r.raise_for_status()
        server = int(r.json()["serverTime"])
        t1 = int(time.time() * 1000)
        midpoint = (t0 + t1) // 2
        self.time_offset_ms = server - midpoint
        return {"server_time": server, "local_midpoint": midpoint, "rtt_ms": t1 - t0, "offset_ms": self.time_offset_ms}

    def _signed_params(self, params=None):
        if not self.configured:
            raise RuntimeError("Binance testnet API credentials are not configured.")
        p = dict(params or {})
        p.setdefault("recvWindow", self.recv_window)
        p["timestamp"] = int(time.time() * 1000) + self.time_offset_ms
        query = urlencode(p, doseq=True)
        p["signature"] = hmac.new(self.api_secret.encode(), query.encode(), hashlib.sha256).hexdigest()
        return p

    async def _signed(self, method, path, params=None, retry_clock=True):
        p = self._signed_params(params)
        headers = {"X-MBX-APIKEY": self.api_key}
        r = await self._request_with_retry(method, f"{self.base_url}{path}", params=p, headers=headers, timeout=15)
        if r.status_code >= 400:
            try:
                detail = r.json()
            except Exception:
                detail = {"msg": r.text}
            if retry_clock and isinstance(detail, dict) and detail.get("code") == -1021:
                await self.sync_time()
                return await self._signed(method, path, params, retry_clock=False)
            raise RuntimeError(f"Binance {r.status_code}: {detail}")
        return r.json()

    async def _api_key_request(self, method, path, params=None):
        if not self.api_key:
            raise RuntimeError("Binance testnet API key is not configured.")
        headers = {"X-MBX-APIKEY": self.api_key}
        r = await self._request_with_retry(method, f"{self.base_url}{path}", params=params or {}, headers=headers, timeout=15)
        if r.status_code >= 400:
            try:
                detail = r.json()
            except Exception:
                detail = {"msg": r.text}
            raise RuntimeError(f"Binance {r.status_code}: {detail}")
        if not r.text:
            return {}
        try:
            return r.json()
        except Exception:
            return {"text": r.text}

    async def start_listen_key(self):
        data = await self._api_key_request("POST", "/fapi/v1/listenKey")
        key = data.get("listenKey")
        if not key:
            raise RuntimeError("Binance did not return a listenKey")
        return key

    async def keepalive_listen_key(self, listen_key):
        return await self._api_key_request("PUT", "/fapi/v1/listenKey", {"listenKey": listen_key})

    async def close_listen_key(self, listen_key):
        return await self._api_key_request("DELETE", "/fapi/v1/listenKey", {"listenKey": listen_key})

    async def public_get(self, path, params=None):
        r = await self._request_with_retry("GET", f"{self.base_url}{path}", params=params or {}, timeout=12)
        r.raise_for_status()
        return r.json()

    async def status(self):
        out = {"configured": self.configured, "execution_enabled": self.enabled, "base_url": self.base_url, "time_offset_ms": self.time_offset_ms, "http_retries": self.http_retries, "retry_base_seconds": self.retry_base_seconds}
        try:
            out["time"] = await self.sync_time()
            out["reachable"] = True
        except Exception as e:
            out["reachable"] = False
            out["error"] = str(e)
            return out
        if self.configured:
            try:
                acct = await self._signed("GET", "/fapi/v2/account")
                out["authenticated"] = True
                out["account"] = {"totalWalletBalance": acct.get("totalWalletBalance"), "availableBalance": acct.get("availableBalance"), "totalUnrealizedProfit": acct.get("totalUnrealizedProfit")}
            except Exception as e:
                out["authenticated"] = False
                out["auth_error"] = str(e)
        else:
            out["authenticated"] = False
        return out

    async def account(self):
        return await self._signed("GET", "/fapi/v2/account")

    async def open_orders(self, symbol=None):
        return await self._signed("GET", "/fapi/v1/openOrders", {"symbol": symbol} if symbol else {})

    async def positions(self):
        acct = await self.account()
        return [p for p in acct.get("positions", []) if abs(float(p.get("positionAmt", 0))) > 0]

    async def mark_price(self, symbol):
        x = await self.public_get("/fapi/v1/premiumIndex", {"symbol": symbol.upper()})
        return float(x["markPrice"])

    async def book_ticker(self, symbol):
        x = await self.public_get("/fapi/v1/ticker/bookTicker", {"symbol": symbol.upper()})
        bid = float(x["bidPrice"])
        ask = float(x["askPrice"])
        mid = (bid + ask) / 2 if bid and ask else 0
        return {"bid": bid, "ask": ask, "mid": mid, "spread_pct": ((ask - bid) / mid * 100) if mid else 999}

    async def exchange_info(self, force=False):
        now = time.time()
        if self._exchange_info_cache is None or force or now - self._exchange_info_ts > 1800:
            self._exchange_info_cache = await self.public_get("/fapi/v1/exchangeInfo")
            self._exchange_info_ts = now
        return self._exchange_info_cache

    async def symbol_rules(self, symbol):
        info = await self.exchange_info()
        row = next((x for x in info.get("symbols", []) if x.get("symbol") == symbol.upper()), None)
        if not row:
            raise RuntimeError(f"Unknown testnet symbol {symbol}")
        fs = {f["filterType"]: f for f in row.get("filters", [])}
        lot = fs.get("LOT_SIZE", {})
        mlot = fs.get("MARKET_LOT_SIZE", lot)
        pricef = fs.get("PRICE_FILTER", {})
        return {"symbol": symbol.upper(), "status": row.get("status"), "step_size": float(mlot.get("stepSize", lot.get("stepSize", 1))), "min_qty": float(mlot.get("minQty", lot.get("minQty", 0))), "max_qty": float(mlot.get("maxQty", lot.get("maxQty", 1e30))), "tick_size": float(pricef.get("tickSize", 0.01)), "min_notional": float((fs.get("MIN_NOTIONAL") or fs.get("NOTIONAL") or {}).get("notional", 5) or 5)}

    @staticmethod
    def _floor_step(value, step):
        return value if step <= 0 else math.floor((value + 1e-12) / step) * step

    async def normalize_quantity(self, symbol, quantity, price=None):
        r = await self.symbol_rules(symbol)
        q = self._floor_step(float(quantity), r["step_size"])
        q = max(q, r["min_qty"])
        q = min(q, r["max_qty"])
        if price and q * float(price) < r["min_notional"]:
            q = self._floor_step((r["min_notional"] / float(price)) + r["step_size"], r["step_size"])
        txt = f"{r['step_size']:.12f}".rstrip("0")
        decimals = len(txt.split(".")[-1]) if "." in txt else 0
        return round(q, decimals)

    async def normalize_price(self, symbol, price):
        r = await self.symbol_rules(symbol)
        p = self._floor_step(float(price), r["tick_size"])
        txt = f"{r['tick_size']:.12f}".rstrip("0")
        decimals = len(txt.split(".")[-1]) if "." in txt else 0
        return round(p, decimals)

    def _require_enabled(self):
        if not self.enabled:
            raise RuntimeError("Testnet execution is locked. Set ENABLE_TESTNET_EXECUTION=true locally to allow order placement.")

    async def set_leverage(self, symbol, leverage):
        self._require_enabled()
        leverage = max(1, min(int(leverage), 20))
        return await self._signed("POST", "/fapi/v1/leverage", {"symbol": symbol.upper(), "leverage": leverage})

    async def market_order(self, symbol, side, quantity, reduce_only=False, client_id=None):
        self._require_enabled()
        side = side.upper()
        if side not in ("BUY", "SELL"):
            raise ValueError("side must be BUY or SELL")
        params = {"symbol": symbol.upper(), "side": side, "type": "MARKET", "quantity": str(quantity), "newClientOrderId": client_id or f"aetheris_{uuid.uuid4().hex[:20]}"}
        if reduce_only:
            params["reduceOnly"] = "true"
        return await self._signed("POST", "/fapi/v1/order", params)

    async def conditional_close(self, symbol, side, order_type, trigger_price, client_id=None):
        self._require_enabled()
        order_type = order_type.upper()
        if order_type not in ("STOP_MARKET", "TAKE_PROFIT_MARKET"):
            raise ValueError("unsupported conditional close type")
        trigger_price = await self.normalize_price(symbol, trigger_price)
        params = {"algoType": "CONDITIONAL", "symbol": symbol.upper(), "side": side.upper(), "type": order_type, "triggerPrice": str(trigger_price), "closePosition": "true", "workingType": "MARK_PRICE", "clientAlgoId": client_id or f"aeth_{uuid.uuid4().hex[:24]}"}
        return await self._signed("POST", "/fapi/v1/algoOrder", params)

    async def cancel_all(self, symbol):
        self._require_enabled()
        symbol = symbol.upper()
        regular = await self._signed("DELETE", "/fapi/v1/allOpenOrders", {"symbol": symbol})
        algo = None
        try:
            algo = await self._signed("DELETE", "/fapi/v1/algoOpenOrders", {"symbol": symbol})
        except Exception as e:
            algo = {"warning": str(e)}
        return {"regular": regular, "algo": algo}

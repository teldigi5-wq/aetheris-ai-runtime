import asyncio, json
from datetime import datetime, timezone
import websockets

class RealtimeExecutionBrain:
    def __init__(self, client, ws_base, reconcile_seconds=30, keepalive_seconds=2700):
        self.client=client
        self.ws_base=ws_base.rstrip('/')
        self.reconcile_seconds=max(10,int(reconcile_seconds))
        self.keepalive_seconds=max(300,int(keepalive_seconds))
        self.running=False
        self.connected=False
        self.listen_key=None
        self.ws_task=None
        self.keepalive_task=None
        self.reconcile_task=None
        self.last_event=None
        self.last_reconcile=None
        self.last_error=None
        self.reconnects=0
        self.events=[]
        self.positions=[]
        self.open_orders=[]
        self.account={}
        self.order_updates=0
        self.account_updates=0

    @staticmethod
    def now(): return datetime.now(timezone.utc).isoformat()

    def log(self, kind, **data):
        row={"time":self.now(),"type":kind,**data}
        self.events.append(row)
        self.events=self.events[-300:]
        return row

    def state(self):
        return {
            "running":self.running,
            "connected":self.connected,
            "configured":self.client.configured,
            "execution_enabled":self.client.enabled,
            "listen_key_active":bool(self.listen_key),
            "last_event":self.last_event,
            "last_reconcile":self.last_reconcile,
            "last_error":self.last_error,
            "reconnects":self.reconnects,
            "order_updates":self.order_updates,
            "account_updates":self.account_updates,
            "positions":self.positions,
            "open_orders":self.open_orders,
            "account":self.account,
            "events":self.events[-80:]
        }

    async def reconcile(self):
        acct, positions, orders = await asyncio.gather(
            self.client.account(), self.client.positions(), self.client.open_orders()
        )
        self.account={
            "wallet_balance":acct.get("totalWalletBalance"),
            "available_balance":acct.get("availableBalance"),
            "unrealized_pnl":acct.get("totalUnrealizedProfit")
        }
        self.positions=[{
            "symbol":p.get("symbol"),
            "positionAmt":p.get("positionAmt"),
            "entryPrice":p.get("entryPrice"),
            "markPrice":p.get("markPrice"),
            "unRealizedProfit":p.get("unrealizedProfit",p.get("unRealizedProfit")),
            "leverage":p.get("leverage"),
            "marginType":p.get("marginType")
        } for p in positions]
        self.open_orders=[{
            "symbol":o.get("symbol"),"orderId":o.get("orderId"),"clientOrderId":o.get("clientOrderId"),
            "side":o.get("side"),"type":o.get("type"),"status":o.get("status"),
            "price":o.get("price"),"stopPrice":o.get("stopPrice"),"origQty":o.get("origQty")
        } for o in orders]
        self.last_reconcile=self.now()
        self.log("RECONCILE",positions=len(self.positions),open_orders=len(self.open_orders))
        return self.state()

    async def _handle(self, msg):
        event=msg.get("e")
        self.last_event=self.now()
        if event=="ORDER_TRADE_UPDATE":
            self.order_updates+=1
            o=msg.get("o",{})
            self.log("ORDER_TRADE_UPDATE",symbol=o.get("s"),side=o.get("S"),order_type=o.get("o"),status=o.get("X"),execution_type=o.get("x"),qty=o.get("q"),filled=o.get("z"),avg_price=o.get("ap"),realized_pnl=o.get("rp"),client_id=o.get("c"))
            if o.get("X") in ("FILLED","CANCELED","EXPIRED","REJECTED"):
                try: await self.reconcile()
                except Exception as e: self.last_error=str(e)
        elif event=="ACCOUNT_UPDATE":
            self.account_updates+=1
            a=msg.get("a",{})
            changed=[]
            for p in a.get("P",[]):
                if abs(float(p.get("pa",0) or 0))>0:
                    changed.append({"symbol":p.get("s"),"amount":p.get("pa"),"entry":p.get("ep"),"unrealized":p.get("up"),"margin_type":p.get("mt")})
            self.log("ACCOUNT_UPDATE",reason=a.get("m"),positions=changed)
        elif event=="listenKeyExpired":
            self.log("LISTEN_KEY_EXPIRED")
            raise RuntimeError("Binance listenKey expired")
        else:
            self.log(event or "USER_EVENT",payload=msg)

    async def _stream_once(self):
        self.listen_key=await self.client.start_listen_key()
        ws_url=f"{self.ws_base}/ws/{self.listen_key}"
        self.log("LISTEN_KEY_STARTED")
        async with websockets.connect(ws_url,ping_interval=None,ping_timeout=None,close_timeout=5,max_size=2_000_000) as ws:
            self.connected=True
            self.last_error=None
            self.log("WS_CONNECTED")
            try:
                async for raw in ws:
                    try: msg=json.loads(raw)
                    except Exception:
                        self.log("WS_NON_JSON"); continue
                    await self._handle(msg)
            finally:
                self.connected=False
                self.log("WS_DISCONNECTED")

    async def _stream_loop(self):
        self.running=True
        try:
            while self.running:
                try:
                    await self._stream_once()
                except asyncio.CancelledError:
                    raise
                except Exception as e:
                    self.connected=False
                    self.last_error=str(e)
                    self.reconnects+=1
                    self.log("WS_ERROR",error=str(e),reconnects=self.reconnects)
                    await asyncio.sleep(min(30,2+self.reconnects))
        finally:
            self.connected=False
            self.running=False

    async def _keepalive_loop(self):
        while self.running:
            await asyncio.sleep(self.keepalive_seconds)
            if self.listen_key:
                try:
                    await self.client.keepalive_listen_key(self.listen_key)
                    self.log("LISTEN_KEY_KEEPALIVE")
                except Exception as e:
                    self.last_error=str(e); self.log("KEEPALIVE_ERROR",error=str(e))

    async def _reconcile_loop(self):
        while self.running:
            try: await self.reconcile()
            except Exception as e:
                self.last_error=str(e); self.log("RECONCILE_ERROR",error=str(e))
            await asyncio.sleep(self.reconcile_seconds)

    async def start(self):
        if self.running: return False
        if not self.client.configured: raise RuntimeError("Binance testnet credentials are not configured")
        await self.client.sync_time()
        await self.reconcile()
        self.running=True
        self.ws_task=asyncio.create_task(self._stream_loop())
        self.keepalive_task=asyncio.create_task(self._keepalive_loop())
        self.reconcile_task=asyncio.create_task(self._reconcile_loop())
        return True

    async def stop(self):
        self.running=False
        tasks=[self.ws_task,self.keepalive_task,self.reconcile_task]
        for t in tasks:
            if t and not t.done(): t.cancel()
        if self.listen_key:
            try: await self.client.close_listen_key(self.listen_key)
            except Exception: pass
        self.listen_key=None
        self.connected=False
        self.log("EXECUTION_BRAIN_STOPPED")
        return self.state()

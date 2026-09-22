from datetime import datetime, timezone
from .risk import size_trade, risk_gate

class PaperBroker:
    def __init__(self, balance=10000):
        self.state = {
            "starting_balance":balance, "balance":balance, "daily_pnl":0.0,
            "positions":[], "journal":[]
        }

    def open(self, symbol, analysis, leverage=1, mtf=None):
        ok, reason = risk_gate(self.state, analysis, mtf)
        if not ok:
            self.log("REJECTED",symbol,{"reason":reason,"analysis":analysis,"mtf":mtf})
            return {"ok":False,"reason":reason}
        s = size_trade(
            self.state["balance"],analysis["entry"],analysis["stop_loss"],
            leverage,analysis["score"]
        )
        if not s["allowed"]:
            return {"ok":False,"reason":s["reason"]}
        p = {
            "symbol":symbol,"side":analysis["decision"],"entry":analysis["entry"],
            "stop_loss":analysis["stop_loss"],"take_profits":analysis["take_profits"],
            "quantity":s["quantity"],"notional":s["notional"],"leverage":s["leverage"],
            "opened_at":datetime.now(timezone.utc).isoformat(),"status":"OPEN",
            "score":analysis["score"],"tp_stage":0
        }
        self.state["positions"].append(p)
        self.log("OPEN",symbol,p)
        return {"ok":True,"position":p,"risk":s}

    def monitor(self, symbol, price):
        events=[]
        for p in list(self.state["positions"]):
            if p["symbol"] != symbol:
                continue
            if "remaining_quantity" not in p:
                p["remaining_quantity"]=p["quantity"]
            if "realized_pnl" not in p:
                p["realized_pnl"]=0.0

            if p["side"]=="LONG":
                if price <= p["stop_loss"]:
                    events.append(self.close(p,price,"STOP/TRAIL")); continue
                if p["tp_stage"]==0 and price >= p["take_profits"][0]:
                    self.partial_close(p,price,0.25,"TP1")
                    p["tp_stage"]=1; p["stop_loss"]=p["entry"]
                    self.log("MANAGE",symbol,{"action":"PARTIAL_25_AND_MOVE_SL_TO_BE","price":price})
                if p["tp_stage"]==1 and price >= p["take_profits"][1]:
                    self.partial_close(p,price,0.35,"TP2")
                    p["tp_stage"]=2
                if p["tp_stage"]>=1:
                    trail=price-(price-p["entry"])*0.45
                    p["stop_loss"]=max(p["stop_loss"],trail)
                if price >= p["take_profits"][-1]:
                    events.append(self.close(p,price,"TP3"))
            else:
                if price >= p["stop_loss"]:
                    events.append(self.close(p,price,"STOP/TRAIL")); continue
                if p["tp_stage"]==0 and price <= p["take_profits"][0]:
                    self.partial_close(p,price,0.25,"TP1")
                    p["tp_stage"]=1; p["stop_loss"]=p["entry"]
                    self.log("MANAGE",symbol,{"action":"PARTIAL_25_AND_MOVE_SL_TO_BE","price":price})
                if p["tp_stage"]==1 and price <= p["take_profits"][1]:
                    self.partial_close(p,price,0.35,"TP2")
                    p["tp_stage"]=2
                if p["tp_stage"]>=1:
                    trail=price+(p["entry"]-price)*0.45
                    p["stop_loss"]=min(p["stop_loss"],trail)
                if price <= p["take_profits"][-1]:
                    events.append(self.close(p,price,"TP3"))
        return events

    def partial_close(self,p,price,fraction,reason):
        qty=min(p["remaining_quantity"],p["quantity"]*fraction)
        if qty<=0: return None
        mult=1 if p["side"]=="LONG" else -1
        pnl=(price-p["entry"])*qty*mult
        p["remaining_quantity"]-=qty
        p["realized_pnl"]+=pnl
        self.state["balance"]+=pnl
        self.state["daily_pnl"]+=pnl
        ev={"symbol":p["symbol"],"exit":price,"reason":reason,"quantity":round(qty,8),"pnl":round(pnl,2)}
        self.log("PARTIAL_CLOSE",p["symbol"],ev)
        return ev

    def close(self,p,price,reason):
        qty=p.get("remaining_quantity",p["quantity"])
        mult=1 if p["side"]=="LONG" else -1
        pnl=(price-p["entry"])*qty*mult
        total=pnl+p.get("realized_pnl",0.0)
        self.state["balance"] += pnl
        self.state["daily_pnl"] += pnl
        self.state["positions"].remove(p)
        event={"symbol":p["symbol"],"exit":price,"reason":reason,"pnl":round(total,2)}
        self.log("CLOSE",p["symbol"],event)
        return event

    def log(self,event,symbol,data):
        self.state["journal"].append({
            "time":datetime.now(timezone.utc).isoformat(),"event":event,
            "symbol":symbol,"data":data
        })
        self.state["journal"]=self.state["journal"][-500:]

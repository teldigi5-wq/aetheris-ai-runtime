from datetime import datetime, timezone

class ShadowTrader:
    def __init__(self):
        self.signals=[]
        self.open={}
        self.closed=[]

    def consider(self, symbol, analysis, mtf=None, smart=None):
        decision=analysis.get("decision","WAIT")
        score=float(analysis.get("score",0))
        if decision=="WAIT" or score<72 or not analysis.get("stop_loss"):
            return None
        if mtf and mtf.get("bias") not in (decision,"MIXED"):
            return None
        if smart and smart.get("bias") not in (decision,"NEUTRAL"):
            return None
        if symbol in self.open:
            return None
        event={
            "time":datetime.now(timezone.utc).isoformat(),"symbol":symbol,"side":decision,
            "score":score,"entry":analysis["entry"],"stop_loss":analysis["stop_loss"],
            "take_profits":analysis.get("take_profits",[]),"mtf_bias":mtf.get("bias") if mtf else None,
            "smart_bias":smart.get("bias") if smart else None,"status":"OPEN"
        }
        self.open[symbol]=event
        self.signals.append(event.copy())
        self.signals=self.signals[-500:]
        return event

    def update(self,symbol,price):
        p=self.open.get(symbol)
        if not p: return None
        side=p["side"]; reason=None
        if side=="LONG":
            if price<=p["stop_loss"]: reason="STOP"
            elif p["take_profits"] and price>=p["take_profits"][-1]: reason="TP3"
        else:
            if price>=p["stop_loss"]: reason="STOP"
            elif p["take_profits"] and price<=p["take_profits"][-1]: reason="TP3"
        if not reason: return None
        r=(price-p["entry"])/(abs(p["entry"]-p["stop_loss"]) or 1e-12)
        if side=="SHORT": r=-r
        result={**p,"status":"CLOSED","exit":price,"reason":reason,"r_multiple":round(r,3),"closed_at":datetime.now(timezone.utc).isoformat()}
        self.closed.append(result); self.closed=self.closed[-500:]
        del self.open[symbol]
        return result

    def snapshot(self):
        wins=[x for x in self.closed if x.get("r_multiple",0)>0]
        avg_r=sum(x.get("r_multiple",0) for x in self.closed)/len(self.closed) if self.closed else 0
        return {"open":list(self.open.values()),"closed":self.closed[-100:],"signals":self.signals[-100:],"stats":{"closed":len(self.closed),"wins":len(wins),"win_rate":round(len(wins)/len(self.closed)*100,1) if self.closed else 0,"avg_r":round(avg_r,3)}}

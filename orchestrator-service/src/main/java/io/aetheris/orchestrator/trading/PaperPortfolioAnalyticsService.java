package io.aetheris.orchestrator.trading;import org.springframework.stereotype.Service;import java.util.*;
@Service public class PaperPortfolioAnalyticsService {private final PaperTradingService paper;private final PaperPositionRepository positions;private final MultiSourceMarketDataBook market;private final PaperEquitySnapshotRepository equity;private final PaperDrawdownGuardService drawdownGuard;public PaperPortfolioAnalyticsService(PaperTradingService paper,PaperPositionRepository positions,MultiSourceMarketDataBook market,PaperEquitySnapshotRepository equity,PaperDrawdownGuardService drawdownGuard){this.paper=paper;this.positions=positions;this.market=market;this.equity=equity;this.drawdownGuard=drawdownGuard;}public PaperEquitySnapshotEntity mark(int minSources,long maxAge,long maxSkew,double maxDiv){
    PaperAccountEntity account=paper.account();
    List<PaperPositionEntity> open=positions.findByStatus(PaperPositionStatus.OPEN);
    double unrealized=0,gross=0;
    try{
        for(PaperPositionEntity position:open){
            double price=market.consensus(position.getSymbol(),minSources,maxAge,maxSkew,maxDiv).medianPrice();
            if(!Double.isFinite(price)||price<=0||!Double.isFinite(position.getQuantity())||
               position.getQuantity()<=0||!Double.isFinite(position.getNotional())){
                throw new IllegalStateException("INVALID_PAPER_MARK_EVIDENCE");
            }
            unrealized+=(position.getSide()==TradeSide.LONG?price-position.getEntryPrice():
                    position.getEntryPrice()-price)*position.getQuantity();
            gross+=position.getNotional();
        }
        double equityValue=account.getCashBalance()+unrealized;
        if(!Double.isFinite(unrealized)||!Double.isFinite(gross)||
           !Double.isFinite(equityValue)||equityValue<=0){
            throw new IllegalStateException("INVALID_PAPER_EQUITY_MARK");
        }
        PaperDrawdownGuardService.OpeningDecision circuit=
                drawdownGuard.observeMarkedEquity(account,equityValue);
        if("CORRUPT_PAPER_CIRCUIT_STATE".equals(circuit.reason())){
            throw new IllegalStateException("CORRUPT_PAPER_CIRCUIT_STATE");
        }
        return equity.save(new PaperEquitySnapshotEntity(UUID.randomUUID(),
                account.getCashBalance(),equityValue,unrealized,gross,open.size()));
    }catch(RuntimeException failure){
        // Invalid/stale/divergent market consensus MUST fail closed for future
        // paper opens. The separate circuit transaction survives this error.
        drawdownGuard.observeMarkedEquity(account,Double.NaN);
        throw failure;
    }
}
public List<PaperEquitySnapshotEntity> history(){return equity.findTop200ByOrderByObservedAtDesc();}public List<PositionCorrelation> correlations(int minSources,long maxAge,long maxSkew,double maxDiv){List<String>symbols=positions.findByStatus(PaperPositionStatus.OPEN).stream().map(PaperPositionEntity::getSymbol).distinct().sorted().toList();List<PositionCorrelation>out=new ArrayList<>();Map<String,List<Double>>returns=new HashMap<>();for(String s:symbols){List<Double>c=market.consensusCloses(s,minSources,maxAge,maxSkew,maxDiv);List<Double>r=new ArrayList<>();for(int i=1;i<c.size();i++)r.add(c.get(i-1)==0?0:c.get(i)/c.get(i-1)-1);returns.put(s,r);}for(int i=0;i<symbols.size();i++)for(int j=i+1;j<symbols.size();j++){String a=symbols.get(i),b=symbols.get(j);List<Double>x=returns.get(a),y=returns.get(b);int n=Math.min(x.size(),y.size());if(n<3)continue;out.add(new PositionCorrelation(a,b,corr(x.subList(x.size()-n,x.size()),y.subList(y.size()-n,y.size())),n));}return List.copyOf(out);}private double corr(List<Double>x,List<Double>y){double mx=x.stream().mapToDouble(Double::doubleValue).average().orElse(0),my=y.stream().mapToDouble(Double::doubleValue).average().orElse(0),num=0,xx=0,yy=0;for(int i=0;i<x.size();i++){double a=x.get(i)-mx,b=y.get(i)-my;num+=a*b;xx+=a*a;yy+=b*b;}return xx==0||yy==0?0:num/Math.sqrt(xx*yy);}}

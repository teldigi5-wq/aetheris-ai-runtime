package io.aetheris.orchestrator.model;
import org.springframework.stereotype.Service;
import java.util.*;
@Service
public class ModelArenaService {
    private final ModelArenaMeasurementRepository repository; public ModelArenaService(ModelArenaMeasurementRepository repository){this.repository=repository;}
    public ModelArenaMeasurementEntity record(ModelArenaRecordRequest r){if(r.qualityScore()!=null&&(r.qualityScore()<0||r.qualityScore()>100))throw new IllegalArgumentException("Quality score must be between 0 and 100");return repository.save(new ModelArenaMeasurementEntity(UUID.randomUUID(),r.providerId(),r.model()==null?"":r.model(),r.scenario()==null?"runtime":r.scenario(),r.success(),r.latencyMs(),r.qualityScore(),r.detail()));}
    public void runtime(String providerId,String model,boolean success,long latencyMs,String detail){record(new ModelArenaRecordRequest(providerId,model,"runtime",success,latencyMs,null,detail));}
    public void autoRuntime(String providerId,String model,boolean success,long latencyMs,String response,String detail){Double quality=success?heuristicQuality(response):0d;record(new ModelArenaRecordRequest(providerId,model,"runtime-auto",success,latencyMs,quality,detail));}
    public double averageQuality(String providerId){return repository.findTop50ByProviderIdOrderByCreatedAtDesc(providerId).stream().map(ModelArenaMeasurementEntity::getQualityScore).filter(Objects::nonNull).mapToDouble(Double::doubleValue).average().orElse(0d);}
    public List<ModelArenaMeasurementEntity> recent(){return repository.findTop100ByOrderByCreatedAtDesc();}
    private double heuristicQuality(String response){if(response==null||response.isBlank())return 0d;int len=response.trim().length();double score=50d;if(len>=120)score+=10;if(len>=400)score+=10;if(len>=1200)score+=5;if(response.contains("\n"))score+=5;String lower=response.toLowerCase(Locale.ROOT);if(lower.contains("i don't know")||lower.contains("cannot answer"))score-=10;return Math.max(0d,Math.min(100d,score));}
}

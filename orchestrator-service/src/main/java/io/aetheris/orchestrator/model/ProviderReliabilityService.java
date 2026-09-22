package io.aetheris.orchestrator.model;

import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;

@Service
public class ProviderReliabilityService {
    private final ProviderHealthRepository repository; private final int failureThreshold; private final Duration cooldown;
    public ProviderReliabilityService(ProviderHealthRepository repository,@Value("${aetheris.providers.circuit-failure-threshold:3}") int failureThreshold,@Value("${aetheris.providers.circuit-cooldown-seconds:60}") long cooldownSeconds){this.repository=repository;this.failureThreshold=Math.max(1,failureThreshold);this.cooldown=Duration.ofSeconds(Math.max(5,cooldownSeconds));}
    public boolean canAttempt(String providerId){return repository.findById(providerId).map(h->!h.circuitOpen(Instant.now())).orElse(true);}
    public int scoreAdjustment(String providerId){return repository.findById(providerId).map(h->{if(h.circuitOpen(Instant.now()))return -1000;long total=h.getSuccessCount()+h.getFailureCount();if(total==0)return 0;double rate=(double)h.getSuccessCount()/total;return (int)Math.round(rate*15)-Math.min(20,h.getConsecutiveFailures()*5);}).orElse(0);}
    @Transactional public void success(String providerId,long latencyMs){ProviderHealthEntity h=repository.findById(providerId).orElseGet(()->new ProviderHealthEntity(providerId));h.success(latencyMs);repository.save(h);}
    @Transactional public void failure(String providerId,String detail){ProviderHealthEntity h=repository.findById(providerId).orElseGet(()->new ProviderHealthEntity(providerId));h.failure(detail,failureThreshold,cooldown);repository.save(h);}
    public List<ProviderHealthSnapshot> snapshots(){return repository.findAll().stream().map(this::snapshot).sorted(Comparator.comparing(ProviderHealthSnapshot::providerId)).toList();}
    public ProviderHealthSnapshot snapshot(String providerId){return repository.findById(providerId).map(this::snapshot).orElse(new ProviderHealthSnapshot(providerId,0,0,0,0,false,null,null));}
    private ProviderHealthSnapshot snapshot(ProviderHealthEntity h){long total=h.getSuccessCount()+h.getFailureCount();long avg=h.getSuccessCount()==0?0:h.getTotalLatencyMs()/h.getSuccessCount();return new ProviderHealthSnapshot(h.getProviderId(),h.getSuccessCount(),h.getFailureCount(),h.getConsecutiveFailures(),avg,h.circuitOpen(Instant.now()),h.getCircuitOpenUntil(),h.getLastError());}
}

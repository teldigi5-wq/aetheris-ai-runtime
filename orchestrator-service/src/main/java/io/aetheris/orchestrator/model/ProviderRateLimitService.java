package io.aetheris.orchestrator.model;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;

@Service
public class ProviderRateLimitService {
    private final ProviderRateLimitRepository repository; public ProviderRateLimitService(ProviderRateLimitRepository repository){this.repository=repository;}
    public boolean canAttempt(String providerId){return repository.findById(providerId).map(r->!r.blocked(Instant.now())).orElse(true);}
    @Transactional public ProviderRateLimitSnapshot mark(String providerId,long retryAfterSeconds,Long remainingUnits,Instant quotaResetAt,String reason){ProviderRateLimitEntity e=repository.findById(providerId).orElseGet(()->new ProviderRateLimitEntity(providerId));e.mark(retryAfterSeconds<=0?60:retryAfterSeconds,remainingUnits,quotaResetAt,reason);return snapshot(repository.save(e));}
    @Transactional public ProviderRateLimitSnapshot clear(String providerId){ProviderRateLimitEntity e=repository.findById(providerId).orElseGet(()->new ProviderRateLimitEntity(providerId));e.clear();return snapshot(repository.save(e));}
    public ProviderRateLimitSnapshot snapshot(String providerId){return repository.findById(providerId).map(this::snapshot).orElse(new ProviderRateLimitSnapshot(providerId,false,null,null,null,null));}
    public List<ProviderRateLimitSnapshot> snapshots(){return repository.findAll().stream().map(this::snapshot).sorted(Comparator.comparing(ProviderRateLimitSnapshot::providerId)).toList();}
    private ProviderRateLimitSnapshot snapshot(ProviderRateLimitEntity e){return new ProviderRateLimitSnapshot(e.getProviderId(),e.blocked(Instant.now()),e.getBlockedUntil(),e.getRemainingUnits(),e.getQuotaResetAt(),e.getReason());}
}

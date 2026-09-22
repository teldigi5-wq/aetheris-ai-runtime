package io.aetheris.orchestrator.host;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class HostRegistryService {
    private final HostNodeRepository repository;
    public HostRegistryService(HostNodeRepository repository){this.repository=repository;}
    public HostNodeEntity register(HostRegistrationRequest request){
        if(repository.findByHostKey(request.hostKey().trim()).isPresent()) throw new IllegalArgumentException("Host key is already registered");
        if(!request.publicKeyFingerprint().matches("[A-Fa-f0-9:-]{16,200}")) throw new IllegalArgumentException("Host public-key fingerprint is invalid");
        return repository.save(new HostNodeEntity(UUID.randomUUID(),request.hostKey().trim(),request.displayName().trim(),request.platform().trim(),request.capabilities(),request.publicKeyFingerprint().trim()));
    }
    public List<HostNodeEntity> list(){return repository.findTop100ByOrderByCreatedAtDesc();}
    public HostNodeEntity getRequired(UUID id){return repository.findById(id).orElseThrow(()->new NoSuchElementException("Unknown host: "+id));}
    @Transactional public HostNodeEntity save(HostNodeEntity host){return repository.save(host);}
    public void requireExecutable(UUID id){HostNodeEntity host=getRequired(id);if(!host.canExecute())throw new IllegalStateException("Host is not paired and online; execution is unavailable");}
}

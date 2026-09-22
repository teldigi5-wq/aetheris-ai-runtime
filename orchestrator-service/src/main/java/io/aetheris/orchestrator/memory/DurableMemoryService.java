package io.aetheris.orchestrator.memory;

import io.aetheris.orchestrator.memory.MemoryContracts.*;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DurableMemoryService {
    private final MemoryRecordRepository records;
    private final MemoryEncryptionService encryption;
    private final MeterRegistry metrics;

    public DurableMemoryService(MemoryRecordRepository records,MemoryEncryptionService encryption,MeterRegistry metrics){
        this.records=records; this.encryption=encryption; this.metrics=metrics;
    }

    @Transactional
    public MemoryView write(String ownerId,WriteRequest request){
        String owner=require(ownerId,"ownerId");
        Objects.requireNonNull(request,"request");
        MemoryScope scope=request.scope()==null?MemoryScope.PROJECT:request.scope();
        MemoryEncryptionService.ProtectedPayload protectedPayload=encryption.protect(require(request.content(),"content"),request.sensitive());
        MemoryRecordEntity record=new MemoryRecordEntity(
                UUID.randomUUID(),owner,scope,request.projectId(),require(request.key(),"key"),
                protectedPayload.payload(),protectedPayload.encrypted(),request.sensitive(),request.tags(),
                request.provenanceType(),request.provenanceReference(),request.expiresAt());
        records.save(record);
        metrics.counter("aetheris_memory_writes_total","result","success","scope",scope.name()).increment();
        return view(record);
    }

    public MemoryView get(String ownerId,String projectId,UUID id){
        MemoryRecordEntity record=records.findById(Objects.requireNonNull(id,"id"))
                .orElseThrow(()->miss("Unknown memory record"));
        authorize(record,ownerId,projectId);
        if(record.expiredAt(Instant.now())){metrics.counter("aetheris_memory_retention_expired_total").increment();throw miss("Memory record expired");}
        metrics.counter("aetheris_memory_retrieval_total","result","hit").increment();
        return view(record);
    }

    @Transactional
    public MemoryView correct(String ownerId,String projectId,UUID id,CorrectRequest request){
        MemoryRecordEntity record=records.findById(Objects.requireNonNull(id,"id"))
                .orElseThrow(()->miss("Unknown memory record"));
        authorize(record,ownerId,projectId);
        Objects.requireNonNull(request,"request");
        MemoryEncryptionService.ProtectedPayload protectedPayload=encryption.protect(require(request.content(),"content"),record.isSensitive());
        record.update(record.getScope(),record.getProjectId(),record.getMemoryKey(),protectedPayload.payload(),protectedPayload.encrypted(),
                record.isSensitive(),request.tags()==null?record.getTags():request.tags(),record.getProvenanceType(),
                request.provenanceReference()==null?record.getProvenanceReference():request.provenanceReference(),request.expiresAt());
        records.save(record);
        metrics.counter("aetheris_memory_corrections_total","scope",record.getScope().name()).increment();
        return view(record);
    }

    @Transactional
    public void delete(String ownerId,String projectId,UUID id){
        MemoryRecordEntity record=records.findById(Objects.requireNonNull(id,"id"))
                .orElseThrow(()->miss("Unknown memory record"));
        authorize(record,ownerId,projectId);
        records.delete(record);
        metrics.counter("aetheris_memory_deletions_total","scope",record.getScope().name()).increment();
    }

    public List<MemoryView> inspect(String ownerId,MemoryScope scope,String projectId,int limit){
        String owner=require(ownerId,"ownerId");
        int bounded=bounded(limit);
        Instant now=Instant.now();
        return records.findTop500ByOwnerIdOrderByUpdatedAtDesc(owner).stream()
                .filter(r->!r.expiredAt(now))
                .filter(r->scope==null||r.getScope()==scope)
                .filter(r->projectMatches(r,projectId))
                .sorted(deterministicRecordOrder())
                .limit(bounded)
                .map(this::view)
                .toList();
    }

    public List<SearchResult> search(String ownerId,String query,MemoryScope scope,String projectId,int limit){
        String owner=require(ownerId,"ownerId");
        Set<String> queryTokens=tokens(query);
        if(queryTokens.isEmpty()){metrics.counter("aetheris_memory_retrieval_total","result","miss").increment();return List.of();}
        Instant now=Instant.now();
        List<SearchResult> results=records.findTop500ByOwnerIdOrderByUpdatedAtDesc(owner).stream()
                .filter(r->!r.expiredAt(now))
                .filter(r->scope==null||r.getScope()==scope)
                .filter(r->projectMatches(r,projectId))
                .map(r->rank(r,query,queryTokens))
                .filter(r->r.score()>0d)
                .sorted(Comparator.comparingDouble(SearchResult::score).reversed()
                        .thenComparing((SearchResult r)->r.memory().updatedAt(),Comparator.reverseOrder())
                        .thenComparing(r->r.memory().id().toString()))
                .limit(bounded(limit))
                .toList();
        metrics.counter("aetheris_memory_retrieval_total","result",results.isEmpty()?"miss":"hit").increment();
        return results;
    }

    @Transactional
    public PurgeResult purgeExpired(){
        Instant now=Instant.now();
        List<MemoryRecordEntity> expired=records.findByExpiresAtLessThanEqual(now);
        if(!expired.isEmpty())records.deleteAllInBatch(expired);
        metrics.counter("aetheris_memory_retention_purged_total").increment(expired.size());
        return new PurgeResult(expired.size(),now);
    }

    private SearchResult rank(MemoryRecordEntity record,String query,Set<String> q){
        MemoryView memory=view(record);
        Set<String> body=tokens(memory.key()+" "+memory.content()+" "+String.join(" ",memory.tags()));
        Set<String> intersection=new HashSet<>(q); intersection.retainAll(body);
        Set<String> union=new HashSet<>(q); union.addAll(body);
        double lexical=union.isEmpty()?0d:(double)intersection.size()/union.size();
        double keyBoost=memory.key().toLowerCase(Locale.ROOT).contains(query.trim().toLowerCase(Locale.ROOT))?0.35d:0d;
        double score=Math.min(1d,lexical+keyBoost);
        String reason="owner+scope+project boundary; lexical-jaccard="+String.format(Locale.ROOT,"%.4f",lexical)+"; key-boost="+String.format(Locale.ROOT,"%.2f",keyBoost)+"; provenance="+memory.provenanceType();
        return new SearchResult(memory,score,reason);
    }

    private MemoryView view(MemoryRecordEntity record){
        String content=encryption.reveal(record.getPayload(),record.isEncrypted());
        return new MemoryView(record.getId(),record.getScope(),record.getProjectId(),record.getMemoryKey(),content,
                record.isSensitive(),record.getTags(),record.getProvenanceType(),record.getProvenanceReference(),
                record.getCreatedAt(),record.getUpdatedAt(),record.getExpiresAt());
    }

    private void authorize(MemoryRecordEntity record,String ownerId,String projectId){
        String owner=require(ownerId,"ownerId");
        boolean ownerOk=record.getOwnerId().equals(owner);
        boolean projectOk=record.getScope()!=MemoryScope.PROJECT || (projectId!=null&&!projectId.isBlank()&&record.getProjectId().equals(projectId.trim()));
        if(!ownerOk||!projectOk){metrics.counter("aetheris_memory_authorization_denials_total").increment();throw new SecurityException("Memory access denied by owner/project boundary");}
    }

    private boolean projectMatches(MemoryRecordEntity record,String projectId){
        if(record.getScope()!=MemoryScope.PROJECT)return projectId==null||projectId.isBlank();
        return projectId!=null&&!projectId.isBlank()&&record.getProjectId().equals(projectId.trim());
    }

    private Comparator<MemoryRecordEntity> deterministicRecordOrder(){
        return Comparator.comparing(MemoryRecordEntity::getUpdatedAt,Comparator.reverseOrder()).thenComparing(r->r.getId().toString());
    }

    private NoSuchElementException miss(String message){metrics.counter("aetheris_memory_retrieval_total","result","miss").increment();return new NoSuchElementException(message);}
    private int bounded(int limit){return Math.max(1,Math.min(limit,100));}
    private String require(String value,String label){if(value==null||value.isBlank())throw new IllegalArgumentException(label+" is required");return value.trim();}
    private Set<String> tokens(String value){
        if(value==null)return Set.of();
        return Arrays.stream(value.toLowerCase(Locale.ROOT).split("[^a-z0-9_+#.-]+"))
                .map(String::trim).filter(s->s.length()>1).collect(Collectors.toCollection(TreeSet::new));
    }
}

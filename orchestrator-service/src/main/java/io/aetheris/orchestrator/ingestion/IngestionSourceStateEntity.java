package io.aetheris.orchestrator.ingestion;

import io.aetheris.orchestrator.memory.MemoryScope;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name="aetheris_ingestion_source_state", uniqueConstraints=@UniqueConstraint(columnNames={"sourceKind","sourceId","namespace"}))
public class IngestionSourceStateEntity {
    @Id private UUID id;
    @Column(nullable=false,length=32) private String sourceKind;
    @Column(nullable=false,length=320) private String sourceId;
    @Column(nullable=false,length=160) private String namespace;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=24) private MemoryScope scope;
    @Column(nullable=false,length=64) private String contentHash;
    @Column(nullable=false) private int revision;
    @Column(nullable=false) private boolean tombstoned;
    @Column(nullable=false) private int chunkCount;
    @Column(nullable=false,length=8000) private String nodeIdsCsv;
    @Column(nullable=false) private Instant updatedAt;
    protected IngestionSourceStateEntity(){}
    public IngestionSourceStateEntity(UUID id,String kind,String sourceId,String namespace,MemoryScope scope){this.id=id;this.sourceKind=kind;this.sourceId=sourceId;this.namespace=namespace;this.scope=scope==null?MemoryScope.PROJECT:scope;this.contentHash="";this.nodeIdsCsv="";this.updatedAt=Instant.now();}
    public UUID getId(){return id;} public String getSourceKind(){return sourceKind;} public String getSourceId(){return sourceId;} public String getNamespace(){return namespace;} public MemoryScope getScope(){return scope;} public String getContentHash(){return contentHash;} public int getRevision(){return revision;} public boolean isTombstoned(){return tombstoned;} public int getChunkCount(){return chunkCount;} public Instant getUpdatedAt(){return updatedAt;}
    public List<UUID> nodeIds(){if(nodeIdsCsv==null||nodeIdsCsv.isBlank())return List.of();List<UUID> out=new ArrayList<>();for(String p:nodeIdsCsv.split(","))try{out.add(UUID.fromString(p));}catch(Exception ignored){}return List.copyOf(out);}
    public void activate(String hash,MemoryScope scope,List<UUID> nodeIds){this.contentHash=hash;this.scope=scope==null?MemoryScope.PROJECT:scope;this.revision=Math.max(1,this.revision+1);this.tombstoned=false;this.chunkCount=nodeIds.size();this.nodeIdsCsv=nodeIds.stream().map(UUID::toString).collect(java.util.stream.Collectors.joining(","));this.updatedAt=Instant.now();}
    public void tombstone(){this.revision=Math.max(1,this.revision+1);this.tombstoned=true;this.contentHash="";this.chunkCount=0;this.nodeIdsCsv="";this.updatedAt=Instant.now();}
}

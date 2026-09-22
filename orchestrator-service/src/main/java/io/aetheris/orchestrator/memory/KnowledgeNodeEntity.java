package io.aetheris.orchestrator.memory;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Entity
@Table(name="aetheris_knowledge_nodes")
public class KnowledgeNodeEntity {
    @Id private UUID id;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=24) private MemoryScope scope;
    @Column(nullable=false,length=160) private String namespace;
    @Column(nullable=false,length=240) private String memoryKey;
    @Column(nullable=false,length=16000) private String content;
    @Column(nullable=false,length=4000) private String tagsCsv;
    @Column(nullable=false) private boolean protectedData;
    @Column(nullable=false) private Instant createdAt;
    @Column(nullable=false) private Instant updatedAt;
    protected KnowledgeNodeEntity(){}
    public KnowledgeNodeEntity(UUID id,MemoryScope scope,String namespace,String memoryKey,String content,Set<String> tags,boolean protectedData){this.id=id;this.createdAt=Instant.now();update(scope,namespace,memoryKey,content,tags,protectedData);}
    public UUID getId(){return id;} public MemoryScope getScope(){return scope;} public String getNamespace(){return namespace;} public String getMemoryKey(){return memoryKey;} public String getContent(){return content;} public boolean isProtectedData(){return protectedData;} public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
    public Set<String> getTags(){if(tagsCsv==null||tagsCsv.isBlank())return Set.of();return Arrays.stream(tagsCsv.split(",")).map(String::trim).filter(s->!s.isBlank()).collect(Collectors.toCollection(TreeSet::new));}
    public void update(MemoryScope scope,String namespace,String memoryKey,String content,Set<String> tags,boolean protectedData){this.scope=scope==null?MemoryScope.PROJECT:scope;this.namespace=require(namespace,"namespace",160);this.memoryKey=require(memoryKey,"memory key",240);this.content=require(content,"memory content",16000);this.tagsCsv=tags==null?"":tags.stream().filter(Objects::nonNull).map(String::trim).filter(s->!s.isBlank()).map(String::toLowerCase).distinct().sorted().collect(Collectors.joining(","));this.protectedData=protectedData;this.updatedAt=Instant.now();if(this.createdAt==null)this.createdAt=this.updatedAt;}
    private String require(String value,String label,int max){if(value==null||value.isBlank())throw new IllegalArgumentException(label+" is required");String v=value.trim();if(v.length()>max)throw new IllegalArgumentException(label+" is too long");return v;}
}

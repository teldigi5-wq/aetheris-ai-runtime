package io.aetheris.orchestrator.ingestion;

import io.aetheris.orchestrator.memory.*;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class KnowledgeIngestionService {
    private final SemanticMemoryService memory; private final VectorMemoryIndexService vector; private final EmbeddingAdapter embedding;
    public KnowledgeIngestionService(SemanticMemoryService memory,VectorMemoryIndexService vector,EmbeddingAdapter embedding){this.memory=memory;this.vector=vector;this.embedding=embedding;}
    @Transactional public KnowledgeIngestionResult ingest(IngestKnowledgeRequest r){String kind=require(r.sourceKind(),"source kind").toUpperCase(Locale.ROOT);if(!Set.of("REPOSITORY","DOCUMENT","OWNER_FILE","TEXT").contains(kind))throw new IllegalArgumentException("Unsupported source kind: "+kind);String sourceId=require(r.sourceId(),"source id"),content=require(r.content(),"content");String ns=r.namespace()==null||r.namespace().isBlank()?kind.toLowerCase(Locale.ROOT):r.namespace().trim();List<String> chunks=chunk(content,1200);List<UUID> ids=new ArrayList<>();for(int i=0;i<chunks.size();i++){Set<String> tags=new LinkedHashSet<>(r.tags());tags.add(kind.toLowerCase(Locale.ROOT));tags.add("ingested");String key=sourceId+"#"+(i+1);KnowledgeNodeEntity node=memory.upsert(new UpsertKnowledgeRequest(r.scope(),ns,key,chunks.get(i),tags,r.protectedData()));vector.index(node.getId());ids.add(node.getId());}return new KnowledgeIngestionResult(kind,sourceId,chunks.size(),List.copyOf(ids),embedding.id());}
    private List<String> chunk(String content,int max){List<String> out=new ArrayList<>();StringBuilder current=new StringBuilder();for(String paragraph:content.replace("\r","").split("\n\s*\n")){String p=paragraph.trim();if(p.isBlank())continue;if(current.length()>0&&current.length()+p.length()+2>max){out.add(current.toString());current.setLength(0);}while(p.length()>max){out.add(p.substring(0,max));p=p.substring(max);}if(!p.isBlank()){if(current.length()>0)current.append("\n\n");current.append(p);}}if(current.length()>0)out.add(current.toString());if(out.isEmpty())out.add(content.substring(0,Math.min(content.length(),max)));return out;}
    private String require(String v,String label){if(v==null||v.isBlank())throw new IllegalArgumentException(label+" is required");return v.trim();}
}

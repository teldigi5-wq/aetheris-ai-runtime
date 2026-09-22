package io.aetheris.orchestrator.memory;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SemanticMemoryService {
    private static final String TOMBSTONE_TAG="stage9-tombstoned";
    private final KnowledgeNodeRepository nodes; private final KnowledgeEdgeRepository edges;
    public SemanticMemoryService(KnowledgeNodeRepository nodes,KnowledgeEdgeRepository edges){this.nodes=nodes;this.edges=edges;}
    @Transactional public KnowledgeNodeEntity upsert(UpsertKnowledgeRequest r){MemoryScope scope=r.scope()==null?MemoryScope.PROJECT:r.scope();String namespace=require(r.namespace(),"namespace");String key=require(r.key(),"key");KnowledgeNodeEntity node=nodes.findByScopeAndNamespaceAndMemoryKey(scope,namespace,key).orElseGet(()->new KnowledgeNodeEntity(UUID.randomUUID(),scope,namespace,key,r.content(),r.tags(),r.protectedData()));node.update(scope,namespace,key,r.content(),r.tags(),r.protectedData());return nodes.save(node);}
    @Transactional public KnowledgeEdgeEntity link(LinkKnowledgeRequest r){KnowledgeNodeEntity from=required(r.fromNodeId()),to=required(r.toNodeId());return edges.save(new KnowledgeEdgeEntity(UUID.randomUUID(),from.getId(),to.getId(),r.relation()));}
    public KnowledgeNodeEntity required(UUID id){return nodes.findById(id).orElseThrow(()->new NoSuchElementException("Unknown knowledge node: "+id));}
    public List<KnowledgeNodeEntity> recent(){return nodes.findTop500ByOrderByUpdatedAtDesc();}
    public List<KnowledgeEdgeEntity> edges(){return edges.findTop500ByOrderByCreatedAtDesc();}
    public List<MemorySearchResult> search(String query,MemoryScope scope,String namespace,boolean includeProtected,int limit){Set<String> q=tokens(query);if(q.isEmpty())return List.of();return nodes.findTop500ByOrderByUpdatedAtDesc().stream().filter(n->!n.getTags().contains(TOMBSTONE_TAG)).filter(n->scope==null||n.getScope()==scope).filter(n->namespace==null||namespace.isBlank()||n.getNamespace().equalsIgnoreCase(namespace.trim())).filter(n->includeProtected||!n.isProtectedData()).map(n->{Set<String> body=tokens(n.getMemoryKey()+" "+n.getContent()+" "+String.join(" ",n.getTags()));Set<String> intersection=new HashSet<>(q);intersection.retainAll(body);Set<String> union=new HashSet<>(q);union.addAll(body);double jaccard=union.isEmpty()?0d:(double)intersection.size()/union.size();double keyBoost=n.getMemoryKey().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT).trim())?0.35:0d;double score=Math.min(1d,jaccard+keyBoost);return new MemorySearchResult(n,score,"local lexical-semantic overlap");}).filter(r->r.score()>0).sorted(Comparator.comparingDouble(MemorySearchResult::score).reversed()).limit(Math.max(1,Math.min(limit,50))).toList();}
    private Set<String> tokens(String value){if(value==null)return Set.of();return Arrays.stream(value.toLowerCase(Locale.ROOT).split("[^a-z0-9_+#.-]+" )).map(String::trim).filter(s->s.length()>1).collect(Collectors.toSet());}
    private String require(String v,String label){if(v==null||v.isBlank())throw new IllegalArgumentException(label+" is required");return v.trim();}
}

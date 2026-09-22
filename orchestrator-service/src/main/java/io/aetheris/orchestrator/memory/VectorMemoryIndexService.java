package io.aetheris.orchestrator.memory;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class VectorMemoryIndexService {
    private static final String TOMBSTONE_TAG="stage9-tombstoned";
    private final MemoryVectorRepository vectors; private final KnowledgeNodeRepository nodes; private final SemanticMemoryService lexical; private final EmbeddingAdapter embedding;
    public VectorMemoryIndexService(MemoryVectorRepository vectors,KnowledgeNodeRepository nodes,SemanticMemoryService lexical,EmbeddingAdapter embedding){this.vectors=vectors;this.nodes=nodes;this.lexical=lexical;this.embedding=embedding;}
    @Transactional public MemoryVectorEntity index(UUID nodeId){KnowledgeNodeEntity node=nodes.findById(nodeId).orElseThrow(()->new NoSuchElementException("Unknown knowledge node: "+nodeId));float[] vector=embedding.embed(node.getMemoryKey()+"\n"+node.getContent()+"\n"+String.join(" ",node.getTags()));MemoryVectorEntity entity=vectors.findByKnowledgeNodeIdAndAdapterId(nodeId,embedding.id()).orElseGet(()->new MemoryVectorEntity(UUID.randomUUID(),nodeId,embedding.id(),vector));entity.update(nodeId,embedding.id(),vector);return vectors.save(entity);}
    @Transactional public int rebuild(){int count=0;for(KnowledgeNodeEntity node:nodes.findTop500ByOrderByUpdatedAtDesc()){if(node.getTags().contains(TOMBSTONE_TAG))continue;index(node.getId());count++;}return count;}
    public List<VectorMemorySearchResult> search(String query,MemoryScope scope,String namespace,boolean includeProtected,int limit){float[] q=embedding.embed(query);if(norm(q)==0d)return lexicalFallback(query,scope,namespace,includeProtected,limit);List<VectorMemorySearchResult> out=new ArrayList<>();for(MemoryVectorEntity vector:vectors.findTop1000ByAdapterIdOrderByUpdatedAtDesc(embedding.id())){KnowledgeNodeEntity node=nodes.findById(vector.getKnowledgeNodeId()).orElse(null);if(node==null||node.getTags().contains(TOMBSTONE_TAG))continue;if(scope!=null&&node.getScope()!=scope)continue;if(namespace!=null&&!namespace.isBlank()&&!node.getNamespace().equalsIgnoreCase(namespace.trim()))continue;if(!includeProtected&&node.isProtectedData())continue;double score=cosine(q,vector.vector());if(score>0d)out.add(new VectorMemorySearchResult(node,score,"local vector cosine"));}out.sort(Comparator.comparingDouble(VectorMemorySearchResult::score).reversed());if(out.isEmpty())return lexicalFallback(query,scope,namespace,includeProtected,limit);return out.stream().limit(Math.max(1,Math.min(limit,50))).toList();}
    private List<VectorMemorySearchResult> lexicalFallback(String q,MemoryScope s,String n,boolean p,int l){return lexical.search(q,s,n,p,l).stream().map(r->new VectorMemorySearchResult(r.node(),r.score(),"lexical fallback")).toList();}
    private double cosine(float[] a,float[] b){int n=Math.min(a.length,b.length);double dot=0,aa=0,bb=0;for(int i=0;i<n;i++){dot+=a[i]*b[i];aa+=a[i]*a[i];bb+=b[i]*b[i];}return aa==0||bb==0?0:dot/(Math.sqrt(aa)*Math.sqrt(bb));}private double norm(float[] v){double n=0;for(float x:v)n+=x*x;return Math.sqrt(n);}
}

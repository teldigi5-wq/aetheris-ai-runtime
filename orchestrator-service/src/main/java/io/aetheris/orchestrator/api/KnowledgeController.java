package io.aetheris.orchestrator.api;

import io.aetheris.orchestrator.memory.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/orchestrator/knowledge")
public class KnowledgeController {
 private final SemanticMemoryService memory; public KnowledgeController(SemanticMemoryService memory){this.memory=memory;}
 @PostMapping("/nodes") public KnowledgeNodeEntity upsert(@RequestBody UpsertKnowledgeRequest r){return memory.upsert(r);}
 @GetMapping("/nodes") public List<KnowledgeNodeEntity> recent(){return memory.recent();}
 @PostMapping("/edges") public KnowledgeEdgeEntity link(@RequestBody LinkKnowledgeRequest r){return memory.link(r);}
 @GetMapping("/edges") public List<KnowledgeEdgeEntity> edges(){return memory.edges();}
 @GetMapping("/search") public List<MemorySearchResult> search(@RequestParam String q,@RequestParam(required=false) MemoryScope scope,@RequestParam(required=false) String namespace,@RequestParam(defaultValue="false") boolean includeProtected,@RequestParam(defaultValue="10") int limit){return memory.search(q,scope,namespace,includeProtected,limit);}
}

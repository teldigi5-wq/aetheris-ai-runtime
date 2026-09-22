package io.aetheris.orchestrator.api;

import io.aetheris.orchestrator.memory.*;
import io.aetheris.orchestrator.memory.MemoryContracts.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/orchestrator/memory")
public class MemoryInspectorController {
    private final DurableMemoryService memory;
    private final MemoryEncryptionService encryption;

    public MemoryInspectorController(DurableMemoryService memory,MemoryEncryptionService encryption){
        this.memory=memory; this.encryption=encryption;
    }

    @GetMapping("/capabilities")
    public Map<String,Object> capabilities(){
        return Map.of(
                "phase","PHASE_9_MEMORY_PRODUCTION",
                "sensitiveEncryptionConfigured",encryption.configured(),
                "isolation","OWNER_AND_PROJECT",
                "deletion","PHYSICAL",
                "retention","ENFORCED_INTERNAL_SWEEP",
                "retrieval","DETERMINISTIC_WITH_PROVENANCE");
    }

    @PostMapping("/records")
    @ResponseStatus(HttpStatus.CREATED)
    public MemoryView write(@RequestHeader("X-Aetheris-Owner-Id") String ownerId,@RequestBody WriteRequest request){
        return memory.write(ownerId,request);
    }

    @GetMapping("/records/{id}")
    public MemoryView get(@RequestHeader("X-Aetheris-Owner-Id") String ownerId,
                          @RequestHeader(value="X-Aetheris-Project-Id",required=false) String projectId,
                          @PathVariable UUID id){
        return memory.get(ownerId,projectId,id);
    }

    @PutMapping("/records/{id}")
    public MemoryView correct(@RequestHeader("X-Aetheris-Owner-Id") String ownerId,
                              @RequestHeader(value="X-Aetheris-Project-Id",required=false) String projectId,
                              @PathVariable UUID id,@RequestBody CorrectRequest request){
        return memory.correct(ownerId,projectId,id,request);
    }

    @DeleteMapping("/records/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestHeader("X-Aetheris-Owner-Id") String ownerId,
                       @RequestHeader(value="X-Aetheris-Project-Id",required=false) String projectId,
                       @PathVariable UUID id){
        memory.delete(ownerId,projectId,id);
    }

    @GetMapping("/inspector")
    public List<MemoryView> inspect(@RequestHeader("X-Aetheris-Owner-Id") String ownerId,
                                    @RequestParam(required=false) MemoryScope scope,
                                    @RequestParam(required=false) String projectId,
                                    @RequestParam(defaultValue="50") int limit){
        return memory.inspect(ownerId,scope,projectId,limit);
    }

    @GetMapping("/search")
    public List<SearchResult> search(@RequestHeader("X-Aetheris-Owner-Id") String ownerId,
                                     @RequestParam String q,
                                     @RequestParam(required=false) MemoryScope scope,
                                     @RequestParam(required=false) String projectId,
                                     @RequestParam(defaultValue="10") int limit){
        return memory.search(ownerId,q,scope,projectId,limit);
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String,String>> denied(SecurityException ex){return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error",ex.getMessage()));}

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String,String>> missing(NoSuchElementException ex){return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error",ex.getMessage()));}

    @ExceptionHandler({IllegalArgumentException.class,IllegalStateException.class})
    public ResponseEntity<Map<String,String>> invalid(RuntimeException ex){return ResponseEntity.badRequest().body(Map.of("error",ex.getMessage()));}
}

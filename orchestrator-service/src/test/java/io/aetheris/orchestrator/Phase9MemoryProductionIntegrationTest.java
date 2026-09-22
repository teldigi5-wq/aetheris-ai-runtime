package io.aetheris.orchestrator;

import io.aetheris.orchestrator.memory.*;
import io.aetheris.orchestrator.memory.MemoryContracts.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={
        "aetheris.memory.master-key-b64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "aetheris.memory.retention-sweep-ms=3600000"
})
class Phase9MemoryProductionIntegrationTest {
    @Autowired DurableMemoryService memory;
    @Autowired MemoryRecordRepository repository;

    @Test
    void sensitiveMemoryIsEncryptedAtRestAndOnlyDecryptedAfterOwnerProjectAuthorization(){
        String owner="owner-"+UUID.randomUUID();
        String project="project-a-"+UUID.randomUUID();
        String secret="phase9-sensitive-"+UUID.randomUUID();
        MemoryView written=memory.write(owner,new WriteRequest(MemoryScope.PROJECT,project,"api-note",secret,true,
                Set.of("phase9","sensitive"),"OWNER_INPUT","test://phase9",null));

        MemoryRecordEntity raw=repository.findById(written.id()).orElseThrow();
        assertThat(raw.isEncrypted()).isTrue();
        assertThat(raw.getPayload()).doesNotContain(secret).startsWith("enc:v1:");
        assertThat(memory.get(owner,project,written.id()).content()).isEqualTo(secret);
        assertThatThrownBy(()->memory.get("other-owner",project,written.id())).isInstanceOf(SecurityException.class);
        assertThatThrownBy(()->memory.get(owner,"other-project",written.id())).isInstanceOf(SecurityException.class);
    }

    @Test
    void deterministicRetrievalNeverCrossesOwnerOrProjectBoundariesAndExplainsProvenance(){
        String owner="owner-"+UUID.randomUUID();
        String otherOwner="owner-"+UUID.randomUUID();
        String project="project-a-"+UUID.randomUUID();
        String otherProject="project-b-"+UUID.randomUUID();
        memory.write(owner,new WriteRequest(MemoryScope.PROJECT,project,"deployment","phase nine memory retrieval alpha",false,
                Set.of("retrieval"),"PROJECT_FILE","repo://docs/memory.md",null));
        memory.write(owner,new WriteRequest(MemoryScope.PROJECT,otherProject,"deployment","phase nine memory retrieval beta",false,
                Set.of("retrieval"),"PROJECT_FILE","repo://other/memory.md",null));
        memory.write(otherOwner,new WriteRequest(MemoryScope.PROJECT,project,"deployment","phase nine memory retrieval gamma",false,
                Set.of("retrieval"),"PROJECT_FILE","repo://foreign/memory.md",null));

        List<SearchResult> first=memory.search(owner,"memory retrieval",MemoryScope.PROJECT,project,10);
        List<SearchResult> second=memory.search(owner,"memory retrieval",MemoryScope.PROJECT,project,10);
        assertThat(first).hasSize(1);
        assertThat(second.stream().map(r->r.memory().id()).toList()).containsExactlyElementsOf(first.stream().map(r->r.memory().id()).toList());
        assertThat(first.getFirst().memory().projectId()).isEqualTo(project);
        assertThat(first.getFirst().reason()).contains("owner+scope+project boundary").contains("provenance=PROJECT_FILE");
    }

    @Test
    void correctionPreservesSensitiveEncryptionAndDeletionIsPhysical(){
        String owner="owner-"+UUID.randomUUID();
        String project="project-"+UUID.randomUUID();
        MemoryView written=memory.write(owner,new WriteRequest(MemoryScope.PROJECT,project,"credential-note","old-sensitive-value",true,
                Set.of("correctable"),"OWNER_INPUT","test://old",null));
        MemoryView corrected=memory.correct(owner,project,written.id(),new CorrectRequest("new-sensitive-value",Set.of("corrected"),"test://corrected",null));
        MemoryRecordEntity raw=repository.findById(written.id()).orElseThrow();
        assertThat(corrected.content()).isEqualTo("new-sensitive-value");
        assertThat(raw.isEncrypted()).isTrue();
        assertThat(raw.getPayload()).doesNotContain("new-sensitive-value");
        assertThat(corrected.provenanceReference()).isEqualTo("test://corrected");

        memory.delete(owner,project,written.id());
        assertThat(repository.findById(written.id())).isEmpty();
    }

    @Test
    void retentionHidesExpiredMemoryAndPurgePhysicallyRemovesIt(){
        String owner="owner-"+UUID.randomUUID();
        String project="project-"+UUID.randomUUID();
        MemoryView expired=memory.write(owner,new WriteRequest(MemoryScope.PROJECT,project,"expired","must disappear",false,
                Set.of("retention"),"OWNER_INPUT","test://expiry",Instant.now().minusSeconds(1)));
        assertThat(memory.search(owner,"disappear",MemoryScope.PROJECT,project,10)).isEmpty();
        PurgeResult purge=memory.purgeExpired();
        assertThat(purge.deleted()).isGreaterThanOrEqualTo(1);
        assertThat(repository.findById(expired.id())).isEmpty();
    }

    @Test
    void inspectorIsOwnerScopedAndSensitiveWritesFailClosedWithoutAKey(){
        String owner="owner-"+UUID.randomUUID();
        String other="owner-"+UUID.randomUUID();
        memory.write(owner,new WriteRequest(MemoryScope.OWNER,null,"preference","owner only",false,Set.of("owner"),"OWNER_INPUT",null,null));
        memory.write(other,new WriteRequest(MemoryScope.OWNER,null,"preference","other owner",false,Set.of("owner"),"OWNER_INPUT",null,null));
        assertThat(memory.inspect(owner,MemoryScope.OWNER,null,20)).extracting(MemoryView::content).contains("owner only").doesNotContain("other owner");

        MemoryEncryptionService noKey=new MemoryEncryptionService("",new SimpleMeterRegistry());
        assertThatThrownBy(()->noKey.protect("secret",true)).isInstanceOf(IllegalStateException.class).hasMessageContaining("requires");
    }
}

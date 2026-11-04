package io.aetheris.orchestrator.security;

import io.aetheris.orchestrator.AetherisOrchestratorApplication;
import io.aetheris.orchestrator.api.OperationsStreamController;
import io.aetheris.orchestrator.api.TaskController;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/**
 * FULL real Spring Boot AetherisOrchestratorApplication servlet lifecycle;
 * only TEST-config /__s0 handlers can succeed. This tests actual Runtime
 * RS256 signed-JTI verification with a deliberately SYNTHETIC revocation flag.
 * It is NOT an authenticated live B62 reader, owner/project grant, registered
 * active session, deployment, or production HTTP/SSE reauthorization.
 * Platform PR #321 separately tests real B62 PostgreSQL mTLS + HTTP/SSE.
 * Actual task/history/live event routes MUST remain 404 throughout.
 */
@SpringBootTest(
    webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes=AetherisOrchestratorApplication.class,
    properties={
        "server.address=127.0.0.1",
        "server.port=0",
        "spring.datasource.url=jdbc:h2:mem:s0_full_sse_revocation;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false",
        "spring.task.scheduling.enabled=false",
        "aetheris.dispatcher.enabled=false",
        "aetheris.syntra.owner-runtime.enabled=false",
        "aetheris.cloud-provider.enabled=false",
        "aetheris.execution.workspace-write-enabled=false",
        "aetheris.execution.allowed-commands=",
        "aetheris.execution.github.allowed-repositories=",
        "aetheris.host.simulation-only=true",
        "management.endpoints.enabled-by-default=false"
    })
@Import(RuntimeFullAppHttpSseRevocationLifecycleTest.TestOnly.class)
class RuntimeFullAppHttpSseRevocationLifecycleTest {
    private static final String ISS="https://identity.aetheris.example.invalid";
    private static final String AUD="aetheris-orchestrator-api";
    private static final String KID="s0-springboot-sse-lifecycle";
    private static final String FRAME="event: ready\ndata: synthetic-only\n\n";
    private static final KeyPair KEY=createKey();
    private static final AtomicBoolean NOT_REVOKED=new AtomicBoolean();
    private static final AtomicInteger DENIED_EVENT=new AtomicInteger();
    private static final CountDownLatch HTTP_READY=new CountDownLatch(1);
    private static final CountDownLatch HTTP_RELEASE=new CountDownLatch(1);
    private static final CountDownLatch SSE_READY=new CountDownLatch(1);
    private static final CountDownLatch SSE_RELEASE=new CountDownLatch(1);
    private static final CountDownLatch SSE_CLOSED=new CountDownLatch(1);

    @LocalServerPort int port;
    @Autowired org.springframework.context.ApplicationContext application;

    private static KeyPair createKey(){
        try {
            var generator=KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch(Exception e){throw new IllegalStateException(e);}
    }
    private static RuntimePinnedPrincipalVerifier verifier(){
        String pem="-----BEGIN PUBLIC KEY-----\n"+
            Base64.getMimeEncoder(64,new byte[]{'\n'})
                .encodeToString(KEY.getPublic().getEncoded())+
            "\n-----END PUBLIC KEY-----";
        return new RuntimePinnedPrincipalVerifier(pem,KID,ISS,AUD,Clock.systemUTC());
    }
    private static String jwt(KeyPair key,boolean includeId){
        Instant now=Instant.now().minusSeconds(5);
        var b=Jwts.builder().header().keyId(KID).and()
            .issuer(ISS).subject("synthetic@example.invalid")
            .claim("aud",List.of(AUD)).claim("uid",42L)
            .claim("role","DEVELOPER")
            .claim("scopes",List.of("orchestrator:read"))
            .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(240)));
        if(includeId)b.id(UUID.randomUUID().toString());
        return b.signWith(key.getPrivate(),Jwts.SIG.RS256).compact();
    }
    @TestConfiguration(proxyBeanMethods=false)
    static class TestOnly {
        @Bean S0Endpoint s0Only(){return new S0Endpoint();}
    }
    @RestController
    static class S0Endpoint {
        private static RuntimePinnedPrincipalVerifier.VerifiedRevocationFacts facts(
                HttpServletRequest r){
            try{return verifier().verifyBearerForRevocation(
                r.getHeader("Authorization")).requireRevocationFacts();}
            catch(IllegalArgumentException invalid){
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
            }
        }
        private static boolean current(
                RuntimePinnedPrincipalVerifier.VerifiedRevocationFacts facts){
            return NOT_REVOKED.get() && facts.signedIdentityUid()==42L
                &&facts.signedTokenId()!=null;
        }
        @GetMapping("/__s0/revoke-http")
        ResponseEntity<Void> http(HttpServletRequest request)throws InterruptedException {
            var facts=facts(request);
            if(!current(facts))return ResponseEntity.notFound().build();
            HTTP_READY.countDown();
            if(!HTTP_RELEASE.await(8,TimeUnit.SECONDS))
                return ResponseEntity.status(503).build();
            return current(facts)?ResponseEntity.noContent().build():
                ResponseEntity.notFound().build();
        }
        @GetMapping(value="/__s0/revoke-sse",
                    produces=MediaType.TEXT_EVENT_STREAM_VALUE)
        void sse(HttpServletRequest request,HttpServletResponse response)
                throws IOException,InterruptedException{
            var facts=facts(request);
            if(!current(facts)){response.setStatus(404);return;}
            response.setHeader("Content-Type",MediaType.TEXT_EVENT_STREAM_VALUE);
            response.setHeader("Cache-Control","no-store");
            response.setStatus(200);
            try(var out=response.getOutputStream()){
                out.write(FRAME.getBytes(StandardCharsets.UTF_8));
                out.flush();
                SSE_READY.countDown();
                if(!SSE_RELEASE.await(8,TimeUnit.SECONDS))return;
                // HTTP 200 is committed: close, never send another event.
                if(!current(facts)){DENIED_EVENT.incrementAndGet();return;}
                out.write("event: next\ndata: synthetic-only\n\n"
                    .getBytes(StandardCharsets.UTF_8));
                out.flush();
            }finally{SSE_CLOSED.countDown();}
        }
    }
    private HttpRequest get(String path,String token){
        var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path))
            .timeout(Duration.ofSeconds(18)).GET();
        if(token!=null)b.header("Authorization","Bearer "+token);
        return b.build();
    }
    @Test
    void actualFullSpringMvcHttpSseLifecycleAndQuarantine()throws Exception {
        assertTrue(port>0);
        assertNotNull(application.getBean(AetherisOrchestratorApplication.class));
        assertNotNull(application.getBean(TaskController.class));
        assertNotNull(application.getBean(OperationsStreamController.class));
        NOT_REVOKED.set(true);
        DENIED_EVENT.set(0);
        String token=jwt(KEY,true);
        var client=HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER).build();
        var realPaths=List.of(
            "/api/orchestrator/live/events",
            "/api/orchestrator/tasks/22222222-2222-4222-8222-222222222222/events",
            "/api/orchestrator/tasks/22222222-2222-4222-8222-222222222222/events/history",
            "/actuator/health");
        for(String route:realPaths){
            var forbidden=client.send(get(route,token),
                HttpResponse.BodyHandlers.ofString());
            assertEquals(404,forbidden.statusCode(),route);
            assertEquals("no-store",
                forbidden.headers().firstValue("Cache-Control").orElse(null));
        }
        assertEquals(401,client.send(get("/__s0/revoke-http",null),
            HttpResponse.BodyHandlers.ofString()).statusCode());
        assertEquals(401,client.send(get("/__s0/revoke-http",jwt(createKey(),true)),
            HttpResponse.BodyHandlers.ofString()).statusCode());
        assertEquals(401,client.send(get("/__s0/revoke-http",jwt(KEY,false)),
            HttpResponse.BodyHandlers.ofString()).statusCode());
        var pending=client.sendAsync(get("/__s0/revoke-http",token),
            HttpResponse.BodyHandlers.ofString());
        assertTrue(HTTP_READY.await(6,TimeUnit.SECONDS));
        var stream=client.sendAsync(get("/__s0/revoke-sse",token),
            HttpResponse.BodyHandlers.ofInputStream()).get(6,TimeUnit.SECONDS);
        assertEquals(200,stream.statusCode());
        assertTrue(SSE_READY.await(6,TimeUnit.SECONDS));
        try(var body=stream.body()){
            byte[] first=body.readNBytes(FRAME.getBytes(StandardCharsets.UTF_8).length);
            assertEquals(FRAME,new String(first,StandardCharsets.UTF_8));
            assertFalse(pending.isDone());
            NOT_REVOKED.set(false); // Test stub, NOT real B62 commit.
            HTTP_RELEASE.countDown();SSE_RELEASE.countDown();
            var after=pending.get(7,TimeUnit.SECONDS);
            assertEquals(404,after.statusCode());
            assertTrue(after.body().isEmpty());
            assertTrue(SSE_CLOSED.await(7,TimeUnit.SECONDS));
            byte[] remaining;
            try{remaining=body.readAllBytes();}
            catch(IOException closed){remaining=new byte[0];}
            assertEquals(0,remaining.length);
            assertEquals(1,DENIED_EVENT.get());
        }finally{HTTP_RELEASE.countDown();SSE_RELEASE.countDown();}
        assertEquals(404,client.send(get("/__s0/revoke-http",token),
            HttpResponse.BodyHandlers.ofString()).statusCode());
        assertEquals(404,client.send(get("/__s0/revoke-sse",token),
            HttpResponse.BodyHandlers.ofString()).statusCode());
        for(String route:realPaths)assertEquals(404,client.send(get(route,token),
            HttpResponse.BodyHandlers.ofString()).statusCode(),route);
        System.out.println("RUNTIME_FULL_SPRINGBOOT_HTTP_RECHECK_BEFORE_SUCCESS_PASS");
        System.out.println("RUNTIME_FULL_SPRINGBOOT_SSE_CLOSE_BEFORE_NEXT_EVENT_PASS");
        System.out.println("RUNTIME_REAL_TASK_HISTORY_SSE_QUARANTINE_NO_GO");
    }
}

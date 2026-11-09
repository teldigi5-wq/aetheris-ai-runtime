package io.aetheris.identity;

import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S0-E6L disposable SOURCE-IDENTITY integration fixture.
 *
 * Copied at CI runtime into the exact pinned Platform PR #415 checkout;
 * it compiles and calls Platform's ACTUAL RuntimeAudienceTokenIssuer,
 * RuntimeAudienceAtomicRegistryExchange, JPA refresh token service and
 * PostgreSQL16 S0-E6J function-only issuer registration. Its outputs are
 * ephemeral CI filesystem files (0600), never Actions artifacts, logs,
 * source commits, public tokens, persistent key storage or real users.
 *
 * This is NOT a published Identity token endpoint.
 */
@DataJpaTest(properties={
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.open-in-view=false",
    "spring.sql.init.mode=never",
    "spring.main.lazy-initialization=false",
    "spring.jpa.properties.hibernate.jdbc.time_zone=UTC"
})
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Import({
    RefreshTokenService.class,
    RuntimeAudienceSessionExchangeService.class,
    S0E6LPlatformSessionProducerTest.Fixture.class
})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class S0E6LPlatformSessionProducerTest {
    private static final String DB="aetheris_e6j_disposable";
    private static final String URL="jdbc:postgresql://127.0.0.1:5432/"+DB;
    private static final String ISSUER="https://identity.aetheris.example.invalid";
    private static final String KID="e6l-platform-real-issuer";
    private static final String AUDIENCE="aetheris-orchestrator-api";
    private static final KeyPair SIGNER=ephemeralKey();

    @TestConfiguration(proxyBeanMethods=false)
    static class Fixture {
        @Bean RuntimeAudienceTokenIssuer runtimeAudienceTokenIssuer(
                Environment environment) {
            return new RuntimeAudienceTokenIssuer(environment);
        }
        @Bean RuntimeAudienceAtomicRegistryExchange runtimeEnrollment(
                RuntimeAudienceSessionExchangeService exchange,
                RuntimeAudienceTokenIssuer issuer,JdbcTemplate jdbc) {
            return new RuntimeAudienceAtomicRegistryExchange(exchange,issuer,
                new RuntimePg16SessionEnrollment(jdbc));
        }
    }

    @DynamicPropertySource
    static void disposableOnly(DynamicPropertyRegistry properties) {
        if(!"true".equals(System.getenv("GITHUB_ACTIONS"))
                || !"ONLY_DISPOSABLE_E6L_PG16".equals(
                    System.getenv("AETHERIS_E6L_MUST_RUN"))
                || !"127.0.0.1".equals(System.getenv("PGHOST"))
                || !DB.equals(System.getenv("PGDATABASE"))
                || !"e6j_test_only".equals(System.getenv("PGUSER"))
                || System.getenv("PGPASSWORD")==null
                || System.getenv("AETHERIS_E6L_FIXTURE_DIR")==null) {
            throw new IllegalStateException("Only disposable CI may run E6L");
        }
        properties.add("spring.datasource.url",()->URL);
        properties.add("spring.datasource.username",()->"e6j_test_only");
        properties.add("spring.datasource.password",()->
            "e6j_disposable_ci_only");
        properties.add("aetheris.runtime-token.pkcs8-base64",()->
            Base64.getEncoder().encodeToString(SIGNER.getPrivate().getEncoded()));
        properties.add("aetheris.runtime-token.key-id",()->KID);
        properties.add("aetheris.runtime-token.issuer",()->ISSUER);
        properties.add("aetheris.runtime-token.audience",()->AUDIENCE);
    }

    private static KeyPair ephemeralKey() {
        try {
            var generator=KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        }catch(Exception e) {
            throw new IllegalStateException("Cannot generate ephemeral issuer");
        }
    }

    @Autowired IdentityAccountRepository accounts;
    @Autowired RefreshTokenService refresh;
    @Autowired RuntimeAudienceTokenIssuer issuer;
    @Autowired RuntimeAudienceAtomicRegistryExchange exchange;
    @Autowired JdbcTemplate jdbc;

    private static Path fixtureDir() throws Exception {
        var path=Path.of(System.getenv("AETHERIS_E6L_FIXTURE_DIR"));
        assertTrue(Files.isDirectory(path));
        assertFalse(Files.isSymbolicLink(path));
        assertEquals("rwx------",
            PosixFilePermissions.toString(Files.getPosixFilePermissions(path)));
        return path;
    }

    private static void secretFile(Path dir,String name,String value)
            throws Exception {
        if(name.contains("/") || name.contains("\\") || value.isBlank())
            throw new IllegalArgumentException("Invalid disposable fixture");
        Path file=dir.resolve(name);
        Files.writeString(file,value,StandardCharsets.UTF_8,
            StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
        Files.setPosixFilePermissions(file,
            PosixFilePermissions.fromString("rw-------"));
    }

    @Test
    void actualPlatformIssuerAtomicallyRegistersTwoIndependentUserSessions()
            throws Exception {
        assertEquals(DB,jdbc.queryForObject("SELECT current_database()",String.class));
        assertTrue(jdbc.queryForObject("SELECT version()",String.class)
            .startsWith("PostgreSQL 16."));
        assertTrue(org.springframework.aop.support.AopUtils.isAopProxy(exchange),
            "Real Spring transaction proxy is required");
        var a=accounts.saveAndFlush(new IdentityAccount(
            "Synthetic A","e6l-platform-a@example.invalid",
            "irrelevant-no-login-test-hash",Role.DEVELOPER));
        var b=accounts.saveAndFlush(new IdentityAccount(
            "Synthetic B","e6l-platform-b@example.invalid",
            "irrelevant-no-login-test-hash",Role.API_CONSUMER));
        assertNotEquals(a.getId(),b.getId());
        jdbc.update("""
            INSERT INTO e6j_runtime_auth.signer_key_states(issuer,kid,state)
            VALUES (?,?,'ACTIVE')""",ISSUER,KID);
        jdbc.update("""
            INSERT INTO e6j_runtime_auth.principal_epochs(
                principal_id,reject_issued_at_or_before_epoch)
            VALUES (?,0),(?,0)""",a.getId(),b.getId());

        // A genuine Platform-signed but UNREGISTERED JWT MUST be denied
        // by the future independent Runtime reader.
        String notEnrolled=issuer.issueForVerifiedExchange(a);
        var first=exchange.exchangeAndEnrollInternal(refresh.issue(a).token());
        var second=exchange.exchangeAndEnrollInternal(refresh.issue(b).token());
        assertNotEquals(first.runtimeAccessToken(),second.runtimeAccessToken());

        var fa=issuer.verifyOwnSignedTokenForEnrollment(first.runtimeAccessToken());
        var fb=issuer.verifyOwnSignedTokenForEnrollment(second.runtimeAccessToken());
        assertEquals(a.getId().longValue(),fa.uid());
        assertEquals(b.getId().longValue(),fb.uid());
        assertNotEquals(fa.jti(),fb.jti());
        assertEquals(2,jdbc.queryForObject(
            "SELECT count(*) FROM e6j_runtime_auth.session_registrations",
            Integer.class));
        assertEquals(2,jdbc.queryForObject(
            "SELECT count(*) FROM e6j_runtime_auth.authority_audit",Integer.class));

        // Actual legacy Platform JwtService HMAC is a strictly negative
        // interop fixture: Runtime RS256 verifier must reject it.
        String hs=new JwtService(
            "synthetic-legacy-only-hmac-secret-longer-than-32-characters",300)
            .issue(a);
        // JJWT chooses HS256/384/512 based on synthetic secret length.
        // Runtime MUST reject every HS-family algorithm; requiring exactly
        // HS256 would incorrectly fail when Platform emits stronger HS384.
        String legacyAlg=Jwts.parser()
            .verifyWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                "synthetic-legacy-only-hmac-secret-longer-than-32-characters"
                    .getBytes(StandardCharsets.UTF_8)))
            .build().parseSignedClaims(hs).getHeader().getAlgorithm();
        assertTrue(java.util.Set.of("HS256","HS384","HS512").contains(legacyAlg));
        assertNotEquals("RS256",legacyAlg);

        String pem="-----BEGIN PUBLIC KEY-----\n"+
            Base64.getMimeEncoder(64,new byte[]{'\n'})
                .encodeToString(SIGNER.getPublic().getEncoded())+
            "\n-----END PUBLIC KEY-----\n";
        var dir=fixtureDir();
        secretFile(dir,"e6l-platform-public.pem",pem);
        secretFile(dir,"user-a.jwt",first.runtimeAccessToken());
        secretFile(dir,"user-b.jwt",second.runtimeAccessToken());
        secretFile(dir,"unregistered.jwt",notEnrolled);
        secretFile(dir,"legacy-hs.jwt",hs);
        // Only safe-to-disclose local test metadata, never printed.
        secretFile(dir,"expected-uid-a",a.getId().toString());
        secretFile(dir,"expected-uid-b",b.getId().toString());
        assertFalse(first.toString().contains(first.runtimeAccessToken()));
        assertFalse(second.toString().contains(second.rotatedRefreshToken().token()));
    }
}

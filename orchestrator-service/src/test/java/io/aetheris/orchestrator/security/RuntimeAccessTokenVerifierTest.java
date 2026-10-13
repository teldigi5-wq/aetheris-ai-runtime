package io.aetheris.orchestrator.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuntimeAccessTokenVerifierTest {
    private static final String SECRET_A = "0123456789abcdef0123456789abcdef";
    private static final String SECRET_B = "fedcba9876543210fedcba9876543210";
    private final RuntimeAccessTokenVerifier verifier = new RuntimeAccessTokenVerifier(SECRET_A);

    private static String issue(String key, Object uid, String role, Object scopes,
                                Instant issued, Instant expires) {
        SecretKey secretKey=Keys.hmacShaKeyFor(key.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .issuer(RuntimeAccessTokenVerifier.DEFAULT_ISSUER)
                .audience().add(RuntimeAccessTokenVerifier.DEFAULT_AUDIENCE).and()
                .subject("owner@example.test")
                .claim("uid", uid)
                .claim("role", role)
                .claim("scopes", scopes)
                .issuedAt(Date.from(issued))
                .expiration(Date.from(expires))
                .signWith(secretKey)
                .compact();
    }

    private static Instant now() { return Instant.now(); }
    private static String currentToken(long uid, List<String> scopes) {
        Instant now=now();
        return issue(SECRET_A,uid,"API_CONSUMER",scopes,now.minusSeconds(20),now.plusSeconds(300));
    }
    private static void rejected(RuntimeAccessTokenVerifier v, String jwt, String scope) {
        assertThatThrownBy(()->v.verify(jwt,scope))
                .isInstanceOf(RuntimeAccessTokenVerifier.RejectedToken.class)
                .hasMessage("ACCESS_TOKEN_REJECTED")
                .hasNoCause();
    }

    @Test
    void validatesActualIdentityIssuerNumericUidAndGatewayReadScope() {
        RuntimeAccessTokenVerifier.VerifiedPrincipal p=verifier.verify(
                currentToken(42L,List.of("users:read","orchestrator:read")),
                RuntimeAccessTokenVerifier.READ_SCOPE);
        assertThat(p.identityAccountId()).isEqualTo(42L);
        assertThat(p.hasScope("orchestrator:read")).isTrue();
        assertThat(p.hasScope("orchestrator:write")).isFalse();
        assertThat(p.toString()).doesNotContain("owner@example.test","Bearer","scopes");
    }

    @Test
    void twoSignedAccountsStayDistinctWithoutHeaderTrust() {
        var a=verifier.verify(currentToken(101L,List.of("orchestrator:read")),"orchestrator:read");
        var b=verifier.verify(currentToken(202L,List.of("orchestrator:read")),"orchestrator:read");
        assertThat(a.identityAccountId()).isNotEqualTo(b.identityAccountId());
        assertThat(OwnerBoundResourcePolicy.permitted(a.identityAccountId(),null,b.identityAccountId(),null))
                .isFalse();
        assertThat(OwnerBoundResourcePolicy.permitted(a.identityAccountId(),null,a.identityAccountId(),null))
                .isTrue();
    }

    @Test
    void rejectsOtherSignatureTamperingAndUnsignedClaims() {
        rejected(verifier,currentToken(42L,List.of("orchestrator:read")).replaceFirst(".$","Z"),
                "orchestrator:read");
        rejected(verifier,issue(SECRET_B,42L,"API_CONSUMER",List.of("orchestrator:read"),
                        now().minusSeconds(10),now().plusSeconds(300)),"orchestrator:read");
        rejected(verifier,"eyJhbGciOiJub25lIn0.eyJ1aWQiOjQyfQ.","orchestrator:read");
        rejected(verifier,"this is not a JWT","orchestrator:read");
    }

    @Test
    void expiredFutureIssuedMissingClaimsAndUnexpectedIdentityTypesFailClosed() {
        Instant t=now();
        rejected(verifier,issue(SECRET_A,42L,"API_CONSUMER",List.of("orchestrator:read"),
                t.minusSeconds(600),t.minusSeconds(60)),"orchestrator:read");
        rejected(verifier,issue(SECRET_A,42L,"API_CONSUMER",List.of("orchestrator:read"),
                t.plusSeconds(300),t.plusSeconds(600)),"orchestrator:read");
        rejected(verifier,issue(SECRET_A,"11111111-1111-4111-8111-111111111111",
                "API_CONSUMER",List.of("orchestrator:read"),
                t.minusSeconds(5),t.plusSeconds(300)),"orchestrator:read");
        rejected(verifier,issue(SECRET_A,0L,"API_CONSUMER",List.of("orchestrator:read"),
                t.minusSeconds(5),t.plusSeconds(300)),"orchestrator:read");
        rejected(verifier,issue(SECRET_A,42L,"SUPER_ADMIN",List.of("orchestrator:read"),
                t.minusSeconds(5),t.plusSeconds(300)),"orchestrator:read");
        rejected(verifier,issue(SECRET_A,42L,"API_CONSUMER",List.of("orchestrator:read"),
                t.minusSeconds(5),t.plusSeconds(26*60*60)),"orchestrator:read");
    }

    @Test
    void forbidsReadWithoutReadScopeAndForbidsWriteWithoutWriteScope() {
        rejected(verifier,currentToken(42L,List.of("users:read")),"orchestrator:read");
        rejected(verifier,currentToken(42L,List.of("orchestrator:read")),"orchestrator:write");
        var principal=verifier.verify(currentToken(42L,List.of("orchestrator:read","orchestrator:write")),
                "orchestrator:write");
        assertThat(principal.hasScope("orchestrator:write")).isTrue();
        rejected(verifier,currentToken(42L,List.of("orchestrator:read")),"inventory:admin");
    }

    @Test
    void malformedScopeShapesAndOversizedTokensNeverBecomePrincipals() {
        Instant t=now();
        rejected(verifier,issue(SECRET_A,42L,"API_CONSUMER",Map.of("orchestrator:read",true),
                t.minusSeconds(10),t.plusSeconds(300)),"orchestrator:read");
        rejected(verifier,issue(SECRET_A,42L,"API_CONSUMER",List.of("orchestrator:read",17),
                t.minusSeconds(10),t.plusSeconds(300)),"orchestrator:read");
        rejected(verifier,"x".repeat(8193),"orchestrator:read");
        rejected(verifier,null,"orchestrator:read");
        rejected(verifier,"","orchestrator:read");
    }

    @Test
    void wrongOrMissingIssuerAudienceIsAlwaysRejectedDespiteValidSignatureAndScope() {
        Instant t = now();
        for (String token : List.of(
            specialToken("other-issuer", RuntimeAccessTokenVerifier.DEFAULT_AUDIENCE),
            specialToken(RuntimeAccessTokenVerifier.DEFAULT_ISSUER, "other-audience"),
            specialToken(null, RuntimeAccessTokenVerifier.DEFAULT_AUDIENCE),
            specialToken(RuntimeAccessTokenVerifier.DEFAULT_ISSUER, null))) {
            rejected(verifier, token, "orchestrator:read");
        }
        // A correctly signed token is still the wrong recipient if this
        // native service has been configured for another audience.
        RuntimeAccessTokenVerifier differentService = new RuntimeAccessTokenVerifier(
                SECRET_A, RuntimeAccessTokenVerifier.DEFAULT_ISSUER, "other-service");
        rejected(differentService, currentToken(42L, List.of("orchestrator:read")),
                "orchestrator:read");
    }

    private static String specialToken(String issuer, String audience) {
        var token = Jwts.builder()
                .subject("owner@example.test")
                .claim("uid", 42L)
                .claim("role", "API_CONSUMER")
                .claim("scopes", List.of("orchestrator:read"))
                .issuedAt(Date.from(now().minusSeconds(10)))
                .expiration(Date.from(now().plusSeconds(120)));
        if (issuer != null) token.issuer(issuer);
        if (audience != null) token.audience().add(audience).and();
        return token.signWith(Keys.hmacShaKeyFor(SECRET_A.getBytes(StandardCharsets.UTF_8))).compact();
    }

    @Test
    void refusesWeakDefaultOrMissingSigningConfiguration() {
        for(String weak:new String[]{null,"", "too-short",
                "change-me-local-development-secret-32chars"}) {
            assertThatThrownBy(()->new RuntimeAccessTokenVerifier(weak))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Verified signing key configuration required");
        }
    }

    @Test
    void verifierCannotOverrideStillClosedTaskAndMissionHttpQuarantine() {
        OwnerSensitiveApiQuarantineFilter filter=new OwnerSensitiveApiQuarantineFilter();
        assertThat(OwnerSensitiveApiQuarantineFilter.isOwnerSensitivePath(
                "/api/orchestrator/tasks","/api/orchestrator/tasks")).isTrue();
        assertThat(OwnerSensitiveApiQuarantineFilter.isOwnerSensitivePath(
                "/api/orchestrator/live/events","/api/orchestrator/live/events")).isTrue();
    }
}

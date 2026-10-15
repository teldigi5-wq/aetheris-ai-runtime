package io.aetheris.orchestrator.task;

import io.aetheris.orchestrator.policy.OperationMode;
import io.aetheris.orchestrator.security.RuntimeAccessTokenVerifier;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Database-backed, two-account read boundary with synthetic signed principals.
 * Deliberately does not expose an HTTP endpoint, authenticate a desktop session
 * or subscribe to the ownerless global event stream.
 */
@SpringBootTest
@Transactional
class VerifiedOwnerTaskReadTest {
    private static final String TEST_SIGNER = "0123456789abcdef0123456789abcdef";
    @Autowired TaskRepository tasks;

    private static String token(long uid, String audience, List<String> scopes) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(RuntimeAccessTokenVerifier.DEFAULT_ISSUER)
                .audience().add(audience).and()
                .subject("owner@example.test")
                .claim("uid", uid)
                .claim("role", "API_CONSUMER")
                .claim("scopes", scopes)
                .issuedAt(Date.from(now.minusSeconds(20)))
                .expiration(Date.from(now.plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(TEST_SIGNER.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private VerifiedOwnerTaskRead reader() {
        return new VerifiedOwnerTaskRead(tasks, new RuntimeAccessTokenVerifier(TEST_SIGNER));
    }

    @Test
    void globalTaskForAccountAIsInvisibleToBAndListFiltersProjectAndLegacyRecords() {
        TaskEntity a = tasks.save(new TaskEntity(UUID.randomUUID(), "Task A",
                "SECRET_COMMAND_A_DO_NOT_RENDER", OperationMode.PRIVATE, 101L, null));
        TaskEntity b = tasks.save(new TaskEntity(UUID.randomUUID(), "Task B",
                "SECRET_COMMAND_B_DO_NOT_RENDER", OperationMode.PRIVATE, 202L, null));
        TaskEntity project = tasks.save(new TaskEntity(UUID.randomUUID(), "Private project",
                "SECRET_PROJECT_COMMAND", OperationMode.PRIVATE, 101L, UUID.randomUUID()));
        TaskEntity legacy = tasks.save(new TaskEntity(UUID.randomUUID(), "Ownerless",
                "SECRET_LEGACY_COMMAND", OperationMode.PRIVATE));
        tasks.flush();

        var reader = reader();
        String ownerA = token(101L, RuntimeAccessTokenVerifier.DEFAULT_AUDIENCE,
                List.of("orchestrator:read"));
        String ownerB = token(202L, RuntimeAccessTokenVerifier.DEFAULT_AUDIENCE,
                List.of("orchestrator:read"));

        assertThat(reader.get(ownerA, a.getId()).taskId()).isEqualTo(a.getId());
        assertThat(reader.get(ownerB, b.getId()).taskId()).isEqualTo(b.getId());
        assertThat(reader.recent(ownerA)).extracting(VerifiedOwnerTaskRead.TaskStatusView::taskId)
                .contains(a.getId())
                .doesNotContain(b.getId(), project.getId(), legacy.getId());
        assertThat(reader.recent(ownerB)).extracting(VerifiedOwnerTaskRead.TaskStatusView::taskId)
                .contains(b.getId())
                .doesNotContain(a.getId(), project.getId(), legacy.getId());
        for (UUID denied : List.of(b.getId(), project.getId(), legacy.getId(), UUID.randomUUID())) {
            assertThatThrownBy(() -> reader.get(ownerA, denied))
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessage("TASK_NOT_AVAILABLE");
        }
        assertThatThrownBy(() -> reader.get(ownerB, a.getId()))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("TASK_NOT_AVAILABLE");

        var safe = reader.get(ownerA, a.getId());
        // Verify the exact safe DTO shape rather than searching for the
        // short account ID "101": random UUIDs may legitimately contain 101.
        // A future owner or command field must fail this schema assertion.
        assertThat(Arrays.stream(VerifiedOwnerTaskRead.TaskStatusView.class.getRecordComponents())
                .map(RecordComponent::getName).toList())
                .containsExactly("taskId", "state", "agentId", "updatedAt");
        assertThat(safe.toString()).doesNotContain(
                "SECRET_COMMAND", "owner@example", "ownerUserId=", "commandText=");
        assertThat(safe.agentId()).isNull();
    }

    @Test
    void anInvalidSignatureOrWrongAudienceCannotReadEvenAValidOwnerTask() {
        TaskEntity a = tasks.save(new TaskEntity(UUID.randomUUID(), "Sensitive",
                "DO_NOT_EXPOSE", OperationMode.PRIVATE, 101L, null));
        String wrongAudience = token(101L, "unrelated-application", List.of("orchestrator:read"));
        String missingScope = token(101L, RuntimeAccessTokenVerifier.DEFAULT_AUDIENCE,
                List.of("users:read"));
        for (String jwt : List.of(wrongAudience, missingScope, "not-a-valid-token")) {
            assertThatThrownBy(() -> reader().get(jwt, a.getId()))
                    .isInstanceOf(RuntimeAccessTokenVerifier.RejectedToken.class)
                    .hasMessage("ACCESS_TOKEN_REJECTED");
        }
    }

    @Test
    void cannotUseWriteOnlyScopeAsAReadPermission() {
        TaskEntity a = tasks.save(new TaskEntity(UUID.randomUUID(), "Scoped",
                "DO_NOT_EXPOSE", OperationMode.PRIVATE, 101L, null));
        String jwt = token(101L, RuntimeAccessTokenVerifier.DEFAULT_AUDIENCE,
                List.of("orchestrator:write"));
        assertThatThrownBy(() -> reader().get(jwt, a.getId()))
                .isInstanceOf(RuntimeAccessTokenVerifier.RejectedToken.class);
    }
}

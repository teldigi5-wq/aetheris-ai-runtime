package io.aetheris.orchestrator.api;

import io.aetheris.orchestrator.security.RuntimeAccessTokenVerifier;
import io.aetheris.orchestrator.task.OwnerTaskCheckpointRepository;
import io.aetheris.orchestrator.task.TaskEventJournalRepository;
import io.aetheris.orchestrator.task.TaskRepository;
import io.aetheris.orchestrator.task.VerifiedOwnerDeliveryCheckpoint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Explicitly opt-in, read/replay + client-declared ACK HTTP adapter.
 *
 * It is absent from the Spring application unless the exact feature flag is
 * enabled. Legacy task/mission/global-feed routes remain quarantined without
 * exception. Even with the flag enabled, this controller independently checks
 * a signed token on EVERY request: forwarded identity headers are ignored.
 *
 * This is NOT an SSE endpoint or a production session/desktop integration.
 * The runtime fails startup if enabled without a strong configured JWT key.
 */
@RestController
@RequestMapping("/api/orchestrator/owner-checkpoints")
@ConditionalOnProperty(name = "aetheris.owner-checkpoint-http.enabled",
        havingValue = "true", matchIfMissing = false)
public final class OwnerCheckpointHttpController {
    private final VerifiedOwnerDeliveryCheckpoint checkpoint;

    public OwnerCheckpointHttpController(
            TaskRepository tasks, TaskEventJournalRepository events,
            OwnerTaskCheckpointRepository checkpoints,
            PlatformTransactionManager transactions,
            @Value("${aetheris.jwt.secret:}") String secret,
            @Value("${aetheris.jwt.issuer:aetheris-identity}") String issuer,
            @Value("${aetheris.jwt.audience:aetheris-api}") String audience) {
        RuntimeAccessTokenVerifier verifier =
                new RuntimeAccessTokenVerifier(secret, issuer, audience);
        this.checkpoint = new VerifiedOwnerDeliveryCheckpoint(
                tasks, events, checkpoints, verifier, transactions);
    }

    @PostMapping(value = "/tasks/{taskId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> open(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable UUID taskId) {
        return safe(ResponseEntity.ok(checkpoint.open(token(authorization), taskId)));
    }

    @GetMapping(value = "/{checkpointId}/events", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> resume(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable UUID checkpointId,
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        return safe(ResponseEntity.ok(checkpoint.resume(
                token(authorization), checkpointId, limit)));
    }

    public record AckInput(UUID eventId) {}

    @PostMapping(value = "/{checkpointId}/ack",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> acknowledge(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable UUID checkpointId, @RequestBody AckInput input) {
        var result = checkpoint.acknowledge(token(authorization), checkpointId,
                input == null ? null : input.eventId());
        if (result == VerifiedOwnerDeliveryCheckpoint.Acknowledgement.OUT_OF_ORDER) {
            return safe(ResponseEntity.status(409)
                    .body(Map.of("status", "ACK_OUT_OF_ORDER")));
        }
        return safe(ResponseEntity.ok(Map.of("status",
                result == VerifiedOwnerDeliveryCheckpoint.Acknowledgement.ADVANCED
                        ? "ACK_ADVANCED" : "ACK_DUPLICATE")));
    }

    /**
     * No forwarded headers/cookies/subject strings are identity. JWT is
     * carried only in Authorization; reject whitespace injection and
     * malformed bearer values before passing bytes to signature verifier.
     */
    private static String token(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")
                || authorization.length() > 8199) {
            throw new RuntimeAccessTokenVerifier.RejectedToken();
        }
        String raw = authorization.substring(7);
        if (raw.isEmpty() || !raw.matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+")) {
            throw new RuntimeAccessTokenVerifier.RejectedToken();
        }
        return raw;
    }

    @ExceptionHandler(RuntimeAccessTokenVerifier.RejectedToken.class)
    public ResponseEntity<Map<String, String>> unauthorized() {
        return safe(ResponseEntity.status(401).body(Map.of("status", "UNAUTHORIZED")));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> notFound() {
        return safe(ResponseEntity.status(404).body(Map.of("status", "TASK_NOT_AVAILABLE")));
    }

    private static <T> ResponseEntity<T> safe(ResponseEntity.BodyBuilder builder, T body) {
        return builder.cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache")
                .header("X-Content-Type-Options", "nosniff")
                .body(body);
    }

    private static <T> ResponseEntity<T> safe(ResponseEntity<T> response) {
        return safe(ResponseEntity.status(response.getStatusCode()), response.getBody());
    }
}

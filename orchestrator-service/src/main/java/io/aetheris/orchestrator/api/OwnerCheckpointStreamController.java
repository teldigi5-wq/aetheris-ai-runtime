package io.aetheris.orchestrator.api;

import io.aetheris.orchestrator.security.RuntimeAccessTokenVerifier;
import io.aetheris.orchestrator.task.OwnerTaskCheckpointRepository;
import io.aetheris.orchestrator.task.TaskEventJournalRepository;
import io.aetheris.orchestrator.task.TaskRepository;
import io.aetheris.orchestrator.task.VerifiedOwnerDeliveryCheckpoint;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Review-only, short-lived owner-scoped SSE adapter. Registered only when
 * BOTH explicit feature flags are true. Never uses the unsafe global stream.
 *
 * Authentication: a signed Authorization bearer verified by the runtime,
 * not a forwarded header, URL token, cookie, IP address or Last-Event-ID.
 * The signed token is NOT retained in the session. A scoped immutable
 * in-process principal is rechecked for expiry and current DB task ownership
 * on every poll. No client-provided event payload is used.
 *
 * Default disabled; NOT a complete revocable production SSE/session system.
 */
@RestController
@RequestMapping("/api/orchestrator/owner-checkpoints")
@ConditionalOnProperty(name = {
        "aetheris.owner-checkpoint-http.enabled",
        "aetheris.owner-stream-http.enabled"
}, havingValue = "true", matchIfMissing = false)
public final class OwnerCheckpointStreamController {
    private static final int MAX_CONNECTIONS = 16;
    private static final int MAX_PAGE = 20;

    private final RuntimeAccessTokenVerifier verifier;
    private final VerifiedOwnerDeliveryCheckpoint checkpoints;
    private final ScheduledExecutorService scheduler;
    private final Set<Session> active = ConcurrentHashMap.newKeySet();
    private final int maximumSeconds;
    private final Object admissionLock = new Object();

    public OwnerCheckpointStreamController(
            TaskRepository tasks, TaskEventJournalRepository events,
            OwnerTaskCheckpointRepository checkpointRepository,
            PlatformTransactionManager transactions,
            @Value("${aetheris.jwt.secret:}") String secret,
            @Value("${aetheris.jwt.issuer:aetheris-identity}") String issuer,
            @Value("${aetheris.jwt.audience:aetheris-api}") String audience,
            @Value("${aetheris.owner-stream-http.max-duration-seconds:20}") int maximumSeconds) {
        if (maximumSeconds < 2 || maximumSeconds > 30) {
            throw new IllegalArgumentException("Owner stream maximum duration out of range");
        }
        this.verifier = new RuntimeAccessTokenVerifier(secret, issuer, audience);
        this.checkpoints = new VerifiedOwnerDeliveryCheckpoint(
                tasks, events, checkpointRepository, verifier, transactions);
        this.maximumSeconds = maximumSeconds;
        this.scheduler = Executors.newScheduledThreadPool(2, runnable -> {
            Thread worker = new Thread(runnable, "owner-journal-sse");
            worker.setDaemon(true);
            return worker;
        });
    }

    @GetMapping(value = "/{checkpointId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> stream(
            @PathVariable UUID checkpointId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String raw = rawBearer(authorization);
        var principal = verifier.verify(raw, RuntimeAccessTokenVerifier.READ_SCOPE);
        // Before HTTP 200, require the matching persisted checkpoint and
        // original task ownership; a UUID by itself grants no access.
        checkpoints.streamPage(principal, checkpointId, 0L, 1);

        Instant now = Instant.now();
        Instant expires = now.plusSeconds(maximumSeconds);
        if (principal.expiresAt().isBefore(expires)) expires = principal.expiresAt();
        if (!expires.isAfter(now)) throw new RuntimeAccessTokenVerifier.RejectedToken();
        final Instant deadline = expires;
        long millis = Math.max(1000L, java.time.Duration.between(now, deadline).toMillis());

        final Session session;
        synchronized (admissionLock) {
            if (active.size() >= MAX_CONNECTIONS) throw new CapacityExhausted();
            session = new Session(principal, checkpointId, deadline, new SseEmitter(millis));
            active.add(session);
        }
        session.emitter.onCompletion(session::close);
        session.emitter.onTimeout(session::close);
        session.emitter.onError(error -> session.close());
        try {
            ScheduledFuture<?> future = scheduler.scheduleWithFixedDelay(
                    session::poll, 0, 1, TimeUnit.SECONDS);
            session.install(future);
        } catch (RuntimeException startupFailure) {
            session.close();
            throw new CapacityExhausted();
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache")
                .header("X-Content-Type-Options", "nosniff")
                .header("X-Accel-Buffering", "no")
                .body(session.emitter);
    }

    private static String rawBearer(String header) {
        if (header == null || !header.startsWith("Bearer ") || header.length() > 8199) {
            throw new RuntimeAccessTokenVerifier.RejectedToken();
        }
        String value = header.substring(7);
        if (!value.matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+")) {
            throw new RuntimeAccessTokenVerifier.RejectedToken();
        }
        return value;
    }

    private final class Session {
        private final RuntimeAccessTokenVerifier.VerifiedPrincipal principal;
        private final UUID checkpointId;
        private final Instant deadline;
        private final SseEmitter emitter;
        private final AtomicBoolean closed = new AtomicBoolean();
        private volatile ScheduledFuture<?> scheduled;
        private long lastSent = 0L;

        private Session(RuntimeAccessTokenVerifier.VerifiedPrincipal principal,
                        UUID checkpointId, Instant deadline, SseEmitter emitter) {
            this.principal = principal;
            this.checkpointId = checkpointId;
            this.deadline = deadline;
            this.emitter = emitter;
        }

        private void install(ScheduledFuture<?> future) {
            scheduled = future;
            if (closed.get()) future.cancel(false);
        }

        /** One bounded, owner-revalidated journal poll. No request headers or
         * original signed token are retained or needed after connection. */
        private synchronized void poll() {
            if (closed.get()) return;
            if (!Instant.now().isBefore(deadline) ||
                    !Instant.now().isBefore(principal.expiresAt())) {
                close();
                return;
            }
            try {
                var page = checkpoints.streamPage(principal, checkpointId,
                        lastSent, MAX_PAGE);
                for (var event : page.events()) {
                    if (closed.get()) break;
                    emitter.send(SseEmitter.event().name("task-recorded")
                            .id(event.eventId().toString())
                            .data(event, MediaType.APPLICATION_JSON));
                    // Connection-local only; durable ACK is NEVER advanced
                    // merely because the server sent an SSE frame.
                    lastSent = event.sequence();
                }
            } catch (RuntimeException | IOException invalidOrDisconnected) {
                // Includes lost ownership, expiry, disconnect, DB outage,
                // blocked output. No private exception messages are logged.
                close();
            }
        }

        private void close() {
            if (!closed.compareAndSet(false, true)) return;
            ScheduledFuture<?> future = scheduled;
            if (future != null) future.cancel(false);
            active.remove(this);
            emitter.complete();
        }
    }

    @PreDestroy
    public void shutdown() {
        for (Session session : List.copyOf(active)) session.close();
        scheduler.shutdownNow();
    }

    private static final class CapacityExhausted extends RuntimeException {
        private CapacityExhausted() { super(null, null, false, false); }
    }

    @ExceptionHandler(RuntimeAccessTokenVerifier.RejectedToken.class)
    public ResponseEntity<Map<String, String>> unauthorized() {
        return ResponseEntity.status(401).cacheControl(CacheControl.noStore())
                .body(Map.of("status", "UNAUTHORIZED"));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> forbiddenOwner() {
        return ResponseEntity.status(404).cacheControl(CacheControl.noStore())
                .body(Map.of("status", "TASK_NOT_AVAILABLE"));
    }

    @ExceptionHandler(CapacityExhausted.class)
    public ResponseEntity<Map<String, String>> atCapacity() {
        return ResponseEntity.status(429).cacheControl(CacheControl.noStore())
                .body(Map.of("status", "STREAM_CAPACITY_EXCEEDED"));
    }
}

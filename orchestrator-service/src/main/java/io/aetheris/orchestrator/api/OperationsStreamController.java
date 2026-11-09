package io.aetheris.orchestrator.api;

import io.aetheris.orchestrator.task.TaskEventStreamService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Temporary fail-closed containment: global task events cannot be exposed
 * until caller identity, project scope and event ownership are verified.
 *
 * This is not tenant authorization. Restoring this endpoint requires
 * independently reviewed two-tenant and revocation tests.
 */
@RestController
@RequestMapping("/api/orchestrator/live")
public class OperationsStreamController {
    // Preserve constructor wiring while the stream endpoint is quarantined.
    public OperationsStreamController(TaskEventStreamService streams) {
        java.util.Objects.requireNonNull(streams, "streams");
    }

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<Void> events() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .build();
    }
}

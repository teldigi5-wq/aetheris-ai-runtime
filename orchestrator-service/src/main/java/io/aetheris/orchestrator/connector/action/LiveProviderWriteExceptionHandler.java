package io.aetheris.orchestrator.connector.action;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Converts provider HTTP failures into a safe gateway error without exposing
 * provider bodies, tokens, account identifiers, targets, or created-object IDs.
 */
@RestControllerAdvice
public class LiveProviderWriteExceptionHandler {

    @ExceptionHandler(LiveProviderWriteException.class)
    public ResponseEntity<Map<String, Object>> handleLiveProviderWriteFailure(
            LiveProviderWriteException error
    ) {

        Map<String, Object> body =
                new LinkedHashMap<>();

        body.put(
                "code",
                "LIVE_PROVIDER_WRITE_FAILED"
        );

        body.put(
                "message",
                error.getMessage()
        );

        body.put(
                "providerStatus",
                error.getProviderStatus()
        );

        if (error.getAcceptedPermissions() != null
                && !error.getAcceptedPermissions().isBlank()) {
            body.put(
                    "acceptedPermissions",
                    error.getAcceptedPermissions()
            );
        }

        return ResponseEntity
                .status(HttpStatus.BAD_GATEWAY)
                .body(body);
    }
}

package io.aetheris.orchestrator.connector.action;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class ConnectorActionBlockedException extends RuntimeException {
    public ConnectorActionBlockedException(String message) {
        super(message);
    }
}

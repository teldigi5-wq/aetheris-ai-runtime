package io.aetheris.workstation;

import java.time.Instant;
import java.util.*;

public record HostCommandEnvelope(UUID commandId, UUID hostId, String capability, String action,
                                  Map<String, Object> arguments, Instant issuedAt, Instant expiresAt,
                                  String signature, String mode) {
    public HostCommandEnvelope {
        arguments = arguments == null ? Map.of() : Collections.unmodifiableMap(new TreeMap<>(arguments));
    }
}

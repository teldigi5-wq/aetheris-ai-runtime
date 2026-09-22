package io.aetheris.orchestrator.host;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
public record HostCommandEnvelope(UUID commandId,UUID hostId,String capability,String action,Map<String,Object> arguments,Instant issuedAt,Instant expiresAt,String signature,String mode) {}

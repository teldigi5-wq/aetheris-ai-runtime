package io.aetheris.orchestrator.host;
import java.time.Instant;
import java.util.UUID;
public record HostPairingChallengeResponse(UUID challengeId, UUID hostId, String nonce, Instant expiresAt, String signedMessageFormat) {}

package io.aetheris.orchestrator.host;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="aetheris_host_pairing_challenges")
public class HostPairingChallengeEntity {
    @Id private UUID id;
    @Column(nullable=false) private UUID hostId;
    @Column(nullable=false, length=128) private String nonceHash;
    @Column(nullable=false) private Instant expiresAt;
    private Instant consumedAt;
    @Column(nullable=false) private Instant createdAt;
    protected HostPairingChallengeEntity(){}
    public HostPairingChallengeEntity(UUID id,UUID hostId,String nonceHash,Instant expiresAt){this.id=id;this.hostId=hostId;this.nonceHash=nonceHash;this.expiresAt=expiresAt;this.createdAt=Instant.now();}
    public UUID getId(){return id;} public UUID getHostId(){return hostId;} public String getNonceHash(){return nonceHash;} public Instant getExpiresAt(){return expiresAt;} public Instant getConsumedAt(){return consumedAt;}
    public void consume(){if(consumedAt!=null)throw new IllegalStateException("Pairing challenge already consumed");if(Instant.now().isAfter(expiresAt))throw new IllegalStateException("Pairing challenge expired");consumedAt=Instant.now();}
}

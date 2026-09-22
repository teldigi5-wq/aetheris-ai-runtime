package io.aetheris.orchestrator.host;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="aetheris_host_pairing_credentials")
public class HostPairingCredentialEntity {
    @Id private UUID id;
    @Column(nullable=false, unique=true) private UUID hostId;
    @Column(nullable=false, length=8000) private String publicKeyPem;
    @Column(nullable=false, length=128) private String fingerprintSha256;
    @Column(nullable=false) private Instant pairedAt;
    private Instant revokedAt;
    protected HostPairingCredentialEntity(){}
    public HostPairingCredentialEntity(UUID id,UUID hostId,String publicKeyPem,String fingerprintSha256){this.id=id;this.hostId=hostId;this.publicKeyPem=publicKeyPem;this.fingerprintSha256=fingerprintSha256;this.pairedAt=Instant.now();}
    public UUID getId(){return id;} public UUID getHostId(){return hostId;} public String getPublicKeyPem(){return publicKeyPem;} public String getFingerprintSha256(){return fingerprintSha256;} public Instant getPairedAt(){return pairedAt;} public Instant getRevokedAt(){return revokedAt;}
    public boolean active(){return revokedAt==null;}
    public void revoke(){if(revokedAt==null)revokedAt=Instant.now();}
}

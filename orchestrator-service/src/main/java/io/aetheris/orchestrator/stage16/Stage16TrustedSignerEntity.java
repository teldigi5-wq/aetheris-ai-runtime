package io.aetheris.orchestrator.stage16;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "stage16_trusted_signers")
public class Stage16TrustedSignerEntity {
    @Id @Column(length = 80)
    private String keyId;
    @Column(nullable = false, length = 160)
    private String displayName;
    @Column(nullable = false, length = 512)
    private String publicKeyBase64;
    @Column(nullable = false, length = 64)
    private String publicKeySha256;
    @Column(nullable = false)
    private boolean enabled;
    @Column(nullable = false)
    private Instant createdAt;
    @Version
    private long version;

    protected Stage16TrustedSignerEntity() {}

    public Stage16TrustedSignerEntity(String keyId, String displayName, String publicKeyBase64, String publicKeySha256) {
        this.keyId = keyId;
        this.displayName = displayName;
        this.publicKeyBase64 = publicKeyBase64;
        this.publicKeySha256 = publicKeySha256;
        this.enabled = true;
        this.createdAt = Instant.now();
    }

    public String getKeyId() { return keyId; }
    public String getDisplayName() { return displayName; }
    public String getPublicKeyBase64() { return publicKeyBase64; }
    public String getPublicKeySha256() { return publicKeySha256; }
    public boolean isEnabled() { return enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public long getVersion() { return version; }
}

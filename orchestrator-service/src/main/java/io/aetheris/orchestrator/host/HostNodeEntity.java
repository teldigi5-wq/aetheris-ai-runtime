package io.aetheris.orchestrator.host;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Entity
@Table(name="aetheris_host_nodes")
public class HostNodeEntity {
    @Id private UUID id;
    @Column(nullable=false, unique=true, length=120) private String hostKey;
    @Column(nullable=false, length=200) private String displayName;
    @Column(nullable=false, length=80) private String platform;
    @Column(nullable=false, length=4000) private String capabilitiesCsv;
    @Column(nullable=false, length=200) private String publicKeyFingerprint;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=24) private HostStatus status;
    private Instant lastSeenAt;
    @Column(nullable=false) private Instant createdAt;
    @Column(nullable=false) private Instant updatedAt;
    @Version private long version;

    protected HostNodeEntity(){}
    public HostNodeEntity(UUID id,String hostKey,String displayName,String platform,Set<String> capabilities,String publicKeyFingerprint){
        this.id=id;this.hostKey=hostKey;this.displayName=displayName;this.platform=platform;this.capabilitiesCsv=join(capabilities);
        this.publicKeyFingerprint=publicKeyFingerprint;this.status=HostStatus.UNPAIRED;this.createdAt=Instant.now();this.updatedAt=this.createdAt;
    }
    public UUID getId(){return id;} public String getHostKey(){return hostKey;} public String getDisplayName(){return displayName;}
    public String getPlatform(){return platform;} public Set<String> getCapabilities(){return split(capabilitiesCsv);}
    public String getPublicKeyFingerprint(){return publicKeyFingerprint;} public HostStatus getStatus(){return status;}
    public Instant getLastSeenAt(){return lastSeenAt;} public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;} public long getVersion(){return version;}
    public boolean canExecute(){return status==HostStatus.ONLINE;}
    public void markPaired(){if(status!=HostStatus.UNPAIRED)throw new IllegalStateException("Host is not awaiting pairing");status=HostStatus.OFFLINE;updatedAt=Instant.now();}
    public void heartbeat(){if(status==HostStatus.UNPAIRED||status==HostStatus.REVOKED)throw new IllegalStateException("Host is not paired");status=HostStatus.ONLINE;lastSeenAt=Instant.now();updatedAt=lastSeenAt;}
    public void markOffline(){if(status==HostStatus.ONLINE){status=HostStatus.OFFLINE;updatedAt=Instant.now();}}
    public void revoke(){status=HostStatus.REVOKED;updatedAt=Instant.now();}
    private static String join(Set<String> values){if(values==null)return "";return values.stream().map(String::trim).filter(v->!v.isBlank()).sorted().collect(Collectors.joining(","));}
    private static Set<String> split(String csv){if(csv==null||csv.isBlank())return Set.of();return Arrays.stream(csv.split(",")).map(String::trim).filter(v->!v.isBlank()).collect(Collectors.toCollection(TreeSet::new));}
}

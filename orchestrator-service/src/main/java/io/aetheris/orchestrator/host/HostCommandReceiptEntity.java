package io.aetheris.orchestrator.host;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity
@Table(name="aetheris_host_command_receipts")
public class HostCommandReceiptEntity {
 @Id private UUID commandId; @Column(nullable=false) private UUID hostId; @Enumerated(EnumType.STRING) @Column(nullable=false,length=24) private HostCommandReceiptStatus status; @Column(nullable=false,length=128) private String signatureHash; @Column(length=4000) private String detail; @Column(nullable=false) private Instant acknowledgedAt;
 protected HostCommandReceiptEntity(){} public HostCommandReceiptEntity(UUID commandId,UUID hostId,HostCommandReceiptStatus status,String signatureHash,String detail){this.commandId=commandId;this.hostId=hostId;this.status=status;this.signatureHash=signatureHash;this.detail=detail;this.acknowledgedAt=Instant.now();}
 public UUID getCommandId(){return commandId;} public UUID getHostId(){return hostId;} public HostCommandReceiptStatus getStatus(){return status;} public String getSignatureHash(){return signatureHash;} public String getDetail(){return detail;} public Instant getAcknowledgedAt(){return acknowledgedAt;}
}

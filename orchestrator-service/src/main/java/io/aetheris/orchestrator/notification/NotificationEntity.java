package io.aetheris.orchestrator.notification;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity
@Table(name="aetheris_notifications")
public class NotificationEntity {
 @Id private UUID id; @Column(nullable=false,length=160) private String fingerprint; @Column(nullable=false,length=120) private String type; @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) private NotificationSeverity severity; @Column(nullable=false,length=240) private String title; @Column(nullable=false,length=4000) private String message; @Column(length=240) private String sourceRef; @Column(nullable=false) private boolean actionRequired; @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) private NotificationStatus status; @Column(nullable=false) private Instant createdAt; private Instant readAt;
 protected NotificationEntity(){} public NotificationEntity(UUID id,String fingerprint,String type,NotificationSeverity severity,String title,String message,String sourceRef,boolean actionRequired){this.id=id;this.fingerprint=fingerprint;this.type=type;this.severity=severity;this.title=title;this.message=message;this.sourceRef=sourceRef;this.actionRequired=actionRequired;this.status=NotificationStatus.UNREAD;this.createdAt=Instant.now();}
 public UUID getId(){return id;} public String getFingerprint(){return fingerprint;} public String getType(){return type;} public NotificationSeverity getSeverity(){return severity;} public String getTitle(){return title;} public String getMessage(){return message;} public String getSourceRef(){return sourceRef;} public boolean isActionRequired(){return actionRequired;} public NotificationStatus getStatus(){return status;} public Instant getCreatedAt(){return createdAt;} public Instant getReadAt(){return readAt;}
 public void read(){if(status!=NotificationStatus.DISMISSED){status=NotificationStatus.READ;readAt=Instant.now();}} public void dismiss(){status=NotificationStatus.DISMISSED;readAt=Instant.now();}
}

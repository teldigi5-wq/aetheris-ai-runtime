package io.aetheris.orchestrator.career;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="aetheris_career_audits")
public class CareerAuditEntity {@Id private UUID id;@Column(nullable=false,length=120) private String targetRole;@Column(nullable=false) private int readinessScore;@Column(nullable=false,length=8000) private String findings;@Column(nullable=false) private Instant createdAt;protected CareerAuditEntity(){}public CareerAuditEntity(UUID id,String targetRole,int score,String findings){this.id=id;this.targetRole=targetRole;this.readinessScore=score;this.findings=findings;this.createdAt=Instant.now();}public UUID getId(){return id;}public String getTargetRole(){return targetRole;}public int getReadinessScore(){return readinessScore;}public String getFindings(){return findings;}public Instant getCreatedAt(){return createdAt;}}

package io.aetheris.orchestrator.memory;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name="aetheris_memory_vectors",uniqueConstraints=@UniqueConstraint(columnNames={"knowledgeNodeId","adapterId"}))
public class MemoryVectorEntity {
    @Id private UUID id;
    @Column(nullable=false) private UUID knowledgeNodeId;
    @Column(nullable=false,length=120) private String adapterId;
    @Column(nullable=false) private int dimensions;
    @Lob @Column(nullable=false) private String vectorCsv;
    @Column(nullable=false) private Instant updatedAt;
    protected MemoryVectorEntity(){}
    public MemoryVectorEntity(UUID id,UUID nodeId,String adapterId,float[] vector){this.id=id;update(nodeId,adapterId,vector);}
    public void update(UUID nodeId,String adapterId,float[] vector){this.knowledgeNodeId=Objects.requireNonNull(nodeId);this.adapterId=Objects.requireNonNull(adapterId);this.dimensions=vector.length;StringBuilder b=new StringBuilder();for(int i=0;i<vector.length;i++){if(i>0)b.append(',');b.append(Float.toString(vector[i]));}this.vectorCsv=b.toString();this.updatedAt=Instant.now();}
    public float[] vector(){String[] parts=vectorCsv.split(",");float[] out=new float[parts.length];for(int i=0;i<parts.length;i++)out[i]=Float.parseFloat(parts[i]);return out;}
    public UUID getId(){return id;} public UUID getKnowledgeNodeId(){return knowledgeNodeId;} public String getAdapterId(){return adapterId;} public int getDimensions(){return dimensions;} public Instant getUpdatedAt(){return updatedAt;}
}

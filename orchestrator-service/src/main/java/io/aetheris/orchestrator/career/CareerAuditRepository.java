package io.aetheris.orchestrator.career;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface CareerAuditRepository extends JpaRepository<CareerAuditEntity,UUID>{List<CareerAuditEntity> findTop50ByOrderByCreatedAtDesc();}

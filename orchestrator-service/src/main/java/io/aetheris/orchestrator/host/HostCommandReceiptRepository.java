package io.aetheris.orchestrator.host;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface HostCommandReceiptRepository extends JpaRepository<HostCommandReceiptEntity,UUID>{List<HostCommandReceiptEntity> findTop100ByOrderByAcknowledgedAtDesc();}

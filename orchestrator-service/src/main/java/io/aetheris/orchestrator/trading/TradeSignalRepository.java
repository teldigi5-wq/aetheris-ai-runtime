package io.aetheris.orchestrator.trading;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface TradeSignalRepository extends JpaRepository<TradeSignalEntity,UUID>{List<TradeSignalEntity> findTop100ByOrderByCreatedAtDesc();}

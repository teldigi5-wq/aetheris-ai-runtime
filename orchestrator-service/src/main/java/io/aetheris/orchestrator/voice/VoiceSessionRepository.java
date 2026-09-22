package io.aetheris.orchestrator.voice;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface VoiceSessionRepository extends JpaRepository<VoiceSessionEntity,UUID>{List<VoiceSessionEntity> findTop100ByOrderByUpdatedAtDesc();}

package io.aetheris.orchestrator.notification;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface NotificationRepository extends JpaRepository<NotificationEntity,UUID>{List<NotificationEntity> findTop200ByOrderByCreatedAtDesc();Optional<NotificationEntity> findFirstByFingerprintOrderByCreatedAtDesc(String fingerprint);long countByStatus(NotificationStatus status);}

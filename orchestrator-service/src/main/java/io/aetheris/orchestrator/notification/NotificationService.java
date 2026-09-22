package io.aetheris.orchestrator.notification;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import java.util.*;
@Service
public class NotificationService {
 private final NotificationRepository repository; public NotificationService(NotificationRepository repository){this.repository=repository;}
 @Transactional public NotificationEntity publish(String fingerprint,String type,NotificationSeverity severity,String title,String message,String sourceRef,boolean actionRequired){String fp=required(fingerprint,"fingerprint");Optional<NotificationEntity> previous=repository.findFirstByFingerprintOrderByCreatedAtDesc(fp);if(previous.isPresent()&&previous.get().getStatus()==NotificationStatus.UNREAD)return previous.get();return repository.save(new NotificationEntity(UUID.randomUUID(),fp,required(type,"type"),severity==null?NotificationSeverity.INFO:severity,required(title,"title"),required(message,"message"),sourceRef,actionRequired));}
 public List<NotificationEntity> recent(){return repository.findTop200ByOrderByCreatedAtDesc();} public long unread(){return repository.countByStatus(NotificationStatus.UNREAD);}
 @Transactional public NotificationEntity read(UUID id){NotificationEntity n=required(id);n.read();return repository.save(n);} @Transactional public NotificationEntity dismiss(UUID id){NotificationEntity n=required(id);n.dismiss();return repository.save(n);} public NotificationEntity required(UUID id){return repository.findById(id).orElseThrow(()->new NoSuchElementException("Unknown notification: "+id));}
 private String required(String v,String label){if(v==null||v.isBlank())throw new IllegalArgumentException(label+" is required");return v.trim();}
}

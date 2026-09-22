package io.aetheris.orchestrator.api;
import io.aetheris.orchestrator.notification.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/orchestrator/notifications")
public class NotificationController {private final NotificationService notifications;public NotificationController(NotificationService notifications){this.notifications=notifications;}@GetMapping public List<NotificationEntity> recent(){return notifications.recent();}@GetMapping("/unread-count") public Map<String,Long> unread(){return Map.of("unread",notifications.unread());}@PostMapping("/{id}/read") public NotificationEntity read(@PathVariable UUID id){return notifications.read(id);}@PostMapping("/{id}/dismiss") public NotificationEntity dismiss(@PathVariable UUID id){return notifications.dismiss(id);}}

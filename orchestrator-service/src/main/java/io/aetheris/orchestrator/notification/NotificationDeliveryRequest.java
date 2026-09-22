package io.aetheris.orchestrator.notification;import java.util.UUID;public record NotificationDeliveryRequest(UUID taskId,UUID notificationId,NotificationChannel channel){}

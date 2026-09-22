package io.aetheris.orchestrator.mission;
import java.time.Instant;
import java.util.*;
public record MissionSessionView(UUID id,String title,String objective,MissionStatus status,Set<UUID> taskIds,Instant createdAt,Instant updatedAt,String liveStreamPath) {}

package io.aetheris.orchestrator.voice;
import java.util.UUID;
public record StartVoiceSessionRequest(UUID missionId,UUID taskId){}

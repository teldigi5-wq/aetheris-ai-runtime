package io.aetheris.orchestrator.mission;
import java.util.UUID;
public record MissionMessageRequest(MissionSpeaker speaker,String content,UUID taskId) {}

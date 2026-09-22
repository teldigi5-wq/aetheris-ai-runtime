package io.aetheris.orchestrator.voice;
public record VoiceCommandResult(boolean accepted,VoicePriorityCommand command,String detail){}

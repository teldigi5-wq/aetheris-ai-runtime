package io.aetheris.orchestrator.api;
import io.aetheris.orchestrator.voice.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/orchestrator/voice/sessions")
public class VoiceSessionController {private final VoiceSessionService voice;public VoiceSessionController(VoiceSessionService voice){this.voice=voice;}@PostMapping public VoiceSessionEntity start(@RequestBody StartVoiceSessionRequest r){return voice.start(r);}@GetMapping public List<VoiceSessionEntity> recent(){return voice.recent();}@PostMapping("/{id}/transcript") public VoiceSessionEntity transcript(@PathVariable UUID id,@RequestBody VoiceTranscriptRequest r){return voice.transcript(id,r);}@PostMapping("/{id}/barge-in") public VoiceSessionEntity barge(@PathVariable UUID id){return voice.bargeIn(id);}@PostMapping("/{id}/priority/{command}") public VoiceCommandResult command(@PathVariable UUID id,@PathVariable VoicePriorityCommand command){return voice.priority(id,command);}}

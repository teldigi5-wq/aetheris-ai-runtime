package io.aetheris.orchestrator.voice;

import io.aetheris.orchestrator.mission.MissionService;
import io.aetheris.orchestrator.task.*;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class VoiceSessionService {
 private final VoiceSessionRepository repository; private final MissionService missions; private final TaskService tasks; private final TaskControlService control;
 public VoiceSessionService(VoiceSessionRepository repository,MissionService missions,TaskService tasks,TaskControlService control){this.repository=repository;this.missions=missions;this.tasks=tasks;this.control=control;}
 @Transactional public VoiceSessionEntity start(StartVoiceSessionRequest r){if(r.missionId()!=null)missions.required(r.missionId());if(r.taskId()!=null)tasks.getRequired(r.taskId());return repository.save(new VoiceSessionEntity(UUID.randomUUID(),r.missionId(),r.taskId()));}
 public List<VoiceSessionEntity> recent(){return repository.findTop100ByOrderByUpdatedAtDesc();}
 @Transactional public VoiceSessionEntity transcript(UUID id,VoiceTranscriptRequest r){VoiceSessionEntity s=required(id);if(r.text()==null||r.text().isBlank())throw new IllegalArgumentException("Transcript text is required");s.transcript(r.text().trim());return repository.save(s);}
 @Transactional public VoiceSessionEntity bargeIn(UUID id){VoiceSessionEntity s=required(id);s.interrupt();return repository.save(s);}
 @Transactional public VoiceCommandResult priority(UUID id,VoicePriorityCommand command){VoiceSessionEntity s=required(id);UUID taskId=s.getTaskId();switch(command){case STOP->{if(taskId==null)control.engage("Deterministic STOP from Syntra voice session");else control.cancelTask(taskId,"Deterministic STOP from Syntra voice session");s.stop();}case PAUSE->{requireTask(taskId);control.pauseTask(taskId,"Voice PAUSE");s.interrupt();}case RESUME->{requireTask(taskId);control.resumeTask(taskId,"Voice RESUME");s.transcript("RESUME");}case TAKE_CONTROL->{requireTask(taskId);control.takeControl(taskId,"Voice TAKE CONTROL");s.interrupt();}}repository.save(s);return new VoiceCommandResult(true,command,"Priority command executed through the deterministic control path without model inference");}
 public VoiceSessionEntity required(UUID id){return repository.findById(id).orElseThrow(()->new NoSuchElementException("Unknown voice session: "+id));} private void requireTask(UUID taskId){if(taskId==null)throw new IllegalStateException("Voice command requires a task-bound session");}
}

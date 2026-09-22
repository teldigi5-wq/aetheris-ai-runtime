package io.aetheris.orchestrator.mission;

import io.aetheris.orchestrator.task.*;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class MissionService {
    private final MissionSessionRepository sessions; private final MissionMessageRepository messages; private final TaskService tasks; private final TaskEventStreamService events;
    public MissionService(MissionSessionRepository sessions,MissionMessageRepository messages,TaskService tasks,TaskEventStreamService events){this.sessions=sessions;this.messages=messages;this.tasks=tasks;this.events=events;}
    public MissionSessionView create(CreateMissionRequest r){if(r.title()==null||r.title().isBlank()||r.objective()==null||r.objective().isBlank())throw new IllegalArgumentException("Mission title and objective are required");return view(sessions.save(new MissionSessionEntity(UUID.randomUUID(),r.title().trim(),r.objective().trim())));}
    public List<MissionSessionView> recent(){return sessions.findTop100ByOrderByUpdatedAtDesc().stream().map(this::view).toList();}
    public MissionSessionEntity required(UUID id){return sessions.findById(id).orElseThrow(()->new NoSuchElementException("Unknown mission: "+id));}
    @Transactional public MissionSessionView attach(UUID id,UUID taskId){tasks.getRequired(taskId);MissionSessionEntity m=required(id);m.attach(taskId);return view(sessions.save(m));}
    @Transactional public MissionSessionView status(UUID id,MissionStatus status){MissionSessionEntity m=required(id);m.status(status);return view(sessions.save(m));}
    public MissionMessageEntity message(UUID id,MissionMessageRequest r){required(id);if(r.content()==null||r.content().isBlank())throw new IllegalArgumentException("Mission message content is required");if(r.taskId()!=null)tasks.getRequired(r.taskId());return messages.save(new MissionMessageEntity(UUID.randomUUID(),id,r.taskId(),r.speaker()==null?MissionSpeaker.OWNER:r.speaker(),r.content().trim()));}
    public List<MissionMessageEntity> messages(UUID id){required(id);return messages.findTop200ByMissionIdOrderByCreatedAtAsc(id);}
    public List<TaskEvent> taskEvents(UUID id){MissionSessionEntity m=required(id);return m.getTaskIds().stream().flatMap(taskId->events.history(taskId).stream()).sorted(Comparator.comparing(TaskEvent::timestamp)).toList();}
    private MissionSessionView view(MissionSessionEntity m){return new MissionSessionView(m.getId(),m.getTitle(),m.getObjective(),m.getStatus(),m.getTaskIds(),m.getCreatedAt(),m.getUpdatedAt(),"/api/orchestrator/live/events");}
}

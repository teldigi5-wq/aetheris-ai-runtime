package io.aetheris.orchestrator.api;
import io.aetheris.orchestrator.mission.*;
import io.aetheris.orchestrator.task.TaskEvent;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/orchestrator/missions")
public class MissionController {
    private final MissionService missions; public MissionController(MissionService missions){this.missions=missions;}
    @PostMapping public MissionSessionView create(@RequestBody CreateMissionRequest r){return missions.create(r);}
    @GetMapping public List<MissionSessionView> recent(){return missions.recent();}
    @PostMapping("/{id}/tasks/{taskId}") public MissionSessionView attach(@PathVariable UUID id,@PathVariable UUID taskId){return missions.attach(id,taskId);}
    @PostMapping("/{id}/status/{status}") public MissionSessionView status(@PathVariable UUID id,@PathVariable MissionStatus status){return missions.status(id,status);}
    @PostMapping("/{id}/messages") public MissionMessageEntity message(@PathVariable UUID id,@RequestBody MissionMessageRequest r){return missions.message(id,r);}
    @GetMapping("/{id}/messages") public List<MissionMessageEntity> messages(@PathVariable UUID id){return missions.messages(id);}
    @GetMapping("/{id}/task-events") public List<TaskEvent> events(@PathVariable UUID id){return missions.taskEvents(id);}
}

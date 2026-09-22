package io.aetheris.orchestrator.api;

import io.aetheris.orchestrator.task.EmergencyStopStatus;
import io.aetheris.orchestrator.task.TaskControlService;
import io.aetheris.orchestrator.task.TaskEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/orchestrator/control")
public class ControlController {
    private final TaskControlService control;
    public ControlController(TaskControlService control){this.control=control;}
    @GetMapping("/emergency-stop") public EmergencyStopStatus status(){return control.status();}
    @PostMapping("/emergency-stop/engage") public EmergencyStopStatus engage(@RequestBody(required=false) Map<String,String> body){return control.engage(body==null?null:body.get("reason"));}
    @PostMapping("/emergency-stop/release") public EmergencyStopStatus release(@RequestBody(required=false) Map<String,String> body){return control.release(body==null?null:body.get("reason"));}
    @PostMapping("/tasks/{taskId}/cancel") public TaskEntity cancel(@PathVariable UUID taskId,@RequestBody(required=false) Map<String,String> body){return control.cancelTask(taskId,body==null?null:body.get("reason"));}
    @PostMapping("/tasks/{taskId}/pause") public TaskEntity pause(@PathVariable UUID taskId,@RequestBody(required=false) Map<String,String> body){return control.pauseTask(taskId,body==null?null:body.get("reason"));}
    @PostMapping("/tasks/{taskId}/resume") public TaskEntity resume(@PathVariable UUID taskId,@RequestBody(required=false) Map<String,String> body){return control.resumeTask(taskId,body==null?null:body.get("reason"));}
    @PostMapping("/tasks/{taskId}/take-control") public TaskEntity takeControl(@PathVariable UUID taskId,@RequestBody(required=false) Map<String,String> body){return control.takeControl(taskId,body==null?null:body.get("reason"));}
}

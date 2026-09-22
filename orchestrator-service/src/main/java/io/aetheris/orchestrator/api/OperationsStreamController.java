package io.aetheris.orchestrator.api;

import io.aetheris.orchestrator.task.TaskEventStreamService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/orchestrator/live")
public class OperationsStreamController {
    private final TaskEventStreamService streams;
    public OperationsStreamController(TaskEventStreamService streams){this.streams=streams;}
    @GetMapping(value="/events",produces=MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(){return streams.subscribeAll();}
}

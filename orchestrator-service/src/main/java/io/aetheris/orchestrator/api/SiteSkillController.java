package io.aetheris.orchestrator.api;

import io.aetheris.orchestrator.operator.AutonomousWebTaskService;
import io.aetheris.orchestrator.operator.SiteSkillTypes.SiteSkillDescriptor;
import io.aetheris.orchestrator.operator.SiteSkillTypes.WebTaskCompilation;
import io.aetheris.orchestrator.operator.SiteSkillTypes.WebTaskExecuteRequest;
import io.aetheris.orchestrator.operator.SiteSkillTypes.WebTaskExecutionResponse;
import io.aetheris.orchestrator.operator.SiteSkillTypes.WebTaskRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/orchestrator/operator/site-skills")
public class SiteSkillController {

    private final AutonomousWebTaskService tasks;

    public SiteSkillController(AutonomousWebTaskService tasks) {
        this.tasks = tasks;
    }

    @GetMapping
    public List<SiteSkillDescriptor> skills() {
        return tasks.skills();
    }

    @PostMapping("/compile")
    public WebTaskCompilation compile(@RequestBody WebTaskRequest request) {
        return tasks.compile(request);
    }

    @PostMapping("/execute")
    public WebTaskExecutionResponse execute(@RequestBody WebTaskExecuteRequest request) {
        return tasks.execute(request);
    }
}

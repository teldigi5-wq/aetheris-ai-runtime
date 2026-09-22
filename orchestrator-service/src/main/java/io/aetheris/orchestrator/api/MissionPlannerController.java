package io.aetheris.orchestrator.api;

import io.aetheris.orchestrator.planner.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/orchestrator/missions/{missionId}/plan")
public class MissionPlannerController {
    private final MissionPlannerService planner; public MissionPlannerController(MissionPlannerService planner){this.planner=planner;}
    @PostMapping public List<MissionPlanNodeEntity> plan(@PathVariable UUID missionId,@RequestBody PlanMissionRequest request){return planner.plan(missionId,request);}
    @GetMapping public List<MissionPlanNodeEntity> nodes(@PathVariable UUID missionId){return planner.nodes(missionId);}
    @PostMapping("/release-ready") public List<MissionPlanNodeEntity> release(@PathVariable UUID missionId){return planner.releaseReady(missionId);}
}

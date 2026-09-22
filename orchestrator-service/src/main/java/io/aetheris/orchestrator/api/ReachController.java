package io.aetheris.orchestrator.api;

import io.aetheris.orchestrator.reach.ReachCatalogService;
import io.aetheris.orchestrator.reach.ReachService;
import io.aetheris.orchestrator.reach.ReachTypes.ChannelDescriptor;
import io.aetheris.orchestrator.reach.ReachTypes.DoctorReport;
import io.aetheris.orchestrator.reach.ReachTypes.DoctorRequest;
import io.aetheris.orchestrator.reach.ReachTypes.InstallPlan;
import io.aetheris.orchestrator.reach.ReachTypes.InstallPlanRequest;
import io.aetheris.orchestrator.reach.ReachTypes.RouteDecision;
import io.aetheris.orchestrator.reach.ReachTypes.RouteRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orchestrator/reach")
public class ReachController {

    private final ReachCatalogService catalog;
    private final ReachService reach;

    public ReachController(ReachCatalogService catalog, ReachService reach) {
        this.catalog = catalog;
        this.reach = reach;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return reach.status();
    }

    @GetMapping("/channels")
    public List<ChannelDescriptor> channels() {
        return catalog.channels();
    }

    @PostMapping("/route")
    public RouteDecision route(@RequestBody RouteRequest request) {
        return reach.route(request);
    }

    @PostMapping("/doctor")
    public DoctorReport doctor(@RequestBody(required = false) DoctorRequest request) {
        return reach.doctor(request);
    }

    @PostMapping("/install-plan")
    public InstallPlan installPlan(@RequestBody(required = false) InstallPlanRequest request) {
        return reach.installPlan(request);
    }
}

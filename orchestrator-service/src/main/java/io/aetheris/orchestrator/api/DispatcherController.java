package io.aetheris.orchestrator.api;
import io.aetheris.orchestrator.dispatcher.*;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/orchestrator/stage8/dispatcher") public class DispatcherController {private final AutonomousDispatcherService service;public DispatcherController(AutonomousDispatcherService service){this.service=service;}@PostMapping("/tick") public DispatcherTickResult tick(){return service.tick();}@GetMapping("/last") public DispatcherTickResult last(){return service.last();}}

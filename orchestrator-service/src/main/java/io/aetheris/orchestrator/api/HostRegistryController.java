package io.aetheris.orchestrator.api;

import io.aetheris.orchestrator.host.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/orchestrator/hosts")
public class HostRegistryController {
    private final HostRegistryService hosts; private final HostPairingService pairing; private final HostCommandService commands;
    public HostRegistryController(HostRegistryService hosts,HostPairingService pairing,HostCommandService commands){this.hosts=hosts;this.pairing=pairing;this.commands=commands;}
    @PostMapping public HostNodeEntity register(@Valid @RequestBody HostRegistrationRequest request){return hosts.register(request);}
    @GetMapping public List<HostNodeEntity> list(){return hosts.list();}
    @GetMapping("/{id}") public HostNodeEntity get(@PathVariable UUID id){return hosts.getRequired(id);}
    @PostMapping("/{id}/pairing-challenges") public HostPairingChallengeResponse beginPairing(@PathVariable UUID id){return pairing.begin(id);}
    @PostMapping("/{id}/pairing-challenges/{challengeId}/complete") public HostNodeEntity completePairing(@PathVariable UUID id,@PathVariable UUID challengeId,@RequestBody CompleteHostPairingRequest request){return pairing.complete(id,challengeId,request);}
    @PostMapping("/{id}/heartbeat") public HostNodeEntity heartbeat(@PathVariable UUID id){return pairing.heartbeat(id);}
    @PostMapping("/{id}/revoke") public HostNodeEntity revoke(@PathVariable UUID id){return pairing.revoke(id);}
    @PostMapping("/commands") public HostCommandEnvelope issue(@RequestBody HostCommandRequest request){return commands.issue(request);}
    @PostMapping("/commands/simulate") public Map<String,Object> simulate(@RequestBody HostCommandEnvelope envelope){return commands.simulate(envelope);}
    @GetMapping("/{id}/telemetry/simulated") public HostTelemetrySnapshot telemetry(@PathVariable UUID id){return commands.simulatedTelemetry(id);}
}

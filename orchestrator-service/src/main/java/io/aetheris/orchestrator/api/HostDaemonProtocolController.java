package io.aetheris.orchestrator.api;
import io.aetheris.orchestrator.host.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/orchestrator/hosts/protocol")
public class HostDaemonProtocolController {private final HostDaemonProtocolService protocol;public HostDaemonProtocolController(HostDaemonProtocolService protocol){this.protocol=protocol;}@PostMapping("/simulate-once") public HostCommandReceiptEntity simulate(@RequestBody HostCommandEnvelope envelope){return protocol.simulateOnce(envelope);}@GetMapping("/receipts") public List<HostCommandReceiptEntity> receipts(){return protocol.recent();}}

package io.aetheris.orchestrator.host;

import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service
public class HostDaemonProtocolService {
    private final HostCommandService commands; private final HostCommandReceiptRepository receipts;
    public HostDaemonProtocolService(HostCommandService commands,HostCommandReceiptRepository receipts){this.commands=commands;this.receipts=receipts;}
    public HostCommandReceiptEntity simulateOnce(HostCommandEnvelope envelope){if(receipts.existsById(envelope.commandId()))throw new IllegalStateException("Host command replay detected: "+envelope.commandId());String hash=hash(envelope.signature());try{Map<String,Object> result=commands.simulate(envelope);HostCommandReceiptEntity receipt=new HostCommandReceiptEntity(envelope.commandId(),envelope.hostId(),HostCommandReceiptStatus.ACKNOWLEDGED,hash,String.valueOf(result.getOrDefault("detail","Simulated command acknowledged")));return receipts.save(receipt);}catch(RuntimeException ex){receipts.save(new HostCommandReceiptEntity(envelope.commandId(),envelope.hostId(),HostCommandReceiptStatus.REJECTED,hash,ex.getMessage()==null?ex.getClass().getSimpleName():ex.getMessage()));throw ex;}}
    public List<HostCommandReceiptEntity> recent(){return receipts.findTop100ByOrderByAcknowledgedAtDesc();}
    private String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((value==null?"":value).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException("Unable to hash host envelope signature",e);}}
}

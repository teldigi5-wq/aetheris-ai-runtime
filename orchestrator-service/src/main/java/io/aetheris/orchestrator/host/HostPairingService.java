package io.aetheris.orchestrator.host;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.time.*;
import java.util.*;

@Service
public class HostPairingService {
    private final HostRegistryService hosts; private final HostPairingChallengeRepository challenges; private final HostPairingCredentialRepository credentials;
    public HostPairingService(HostRegistryService hosts,HostPairingChallengeRepository challenges,HostPairingCredentialRepository credentials){this.hosts=hosts;this.challenges=challenges;this.credentials=credentials;}
    @Transactional public HostPairingChallengeResponse begin(UUID hostId){HostNodeEntity host=hosts.getRequired(hostId);if(host.getStatus()!=HostStatus.UNPAIRED)throw new IllegalStateException("Host is not awaiting pairing");byte[] nonceBytes=new byte[32];new SecureRandom().nextBytes(nonceBytes);String nonce=Base64.getUrlEncoder().withoutPadding().encodeToString(nonceBytes);UUID id=UUID.randomUUID();Instant expires=Instant.now().plus(Duration.ofMinutes(5));challenges.save(new HostPairingChallengeEntity(id,hostId,sha256(nonce),expires));return new HostPairingChallengeResponse(id,hostId,nonce,expires,"hostId:challengeId:nonce");}
    @Transactional public HostNodeEntity complete(UUID hostId,UUID challengeId,CompleteHostPairingRequest request){HostNodeEntity host=hosts.getRequired(hostId);HostPairingChallengeEntity challenge=challenges.findById(challengeId).orElseThrow(()->new NoSuchElementException("Unknown pairing challenge"));if(!challenge.getHostId().equals(hostId))throw new IllegalArgumentException("Challenge does not belong to host");if(!MessageDigest.isEqual(hexBytes(challenge.getNonceHash()),hexBytes(sha256(request.nonce()))))throw new IllegalArgumentException("Pairing nonce mismatch");PublicKey publicKey=parsePublicKey(request.publicKeyPem());String fingerprint=fingerprint(publicKey);String registered=host.getPublicKeyFingerprint().replace(":","").toLowerCase(Locale.ROOT);if(!registered.equals(fingerprint))throw new IllegalArgumentException("Pairing public key fingerprint mismatch");String message=hostId+":"+challengeId+":"+request.nonce();if(!verify(publicKey,message,request.signatureBase64()))throw new IllegalArgumentException("Pairing signature verification failed");challenge.consume();credentials.findByHostId(hostId).ifPresent(existing->{throw new IllegalStateException("Host already has a pairing credential");});credentials.save(new HostPairingCredentialEntity(UUID.randomUUID(),hostId,request.publicKeyPem().trim(),fingerprint));host.markPaired();return hosts.save(host);}
    @Transactional public HostNodeEntity heartbeat(UUID hostId){HostPairingCredentialEntity credential=credentials.findByHostId(hostId).filter(HostPairingCredentialEntity::active).orElseThrow(()->new IllegalStateException("Host is not actively paired"));HostNodeEntity host=hosts.getRequired(credential.getHostId());host.heartbeat();return hosts.save(host);}
    @Transactional public HostNodeEntity revoke(UUID hostId){credentials.findByHostId(hostId).ifPresent(HostPairingCredentialEntity::revoke);HostNodeEntity host=hosts.getRequired(hostId);host.revoke();return hosts.save(host);}
    private PublicKey parsePublicKey(String pem){try{String clean=pem.replace("-----BEGIN PUBLIC KEY-----","").replace("-----END PUBLIC KEY-----","").replaceAll("\\s","");byte[] der=Base64.getDecoder().decode(clean);for(String alg:List.of("RSA","EC")){try{return KeyFactory.getInstance(alg).generatePublic(new X509EncodedKeySpec(der));}catch(Exception ignored){}}throw new IllegalArgumentException("Unsupported host public key");}catch(IllegalArgumentException e){throw e;}catch(Exception e){throw new IllegalArgumentException("Invalid host public key",e);}}
    private boolean verify(PublicKey key,String message,String signatureBase64){try{String alg=key.getAlgorithm().equalsIgnoreCase("EC")?"SHA256withECDSA":"SHA256withRSA";Signature sig=Signature.getInstance(alg);sig.initVerify(key);sig.update(message.getBytes(StandardCharsets.UTF_8));return sig.verify(Base64.getDecoder().decode(signatureBase64));}catch(Exception e){return false;}}
    private String fingerprint(PublicKey key){return sha256(key.getEncoded());}
    private String sha256(String text){return sha256(text.getBytes(StandardCharsets.UTF_8));}
    private String sha256(byte[] value){try{byte[] digest=MessageDigest.getInstance("SHA-256").digest(value);return HexFormat.of().formatHex(digest);}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private byte[] hexBytes(String value){return HexFormat.of().parseHex(value);}
}

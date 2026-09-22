package io.aetheris.orchestrator.memory;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class MemoryEncryptionService {
    private static final String PREFIX="enc:v1:";
    private static final int IV_BYTES=12;
    private static final int TAG_BITS=128;
    private final SecretKey key;
    private final SecureRandom random=new SecureRandom();
    private final MeterRegistry metrics;

    public MemoryEncryptionService(@Value("${aetheris.memory.master-key-b64:}") String encodedKey,MeterRegistry metrics){
        this.metrics=metrics;
        this.key=parse(encodedKey);
    }

    public ProtectedPayload protect(String plaintext,boolean sensitive){
        if(!sensitive)return new ProtectedPayload(plaintext,false);
        if(key==null){metrics.counter("aetheris_memory_crypto_failures_total","operation","encrypt").increment();throw new IllegalStateException("Sensitive memory requires aetheris.memory.master-key-b64");}
        try{
            byte[] iv=new byte[IV_BYTES]; random.nextBytes(iv);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(TAG_BITS,iv));
            byte[] cipherText=cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return new ProtectedPayload(PREFIX+Base64.getEncoder().encodeToString(iv)+":"+Base64.getEncoder().encodeToString(cipherText),true);
        }catch(Exception ex){metrics.counter("aetheris_memory_crypto_failures_total","operation","encrypt").increment();throw new IllegalStateException("Unable to encrypt memory payload",ex);}
    }

    public String reveal(String payload,boolean encrypted){
        if(!encrypted)return payload;
        if(key==null){metrics.counter("aetheris_memory_crypto_failures_total","operation","decrypt").increment();throw new IllegalStateException("Memory decryption key is not configured");}
        try{
            if(payload==null||!payload.startsWith(PREFIX))throw new IllegalArgumentException("Unsupported encrypted memory payload");
            String[] parts=payload.substring(PREFIX.length()).split(":",2);
            if(parts.length!=2)throw new IllegalArgumentException("Malformed encrypted memory payload");
            byte[] iv=Base64.getDecoder().decode(parts[0]);
            byte[] cipherText=Base64.getDecoder().decode(parts[1]);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(TAG_BITS,iv));
            return new String(cipher.doFinal(cipherText),StandardCharsets.UTF_8);
        }catch(Exception ex){metrics.counter("aetheris_memory_crypto_failures_total","operation","decrypt").increment();throw new IllegalStateException("Unable to decrypt memory payload",ex);}
    }

    public boolean configured(){return key!=null;}

    private SecretKey parse(String encoded){
        if(encoded==null||encoded.isBlank())return null;
        byte[] raw;
        try{raw=Base64.getDecoder().decode(encoded.trim());}catch(IllegalArgumentException ex){throw new IllegalStateException("aetheris.memory.master-key-b64 must be valid Base64",ex);}
        if(raw.length!=32)throw new IllegalStateException("aetheris.memory.master-key-b64 must decode to exactly 32 bytes");
        return new SecretKeySpec(raw,"AES");
    }

    public record ProtectedPayload(String payload,boolean encrypted){}
}

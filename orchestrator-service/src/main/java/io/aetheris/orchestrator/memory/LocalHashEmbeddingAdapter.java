package io.aetheris.orchestrator.memory;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Component
@Primary
public class LocalHashEmbeddingAdapter implements EmbeddingAdapter {
    private static final int DIMENSIONS=96;
    @Override public String id(){return "local-hash-v1";}
    @Override public int dimensions(){return DIMENSIONS;}
    @Override public float[] embed(String text){float[] vector=new float[DIMENSIONS];if(text==null||text.isBlank())return vector;for(String token:text.toLowerCase(Locale.ROOT).split("[^a-z0-9_+#.-]+")){if(token.length()<2)continue;byte[] digest=digest(token);int bucket=((digest[0]&0xff)<<8 | (digest[1]&0xff))%DIMENSIONS;float sign=(digest[2]&1)==0?1f:-1f;float weight=1f+Math.min(3f,token.length()/8f);vector[bucket]+=sign*weight;}double norm=0d;for(float v:vector)norm+=v*v;if(norm>0){float scale=(float)(1d/Math.sqrt(norm));for(int i=0;i<vector.length;i++)vector[i]*=scale;}return vector;}
    private byte[] digest(String token){try{return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}

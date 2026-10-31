package com.example.bulgariantrainer.service;

import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.core.StreamReadFeature;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;

/** Dedicated strict mapper; never changes JSON handling in existing modes. */
@Component
public class AspectPairJson {
    private final ObjectMapper mapper=JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    public JsonNode tree(String value){return mapper.readTree(value);}
    public String write(Object value){return mapper.writeValueAsString(value);}
    public <T> T read(String value,Class<T> type){return mapper.readValue(value,type);}
    public <T> T convert(JsonNode value,Class<T> type){return mapper.treeToValue(value,type);}
    public String hash(Object value){return hashBytes(write(value).getBytes(StandardCharsets.UTF_8));}
    public String hashBytes(byte[] value){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}
        catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
}

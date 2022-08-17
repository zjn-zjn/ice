package com.ice.common.utils;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.io.IOException;

/**
 * @author waitmoon
 */
public final class JacksonUtils {

    private static ObjectMapper mapper() {
        return JsonMapper.builder()
                .serializationInclusion(JsonInclude.Include.NON_NULL)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(JsonGenerator.Feature.IGNORE_UNKNOWN, true)
                .configure(JsonParser.Feature.ALLOW_COMMENTS, true)
                .configure(JsonReadFeature.ALLOW_MISSING_VALUES.mappedFeature(), true)
                .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
                .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
                .build();
    }

    public static String toJsonString(Object obj) {
        try {
            return mapper().writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            //ignore
        }
        return null;
    }

    public static byte[] toJsonBytes(Object obj) {
        try {
            return mapper().writeValueAsBytes(obj);
        } catch (JsonProcessingException e) {
            //ignore
        }
        return null;
    }

    public static <T> T readJson(String json, Class<T> clazz) throws JsonProcessingException {
        return mapper().readValue(json, clazz);
    }

    public static <T> T readJsonBytes(byte[] jsonBytes, Class<T> clazz) throws IOException {
        return mapper().readValue(jsonBytes, clazz);
    }

    public static boolean isJson(String json) {
        try {
            mapper().readTree(json);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean isJsonObject(String json) {
        try {
            JsonNode node = mapper().readTree(json);
            return node.isObject();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean isJsonArray(String json) {
        try {
            JsonNode node = mapper().readTree(json);
            return node.isArray();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
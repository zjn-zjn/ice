package com.ice.common.utils;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * @author zjn
 */
public final class JacksonUtils {

    private static ObjectMapper mapper() {
        return new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL).configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
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
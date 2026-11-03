package com.zhul.erp.modules.masterdata.customerprofile.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/** 画像里多选字段存成 JSON 数组（字典码值），这里统一读写 */
public final class JsonArrays {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonArrays() {
    }

    public static boolean nonEmpty(String json) {
        return !ints(json).isEmpty() || hasObjects(json);
    }

    public static List<Integer> ints(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<Object> raw = MAPPER.readValue(json, new TypeReference<>() { });
            List<Integer> out = new ArrayList<>(raw.size());
            for (Object o : raw) {
                if (o instanceof Number n) {
                    out.add(n.intValue());
                }
            }
            return out;
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    public static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value == null ? List.of() : value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public static <T> List<T> read(String json, TypeReference<List<T>> type) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<T> v = MAPPER.readValue(json, type);
            return v == null ? List.of() : v;
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private static boolean hasObjects(String json) {
        if (!StringUtils.hasText(json)) {
            return false;
        }
        try {
            return !MAPPER.readTree(json).isEmpty() && MAPPER.readTree(json).get(0).isObject();
        } catch (JsonProcessingException e) {
            return false;
        }
    }
}

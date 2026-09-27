package com.zhul.erp.modules.masterdata.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * 国家（ISO 3166-1 两位代码）→ IANA 时区列表，启动时从 classpath:masterdata/country-timezones.json 加载。
 * 数据由 npm 包 countries-and-timezones 3.10.0（MIT）生成，只保留以该国为主归属国的时区
 * （如德国不含苏黎世）；只有一个时区的国家，前端选择国家后直接带出。
 */
@Component
@RequiredArgsConstructor
public class CountryTimezoneCatalog {

    private static final String RESOURCE = "masterdata/country-timezones.json";

    private final ObjectMapper objectMapper;

    private Map<String, List<String>> byCountry = Map.of();

    @PostConstruct
    public void load() throws IOException {
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            byCountry = Map.copyOf(objectMapper.readValue(in, new TypeReference<Map<String, List<String>>>() {
            }));
        }
    }

    public Map<String, List<String>> all() {
        return byCountry;
    }
}

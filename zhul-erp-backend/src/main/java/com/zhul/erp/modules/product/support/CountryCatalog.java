package com.zhul.erp.modules.product.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.modules.product.dto.CountryVO;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 统一的国家/地区清单（ISO 3166-1），启动时从 classpath:product/countries.json 加载。
 * 品牌原产地保存英文名；写入时用 {@link #canonicalName(String)} 校验，比较忽略大小写和首尾空格。
 */
@Component
@RequiredArgsConstructor
public class CountryCatalog {

    private static final String RESOURCE = "product/countries.json";

    private final ObjectMapper objectMapper;

    private List<CountryVO> countries = List.of();
    private final Map<String, String> byLowerName = new HashMap<>(512);

    @PostConstruct
    public void load() throws IOException {
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            countries = List.copyOf(objectMapper.readValue(in, new TypeReference<List<CountryVO>>() {
            }));
        }
        for (CountryVO country : countries) {
            byLowerName.put(country.getNameEn().toLowerCase(Locale.ROOT), country.getNameEn());
        }
    }

    public List<CountryVO> all() {
        return countries;
    }

    /** 返回清单里的规范英文名；不在清单内返回 null */
    public String canonicalName(String input) {
        if (input == null) {
            return null;
        }
        return byLowerName.get(input.trim().toLowerCase(Locale.ROOT));
    }
}

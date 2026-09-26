package com.zhul.erp.modules.masterdata.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.modules.masterdata.dto.RegionVO;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * 中国省市区三级行政区划，启动时从 classpath:masterdata/regions.json 加载。
 * 数据取自 npm 包 china-division 2.7.0 的 pca-code.json，直辖市的"市辖区""县"两级已合并为与省同名的一级，
 * 使路径读作「上海市/上海市/浦东新区」。
 */
@Component
@RequiredArgsConstructor
public class RegionCatalog {

    private static final String RESOURCE = "masterdata/regions.json";

    private final ObjectMapper objectMapper;

    private List<RegionVO> regions = List.of();

    @PostConstruct
    public void load() throws IOException {
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            regions = List.copyOf(objectMapper.readValue(in, new TypeReference<List<RegionVO>>() {
            }));
        }
    }

    public List<RegionVO> all() {
        return regions;
    }
}

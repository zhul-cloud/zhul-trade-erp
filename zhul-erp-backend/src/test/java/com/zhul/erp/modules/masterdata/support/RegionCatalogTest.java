package com.zhul.erp.modules.masterdata.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.modules.masterdata.dto.RegionVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RegionCatalogTest {

    private RegionCatalog catalog;

    @BeforeEach
    void setUp() throws Exception {
        catalog = new RegionCatalog(new ObjectMapper());
        catalog.load();
    }

    @Test
    void municipalityReadsAsProvinceCityDistrictWithSameCityName() {
        RegionVO shanghai = find(catalog.all(), "上海市");
        assertThat(shanghai.getChildren()).hasSize(1);
        RegionVO city = shanghai.getChildren().get(0);
        assertThat(city.getName()).isEqualTo("上海市");
        assertThat(find(city.getChildren(), "浦东新区").getCode()).isEqualTo("310115");
    }

    @Test
    void chongqingCountiesAreMergedUnderOneCity() {
        RegionVO chongqing = find(catalog.all(), "重庆市");
        assertThat(chongqing.getChildren()).hasSize(1);
        assertThat(find(chongqing.getChildren().get(0).getChildren(), "巫山县")).isNotNull();
    }

    @Test
    void everyProvinceHasCitiesAndEveryCityHasDistricts() {
        assertThat(catalog.all()).hasSize(31);
        for (RegionVO province : catalog.all()) {
            assertThat(province.getChildren()).as(province.getName()).isNotEmpty();
            for (RegionVO city : province.getChildren()) {
                assertThat(city.getChildren()).as(province.getName() + "/" + city.getName()).isNotEmpty();
            }
        }
    }

    private static RegionVO find(List<RegionVO> nodes, String name) {
        return nodes.stream().filter(n -> n.getName().equals(name)).findFirst().orElseThrow();
    }
}

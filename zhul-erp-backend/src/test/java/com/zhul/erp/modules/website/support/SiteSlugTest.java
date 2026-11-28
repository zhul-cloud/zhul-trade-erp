package com.zhul.erp.modules.website.support;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 与站点仓库 scripts/lib/site-data.js 的 productSlug 一致；期望值由站点的 JS 实现算出 */
class SiteSlugTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "Siemens||6ES7214-1AG40-0XB0|siemens-6es7214-1ag40-0xb0",
        "Mitsubishi|FR-A800|FR-A840-7.5K|mitsubishi-fr-a800-fr-a840-7-5k",
        "ABB|Plastic Fibre Optic|61059113|abb-plastic-fibre-optic-61059113",
        "Allen Bradley||1756-L71/B|allen-bradley-1756-l71-b",
        "'  Schneider_Electric '|Altivar 320|ATV320U07M2C|schneider-electric-altivar-320-atv320u07m2c",
        "SMC||CDQ2B32-50DZ (new)|smc-cdq2b32-50dz-new",
        "Omron|E3Z|E3Z-D61 2M|omron-e3z-e3z-d61-2m",
        "Phoenix Contact||2866763 — QUINT|phoenix-contact-2866763-quint",
    })
    void matchesSiteImplementation(String brand, String series, String model, String expected) {
        assertEquals(expected, SiteSlug.of(brand, series, model));
    }
}

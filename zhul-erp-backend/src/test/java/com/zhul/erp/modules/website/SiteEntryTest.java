package com.zhul.erp.modules.website;

import com.zhul.erp.modules.website.support.SiteEntry;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** spec website-import「站点原有内容与 ERP 内容的取舍」：导入前后的字段取舍 */
class SiteEntryTest {

    private static final LocalDateTime IMPORTED = LocalDateTime.of(2026, 11, 26, 10, 0);

    private static Map<String, Object> identity() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("brand", "Siemens");
        m.put("model", "6ES7212-1AE40-0XB0");
        m.put("series", null);
        m.put("cat", "controllers");
        m.put("slug", "siemens-6es7212-1ae40-0xb0");
        m.put("release", "WP10001");
        return m;
    }

    private static Map<String, Object> erp() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("spec", "CPU 1212C (ERP)");
        m.put("overview", "ERP overview");
        m.put("meta_description", "ERP meta");
        m.put("faq", List.of(Map.of("q", "ERP?", "a", "Yes")));
        m.put("specs", List.of());
        m.put("photos", List.of(Map.of("src", "https://oss/x.jpg", "tag", "Photo")));
        m.put("photo", "https://oss/x.jpg");
        m.put("status", "instock");
        return m;
    }

    private static Map<String, Object> original() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("brand", "Siemens");
        m.put("model", "6ES7212-1AE40-0XB0");
        m.put("series", "S7-1200");
        m.put("cat", "plc");
        m.put("spec", "CPU 1212C (site)");
        m.put("overview", "site overview");
        m.put("meta_description", "site meta");
        m.put("faq", List.of(Map.of("q", "site?", "a", "Yes")));
        m.put("specs", List.of(List.of("Order code", "6ES7212")));
        m.put("photo", "6ES7212-1AE40-0XB0.jpg");
        m.put("linkedin", "6ES7212-1AE40-0XB0.png");
        m.put("photos", List.of(Map.of("src", "/assets/products/a.jpg", "tag", "Photo")));
        m.put("status", "legacy");
        m.put("reviews_verified", List.of(Map.of("name", "A")));
        m.put("slug", "siemens-s7-1200-6es7212-1ae40-0xb0");
        return m;
    }

    @Test
    void notImported_allErp_emptyValuesDropped() {
        Map<String, Object> d = SiteEntry.build(identity(), erp(), Map.of(), null, null, Set.of());
        assertThat(d).containsEntry("spec", "CPU 1212C (ERP)").containsEntry("overview", "ERP overview")
                .containsEntry("series", "").containsEntry("release", "WP10001").containsEntry("status", "instock");
        assertThat(d).doesNotContainKeys("specs", "linkedin", "sell_price");
    }

    @Test
    void imported_untouchedGroupsKeepSiteContent_identityAlwaysErp() {
        Map<String, LocalDateTime> updated = Map.of("content_en", IMPORTED.minusDays(3), "faq", IMPORTED.minusDays(1));
        Map<String, Object> d = SiteEntry.build(identity(), erp(), updated, original(), IMPORTED, Set.of());
        assertThat(d).containsEntry("spec", "CPU 1212C (site)").containsEntry("overview", "site overview")
                .containsEntry("faq", original().get("faq")).containsEntry("specs", original().get("specs"))
                .containsEntry("photo", "6ES7212-1AE40-0XB0.jpg").containsEntry("status", "legacy");
        // 身份字段始终用 ERP；站点独有字段、linkedin 原样
        assertThat(d).containsEntry("cat", "controllers").containsEntry("slug", "siemens-6es7212-1ae40-0xb0")
                .containsEntry("release", "WP10001").containsEntry("linkedin", "6ES7212-1AE40-0XB0.png")
                .containsEntry("reviews_verified", original().get("reviews_verified"));
    }

    @Test
    void imported_groupUpdatedAfterImport_usesErp_specExample() {
        // 11 月 26 日导入，28 日重新写入英文营销内容：英文 SEO 描述、首屏定义块用 ERP；规格、FAQ 仍用站点
        Map<String, LocalDateTime> updated = Map.of("content_en", IMPORTED.plusDays(2), "media", IMPORTED.plusHours(1));
        Map<String, Object> d = SiteEntry.build(identity(), erp(), updated, original(), IMPORTED, Set.of());
        assertThat(d).containsEntry("overview", "ERP overview").containsEntry("meta_description", "ERP meta")
                .containsEntry("spec", "CPU 1212C (site)").containsEntry("faq", original().get("faq"));
        assertThat(d).containsEntry("photo", "https://oss/x.jpg").containsEntry("photos", erp().get("photos"))
                .doesNotContainKey("videos");
    }

    @Test
    void imported_useErpButton_andErpEmptyRemovesField() {
        Map<String, Object> d = SiteEntry.build(identity(), erp(), Map.of(), original(), IMPORTED, Set.of("specs", "status"));
        assertThat(d).doesNotContainKey("specs").containsEntry("status", "instock")
                .containsEntry("overview", "site overview");
        assertThat(SiteEntry.useErp("faq", Map.of(), original(), IMPORTED, List.of("faq"))).isTrue();
        assertThat(SiteEntry.useErp("faq", Map.of("faq", IMPORTED), original(), IMPORTED, List.of())).isFalse();
        assertThat(SiteEntry.groupOf("meta_description_ru")).isEqualTo("content_ru");
        assertThat(SiteEntry.groupOf("linkedin")).isNull();
        assertThat(SiteEntry.groupOf("brand")).isNull();
    }
}

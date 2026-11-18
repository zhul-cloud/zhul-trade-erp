package com.zhul.erp.modules.masterdata.support;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** openspec brand-unify：待确认品牌的建议只做提示 */
class BrandSuggestTest {

    private static final List<BrandSuggest.Entry> ENTRIES = List.of(
            new BrandSuggest.Entry(1L, "Allen-Bradley", "Allen-Bradley", false),
            new BrandSuggest.Entry(1L, "Allen-Bradley", "Rockwell", true),
            new BrandSuggest.Entry(2L, "Beckhoff", "Beckhoff", false),
            new BrandSuggest.Entry(3L, "Mitsubishi Electric", "Mitsubishi", true),
            new BrandSuggest.Entry(4L, "ABB", "ABB", false),
            new BrandSuggest.Entry(5L, "LS Electric", "LS", true));

    private static BrandSuggest.Suggestion of(String text) {
        Map<String, BrandSuggest.Entry> byKey = new HashMap<>();
        ENTRIES.forEach(e -> byKey.putIfAbsent(e.text().trim().toLowerCase(Locale.ROOT), e));
        return BrandSuggest.of(text, byKey, ENTRIES);
    }

    @Test
    void placeholderWordsAreNotBrands() {
        assertThat(of(" 未知品牌 ").kind()).isEqualTo(BrandSuggest.KIND_NOT_BRAND);
        assertThat(of("N/A").kind()).isEqualTo(BrandSuggest.KIND_NOT_BRAND);
    }

    @Test
    void severalKnownBrandsAreNotOneBrand() {
        BrandSuggest.Suggestion s = of("Mitsubishi/LS/ABB");
        assertThat(s.kind()).isEqualTo(BrandSuggest.KIND_NOT_BRAND);
        assertThat(s.text()).contains("多个品牌");
    }

    @Test
    void similarBySymbolsContainmentAndSpelling() {
        assertThat(of("Allen Bradley").brandId()).isEqualTo(1L);
        assertThat(of("Allen Bradley").text()).isEqualTo("相近：Allen-Bradley（写法接近）");
        assertThat(of("Rockwell Automation").text()).isEqualTo("相近：Allen-Bradley（已有别名 Rockwell）");
        assertThat(of("Beckhof").brandId()).isEqualTo(2L);
        assertThat(of("Beckhof").kind()).isEqualTo(BrandSuggest.KIND_SIMILAR);
    }

    @Test
    void unrelatedOrShortWordsGetNoSuggestion() {
        assertThat(of("维控 wecon")).isNull();
        assertThat(of("AB")).isNull();
        assertThat(of("")).isNull();
    }
}

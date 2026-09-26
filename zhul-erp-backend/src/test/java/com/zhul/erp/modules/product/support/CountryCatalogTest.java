package com.zhul.erp.modules.product.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.modules.product.dto.CountryVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CountryCatalogTest {

    private CountryCatalog catalog;

    @BeforeEach
    void setUp() throws Exception {
        catalog = new CountryCatalog(new ObjectMapper());
        catalog.load();
    }

    @Test
    void catalogHasEveryEntryWithCodeAndBothNames() {
        List<CountryVO> all = catalog.all();
        assertEquals(249, all.size());
        Set<String> codes = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (CountryVO c : all) {
            assertTrue(c.getCode().matches("[A-Z]{2}"), c.getCode());
            assertFalse(c.getNameEn().isBlank());
            assertFalse(c.getNameZh().isBlank());
            assertTrue(codes.add(c.getCode()), "重复的代码 " + c.getCode());
            assertTrue(names.add(c.getNameEn().toLowerCase()), "重复的英文名 " + c.getNameEn());
        }
    }

    @Test
    void canonicalNameIgnoresCaseAndSurroundingSpaces() {
        assertEquals("Germany", catalog.canonicalName("Germany"));
        assertEquals("Germany", catalog.canonicalName("  germany "));
        assertEquals("Japan", catalog.canonicalName("JAPAN"));
    }

    @Test
    void existingSpellingsInDevelopmentDataAreInCatalog() {
        // 开发库里现有品牌的原产地（USA 已由 schema_v1.2.1.sql 订正为 United States）
        for (String name : new String[]{"Germany", "France", "Japan", "United States", "Switzerland",
                "Taiwan, China", "Denmark"}) {
            assertNotNull(catalog.canonicalName(name), name);
        }
    }

    @Test
    void regionsUseChinaSuffixSpelling() {
        assertEquals("Hong Kong, China", catalog.canonicalName("hong kong, china"));
        assertEquals("Macao, China", catalog.canonicalName("Macao, China"));
    }

    @Test
    void namesOutsideTheCatalogAreRejected() {
        assertNull(catalog.canonicalName("Deutschland1"));
        assertNull(catalog.canonicalName("德国"));
        assertNull(catalog.canonicalName("USA"));
        assertNull(catalog.canonicalName(""));
        assertNull(catalog.canonicalName(null));
    }
}

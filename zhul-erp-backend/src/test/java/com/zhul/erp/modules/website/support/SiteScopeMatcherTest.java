package com.zhul.erp.modules.website.support;

import com.zhul.erp.modules.website.dto.SiteScope;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** spec website-site 商品范围：品牌、品类（含下级）筛选，空表示不限，两项都设时都要满足 */
class SiteScopeMatcherTest {

    private static final long OMRON = 1L;
    private static final long SIEMENS = 2L;
    private static final long SENSORS = 10L;
    private static final long PROXIMITY = 11L;
    private static final long DRIVES = 20L;

    @Test
    void emptyScope_matchesEverything() {
        assertTrue(SiteScopeMatcher.matches(null, SIEMENS, DRIVES, null));
        assertTrue(SiteScopeMatcher.matches(new SiteScope(List.of(), List.of()), null, null, null));
    }

    @Test
    void brandOnly() {
        SiteScope s = new SiteScope(List.of(OMRON), List.of());
        assertTrue(SiteScopeMatcher.matches(s, OMRON, DRIVES, null));
        assertFalse(SiteScopeMatcher.matches(s, SIEMENS, DRIVES, null));
        assertFalse(SiteScopeMatcher.matches(s, null, DRIVES, null));
    }

    @Test
    void categoryIncludesChildren() {
        SiteScope s = new SiteScope(List.of(), List.of(SENSORS));
        assertTrue(SiteScopeMatcher.matches(s, SIEMENS, SENSORS, null));
        assertTrue(SiteScopeMatcher.matches(s, SIEMENS, PROXIMITY, SENSORS), "下级品类");
        assertFalse(SiteScopeMatcher.matches(s, SIEMENS, DRIVES, null));
        assertFalse(SiteScopeMatcher.matches(s, SIEMENS, null, null));
    }

    @Test
    void brandAndCategory_bothRequired() {
        SiteScope s = new SiteScope(List.of(OMRON), List.of(SENSORS));
        assertTrue(SiteScopeMatcher.matches(s, OMRON, PROXIMITY, SENSORS));
        assertFalse(SiteScopeMatcher.matches(s, OMRON, DRIVES, null));
        assertFalse(SiteScopeMatcher.matches(s, SIEMENS, SENSORS, null));
    }
}

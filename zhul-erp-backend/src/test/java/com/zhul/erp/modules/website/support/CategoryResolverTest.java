package com.zhul.erp.modules.website.support;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** spec website-site「品类对照」：二级有对照用二级，没有用一级，都没有为 null */
class CategoryResolverTest {

    private static final long SENSORS = 10L;
    private static final long PROXIMITY = 11L;
    private static final long PHOTO = 12L;
    private static final long DRIVES = 20L;
    private static final long INVERTER = 21L;

    private final Map<Long, String> map = Map.of(SENSORS, "sensors", PHOTO, "photo-sensors", INVERTER, "");

    @Test
    void secondLevelWithoutMapping_fallsBackToFirstLevel() {
        assertEquals("sensors", CategoryResolver.resolve(map, PROXIMITY, SENSORS));
    }

    @Test
    void secondLevelMapping_winsOverFirstLevel() {
        assertEquals("photo-sensors", CategoryResolver.resolve(map, PHOTO, SENSORS));
    }

    @Test
    void firstLevelItself() {
        assertEquals("sensors", CategoryResolver.resolve(map, SENSORS, null));
    }

    @Test
    void noMappingAnywhere_isNull_blankCountsAsMissing() {
        assertNull(CategoryResolver.resolve(map, INVERTER, DRIVES), "二级为空串、一级没设置");
        assertNull(CategoryResolver.resolve(map, DRIVES, null));
        assertNull(CategoryResolver.resolve(map, null, null));
    }
}

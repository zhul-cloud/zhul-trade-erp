package com.zhul.erp.modules.product.support;

import com.zhul.erp.modules.product.entity.ProductCategoryDO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 品类匹配规则（spec product/product-candidate「商品候选池」） */
class CategoryMatcherTest {

    private static ProductCategoryDO cat(long id, String zh, String en, String code, Long parent) {
        ProductCategoryDO c = new ProductCategoryDO();
        c.setId(id);
        c.setCategoryNameZh(zh);
        c.setCategoryName(en);
        c.setCategoryCode(code);
        c.setParentId(parent);
        return c;
    }

    private static final List<ProductCategoryDO> CATS = List.of(
            cat(5, "伺服与运动", "Servo & Motion", "servo", null),
            cat(51, "伺服驱动器", "Servo Drives", "servo_drive", 5L),
            cat(52, "伺服电机", "Servo Motors", "servo_motor", 5L),
            cat(11, "继电器与信号接口", "Relays & Signal Interfaces", "relays", null),
            cat(111, "中间继电器", "General Purpose Relays", "relay_general", 11L),
            cat(112, "固态继电器", "Solid State Relays", "relay_ssr", 11L),
            cat(10, "低压电器", "Low Voltage Switchgear", "switchgear", null),
            cat(101, "热过载继电器", "Thermal Overload Relays", "thermal_overload", 10L),
            cat(102, "附件", "Switchgear Accessories", "switchgear_accessory", 10L),
            cat(20, "变频器与驱动", "Inverters & Drives", "inverter", null),
            cat(201, "通用变频器", "General Purpose Inverters", "inverter_general", 20L),
            cat(30, "传感器", "Sensors", "sensors", null),
            cat(301, "光电传感器", "Photoelectric Sensors", "photoelectric", 30L));

    private static Long id(String text) {
        return CategoryMatcher.match(text, CATS).categoryId();
    }

    @Test
    void exactSubCategory() {
        assertEquals(51L, id("伺服驱动器"));
        assertEquals(52L, id("servo motors"), "英文名忽略大小写");
        assertEquals(51L, id(" servo_drive "), "编码，忽略首尾空格");
    }

    @Test
    void exactTopCategoryStaysTop() {
        assertEquals(5L, id("伺服与运动"));
    }

    @Test
    void topContainsThenRefine() {
        // 一级「传感器」被包含，其下只有「光电传感器」包含「光电传感器」→ 细化
        assertEquals(301L, id("欧姆龙光电传感器"));
        // 一级「变频器与驱动」包含「变频器」，其下只有「通用变频器」包含它 → 细化
        assertEquals(201L, id("变频器"));
    }

    @Test
    void multipleSubsUnderOneTopFallBackToTop() {
        assertEquals(11L, id("继电器"), "一级包含，其下中间、固态继电器都含它");
    }

    @Test
    void subContainsRound() {
        assertEquals(51L, id("AC伺服驱动器"), "细分名被包含");
        CategoryMatcher.Result r = CategoryMatcher.match("伺服", CATS);
        assertEquals(5L, r.categoryId(), "一级名包含「伺服」，其下两个细分都含它 → 一级");
    }

    @Test
    void multipleTopsAmbiguous() {
        CategoryMatcher.Result r = CategoryMatcher.match("伺服电机 通用变频器", CATS);
        assertNull(r.categoryId());
        assertTrue(r.ambiguous());
    }

    @Test
    void shortSubNameNotContainedReversely() {
        CategoryMatcher.Result r = CategoryMatcher.match("仪表附件", CATS);
        assertNull(r.categoryId(), "「附件」不足 3 个字，不因文字含有它而命中");
        assertFalse(r.ambiguous());
        assertEquals(102L, id("附件"), "精确仍可命中");
    }

    @Test
    void emptyOrNoHit() {
        for (String t : new String[] {null, "", "  ", "轴承"}) {
            CategoryMatcher.Result r = CategoryMatcher.match(t, CATS);
            assertNull(r.categoryId(), String.valueOf(t));
            assertFalse(r.ambiguous(), String.valueOf(t));
        }
    }

    @Test
    void rootOf() {
        assertEquals(5L, CategoryMatcher.rootOf(51L, CATS));
        assertEquals(5L, CategoryMatcher.rootOf(5L, CATS));
        assertNull(CategoryMatcher.rootOf(999L, CATS));
        assertNull(CategoryMatcher.rootOf(null, CATS));
    }
}

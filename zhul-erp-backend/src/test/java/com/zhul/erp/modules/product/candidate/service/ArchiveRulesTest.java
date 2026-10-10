package com.zhul.erp.modules.product.candidate.service;

import com.zhul.erp.modules.product.candidate.support.ModelRules;
import com.zhul.erp.modules.product.entity.ProductCategoryDO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 「像不像型号」与品类建议规则（spec inquiry/inquiry-intake「确认询盘时自动建档」、product/product-candidate） */
class ArchiveRulesTest {

    @Test
    void looksLikeModel() {
        for (String m : new String[] {"LXM32AD30N4", "6ES7 214-1AG40-0XB0", "6ES7 214 1AG40 0XB0", "AX32-30-10-80",
                "FA87B CM112M/BR/HR/TF/VR/AK1H/SB50", "RF147 DRE225M4/TH", "TA25DU-8.5M"}) {
            assertTrue(ModelRules.looksLikeModel(m), m);
        }
        for (String m : new String[] {"Contactor, 32A", "Thermal overload relay 9-13A", "接触器 32A", "LXM", "", "  ",
                "Thermal overload relay, 12A-18A"}) {
            assertFalse(ModelRules.looksLikeModel(m), m);
        }
    }

    private static ProductCategoryDO cat(long id, String zh, String en, String code, Long parent) {
        ProductCategoryDO c = new ProductCategoryDO();
        c.setId(id);
        c.setCategoryNameZh(zh);
        c.setCategoryName(en);
        c.setCategoryCode(code);
        c.setParentId(parent);
        return c;
    }

    @Test
    void suggestCategory() {
        List<ProductCategoryDO> cats = List.of(cat(1, "可编程控制器", "PLC", "plc", null), cat(5, "伺服", "AC Servo", "servo", null),
                cat(51, "伺服驱动器", "Servo Drive", "servo-drive", 5L), cat(9, "接触器", "Contactor", "contactor", null),
                cat(10, "变频器", "AC Inverter", "inverter", null));
        assertEquals(1L, ArchiveSuggest.of("PLC", cats), "精确匹配英文名");
        assertEquals(5L, ArchiveSuggest.of("伺服驱动器", cats), "命中二级品类取一级");
        assertEquals(9L, ArchiveSuggest.of("交流接触器", cats), "包含匹配");
        assertNull(ArchiveSuggest.of("编码器", cats), "没有兜底品类时匹配不到就不建议");
        assertNull(ArchiveSuggest.of("", cats));

        List<ProductCategoryDO> withOther = new java.util.ArrayList<>(cats);
        withOther.add(cat(17, "其他", "Others", "other", null));
        assertEquals(17L, ArchiveSuggest.of("编码器", withOther), "匹配不到归入其他");
        assertEquals(17L, ArchiveSuggest.of("", withOther), "没写品类归入其他");
        assertEquals(17L, ArchiveSuggest.of(null, withOther));
        assertEquals(9L, ArchiveSuggest.of("交流接触器", withOther), "命中的不受影响");
        assertNull(ArchiveSuggest.of("伺服变频器", withOther), "同时命中多个一级品类仍留空");

        // 一级品类名包含优先于二级品类名：「继电器」不因多个上级下都有「xx继电器」而留空
        List<ProductCategoryDO> relays = List.of(cat(11, "继电器与信号接口", "Relays & Signal Interfaces", "relays", null),
                cat(111, "安全继电器", "Safety Relays", "safety_relay", 11L), cat(10, "低压电器", "Low Voltage Switchgear", "switchgear", null),
                cat(101, "热过载继电器", "Thermal Overload Relays", "thermal_overload", 10L),
                cat(102, "附件", "Switchgear Accessories", "switchgear_accessory", 10L), cat(17, "其他", "Others", "other", null));
        assertEquals(11L, ArchiveSuggest.of("继电器", relays), "一级品类名包含");
        assertEquals(10L, ArchiveSuggest.of("热过载继电器", relays), "精确命中二级");
        assertEquals(17L, ArchiveSuggest.of("仪表附件", relays), "短名「附件」不做反向包含，归入其他");
        assertEquals(10L, ArchiveSuggest.of("附件", relays), "短名仍可精确匹配");
    }

    /** 包内访问 ProductArchiver 的静态方法 */
    private static final class ArchiveSuggest {
        static Long of(String text, List<ProductCategoryDO> cats) {
            return ProductArchiver.suggestCategory(text, cats);
        }
    }
}

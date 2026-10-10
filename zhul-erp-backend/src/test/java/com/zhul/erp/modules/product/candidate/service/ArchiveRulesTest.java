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
        assertNull(ArchiveSuggest.of("编码器", cats), "匹配不到");
        assertNull(ArchiveSuggest.of("", cats));
    }

    /** 包内访问 ProductArchiver 的静态方法 */
    private static final class ArchiveSuggest {
        static Long of(String text, List<ProductCategoryDO> cats) {
            return ProductArchiver.suggestCategory(text, cats);
        }
    }
}

package com.zhul.erp.modules.finance;

import com.zhul.erp.modules.finance.support.VoucherRules;
import com.zhul.erp.modules.inventory.constants.InvConstants;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 业务凭证规则（openspec inventory-vouchers task 3.1） */
class VoucherRulesTest {

    /** 库存流水的每个业务类型都要归类：生成凭证、不生成凭证（核对里单列）、不计入，且只归一类 */
    @Test
    void 每个库存业务类型都归了类() throws IllegalAccessException {
        List<String> missing = new ArrayList<>();
        for (Field f : InvConstants.class.getFields()) {
            if (!f.getName().startsWith("BIZ_") || f.getType() != String.class || !Modifier.isStatic(f.getModifiers())) {
                continue;
            }
            String biz = (String) f.get(null);
            int hits = (VoucherRules.ACTIONS.containsKey(biz) ? 1 : 0) + (VoucherRules.NO_VOUCHER.containsKey(biz) ? 1 : 0)
                    + (VoucherRules.NOT_COUNTED.contains(biz) ? 1 : 0);
            if (hits != 1) {
                missing.add(f.getName() + "=" + hits);
            }
        }
        assertTrue(missing.isEmpty(), "业务类型要在 VoucherRules 里归且只归一类：" + missing);
    }

    @Test
    void 业务项都有默认科目_键不重复() {
        Set<String> keys = new HashSet<>();
        for (VoucherRules.Item i : VoucherRules.ITEMS) {
            assertTrue(keys.add(i.key()), "重复的业务项 " + i.key());
            assertTrue(!i.defaultCode().isEmpty() && !i.defaultName().isEmpty(), i.key());
        }
        assertEquals(4 + 4 + 8, VoucherRules.ITEMS.size());
        assertEquals("1405", VoucherRules.item(VoucherRules.STOCK).defaultCode());
        assertEquals("6711", VoucherRules.item(VoucherRules.otherOut(InvConstants.OO_SCRAP)).defaultCode());
        assertEquals("6601", VoucherRules.item(VoucherRules.otherOut(InvConstants.OO_SAMPLE)).defaultCode());
        assertEquals("1221", VoucherRules.item(VoucherRules.otherOut(InvConstants.OO_LOST)).defaultCode());
        assertEquals("其他出库 · 报废", VoucherRules.itemLabel(VoucherRules.otherOut(InvConstants.OO_SCRAP)));
        assertEquals("成本调整对方 · 外币付款", VoucherRules.itemLabel(VoucherRules.adjOffset(InvConstants.CA_PAYMENT)));
        for (VoucherRules.Action a : VoucherRules.ACTIONS.values()) {
            assertTrue(a.counter() == null || VoucherRules.item(a.counter()) != null, a.action());
            assertNotNull(VoucherRules.CATEGORY_NAMES.get(a.category()));
        }
    }
}

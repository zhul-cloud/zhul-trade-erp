package com.zhul.erp.modules.logistics.service;

import com.zhul.erp.modules.sales.constants.SalesConstants;
import com.zhul.erp.modules.sales.entity.SalesOrderDO;
import com.zhul.erp.modules.sales.entity.SalesOrderItemDO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** spec logistics/shipping-documents「补发订单的申报货值」 */
class DocGroupReplacementTest {

    private static SalesOrderItemDO item(String unit, String declared, String descEn) {
        SalesOrderItemDO i = new SalesOrderItemDO();
        i.setUnitPrice(unit == null ? null : new BigDecimal(unit));
        i.setDeclaredPrice(declared == null ? null : new BigDecimal(declared));
        i.setDescriptionEn(descEn);
        i.setDescription("");
        return i;
    }

    private static SalesOrderDO order(int source) {
        SalesOrderDO o = new SalesOrderDO();
        o.setSource(source);
        return o;
    }

    @Test
    void replacementLineUsesDeclaredPriceAndNote() {
        assertEquals(new BigDecimal("441.10"), DocGroupService.priceOf(item("0.00", "441.10", "Servo motor")));
        assertEquals(new BigDecimal("50.00"), DocGroupService.priceOf(item("50.00", null, "Servo motor")));
        assertEquals(BigDecimal.ZERO, DocGroupService.priceOf(item(null, null, "")));
        assertEquals("Servo motor (" + SalesConstants.REPLACEMENT_DOC_NOTE + ")", DocGroupService.describe(item("0", "441.10", "Servo motor")));
        assertEquals(SalesConstants.REPLACEMENT_DOC_NOTE, DocGroupService.describe(item("0", "15", "")));
        assertEquals("Servo motor", DocGroupService.describe(item("50", null, "Servo motor")));
    }

    @Test
    void docNoteOnlyWhenGroupHasReplacement() {
        assertEquals("", DocGroupService.replacementNote(List.of(order(SalesConstants.SO_FROM_PI), order(SalesConstants.SO_MANUAL))));
        assertEquals(SalesConstants.REPLACEMENT_DOC_NOTE,
                DocGroupService.replacementNote(List.of(order(SalesConstants.SO_FROM_PI), order(SalesConstants.SO_REPLACEMENT))));
    }
}

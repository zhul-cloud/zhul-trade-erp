package com.zhul.erp.modules.inventory;

import com.zhul.erp.modules.inventory.service.InventoryPosting.Doc;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** BUS-092：流水业务说明超过字段宽度时截断，不让过账失败 */
class InventoryDocLabelTest {

    @Test
    void label_cutToColumnWidth() {
        Doc doc = Doc.of("MERGE", "PRODUCT", 1L, "");
        assertEquals("", doc.label(null).bizLabel());
        assertEquals("期初", doc.label("期初").bizLabel());
        assertEquals(128, doc.label("x".repeat(128)).bizLabel().length());
        assertEquals(128, doc.label("型".repeat(200)).bizLabel().length());
    }
}

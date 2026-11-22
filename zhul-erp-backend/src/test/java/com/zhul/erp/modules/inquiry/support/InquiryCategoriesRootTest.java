package com.zhul.erp.modules.inquiry.support;

import com.zhul.erp.modules.product.entity.ProductCategoryDO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 无货分析与询价记录品类筛选共用的一级品类规则 */
class InquiryCategoriesRootTest {

    private static ProductCategoryDO c(long id, Long parent, String zh, String en) {
        ProductCategoryDO c = new ProductCategoryDO();
        c.setId(id);
        c.setParentId(parent);
        c.setCategoryNameZh(zh);
        c.setCategoryName(en);
        return c;
    }

    private final List<ProductCategoryDO> all = List.of(c(1, null, "伺服系统", "Servo"), c(11, 1L, "伺服电机", "Servo Motor"));

    @Test
    void idFirst_thenExactNameIgnoringCase_elseNull() {
        assertEquals(1L, InquiryCategories.rootOfItem(11L, "随便写", all));
        assertEquals(1L, InquiryCategories.rootOfItem(null, " 伺服电机 ", all));
        assertEquals(1L, InquiryCategories.rootOfItem(null, "servo motor", all));
        assertNull(InquiryCategories.rootOfItem(null, "伺服", all));
        assertNull(InquiryCategories.rootOfItem(null, "", all));
        assertEquals(2, InquiryCategories.under(1L, all).size());
    }
}

package com.zhul.erp.modules.product.candidate.service;

import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 单据明细关联商品的历史回填在后台线程里跑，线程里没有租户上下文：入口自己进入平台范围，
 * 按型号回填各公司的明细（spec product/document-product-link「历史单据回填与待关联清单」）。
 */
class DocProductBackfillTenantContractTest extends IntegrationTestBase {

    private static final int TENANT = 99000095;

    @Autowired
    private CandidateBackfill backfill;

    private long product;

    @BeforeEach
    void data() {
        cleanup();
        jdbc.update("insert into product_brand (tenant_id, brand_name, status) values (0, 'DPBTBrand', 1)");
        long brand = jdbc.queryForObject("select id from product_brand where brand_name = 'DPBTBrand'", Long.class);
        jdbc.update("insert into product (tenant_id, brand_id, brand_key, mpn_raw, mpn_normalized, mpn_display, product_name, status, archive_status) "
                + "values (0, ?, ?, 'DPBT-100', 'dpbt100', 'DPBT-100', 'x', 1, 2)", brand, "#" + brand);
        product = jdbc.queryForObject("select id from product where mpn_normalized = 'dpbt100'", Long.class);
        jdbc.update("insert into purchase_order_item (tenant_id, po_id, model, brand, description, quantity, unit_price, amount, sort_order) "
                + "values (?, 0, 'dpbt 100', 'DPBTBrand', '', 1, 1, 1, 1)", TENANT);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("delete from purchase_order_item where tenant_id = ?", TENANT);
        jdbc.update("delete from product where mpn_normalized = 'dpbt100'");
        jdbc.update("delete from product_brand where brand_name = 'DPBTBrand'");
    }

    @Test
    void backfillThreadWithoutTenantContext_fillsOtherTenantLines() throws Exception {
        AtomicReference<Throwable> failed = new AtomicReference<>();
        Thread t = new Thread(() -> {
            try {
                assertNull(TenantContext.getTenantId());
                backfill.backfillDocuments();
            } catch (Throwable e) {
                failed.set(e);
            }
        });
        t.start();
        t.join();
        assertNull(failed.get());
        assertEquals(product, jdbc.queryForObject("select product_id from purchase_order_item where tenant_id = ?", Long.class, TENANT));
    }
}

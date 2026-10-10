package com.zhul.erp.modules.product.candidate;

import com.zhul.erp.modules.product.candidate.service.ProductArchiver;
import com.zhul.erp.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** spec product/product-candidate「商品候选池」：匹配不到的待审核候选补齐建议品类「其他」，同时命中多个的留空，其他状态不动 */
class OtherCategorySuggestIntegrationTest extends IntegrationTestBase {

    @Autowired
    private ProductArchiver archiver;

    private long other;
    private long plc;

    @BeforeEach
    void seed() {
        cleanup();
        other = category("other", "Others", "其他");
        plc = category("ito_plc", "ITO Alpha", "仝甲控制器");
        category("ito_servo", "ITO Beta", "仝乙驱动");
    }

    @AfterEach
    void cleanup() {
        jdbc.update("delete from product_candidate where mpn_raw like 'ITO-%'");
        jdbc.update("delete from product_category where category_code in ('ito_plc', 'ito_servo')");
        jdbc.update("update product_category set deleted_at = now() where tenant_id = 0 and category_code = 'other' and create_by = 'ito'");
    }

    private long category(String code, String en, String zh) {
        jdbc.update("insert into product_category (tenant_id, category_code, category_name, category_name_zh, status, create_by) "
                + "values (0, ?, ?, ?, 1, 'ito') on duplicate key update status = 1, deleted_at = null, parent_id = null, "
                + "category_name = values(category_name), category_name_zh = values(category_name_zh)", code, en, zh);
        return jdbc.queryForObject("select id from product_category where tenant_id = 0 and category_code = ?", Long.class, code);
    }

    private long candidate(String mpn, String categoryText, int status, Long categoryId) {
        jdbc.update("insert into product_candidate (tenant_id, brand_key, brand_text, mpn_raw, mpn_normalized, category_id, category_text, "
                + "status, level, source_count) values (0, 'ito', 'ITO', ?, ?, ?, ?, ?, 1, 0)",
                mpn, mpn.toLowerCase().replace("-", ""), categoryId, categoryText, status);
        return jdbc.queryForObject("select id from product_candidate where mpn_raw = ?", Long.class, mpn);
    }

    private Long categoryOf(long id) {
        return jdbc.queryForObject("select category_id from product_candidate where id = ?", Long.class, id);
    }

    @Test
    void resuggestPendingWithoutCategory() {
        long bearing = candidate("ITO-1", "轴承", 1, null);
        long blank = candidate("ITO-2", "", 1, null);
        long ambiguous = candidate("ITO-3", "仝", 1, null);
        long matched = candidate("ITO-4", "仝甲控制器", 1, null);
        long chosen = candidate("ITO-5", "轴承", 1, plc);
        long rejected = candidate("ITO-6", "轴承", 4, null);

        archiver.resuggestCategories();

        assertEquals(other, categoryOf(bearing), "匹配不到归入其他");
        assertEquals(other, categoryOf(blank), "没写品类归入其他");
        assertNull(categoryOf(ambiguous), "同时命中多个仍留空");
        assertEquals(plc, categoryOf(matched));
        assertEquals(plc, categoryOf(chosen), "已有品类的不动");
        assertNull(categoryOf(rejected), "不是待审核的不动");
    }
}

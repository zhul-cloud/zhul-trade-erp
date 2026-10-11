package com.zhul.erp.modules.product.candidate;

import com.zhul.erp.modules.product.candidate.service.ProductArchiver;
import com.zhul.erp.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** spec product/product-candidate「商品候选池」：启动时把建议为一级品类的待审核候选细化到细分品类，其他状态不动 */
class SubCategoryRefineIntegrationTest extends IntegrationTestBase {

    @Autowired
    private ProductArchiver archiver;

    private long top;
    private long drive;

    @BeforeEach
    void seed() {
        cleanup();
        top = category("itr_motion", "ITR Motion", "仝丙运动", null);
        drive = category("itr_drive", "ITR Drives", "仝丙驱动器", top);
        category("itr_motor", "ITR Motors", "仝丙电机", top);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("delete from product_candidate where mpn_raw like 'ITR-%'");
        jdbc.update("delete from product_category where category_code in ('itr_drive', 'itr_motor')");
        jdbc.update("delete from product_category where category_code = 'itr_motion'");
    }

    private long category(String code, String en, String zh, Long parentId) {
        jdbc.update("insert into product_category (tenant_id, category_code, category_name, category_name_zh, parent_id, status, create_by) "
                + "values (0, ?, ?, ?, ?, 1, 'itr')", code, en, zh, parentId);
        return jdbc.queryForObject("select id from product_category where tenant_id = 0 and category_code = ?", Long.class, code);
    }

    private long candidate(String mpn, String categoryText, int status, Long categoryId) {
        jdbc.update("insert into product_candidate (tenant_id, brand_key, brand_text, mpn_raw, mpn_normalized, category_id, category_text, "
                + "status, level, source_count) values (0, 'itr', 'ITR', ?, ?, ?, ?, ?, 1, 0)",
                mpn, mpn.toLowerCase().replace("-", ""), categoryId, categoryText, status);
        return jdbc.queryForObject("select id from product_candidate where mpn_raw = ?", Long.class, mpn);
    }

    private Long categoryOf(long id) {
        return jdbc.queryForObject("select category_id from product_candidate where id = ?", Long.class, id);
    }

    @Test
    void refineTopSuggestionToSubCategory() {
        long refinable = candidate("ITR-1", "仝丙驱动器", 1, top);
        long stillTop = candidate("ITR-2", "仝丙", 1, top);
        long empty = candidate("ITR-3", "仝丙驱动器", 1, null);
        long archived = candidate("ITR-4", "仝丙驱动器", 2, top);
        long rejected = candidate("ITR-5", "仝丙驱动器", 4, top);

        archiver.resuggestCategories();

        assertEquals(drive, categoryOf(refinable), "能确定细分品类的细化");
        assertEquals(top, categoryOf(stillTop), "其下两个细分都含它，仍是一级");
        assertEquals(drive, categoryOf(empty), "没有品类的直接补细分");
        assertEquals(top, categoryOf(archived), "已建档不动");
        assertEquals(top, categoryOf(rejected), "已驳回不动");
    }
}

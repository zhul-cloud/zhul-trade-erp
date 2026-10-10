package com.zhul.erp.modules.product;

import com.zhul.erp.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** spec product/category「初始品类」：16 个一级 + 兜底「其他」、140 个二级，中英文；旧编码保留 ID；旧品类引用迁移；可重复执行 */
class ProductCategorySeedIntegrationTest extends IntegrationTestBase {

    private static final List<String> SCRIPTS = List.of("db/migration/V1.2.45__seed_product_categories.sql",
            "db/migration/V1.2.46__other_category.sql");

    @Autowired
    private DataSource dataSource;

    @AfterEach
    void cleanup() {
        jdbc.update("delete from supplier_product_scope where supplier_id = 99000777");
        jdbc.update("delete from product where mpn_raw like 'ITSEED-%'");
        jdbc.update("delete from product_brand where brand_name = 'ITSeedBrand'");
        jdbc.update("delete from product_category where category_code in ('hydraulics', 'it_old_bearing', 'it_old_seal', 'it_old_root')");
    }

    private void runScript() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            for (String script : SCRIPTS) {
                ScriptUtils.executeSqlScript(c, new ClassPathResource(script));
            }
        }
    }

    private Map<String, Object> category(String code) {
        return jdbc.queryForMap("select * from product_category where tenant_id = 0 and category_code = ?", code);
    }

    private long id(String code) {
        return ((Number) category(code).get("id")).longValue();
    }

    private void assertSeeded() {
        assertEquals(17, jdbc.queryForObject("select count(*) from product_category where tenant_id = 0 and parent_id is null "
                + "and deleted_at is null and status = 1", Integer.class));
        assertEquals(140, jdbc.queryForObject("select count(*) from product_category c join product_category p on p.id = c.parent_id "
                + "where c.tenant_id = 0 and c.deleted_at is null and c.status = 1 and p.deleted_at is null and p.status = 1", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from product_category where tenant_id = 0 and deleted_at is null and status = 1 "
                + "and (category_name = '' or category_name_zh = '')", Integer.class), "中英文名都写入");
        Map<String, Object> plc = category("plc");
        assertEquals("PLC & Controllers", plc.get("category_name"));
        assertEquals("PLC 与控制器", plc.get("category_name_zh"));
        assertEquals("电源与变压器", category("power_supplies").get("category_name_zh"));
        Map<String, Object> cpu = category("plc_cpu");
        assertEquals("PLC CPU/一体机", cpu.get("category_name_zh"));
        assertEquals("PLC CPUs & Compact PLCs", cpu.get("category_name"));
        assertEquals(id("plc"), ((Number) cpu.get("parent_id")).longValue());
        assertNull(category("industrial_network").get("parent_id"), "工业网络升为一级");
        assertEquals(id("fluid_power"), ((Number) category("hydraulic_valve").get("parent_id")).longValue());
        List<String> order = jdbc.queryForList("select category_code from product_category where tenant_id = 0 and parent_id is null "
                + "and deleted_at is null and status = 1 order by sort_order", String.class);
        assertEquals("plc", order.get(0));
        assertEquals("electronic_components", order.get(15));
        assertEquals("other", order.get(16), "兜底品类「其他」排在最后");
        assertEquals("其他", category("other").get("category_name_zh"));
        assertEquals("Others", category("other").get("category_name"));
    }

    @Test
    void seedThenLegacyRemapAndRerun() throws Exception {
        // 其他测试会清空品类表，这里先按迁移脚本初始化一次
        runScript();
        long plcId = id("plc");
        long cpuId = id("plc_cpu");
        assertSeeded();

        // 模拟其他环境里仍在用的旧品类
        jdbc.update("insert into product_category (tenant_id, category_code, category_name, category_name_zh, status) "
                + "values (0, 'hydraulics', 'Hydraulics', '液压', 1)");
        jdbc.update("insert into product_category (tenant_id, category_code, category_name, category_name_zh, status) "
                + "values (0, 'it_old_root', 'Old Root', '旧一级', 1)");
        long oldRoot = id("it_old_root");
        jdbc.update("insert into product_category (tenant_id, category_code, category_name, category_name_zh, parent_id, status) "
                + "values (0, 'it_old_bearing', 'Bearings', '轴承', ?, 1)", oldRoot);
        jdbc.update("insert into product_category (tenant_id, category_code, category_name, category_name_zh, parent_id, status) "
                + "values (0, 'it_old_seal', 'Seals', '密封件', ?, 1)", oldRoot);
        jdbc.update("insert into product_brand (tenant_id, brand_name, status) values (0, 'ITSeedBrand', 1)");
        long brand = jdbc.queryForObject("select id from product_brand where brand_name = 'ITSeedBrand'", Long.class);
        jdbc.update("insert into product (tenant_id, brand_id, category_id, mpn_raw, mpn_normalized, status) values (0, ?, ?, 'ITSEED-1', 'itseed1', 1)",
                brand, id("hydraulics"));
        jdbc.update("insert into product (tenant_id, brand_id, category_id, mpn_raw, mpn_normalized, status) values (0, ?, ?, 'ITSEED-2', 'itseed2', 1)",
                brand, plcId);
        jdbc.update("insert into supplier_product_scope (tenant_id, supplier_id, brand_id, category_id) values (1000, 99000777, ?, ?)",
                brand, id("it_old_bearing"));
        jdbc.update("insert into supplier_product_scope (tenant_id, supplier_id, brand_id, category_id) values (1000, 99000777, ?, ?)",
                brand, cpuId);

        runScript();

        assertSeeded();
        assertEquals(plcId, id("plc"), "在用编码保留 ID");
        assertEquals(cpuId, id("plc_cpu"));
        assertEquals(id("fluid_power"), jdbc.queryForObject("select category_id from product where mpn_raw = 'ITSEED-1'", Long.class),
                "旧品类上的商品迁到新品类");
        assertEquals(plcId, jdbc.queryForObject("select category_id from product where mpn_raw = 'ITSEED-2'", Long.class));
        assertNotNull(category("hydraulics").get("deleted_at"), "迁完无引用的旧一级软删除");
        assertNotNull(category("it_old_seal").get("deleted_at"), "无引用的旧二级软删除");
        Map<String, Object> bearing = category("it_old_bearing");
        assertNull(bearing.get("deleted_at"), "仍被供应商引用的旧二级保留");
        assertEquals(0, ((Number) bearing.get("status")).intValue(), "并停用");
        Map<String, Object> root = category("it_old_root");
        assertNull(root.get("deleted_at"), "还有未删除下级的旧一级保留");
        assertEquals(0, ((Number) root.get("status")).intValue());
        assertEquals(2, jdbc.queryForObject("select count(*) from supplier_product_scope where supplier_id = 99000777", Integer.class));

        runScript();
        assertSeeded();
        assertEquals(plcId, id("plc"), "再次执行结果一致");
    }
}

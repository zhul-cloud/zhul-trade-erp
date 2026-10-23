package com.zhul.erp.modules.product;

import com.zhul.erp.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * spec product/category「品类携带品类简介」细分品类初始简介，product/series「初始系列」：
 * 补简介、新增主力系列、改名保留引用、挂错品牌的系列删除或停用、已删除的不恢复、清单外不动、可重复执行
 */
class ProductSeriesSeedIntegrationTest extends IntegrationTestBase {

    private static final String BRANDS = "db/migration/V1.2.48__seed_product_brands.sql";
    private static final String SCRIPT = "db/migration/V1.3.9__seed_subcategory_desc_and_series.sql";
    /** 其他测试会清空品类表，这里先写入定稿品类，结束后把品类表恢复原样 */
    private static final List<String> CATEGORIES = List.of("db/migration/V1.2.45__seed_product_categories.sql",
            "db/migration/V1.2.46__other_category.sql", "db/migration/V1.2.50__seed_category_descriptions.sql");

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.update("drop table if exists it_v139_category_snapshot");
        jdbc.update("create table it_v139_category_snapshot as select * from product_category");
        wipe();
        for (String script : CATEGORIES) {
            run(script);
        }
        run(BRANDS);
    }

    @AfterEach
    void tearDown() {
        wipe();
        jdbc.update("delete from product_category");
        jdbc.update("insert into product_category select * from it_v139_category_snapshot");
        jdbc.update("drop table it_v139_category_snapshot");
    }

    private void wipe() {
        for (String t : List.of("product", "product_series", "product_brand_alias", "product_brand")) {
            jdbc.update("delete from " + t);
        }
        jdbc.update("drop table if exists bak_v139_category_desc");
        jdbc.update("drop table if exists bak_v139_product_series");
    }

    private void run(String script) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(c, new ClassPathResource(script));
        }
    }

    private long brand(String name) {
        return jdbc.queryForObject("select id from product_brand where tenant_id = 0 and brand_name = ?", Long.class, name);
    }

    private long series(String brand, String name) {
        jdbc.update("insert into product_series (tenant_id, brand_id, series_name, status) values (0, ?, ?, 1)", brand(brand), name);
        return jdbc.queryForObject("select id from product_series where brand_id = ? and series_name = ?", Long.class,
                brand(brand), name);
    }

    private void product(String brand, long seriesId, String mpn) {
        long category = jdbc.queryForObject("select id from product_category where tenant_id = 0 and category_code = 'plc'", Long.class);
        jdbc.update("insert into product (tenant_id, brand_id, category_id, series_id, mpn_raw, mpn_normalized, status) "
                + "values (0, ?, ?, ?, ?, ?, 1)", brand(brand), category, seriesId, mpn, mpn.toLowerCase().replace("-", ""));
    }

    private Map<String, Object> row(long id) {
        return jdbc.queryForMap("select * from product_series where id = ?", id);
    }

    private List<String> activeSeries(String brand) {
        return jdbc.queryForList("select series_name from product_series where brand_id = ? and deleted_at is null and status = 1",
                String.class, brand(brand));
    }

    private int liveSeriesCount() {
        return jdbc.queryForObject("select count(*) from product_series where tenant_id = 0", Integer.class);
    }

    @Test
    void subCategoryDescriptions() throws Exception {
        jdbc.update("update product_category set description = '', description_zh = '' where tenant_id = 0 and parent_id is not null");
        jdbc.update("update product_category set description_zh = '通用变频器' where tenant_id = 0 and category_code = 'vfd'");

        run(SCRIPT);

        Map<String, Object> soft = jdbc.queryForMap("select * from product_category where tenant_id = 0 and category_code = 'soft_starter'");
        assertEquals("降低电机启动电流与冲击的软启动器。", soft.get("description_zh"));
        assertEquals("Soft starters that reduce motor starting current and mechanical shock.", soft.get("description"));
        Map<String, Object> vfd = jdbc.queryForMap("select * from product_category where tenant_id = 0 and category_code = 'vfd'");
        assertEquals("通用变频器", vfd.get("description_zh"), "已填写的简介不被覆盖");
        assertEquals("General purpose inverters (VFDs) that control AC motor speed.", vfd.get("description"), "空着的英文简介写入");
        assertEquals(0, jdbc.queryForObject("select count(*) from product_category where tenant_id = 0 and parent_id is not null "
                + "and deleted_at is null and (description = '' or description_zh = '')", Integer.class), "140 个细分品类都有中英文简介");
    }

    @Test
    void seriesSeedAndRerun() throws Exception {
        long s71200 = series("Siemens", "S7-1200");
        jdbc.update("update product_series set status = 0 where id = ?", s71200);
        long withDesc = series("Siemens", "LOGO!");
        jdbc.update("update product_series set description = '手工简介' where id = ?", withDesc);
        long atv48 = series("Schneider Electric", "ATV48");
        product("Schneider Electric", atv48, "ITSS-ATS48");
        long deltaUnused = series("Delta", "S7-1200");
        long deletedSy = series("SMC", "SY");
        jdbc.update("update product_series set deleted_at = now() where id = ?", deletedSy);
        long custom = series("SMC", "自定义系列");

        run(SCRIPT);

        Map<String, Object> s7 = row(s71200);
        assertEquals("小型 PLC：SIMATIC S7-1200 CPU、信号模块与通讯模块，TIA 博途编程。", s7.get("description"), "已有系列补简介");
        assertEquals(0, ((Number) s7.get("status")).intValue(), "已有系列的启用状态不变");
        assertEquals("手工简介", row(withDesc).get("description"), "已有简介不覆盖");

        Map<String, Object> ats48 = row(atv48);
        assertEquals("ATS48", ats48.get("series_name"), "写错的系列名在原 ID 上改正");
        assertEquals(atv48, jdbc.queryForObject("select series_id from product where mpn_raw = 'ITSS-ATS48'", Long.class),
                "商品仍属于该系列");

        assertNotNull(row(deltaUnused).get("deleted_at"), "挂错品牌、没有商品使用的系列软删除");

        List<String> smc = activeSeries("SMC");
        assertTrue(smc.containsAll(List.of("CQ2", "CP96", "AC", "ZP", "KQ2", "ITV")), "新增主力系列");
        assertTrue(!smc.contains("SY"), "同名系列曾被删除的不恢复");
        assertEquals(1, jdbc.queryForObject("select count(*) from product_series where brand_id = ? and series_name = 'SY'",
                Integer.class, brand("SMC")), "也不新建");
        assertNull(row(custom).get("deleted_at"), "清单之外的系列不动");
        assertEquals(0, jdbc.queryForObject("select count(*) from product_series where tenant_id = 0 and deleted_at is null "
                + "and series_name <> '自定义系列' and description = ''", Integer.class), "清单内系列都有简介");

        int count = liveSeriesCount();
        run(SCRIPT);
        assertEquals(count, liveSeriesCount(), "重复执行不产生重复系列");
        assertEquals("ATS48", row(atv48).get("series_name"));
    }

    @Test
    void misplacedSeriesInUseIsDisabled() throws Exception {
        long delta = series("Delta", "S7-1200");
        product("Delta", delta, "ITSS-DELTA-1");

        run(SCRIPT);

        Map<String, Object> r = row(delta);
        assertNull(r.get("deleted_at"), "有商品使用时不删除");
        assertEquals(0, ((Number) r.get("status")).intValue(), "改为停用");
        assertTrue(!activeSeries("Delta").contains("S7-1200"));
    }

    @Test
    void rollbackRestoresPreviousState() throws Exception {
        long atv48 = series("Schneider Electric", "ATV48");
        long delta = series("Delta", "S7-1200");
        int before = jdbc.queryForObject("select count(*) from product_series where deleted_at is null", Integer.class);

        run(SCRIPT);
        try (Connection c = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(c, new org.springframework.core.io.FileSystemResource(
                    "../sql/build/rollback/V1.3.9__seed_subcategory_desc_and_series_rollback.sql"));
        }

        assertEquals("ATV48", row(atv48).get("series_name"), "改名改回");
        assertEquals("", row(atv48).get("description"), "简介恢复为空");
        assertNull(row(delta).get("deleted_at"), "删除的系列恢复");
        assertEquals(before, jdbc.queryForObject("select count(*) from product_series where deleted_at is null", Integer.class),
                "新建的系列都软删除");
    }
}

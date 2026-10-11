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

/** spec product/brand「初始品牌与系列」「品牌等级」：83 个品牌、等级、别名、已有品牌保留 ID 改名、Vacon 并入 Danfoss、名单外品牌、可重复执行 */
class ProductBrandSeedIntegrationTest extends IntegrationTestBase {

    private static final String SCRIPT = "db/migration/V1.2.48__seed_product_brands.sql";

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    @AfterEach
    void wipe() {
        for (String t : List.of("product", "product_series", "product_brand_alias", "product_brand")) {
            jdbc.update("delete from " + t);
        }
        jdbc.update("delete from inquiry_item where confirmed_model like 'ITBS-%'");
    }

    private void runScript() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(c, new ClassPathResource(SCRIPT));
        }
    }

    private long brand(String name) {
        jdbc.update("insert into product_brand (tenant_id, brand_name, status) values (0, ?, 1)", name);
        return id(name);
    }

    private long id(String name) {
        return jdbc.queryForObject("select id from product_brand where tenant_id = 0 and brand_name = ?", Long.class, name);
    }

    private Map<String, Object> row(long id) {
        return jdbc.queryForMap("select * from product_brand where id = ?", id);
    }

    private void alias(long brandId, String alias) {
        jdbc.update("insert into product_brand_alias (tenant_id, brand_id, alias, alias_key) values (0, ?, ?, ?)",
                brandId, alias, alias.trim().toLowerCase());
    }

    private void series(long brandId, String name) {
        jdbc.update("insert into product_series (tenant_id, brand_id, series_name, status) values (0, ?, ?, 1)", brandId, name);
    }

    private List<String> aliasesOf(long brandId) {
        return jdbc.queryForList("select alias from product_brand_alias where brand_id = ? order by alias", String.class, brandId);
    }

    @Test
    void seedMergeAndRerun() throws Exception {
        // 一个已有旧数据的环境
        long abb = brand("ABB Group");
        alias(abb, "ABB");
        series(abb, "ACS880");
        long mitsubishi = brand("Mitsubishi");
        alias(mitsubishi, "三菱");
        long omron = brand("Omron");
        long danfoss = brand("Danfoss");
        series(danfoss, "FC");
        long vacon = brand("Vacon");
        alias(vacon, "伟肯");
        series(vacon, "NXP");
        series(vacon, "FC");
        long hitech = brand("ITHitech");
        series(hitech, "PWS");
        long hider = brand("ITHider");

        runScript();

        assertEquals(83, jdbc.queryForObject("select count(*) from product_brand where tenant_id = 0 and deleted_at is null "
                + "and status = 1", Integer.class));
        assertEquals(8, jdbc.queryForObject("select count(*) from product_brand where tenant_id = 0 and deleted_at is null "
                + "and brand_level = 2", Integer.class));
        assertEquals(27, jdbc.queryForObject("select count(*) from product_brand where tenant_id = 0 and deleted_at is null "
                + "and brand_level = 1", Integer.class));
        assertEquals(List.of("ABB", "Allen-Bradley", "Delta", "Mitsubishi Electric", "OMRON", "Schneider Electric", "Siemens", "Yaskawa"),
                jdbc.queryForList("select brand_name from product_brand where tenant_id = 0 and deleted_at is null and brand_level = 2 "
                        + "order by brand_name", String.class));

        Map<String, Object> abbRow = row(abb);
        assertEquals("ABB", abbRow.get("brand_name"), "已有品牌保留 ID 改为正式写法");
        assertEquals("Switzerland", abbRow.get("country"));
        assertEquals("ACS 变频器、AC500 PLC、电机、机器人；B&R 是旗下品牌", abbRow.get("description_zh"));
        assertTrue(((String) abbRow.get("description")).startsWith("Zurich-based"));
        assertTrue(aliasesOf(abb).contains("ABB Group"), "旧名称留作别名");
        assertTrue(aliasesOf(abb).contains("阿西布朗勃法瑞"));
        assertTrue(!aliasesOf(abb).contains("ABB"), "与名称相同的别名去掉");
        assertEquals("Mitsubishi Electric", row(mitsubishi).get("brand_name"));
        assertTrue(aliasesOf(mitsubishi).containsAll(List.of("三菱", "三菱电机", "Mitsubishi")));
        assertEquals("OMRON", row(omron).get("brand_name"));
        assertEquals(1, jdbc.queryForObject("select count(*) from product_series where brand_id = ? and series_name = 'ACS880'",
                Integer.class, abb), "系列仍挂在原品牌下");

        // Vacon 并入 Danfoss：不冲突的系列移过去，冲突的 FC 留下，Vacon 停用保留
        assertEquals(List.of("FC", "NXP"), jdbc.queryForList("select series_name from product_series where brand_id = ? order by series_name",
                String.class, danfoss));
        assertTrue(aliasesOf(danfoss).containsAll(List.of("伟肯", "丹佛斯")));
        assertTrue(!aliasesOf(danfoss).contains("Vacon"), "Vacon 还未删除时名称与别名不重复");
        Map<String, Object> vaconRow = row(vacon);
        assertNull(vaconRow.get("deleted_at"), "还有冲突的系列，停用保留");
        assertEquals(0, ((Number) vaconRow.get("status")).intValue());

        // 名单外品牌
        assertEquals(0, ((Number) row(hitech).get("status")).intValue(), "有引用的停用");
        assertNull(row(hitech).get("deleted_at"));
        assertNotNull(row(hider).get("deleted_at"), "没有引用的软删除");

        // 新品牌带别名
        long ab = id("Allen-Bradley");
        assertTrue(aliasesOf(ab).containsAll(List.of("AB", "罗克韦尔", "Rockwell")));
        assertEquals("United States", row(ab).get("country"));

        // 冲突解除后再执行：Vacon 全部移走，软删除
        jdbc.update("delete from product_series where brand_id = ? and series_name = 'FC'", vacon);
        runScript();
        assertNotNull(row(vacon).get("deleted_at"));
        assertTrue(aliasesOf(danfoss).contains("Vacon"), "Vacon 删除后成为 Danfoss 的别名");
        assertEquals(2, jdbc.queryForObject("select count(*) from product_series where brand_id = ?", Integer.class, danfoss));
        assertEquals(abb, id("ABB"), "再次执行结果一致");
        assertEquals(83, jdbc.queryForObject("select count(*) from product_brand where tenant_id = 0 and deleted_at is null "
                + "and status = 1", Integer.class));
    }
}

package com.zhul.erp.modules.product.candidate;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.common.constants.RedisKeyConstants;
import com.zhul.erp.modules.inquiry.InquiryContractSupport;
import com.zhul.erp.modules.product.candidate.service.ProductArchiver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** spec product/product-candidate「从商品文件导入候选」：预览分类、确认写入、来源记录、可见范围与权限 */
class CandidateImportContractTest extends InquiryContractSupport {

    private static final String CAND = "/api/v1/product/candidates";
    private static final String PRODUCTS = "/api/v1/product/products";

    @Autowired
    private ProductArchiver archiver;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private long siemens;
    private long abb;
    private long psu;

    @BeforeEach
    void seed() {
        cleanup();
        siemens = brand("ITSiemens");
        jdbc.update("insert into product_brand_alias (tenant_id, brand_id, alias, alias_key) values (0, ?, '西门子IT', '西门子it')", siemens);
        abb = brand("ITABB");
        psu = category("it-psu", "Power Supply", "开关电源");
        category("it-inv", "Inverter", "变频器");
    }

    @AfterEach
    void cleanup() {
        jdbc.update("delete from product_candidate_source");
        jdbc.update("delete from product_candidate");
        jdbc.update("delete from product where brand_id in (select id from product_brand where brand_name like 'IT%')");
        jdbc.update("delete from product_brand_alias where brand_id in (select id from product_brand where brand_name like 'IT%')");
        jdbc.update("delete from product_brand where brand_name like 'IT%'");
        jdbc.update("delete from product_category where category_code like 'it-%'");
    }

    private long brand(String name) {
        jdbc.update("insert into product_brand (tenant_id, brand_name, status) values (0, ?, 1)", name);
        return jdbc.queryForObject("select id from product_brand where brand_name = ?", Long.class, name);
    }

    private long category(String code, String en, String zh) {
        jdbc.update("insert into product_category (tenant_id, category_code, category_name, category_name_zh, status) values (0, ?, ?, ?, 1)",
                code, en, zh);
        return jdbc.queryForObject("select id from product_category where category_code = ?", Long.class, code);
    }

    private String tenantToken(String username, int tenantId) {
        String token = jwtUtils.generateToken(Map.of("tenantId", tenantId), username, 3600);
        redis.opsForValue().set(RedisKeyConstants.TOKEN_PREFIX + token, "1");
        return token;
    }

    private static Map<String, Object> file(String name, String content) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("fileName", name);
        f.put("content", content);
        return f;
    }

    private static String wiki(String brand, String category, String model, String nameZh, String title) {
        return "---\nbrand: " + brand + "\ncategory: " + category + "\nmodel: " + model + "\n---\n\n# " + brand + " " + model
                + (title == null ? "" : " — " + title) + "\n\n## 基本信息\n\n| 字段 | 内容 |\n|---|---|\n| 中文产品名 | " + nameZh
                + " |\n| 英文产品名 | " + model + " English |\n";
    }

    private static String resource(String name) throws IOException {
        try (InputStream in = CandidateImportContractTest.class.getResourceAsStream("/product-candidate/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static JsonNode byFile(JsonNode arr, String fileName) {
        for (JsonNode n : arr) {
            if (fileName.equals(n.path("fileName").asText())) {
                return n;
            }
        }
        throw new AssertionError("没有 " + fileName + "：" + arr);
    }

    @Test
    void preview_confirm_sources_visibility() throws Exception {
        loginAsAdmin("it_imp_admin");
        String admin = token("it_imp_admin");
        // 商品库已有 ITSiemens / 6ES7214-1AG40-0XB0
        ok(call(json(post(PRODUCTS), write(Map.of("brandId", siemens, "categoryId", psu, "mpnRaw", "6ES7214-1AG40-0XB0"))), admin));
        // 询盘带来的待审核候选 ITABB / ACS580-01-12A7-4，名称为空；已驳回的 ITABB / ACS355-03E-01A2-4
        long inq = ok(call(json(post(INQ), "{\"customerId\":" + customer("Rocha", "Brazil") + ",\"source\":1,\"rawContent\":\"rfq\"}"),
                admin)).path("id").asLong();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String m : List.of("ACS580-01-12A7-4", "ACS355-03E-01A2-4")) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("brand", "ITABB");
            r.put("category", "变频器");
            r.put("confirmedModel", m);
            r.put("quantity", 1);
            r.put("unit", "个");
            rows.add(r);
        }
        ok(call(json(post(INQ + "/" + inq + "/confirm"), write(Map.of("rows", rows))), admin));
        long pool = jdbc.queryForObject("select id from product_candidate where mpn_raw = 'ACS580-01-12A7-4'", Long.class);
        jdbc.update("update product_candidate set product_name = '', description = '' where id = ?", pool);
        long rejected = jdbc.queryForObject("select id from product_candidate where mpn_raw = 'ACS355-03E-01A2-4'", Long.class);
        jdbc.update("update product_candidate set status = 4 where id = ?", rejected);

        String real = resource("wiki-6EP3437-8SB00-0AY0.md").replace("brand: Siemens", "brand: 西门子IT");
        List<Map<String, Object>> files = List.of(
                file("6EP3437-8SB00-0AY0.md", real),
                file("6ES7214.md", wiki("ITSiemens", "PLC", "6es7 214-1ag40-0xb0", "CPU", null)),
                file("ACS580.md", wiki("ITABB", "Inverter", "ACS580-01-12A7-4", "ACS580 通用变频器", "通用变频器 5.5kW")),
                file("ACS355.md", wiki("ITABB", "Inverter", "ACS355-03E-01A2-4", "ACS355", null)),
                file("MD-15.md", wiki("ITQUEEN", "SolenoidValve", "MD-15WAG-SCS13-M16", "电磁阀", null)),
                file("dup.md", wiki("ITSiemens", "PowerSupply", "6EP3437 8SB00-0AY0", "重复", null)),
                file("index.md", "---\ntitle: 索引\n---\n# 商品索引\n"));

        // 预览：分类正确、不落库
        JsonNode preview = ok(call(json(post(CAND + "/import/preview"), write(Map.of("files", files))), admin));
        JsonNode siemensPsu = byFile(preview, "6EP3437-8SB00-0AY0.md");
        assertEquals("NEW", siemensPsu.path("result").asText(), siemensPsu.toString());
        assertEquals(siemens, siemensPsu.path("brandId").asLong(), "品牌按别名识别");
        assertEquals(psu, siemensPsu.path("categoryId").asLong(), "品类按 PowerSupply（开关电源）自动匹配");
        assertEquals("SITOP PSU8200系列开关电源", siemensPsu.path("productName").asText());
        assertEquals("IN_PRODUCT", byFile(preview, "6ES7214.md").path("result").asText(), "型号写法不同也认出已在商品库");
        JsonNode inPool = byFile(preview, "ACS580.md");
        assertEquals("IN_POOL", inPool.path("result").asText());
        assertEquals(pool, inPool.path("candidateId").asLong());
        assertEquals("REJECTED", byFile(preview, "ACS355.md").path("result").asText());
        JsonNode queen = byFile(preview, "MD-15.md");
        assertEquals("NEW", queen.path("result").asText());
        assertTrue(queen.path("brandId").isMissingNode() || queen.path("brandId").isNull(), "品牌未识别");
        assertEquals("DUPLICATE", byFile(preview, "dup.md").path("result").asText());
        JsonNode index = byFile(preview, "index.md");
        assertEquals("INVALID", index.path("result").asText());
        assertTrue(index.path("note").asText().contains("缺少型号"), index.toString());
        assertEquals(0, jdbc.queryForObject("select count(*) from product_candidate_source where source_type = 6", Integer.class),
                "预览不落库");

        // 公司账号（只有商品候选菜单，没有审核权限）确认导入
        loginWithResources("it_imp_viewer", 100099);
        String company = tenantToken("it_imp_viewer", 1001);
        JsonNode res = ok(call(json(post(CAND + "/import/confirm"), write(Map.of("files", files))), company));
        assertEquals(2, res.path("created").asInt(), res.toString());
        assertEquals(1, res.path("appended").asInt());
        assertEquals(4, res.path("skipped").asInt());
        assertEquals(0, res.path("failed").asInt());

        long newId = jdbc.queryForObject("select id from product_candidate where mpn_raw = '6EP3437-8SB00-0AY0' and deleted_at is null",
                Long.class);
        Map<String, Object> created = jdbc.queryForMap("select status, brand_id, category_id, product_name, description, description_en, "
                + "source_count from product_candidate where id = ?", newId);
        assertEquals(1, ((Number) created.get("status")).intValue());
        assertEquals(siemens, ((Number) created.get("brand_id")).longValue());
        assertEquals(psu, ((Number) created.get("category_id")).longValue());
        assertEquals("SITOP PSU8200系列开关电源，3AC输入，DC24V/40A输出", created.get("description"));
        assertEquals("Siemens SITOP PSU8200 Power Supply", created.get("description_en"));
        assertEquals(1, ((Number) created.get("source_count")).intValue());
        Map<String, Object> src = jdbc.queryForMap("select tenant_id, source_type, file_name, create_by from product_candidate_source "
                + "where candidate_id = ?", newId);
        assertEquals(1001, ((Number) src.get("tenant_id")).intValue());
        assertEquals(6, ((Number) src.get("source_type")).intValue());
        assertEquals("6EP3437-8SB00-0AY0.md", src.get("file_name"));

        Map<String, Object> appended = jdbc.queryForMap("select product_name, description, source_count from product_candidate where id = ?", pool);
        assertEquals("ACS580 通用变频器", appended.get("product_name"), "只补空字段");
        assertEquals("通用变频器 5.5kW", appended.get("description"));
        assertEquals(2, ((Number) appended.get("source_count")).intValue());
        assertEquals(4, jdbc.queryForObject("select status from product_candidate where id = ?", Integer.class, rejected),
                "驳回的不动");
        assertNull(jdbc.queryForObject("select brand_id from product_candidate where mpn_raw = 'MD-15WAG-SCS13-M16'", Long.class));

        // 公司账号看得到自己导入的候选，来源显示文件名与导入人
        JsonNode detail = ok(call(get(CAND + "/" + newId), company));
        JsonNode source = detail.path("sources").get(0);
        assertEquals("导入", source.path("sourceTypeName").asText());
        assertEquals("6EP3437-8SB00-0AY0.md", source.path("fileName").asText());
        assertEquals("it_imp_viewer", source.path("createBy").asText());
        // 没有审核权限不能建档
        assertEquals(403, perform(json(post(CAND + "/" + newId + "/approve"), write(Map.of("categoryId", psu))), company).getStatus());

        // 再次导入同一批：新建的变为已在候选池、追加来源；询盘重算不影响导入来源
        JsonNode again = ok(call(json(post(CAND + "/import/preview"), write(Map.of("files", files.subList(0, 1)))), company));
        assertEquals("IN_POOL", again.get(0).path("result").asText());
        // ACS580 的询盘型号改成真实型号 ACS580-01-17A0-4：询盘来源移到新型号，导入来源留在原候选，候选不被删除
        long acsItem = jdbc.queryForObject("select id from inquiry_item where customer_inquiry_id = ? and confirmed_model = ?",
                Long.class, inq, "ACS580-01-12A7-4");
        jdbc.update("update inquiry_item set actual_model = 'ACS580-01-17A0-4', actual_model_key = 'acs5800117a04' where id = ?", acsItem);
        new TransactionTemplate(transactionManager).executeWithoutResult(t -> archiver.sync(List.of(acsItem)));
        assertEquals(1, jdbc.queryForObject("select count(*) from product_candidate where id = ? and deleted_at is null",
                Integer.class, pool), "导入来源还在，候选不删除");
        assertEquals(List.of(6), jdbc.queryForList("select source_type from product_candidate_source where candidate_id = ? "
                + "and deleted_at is null", Integer.class, pool));
        assertEquals(1, jdbc.queryForObject("select source_count from product_candidate where id = ?", Integer.class, pool));
    }
}

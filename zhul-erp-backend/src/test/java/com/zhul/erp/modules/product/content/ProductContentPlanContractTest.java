package com.zhul.erp.modules.product.content;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.common.constants.RedisKeyConstants;
import com.zhul.erp.modules.inquiry.InquiryContractSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** spec product/product-content 与 product/product 规格、应用场景按语言保存、内容上传的写入例外 */
class ProductContentPlanContractTest extends InquiryContractSupport {

    private static final String TASKS = "/api/v1/product/content-tasks";
    private static final String PRODUCTS = "/api/v1/product/products";

    private String admin;
    private long siemens;
    private long plc;
    private long p1;
    private long p2;

    @BeforeEach
    void seed() throws Exception {
        cleanup();
        jdbc.update("insert into product_brand (tenant_id, brand_name, status) values (0, 'ITCSiemens', 1)");
        siemens = jdbc.queryForObject("select id from product_brand where brand_name = 'ITCSiemens'", Long.class);
        jdbc.update("insert into product_brand_alias (tenant_id, brand_id, alias, alias_key) values (0, ?, '西门子IT', '西门子it')", siemens);
        jdbc.update("insert into product_category (tenant_id, category_code, category_name, category_name_zh, status) values (0, 'itc-plc', 'PLC', '可编程控制器', 1)");
        plc = jdbc.queryForObject("select id from product_category where category_code = 'itc-plc'", Long.class);
        loginAsAdmin("it_content_admin");
        admin = token("it_content_admin");
        p1 = ok(call(json(post(PRODUCTS), write(Map.of("brandId", siemens, "categoryId", plc, "mpnRaw", "6ES7214-1AG40-0XB0"))), admin))
                .path("id").asLong();
        p2 = ok(call(json(post(PRODUCTS), write(Map.of("brandId", siemens, "categoryId", plc, "mpnRaw", "6ES7321-1FH00-0AA0"))), admin))
                .path("id").asLong();
    }

    @AfterEach
    void cleanup() {
        for (String t : new String[] {"product_content_import", "product_content_task", "product_seo", "product_seo_faq"}) {
            jdbc.update("delete from " + t + " where product_id in (select id from product where brand_id in "
                    + "(select id from product_brand where brand_name like 'ITC%'))");
        }
        String products = "(select id from product where brand_id in (select id from product_brand where brand_name like 'ITC%'))";
        jdbc.update("delete from product_relationship_note where relationship_id in (select id from product_relationship where product_id in " + products + ")");
        for (String t : new String[] {"product_specification", "product_application", "product_document", "product_relationship", "product_locale"}) {
            jdbc.update("delete from " + t + " where product_id in " + products);
        }
        jdbc.update("delete from product where brand_id in (select id from product_brand where brand_name like 'ITC%')");
        jdbc.update("delete from product_brand_alias where brand_id in (select id from product_brand where brand_name like 'ITC%')");
        jdbc.update("delete from product_brand where brand_name like 'ITC%'");
        jdbc.update("delete from product_category where category_code like 'itc-%'");
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

    private static String md(String brand, String model, String lang, String body) {
        return "---\nbrand: " + brand + "\nmodel: " + model + "\nlang: " + lang + "\n---\n\n" + body;
    }

    private static final String EN_BODY = """
            ## 一句话规格摘要
            CPU 1214C, 14 DI / 10 DO

            ## 首屏定义块
            The Siemens 6ES7214-1AG40-0XB0 is a compact CPU for the S7-1200 PLC family.

            ## SEO 标题
            6ES7214-1AG40-0XB0 Siemens CPU 1214C | Fast Quote

            ## SEO 描述
            Genuine Siemens CPU 1214C in stock.

            ## 产品长描述
            Paragraph one.
            Paragraph two.

            ## 规格参数表
            | 字段 | 内容 | 单位 |
            |---|---|---|
            | Rated Voltage | 24 | V DC |
            | Digital Inputs | 14 | |
            | Digital Outputs | 10 | |

            ## 应用场景
            - ⚙️ | Machine control | Small machines

            ## 兼容替代型号
            | 原型号 | 兼容说明 |
            |---|---|
            | 6ES7321-1FH00-0AA0 | Same family |
            | 6ES7214-1AG31-0XB0 | Previous version |

            ## FAQ
            1. Q: Is it in stock?
               A: Yes, ships in 24 hours.
            2. Q: Warranty?
               A: 12 months.

            ## 技术资料
            - [Datasheet](https://example.com/6es7214.pdf)

            ## 价格
            - 售价：100
            """;

    @Test
    void taskList_package_previewConfirm_rules_isolation_permission() throws Exception {
        // 所有商品自动出现在待生成里
        JsonNode page = ok(call(json(post(TASKS + "/page"), write(Map.of("status", 1, "brandId", siemens))), admin));
        assertEquals(2, page.path("total").asInt());
        assertEquals(p2, page.path("records").get(0).path("productId").asLong(), "按更新时间倒序");
        assertEquals("可编程控制器", page.path("records").get(0).path("categoryName").asText());
        assertEquals(2, ok(call(json(post(TASKS + "/counts"), write(Map.of("brandId", siemens))), admin)).path("1").asInt());

        // 任务包：单个是 Markdown，多个是 zip；记录下载时间
        MockHttpServletResponse one = perform(get(TASKS + "/package").param("productIds", String.valueOf(p1)), admin);
        assertEquals(200, one.getStatus());
        String pkg = one.getContentAsString(StandardCharsets.UTF_8);
        assertTrue(pkg.contains("brand: ITCSiemens") && pkg.contains("model: 6ES7214-1AG40-0XB0") && pkg.contains("## 规格参数表"), pkg);
        assertTrue(pkg.contains("品类：可编程控制器"));
        MockHttpServletResponse zip = perform(get(TASKS + "/package").param("productIds", p1 + "," + p2), admin);
        List<String> names = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip.getContentAsByteArray()))) {
            for (ZipEntry e = in.getNextEntry(); e != null; e = in.getNextEntry()) {
                names.add(e.getName());
            }
        }
        assertEquals(List.of("6ES7214-1AG40-0XB0.md", "6ES7321-1FH00-0AA0.md"), names);
        assertFalse(ok(call(json(post(TASKS + "/page"), write(Map.of("brandId", siemens))), admin)).path("records").get(0)
                .path("downloadedAt").isNull());
        // 任务包原样上传：只有表头和说明，没有可写入的内容
        JsonNode raw = ok(call(json(post(TASKS + "/preview"), write(Map.of("files", List.of(file("raw.md", pkg))))), admin)).get(0);
        assertFalse(raw.path("confirmable").asBoolean());

        // 平台已核实的英文规格 Rated Voltage
        ok(call(json(put(PRODUCTS + "/" + p1 + "/specifications"), write(Map.of("items", List.of(
                Map.of("specKey", "rated_voltage", "specLabel", "Rated Voltage", "specValue", "24V", "verified", 1),
                Map.of("specKey", "old", "specLabel", "Old", "specValue", "x"))))), admin));

        // 预览：别名品牌 + 不同写法型号能匹配；找不到商品的文件不能确认；同一商品同一语言重复
        List<Map<String, Object>> files = List.of(
                file("a.en.md", md("西门子IT", "6es7 214-1ag40-0xb0", "en", EN_BODY)),
                file("b.en.md", md("ITCSiemens", "6ES7999-XXX", "en", EN_BODY)),
                file("c.en.md", md("ITCSiemens", "6ES7214-1AG40-0XB0", "en", "## SEO 标题\ndup\n")));
        JsonNode preview = ok(call(json(post(TASKS + "/preview"), write(Map.of("files", files))), admin));
        JsonNode a = preview.get(0);
        assertTrue(a.path("confirmable").asBoolean(), a.toString());
        assertEquals(p1, a.path("productId").asLong());
        JsonNode specs = block(a, "SPECS");
        assertEquals("shared", specs.path("target").asText());
        assertEquals(3, specs.path("count").asInt());
        assertTrue(specs.path("hints").toString().contains("Rated Voltage」已核实"), specs.toString());
        assertEquals("company", block(a, "FAQ").path("target").asText());
        assertTrue(a.path("notes").toString().contains("第三期"));
        assertFalse(preview.get(1).path("confirmable").asBoolean());
        assertTrue(preview.get(1).path("errors").toString().contains("找不到商品"));
        assertTrue(preview.get(2).path("errors").toString().contains("a.en.md"));
        assertEquals(0, jdbc.queryForObject("select count(*) from product_content_import where product_id = ?", Integer.class, p1),
                "预览不落库");

        // 确认：逐个文件独立，失败的不影响成功的；跳过应用场景
        Map<String, Object> skipApps = file("a.en.md", md("西门子IT", "6es7 214-1ag40-0xb0", "en", EN_BODY));
        skipApps.put("skip", List.of("APPS"));
        JsonNode res = ok(call(json(post(TASKS + "/confirm"), write(Map.of("files", List.of(skipApps, files.get(1))))), admin));
        assertEquals(1, res.path("succeeded").asInt(), res.toString());
        assertEquals(1, res.path("failed").asInt());

        JsonNode enSpecs = ok(call(get(PRODUCTS + "/" + p1 + "/specifications"), admin));
        assertEquals(3, enSpecs.size(), "已核实的保留，未核实的 Old 被替换，同名 Rated Voltage 不重复");
        assertEquals("24V", find(enSpecs, "specLabel", "Rated Voltage").path("specValue").asText());
        assertEquals(0, ok(call(get(PRODUCTS + "/" + p1 + "/applications"), admin)).size(), "跳过的块不写入");
        assertEquals("CPU 1214C, 14 DI / 10 DO",
                jdbc.queryForObject("select spec_summary from product where id = ?", String.class, p1), "英文规格摘要同步商品");
        JsonNode rels = ok(call(get(PRODUCTS + "/" + p1 + "/relationships"), admin));
        assertEquals(2, rels.size());
        JsonNode sameFamily = find(rels, "relatedMpn", "6ES7321-1FH00-0AA0");
        assertEquals(p2, sameFamily.path("relatedProductId").asLong(), "关联型号在库内时关联商品");
        assertEquals(4, sameFamily.path("relationshipType").asInt());
        assertEquals(1, jdbc.queryForObject("select count(*) from product_document where product_id = ? and deleted_at is null", Integer.class, p1));

        JsonNode content = ok(call(get(TASKS + "/products/" + p1), admin));
        assertEquals(2, content.path("status").asInt());
        JsonNode en = content.path("langs").path("en");
        assertEquals("6ES7214-1AG40-0XB0 Siemens CPU 1214C | Fast Quote", en.path("seoTitle").asText());
        assertEquals("Paragraph one.\nParagraph two.", en.path("longDescription").asText());
        assertEquals(2, en.path("faqs").size());
        assertEquals("a.en.md", en.path("fileName").asText());

        // 中文：规格按语言独立，兼容型号只更新中文说明；技术资料同地址不重复
        String zhBody = """
                ## 规格参数表
                | 工作电压 | 24 | V DC |
                ## 兼容替代型号
                | 原型号 | 兼容说明 |
                |---|---|
                | 6ES7321-1FH00-0AA0 | 同系列 |
                ## 技术资料
                - [数据手册](https://example.com/6es7214.pdf)
                ## FAQ
                问：有现货吗？
                答：有。
                """;
        ok(call(json(post(TASKS + "/confirm"), write(Map.of("files", List.of(file("a.zh.md", md("ITCSiemens", "6ES7214-1AG40-0XB0", "zh", zhBody)))))), admin));
        assertEquals(1, ok(call(get(PRODUCTS + "/" + p1 + "/specifications").param("lang", "zh"), admin)).size());
        assertEquals(3, ok(call(get(PRODUCTS + "/" + p1 + "/specifications"), admin)).size(), "英文不受影响");
        JsonNode zhRels = ok(call(get(PRODUCTS + "/" + p1 + "/relationships").param("lang", "zh"), admin));
        assertEquals(2, zhRels.size(), "兼容型号按型号合并，不新增");
        assertEquals("同系列", find(zhRels, "relatedMpn", "6ES7321-1FH00-0AA0").path("note").asText());
        assertEquals("Same family", find(ok(call(get(PRODUCTS + "/" + p1 + "/relationships"), admin)), "relatedMpn", "6ES7321-1FH00-0AA0")
                .path("note").asText());
        assertEquals(1, jdbc.queryForObject("select count(*) from product_document where product_id = ? and deleted_at is null", Integer.class, p1));
        assertEquals(2, ok(call(get(TASKS + "/products/" + p1), admin)).path("langs").path("en").path("faqs").size(), "FAQ 按语言替换");

        // 俄文写入后完成
        ok(call(json(post(TASKS + "/confirm"), write(Map.of("files", List.of(file("a.ru.md",
                md("ITCSiemens", "6ES7214-1AG40-0XB0", "ru", "## SEO 标题\nКупить 6ES7214-1AG40-0XB0\n")))))), admin));
        assertEquals(3, ok(call(get(TASKS + "/products/" + p1), admin)).path("status").asInt());
        assertEquals(1, ok(call(json(post(TASKS + "/counts"), write(Map.of("brandId", siemens))), admin)).path("3").asInt());

        // 公司隔离：另一公司看不到本公司 SEO 与 FAQ、任务状态
        String other = tenantToken("it_content_admin", 1001);
        JsonNode otherContent = ok(call(get(TASKS + "/products/" + p1), other));
        assertEquals(1, otherContent.path("status").asInt());
        JsonNode otherTitle = otherContent.path("langs").path("en").path("seoTitle");
        assertTrue(otherTitle.isNull() || otherTitle.isMissingNode(), otherTitle.toString());
        assertEquals(0, otherContent.path("langs").path("en").path("faqs").size());
        // 租户账号确认上传可以写共享商品库（例外），直接改规格仍被拒绝
        JsonNode t = ok(call(json(post(TASKS + "/confirm"), write(Map.of("files", List.of(file("o.en.md",
                md("ITCSiemens", "6ES7321-1FH00-0AA0", "en", "## 一句话规格摘要\nSM321 16DI\n")))))), other));
        assertEquals(1, t.path("succeeded").asInt(), t.toString());
        assertFalse(call(json(put(PRODUCTS + "/" + p2 + "/specifications"), write(Map.of("items", List.of()))), other).path("code").asInt() == 0);

        // 只有菜单没有维护权限：能预览，确认被拒
        loginWithResources("it_content_viewer", 100100);
        String viewer = token("it_content_viewer");
        JsonNode vp = ok(call(json(post(TASKS + "/preview"), write(Map.of("files", List.of(files.get(0))))), viewer)).get(0);
        assertTrue(vp.path("confirmable").asBoolean(), vp.toString());
        assertEquals(403, perform(json(post(TASKS + "/confirm"), write(Map.of("files", List.of(files.get(0))))), viewer).getStatus());
    }

    private static JsonNode block(JsonNode preview, String section) {
        for (JsonNode b : preview.path("blocks")) {
            if (section.equals(b.path("section").asText())) {
                return b;
            }
        }
        throw new AssertionError("没有块 " + section + "：" + preview);
    }

    private static JsonNode find(JsonNode arr, String field, String value) {
        for (JsonNode n : arr) {
            if (value.equals(n.path(field).asText())) {
                return n;
            }
        }
        throw new AssertionError("没有 " + field + "=" + value + "：" + arr);
    }
}

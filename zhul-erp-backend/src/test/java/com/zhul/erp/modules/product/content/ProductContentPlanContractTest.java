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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * spec product/product-content（资料与营销两阶段、两种任务包、资料核对、营销按平台、平台设置、按平台不发布）
 * 与 product/product 资料只存中文、内容上传与核对的写入例外
 */
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
        for (String t : new String[] {"product_content_import", "product_content_task", "product_fact_task", "product_seo",
                "product_seo_faq", "product_faq", "product_platform_content", "product_platform_keyword", "product_marketing_skip"}) {
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
        jdbc.update("delete from sys_config where config_key = 'product.content.platforms' and tenant_id <> 0");
        jdbc.update("update sys_config set config_value = 'alibaba,mic,site,social' where tenant_id = 0 "
                + "and config_key = 'product.content.platforms'");
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

    private static String md(String type, String brand, String model, String lang, String body) {
        return "---\ntype: " + type + "\nbrand: " + brand + "\nmodel: " + model + "\nlang: " + lang + "\n---\n\n" + body;
    }

    private static String platformMd(String platform, String model, String lang, String body) {
        return "---\ntype: marketing\nplatform: " + platform + "\nbrand: ITCSiemens\nmodel: " + model + "\nlang: " + lang
                + "\n---\n\n" + body;
    }

    private static String facts(String brand, String model, String body) {
        return md("facts", brand, model, "zh", body);
    }

    /** 独立站营销包 */
    private static String marketing(String brand, String model, String lang, String body) {
        return "---\ntype: marketing\nplatform: site\nbrand: " + brand + "\nmodel: " + model + "\nlang: " + lang
                + "\n---\n\n" + body;
    }

    private static final String FACTS_BODY = """
            ## 一句话规格摘要
            CPU 1214C，14 入 10 出

            ## 规格参数表
            | 字段 | 内容 | 单位 |
            |---|---|---|
            | 额定电压 | 24 | V DC |
            | 数字量输入 | 14 | |
            | 数字量输出 | 10 | |

            ## 应用场景
            - ⚙️ | 设备控制 | 小型设备

            ## 兼容替代型号
            | 原型号 | 兼容说明 |
            |---|---|
            | 6ES7321-1FH00-0AA0 | 同系列 |
            | 6ES7214-1AG31-0XB0 | 上一代 |

            ## 技术问答
            1. Q: 能用 TIA Portal 编程吗？
               A: 可以，V13 及以上。
            2. Q: 支持几路高速计数？
               A: 6 路。

            ## 技术资料
            - [数据手册](https://example.com/6es7214.pdf)

            ## SEO 标题
            不应写入

            ## 价格
            - 售价：100
            """;

    private static final String EN_BODY = """
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
            | Rated Voltage | 24 | V DC |

            ## FAQ
            1. Q: Is it in stock?
               A: Yes, ships in 24 hours.
            2. Q: Warranty?
               A: 12 months.
            """;

    private JsonNode task(long productId, String token) throws Exception {
        JsonNode records = ok(call(json(post(TASKS + "/page"), write(Map.of("brandId", siemens))), token)).path("records");
        return find(records, "productId", String.valueOf(productId));
    }

    private int stage(long productId, String token) throws Exception {
        return task(productId, token).path("status").asInt();
    }

    private JsonNode confirm(String token, Map<String, Object> file) throws Exception {
        return ok(call(json(post(TASKS + "/confirm"), write(Map.of("files", List.of(file)))), token));
    }

    @Test
    void packages_preview_factsConfirm_sharedStage_verify_marketing() throws Exception {
        String other = tenantToken("it_content_admin", 1001);
        // 平台模板只启用独立站：独立站英文、俄文写完即营销完成
        ok(call(json(put(TASKS + "/platforms"), write(Map.of("platforms", List.of("site")))), admin));
        // 所有商品自动进入「资料待补」
        JsonNode page = ok(call(json(post(TASKS + "/page"), write(Map.of("status", 1, "brandId", siemens))), admin));
        assertEquals(2, page.path("total").asInt());
        assertEquals(p2, page.path("records").get(0).path("productId").asLong(), "按更新时间倒序");
        assertEquals("可编程控制器", page.path("records").get(0).path("categoryName").asText());
        assertEquals(2, ok(call(json(post(TASKS + "/counts"), write(Map.of("brandId", siemens))), admin)).path("1").asInt());

        // 资料包：中文模版、只有资料章节；营销包：营销章节；多个打 zip；分别记录下载时间
        MockHttpServletResponse one = perform(get(TASKS + "/package").param("productIds", String.valueOf(p1))
                .param("type", "facts"), admin);
        assertEquals(200, one.getStatus());
        String pkg = one.getContentAsString(StandardCharsets.UTF_8);
        assertTrue(pkg.contains("type: facts") && pkg.contains("lang: zh") && pkg.contains("model: 6ES7214-1AG40-0XB0")
                && pkg.contains("## 技术问答") && !pkg.contains("## SEO 标题"), pkg);
        assertTrue(pkg.contains("品类：可编程控制器"));
        String mkt = perform(get(TASKS + "/package").param("productIds", String.valueOf(p1)).param("type", "marketing")
                        .param("platform", "site"), admin)
                .getContentAsString(StandardCharsets.UTF_8);
        assertTrue(mkt.contains("type: marketing") && mkt.contains("platform: site") && mkt.contains("lang: en")
                && mkt.contains("## SEO 标题")
                && !mkt.contains("## 规格参数表"), mkt);
        MockHttpServletResponse zip = perform(get(TASKS + "/package").param("productIds", p1 + "," + p2)
                .param("type", "facts"), admin);
        List<String> names = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip.getContentAsByteArray()))) {
            for (ZipEntry e = in.getNextEntry(); e != null; e = in.getNextEntry()) {
                names.add(e.getName());
            }
        }
        assertEquals(List.of("6ES7214-1AG40-0XB0.facts.md", "6ES7321-1FH00-0AA0.facts.md"), names);
        assertTrue(task(p1, other).hasNonNull("factDownloadedAt"), "资料包下载时间所有公司共享");
        assertTrue(task(p1, admin).hasNonNull("downloadedAt"), "本公司下载过营销包");
        assertFalse(task(p1, other).hasNonNull("downloadedAt"), "营销包下载时间按公司");
        assertFalse(call(get(TASKS + "/package").param("productIds", String.valueOf(p1)).param("type", "seo"), admin)
                .path("code").asInt() == 0);
        // 任务包原样上传：只有表头和说明，没有可写入的内容
        JsonNode raw = ok(call(json(post(TASKS + "/preview"), write(Map.of("files", List.of(file("raw.md", pkg))))), admin)).get(0);
        assertFalse(raw.path("confirmable").asBoolean());

        // 平台已核实的中文规格「额定电压」；以前写入的英文规格不显示也不受影响
        ok(call(json(put(PRODUCTS + "/" + p1 + "/specifications"), write(Map.of("items", List.of(
                Map.of("specKey", "rated_voltage", "specLabel", "额定电压", "specValue", "24V", "verified", 1),
                Map.of("specKey", "old", "specLabel", "旧字段", "specValue", "x"))))), admin));
        jdbc.update("insert into product_specification (tenant_id, product_id, lang, spec_key, spec_label, spec_value, verified) "
                + "values (0, ?, 'en', 'legacy', 'Legacy', 'y', 0)", p1);
        assertFalse(call(get(PRODUCTS + "/" + p1 + "/specifications").param("lang", "en"), admin).path("code").asInt() == 0,
                "商品资料只维护中文");

        // 预览：别名品牌 + 不同写法型号；资料包语言不是中文、缺包类型、找不到商品、同商品同语言重复都不能确认
        List<Map<String, Object>> files = List.of(
                file("a.facts.md", facts("西门子IT", "6es7 214-1ag40-0xb0", FACTS_BODY)),
                file("b.facts.md", facts("ITCSiemens", "6ES7999-XXX", FACTS_BODY)),
                file("c.facts.md", facts("ITCSiemens", "6ES7214-1AG40-0XB0", "## 一句话规格摘要\ndup\n")),
                file("d.md", md("facts", "ITCSiemens", "6ES7214-1AG40-0XB0", "en", FACTS_BODY)),
                file("e.md", "---\nbrand: ITCSiemens\nmodel: 6ES7214-1AG40-0XB0\nlang: en\n---\n" + EN_BODY));
        JsonNode preview = ok(call(json(post(TASKS + "/preview"), write(Map.of("files", files))), admin));
        JsonNode a = preview.get(0);
        assertTrue(a.path("confirmable").asBoolean(), a.toString());
        assertEquals(p1, a.path("productId").asLong());
        assertEquals("facts", a.path("type").asText());
        JsonNode specs = block(a, "SPECS");
        assertEquals("shared", specs.path("target").asText());
        assertEquals(3, specs.path("count").asInt());
        assertTrue(specs.path("hints").toString().contains("额定电压」已核实"), specs.toString());
        assertEquals("shared", block(a, "TECH_FAQ").path("target").asText());
        assertTrue(block(a, "TECH_FAQ").path("hints").toString().contains("待平台审核"));
        assertTrue(a.path("notes").toString().contains("不属于资料包"), "资料包里的营销章节跳过");
        assertTrue(a.path("notes").toString().contains("第三期"));
        assertTrue(preview.get(1).path("errors").toString().contains("找不到商品"));
        assertTrue(preview.get(2).path("errors").toString().contains("a.facts.md"));
        assertTrue(preview.get(3).path("errors").toString().contains("资料包只能是中文"));
        assertTrue(preview.get(4).path("errors").toString().contains("缺少包类型"));
        assertEquals(0, jdbc.queryForObject("select count(*) from product_content_import where product_id = ?", Integer.class, p1),
                "预览不落库");

        // 确认资料包（另一家公司操作）：逐个文件独立；只写中文；技术问答待审核
        JsonNode res = ok(call(json(post(TASKS + "/confirm"), write(Map.of("files", List.of(files.get(0), files.get(1))))), other));
        assertEquals(1, res.path("succeeded").asInt(), res.toString());
        assertEquals(1, res.path("failed").asInt());
        JsonNode zhSpecs = ok(call(get(PRODUCTS + "/" + p1 + "/specifications"), admin));
        assertEquals(3, zhSpecs.size(), "已核实的保留，未核实的旧字段被替换，同名额定电压不重复");
        assertEquals("24V", find(zhSpecs, "specLabel", "额定电压").path("specValue").asText());
        assertEquals(1, jdbc.queryForObject("select count(*) from product_specification where product_id = ? and lang = 'en' "
                + "and deleted_at is null", Integer.class, p1), "英文旧规格不受影响");
        assertEquals("CPU 1214C，14 入 10 出",
                jdbc.queryForObject("select spec_summary from product where id = ?", String.class, p1), "中文规格摘要同步商品");
        JsonNode rels = ok(call(get(PRODUCTS + "/" + p1 + "/relationships"), admin));
        assertEquals(2, rels.size());
        JsonNode sameFamily = find(rels, "relatedMpn", "6ES7321-1FH00-0AA0");
        assertEquals(p2, sameFamily.path("relatedProductId").asLong(), "关联型号在库内时关联商品");
        assertEquals("同系列", sameFamily.path("note").asText());
        assertEquals(1, jdbc.queryForObject("select count(*) from product_document where product_id = ? and deleted_at is null", Integer.class, p1));
        assertEquals(2, ok(call(get(PRODUCTS + "/" + p1 + "/faqs"), admin)).size(), "平台账号看得到待审核的技术问答");
        assertEquals(0, ok(call(get(PRODUCTS + "/" + p1 + "/faqs"), other)).size(), "公司账号看不到待审核的技术问答");
        assertEquals(0, jdbc.queryForObject("select count(*) from product_seo where product_id = ?", Integer.class, p1),
                "资料包里的 SEO 标题不写入");

        // 资料阶段所有公司共享
        assertEquals(2, stage(p1, admin));
        assertEquals(2, stage(p1, other));
        assertEquals("it_content_admin", task(p1, admin).path("factImportedBy").asText());

        // 资料待核时营销内容照样写入，阶段仍为资料待核
        JsonNode en = confirm(admin, file("a.en.md", marketing("ITCSiemens", "6ES7214-1AG40-0XB0", "en", EN_BODY)));
        assertEquals(1, en.path("succeeded").asInt(), en.toString());
        assertEquals(3, ok(call(get(PRODUCTS + "/" + p1 + "/specifications"), admin)).size(), "营销包里的规格参数表不写入");
        confirm(admin, file("a.ru.md", marketing("ITCSiemens", "6ES7214-1AG40-0XB0", "ru", "## SEO 标题\nКупить 6ES7214-1AG40-0XB0\n")));
        assertEquals(2, stage(p1, admin));

        // 公司账号核对资料：中文规格、应用场景变为已核实，记录核对人；本公司营销写满直接已完成，另一家进入营销待生成
        assertEquals(0, call(json(post(TASKS + "/products/" + p1 + "/verify-facts"), "{}"), other).path("code").asInt());
        assertEquals(0, jdbc.queryForObject("select count(*) from product_specification where product_id = ? and lang = 'zh' "
                + "and deleted_at is null and verified = 0", Integer.class, p1));
        assertEquals(0, jdbc.queryForObject("select count(*) from product_application where product_id = ? and lang = 'zh' "
                + "and deleted_at is null and verified = 0", Integer.class, p1));
        assertEquals(1, jdbc.queryForObject("select count(*) from product_specification where product_id = ? and lang = 'en' "
                + "and deleted_at is null and verified = 0", Integer.class, p1), "英文旧规格不变");
        assertEquals(4, stage(p1, admin));
        assertEquals(3, stage(p1, other));
        assertEquals("it_content_admin", task(p1, other).path("factVerifiedBy").asText());
        JsonNode again = call(json(post(TASKS + "/products/" + p1 + "/verify-facts"), "{}"), admin);
        assertEquals("FACTS_ALREADY_VERIFIED", again.path("data").path("errorCode").asText(), again.toString());

        // 核对后再导入回到待核；已核实的规格不被覆盖
        confirm(admin, file("a2.facts.md", facts("ITCSiemens", "6ES7214-1AG40-0XB0", """
                ## 规格参数表
                | 数字量输入 | 16 | |
                | 防护等级 | IP20 | |
                """)));
        assertEquals(2, stage(p1, admin));
        assertEquals(2, stage(p1, other));
        JsonNode afterReimport = ok(call(get(PRODUCTS + "/" + p1 + "/specifications"), admin));
        assertEquals(4, afterReimport.size(), "已核实的 3 条保留，新字段防护等级写入");
        assertEquals("14", find(afterReimport, "specLabel", "数字量输入").path("specValue").asText());

        // 商品详情：阶段、资料状态、本公司英文与俄文营销内容；另一家公司看不到
        JsonNode content = ok(call(get(TASKS + "/products/" + p1), admin));
        assertEquals(2, content.path("status").asInt());
        assertTrue(content.hasNonNull("factVerifiedAt"));
        JsonNode enContent = content.path("langs").path("en");
        assertEquals("6ES7214-1AG40-0XB0 Siemens CPU 1214C | Fast Quote", enContent.path("seoTitle").asText());
        assertEquals("Paragraph one.\nParagraph two.", enContent.path("longDescription").asText());
        assertEquals(2, enContent.path("faqs").size());
        assertEquals("a.en.md", enContent.path("fileName").asText());
        assertTrue(content.path("langs").path("zh").isMissingNode(), "营销内容不再有中文");
        JsonNode otherContent = ok(call(get(TASKS + "/products/" + p1), other));
        JsonNode otherTitle = otherContent.path("langs").path("en").path("seoTitle");
        assertTrue(otherTitle.isNull() || otherTitle.isMissingNode(), otherTitle.toString());
        assertEquals(0, otherContent.path("langs").path("en").path("faqs").size());

        // 没有导入也可以直接核对：平台手工维护中文规格后核对，进入营销阶段
        ok(call(json(put(PRODUCTS + "/" + p2 + "/specifications"), write(Map.of("items", List.of(
                Map.of("specKey", "inputs", "specLabel", "输入点数", "specValue", "16"))))), admin));
        assertEquals(1, stage(p2, admin));
        ok(call(json(post(TASKS + "/products/" + p2 + "/verify-facts"), "{}"), admin));
        assertEquals(3, stage(p2, admin));
        JsonNode counts = ok(call(json(post(TASKS + "/counts"), write(Map.of("brandId", siemens))), admin));
        assertEquals(1, counts.path("2").asInt());
        assertEquals(1, counts.path("3").asInt());
        assertEquals(2, counts.path("all").asInt());

        // 公司账号直接改共享规格仍被拒绝
        assertFalse(call(json(put(PRODUCTS + "/" + p2 + "/specifications"), write(Map.of("items", List.of()))), other).path("code").asInt() == 0);

        // 只有菜单没有维护权限：能预览，确认与资料已核被拒
        loginWithResources("it_content_viewer", 100100);
        String viewer = token("it_content_viewer");
        JsonNode vp = ok(call(json(post(TASKS + "/preview"), write(Map.of("files", List.of(files.get(0))))), viewer)).get(0);
        assertTrue(vp.path("confirmable").asBoolean(), vp.toString());
        assertEquals(403, perform(json(post(TASKS + "/confirm"), write(Map.of("files", List.of(files.get(0))))), viewer).getStatus());
        assertEquals(403, perform(json(post(TASKS + "/products/" + p2 + "/verify-facts"), "{}"), viewer).getStatus());
    }

    private static final String ALIBABA_BODY = """
            ## 标题
            Siemens 6ES7214-1AG40-0XB0 CPU 1214C PLC, Genuine New

            ## 关键词库
            | 关键词 | 档位 |
            |---|---|
            | S7-1200 CPU | 主要 |
            | 6ES7214-1AG40-0XB0 | 核心 |
            | siemens plc price | 长尾 |
            | CPU 1214C | 核心 |
            | 6es7214-1ag40-0xb0 | 长尾 |

            ## 卖点
            - Genuine Siemens
            - 14 DI / 10 DO
            - Ships in 24 hours
            - 12-month warranty
            - Datasheet support
            - Free wiring advice
            """;

    @Test
    void platforms_skips_platformPackages_keywords() throws Exception {
        String a = tenantToken("it_content_admin", 1002);
        String b = tenantToken("it_content_admin", 1003);

        // 平台设置：默认四个都启用；至少一个；平台无效报错；公司覆盖不影响其他公司
        JsonNode defaults = ok(call(get(TASKS + "/platforms"), a));
        assertEquals(4, defaults.size());
        assertTrue(defaults.toString().contains("\"code\":\"alibaba\"") && defaults.get(0).path("enabled").asBoolean());
        assertTrue(call(json(put(TASKS + "/platforms"), write(Map.of("platforms", List.of()))), a).toString().contains("至少启用一个平台"));
        assertFalse(call(json(put(TASKS + "/platforms"), write(Map.of("platforms", List.of("amazon")))), a).path("code").asInt() == 0);
        JsonNode saved = ok(call(json(put(TASKS + "/platforms"), write(Map.of("platforms", List.of("site", "alibaba")))), a));
        assertEquals(List.of(true, false, true, false), List.of(saved.get(0).path("enabled").asBoolean(),
                saved.get(1).path("enabled").asBoolean(), saved.get(2).path("enabled").asBoolean(), saved.get(3).path("enabled").asBoolean()));
        JsonNode bPlatforms = ok(call(get(TASKS + "/platforms"), b));
        for (JsonNode p : bPlatforms) {
            assertTrue(p.path("enabled").asBoolean(), "公司 A 的设置不影响公司 B");
        }

        // 资料核对后进入营销阶段
        ok(call(json(post(TASKS + "/products/" + p1 + "/verify-facts"), "{}"), admin));
        assertEquals(3, stage(p1, a));

        // 按平台下载营销包：模版只含该平台章节；没启用的平台拒绝；批量打 zip
        String ali = perform(get(TASKS + "/package").param("productIds", String.valueOf(p1)).param("type", "marketing")
                .param("platform", "alibaba"), a).getContentAsString(StandardCharsets.UTF_8);
        assertTrue(ali.contains("platform: alibaba") && ali.contains("## 关键词库") && ali.contains("## 卖点")
                && !ali.contains("## SEO 标题") && !ali.contains("## 规格参数表"), ali);
        MockHttpServletResponse aliResp = perform(get(TASKS + "/package").param("productIds", String.valueOf(p1))
                .param("type", "marketing").param("platform", "alibaba"), a);
        assertTrue(aliResp.getHeader("Content-Disposition").contains("6ES7214-1AG40-0XB0.alibaba.md"));
        JsonNode micDenied = call(get(TASKS + "/package").param("productIds", String.valueOf(p1)).param("type", "marketing")
                .param("platform", "mic"), a);
        assertEquals("PLATFORM_NOT_ENABLED", micDenied.path("data").path("errorCode").asText(), micDenied.toString());
        assertFalse(call(get(TASKS + "/package").param("productIds", String.valueOf(p1)).param("type", "marketing"), a)
                .path("code").asInt() == 0, "营销包要选平台");
        MockHttpServletResponse socialZip = perform(get(TASKS + "/package").param("productIds", p1 + "," + p2)
                .param("type", "marketing").param("platform", "social"), b);
        List<String> names = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(socialZip.getContentAsByteArray()))) {
            for (ZipEntry e = in.getNextEntry(); e != null; e = in.getNextEntry()) {
                names.add(e.getName());
            }
        }
        assertEquals(List.of("6ES7214-1AG40-0XB0.social.md", "6ES7321-1FH00-0AA0.social.md"), names);

        // 预览：没启用的平台不能确认；关键词库按档位排序、去重提示；卖点超量提示
        JsonNode micPreview = ok(call(json(post(TASKS + "/preview"), write(Map.of("files", List.of(
                file("x.mic.md", platformMd("mic", "6ES7214-1AG40-0XB0", "en", "## 标题\nMIC title\n")))))), a)).get(0);
        assertFalse(micPreview.path("confirmable").asBoolean());
        assertTrue(micPreview.path("errors").toString().contains("本公司没有启用中国制造网"), micPreview.toString());
        JsonNode aliPreview = ok(call(json(post(TASKS + "/preview"), write(Map.of("files", List.of(
                file("a.alibaba.md", platformMd("alibaba", "6ES7214-1AG40-0XB0", "en", ALIBABA_BODY)))))), a)).get(0);
        assertTrue(aliPreview.path("confirmable").asBoolean(), aliPreview.toString());
        assertEquals("alibaba", aliPreview.path("platform").asText());
        JsonNode kwBlock = block(aliPreview, "KEYWORDS");
        assertEquals("company", kwBlock.path("target").asText());
        assertEquals(4, kwBlock.path("count").asInt());
        assertEquals("6ES7214-1AG40-0XB0", kwBlock.path("rows").get(0).get(0).asText());
        assertEquals("核心", kwBlock.path("rows").get(1).get(1).asText());
        assertTrue(kwBlock.path("hints").toString().contains("重复的 1 个"), kwBlock.toString());
        assertTrue(block(aliPreview, "SELLING_POINTS").path("hints").toString().contains("超过 5 条"));

        // 确认阿里国际站：任务仍为营销待生成（独立站还没写）
        JsonNode aliRes = confirm(a, file("a.alibaba.md", platformMd("alibaba", "6ES7214-1AG40-0XB0", "en", ALIBABA_BODY)));
        assertEquals(1, aliRes.path("succeeded").asInt(), aliRes.toString());
        assertEquals("alibaba", aliRes.path("items").get(0).path("platform").asText());
        JsonNode row = task(p1, a);
        assertTrue(row.hasNonNull("alibabaAt"));
        assertEquals(3, row.path("status").asInt());

        // 独立站缺俄文不算完成；标不发布后完成，撤销后回退
        confirm(a, file("s.en.md", marketing("ITCSiemens", "6ES7214-1AG40-0XB0", "en", EN_BODY)));
        assertEquals(3, stage(p1, a), "独立站缺俄文");
        JsonNode micSkip = call(put(TASKS + "/products/" + p1 + "/skips/mic"), a);
        assertEquals("PLATFORM_NOT_ENABLED", micSkip.path("data").path("errorCode").asText(), "没启用的平台不能标不发布");
        ok(call(put(TASKS + "/products/" + p1 + "/skips/site"), a));
        ok(call(put(TASKS + "/products/" + p1 + "/skips/site"), a));
        assertEquals(4, stage(p1, a));
        assertEquals("it_content_admin", task(p1, a).path("skips").path("site").path("markedBy").asText());
        assertEquals(1, jdbc.queryForObject("select count(*) from product_marketing_skip where product_id = ?", Integer.class, p1),
                "重复标记不新增");
        ok(call(delete(TASKS + "/products/" + p1 + "/skips/site"), a));
        assertEquals(3, stage(p1, a));
        assertFalse(task(p1, a).path("skips").has("site"));
        ok(call(put(TASKS + "/products/" + p1 + "/skips/site"), a));
        assertEquals(4, stage(p1, a), "撤销后可以再标记");
        JsonNode counts = ok(call(json(post(TASKS + "/counts"), write(Map.of("brandId", siemens))), a));
        assertEquals(1, counts.path("4").asInt());
        // 标了不发布照样能写入，标记保留
        confirm(a, file("s.ru.md", marketing("ITCSiemens", "6ES7214-1AG40-0XB0", "ru", "## SEO 标题\nКупить\n")));
        assertTrue(task(p1, a).path("skips").has("site"));

        // 商品内容：启用平台、各平台内容、关键词按档位排序、不发布标记
        JsonNode detail = ok(call(get(TASKS + "/products/" + p1), a));
        assertEquals("[\"alibaba\",\"site\"]", detail.path("platforms").toString());
        assertEquals(4, detail.path("status").asInt());
        JsonNode aliContent = detail.path("platformContents").path("alibaba");
        assertEquals("Siemens 6ES7214-1AG40-0XB0 CPU 1214C PLC, Genuine New", aliContent.path("title").asText());
        assertEquals(List.of("6ES7214-1AG40-0XB0", "CPU 1214C", "S7-1200 CPU", "siemens plc price"),
                List.of(aliContent.path("keywords").get(0).path("keyword").asText(), aliContent.path("keywords").get(1).path("keyword").asText(),
                        aliContent.path("keywords").get(2).path("keyword").asText(), aliContent.path("keywords").get(3).path("keyword").asText()));
        assertEquals(1, aliContent.path("keywords").get(0).path("tier").asInt());
        assertEquals(5, aliContent.path("sellingPoints").size());
        assertEquals("a.alibaba.md", aliContent.path("fileName").asText());
        assertTrue(detail.path("skips").has("site"));

        // 再次上传：跳过的标题保留，关键词库整份替换
        Map<String, Object> second = file("a2.alibaba.md", platformMd("alibaba", "6ES7214-1AG40-0XB0", "en", """
                ## 标题
                New title

                ## 关键词库
                | only keyword | 长尾 |
                """));
        second.put("skip", List.of("TITLE"));
        confirm(a, second);
        JsonNode aliAgain = ok(call(get(TASKS + "/products/" + p1), a)).path("platformContents").path("alibaba");
        assertEquals("Siemens 6ES7214-1AG40-0XB0 CPU 1214C PLC, Genuine New", aliAgain.path("title").asText(), "跳过的块保留");
        assertEquals(1, aliAgain.path("keywords").size());
        assertEquals(3, aliAgain.path("keywords").get(0).path("tier").asInt());
        assertEquals(5, aliAgain.path("sellingPoints").size(), "没上传的卖点保留");

        // 按公司隔离：公司 B 看不到 A 的内容和标记，四个平台都还要写
        JsonNode bDetail = ok(call(get(TASKS + "/products/" + p1), b));
        assertEquals(4, bDetail.path("platforms").size());
        assertTrue(bDetail.path("platformContents").isEmpty() || bDetail.path("platformContents").isMissingNode());
        assertFalse(bDetail.path("skips").has("site"));
        assertEquals(3, bDetail.path("status").asInt());

        // 启用的平台都标了不发布：资料核对后即完成
        ok(call(json(post(TASKS + "/products/" + p2 + "/verify-facts"), "{}"), admin));
        for (String platform : List.of("alibaba", "mic", "site", "social")) {
            ok(call(put(TASKS + "/products/" + p2 + "/skips/" + platform), b));
        }
        assertEquals(4, stage(p2, b));
        assertEquals(3, stage(p2, a), "不发布只影响本公司");

        // 关掉平台后：该平台不计进度、内容不返回，已写入的内容保留
        ok(call(json(put(TASKS + "/platforms"), write(Map.of("platforms", List.of("site")))), a));
        JsonNode siteOnly = ok(call(get(TASKS + "/products/" + p1), a));
        assertEquals("[\"site\"]", siteOnly.path("platforms").toString());
        assertTrue(siteOnly.path("platformContents").path("alibaba").isMissingNode());
        assertEquals(1, jdbc.queryForObject("select count(*) from product_platform_content where product_id = ? and deleted_at is null",
                Integer.class, p1));

        // 没有维护权限：能看平台设置，不能改、不能标不发布
        loginWithResources("it_content_viewer", 100100);
        String viewer = token("it_content_viewer");
        ok(call(get(TASKS + "/platforms"), viewer));
        assertEquals(403, perform(json(put(TASKS + "/platforms"), write(Map.of("platforms", List.of("site")))), viewer).getStatus());
        assertEquals(403, perform(put(TASKS + "/products/" + p1 + "/skips/site"), viewer).getStatus());
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

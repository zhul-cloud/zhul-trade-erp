package com.zhul.erp.modules.product.content;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.common.constants.RedisKeyConstants;
import com.zhul.erp.modules.inquiry.InquiryContractSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * spec product/product-content「商品内容规范 v2」「资料待确认项」「关键词总库」与确认写入、商品运营详情；
 * product/product「商品规格参数」的证据列。样例为技能对 Delta VFD015M21A 的实际输出按 v2 整理。
 */
class ContentSchemaV2ContractTest extends InquiryContractSupport {

    private static final String TASKS = "/api/v1/product/content-tasks";
    private static final String PRODUCTS = "/api/v1/product/products";
    private static final String BRAND = "ITCV2Delta";

    private String admin;
    private long brand;
    private long product;

    @BeforeEach
    void seed() throws Exception {
        cleanup();
        jdbc.update("insert into product_brand (tenant_id, brand_name, status) values (0, ?, 1)", BRAND);
        brand = jdbc.queryForObject("select id from product_brand where brand_name = ?", Long.class, BRAND);
        jdbc.update("insert into product_category (tenant_id, category_code, category_name, category_name_zh, status) "
                + "values (0, 'itc-v2-vfd', 'VFD', '变频器', 1)");
        long category = jdbc.queryForObject("select id from product_category where category_code = 'itc-v2-vfd'", Long.class);
        jdbc.update("insert into product_series (tenant_id, brand_id, series_name, status) values (0, ?, 'VFD-M Series', 1)", brand);
        loginAsAdmin("it_v2_admin");
        admin = token("it_v2_admin");
        product = ok(call(json(post(PRODUCTS), write(Map.of("brandId", brand, "categoryId", category,
                "mpnRaw", "VFD015M21A"))), admin)).path("id").asLong();
    }

    @AfterEach
    void cleanup() {
        String products = "(select id from product where brand_id in (select id from product_brand where brand_name = '" + BRAND + "'))";
        for (String t : new String[] {"product_content_import", "product_content_task", "product_fact_task", "product_seo",
                "product_seo_faq", "product_faq", "product_platform_content", "product_platform_keyword", "product_marketing_skip",
                "product_keyword", "product_keyword_use", "product_fact_note", "product_specification", "product_application",
                "product_document", "product_locale", "product_logistics", "product_customs"}) {
            jdbc.update("delete from " + t + " where product_id in " + products);
        }
        jdbc.update("delete from product_relationship_note where relationship_id in (select id from product_relationship where product_id in "
                + products + ")");
        jdbc.update("delete from product_relationship where product_id in " + products);
        jdbc.update("delete from product where brand_id in (select id from product_brand where brand_name = ?)", BRAND);
        jdbc.update("delete from product_series where brand_id in (select id from product_brand where brand_name = ?)", BRAND);
        jdbc.update("delete from product_brand where brand_name = ?", BRAND);
        jdbc.update("delete from product_category where category_code = 'itc-v2-vfd'");
        jdbc.update("delete from sys_config where config_key = 'product.content.platforms' and tenant_id <> 0");
        jdbc.update("update sys_config set config_value = 'alibaba,mic,site,social' where tenant_id = 0 "
                + "and config_key = 'product.content.platforms'");
    }

    private static String sample(String name) throws IOException {
        try (InputStream in = ContentSchemaV2ContractTest.class.getResourceAsStream("/product-content/v2/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("brand: Delta", "brand: " + BRAND);
        }
    }

    private static Map<String, Object> file(String name, String content) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("fileName", name);
        f.put("content", content);
        return f;
    }

    private static String marketingV2(String body) {
        return "---\nschema: 2\ntype: marketing\nbrand: " + BRAND + "\nmodel: VFD015M21A\nlang: en\n---\n\n# X\n\n" + body;
    }

    private static String factsV2(String body) {
        return "---\nschema: 2\ntype: facts\nbrand: " + BRAND + "\nmodel: VFD015M21A\nlang: zh\n---\n\n# X\n\n" + body;
    }

    private JsonNode preview(String entry, Map<String, Object> file) throws Exception {
        return ok(call(json(post(TASKS + entry + "/preview"), write(Map.of("files", List.of(file)))), admin));
    }

    private JsonNode confirm(String entry, Map<String, Object> file) throws Exception {
        return ok(call(json(post(TASKS + entry + "/confirm"), write(Map.of("files", List.of(file)))), admin));
    }

    private JsonNode content() throws Exception {
        return ok(call(get(TASKS + "/products/" + product), admin));
    }

    private static JsonNode block(JsonNode item, String key) {
        for (JsonNode b : item.path("blocks")) {
            if (key.equals(b.path("section").asText())) {
                return b;
            }
        }
        throw new AssertionError("没有块 " + key + "：" + item);
    }

    private static JsonNode part(JsonNode items, String part) {
        for (JsonNode n : items) {
            if (part.equals(n.path("part").asText())) {
                return n;
            }
        }
        throw new AssertionError("没有项 " + part + "：" + items);
    }

    private static List<String> texts(JsonNode arr, String field) {
        List<String> result = new ArrayList<>();
        arr.forEach(n -> result.add(n.path(field).asText()));
        return result;
    }

    private static List<String> values(JsonNode arr) {
        List<String> result = new ArrayList<>();
        arr.forEach(n -> result.add(n.asText()));
        return result;
    }

    @Test
    void factsV2_evidence_notes_sources_identity_andReplace() throws Exception {
        JsonNode items = preview("/facts", file("Delta_VFD015M21A_商品资料.md", sample("facts-VFD015M21A.md")));
        assertEquals(1, items.size());
        JsonNode p = items.get(0);
        assertTrue(p.path("confirmable").asBoolean(), p.toString());
        assertEquals(2, p.path("schema").asInt());
        JsonNode specs = block(p, "SPECS");
        assertEquals(List.of("项目", "数值及单位", "条件/范围", "证据状态", "来源"), values(specs.path("columns")));
        assertEquals("S03 项目原始铭牌照片", specs.path("rows").get(2).get(4).asText(), specs.toString());
        assertTrue(block(p, "SOURCES").path("hints").toString().contains("本地路径 3 条不导入"), block(p, "SOURCES").toString());
        assertTrue(block(p, "UNKNOWNS").path("hints").get(0).asText().contains("整份替换"));

        JsonNode result = confirm("/facts", file("Delta_VFD015M21A_商品资料.md", sample("facts-VFD015M21A.md")));
        assertEquals(1, result.path("succeeded").asInt(), result.toString());

        // 规格带证据，未核实；手工保存时证据原样带回
        JsonNode list = ok(call(get(PRODUCTS + "/" + product + "/specifications"), admin));
        assertEquals(7, list.size());
        JsonNode current = list.get(3);
        assertEquals("输入相数及电流", current.path("specLabel").asText());
        assertEquals("单相 15.7 A；三相 9.0 A", current.path("specValue").asText());
        assertEquals("两种输入条件分别列示；不可与输出电流互换", current.path("conditionText").asText());
        assertEquals("实物标签确认，手册佐证", current.path("evidence").asText());
        assertTrue(current.path("sourceNote").asText().startsWith("S02 Delta 官方 VFD-M 中文手册"));
        assertEquals(0, current.path("verified").asInt());
        List<Map<String, Object>> back = new ArrayList<>();
        for (JsonNode s : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            for (String f : new String[] {"specKey", "specLabel", "specValue", "conditionText", "evidence", "sourceNote"}) {
                m.put(f, s.path(f).asText());
            }
            m.put("verified", s.path("specLabel").asText().equals("输入相数及电流") ? 1 : 0);
            back.add(m);
        }
        JsonNode saved = ok(call(json(put(PRODUCTS + "/" + product + "/specifications"), write(Map.of("items", back))), admin));
        assertEquals("实物标签确认，手册佐证", saved.get(3).path("evidence").asText());
        assertEquals(1, saved.get(3).path("verified").asInt());

        // 资料待确认项：关键未知字段 4、冲突与待确认 4
        JsonNode notes = content().path("factNotes");
        assertEquals(8, notes.size(), notes.toString());
        assertEquals(1, notes.get(0).path("kind").asInt());
        assertEquals("当前可售数量、成色、测试记录、保修", notes.get(0).path("item").asText());
        assertEquals(2, notes.get(4).path("kind").asInt());

        // 来源网页链接与技术资料按地址合并，本地路径不导入；名称补空，系列按系列库匹配
        assertEquals(2, jdbc.queryForObject("select count(*) from product_document where product_id = ? and deleted_at is null",
                Integer.class, product));
        Map<String, Object> row = jdbc.queryForMap("select p.product_name, s.series_name from product p "
                + "left join product_series s on s.id = p.series_id where p.id = ?", product);
        assertEquals("台达 VFD-M 变频器", row.get("product_name"));
        assertEquals("VFD-M Series", row.get("series_name"));
        assertEquals(3, jdbc.queryForObject("select count(*) from product_application where product_id = ? and deleted_at is null",
                Integer.class, product));

        // 再导入只含 2 项关键未知字段的资料包：待确认项变为这 2 项（冲突与待确认清空）
        JsonNode again = confirm("/facts", file("again.md", factsV2("""
                ## 关键未知字段

                | 项目 | 当前状态 | 对发布的影响 |
                | --- | --- | --- |
                | 净重 | 未称重 | 不填物流重量 |
                | 认证 | 未获得 | 不写认证 |
                """)));
        assertEquals(1, again.path("succeeded").asInt(), again.toString());
        assertEquals(List.of("净重", "认证"), texts(content().path("factNotes"), "item"));
        // 已核实的规格保留
        assertEquals(1, ok(call(get(PRODUCTS + "/" + product + "/specifications"), admin)).get(3).path("verified").asInt());
    }

    @Test
    void marketingV2_combinedFile_writesLibraryAndEveryPlatform() throws Exception {
        String sample = sample("marketing-VFD015M21A.md");
        JsonNode items = preview("/marketing", file("Delta_VFD015M21A_营销内容.md", sample));
        assertEquals(List.of("keywords", "alibaba", "mic", "site", "social"), texts(items, "part"));
        items.forEach(i -> assertTrue(i.path("confirmable").asBoolean(), i.toString()));
        int librarySize = block(part(items, "keywords"), "keywords:KEYWORD_LIBRARY").path("count").asInt();
        assertTrue(librarySize > 10);
        assertTrue(block(part(items, "alibaba"), "alibaba:TITLE").path("rows").get(0).get(1).asText().startsWith("推荐"));

        JsonNode result = confirm("/marketing", file("Delta_VFD015M21A_营销内容.md", sample));
        assertEquals(5, result.path("succeeded").asInt(), result.toString());

        JsonNode c = content();
        JsonNode library = c.path("keywordLibrary");
        assertEquals(librarySize, library.size());
        assertEquals("Delta VFD015M21A", library.get(0).path("keyword").asText());
        assertEquals(1, library.get(0).path("tier").asInt());
        assertFalse(library.get(0).path("category").asText().isEmpty());
        assertFalse(library.get(0).path("intent").asText().isEmpty());
        assertTrue(values(library.get(0).path("platforms")).containsAll(List.of("alibaba", "mic")),
                library.get(0).toString());
        JsonNode ali = c.path("platformContents").path("alibaba");
        assertEquals("Delta VFD015M21A VFD-M AC Motor Drive 1.5 kW 2 HP 200-240 V Input", ali.path("title").asText());
        assertEquals(3, ali.path("titleAlternates").size());
        assertEquals("输出参数", ali.path("titleAlternates").get(0).path("angle").asText());
        assertEquals(7, ali.path("attributes").size());
        assertEquals(7, ali.path("faqs").size());
        assertTrue(ali.path("cta").asText().startsWith("Send an inquiry"));
        assertTrue(ali.path("keywords").size() > 0);
        assertEquals(4, ali.path("sellingPoints").size());
        JsonNode en = c.path("langs").path("en");
        assertEquals("Delta VFD015M21A VFD-M AC Motor Drive", en.path("h1").asText());
        assertEquals("delta-vfd015m21a-vfd-m-ac-motor-drive", en.path("urlSlug").asText());
        assertEquals(2, en.path("altTexts").size());
        assertEquals(7, en.path("faqs").size());
        JsonNode social = c.path("platformContents").path("social");
        assertEquals(5, social.path("posts").size());
        assertEquals("LinkedIn", social.path("posts").get(0).path("channel").asText());
        assertEquals(2, social.path("videos").size());
        assertTrue(social.path("videos").get(0).path("markdown").asText().contains("| 预计时间 |"));
        // 上传记录每项一条
        assertEquals(5, ok(call(get(TASKS + "/products/" + product + "/imports").param("type", "marketing"), admin)).size());
        // 四个平台都写过
        assertTrue(c.hasNonNull("alibabaAt") && c.hasNonNull("micAt") && c.hasNonNull("enAt") && c.hasNonNull("socialAt"));

        // 其他公司看不到本公司的关键词与营销内容
        String token = jwtUtils.generateToken(Map.of("tenantId", 1007), "it_v2_admin", 3600);
        redis.opsForValue().set(RedisKeyConstants.TOKEN_PREFIX + token, "1");
        JsonNode other = ok(call(get(TASKS + "/products/" + product), token));
        assertEquals(0, other.path("keywordLibrary").size());
        assertEquals(0, other.path("platformContents").size());
    }

    @Test
    void marketingV2_disabledPlatformItemRejected_othersWritten_skipKey() throws Exception {
        ok(call(json(put(TASKS + "/platforms"), write(Map.of("platforms", List.of("alibaba", "site")))), admin));
        String sample = sample("marketing-VFD015M21A.md");
        Map<String, Object> f = file("all.md", sample);
        f.put("skip", List.of("alibaba:FAQ"));
        JsonNode items = preview("/marketing", f);
        assertFalse(part(items, "mic").path("confirmable").asBoolean());
        assertFalse(part(items, "social").path("confirmable").asBoolean());
        assertTrue(block(part(items, "alibaba"), "alibaba:FAQ").path("skipped").asBoolean());
        assertFalse(block(part(items, "site"), "site:FAQ").path("skipped").asBoolean(), "跳过键只对本项生效");

        JsonNode result = confirm("/marketing", f);
        assertEquals(3, result.path("succeeded").asInt(), result.toString());
        assertEquals(2, result.path("failed").asInt());
        JsonNode c = content();
        assertEquals(0, c.path("platformContents").path("alibaba").path("faqs").size(), "跳过的块不写入");
        assertFalse(c.path("platformContents").has("mic"));
    }

    @Test
    void keywordPicks_singlePlatformFile_libraryReplace_legacyMerge() throws Exception {
        confirm("/marketing", file("all.md", sample("marketing-VFD015M21A.md")));
        JsonNode before = content();
        int aliPicks = before.path("platformContents").path("alibaba").path("keywords").size();
        int librarySize = before.path("keywordLibrary").size();

        // 只含中国制造网的 v2 文件，选用词不在库里：提示并按主要档补进总库；阿里的选用不变
        String micOnly = marketingV2("""
                ## 中国制造网

                ### 选用关键词

                - Delta VFD015M21A
                - VFD015M21A spare drive
                """);
        JsonNode p = preview("/marketing", file("mic.md", micOnly)).get(0);
        assertTrue(block(p, "mic:KEYWORD_PICKS").path("hints").toString().contains("1 个词不在关键词库里"), p.toString());
        assertEquals(1, confirm("/marketing", file("mic.md", micOnly)).path("succeeded").asInt());
        JsonNode c = content();
        assertEquals(librarySize + 1, c.path("keywordLibrary").size());
        assertTrue(texts(c.path("keywordLibrary"), "keyword").contains("VFD015M21A spare drive"));
        assertEquals(List.of("Delta VFD015M21A", "VFD015M21A spare drive"),
                texts(c.path("platformContents").path("mic").path("keywords"), "keyword"));
        assertEquals(aliPicks, c.path("platformContents").path("alibaba").path("keywords").size());

        // 替换总库为两个词，只含独立站章节：不在新库里的词连同阿里的选用一起去掉
        String small = marketingV2("""
                ## 关键词库

                | 优先级 | 分类 | Keyword | 搜索意图 | 事实依据 |
                | --- | --- | --- | --- | --- |
                | P1 | 核心词 | Delta VFD015M21A | 精确型号 | F01 |
                | P3 | 长尾 | VFD015M21A spare drive | 备件 | F01 |

                ## 独立站

                ### 首屏定义块

                Delta VFD015M21A is a VFD-M drive.
                """);
        JsonNode sp = preview("/marketing", file("small.md", small));
        assertTrue(block(part(sp, "keywords"), "keywords:KEYWORD_LIBRARY").path("hints").get(0).asText().contains("去掉"),
                sp.toString());
        assertEquals(2, confirm("/marketing", file("small.md", small)).path("succeeded").asInt());
        c = content();
        assertEquals(List.of("Delta VFD015M21A", "VFD015M21A spare drive"), texts(c.path("keywordLibrary"), "keyword"));
        assertEquals(3, c.path("keywordLibrary").get(1).path("tier").asInt());
        assertEquals(List.of("Delta VFD015M21A"), texts(c.path("platformContents").path("alibaba").path("keywords"), "keyword"));
        assertEquals(2, c.path("platformContents").path("mic").path("keywords").size());

        // 旧格式阿里国际站包：合并进总库（同词档位取最高），整份替换阿里的选用
        String legacy = "---\ntype: marketing\nplatform: alibaba\nbrand: " + BRAND + "\nmodel: VFD015M21A\nlang: en\n---\n\n"
                + "## 关键词库\n| 关键词 | 档位 |\n|---|---|\n| delta vfd015m21a | 长尾 |\n| VFD015M21A price | 长尾 |\n";
        JsonNode lp = preview("/marketing", file("old.md", legacy)).get(0);
        assertTrue(lp.path("notes").get(0).asText().contains("旧格式"));
        assertEquals(1, confirm("/marketing", file("old.md", legacy)).path("succeeded").asInt());
        c = content();
        assertEquals(List.of("Delta VFD015M21A", "VFD015M21A spare drive", "VFD015M21A price"),
                texts(c.path("keywordLibrary"), "keyword"));
        assertEquals(1, c.path("keywordLibrary").get(0).path("tier").asInt(), "同词档位取最高");
        assertEquals(List.of("Delta VFD015M21A", "VFD015M21A price"),
                texts(c.path("platformContents").path("alibaba").path("keywords"), "keyword"));
    }
}

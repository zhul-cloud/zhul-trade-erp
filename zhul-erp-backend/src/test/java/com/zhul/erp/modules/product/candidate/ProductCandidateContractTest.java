package com.zhul.erp.modules.product.candidate;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.common.constants.RedisKeyConstants;
import com.zhul.erp.modules.inquiry.InquiryContractSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
 * spec product/product-candidate、inquiry/inquiry-intake「确认询盘时自动建档」、inquiry/sourcing-quote「采购回填真实型号」、
 * product/product「商品列表展示需求热度」与写入限制的候选审核例外。
 */
class ProductCandidateContractTest extends InquiryContractSupport {

    private static final String CAND = "/api/v1/product/candidates";
    private static final String PRODUCTS = "/api/v1/product/products";

    private String admin;
    private long siemens;
    private long servo;
    private long contactor;

    @BeforeEach
    void seed() {
        cleanup();
        siemens = brand("ITSiemens");
        brand("ITABB");
        brand("ITSchneider");
        servo = category("it-servo", "AC Servo", "伺服");
        contactor = category("it-contactor", "Contactor", "接触器");
    }

    @AfterEach
    void cleanup() {
        jdbc.update("delete from product_candidate_source");
        jdbc.update("delete from product_candidate");
        jdbc.update("delete from product where brand_id in (select id from product_brand where brand_name like 'IT%' or brand_name = 'Hengstler')");
        jdbc.update("delete from product_brand_alias where brand_id in (select id from product_brand where brand_name like 'IT%' or brand_name = 'Hengstler')");
        jdbc.update("delete from product_series where brand_id in (select id from product_brand where brand_name like 'IT%' or brand_name = 'Hengstler')");
        jdbc.update("delete from product_brand where brand_name like 'IT%' or brand_name = 'Hengstler'");
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

    private static Map<String, Object> row(String brand, String category, String model) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("brand", brand);
        r.put("category", category);
        r.put("confirmedModel", model);
        r.put("quantity", 1);
        r.put("unit", "个");
        r.put("description", model + " 中文描述");
        r.put("descriptionEn", model + " description");
        return r;
    }

    private long confirmInquiry(List<Map<String, Object>> rows) throws Exception {
        long c = customer("Wingrid Rocha", "Brazil");
        long id = ok(call(json(post(INQ), "{\"customerId\":" + c + ",\"source\":1,\"rawContent\":\"rfq\"}"), admin)).path("id").asLong();
        ok(call(json(post(INQ + "/" + id + "/confirm"), write(Map.of("rows", rows))), admin));
        return id;
    }

    private long item(long inquiryId, String model) {
        return jdbc.queryForObject("select id from inquiry_item where customer_inquiry_id = ? and confirmed_model = ?", Long.class, inquiryId, model);
    }

    private int archive(long itemId) {
        return jdbc.queryForObject("select archive_status from inquiry_item where id = ?", Integer.class, itemId);
    }

    private long candidateOf(String mpn) {
        return jdbc.queryForObject("select id from product_candidate where mpn_raw = ? and deleted_at is null", Long.class, mpn);
    }

    private void assignAll(long inquiryId) throws Exception {
        List<Long> tasks = jdbc.queryForList("select id from sourcing_task where customer_inquiry_id = ?", Long.class, inquiryId);
        ok(call(json(post(BOARD + "/assign"), write(Map.of("taskIds", tasks, "assigneeId", BUYER_LIN))), admin));
    }

    private JsonNode save(long itemId, boolean submit, Map<String, Object> extra) throws Exception {
        long task = jdbc.queryForObject("select sourcing_task_id from inquiry_item where id = ?", Long.class, itemId);
        Map<String, Object> it = new LinkedHashMap<>();
        it.put("itemId", itemId);
        it.putAll(extra);
        return call(json(put(MY + "/" + task + "/quotes"), write(Map.of("submit", submit, "items", List.of(it)))), token("it_lin"));
    }

    @Test
    void confirm_matchOrPoolOrNeedModel_thenBackfillUpgradeApproveRejectBatch() throws Exception {
        loginAsAdmin("it_cand_admin");
        admin = token("it_cand_admin");
        // 商品库已有 ITSiemens / 6ES7214-1AG40-0XB0
        long existing = ok(call(json(post(PRODUCTS), write(Map.of("brandId", siemens, "categoryId", servo, "mpnRaw", "6ES7214-1AG40-0XB0"))),
                admin)).path("id").asLong();

        long inq = confirmInquiry(List.of(row("ITSiemens", "PLC", "6ES7 214-1AG40-0XB0"), row("ITABB", "接触器", "Contactor, 32A"),
                row("ITSchneider", "伺服驱动器", "LXM32AD30N4"), row("Hengstler", "编码器", "RI58-O/1024ER")));
        long siemensItem = item(inq, "6ES7 214-1AG40-0XB0");
        long abbItem = item(inq, "Contactor, 32A");
        long lxmItem = item(inq, "LXM32AD30N4");
        assertEquals(1, archive(siemensItem), "商品库已有：已建档");
        assertEquals(existing, jdbc.queryForObject("select product_id from inquiry_item where id = ?", Long.class, siemensItem));
        assertEquals(3, archive(abbItem), "不是型号：待回填真实型号");
        assertEquals(2, archive(lxmItem), "进候选池");
        long lxm = candidateOf("LXM32AD30N4");
        assertEquals(servo, jdbc.queryForObject("select category_id from product_candidate where id = ?", Long.class, lxm), "按品类文字建议一级品类");
        long hengstler = candidateOf("RI58-O/1024ER");
        assertEquals(0, jdbc.queryForObject("select count(*) from product_candidate where id = ? and brand_id is not null", Integer.class, hengstler),
                "品牌没识别也进池");

        JsonNode detail = ok(call(get(INQ + "/" + inq), admin));
        JsonNode abbVo = null;
        for (JsonNode i : detail.path("items")) {
            if (i.path("confirmedModel").asText().equals("Contactor, 32A")) {
                abbVo = i;
            }
        }
        assertEquals("待回填真实型号", abbVo.path("archiveStatusName").asText());

        // 采购回填真实型号 → 进池；提交有货回价 → 可信度提升
        assignAll(inq);
        ok(save(abbItem, false, Map.of("actualModel", "AX32-30-10-80", "quotes", List.of())));
        assertEquals(2, archive(abbItem));
        long ax = candidateOf("AX32-30-10-80");
        assertEquals(contactor, jdbc.queryForObject("select category_id from product_candidate where id = ?", Long.class, ax));
        assertEquals(2, jdbc.queryForObject("select source_type from product_candidate_source where candidate_id = ? and deleted_at is null",
                Integer.class, ax), "来源为采购回填真实型号");
        // 单独保存真实型号的接口：清空回到待回填，再填回来
        long abbTask = jdbc.queryForObject("select sourcing_task_id from inquiry_item where id = ?", Long.class, abbItem);
        String actualUrl = MY + "/" + abbTask + "/items/" + abbItem + "/actual-model";
        assertEquals("待回填真实型号", ok(call(json(put(actualUrl), "{\"actualModel\":\"\"}"), token("it_lin"))).path("archiveStatusName").asText());
        assertEquals("候选中", ok(call(json(put(actualUrl), "{\"actualModel\":\"AX32-30-10-80\"}"), token("it_lin"))).path("archiveStatusName").asText());
        assertEquals("询价任务不存在", fail(call(json(put(actualUrl), "{\"actualModel\":\"X\"}"), token("it_jiang"))).path("message").asText());
        ok(save(lxmItem, true, Map.of("quotes", List.of(Map.of("channel", 1, "shopName", "工控店", "unitPrice", 100, "itemCondition", 1)))));
        assertEquals(2, jdbc.queryForObject("select level from product_candidate where id = ?", Integer.class, lxm), "采购问到有货");

        // 列表：按更新时间倒序，每行带来源
        JsonNode page = ok(call(json(post(CAND + "/page"), "{}"), admin));
        assertEquals(3, page.path("total").asInt());
        for (int i = 1; i < page.path("records").size(); i++) {
            JsonNode a = page.path("records").get(i - 1);
            JsonNode b = page.path("records").get(i);
            assertTrue(a.path("level").asInt() > b.path("level").asInt() || a.path("level").asInt() == b.path("level").asInt()
                    && a.path("updateTime").asText().compareTo(b.path("updateTime").asText()) >= 0, "可信度从高到低，再按更新时间倒序");
        }
        assertEquals("LXM32AD30N4", page.path("records").get(0).path("mpnRaw").asText(), "可信度最高的在前");
        for (JsonNode r : page.path("records")) {
            if (r.path("mpnRaw").asText().equals("LXM32AD30N4")) {
                assertEquals(2, r.path("mineSourceCount").asInt());
                assertEquals("采购回价有货", r.path("sources").get(0).path("sourceTypeName").asText(), "最近的来源在前");
                assertFalse(r.path("sources").get(1).path("inquiryCode").asText().isEmpty(), "来源带询盘号");
            }
        }
        assertEquals("Contactor, 32A", ok(call(get(CAND + "/" + ax), admin)).path("originalModel").asText(), "回填自询盘原文");

        // 通过建档 → 回填关联、热度
        JsonNode approved = ok(call(json(post(CAND + "/" + lxm + "/approve"), write(Map.of("categoryId", servo))), admin));
        assertEquals("已建档", approved.path("statusName").asText());
        long lxmProduct = approved.path("productId").asLong();
        assertEquals("LXM32AD30N4 中文描述", jdbc.queryForObject("select product_name from product where id = ?", String.class, lxmProduct),
                "商品名称默认用中文描述");
        assertEquals(6, jdbc.queryForObject("select lifecycle_status from product where id = ?", Integer.class, lxmProduct), "待查 → 未知");
        assertEquals(lxmProduct, jdbc.queryForObject("select product_id from inquiry_item where id = ?", Long.class, lxmItem));
        assertEquals(1, archive(lxmItem));
        JsonNode hot = ok(call(get(PRODUCTS).param("sort", "inquiryCount").param("keyword", "LXM32"), admin));
        assertEquals(1, hot.path("records").get(0).path("inquiryCount").asInt());
        assertEquals("候选已审核（已建档）", fail(call(json(post(CAND + "/" + lxm + "/approve"), write(Map.of("categoryId", servo))), admin))
                .path("message").asText());

        // 生命周期：采购核实停产 → 已停产，依据自动填写
        jdbc.update("update inquiry_item set lifecycle = 2, replacement_model = 'RI58-O/2048ER' where customer_inquiry_id = ? and confirmed_model = ?",
                inq, "RI58-O/1024ER");
        JsonNode hd = ok(call(get(CAND + "/" + hengstler), admin));
        assertEquals(4, hd.path("suggestedLifecycle").asInt());
        assertEquals("采购询价核实停产，替代型号 RI58-O/2048ER", hd.path("suggestedLifecycleSource").asText());
        // 品牌没识别：审核时新建品牌
        JsonNode newBrand = ok(call(json(post(CAND + "/" + hengstler + "/approve"), write(Map.of("brandMode", "NEW", "brandName", "Hengstler",
                "categoryId", servo, "newSeriesName", "RI58"))), admin));
        assertEquals("已建档", newBrand.path("statusName").asText());
        assertEquals(1, jdbc.queryForObject("select count(*) from product_series where series_name = 'RI58'", Integer.class), "审核时新建系列");
        assertEquals(4, jdbc.queryForObject("select lifecycle_status from product where id = ?", Integer.class, newBrand.path("productId").asLong()));

        // 驳回 → 待回填；重新打开 → 候选中；批量通过
        assertEquals("选择「其他」时请填写说明", fail(call(json(post(CAND + "/" + ax + "/reject"), "{\"reason\":3}"), admin)).path("message").asText());
        ok(call(json(post(CAND + "/" + ax + "/reject"), "{\"reason\":1}"), admin));
        assertEquals(3, archive(abbItem));
        ok(call(post(CAND + "/" + ax + "/reopen"), admin));
        assertEquals(2, archive(abbItem));
        JsonNode batch = ok(call(json(post(CAND + "/batch-approve"), write(Map.of("ids", List.of(ax, lxm)))), admin));
        assertEquals(1, batch.path("approved").asInt());
        assertEquals("已审核", batch.path("skipped").get(0).path("reason").asText());
        assertEquals(1, archive(abbItem));
    }

    @Test
    void tenantSeesOnlyOwnSources_andCanArchiveViaReview() throws Exception {
        loginAsAdmin("it_cand_admin");
        // 候选来自两家公司：1001 有 2 条，1002 有 1 条
        jdbc.update("insert into product_candidate (tenant_id, brand_id, brand_text, brand_key, mpn_raw, mpn_normalized, category_id, status, level, "
                + "source_count) values (0, ?, 'ITSiemens', ?, '6ES7215-1AG40-0XB0', '6ES72151AG400XB0', ?, 1, 1, 3)", siemens, "#" + siemens, servo);
        long c = candidateOf("6ES7215-1AG40-0XB0");
        jdbc.update("insert into product_candidate (tenant_id, brand_text, brand_key, mpn_raw, mpn_normalized, status, level, source_count) "
                + "values (0, 'ITABB', 'itabb', 'AX25-30-10-80', 'AX25301080', 1, 1, 1)");
        long other = candidateOf("AX25-30-10-80");
        for (Object[] s : new Object[][] {{1001, c}, {1001, c}, {1002, c}, {1002, other}}) {
            jdbc.update("insert into product_candidate_source (tenant_id, candidate_id, source_type) values (?, ?, 1)", s[0], s[1]);
        }
        String t1001 = tenantToken("it_cand_admin", 1001);
        JsonNode page = ok(call(json(post(CAND + "/page"), "{}"), t1001));
        assertEquals(1, page.path("total").asInt(), "只看到有本公司来源的候选");
        JsonNode d = ok(call(get(CAND + "/" + c), t1001));
        assertEquals(2, d.path("sources").size());
        assertEquals(1, d.path("otherSourceCount").asInt());
        assertEquals("候选不存在", fail(call(get(CAND + "/" + other), t1001)).path("message").asText());

        // 租户账号直接新建商品仍被拒绝，经候选审核可以建档
        JsonNode direct = call(json(post(PRODUCTS), write(Map.of("brandId", siemens, "categoryId", servo, "mpnRaw", "X-1"))), t1001);
        assertEquals("PLATFORM_ADMIN_REQUIRED", direct.path("data").path("errorCode").asText());
        JsonNode approved = ok(call(json(post(CAND + "/" + c + "/approve"), write(Map.of("categoryId", servo))), t1001));
        assertEquals("已建档", approved.path("statusName").asText());
        assertTrue(approved.path("productId").asLong() > 0);
        assertFalse(call(json(post(PRODUCTS), write(Map.of("brandId", siemens, "categoryId", servo, "mpnRaw", "X-2"))), t1001)
                .path("code").asInt() == 0, "审核结束后例外不残留");
    }

    @Test
    void reviewRequiresPermission() throws Exception {
        String viewer = token("it_lin");
        jdbc.update("insert into role_resource (role_code, resource_id, resource_code) values (?, 100099, '')", BUYER_ROLE);
        jdbc.update("insert into product_candidate (tenant_id, brand_id, brand_text, brand_key, mpn_raw, mpn_normalized, category_id, status, level, "
                + "source_count) values (0, ?, 'ITSiemens', ?, '6ES7215-1AG40-0XB0', '6ES72151AG400XB0', ?, 1, 1, 0)", siemens, "#" + siemens, servo);
        long c = candidateOf("6ES7215-1AG40-0XB0");
        assertEquals(0, call(json(post(CAND + "/page"), "{}"), viewer).path("code").asInt(), "有菜单可以查看");
        assertEquals(403, perform(json(post(CAND + "/" + c + "/approve"), write(Map.of("categoryId", servo))), viewer).getStatus());
    }

    private static JsonNode fail(JsonNode res) {
        assertFalse(res.path("code").asInt() == 0, res.toString());
        return res;
    }
}

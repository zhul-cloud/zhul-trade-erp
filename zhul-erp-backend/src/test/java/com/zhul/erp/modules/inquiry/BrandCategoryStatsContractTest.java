package com.zhul.erp.modules.inquiry;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** 询价统计 → 品牌品类（spec inquiry/sourcing-stats「品牌品类统计」） */
class BrandCategoryStatsContractTest extends InquiryContractSupport {

    private static final String API = "/api/v1/inquiry/sourcing-stats/brand-category";

    @AfterEach
    void cleanupCategories() {
        jdbc.update("delete from product_category where category_code like 'itb\\_%' and parent_id is not null");
        jdbc.update("delete from product_category where category_code like 'itb\\_%'");
    }

    private long category(String code, String zh, Long parentId) {
        jdbc.update("insert into product_category (tenant_id, category_code, category_name, category_name_zh, parent_id, status, create_by) "
                + "values (0, ?, ?, ?, ?, 1, 'itb')", code, code, zh, parentId);
        return jdbc.queryForObject("select id from product_category where tenant_id = 0 and category_code = ?", Long.class, code);
    }

    private long inquiry(String code, String date, int status) {
        jdbc.update("insert into customer_inquiry (tenant_id, inquiry_code, inquiry_date, quote_deadline, status) values (0, ?, ?, ?, ?)",
                code, date, date, status);
        return jdbc.queryForObject("select id from customer_inquiry where tenant_id = 0 and inquiry_code = ?", Long.class, code);
    }

    private long task(String code, long inquiryId, String firstAssignedAt) {
        jdbc.update("insert into sourcing_task (tenant_id, task_code, customer_inquiry_id, brand, brand_key, status, first_assigned_at) "
                + "values (0, ?, ?, 'Siemens', 'siemens', 2, ?)", code, inquiryId, firstAssignedAt);
        return jdbc.queryForObject("select id from sourcing_task where tenant_id = 0 and task_code = ?", Long.class, code);
    }

    private long item(long inquiryId, int line, String brand, String category, Long categoryId, int quoteStatus, Long taskId) {
        jdbc.update("insert into inquiry_item (tenant_id, customer_inquiry_id, line_no, brand, brand_key, category, category_id, "
                        + "original_model, quantity, quote_status, sourcing_task_id) values (0, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?)",
                inquiryId, line, brand, brand.toLowerCase(), category, categoryId, "M" + inquiryId + "-" + line, quoteStatus, taskId);
        return jdbc.queryForObject("select id from inquiry_item where customer_inquiry_id = ? and line_no = ?", Long.class, inquiryId, line);
    }

    private void itemWithKey(long inquiryId, int line, String brand, String brandKey) {
        jdbc.update("insert into inquiry_item (tenant_id, customer_inquiry_id, line_no, brand, brand_key, original_model, quantity, quote_status) "
                + "values (0, ?, ?, ?, ?, ?, 1, 2)", inquiryId, line, brand, brandKey, "K" + inquiryId + "-" + line);
    }

    private void quote(long itemId, long inquiryId, long taskId, String quotedAt, int status) {
        jdbc.update("insert into sourcing_quote (tenant_id, inquiry_item_id, customer_inquiry_id, task_id, brand, brand_key, model, model_key, "
                        + "channel, currency_code, no_stock, quoted_by, quoted_at, status) values (0, ?, ?, ?, 'Siemens', 'siemens', 'M', 'm', 2, 'CNY', 0, ?, ?, ?)",
                itemId, inquiryId, taskId, BUYER_LIN, quotedAt, status);
    }

    private static void eq(String expected, JsonNode n) {
        assertEquals(0, new BigDecimal(expected).compareTo(n.decimalValue()), "期望 " + expected + "，实际 " + n);
    }

    private static JsonNode named(JsonNode rows, String name) {
        for (JsonNode r : rows) {
            if (name.equals(r.path("name").asText())) {
                return r;
            }
        }
        throw new AssertionError("没有 " + name + "：" + rows);
    }

    @Test
    void quarter_byBrandAndRootCategory_rateAndAvgHours_excludesCancelledAndNotSourcing() throws Exception {
        long plc = category("itb_plc", "仝己PLC", null);
        long cpu = category("itb_cpu", "仝己CPU模块", plc);

        long a = inquiry("ITB-A", "2026-08-10", 7);
        long t = task("ITB-A-T", a, "2026-08-10 10:00:00");
        long s1 = item(a, 1, "Siemens", "", cpu, 2, t);
        long s2 = item(a, 2, "Siemens", "", null, 2, t);
        long s3 = item(a, 3, "Siemens", "", null, 3, t);
        item(a, 4, "Siemens", "仝己PLC", null, 1, t);
        item(a, 5, "", "", null, 2, null);
        quote(s1, a, t, "2026-08-10 12:00:00", 2);
        quote(s2, a, t, "2026-08-10 10:30:00", 4);
        quote(s2, a, t, "2026-08-10 18:00:00", 2);
        quote(s2, a, t, "2026-08-10 16:00:00", 2);
        quote(s3, a, t, "2026-08-10 09:00:00", 2);
        item(inquiry("ITB-B", "2026-09-30", 10), 1, "Siemens", "", null, 2, null);
        item(inquiry("ITB-C", "2026-06-15", 7), 1, "Siemens", "", null, 2, null);
        item(inquiry("ITB-D", "2026-07-01", 3), 1, "Siemens", "", null, 1, null);
        loginAsAdmin("it_bc_admin");
        String admin = token("it_bc_admin");

        JsonNode r = ok(call(get(API).param("period", "QUARTER").param("date", "2026-08-15"), admin));
        assertEquals("2026-07-01", r.path("dateFrom").asText());
        assertEquals("2026-09-30", r.path("dateTo").asText());
        JsonNode tot = r.path("totals");
        assertEquals(5, tot.path("items").asInt(), "已取消、还没进询价的询盘不算：" + r);
        assertEquals(3, tot.path("priced").asInt());
        assertEquals(1, tot.path("noStock").asInt());
        assertEquals(1, tot.path("pending").asInt());
        eq("60.00", tot.path("pricedRate"));
        // 120、360（作废的不算，取最早的已提交）、0（早于分配）分钟，平均 160 分钟
        eq("2.7", tot.path("avgHours"));
        assertEquals(1, r.path("previous").path("items").asInt(), "上一季度");

        JsonNode siemens = r.path("brands").get(0);
        assertEquals("Siemens", siemens.path("name").asText(), "按型号数倒序");
        assertEquals(4, siemens.path("items").asInt());
        eq("50.00", siemens.path("pricedRate"));
        JsonNode noBrand = named(r.path("brands"), "未填品牌");
        assertEquals(1, noBrand.path("items").asInt());
        assertTrue(noBrand.path("avgHours").isMissingNode(), "没有任务和回价的型号不算回价时长");

        JsonNode root = named(r.path("categories"), "仝己PLC");
        assertEquals(2, root.path("items").asInt(), "细分品类归一级，品类文字也能匹配");
        assertEquals(plc, root.path("categoryId").asLong());
        eq("2.0", root.path("avgHours"));
        assertEquals(3, named(r.path("categories"), "未分类").path("items").asInt());
    }

    /** 存量品牌键有空值、大小写不一，按品牌名重新识别后归并；不同品牌不会因为空键被并到一起（audit BUS-097） */
    @Test
    void brands_groupedByResolvedBrand_notByStoredKey() throws Exception {
        long a = inquiry("ITB-K", "2026-08-12", 7);
        itemWithKey(a, 1, "Itbrand Alpha", "");
        itemWithKey(a, 2, "ITBRAND ALPHA", "itbrand alpha");
        itemWithKey(a, 3, "Itbrand Beta", "");
        loginAsAdmin("it_bc_admin3");
        JsonNode r = ok(call(get(API).param("period", "DAY").param("date", "2026-08-12"), token("it_bc_admin3")));
        assertEquals(2, r.path("brands").size(), "两个品牌：" + r.path("brands"));
        assertEquals(2, r.path("brands").get(0).path("items").asInt());
        assertEquals(1, named(r.path("brands"), "Itbrand Beta").path("items").asInt());
    }

    @Test
    void dayWeekYear_ranges_andInvalidPeriod() throws Exception {
        long a = inquiry("ITB-E", "2026-08-12", 5);
        item(a, 1, "Omron", "", null, 1, null);
        item(inquiry("ITB-F", "2026-01-02", 7), 1, "Omron", "", null, 2, null);
        loginAsAdmin("it_bc_admin2");
        String admin = token("it_bc_admin2");

        JsonNode d = ok(call(get(API).param("period", "DAY").param("date", "2026-08-12"), admin));
        assertEquals("2026-08-12", d.path("dateTo").asText());
        assertEquals(1, d.path("totals").path("items").asInt());
        eq("0.00", d.path("totals").path("pricedRate"));
        assertTrue(d.path("previous").path("pricedRate").isMissingNode(), "上一天没有型号，比率为空");

        JsonNode w = ok(call(get(API).param("period", "WEEK").param("date", "2026-08-12"), admin));
        assertEquals("2026-08-10", w.path("dateFrom").asText());
        assertEquals("2026-08-16", w.path("dateTo").asText());

        JsonNode y = ok(call(get(API).param("period", "year").param("date", "2026-08-12"), admin));
        assertEquals("2026-01-01", y.path("dateFrom").asText());
        assertEquals(2, y.path("totals").path("items").asInt());

        JsonNode m = ok(call(get(API).param("date", "2026-08-12"), admin));
        assertEquals("MONTH", m.path("period").asText(), "默认按月");
        assertEquals("2026-08-31", m.path("dateTo").asText());

        assertTrue(call(get(API).param("period", "HOUR"), admin).path("message").asText().contains("按日、周、月、季度或年"));
    }
}

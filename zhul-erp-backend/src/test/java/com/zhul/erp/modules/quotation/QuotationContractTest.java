package com.zhul.erp.modules.quotation;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.modules.document.support.DocumentConverter;
import com.zhul.erp.modules.inquiry.InquiryContractSupport;
import com.zhul.erp.modules.inquiry.support.PriceKeys;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * 报价单（spec quotation/quotation）、报价推进询盘（inquiry/inquiry-intake）、型号级锁定（inquiry/sourcing-quote）。
 * 询盘与价格大多直接造数据；「部分型号已报出」走真实的询价录入接口。
 */
class QuotationContractTest extends InquiryContractSupport {

    private static final String QT = "/api/v1/quotations";
    /** loginAsAdmin 建的账号对应的 user_basic.id（见 IntegrationTestBase） */
    private static final long ADMIN_USER = 99000002L;
    private static final int MENU_QUOTATION = 100073;

    @Autowired private DocumentConverter converter;

    private String admin;
    private int codeSeq;

    @BeforeEach
    void setUpQuotation() {
        cleanupQuotation();
        jdbc.update("insert into exchange_rate (tenant_id, currency_code, rate) values (0, 'USD', 7.150000)");
        loginAsAdmin("it_qt_admin");
        admin = token("it_qt_admin");
    }

    @AfterEach
    void tearDownQuotation() {
        cleanupQuotation();
    }

    private void cleanupQuotation() {
        for (String t : new String[] {"quotation_send_log", "quotation_fee", "quotation_item", "quotation", "exchange_rate", "exchange_rate_log"}) {
            jdbc.update("delete from " + t + " where tenant_id = 0");
        }
    }

    // ---------------------------------------------------------------- 造数据

    /** 型号：型号、品牌、品类、数量、采购成本价（null 为还在询价，"NOSTOCK" 为无货）、货况、货期 */
    private record M(String model, String brand, String category, int qty, String cost, int condition, int lead) {
    }

    private static M m(String model, String cost) {
        return new M(model, "Siemens", "PLC", 1, cost, 1, 1);
    }

    private long seedInquiry(long customer, int status, String date, M... items) {
        String code = "ITQ" + (++codeSeq) + System.nanoTime() % 100000;
        jdbc.update("insert into customer_inquiry (tenant_id, inquiry_code, customer_id, inquiry_date, quote_deadline, status, owner_id, "
                        + "total_item_count, priced_item_count, customer_type) values (0, ?, ?, ?, ?, ?, ?, ?, ?, 1)",
                code, customer, date, date, status, ADMIN_USER, items.length,
                (int) java.util.Arrays.stream(items).filter(i -> i.cost() != null).count());
        long inquiryId = jdbc.queryForObject("select id from customer_inquiry where inquiry_code = ?", Long.class, code);
        int line = 1;
        for (M i : items) {
            jdbc.update("insert into inquiry_item (tenant_id, customer_inquiry_id, line_no, brand, brand_key, category, original_model, "
                            + "confirmed_model, model_key, quantity, quote_status, price_source) values (0, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, 2)",
                    inquiryId, line++, i.brand(), i.brand().toLowerCase(), i.category(), i.model(), i.model(), PriceKeys.model(i.model()), i.qty());
            long itemId = jdbc.queryForObject("select max(id) from inquiry_item where customer_inquiry_id = ?", Long.class, inquiryId);
            if ("NOSTOCK".equals(i.cost())) {
                jdbc.update("insert into sourcing_quote (tenant_id, inquiry_item_id, customer_inquiry_id, brand, model, channel, no_stock, "
                        + "status, quoted_by) values (0, ?, ?, ?, ?, 5, 1, 2, ?)", itemId, inquiryId, i.brand(), i.model(), BUYER_LIN);
                jdbc.update("update inquiry_item set quote_status = 3 where id = ?", itemId);
            } else if (i.cost() != null) {
                jdbc.update("insert into sourcing_quote (tenant_id, inquiry_item_id, customer_inquiry_id, brand, model, channel, shop_name, "
                                + "currency_code, unit_price, unit_price_cny, item_condition, lead_time, recommended, status, quoted_by) "
                                + "values (0, ?, ?, ?, ?, 2, '1688 店铺', 'CNY', ?, ?, ?, ?, 1, 2, ?)",
                        itemId, inquiryId, i.brand(), i.model(), new BigDecimal(i.cost()), new BigDecimal(i.cost()), i.condition(), i.lead(), BUYER_LIN);
                long quoteId = jdbc.queryForObject("select max(id) from sourcing_quote where inquiry_item_id = ?", Long.class, itemId);
                jdbc.update("update inquiry_item set quote_status = 2, selected_quote_id = ? where id = ?", quoteId, itemId);
            }
        }
        return inquiryId;
    }

    private List<Long> itemIds(long inquiryId) {
        return jdbc.queryForList("select id from inquiry_item where customer_inquiry_id = ? order by line_no", Long.class, inquiryId);
    }

    private int inquiryStatus(long inquiryId) {
        return jdbc.queryForObject("select status from customer_inquiry where id = ?", Integer.class, inquiryId);
    }

    private String inquiryCode(long inquiryId) {
        return jdbc.queryForObject("select inquiry_code from customer_inquiry where id = ?", String.class, inquiryId);
    }

    private JsonNode create(String body) throws Exception {
        return call(json(post(QT), body), admin);
    }

    private JsonNode byInquiries(Long... ids) throws Exception {
        return ok(create(write(Map.of("inquiryIds", List.of(ids)))));
    }

    /** 由详情生成保存请求，按需改行 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> saveBody(JsonNode detail) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("currencyCode", detail.path("currencyCode").asText());
        body.put("incoterm", detail.path("incoterm").asText(""));
        body.put("incotermPlace", detail.path("incotermPlace").asText(""));
        body.put("validUntil", detail.path("validUntil").asText(null));
        body.put("remark", detail.path("remark").asText(""));
        List<Map<String, Object>> items = new ArrayList<>();
        for (JsonNode i : detail.path("items")) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", i.path("id").asLong());
            m.put("description", i.path("description").asText(""));
            m.put("leadTime", i.path("leadTime").asInt());
            m.put("warranty", i.path("warranty").asText());
            m.put("quantity", i.path("quantity").asInt());
            m.put("pricingMode", i.path("pricingMode").asInt());
            m.put("marginRate", i.path("marginRate").isMissingNode() || i.path("marginRate").isNull() ? null : i.path("marginRate").decimalValue());
            m.put("markupAmount", i.path("markupAmount").isMissingNode() || i.path("markupAmount").isNull() ? null : i.path("markupAmount").decimalValue());
            m.put("unitPrice", i.path("unitPrice").decimalValue());
            items.add(m);
        }
        body.put("items", items);
        List<Map<String, Object>> fees = new ArrayList<>();
        for (JsonNode f : detail.path("fees")) {
            fees.add(new LinkedHashMap<>(Map.of("feeName", f.path("feeName").asText(), "amount", f.path("amount").decimalValue())));
        }
        body.put("fees", fees);
        return body;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> line(Map<String, Object> body, int index) {
        return ((List<Map<String, Object>>) body.get("items")).get(index);
    }

    private JsonNode save(long id, Map<String, Object> body) throws Exception {
        return call(json(put(QT + "/" + id), write(body)), admin);
    }

    private static JsonNode fail(JsonNode res) {
        assertTrue(res.path("code").asInt() != 0, "应当失败：" + res);
        return res;
    }

    private static void money(String expected, JsonNode actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual.decimalValue()), "期望 " + expected + "，实际 " + actual);
    }

    // ---------------------------------------------------------------- 按询盘报价

    @Test
    void quoteInquiriesSortedReadyFirstThenSourcing_quotedExcluded() throws Exception {
        long c = customer("Pacific Controls", "Australia");
        long r1 = seedInquiry(c, 6, "2026-10-03");
        long r2 = seedInquiry(c, 6, "2026-10-01");
        long r3 = seedInquiry(c, 6, "2026-09-27");
        long s1 = seedInquiry(c, 5, "2026-10-04");
        long s2 = seedInquiry(c, 5, "2026-09-30");
        seedInquiry(c, 7, "2026-10-05");
        JsonNode page = ok(call(json(post(QT + "/candidates/inquiries"), "{}"), admin));
        List<String> codes = page.path("records").findValuesAsText("inquiryCode");
        assertEquals(List.of(inquiryCode(r1), inquiryCode(r2), inquiryCode(r3), inquiryCode(s1), inquiryCode(s2)), codes);
        assertEquals(3, page.path("readyCount").asInt());
        assertEquals(2, page.path("sourcingCount").asInt());
        assertEquals(3, ok(call(json(post(QT + "/candidates/inquiries"), "{\"readyOnly\":true}"), admin)).path("total").asInt());
    }

    @Test
    void incotermDefaultsToDapAndCustomerCountry_unlessCustomerHasTerms() throws Exception {
        long c = customer("Pacific Controls", "Australia");
        JsonNode q = byInquiries(seedInquiry(c, 6, "2026-10-03", m("6ES7214-1AG40-0XB0", "2640")));
        assertEquals("DAP", q.path("incoterm").asText());
        assertEquals("Australia", q.path("incotermPlace").asText());
        jdbc.update("update customer set incoterm = 'FOB', incoterm_place = 'Shanghai' where id = ?", c);
        JsonNode q2 = byInquiries(seedInquiry(c, 6, "2026-10-04", m("6ES7231-4HD32-0XB0", "880")));
        assertEquals("FOB", q2.path("incoterm").asText(), "客户档案有默认交易条件时取客户的");
        assertEquals("Shanghai", q2.path("incotermPlace").asText());
    }

    @Test
    void mergeInquiriesOfSameCustomer_pendingNotice_differentCustomerRejected() throws Exception {
        long c = customer("Pacific Controls", "Australia");
        long a = seedInquiry(c, 6, "2026-10-03", m("6ES7214-1AG40-0XB0", "2640"), m("6ES7231-4HD32-0XB0", "880"));
        long b = seedInquiry(c, 5, "2026-10-02", m("6AV2123-2GB03-0AX0", "1500"), m("6SL3210-1KE21-3UF1", null));
        JsonNode q = byInquiries(a, b);
        assertEquals(3, q.path("items").size(), "可报价的全部型号 + 询价中已回价的型号");
        assertEquals(1, q.path("pendingItemCount").asInt(), "还有 1 个型号在询价");
        assertTrue(q.path("quotationNo").asText().matches("QT\\d{8}\\d{3}"));
        assertEquals("USD", q.path("currencyCode").asText());
        money("7.15", q.path("exchangeRate"));
        assertEquals(inquiryCode(a), q.path("items").get(0).path("inquiryCode").asText());

        long other = seedInquiry(customer("Akij Group", "Bangladesh"), 6, "2026-10-03", m("1756-L83E", "6000"));
        assertEquals("不同客户需要分开报价", fail(create(write(Map.of("inquiryIds", List.of(a, other))))).path("message").asText());
    }

    // ---------------------------------------------------------------- 定价与编辑

    @Test
    void fromOneInquiry_suggestedPrice_editMargin_fee_floor_andRateChange() throws Exception {
        long c = customer("Pacific Controls", "Australia");
        long inq = seedInquiry(c, 6, "2026-10-03", new M("6ES7214-1AG40-0XB0", "Siemens", "PLC", 2, "520", 1, 1));
        JsonNode q = byInquiries(inq);
        JsonNode l = q.path("items").get(0);
        money("520", l.path("costPrice"));
        money("10", l.path("suggestedMargin"));
        assertEquals("全新原装 10%", l.path("suggestBasis").asText());
        money("577.78", l.path("unitPriceCny"));
        money("80.81", l.path("unitPrice"));
        long id = q.path("id").asLong();

        // 改毛利率 15%
        Map<String, Object> body = saveBody(q);
        line(body, 0).put("marginRate", new BigDecimal("15"));
        body.put("fees", List.of(Map.of("feeName", "Shipping Cost", "amount", 60)));
        JsonNode saved = ok(save(id, body));
        l = saved.path("items").get(0);
        money("611.76", l.path("unitPriceCny"));
        money("85.56", l.path("unitPrice"));
        money("171.12", l.path("amount"));
        money("25.67", l.path("netProfit"));
        money("183.51", l.path("netProfitCny"));
        money("231.12", saved.path("totalAmount"));
        money("1652.51", saved.path("totalAmountCny"));
        money("25.67", saved.path("netProfit"));
        money("15", saved.path("marginRate"));
        assertFalse(l.path("belowFloor").asBoolean());

        // 直接改外币售价到红线以下：只提示，仍可保存与发送
        line(body, 0).put("pricingMode", 3);
        line(body, 0).put("unitPrice", new BigDecimal("75"));
        JsonNode low = ok(save(id, body));
        assertTrue(low.path("items").get(0).path("belowFloor").asBoolean());
        money("3.03", low.path("items").get(0).path("marginRate"));

        // 草稿遇到汇率调整：提示新汇率，按原毛利率重算
        line(body, 0).put("pricingMode", 1);
        line(body, 0).put("marginRate", new BigDecimal("15"));
        ok(save(id, body));
        jdbc.update("update exchange_rate set rate = 7.180000 where tenant_id = 0 and currency_code = 'USD'");
        JsonNode detail = ok(call(get(QT + "/" + id), admin));
        money("7.18", detail.path("systemRate"));
        money("7.15", detail.path("exchangeRate"));
        JsonNode recalced = ok(call(post(QT + "/" + id + "/recalc-rate"), admin));
        money("7.18", recalced.path("exchangeRate"));
        money("85.20", recalced.path("items").get(0).path("unitPrice"));
        assertTrue(recalced.path("systemRate").isMissingNode() || recalced.path("systemRate").isNull());
    }

    @Test
    void returningCustomerHint_andMissingRate() throws Exception {
        long c = customer("Pacific Controls", "Australia");
        long inq = seedInquiry(c, 6, "2026-10-03", m("6ES7214-1AG40-0XB0", "2640"));
        jdbc.update("update customer_inquiry set customer_type = 2 where id = ?", inq);
        String[][] won = {{"QTIT90000001", "16.50", "2026-08-01"}, {"QTIT90000002", "18.20", "2026-09-01"},
                {"QTIT90000003", "20.10", "2026-07-01"}, {"QTIT90000004", "30.00", "2026-01-01"}};
        for (String[] w : won) {
            jdbc.update("insert into quotation (tenant_id, quotation_no, customer_id, status, margin_rate, closed_at) values (0, ?, ?, 3, ?, ?)",
                    w[0], c, new BigDecimal(w[1]), w[2]);
        }
        JsonNode q = byInquiries(inq);
        assertTrue(q.path("returningCustomer").asBoolean());
        List<BigDecimal> margins = new ArrayList<>();
        q.path("returningCustomerMargins").forEach(n -> margins.add(n.decimalValue().stripTrailingZeros()));
        assertEquals(List.of(new BigDecimal("18.2"), new BigDecimal("16.5"), new BigDecimal("20.1")), margins, "最近 3 张已成交");

        long gbp = customer("British Valves", "United Kingdom");
        jdbc.update("update customer set currency = 'GBP' where id = ?", gbp);
        long gInq = seedInquiry(gbp, 6, "2026-10-03", m("1756-L83E", "6000"));
        assertEquals("还没有设置 GBP 汇率，请联系管理员在系统管理中设置",
                fail(create(write(Map.of("inquiryIds", List.of(gInq))))).path("message").asText());
    }

    // ---------------------------------------------------------------- 挑选型号报价

    @Test
    void pickItems_manyInquiries_search_pending_crossInquiry() throws Exception {
        long c = customer("Pacific Controls", "Australia");
        List<Long> ready = new ArrayList<>();
        for (int d = 1; d <= 8; d++) {
            ready.add(seedInquiry(c, 6, String.format("2026-09-%02d", d), m("6ES7-R" + d, "100" + d), m("3RT2015-" + d, "50")));
        }
        long sourcing = seedInquiry(c, 5, "2026-09-30", m("6ES7-S1", "300"), m("6ES7-PENDING", null));
        for (int d = 1; d <= 3; d++) {
            seedInquiry(c, 7, String.format("2026-09-%02d", 20 + d), m("ABB-Q" + d, "100"));
        }
        JsonNode customers = ok(call(get(QT + "/candidates/customers").param("keyword", "Pacific"), admin));
        assertEquals(12, customers.get(0).path("inquiryCount").asInt());

        JsonNode page1 = ok(call(json(post(QT + "/candidates/items"), "{\"customerId\":" + c + "}"), admin));
        assertEquals(12, page1.path("total").asInt());
        assertEquals(10, page1.path("records").size());
        assertEquals(inquiryCode(ready.get(7)), page1.path("records").get(0).path("inquiryCode").asText(), "可报价里最新的在最前");
        assertEquals(inquiryCode(sourcing), page1.path("records").get(8).path("inquiryCode").asText(), "询价中排在可报价之后");
        assertEquals(7, page1.path("records").get(9).path("status").asInt(), "已报价排最后");
        assertEquals(2, ok(call(json(post(QT + "/candidates/items"), "{\"customerId\":" + c + ",\"page\":2}"), admin)).path("records").size());

        JsonNode search = ok(call(json(post(QT + "/candidates/items"), "{\"customerId\":" + c + ",\"keyword\":\"6ES7\"}"), admin));
        for (JsonNode inq : search.path("records")) {
            for (JsonNode it : inq.path("items")) {
                assertTrue(it.path("model").asText().contains("6ES7"), "搜索只列出命中的型号");
            }
        }
        JsonNode pendingItem = null;
        for (JsonNode inq : search.path("records")) {
            for (JsonNode it : inq.path("items")) {
                if ("6ES7-PENDING".equals(it.path("model").asText())) {
                    pendingItem = it;
                }
            }
        }
        assertFalse(pendingItem.path("pickable").asBoolean());
        assertEquals("还在询价，回价后才能报价", pendingItem.path("disabledReason").asText());

        assertTrue(fail(create(write(Map.of("itemIds", List.of(pendingItem.path("itemId").asLong()))))).path("message").asText()
                .contains("还在询价，回价后才能报价"));
        List<Long> first = itemIds(ready.get(0));
        List<Long> second = itemIds(ready.get(1));
        JsonNode q = ok(create(write(Map.of("itemIds", List.of(first.get(0), first.get(1), second.get(0))))));
        assertEquals(3, q.path("items").size());
        assertEquals(List.of(inquiryCode(ready.get(0)), inquiryCode(ready.get(0)), inquiryCode(ready.get(1))),
                q.path("items").findValuesAsText("inquiryCode"));

        // 已在草稿中的型号仍可选，并提示草稿编号
        JsonNode again = ok(call(json(post(QT + "/candidates/items"), "{\"customerId\":" + c + ",\"keyword\":\"6ES7-R1\"}"), admin));
        assertEquals(q.path("quotationNo").asText(), again.path("records").get(0).path("items").get(0).path("draftQuotationNo").asText());
    }

    // ---------------------------------------------------------------- 文字报价与导出

    @Test
    void textQuote_andExportWithoutCost() throws Exception {
        long c = customer("Pacific Controls", "Australia");
        long inq = seedInquiry(c, 6, "2026-10-03", new M("1756-PB72", "Allen-Bradley", "电源", 1, "2640", 1, 2));
        JsonNode q = byInquiries(inq);
        long id = q.path("id").asLong();
        Map<String, Object> body = saveBody(q);
        line(body, 0).put("pricingMode", 3);
        line(body, 0).put("unitPrice", new BigDecimal("463.20"));
        ok(save(id, body));
        assertEquals("1756-PB72 Allen-Bradley 1 $463.2 1-2 days original new 1 year warranty time",
                ok(call(get(QT + "/" + id + "/text"), admin)).path("text").asText());

        MockHttpServletResponse xlsx = perform(get(QT + "/" + id + "/export").param("format", "xlsx"), admin);
        assertTrue(xlsx.getHeader("Content-Disposition").contains(q.path("quotationNo").asText()));
        StringBuilder all = new StringBuilder();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx.getContentAsByteArray()))) {
            DataFormatter fmt = new DataFormatter();
            for (Row row : wb.getSheetAt(0)) {
                row.forEach(cell -> all.append(fmt.formatCellValue(cell)).append('|'));
            }
        }
        assertTrue(all.toString().contains("1756-PB72"));
        assertTrue(all.toString().contains("463.2"));
        assertFalse(all.toString().contains("2640"), "导出文件不含采购成本价");
        assertFalse(all.toString().contains("1688"), "导出文件不含采购渠道与店铺");

        Assumptions.assumeTrue(converter.available(), "本机未安装 LibreOffice");
        MockHttpServletResponse pdf = perform(get(QT + "/" + id + "/export").param("format", "pdf"), admin);
        assertEquals("application/pdf", pdf.getContentType());
        assertTrue(pdf.getContentAsByteArray().length <= 500 * 1024);

        // 改价后预览：同样内容第二次命中缓存
        line(body, 0).put("unitPrice", new BigDecimal("400.00"));
        String preview = write(Map.of("quotationId", id, "content", body));
        JsonNode p1 = ok(call(json(post(QT + "/preview"), preview), admin));
        assertTrue(p1.path("pages").get(0).asText().startsWith("data:image/jpeg;base64,"));
        long start = System.nanoTime();
        JsonNode p2 = ok(call(json(post(QT + "/preview"), preview), admin));
        assertEquals(p1.path("pages").get(0).asText(), p2.path("pages").get(0).asText());
        assertTrue((System.nanoTime() - start) / 1_000_000 < 1500, "同样内容不重复转换");
        money("463.20", ok(call(get(QT + "/" + id), admin)).path("items").get(0).path("unitPrice"));
    }

    // ---------------------------------------------------------------- 状态流转

    @Test
    void sendCopyLostWonVoid_pushInquiryStatuses() throws Exception {
        long c = customer("Pacific Controls", "Australia");
        long a = seedInquiry(c, 6, "2026-10-03", m("6ES7214-1AG40-0XB0", "2640"));
        long b = seedInquiry(c, 6, "2026-10-02", m("6ES7231-4HD32-0XB0", "880"));
        JsonNode q = byInquiries(a, b);
        long id = q.path("id").asLong();

        JsonNode sent = ok(call(json(post(QT + "/" + id + "/send"), "{\"channel\":3}"), admin));
        assertEquals(2, sent.path("status").asInt());
        assertEquals("PDF", sent.path("sendLogs").get(0).path("channelName").asText());
        assertEquals(7, inquiryStatus(a), "发送报价单推进询盘");
        assertEquals(7, inquiryStatus(b));
        assertTrue(fail(save(id, saveBody(q))).path("message").asText().contains("复制为新报价单"), "已发送只读");
        ok(call(json(post(QT + "/" + id + "/send"), "{\"channel\":1}"), admin));
        assertEquals(2, ok(call(get(QT + "/" + id), admin)).path("sendLogs").size(), "发送记录可追加");

        // 改价重报
        JsonNode copy = ok(call(post(QT + "/" + id + "/copy"), admin));
        assertEquals(1, copy.path("status").asInt());
        assertEquals(q.path("quotationNo").asText(), copy.path("copiedFromNo").asText());
        Map<String, Object> body = saveBody(copy);
        line(body, 0).put("marginRate", new BigDecimal("8"));
        ok(save(copy.path("id").asLong(), body));
        money(sent.path("totalAmount").asText(), ok(call(get(QT + "/" + id), admin)).path("totalAmount"));

        // 未成交须选原因、选其他须说明
        assertEquals("请选择未成交原因", fail(call(json(post(QT + "/" + id + "/lost"), "{}"), admin)).path("message").asText());
        assertEquals("选择「其他」时请填写说明", fail(call(json(post(QT + "/" + id + "/lost"), "{\"reason\":\"OTHER\"}"), admin))
                .path("message").asText());
        assertEquals(2, ok(call(get(QT + "/" + id), admin)).path("status").asInt(), "状态不变");

        // 不能手动标成交：成交由销售订单推进（见 sales 模块的契约测试）
        assertTrue(perform(post(QT + "/" + id + "/won"), admin).getStatus() >= 400, "没有手动标成交的接口");
        assertEquals(2, ok(call(get(QT + "/" + id), admin)).path("status").asInt());
        assertEquals(2, ok(call(get(QT + "/by-inquiry/" + a), admin)).size(), "询盘详情看到两张报价单");

        // 唯一一张已发送的报价单未成交 → 询盘未成交；作废同理
        long lostInq = seedInquiry(c, 6, "2026-10-01", m("1756-L83E", "6000"));
        long lostId = byInquiries(lostInq).path("id").asLong();
        ok(call(json(post(QT + "/" + lostId + "/send"), "{\"channel\":2}"), admin));
        String reason = jdbc.queryForObject("select item_code from dict_item where dict_type = 'quotation_lost_reason' and item_code <> 'OTHER' "
                + "order by sort_order limit 1", String.class);
        JsonNode lost = ok(call(json(post(QT + "/" + lostId + "/lost"), "{\"reason\":\"" + reason + "\"}"), admin));
        assertEquals(4, lost.path("status").asInt());
        assertFalse(lost.path("lostReasonName").asText().isEmpty());
        assertEquals(9, inquiryStatus(lostInq));

        long voidInq = seedInquiry(c, 6, "2026-09-30", m("1756-L85E", "9000"));
        long voidId = byInquiries(voidInq).path("id").asLong();
        assertTrue(fail(call(post(QT + "/" + voidId + "/void"), admin)).path("message").asText().contains("先标为已发送"));
        ok(call(json(post(QT + "/" + voidId + "/send"), "{\"channel\":4}"), admin));
        ok(call(post(QT + "/" + voidId + "/void"), admin));
        assertEquals(9, inquiryStatus(voidInq));

        // 成交由销售订单推进；这里直接置为部分成交，验证本月成交统计把部分成交算在内
        jdbc.update("update quotation set status = 6, closed_at = NOW() where id = ?", id);
        JsonNode stats = ok(call(get(QT + "/stats"), admin));
        assertEquals(1, stats.path("draftCount").asInt(), "复制出的草稿");
        assertEquals(1, stats.path("monthWonCount").asInt());
        money(ok(call(get(QT + "/" + id), admin)).path("totalAmountCny").asText(), stats.path("monthWonAmountCny"));

        // 草稿可删除（软删除）
        ok(call(delete(QT + "/" + copy.path("id").asLong()), admin));
        assertEquals(1, ok(call(get(QT + "/by-inquiry/" + a), admin)).size());
        assertEquals(1, jdbc.queryForObject("select count(*) from quotation where id = ? and deleted_at is not null", Integer.class,
                copy.path("id").asLong()));
    }

    @Test
    void noStockLineNeedsPriceBeforeSending() throws Exception {
        long c = customer("Pacific Controls", "Australia");
        long inq = seedInquiry(c, 6, "2026-10-03", m("6ES7214-1AG40-0XB0", "2640"), m("6ES7-NOSTOCK", "NOSTOCK"));
        JsonNode byInquiry = byInquiries(inq);
        assertEquals(1, byInquiry.path("items").size(), "按询盘报价时无货型号默认不带入");
        JsonNode q = ok(create(write(Map.of("itemIds", itemIds(inq)))));
        JsonNode noStock = q.path("items").get(1);
        assertTrue(noStock.path("noStock").asBoolean());
        assertTrue(noStock.path("marginRate").isMissingNode() || noStock.path("marginRate").isNull(), "无货型号毛利率显示「—」");
        assertTrue(fail(call(json(post(QT + "/" + q.path("id").asLong() + "/send"), "{\"channel\":1}"), admin)).path("message").asText()
                .contains("还没有售价"));
    }

    // ---------------------------------------------------------------- 型号级锁定（真实询价录入）

    @Test
    void partiallyQuotedInquiry_onlyQuotedItemsLocked() throws Exception {
        long c = customer("Pacific Controls", "Australia");
        long inq = ok(call(json(post(INQ), "{\"customerId\":" + c + ",\"source\":1,\"urgent\":false,\"rawContent\":\"rfq\"}"), admin))
                .path("id").asLong();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String model : List.of("HG-SN202BJ", "HG-KN43J-S100")) {
            rows.add(Map.of("brand", "Mitsubishi", "category", "伺服电机", "confirmedModel", model, "quantity", 1));
        }
        ok(call(json(post(INQ + "/" + inq + "/confirm"), write(Map.of("rows", rows))), admin));
        long task = jdbc.queryForObject("select id from sourcing_task where customer_inquiry_id = ?", Long.class, inq);
        ok(call(json(post(BOARD + "/assign"), "{\"taskIds\":[" + task + "],\"assigneeId\":" + BUYER_LIN + "}"), admin));
        List<Long> items = itemIds(inq);
        String lin = token("it_lin");
        ok(call(json(put(MY + "/" + task + "/quotes"), quotesBody(items.get(0), "2640")), lin));
        assertEquals(5, inquiryStatus(inq), "还有型号在询价");

        long id = byInquiries(inq).path("id").asLong();
        ok(call(json(post(QT + "/" + id + "/send"), "{\"channel\":1}"), admin));
        assertEquals(7, inquiryStatus(inq), "只报了部分型号也进入已报价");
        JsonNode inquiryItems = ok(call(get(INQ + "/" + inq), admin)).path("items");
        int quotedCount = 0;
        for (JsonNode it : inquiryItems) {
            quotedCount += it.path("quoted").asBoolean() ? 1 : 0;
        }
        assertEquals(1, quotedCount, "询盘详情里已报出的型号有标记");

        assertEquals("HG-SN202BJ 已报给客户，回价不能再修改",
                call(json(put(MY + "/" + task + "/quotes"), quotesBody(items.get(0), "2500")), lin).path("message").asText());
        ok(call(json(put(MY + "/" + task + "/quotes"), quotesBody(items.get(1), "1800")), lin));
        JsonNode detail = ok(call(get(MY + "/" + task), lin));
        assertTrue(detail.path("task").path("editable").asBoolean());
        assertTrue(detail.path("items").get(0).path("locked").asBoolean());
        assertFalse(detail.path("items").get(1).path("locked").asBoolean());
    }

    private String quotesBody(long itemId, String price) throws Exception {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("channel", 2);
        entry.put("shopName", "三菱配件专营");
        entry.put("unitPrice", new BigDecimal(price));
        entry.put("itemCondition", 1);
        entry.put("leadTime", 1);
        entry.put("recommended", true);
        return write(Map.of("submit", true, "items", List.of(Map.of("itemId", itemId, "quotes", List.of(entry)))));
    }

    // ---------------------------------------------------------------- 权限与数据范围

    @Test
    void dataScope_andPartTimerCannotAccess() throws Exception {
        long c = customer("Pacific Controls", "Australia");
        long mineId = byInquiries(seedInquiry(c, 6, "2026-10-03", m("6ES7214-1AG40-0XB0", "2640"))).path("id").asLong();
        long othersId = byInquiries(seedInquiry(c, 6, "2026-10-02", m("6ES7231-4HD32-0XB0", "880"))).path("id").asLong();
        jdbc.update("update quotation set owner_id = ? where id = ?", BUYER_LIN, othersId);
        JsonNode byModel = ok(call(json(post(QT + "/page"), "{\"keyword\":\"6ES7214\"}"), admin));
        assertEquals(1, byModel.path("total").asInt(), "按型号搜索");
        assertEquals("Australia", byModel.path("records").get(0).path("customerCountry").asText());
        assertEquals(1, byModel.path("records").get(0).path("totalQuantity").asInt());

        loginWithResources("it_qt_staff", MENU_QUOTATION);
        String staff = token("it_qt_staff");
        assertEquals("报价单不存在", call(get(QT + "/" + othersId), staff).path("message").asText());
        JsonNode page = ok(call(json(post(QT + "/page"), "{}"), staff));
        assertEquals(1, page.path("total").asInt());
        assertEquals(mineId, page.path("records").get(0).path("id").asLong());
        assertEquals(403, perform(json(post(QT + "/page"), "{}"), token("it_wang")).getStatus(), "兼职采购不能访问报价单");
    }
}

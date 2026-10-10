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
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    @Autowired private com.zhul.erp.modules.aitask.service.AiTaskService aiTaskService;

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
        for (String t : new String[] {"quotation_send_log", "quotation_version", "quotation_fee", "quotation_item", "quotation", "exchange_rate", "exchange_rate_log"}) {
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

        // 已有草稿的询盘：候选里带出草稿报价单，点击直接打开
        JsonNode cands = ok(call(json(post(QT + "/candidates/inquiries"), "{}"), admin));
        JsonNode card = null;
        for (JsonNode n : cands.path("records")) {
            if (n.path("inquiryId").asLong() == a) {
                card = n;
            }
        }
        assertNotNull(card, "草稿不改变询盘状态，仍在候选中：" + cands);
        assertEquals(q.path("id").asLong(), card.path("draftQuotationId").asLong());
        assertEquals(q.path("quotationNo").asText(), card.path("draftQuotationNo").asText());

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
        // 卢布：录入汇率后可以用卢布报价，表头取 RUB 汇率
        long rub = customer("Moscow Automation", "Russia");
        jdbc.update("update customer set currency = 'RUB' where id = ?", rub);
        long rInq = seedInquiry(rub, 6, "2026-10-03", m("1756-L85E", "9000"));
        assertTrue(fail(create(write(Map.of("inquiryIds", List.of(rInq))))).path("message").asText().contains("RUB"));
        jdbc.update("insert into exchange_rate (tenant_id, currency_code, rate) values (0, 'RUB', 0.082500)");
        JsonNode rq = byInquiries(rInq);
        assertEquals("RUB", rq.path("currencyCode").asText());
        money("0.0825", rq.path("exchangeRate"));
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
        assertTrue(fail(save(id, saveBody(q))).path("message").asText().contains("修改（出新版本）"), "已发送只读");
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

        // 唯一一张已发送的报价单未成交 → 询盘未成交，不能再报价
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
        assertEquals(6, inquiryStatus(voidInq), "作废视为没报过：型号都有价格，回到可报价");
        long again = byInquiries(voidInq).path("id").asLong();
        assertTrue(again != voidId, "作废后可以再报一张");
        ok(call(json(post(QT + "/" + again + "/send"), "{\"channel\":4}"), admin));
        assertEquals(7, inquiryStatus(voidInq));

        // 作废时还有型号在询价 → 回到询价中
        long pendingInq = seedInquiry(c, 5, "2026-09-29", m("1756-IB16", "800"), m("1756-OB16", null));
        long pendingId = byInquiries(pendingInq).path("id").asLong();
        ok(call(json(post(QT + "/" + pendingId + "/send"), "{\"channel\":4}"), admin));
        assertEquals(7, inquiryStatus(pendingInq));
        ok(call(post(QT + "/" + pendingId + "/void"), admin));
        assertEquals(5, inquiryStatus(pendingInq));

        // 一张作废、一张未成交 → 未成交优先
        long mixInq = seedInquiry(c, 6, "2026-09-28", m("1756-IF8", "1500"));
        long mixA = byInquiries(mixInq).path("id").asLong();
        long mixB = byInquiries(mixInq).path("id").asLong();
        ok(call(json(post(QT + "/" + mixA + "/send"), "{\"channel\":4}"), admin));
        ok(call(post(QT + "/" + mixA + "/void"), admin));
        assertEquals(6, inquiryStatus(mixInq), "作废加草稿：回到可报价");
        ok(call(json(post(QT + "/" + mixB + "/send"), "{\"channel\":4}"), admin));
        ok(call(json(post(QT + "/" + mixB + "/lost"), "{\"reason\":\"" + reason + "\"}"), admin));
        assertEquals(9, inquiryStatus(mixInq));
        assertTrue(fail(create(write(Map.of("inquiryIds", List.of(mixInq))))).path("message").asText().contains("当前状态不能报价"));

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
    void reviseSendAbandon_versionsKept_editingDoesNotAffectInquiry() throws Exception {
        long c = customer("Pacific Controls", "Australia");
        long a = seedInquiry(c, 6, "2026-10-03", m("6ES7214-1AG40-0XB0", "2640"), m("6ES7231-4HD32-0XB0", "1680"),
                m("6ES7972-0BA42-0XA0", "200"));
        JsonNode q = byInquiries(a);
        long id = q.path("id").asLong();
        assertEquals("草稿报价单可以直接修改", fail(call(post(QT + "/" + id + "/revise"), admin)).path("message").asText());
        JsonNode rev1 = ok(call(json(post(QT + "/" + id + "/send"), "{\"channel\":3}"), admin));
        assertEquals(1, rev1.path("versions").size(), "发送时保存 Rev.1");

        // 出新版本：编号不变、内容复制、可编辑；第 1 行数量改为 3，删掉第 3 行
        JsonNode rev = ok(call(post(QT + "/" + id + "/revise"), admin));
        assertEquals(2, rev.path("versionNo").asInt());
        assertEquals(2, rev.path("editingVersionNo").asInt());
        assertEquals(q.path("quotationNo").asText(), rev.path("quotationNo").asText());
        assertTrue(rev.path("editable").asBoolean());
        assertEquals(3, rev.path("items").size());
        assertTrue(fail(call(post(QT + "/" + id + "/revise"), admin)).path("message").asText().contains("已有修改中的 Rev.2"));
        Map<String, Object> body = saveBody(rev);
        line(body, 0).put("quantity", 3);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) body.get("items");
        lines.remove(2);
        Map<String, Object> eur = new LinkedHashMap<>(body);
        eur.put("currencyCode", "EUR");
        assertTrue(fail(save(id, eur)).path("message").asText().contains("新版本不能改币种"));
        JsonNode saved = ok(save(id, body));
        assertEquals(2, saved.path("items").size());

        // 编辑中：当前版本、询盘状态与列表都还按 Rev.1
        JsonNode current = ok(call(get(QT + "/" + id).param("version", "1"), admin));
        assertEquals(3, current.path("items").size());
        assertFalse(current.path("editable").asBoolean());
        assertEquals(7, inquiryStatus(a));
        JsonNode row = ok(call(json(post(QT + "/page"), "{}"), admin)).path("records").get(0);
        assertEquals(3, row.path("itemCount").asInt());
        assertEquals(2, row.path("editingVersionNo").asInt());
        money(rev1.path("totalAmount").asText(), row.path("totalAmount"));

        // 发送 Rev.2：成为当前版本
        JsonNode sent = ok(call(json(post(QT + "/" + id + "/send"), "{\"channel\":3}"), admin));
        assertEquals(2, sent.path("currentVersionNo").asInt());
        assertTrue(sent.path("editingVersionNo").isNull() || sent.path("editingVersionNo").isMissingNode());
        assertEquals(2, sent.path("items").size());
        assertEquals(2, sent.path("versions").size());
        assertEquals(2, sent.path("sendLogs").get(1).path("versionNo").asInt());
        money(saved.path("totalAmount").asText(), sent.path("totalAmount"));
        assertEquals(2, ok(call(json(post(QT + "/page"), "{}"), admin)).path("records").get(0).path("itemCount").asInt());
        assertEquals(3, ok(call(get(QT + "/" + id).param("version", "1"), admin)).path("items").size(), "Rev.1 只读可查");
        assertEquals(7, inquiryStatus(a));

        // 放弃：回到 Rev.2；再出新版本时版本号不重复
        ok(call(post(QT + "/" + id + "/revise"), admin));
        JsonNode abandoned = ok(call(post(QT + "/" + id + "/abandon"), admin));
        assertEquals(2, abandoned.path("versionNo").asInt());
        assertEquals(2, abandoned.path("versions").size());
        assertEquals(4, ok(call(post(QT + "/" + id + "/revise"), admin)).path("versionNo").asInt());

        // 修改中的报价单标为未成交时一并放弃新版本
        String reason = jdbc.queryForObject("select item_code from dict_item where dict_type = 'quotation_lost_reason' and item_code <> 'OTHER' "
                + "order by sort_order limit 1", String.class);
        JsonNode lost = ok(call(json(post(QT + "/" + id + "/lost"), "{\"reason\":\"" + reason + "\"}"), admin));
        assertEquals(4, lost.path("status").asInt());
        assertTrue(lost.path("editingVersionNo").isNull() || lost.path("editingVersionNo").isMissingNode());
        assertEquals(2, lost.path("items").size());
    }

    @Test
    void noStockLine_byInquiry_replacement_textExport_sendable_notInPi() throws Exception {
        long c = customer("Pacific Controls", "Australia");
        long inq = seedInquiry(c, 6, "2026-10-03", m("6ES7214-1AG40-0XB0", "2640"), m("6ES7313-6CE01-0AB0", "NOSTOCK"));
        jdbc.update("update inquiry_item set lifecycle = 2, replacement_model = '6ES7313-6CG04-0AB0' where customer_inquiry_id = ? and line_no = 2", inq);
        JsonNode q = byInquiries(inq);
        assertEquals(2, q.path("items").size(), "按询盘报价时无货型号一起带入");
        JsonNode noStock = q.path("items").get(1);
        assertTrue(noStock.path("noStockLine").asBoolean());
        assertEquals("6ES7313-6CG04-0AB0", noStock.path("replacementModel").asText());
        money(q.path("items").get(0).path("amount").asText(), q.path("itemAmount"));
        long id = q.path("id").asLong();
        Map<String, Object> body = saveBody(q);
        line(body, 0).put("pricingMode", 3);
        line(body, 0).put("unitPrice", new BigDecimal("463.20"));
        ok(save(id, body));
        String text = ok(call(get(QT + "/" + id + "/text"), admin)).path("text").asText();
        assertTrue(text.endsWith("6ES7313-6CE01-0AB0 Siemens 1 no stock, discontinued, replacement: 6ES7313-6CG04-0AB0"), text);

        MockHttpServletResponse xlsx = perform(get(QT + "/" + id + "/export").param("format", "xlsx"), admin);
        StringBuilder all = new StringBuilder();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx.getContentAsByteArray()))) {
            wb.getCreationHelper().createFormulaEvaluator().evaluateAll();
            DataFormatter fmt = new DataFormatter();
            for (Row row : wb.getSheetAt(0)) {
                row.forEach(cell -> all.append(fmt.formatCellValue(cell, wb.getCreationHelper().createFormulaEvaluator())).append('|'));
            }
        }
        assertTrue(all.toString().contains("No stock"), all.toString());
        assertTrue(all.toString().contains("Discontinued, replacement: 6ES7313-6CG04-0AB0"));
        assertFalse(all.toString().contains("#VALUE"), "无货行的行内公式已清空");

        ok(call(json(post(QT + "/" + id + "/send"), "{\"channel\":1}"), admin));
        JsonNode candidate = ok(call(get("/api/v1/sales/pis/candidates/quotations/" + id), admin));
        assertEquals(1, candidate.path("items").size(), "无货行不能开 PI");

        // 填了售价就按正常报价
        long inq2 = seedInquiry(c, 6, "2026-10-04", m("6ES7-ALL-NOSTOCK", "NOSTOCK"));
        JsonNode allNoStock = byInquiries(inq2);
        assertEquals("报价单的型号都是无货，至少要有一个报价的型号才能发送",
                fail(call(json(post(QT + "/" + allNoStock.path("id").asLong() + "/send"), "{\"channel\":1}"), admin)).path("message").asText());
        Map<String, Object> priced = saveBody(allNoStock);
        line(priced, 0).put("unitPrice", new BigDecimal("120.00"));
        JsonNode after = ok(save(allNoStock.path("id").asLong(), priced));
        assertFalse(after.path("items").get(0).path("noStockLine").asBoolean());
        money("120.00", after.path("itemAmount"));
    }

    @Test
    void brandsInCustomerDocumentsUseEnglishName() throws Exception {
        jdbc.update("delete from product_brand_alias where alias_key = '烛龙测试品牌'");
        jdbc.update("delete from product_brand where tenant_id = 0 and brand_name = 'Zhul Test Brand'");
        jdbc.update("insert into product_brand (tenant_id, brand_name) values (0, 'Zhul Test Brand')");
        long brand = jdbc.queryForObject("select id from product_brand where tenant_id = 0 and brand_name = 'Zhul Test Brand'", Long.class);
        jdbc.update("insert into product_brand_alias (tenant_id, brand_id, alias, alias_key) values (0, ?, '烛龙测试品牌', '烛龙测试品牌')", brand);
        try {
            long c = customer("Pacific Controls", "Australia");
            long inq = seedInquiry(c, 6, "2026-10-03", new M("ZT-001", "烛龙测试品牌", "PLC", 2, "1000", 1, 1),
                    new M("UN-001", "没收录的牌子", "PLC", 1, "500", 1, 1));
            JsonNode q = byInquiries(inq);
            assertEquals("烛龙测试品牌", q.path("items").get(0).path("brand").asText(), "系统内仍显示原文");
            String text = ok(call(get(QT + "/" + q.path("id").asLong() + "/text"), admin)).path("text").asText();
            assertTrue(text.contains("ZT-001 Zhul Test Brand 2 "), text);
            assertTrue(text.contains("UN-001 没收录的牌子 1 "), "匹配不到品牌主数据时保留原文");
        } finally {
            jdbc.update("delete from product_brand_alias where brand_id = ?", brand);
            jdbc.update("delete from product_brand where id = ?", brand);
        }
    }

    @Test
    void priceHistory_orderFirst_sameCustomer_andStrategyTiers() throws Exception {
        long c = customer("ACROBOT", "India");
        long old = seedInquiry(c, 6, "2026-09-01", m("6ES7214-1AG40-0XB0", "2640"), m("6ES7231-4HD32-0XB0", "1680"));
        JsonNode first = byInquiries(old);
        Map<String, Object> body = saveBody(first);
        line(body, 0).put("pricingMode", 3);
        line(body, 0).put("unitPrice", new BigDecimal("463.20"));
        line(body, 1).put("pricingMode", 3);
        line(body, 1).put("unitPrice", new BigDecimal("267.00"));
        ok(save(first.path("id").asLong(), body));
        ok(call(json(post(QT + "/" + first.path("id").asLong() + "/send"), "{\"channel\":1}"), admin));
        jdbc.update("delete from sales_order_item where tenant_id = 0");
        jdbc.update("delete from sales_order where tenant_id = 0");
        jdbc.update("insert into sales_order (tenant_id, so_no, source, sales_date, customer_id, owner_id, currency_code, status) "
                + "values (0, 'SO20260912001', 2, '2026-09-12', ?, ?, 'USD', 1)", c, ADMIN_USER);
        long so = jdbc.queryForObject("select id from sales_order where so_no = 'SO20260912001'", Long.class);
        jdbc.update("insert into sales_order_item (tenant_id, so_id, line_no, model, quantity, unit_price) values (0, ?, 1, '6ES7214-1AG40-0XB0', 1, 440.00)", so);
        try {
            long now = seedInquiry(c, 6, "2026-10-08", m("6ES7214-1AG40-0XB0", "2640"), m("6es7231 4hd32 0xb0", "1680"), m("1756-PB72", "3100"));
            long id = byInquiries(now).path("id").asLong();
            JsonNode history = ok(call(get(QT + "/" + id + "/price-history"), admin));
            assertEquals(2, history.size(), "没有历史价的型号不返回");
            assertEquals("ORDER", history.get(0).path("kind").asText(), "成交价优先");
            assertEquals("SO20260912001", history.get(0).path("docNo").asText());
            money("440.00", history.get(0).path("unitPrice"));
            assertEquals("QUOTATION", history.get(1).path("kind").asText(), "型号写法不同也能按归一化型号匹配");
            assertEquals(first.path("quotationNo").asText(), history.get(1).path("docNo").asText());
        } finally {
            jdbc.update("delete from sales_order_item where tenant_id = 0");
            jdbc.update("delete from sales_order where tenant_id = 0");
        }

        JsonNode tiers = ok(call(get(QT + "/strategy-tiers"), admin));
        assertEquals(3, tiers.size());
        money("35", tiers.get(0).path("marginRate"));
        assertTrue(tiers.get(2).path("maxCost").isNull() || tiers.get(2).path("maxCost").isMissingNode());
        assertEquals("最后一档不设上限", fail(call(json(put(QT + "/strategy-tiers"),
                "{\"tiers\":[{\"maxCost\":500,\"marginRate\":30}]}"), admin)).path("message").asText());
        assertEquals("各档的采购成本价上限需要大于 0 且逐档递增", fail(call(json(put(QT + "/strategy-tiers"),
                "{\"tiers\":[{\"maxCost\":500,\"marginRate\":30},{\"maxCost\":200,\"marginRate\":20},{\"marginRate\":10}]}"), admin))
                .path("message").asText());
        try {
            JsonNode saved = ok(call(json(put(QT + "/strategy-tiers"), "{\"tiers\":[{\"maxCost\":500,\"marginRate\":30},{\"marginRate\":15}]}"), admin));
            assertEquals(2, saved.size());
            assertEquals(2, ok(call(get(QT + "/strategy-tiers"), admin)).size());
        } finally {
            jdbc.update("delete from sys_config where tenant_id <> 0 and config_key = 'quotation.strategy.cost-tiers'");
            jdbc.update("update sys_config set config_value = '[{\"maxCost\":300,\"marginRate\":35},{\"maxCost\":3000,\"marginRate\":20},{\"maxCost\":null,\"marginRate\":12}]' "
                    + "where tenant_id = 0 and config_key = 'quotation.strategy.cost-tiers'");
        }
    }

    @Test
    void bilingualDescriptions_confirmQuotationPiExport_andTranslation() throws Exception {
        // 解析确认：中英文两份描述都保存
        long c = customer("Pacific Controls", "Australia");
        long manual = ok(call(json(post(INQ), "{\"customerId\":" + c + ",\"source\":1,\"urgent\":false,\"rawContent\":\"rfq\"}"), admin))
                .path("id").asLong();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("brand", "Siemens");
        row.put("category", "断路器");
        row.put("confirmedModel", "3VA2340-5JQ42-0AA0");
        row.put("quantity", 3);
        row.put("description", "西门子 3VA2 塑壳断路器，400A 4P");
        row.put("descriptionEn", "Siemens 3VA2 MCCB, 400A 4P");
        ok(call(json(post(INQ + "/" + manual + "/confirm"), write(Map.of("rows", List.of(row)))), admin));
        assertEquals("Siemens 3VA2 MCCB, 400A 4P", jdbc.queryForObject(
                "select description_en from inquiry_item where customer_inquiry_id = ?", String.class, manual));
        JsonNode inquiryItem = ok(call(get(INQ + "/" + manual), admin)).path("items").get(0);
        assertEquals("西门子 3VA2 塑壳断路器，400A 4P", inquiryItem.path("description").asText(), "系统内显示中文");

        // 报价单：两份都带入，页面中文，导出与文字报价英文
        long inq = seedInquiry(c, 6, "2026-10-03", m("6ES7214-1AG40-0XB0", "2640"), m("6ES7231-4HD32-0XB0", "1680"));
        jdbc.update("update inquiry_item set description = 'CPU 1214C 紧凑型', description_en = 'CPU 1214C compact' where customer_inquiry_id = ? and line_no = 1", inq);
        jdbc.update("update inquiry_item set description = '模拟量输入模块', description_en = '' where customer_inquiry_id = ? and line_no = 2", inq);
        JsonNode q = byInquiries(inq);
        assertEquals("CPU 1214C 紧凑型", q.path("items").get(0).path("description").asText());
        assertEquals("CPU 1214C compact", q.path("items").get(0).path("descriptionEn").asText());
        long id = q.path("id").asLong();
        Map<String, Object> body = saveBody(q);
        line(body, 0).put("descriptionEn", "S7-1200 CPU 1214C, 24VDC");
        line(body, 1).put("descriptionEn", "");
        for (int n = 0; n < 2; n++) {
            line(body, n).put("pricingMode", 3);
            line(body, n).put("unitPrice", new BigDecimal("100.00"));
        }
        JsonNode saved = ok(save(id, body));
        assertEquals("S7-1200 CPU 1214C, 24VDC", saved.path("items").get(0).path("descriptionEn").asText());
        assertEquals("CPU 1214C 紧凑型", saved.path("items").get(0).path("description").asText(), "中文描述不变");

        MockHttpServletResponse xlsx = perform(get(QT + "/" + id + "/export").param("format", "xlsx"), admin);
        StringBuilder all = new StringBuilder();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx.getContentAsByteArray()))) {
            DataFormatter fmt = new DataFormatter();
            for (Row r : wb.getSheetAt(0)) {
                r.forEach(cell -> all.append(fmt.formatCellValue(cell)).append('|'));
            }
        }
        assertTrue(all.toString().contains("S7-1200 CPU 1214C, 24VDC"), "导出用英文描述");
        assertFalse(all.toString().contains("CPU 1214C 紧凑型"));
        assertTrue(all.toString().contains("模拟量输入模块"), "没有英文描述时退回中文");

        // 生成英文描述：提交任务 → 回调 → 询盘型号补英文（只补空的），结果按调用方的 key 返回
        long inquiryItem2 = jdbc.queryForObject("select id from inquiry_item where customer_inquiry_id = ? and line_no = 2", Long.class, inq);
        long lineId = q.path("items").get(1).path("id").asLong();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("key", String.valueOf(lineId));
        item.put("text", "模拟量输入模块");
        item.put("inquiryItemId", inquiryItem2);
        long task = ok(call(json(post("/api/v1/translations/item-descriptions"), write(Map.of("items", List.of(item)))), admin))
                .path("taskId").asLong();
        for (int i = 0; i < 30 && jdbc.queryForObject("select status from ai_task where id = ?", Integer.class, task) <= 2; i++) {
            Thread.sleep(100);
        }
        com.zhul.erp.modules.aitask.dto.AiTaskCallbackRequest cb = new com.zhul.erp.modules.aitask.dto.AiTaskCallbackRequest();
        cb.setStatus("success");
        cb.setOutput(Map.of("items", List.of(Map.of("key", inquiryItem2 + "#" + lineId, "text", "Analog input module"))));
        aiTaskService.handleCallback(task, cb);
        JsonNode result = ok(call(get("/api/v1/translations/item-descriptions/" + task), admin));
        assertEquals(3, result.path("status").asInt());
        assertEquals(String.valueOf(lineId), result.path("results").get(0).path("key").asText());
        assertEquals("Analog input module", result.path("results").get(0).path("text").asText());
        assertEquals("Analog input module", jdbc.queryForObject("select description_en from inquiry_item where id = ?", String.class, inquiryItem2));
        fail(call(get("/api/v1/translations/item-descriptions/" + task), token("it_lin")));
    }

    @Test
    void listSortsByItemCountQuantityAndTotal_unknownFieldFallsBack() throws Exception {
        long c = customer("Pacific Controls", "Australia");
        long small = byInquiries(seedInquiry(c, 6, "2026-10-03", new M("A-1", "Siemens", "PLC", 9, "100", 1, 1))).path("id").asLong();
        long big = byInquiries(seedInquiry(c, 6, "2026-10-04", m("B-1", "5000"), m("B-2", "5000"))).path("id").asLong();
        java.util.function.Function<String, List<Long>> ids = body -> {
            try {
                List<Long> list = new ArrayList<>();
                ok(call(json(post(QT + "/page"), body), admin)).path("records").forEach(r -> list.add(r.path("id").asLong()));
                return list;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        };
        assertEquals(List.of(small, big), ids.apply("{\"sortField\":\"itemCount\",\"sortOrder\":\"ascend\"}"));
        assertEquals(List.of(small, big), ids.apply("{\"sortField\":\"totalQuantity\",\"sortOrder\":\"descend\"}"), "9 件多于 2 件");
        assertEquals(List.of(big, small), ids.apply("{\"sortField\":\"totalAmount\",\"sortOrder\":\"descend\"}"));
        assertEquals(List.of(big, small), ids.apply("{\"sortField\":\"id; drop table quotation\",\"sortOrder\":\"ascend\"}"), "不认识的字段按创建时间倒序");
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

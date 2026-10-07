package com.zhul.erp.modules.sales;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.modules.inquiry.InquiryContractSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** 销售管理契约测试公共部分：直接造报价单（含询盘与询盘型号）、收款账户，平台租户 0 */
public abstract class SalesContractSupport extends InquiryContractSupport {

    protected static final String PI = "/api/v1/sales/pis";
    protected static final String SO = "/api/v1/sales/orders";
    protected static final long ADMIN_USER = 99000002L;
    protected static final String TODAY = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    private static final String[] SALES_TABLES = {"sales_order_fee", "sales_order_item", "sales_order", "payment_receipt",
            "proforma_invoice_send_log", "proforma_invoice_fee", "proforma_invoice_item", "proforma_invoice_version", "proforma_invoice",
            "quotation_send_log", "quotation_version", "quotation_fee", "quotation_item", "quotation", "tenant_bank_account", "document_sequence",
            "customer_party", "exchange_rate", "exchange_rate_log"};

    protected String admin;
    private int quotationSeq;

    @BeforeEach
    void setUpSales() {
        cleanupSales();
        jdbc.update("insert into exchange_rate (tenant_id, currency_code, rate) values (0, 'USD', 7.150000), (0, 'EUR', 7.800000)");
        jdbc.update("update sys_config set config_value = 'FW' where tenant_id = 0 and config_key = 'document.number-prefix'");
        loginAsAdmin("it_sales_admin");
        admin = token("it_sales_admin");
    }

    @AfterEach
    void tearDownSales() {
        cleanupSales();
        jdbc.update("update sys_config set config_value = '' where tenant_id = 0 and config_key = 'document.number-prefix'");
    }

    private void cleanupSales() {
        for (String t : SALES_TABLES) {
            jdbc.update("delete from " + t + " where tenant_id = 0");
        }
    }

    /** 型号行：型号、数量、采购成本价（CNY）、售价（外币） */
    protected record L(String model, int qty, String cost, String price) {
    }

    protected static L l(String model, int qty, String cost, String price) {
        return new L(model, qty, cost, price);
    }

    /** 造一张报价单（状态、币种、费用），每行挂到同一张新询盘的型号上；返回报价单 ID */
    protected long quotation(long customer, int status, String currency, String shipping, L... lines) {
        String code = "ITS" + (++quotationSeq) + System.nanoTime() % 100000;
        jdbc.update("insert into customer_inquiry (tenant_id, inquiry_code, customer_id, inquiry_date, quote_deadline, status, owner_id, "
                + "total_item_count, priced_item_count, customer_type) values (0, ?, ?, CURDATE(), CURDATE(), 7, ?, ?, ?, 1)",
                code, customer, ADMIN_USER, lines.length, lines.length);
        long inquiryId = jdbc.queryForObject("select id from customer_inquiry where inquiry_code = ?", Long.class, code);
        String no = "FWQT" + TODAY + String.format("%03d", quotationSeq);
        BigDecimal rate = new BigDecimal("USD".equals(currency) ? "7.15" : "7.80");
        jdbc.update("insert into quotation (tenant_id, quotation_no, customer_id, owner_id, currency_code, exchange_rate, incoterm, status, sent_at) "
                + "values (0, ?, ?, ?, ?, ?, 'FOB', ?, NOW() - INTERVAL ? MINUTE)", no, customer, ADMIN_USER, currency, rate, status, 100 - quotationSeq);
        long qid = jdbc.queryForObject("select id from quotation where quotation_no = ? and tenant_id = 0", Long.class, no);
        BigDecimal total = BigDecimal.ZERO;
        int lineNo = 1;
        for (L x : lines) {
            jdbc.update("insert into inquiry_item (tenant_id, customer_inquiry_id, line_no, brand, brand_key, category, original_model, "
                    + "confirmed_model, model_key, quantity, quote_status, price_source) values (0, ?, ?, 'Siemens', 'siemens', 'PLC', ?, ?, ?, ?, 2, 2)",
                    inquiryId, lineNo, x.model(), x.model(), x.model().toLowerCase(), x.qty());
            long itemId = jdbc.queryForObject("select max(id) from inquiry_item where customer_inquiry_id = ?", Long.class, inquiryId);
            BigDecimal amount = new BigDecimal(x.price()).multiply(BigDecimal.valueOf(x.qty()));
            total = total.add(amount);
            jdbc.update("insert into quotation_item (tenant_id, quotation_id, line_no, customer_inquiry_id, inquiry_item_id, model, brand, category, "
                    + "item_condition, lead_time, quantity, cost_price, floor_margin, unit_price, amount) values (0, ?, ?, ?, ?, ?, 'Siemens', 'PLC', 1, 1, ?, ?, 5, ?, ?)",
                    qid, lineNo++, inquiryId, itemId, x.model(), x.qty(), new BigDecimal(x.cost()), new BigDecimal(x.price()), amount);
        }
        if (shipping != null) {
            jdbc.update("insert into quotation_fee (tenant_id, quotation_id, fee_name, amount, sort_order) values (0, ?, 'Shipping Cost', ?, 1)",
                    qid, new BigDecimal(shipping));
            total = total.add(new BigDecimal(shipping));
        }
        jdbc.update("update quotation set total_amount = ? where id = ?", total, qid);
        return qid;
    }

    protected List<Long> quotationItemIds(long quotationId) {
        return jdbc.queryForList("select id from quotation_item where quotation_id = ? and is_current = 1 and deleted_at is null order by line_no", Long.class, quotationId);
    }

    protected long inquiryOf(long quotationId) {
        return jdbc.queryForObject("select customer_inquiry_id from quotation_item where quotation_id = ? limit 1", Long.class, quotationId);
    }

    protected void bankAccount(String currency) {
        jdbc.update("insert into tenant_bank_account (tenant_id, currency_code, bank_name, account_name, account_no, swift_code, is_default) "
                + "values (0, ?, 'JPMorgan Chase Bank N.A., Singapore Branch', 'Fuzhou Fouwell Technology Co., Ltd.', '10141740757803', 'CHASSGSGXXX', 1)",
                currency);
    }

    protected JsonNode createPi(List<Long> quotationItemIds) throws Exception {
        List<Map<String, Object>> items = new ArrayList<>();
        quotationItemIds.forEach(id -> items.add(Map.of("quotationItemId", id)));
        return call(json(post(PI), write(Map.of("items", items))), admin);
    }

    /** 由详情生成保存请求 */
    protected Map<String, Object> saveBody(JsonNode pi) throws Exception {
        JsonNode v = pi.path("version");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("buyer", objectMapper.convertValue(v.path("buyer"), Map.class));
        body.put("consignee", v.path("consignee").isNull() || v.path("consignee").isMissingNode() ? null
                : objectMapper.convertValue(v.path("consignee"), Map.class));
        body.put("deliveryTime", v.path("deliveryTime").asText(""));
        body.put("paymentTerm", v.path("paymentTerm").asText(""));
        body.put("incoterm", v.path("incoterm").asText(""));
        body.put("incotermPlace", v.path("incotermPlace").asText(""));
        body.put("portOfShipment", v.path("portOfShipment").asText(""));
        body.put("remark", v.path("remark").asText(""));
        body.put("bankAccountId", v.path("bankAccount").path("id").isMissingNode() ? null : v.path("bankAccount").path("id").asInt());
        body.put("discountType", v.path("discountType").asInt());
        body.put("discountValue", v.path("discountValue").decimalValue());
        List<Map<String, Object>> items = new ArrayList<>();
        for (JsonNode i : v.path("items")) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", i.path("id").asLong());
            m.put("description", i.path("description").asText(""));
            m.put("leadTime", i.path("leadTime").asInt());
            m.put("warranty", i.path("warranty").asText());
            m.put("quantity", i.path("quantity").asInt());
            m.put("unitPrice", i.path("unitPrice").decimalValue());
            m.put("hsCode", i.path("hsCode").asText(""));
            m.put("originCountry", i.path("originCountry").asText(""));
            m.put("remark", i.path("remark").asText(""));
            items.add(m);
        }
        body.put("items", items);
        List<Map<String, Object>> fees = new ArrayList<>();
        for (JsonNode f : v.path("fees")) {
            fees.add(new LinkedHashMap<>(Map.of("feeName", f.path("feeName").asText(), "amount", f.path("amount").decimalValue())));
        }
        body.put("fees", fees);
        return body;
    }

    @SuppressWarnings("unchecked")
    protected static Map<String, Object> item(Map<String, Object> body, int index) {
        return ((List<Map<String, Object>>) body.get("items")).get(index);
    }

    protected JsonNode savePi(long id, Map<String, Object> body) throws Exception {
        return call(json(put(PI + "/" + id), write(body)), admin);
    }

    protected JsonNode send(long id) throws Exception {
        return call(json(post(PI + "/" + id + "/sent"), "{\"channel\":3}"), admin);
    }

    protected static JsonNode fail(JsonNode res) {
        assertTrue(res.path("code").asInt() != 0, "应当失败：" + res);
        return res;
    }

    protected static void money(String expected, JsonNode actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual.decimalValue()), "期望 " + expected + "，实际 " + actual);
    }

    protected static final int MENU_PI = 100078;
    protected static final int MENU_SO = 100079;
    protected static final int BTN_SLIP = 110179;
    protected static final int BTN_CONFIRM = 110180;
    protected static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R'};

    /** 建一张已发送的 PI（带美元收款账户），可选整单折扣百分比；返回 PI ID */
    protected long sentPi(List<Long> quotationItemIds, Integer discountPercent) throws Exception {
        if (jdbc.queryForObject("select count(*) from tenant_bank_account where tenant_id = 0", Integer.class) == 0) {
            bankAccount("USD");
        }
        JsonNode pi = ok(createPi(quotationItemIds));
        long id = pi.path("id").asLong();
        Map<String, Object> body = saveBody(pi);
        body.put("bankAccountId", jdbc.queryForObject("select min(id) from tenant_bank_account where tenant_id = 0", Integer.class));
        if (discountPercent != null) {
            body.put("discountType", 1);
            body.put("discountValue", discountPercent);
        }
        ok(savePi(id, body));
        ok(send(id));
        return id;
    }

    protected JsonNode uploadSlip(long piId, String amount, String date, String token) throws Exception {
        return call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(PI + "/" + piId + "/slips")
                .file(new org.springframework.mock.web.MockMultipartFile("files", "slip.png", "image/png", PNG))
                .param("amount", amount).param("paidDate", date), token);
    }

    protected JsonNode confirmReceipt(long piId, String amount, Long slipId, boolean feeDiff, String token) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", amount);
        body.put("receiptDate", LocalDate.now().toString());
        body.put("bankAccountId", jdbc.queryForObject("select min(id) from tenant_bank_account where tenant_id = 0", Integer.class));
        body.put("slipId", slipId);
        body.put("feeDiff", feeDiff);
        return call(json(post(PI + "/" + piId + "/receipts"), write(body)), token);
    }
}

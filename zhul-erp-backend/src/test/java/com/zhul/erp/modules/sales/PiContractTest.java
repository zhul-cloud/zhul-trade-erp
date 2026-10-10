package com.zhul.erp.modules.sales;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.modules.document.support.DocumentConverter;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** spec sales/proforma-invoice：开 PI 的两种方式、内容与计算、状态与版本 */
class PiContractTest extends SalesContractSupport {

    @Autowired private DocumentConverter converter;

    private static final L[] FOUR = {l("6ES7214-1AG40-0XB0", 1, "4500", "740.44"), l("6ES7231-4HD32-0XB0", 2, "1230", "180.00"),
            l("6AV2123-2GB03-0AX0", 2, "420", "65.73"), l("6SL3210-1KE21-3UF1", 2, "350", "54.39")};

    // ---------------------------------------------------------------- 开 PI

    @Test
    void listSortsByQuantityAndTotal() throws Exception {
        long c = customer("ACROBOT", "India");
        long few = ok(createPi(quotationItemIds(quotation(c, 2, "USD", null, l("A-1", 1, "100", "500"))))).path("id").asLong();
        long many = ok(createPi(quotationItemIds(quotation(c, 2, "USD", null, l("B-1", 8, "10", "20"))))).path("id").asLong();
        java.util.List<Long> byQty = new java.util.ArrayList<>();
        ok(call(json(post(PI + "/page"), "{\"sortField\":\"totalQuantity\",\"sortOrder\":\"descend\"}"), admin))
                .path("records").forEach(r -> byQty.add(r.path("id").asLong()));
        assertEquals(java.util.List.of(many, few), byQty, "8 件多于 1 件");
        java.util.List<Long> byTotal = new java.util.ArrayList<>();
        ok(call(json(post(PI + "/page"), "{\"sortField\":\"totalAmount\",\"sortOrder\":\"descend\"}"), admin))
                .path("records").forEach(r -> byTotal.add(r.path("id").asLong()));
        assertEquals(java.util.List.of(few, many), byTotal, "USD 500 大于 USD 160");
    }

    @Test
    void byQuotation_uncheckOneLine_feesCarried_quotationListsPi() throws Exception {
        long c = customer("ACROBOT TECHNOLOGIES", "India");
        long q = quotation(c, 2, "USD", "60", FOUR);
        List<Long> ids = quotationItemIds(q);
        JsonNode pi = ok(createPi(ids.subList(0, 3)));
        assertEquals("FWPI" + TODAY + "001", pi.path("piNo").asText());
        assertEquals(1, pi.path("status").asInt());
        JsonNode v = pi.path("version");
        assertEquals(3, v.path("items").size());
        assertEquals("FWQT" + TODAY + "001", v.path("items").get(0).path("quotationNo").asText());
        assertEquals(1, v.path("fees").size());
        assertEquals("Shipping Cost", v.path("fees").get(0).path("feeName").asText());
        money("60", v.path("fees").get(0).path("amount"));
        money("7.15", pi.path("exchangeRate"));
        assertEquals("T/T 100% in advance", v.path("paymentTerm").asText());
        assertEquals("FOB", v.path("incoterm").asText());

        JsonNode listed = ok(call(get(PI + "/by-quotation/" + q), admin));
        assertEquals(1, listed.size());
        assertEquals(pi.path("piNo").asText(), listed.get(0).path("piNo").asText());
        // 候选型号标出已在 PI 中
        JsonNode cand = ok(call(get(PI + "/candidates/quotations/" + q), admin));
        assertEquals(pi.path("piNo").asText(), cand.path("items").get(0).path("inPiNo").asText());
        assertTrue(cand.path("items").get(3).path("inPiNo").isNull() || cand.path("items").get(3).path("inPiNo").isMissingNode());
    }

    @Test
    void acrossQuotations_defaultFees_currencyMustMatch_draftQuotationRejected() throws Exception {
        long c = customer("Pacific Controls", "Australia");
        long a = quotation(c, 2, "USD", "60", FOUR[0], FOUR[1]);
        long b = quotation(c, 2, "USD", null, FOUR[2]);
        JsonNode cands = ok(call(get(PI + "/candidates/quotations").param("customerId", String.valueOf(c)), admin));
        assertEquals(2, cands.size());
        assertEquals(b, cands.get(0).path("quotationId").asLong(), "按发送时间从新到旧");

        JsonNode pi = ok(createPi(List.of(quotationItemIds(a).get(0), quotationItemIds(a).get(1), quotationItemIds(b).get(0))));
        JsonNode items = pi.path("version").path("items");
        assertEquals(3, items.size());
        assertFalse(items.get(0).path("quotationNo").asText().equals(items.get(2).path("quotationNo").asText()));
        assertEquals(List.of("Shipping Cost", "Bank Charge"), pi.path("version").path("fees").findValuesAsText("feeName"));
        assertEquals(2, pi.path("quotations").size());

        long eur = quotation(c, 2, "EUR", null, l("3RT2026-1BB40", 1, "300", "60"));
        assertEquals("所选报价单币种不同（USD、EUR），请分开开 PI",
                fail(createPi(List.of(quotationItemIds(a).get(0), quotationItemIds(eur).get(0)))).path("message").asText());
        long draft = quotation(c, 1, "USD", null, l("3RT2026-1BB40", 1, "300", "60"));
        assertTrue(fail(createPi(quotationItemIds(draft))).path("message").asText().contains("只有已发送或部分成交的报价单可以开 PI"));
        long other = quotation(customer("Other Co", "Japan"), 2, "USD", null, l("X1", 1, "10", "5"));
        assertEquals("只能选择同一客户的报价单", fail(createPi(List.of(quotationItemIds(a).get(0), quotationItemIds(other).get(0)))).path("message").asText());
    }

    @Test
    void quotationWithActivePiCannotRevise_editingRevisionDoesNotChangePiCandidates() throws Exception {
        long c = customer("ACROBOT", "India");
        long q = quotation(c, 2, "USD", null, FOUR);

        // 修改中的新版本删掉一行，开 PI 的候选仍按当前版本
        ok(call(post("/api/v1/quotations/" + q + "/revise"), admin));
        JsonNode rev = ok(call(get("/api/v1/quotations/" + q), admin));
        assertEquals(2, rev.path("versionNo").asInt());
        jdbc.update("update quotation_item set deleted_at = NOW() where quotation_id = ? and version_no = 2 order by line_no desc limit 1", q);
        assertEquals(FOUR.length - 1, ok(call(get("/api/v1/quotations/" + q), admin)).path("items").size());
        JsonNode cand = ok(call(get(PI + "/candidates/quotations/" + q), admin));
        assertEquals(FOUR.length, cand.path("items").size());
        ok(call(post("/api/v1/quotations/" + q + "/abandon"), admin));

        long pi = ok(createPi(quotationItemIds(q))).path("id").asLong();
        String piNo = jdbc.queryForObject("select pi_no from proforma_invoice where id = ?", String.class, pi);
        JsonNode detail = ok(call(get("/api/v1/quotations/" + q), admin));
        assertEquals(piNo, detail.path("activePiNo").asText());
        assertEquals("已开 PI " + piNo + "，请在 PI 上修改",
                fail(call(post("/api/v1/quotations/" + q + "/revise"), admin)).path("message").asText());

        ok(call(post(PI + "/" + pi + "/void"), admin));
        assertEquals(3, ok(call(post("/api/v1/quotations/" + q + "/revise"), admin)).path("versionNo").asInt(), "PI 作废后可以出新版本");
    }

    @Test
    void buyerAndConsigneeFromCustomerParties() throws Exception {
        long c = customer("ACROBOT", "India");
        jdbc.update("insert into customer_party (tenant_id, customer_id, party_type, company_name, country, address, is_default) values "
                + "(0, ?, 3, 'ACROBOT TECHNOLOGIES PRIVATE LIMITED', 'India', 'Pune', 1), "
                + "(0, ?, 1, 'ACROBOT Mumbai Warehouse', 'India', 'Mumbai', 0), "
                + "(0, ?, 1, 'ACROBOT Ahmedabad Warehouse', 'India', 'Ahmedabad', 1)", c, c, c);
        JsonNode v = ok(createPi(quotationItemIds(quotation(c, 2, "USD", null, FOUR[0])))).path("version");
        assertEquals("ACROBOT TECHNOLOGIES PRIVATE LIMITED", v.path("buyer").path("name").asText());
        assertEquals("ACROBOT Ahmedabad Warehouse", v.path("consignee").path("name").asText());

        long plain = customer("Plain Co", "Japan");
        JsonNode v2 = ok(createPi(quotationItemIds(quotation(plain, 2, "USD", null, FOUR[0])))).path("version");
        assertEquals("Plain Co", v2.path("buyer").path("name").asText(), "没有发票抬头时取客户注册信息");
        assertEquals("Plain Co", v2.path("consignee").path("name").asText(), "没有收货人时与买方相同");
        assertTrue(v2.path("consignee").path("partyId").isNull() || v2.path("consignee").path("partyId").isMissingNode());
    }

    @Test
    void defaultsFromDictionaries_deliveryByLongestLeadTime() throws Exception {
        long c = customer("ACROBOT", "India");
        long q = quotation(c, 2, "USD", null, FOUR);
        // 第 2 行货期改为 1-2 周（码值 6），其余为现货
        jdbc.update("update quotation_item set lead_time = 6 where quotation_id = ? and line_no = 2", q);
        JsonNode v = ok(createPi(quotationItemIds(q))).path("version");
        assertEquals("1-2 weeks after payment", v.path("deliveryTime").asText());
        assertEquals("T/T 100% in advance", v.path("paymentTerm").asText());
        assertEquals("Hong Kong", v.path("portOfShipment").asText());
        assertEquals("FOB", v.path("incoterm").asText(), "沿用报价单的贸易术语");

        jdbc.update("update quotation set incoterm = '', incoterm_place = '' where id = ?", q);
        long q2 = quotation(c, 2, "USD", null, l("3RT2026", 1, "100", "20"));
        jdbc.update("update quotation set incoterm = '' where id = ?", q2);
        JsonNode v2 = ok(createPi(quotationItemIds(q2))).path("version");
        assertEquals("DAP", v2.path("incoterm").asText(), "报价单没有贸易术语时为 DAP + 客户国家");
        assertEquals("India", v2.path("incotermPlace").asText());
        assertEquals("3-5 days after payment", v2.path("deliveryTime").asText(), "现货对应付款后 3-5 天");
    }

    @Test
    void buyerFromAnotherOwnCustomer() throws Exception {
        long c = customer("ACROBOT", "India");
        long group = customer("ACROBOT GROUP", "Singapore");
        jdbc.update("insert into customer_party (tenant_id, customer_id, party_type, company_name, country, address, is_default) values "
                + "(0, ?, 3, 'ACROBOT GROUP PTE LTD', 'Singapore', '1 Raffles Place', 1)", group);
        long id = ok(createPi(quotationItemIds(quotation(c, 2, "USD", null, FOUR[0])))).path("id").asLong();
        JsonNode own = ok(call(get(PI + "/" + id + "/parties"), admin));
        assertTrue(own.get(own.size() - 1).path("registration").asBoolean(), "最后一条是客户注册信息");
        JsonNode other = ok(call(get(PI + "/" + id + "/parties").param("customerId", String.valueOf(group)), admin));
        assertEquals("ACROBOT GROUP PTE LTD", other.get(0).path("name").asText());

        jdbc.update("update customer set owner_id = ? where id = ?", BUYER_LIN, group);
        loginWithResources("it_pi_scope", MENU_PI);
        assertEquals("客户不存在", call(get(PI + "/" + id + "/parties").param("customerId", String.valueOf(group)),
                token("it_pi_scope")).path("message").asText(), "不在自己数据范围内的客户不能选");
    }

    // ---------------------------------------------------------------- 内容与计算

    @Test
    void discountFivePercent_priceChange_overDiscount_saveConsigneeToCustomer() throws Exception {
        long c = customer("ACROBOT", "India");
        JsonNode pi = ok(createPi(quotationItemIds(quotation(c, 2, "USD", "60", FOUR))));
        long id = pi.path("id").asLong();
        Map<String, Object> body = saveBody(pi);
        body.put("discountType", 1);
        body.put("discountValue", 5);
        JsonNode v = ok(savePi(id, body)).path("version");
        money("1340.68", v.path("itemAmount"));
        money("67.03", v.path("discountAmount"));
        money("1333.65", v.path("totalAmount"));
        money("9535.60", v.path("totalAmountCny"));
        money("84.84", v.path("netProfit"));

        body.put("discountType", 2);
        body.put("discountValue", 2000);
        assertEquals("折扣不能超过型号小计", fail(savePi(id, body)).path("message").asText());

        body.put("discountType", 0);
        item(body, 1).put("unitPrice", "170.00");
        body.put("consignee", Map.of("name", "ACROBOT Ahmedabad Warehouse", "address", "Plot 12, GIDC", "country", "India"));
        body.put("saveConsigneeToCustomer", true);
        JsonNode saved = ok(savePi(id, body));
        JsonNode line = saved.path("version").path("items").get(1);
        money("180.00", line.path("quotedPrice"));
        money("170.00", line.path("unitPrice"));
        money("340.00", line.path("amount"));
        assertTrue(saved.path("version").path("consignee").path("partyId").asLong() > 0);
        assertEquals(1, jdbc.queryForObject("select count(*) from customer_party where customer_id = ? and party_type = 1 and is_default = 1",
                Integer.class, c));
    }

    // ---------------------------------------------------------------- 状态与版本

    @Test
    void sendNeedsBankAccount_reviseQuantity_versionsKept_abandon() throws Exception {
        long c = customer("ACROBOT", "India");
        JsonNode pi = ok(createPi(quotationItemIds(quotation(c, 2, "USD", "60", FOUR))));
        long id = pi.path("id").asLong();
        assertTrue(fail(send(id)).path("message").asText().startsWith("请先选择收款账户"));
        bankAccount("USD");
        Map<String, Object> body = saveBody(ok(call(get(PI + "/" + id), admin)));
        body.put("bankAccountId", jdbc.queryForObject("select id from tenant_bank_account where tenant_id = 0", Integer.class));
        ok(savePi(id, body));
        JsonNode sent = ok(send(id));
        assertEquals(2, sent.path("status").asInt());
        assertEquals(1, sent.path("currentVersionNo").asInt());
        assertFalse(sent.path("editable").asBoolean());
        assertTrue(fail(savePi(id, body)).path("message").asText().contains("请先点「修改」"));

        JsonNode rev = ok(call(post(PI + "/" + id + "/revise"), admin));
        assertEquals(2, rev.path("editingVersionNo").asInt());
        assertTrue(rev.path("editable").asBoolean());
        Map<String, Object> b2 = saveBody(rev);
        item(b2, 1).put("quantity", 3);
        ok(savePi(id, b2));
        JsonNode sent2 = ok(send(id));
        assertEquals(2, sent2.path("currentVersionNo").asInt());
        assertEquals(pi.path("piNo").asText(), sent2.path("piNo").asText(), "编号不变");
        assertEquals(3, sent2.path("version").path("items").get(1).path("quantity").asInt());
        JsonNode rev1 = ok(call(get(PI + "/" + id).param("version", "1"), admin));
        assertEquals(2, rev1.path("version").path("items").get(1).path("quantity").asInt(), "Rev.1 只读可查");
        assertEquals(2, sent2.path("versions").size());
        assertEquals(2, sent2.path("sendLogs").size());

        ok(call(post(PI + "/" + id + "/revise"), admin));
        JsonNode abandoned = ok(call(post(PI + "/" + id + "/abandon"), admin));
        assertTrue(abandoned.path("editingVersionNo").isNull() || abandoned.path("editingVersionNo").isMissingNode());
        assertEquals(2, abandoned.path("version").path("versionNo").asInt());
        assertEquals(3, abandoned.path("versions").get(2).path("status").asInt());
        assertEquals("只有草稿 PI 可以删除，已发送的 PI 请作废", fail(call(delete(PI + "/" + id), admin)).path("message").asText());
    }

    @Test
    void convertedCannotRevise_receiptBlocksVoid_draftDeletable() throws Exception {
        long c = customer("ACROBOT", "India");
        long id = ok(createPi(quotationItemIds(quotation(c, 2, "USD", null, FOUR[0])))).path("id").asLong();
        jdbc.update("update proforma_invoice set status = 3, current_version_no = 1, editing_version_no = null where id = ?", id);
        jdbc.update("insert into sales_order (tenant_id, so_no, pi_id, customer_id, owner_id, status) values (0, 'SO20261008001', ?, ?, ?, 1)",
                id, c, ADMIN_USER);
        assertEquals("PI 已转成订单 SO20261008001，需要修改请先取消订单",
                fail(call(post(PI + "/" + id + "/revise"), admin)).path("message").asText());

        long id2 = ok(createPi(quotationItemIds(quotation(c, 2, "USD", null, FOUR[1])))).path("id").asLong();
        jdbc.update("update proforma_invoice set status = 2, current_version_no = 1, editing_version_no = null where id = ?", id2);
        jdbc.update("insert into payment_receipt (tenant_id, pi_id, kind, amount, status) values (0, ?, 2, 500, 1)", id2);
        assertEquals("已有到账记录的 PI 不能作废", fail(call(post(PI + "/" + id2 + "/void"), admin)).path("message").asText());

        long id3 = ok(createPi(quotationItemIds(quotation(c, 2, "USD", null, FOUR[2])))).path("id").asLong();
        ok(call(delete(PI + "/" + id3), admin));
        assertEquals("PI 不存在", fail(call(get(PI + "/" + id3), admin)).path("message").asText());
    }

    // ---------------------------------------------------------------- 导出

    @Test
    void exportPdf_fileNameAndContent_noCost() throws Exception {
        Assumptions.assumeTrue(converter.available(), "本机没有 LibreOffice，跳过 PDF 导出");
        long c = customer("ACROBOT TECHNOLOGIES PRIVATE LIMITED", "India");
        jdbc.update("update customer set short_name = 'ACROBOT' where id = ?", c);
        bankAccount("USD");
        JsonNode pi = ok(createPi(quotationItemIds(quotation(c, 2, "USD", "60", FOUR)).subList(0, 3)));
        long id = pi.path("id").asLong();
        Map<String, Object> body = saveBody(pi);
        body.put("discountType", 1);
        body.put("discountValue", 5);
        ok(savePi(id, body));

        MockHttpServletResponse res = perform(get(PI + "/" + id + "/export").param("format", "pdf"), admin);
        assertEquals(200, res.getStatus());
        assertTrue(res.getHeader("Content-Disposition").contains(pi.path("piNo").asText() + "_ACROBOT.pdf"));
        String text;
        try (PDDocument doc = Loader.loadPDF(res.getContentAsByteArray())) {
            text = new PDFTextStripper().getText(doc);
        }
        assertTrue(text.contains(pi.path("piNo").asText()), text);
        assertTrue(text.contains("ACROBOT TECHNOLOGIES PRIVATE LIMITED"), text);
        assertTrue(text.contains("10141740757803"), "银行账号");
        assertTrue(text.contains("Discount") && text.contains("Shipping Cost"), text);
        // 型号 1,231.90 + 运费 60.00 − 5% 折扣 61.60 = 1,230.30
        assertTrue(text.contains("-61.60") && text.contains("1,230.30"), text);
        assertFalse(text.contains("4,500") || text.contains("4500"), "不含采购成本");
        assertFalse(text.contains("Rev."), "不显示版本号");
    }

    // ---------------------------------------------------------------- 列表与查看范围

    @Test
    void quotationDetailListsPis_pageFilters_dataScope_partTimerDenied() throws Exception {
        long c = customer("Pacific Controls", "Australia");
        long q = quotation(c, 2, "USD", null, FOUR);
        List<Long> ids = quotationItemIds(q);
        long mine = ok(createPi(ids.subList(0, 2))).path("id").asLong();
        long others = ok(createPi(ids.subList(2, 4))).path("id").asLong();
        JsonNode listed = ok(call(get(PI + "/by-quotation/" + q), admin));
        assertEquals(2, listed.size(), "报价单详情列出两张 PI");
        assertEquals("未付款", listed.get(0).path("receiptStatusName").asText());

        JsonNode byModel = ok(call(json(post(PI + "/page"), "{\"keyword\":\"6AV2123\"}"), admin));
        assertEquals(1, byModel.path("total").asInt(), "按型号搜索");
        assertEquals(others, byModel.path("records").get(0).path("id").asLong());
        JsonNode row = byModel.path("records").get(0);
        assertEquals(4, row.path("totalQuantity").asInt(), "两行各 2 件");
        assertEquals("Australia", row.path("customerCountry").asText());
        assertEquals(1, row.path("customerType").asInt(), "来源询盘为新客户");
        String no = ok(call(get(PI + "/" + mine), admin)).path("piNo").asText();
        JsonNode byNo = ok(call(json(post(PI + "/page"), write(Map.of("keyword", no.substring(2)))), admin));
        assertEquals(1, byNo.path("total").asInt(), "不带前缀的编号也能搜到");
        assertEquals(2, ok(call(json(post(PI + "/page"), "{\"status\":1}"), admin)).path("total").asInt());
        assertEquals(2, ok(call(get(PI + "/stats"), admin)).path("draftCount").asInt());

        jdbc.update("update proforma_invoice set owner_id = ? where id = ?", BUYER_LIN, others);
        loginWithResources("it_pi_staff", 100078);
        String staff = token("it_pi_staff");
        assertEquals("PI 不存在", call(get(PI + "/" + others), staff).path("message").asText());
        JsonNode page = ok(call(json(post(PI + "/page"), "{}"), staff));
        assertEquals(1, page.path("total").asInt());
        assertEquals(mine, page.path("records").get(0).path("id").asLong());
        assertEquals(403, perform(json(post(PI + "/page"), "{}"), token("it_wang")).getStatus(), "兼职采购不能访问 PI");
    }
}

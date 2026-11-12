package com.zhul.erp.modules.sales;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.zhul.erp.support.TestDates.today;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** spec sales/sales-order、quotation/quotation（成交由订单推进）、inquiry/inquiry-intake（成交推进询盘）、system/document-numbering（链路与搜索） */
class SalesOrderContractTest extends SalesContractSupport {

    private static final L[] FOUR = {l("6ES7214-1AG40-0XB0", 1, "4500", "740.44"), l("6ES7231-4HD32-0XB0", 2, "1230", "180.00"),
            l("6AV2123-2GB03-0AX0", 2, "420", "65.73"), l("6SL3210-1KE21-3UF1", 2, "350", "54.39")};

    private JsonNode convert(long piId) throws Exception {
        return call(post(PI + "/" + piId + "/convert"), admin);
    }

    private JsonNode cancel(long soId, String reason) throws Exception {
        return call(json(post(SO + "/" + soId + "/cancel"), write(Map.of("reason", reason))), admin);
    }

    private int quotationStatus(long id) {
        return jdbc.queryForObject("select status from quotation where id = ?", Integer.class, id);
    }

    private int inquiryStatus(long id) {
        return jdbc.queryForObject("select status from customer_inquiry where id = ?", Integer.class, id);
    }

    @Test
    void convertBySlip_wholeQuotationWon_inquiryWon_noDuplicate() throws Exception {
        long c = customer("ACROBOT", "India");
        long q = quotation(c, 2, "USD", "60", FOUR);
        long pi = sentPi(quotationItemIds(q), 5);
        assertEquals("客户还没有付款，要先采购请选择「先采购后收款」并填写原因", fail(convert(pi)).path("message").asText());
        ok(uploadSlip(pi, "667.00", "2026-10-07", admin));
        JsonNode so = ok(convert(pi));
        assertEquals("SO" + today() + "001", so.path("soNo").asText(), "内部单据不带前缀");
        assertEquals("待到账", so.path("receiptStatusName").asText());
        assertEquals(4, so.path("items").size());
        money("1333.65", so.path("totalAmount"));
        money("67.03", so.path("discountAmount"));
        assertEquals(3, ok(call(get(PI + "/" + pi), admin)).path("status").asInt(), "PI 已转订单");
        assertEquals(3, quotationStatus(q), "全部型号成交 → 已成交");
        assertEquals(8, inquiryStatus(inquiryOf(q)), "成交推进询盘");
        assertEquals("PI 已转成订单 SO" + today() + "001", fail(convert(pi)).path("message").asText());
    }

    /** spec sales/sales-order「订单列表查看全部型号」：分页，顺序与明细一致，带币种；看不到的订单不能查 */
    @Test
    void orderModels_paged_scoped() throws Exception {
        long c = customer("ACROBOT", "India");
        long pi = sentPi(quotationItemIds(quotation(c, 2, "USD", "60", FOUR)), 5);
        ok(uploadSlip(pi, "667.00", "2026-10-07", admin));
        JsonNode so = ok(convert(pi));
        long id = so.path("id").asLong();
        JsonNode p1 = ok(call(get(SO + "/" + id + "/models").param("page", "1").param("pageSize", "3"), admin));
        assertEquals(4, p1.path("total").asInt());
        assertEquals(3, p1.path("records").size());
        assertEquals(so.path("items").get(0).path("model").asText(), p1.path("records").get(0).path("model").asText());
        assertEquals("USD", p1.path("records").get(0).path("currencyCode").asText());
        JsonNode p2 = ok(call(get(SO + "/" + id + "/models").param("page", "2").param("pageSize", "3"), admin));
        assertEquals(so.path("items").get(3).path("model").asText(), p2.path("records").get(0).path("model").asText());
        jdbc.update("update sales_order set owner_id = ? where id = ?", BUYER_LIN, id);
        loginWithResources("it_so_staff", MENU_SO);
        assertEquals("销售订单不存在", call(get(SO + "/" + id + "/models"), token("it_so_staff")).path("message").asText());
    }

    /** UI-015：订单列表按所属事业部筛选，与销售统计同一口径；只在可见订单内缩小 */
    @Test
    void page_filterByBusinessUnit() throws Exception {
        long c = customer("ACROBOT", "India");
        long[] ids = new long[2];
        for (int i = 0; i < 2; i++) {
            long pi = sentPi(quotationItemIds(quotation(c, 2, "USD", null, FOUR[0])), null);
            ok(uploadSlip(pi, "100.00", "2026-10-07", admin));
            ids[i] = ok(convert(pi)).path("id").asLong();
        }
        jdbc.update("update sales_order set business_unit_id = 7 where id = ?", ids[0]);
        jdbc.update("update sales_order set business_unit_id = 8 where id = ?", ids[1]);
        JsonNode unit7 = ok(call(json(post(SO + "/page"), write(Map.of("businessUnitId", 7))), admin));
        assertEquals(1, unit7.path("total").asInt());
        assertEquals(ids[0], unit7.path("records").get(0).path("id").asLong());
        assertEquals(2, ok(call(json(post(SO + "/page"), "{}"), admin)).path("total").asInt(), "不传事业部不过滤");

        jdbc.update("update sales_order set owner_id = ? where id = ?", BUYER_LIN, ids[0]);
        loginWithResources("it_so_unit", MENU_SO);
        assertEquals(0, ok(call(json(post(SO + "/page"), write(Map.of("businessUnitId", 7))), token("it_so_unit"))).path("total").asInt(),
                "看不到的订单不会因事业部条件出现");
    }

    /** BUS-026：收款状态筛选改为子查询后结果不变——PI 转成的订单按 PI 的收款状态 */
    @Test
    void page_filterByReceiptStatus_piSubquery() throws Exception {
        long c = customer("ACROBOT", "India");
        long pi = sentPi(quotationItemIds(quotation(c, 2, "USD", null, FOUR[0])), null);
        ok(uploadSlip(pi, "100.00", "2026-10-07", admin));
        long id = ok(convert(pi)).path("id").asLong();
        int status = jdbc.queryForObject("select receipt_status from proforma_invoice where id = ?", Integer.class, pi);
        JsonNode hit = ok(call(json(post(SO + "/page"), write(Map.of("receiptStatus", status))), admin));
        assertEquals(1, hit.path("total").asInt());
        assertEquals(id, hit.path("records").get(0).path("id").asLong());
        int other = status == 1 ? 2 : 1;
        assertEquals(0, ok(call(json(post(SO + "/page"), write(Map.of("receiptStatus", other))), admin)).path("total").asInt());
    }

    /** 已发送、有水单、PI 当前版本备注为 remark 的 PI */
    private long paidPiWithRemark(long customer, String remark) throws Exception {
        long pi = sentPi(quotationItemIds(quotation(customer, 2, "USD", null, FOUR[0])), null);
        ok(uploadSlip(pi, "100.00", "2026-10-07", admin));
        jdbc.update("update proforma_invoice_version v join proforma_invoice p on p.id = v.pi_id and v.version_no = p.current_version_no"
                + " set v.remark = ? where p.id = ?", remark, pi);
        return pi;
    }

    /** spec sales/sales-order「由 PI 转成销售订单」：转订单时填写备注与附件（feedback-1109） */
    @Test
    void convert_remarkAndAttachments() throws Exception {
        long c = customer("Ahmed Trading", "United Arab Emirates");
        long file = ok(call(multipart("/api/v1/attachments")
                .file(new MockMultipartFile("file", "客户PO.pdf", "application/pdf", "%PDF-1.4\n%test".getBytes(StandardCharsets.US_ASCII)))
                .param("ownerType", "SALES_ORDER"), admin)).path("id").asLong();
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("remark", " 客户要原装新品，采购时确认生产日期 ");
        b.put("attachmentIds", List.of(file));
        JsonNode changed = ok(call(json(post(PI + "/" + paidPiWithRemark(c, "Prices valid for 15 days.") + "/convert"), write(b)), admin));
        assertEquals("客户要原装新品，采购时确认生产日期", changed.path("remark").asText(), "改过的备注");
        assertEquals(1, changed.path("attachments").size());
        assertEquals("客户PO.pdf", changed.path("attachments").get(0).path("fileName").asText());

        JsonNode cleared = ok(call(json(post(PI + "/" + paidPiWithRemark(c, "Prices valid for 15 days.") + "/convert"),
                write(Map.of("remark", ""))), admin));
        assertEquals("", cleared.path("remark").asText(), "清空备注");
        assertEquals(0, cleared.path("attachments").size());

        JsonNode kept = ok(convert(paidPiWithRemark(c, "Prices valid for 15 days.")));
        assertEquals("Prices valid for 15 days.", kept.path("remark").asText(), "不传备注时沿用 PI 备注");

        assertTrue(fail(call(json(post(PI + "/" + paidPiWithRemark(c, "") + "/convert"), write(Map.of("remark", "x".repeat(501)))), admin))
                .path("message").asText().contains("500"));
    }

    @Test
    void payLater_unpaidPiConverts_withReason_flagAndFilter() throws Exception {
        long c = customer("ACROBOT", "India");
        long q = quotation(c, 2, "USD", null, FOUR);
        long pi = sentPi(quotationItemIds(q), null);
        String noReason = "客户还没有付款，要先采购请选择「先采购后收款」并填写原因";
        assertEquals(noReason, fail(call(json(post(PI + "/" + pi + "/convert"), write(Map.of("payLater", false))), admin)).path("message").asText());
        assertEquals(noReason, fail(call(json(post(PI + "/" + pi + "/convert"), write(Map.of("payLater", true, "payLaterReason", "  "))), admin))
                .path("message").asText());
        assertTrue(fail(call(json(post(PI + "/" + pi + "/convert"), write(Map.of("payLater", true, "payLaterReason", "x".repeat(201)))), admin))
                .path("message").asText().contains("不能超过 200"));

        JsonNode so = ok(call(json(post(PI + "/" + pi + "/convert"), write(Map.of("payLater", true, "payLaterReason", "老客户，月底前付款"))), admin));
        long id = so.path("id").asLong();
        assertTrue(so.path("payLater").asBoolean());
        assertEquals("老客户，月底前付款", so.path("payLaterReason").asText());
        assertFalse(so.path("payLaterByName").asText("").isEmpty(), "记录操作人");
        assertFalse(so.path("payLaterAt").isNull());
        assertEquals("未付款", so.path("receiptStatusName").asText());
        assertEquals(java.time.LocalDate.now().toString(), so.path("salesDate").asText(), "没有水单与到账时销售日期取当天");
        assertEquals(4, jdbc.queryForObject("select count(*) from purchase_requirement where so_id = ?", Integer.class, id), "采购需求照常生成");

        // 列表标记与「先采购后收款未收款」筛选
        JsonNode open = ok(call(json(post(SO + "/page"), write(Map.of("payLaterOpen", true))), admin));
        assertEquals(1, open.path("total").asInt());
        assertTrue(open.path("records").get(0).path("payLater").asBoolean());
        // 有了水单后不再算未收款，标记保留
        ok(uploadSlip(pi, "500.00", java.time.LocalDate.now().toString(), admin));
        assertEquals(0, ok(call(json(post(SO + "/page"), write(Map.of("payLaterOpen", true))), admin)).path("total").asInt());
        JsonNode after = ok(call(get(SO + "/" + id), admin));
        assertTrue(after.path("payLater").asBoolean());
        assertEquals("待到账", after.path("receiptStatusName").asText());

        // 有水单的 PI 转订单时忽略先采购后收款
        long q2 = quotation(c, 2, "USD", null, FOUR);
        long pi2 = sentPi(quotationItemIds(q2), null);
        ok(uploadSlip(pi2, "100.00", java.time.LocalDate.now().toString(), admin));
        JsonNode so2 = ok(call(json(post(PI + "/" + pi2 + "/convert"), write(Map.of("payLater", true, "payLaterReason", "不该记"))), admin));
        assertFalse(so2.path("payLater").asBoolean());
        assertEquals("", jdbc.queryForObject("select pay_later_reason from sales_order where id = ?", String.class, so2.path("id").asLong()));
    }

    @Test
    void partialDeal_thenCancelRollsBack_reasonRequired() throws Exception {
        long c = customer("ACROBOT", "India");
        long q = quotation(c, 2, "USD", null, FOUR);
        List<Long> lines = quotationItemIds(q);
        long pi = sentPi(lines.subList(0, 3), null);
        ok(confirmReceipt(pi, "500", null, false, admin));
        long so = ok(convert(pi)).path("id").asLong();
        assertEquals(6, quotationStatus(q), "部分成交");
        assertEquals(0, jdbc.queryForObject("select won from quotation_item where id = ?", Integer.class, lines.get(3)));
        JsonNode cand = ok(call(get(PI + "/candidates/quotations/" + q), admin));
        assertTrue(cand.path("items").get(0).path("won").asBoolean());
        assertEquals("部分成交", cand.path("statusName").asText(), "部分成交的报价单还能就剩余型号开 PI");
        assertEquals(8, inquiryStatus(inquiryOf(q)));

        assertEquals("请填写取消原因", fail(cancel(so, " ")).path("message").asText());
        JsonNode cancelled = ok(cancel(so, "客户取消"));
        assertEquals(2, cancelled.path("status").asInt());
        assertEquals("客户取消", cancelled.path("cancelReason").asText());
        assertEquals(2, quotationStatus(q), "没有有效订单 → 回到已发送");
        assertEquals(7, inquiryStatus(inquiryOf(q)), "询盘回到已报价");
        JsonNode piAfter = ok(call(get(PI + "/" + pi), admin));
        assertEquals(2, piAfter.path("status").asInt(), "PI 解锁");
        money("500", piAfter.path("receivedAmount"));
    }

    @Test
    void customerAddsModel_cancelReviseReconvert_receiptsKept_chain_searchBySlipNumber() throws Exception {
        long c = customer("ACROBOT", "India");
        long q = quotation(c, 2, "USD", null, FOUR);
        List<Long> lines = quotationItemIds(q);
        long pi = sentPi(lines.subList(0, 3), null);
        ok(confirmReceipt(pi, "500", null, false, admin));
        long so1 = ok(convert(pi)).path("id").asLong();
        assertEquals(1, ok(call(get(SO + "/" + so1), admin)).path("receipts").size(), "在订单上看收款");

        ok(cancel(so1, "客户追加型号"));
        ok(call(post(PI + "/" + pi + "/revise"), admin));
        ok(call(json(post(PI + "/" + pi + "/items"), write(Map.of("items", List.of(Map.of("quotationItemId", lines.get(3)))))), admin));
        ok(send(pi));
        JsonNode so2 = ok(convert(pi));
        assertEquals("SO" + today() + "002", so2.path("soNo").asText());
        assertEquals(4, so2.path("items").size());
        assertEquals(2, so2.path("piVersionNo").asInt());
        assertEquals(1, so2.path("receipts").size(), "取消前登记的到账仍在");
        money("500", so2.path("receipts").get(0).path("amount"));
        assertEquals(3, quotationStatus(q), "按新订单重新计算 → 已成交");

        // 从询盘看到报价单 → PI → 订单
        JsonNode chain = ok(call(get("/api/v1/sales/chain").param("type", "inquiry").param("id", String.valueOf(inquiryOf(q))), admin));
        assertEquals("FWQT" + today() + "001", chain.path("quotations").get(0).path("no").asText());
        assertEquals(1, chain.path("pis").size());
        assertEquals(2, chain.path("orders").size());
        assertTrue(chain.path("inquiries").get(0).path("current").asBoolean());

        // 客户水单上写的是不带前缀的 PI 编号
        String piNo = ok(call(get(PI + "/" + pi), admin)).path("piNo").asText();
        JsonNode found = ok(call(json(post(SO + "/page"), write(Map.of("keyword", piNo.substring(2)))), admin));
        assertEquals(2, found.path("total").asInt());
        assertEquals(7, found.path("records").get(0).path("totalQuantity").asInt(), "1 + 2 + 2 + 2");
        assertEquals("India", found.path("records").get(0).path("customerCountry").asText());
        assertEquals(1, ok(call(json(post(SO + "/page"), "{\"status\":1}"), admin)).path("total").asInt());
    }

    @Test
    void orderDataScope_partTimerDenied() throws Exception {
        long c = customer("ACROBOT", "India");
        long pi = sentPi(quotationItemIds(quotation(c, 2, "USD", null, FOUR[0])), null);
        ok(uploadSlip(pi, "100", "2026-10-07", admin));
        long so = ok(convert(pi)).path("id").asLong();
        jdbc.update("update sales_order set owner_id = ? where id = ?", BUYER_LIN, so);
        loginWithResources("it_so_staff", MENU_SO);
        String staff = token("it_so_staff");
        assertEquals("销售订单不存在", call(get(SO + "/" + so), staff).path("message").asText());
        assertEquals(0, ok(call(json(post(SO + "/page"), "{}"), staff)).path("total").asInt());
        assertEquals(403, perform(json(post(SO + "/page"), "{}"), token("it_wang")).getStatus(), "兼职采购不能访问销售订单");
    }
}

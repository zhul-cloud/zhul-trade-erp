package com.zhul.erp.modules.sales;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
        assertEquals("请先上传客户的付款水单，或等财务登记到账", fail(convert(pi)).path("message").asText());
        ok(uploadSlip(pi, "667.00", "2026-10-07", admin));
        JsonNode so = ok(convert(pi));
        assertEquals("SO" + TODAY + "001", so.path("soNo").asText(), "内部单据不带前缀");
        assertEquals("待到账", so.path("receiptStatusName").asText());
        assertEquals(4, so.path("items").size());
        money("1333.65", so.path("totalAmount"));
        money("67.03", so.path("discountAmount"));
        assertEquals(3, ok(call(get(PI + "/" + pi), admin)).path("status").asInt(), "PI 已转订单");
        assertEquals(3, quotationStatus(q), "全部型号成交 → 已成交");
        assertEquals(8, inquiryStatus(inquiryOf(q)), "成交推进询盘");
        assertEquals("PI 已转成订单 SO" + TODAY + "001", fail(convert(pi)).path("message").asText());
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
        assertEquals("SO" + TODAY + "002", so2.path("soNo").asText());
        assertEquals(4, so2.path("items").size());
        assertEquals(2, so2.path("piVersionNo").asInt());
        assertEquals(1, so2.path("receipts").size(), "取消前登记的到账仍在");
        money("500", so2.path("receipts").get(0).path("amount"));
        assertEquals(3, quotationStatus(q), "按新订单重新计算 → 已成交");

        // 从询盘看到报价单 → PI → 订单
        JsonNode chain = ok(call(get("/api/v1/sales/chain").param("type", "inquiry").param("id", String.valueOf(inquiryOf(q))), admin));
        assertEquals("FWQT" + TODAY + "001", chain.path("quotations").get(0).path("no").asText());
        assertEquals(1, chain.path("pis").size());
        assertEquals(2, chain.path("orders").size());
        assertTrue(chain.path("inquiries").get(0).path("current").asBoolean());

        // 客户水单上写的是不带前缀的 PI 编号
        String piNo = ok(call(get(PI + "/" + pi), admin)).path("piNo").asText();
        JsonNode found = ok(call(json(post(SO + "/page"), write(Map.of("keyword", piNo.substring(2)))), admin));
        assertEquals(2, found.path("total").asInt());
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

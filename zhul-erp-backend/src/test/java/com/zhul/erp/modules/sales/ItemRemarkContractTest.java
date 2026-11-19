package com.zhul.erp.modules.sales;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * openspec item-remark：业务员在询盘型号上写的备注随型号显示在报价单、PI、销售订单、采购需求与采购单上（始终是最新内容）；
 * 采购单已下单后改备注，采购单行标红，采购核对后清除；手动订单的型号在订单上直接写备注。
 */
class ItemRemarkContractTest extends SalesContractSupport {

    private static final String INQ = "/api/v1/inquiry/customer-inquiries";
    private static final String QT = "/api/v1/quotations";
    private static final String PO = "/api/v1/purchase/orders";

    private long inquiryItem(long quotationId, int lineNo) {
        return jdbc.queryForObject("select inquiry_item_id from quotation_item where quotation_id = ? and line_no = ? and is_current = 1",
                Long.class, quotationId, lineNo);
    }

    private JsonNode setRemark(long inquiryId, long itemId, String value) throws Exception {
        return call(json(put(INQ + "/" + inquiryId + "/items/" + itemId + "/remark"), write(Map.of("remark", value))), admin);
    }

    @Test
    void remark_showsOnEveryDoc_orderedPurchaseAlertedUntilChecked() throws Exception {
        long c = customer("PRIME CONTROLS", "United States");
        long q = quotation(c, 2, "USD", null, l("LC2D18B7", 20, "96", "18.50"), l("E3Z-D61", 10, "85", "16.20"));
        long inq = inquiryOf(q);
        long item1 = inquiryItem(q, 1);

        // 询盘详情写备注：去首尾空格，记修改人与时间
        JsonNode saved = ok(setRemark(inq, item1, "  要 AC220V 线圈 "));
        assertEquals("要 AC220V 线圈", saved.path("remark").asText());
        assertFalse(saved.path("remarkBy").asText().isEmpty(), "记录修改人");
        assertFalse(saved.path("remarkAt").isNull(), "记录修改时间");
        JsonNode inquiry = ok(call(get(INQ + "/" + inq), admin));
        assertEquals("要 AC220V 线圈", inquiry.path("items").get(0).path("itemRemark").asText());
        assertEquals("", inquiry.path("items").get(1).path("itemRemark").asText());
        assertEquals("型号备注不能超过 200 个字", fail(setRemark(inq, item1, "长".repeat(201))).path("message").asText());

        // 报价单、PI 显示（实时读取询盘型号）
        assertEquals("要 AC220V 线圈", ok(call(get(QT + "/" + q), admin)).path("items").get(0).path("itemRemark").asText());
        long pi = sentPi(quotationItemIds(q), null);
        assertEquals("要 AC220V 线圈", ok(call(get(PI + "/" + pi), admin)).path("version").path("items").get(0).path("itemRemark").asText());

        // 转订单：订单、采购需求、采购单都显示
        ok(uploadSlip(pi, "532.00", "2026-11-18", admin));
        long soId = ok(call(post(PI + "/" + pi + "/convert"), admin)).path("id").asLong();
        assertEquals("要 AC220V 线圈", order(soId).path("items").get(0).path("itemRemark").asText());
        List<Long> reqs = requirementIds(soId);
        long sup = supplier("上海锐控电气", null);
        long draftPo = generate(sup, List.of(reqs.get(0)), admin);
        assertEquals("要 AC220V 线圈", po(draftPo, admin).path("items").get(0).path("itemRemark").asText());

        // 草稿采购单：改备注直接显示最新内容，不标红
        ok(setRemark(inq, item1, "要 AC220V 线圈，带 2 个辅助触点"));
        JsonNode draft = po(draftPo, admin);
        assertEquals("要 AC220V 线圈，带 2 个辅助触点", draft.path("items").get(0).path("itemRemark").asText());
        assertFalse(draft.path("items").get(0).path("remarkAlert").asBoolean());

        // 下单后再改：标红并写采购单日志；核对后清除
        priceAndConfirm(draftPo, "92", admin);
        ok(setRemark(inq, item1, "改为 DC24V 线圈"));
        JsonNode ordered = po(draftPo, admin);
        assertEquals("改为 DC24V 线圈", ordered.path("items").get(0).path("itemRemark").asText());
        assertTrue(ordered.path("items").get(0).path("remarkAlert").asBoolean());
        assertEquals(1, ordered.path("remarkAlerts").asInt());
        assertEquals(1, jdbc.queryForObject("select count(*) from purchase_order_log where po_id = ? and action = '型号备注更新'",
                Integer.class, draftPo));
        JsonNode checked = ok(call(post(PO + "/" + draftPo + "/remark-checked"), admin));
        assertFalse(checked.path("items").get(0).path("remarkAlert").asBoolean());
        assertEquals(0, checked.path("remarkAlerts").asInt());

        // 从订单改（有询盘的型号改的是询盘型号的备注）
        long soItem1 = jdbc.queryForObject("select id from sales_order_item where so_id = ? and line_no = 1", Long.class, soId);
        ok(call(json(put(SO + "/" + soId + "/items/" + soItem1 + "/remark"), write(Map.of("remark", ""))), admin));
        assertEquals("", ok(call(get(INQ + "/" + inq), admin)).path("items").get(0).path("itemRemark").asText(), "清空");

        // 询盘已取消：拒绝
        jdbc.update("update customer_inquiry set status = 10 where id = ?", inq);
        assertEquals("询盘已取消，不能修改", fail(setRemark(inq, item1, "x")).path("message").asText());
    }

    /** 没有询盘的手动订单型号：在订单上直接写备注 */
    @Test
    void manualOrderLine_remarkOnOrder() throws Exception {
        long c = customer("PRIME CONTROLS", "United States");
        long q = quotation(c, 2, "USD", null, l("LC2D18B7", 20, "96", "18.50"));
        long pi = sentPi(quotationItemIds(q), null);
        ok(uploadSlip(pi, "370.00", "2026-11-18", admin));
        long soId = ok(call(post(PI + "/" + pi + "/convert"), admin)).path("id").asLong();
        long soItem = jdbc.queryForObject("select id from sales_order_item where so_id = ? and line_no = 1", Long.class, soId);
        // 模拟手动订单的型号：没有询盘来源
        jdbc.update("update sales_order_item set inquiry_item_id = 0 where id = ?", soItem);
        JsonNode saved = ok(call(json(put(SO + "/" + soId + "/items/" + soItem + "/remark"), write(Map.of("remark", "原装进口"))), admin));
        assertEquals("原装进口", saved.path("remark").asText());
        assertEquals("原装进口", order(soId).path("items").get(0).path("itemRemark").asText());
        assertEquals("原装进口", jdbc.queryForObject("select remark from sales_order_item where id = ?", String.class, soItem),
                "存在订单型号上");
    }

    private JsonNode order(long soId) throws Exception {
        return ok(call(get(SO + "/" + soId), admin));
    }
}

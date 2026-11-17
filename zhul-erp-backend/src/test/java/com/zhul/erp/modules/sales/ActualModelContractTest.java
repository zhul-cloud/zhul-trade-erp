package com.zhul.erp.modules.sales;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.modules.purchase.support.RequirementLifecycle;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * openspec actual-model-and-description：单据型号以真实型号为准（报价单、PI、销售订单、采购需求、采购订单），
 * 还没发给外部的单据跟着改，已发出的只提示；描述带到采购侧。
 */
class ActualModelContractTest extends SalesContractSupport {

    private static final String INQ = "/api/v1/inquiry/customer-inquiries";
    private static final String QT = "/api/v1/quotations";

    @SpyBean
    private RequirementLifecycle requirementLifecycle;

    private long inquiryItem(long quotationId, int lineNo) {
        return jdbc.queryForObject("select inquiry_item_id from quotation_item where quotation_id = ? and line_no = ? and is_current = 1",
                Long.class, quotationId, lineNo);
    }

    private JsonNode setActual(long inquiryId, long itemId, String value) throws Exception {
        return call(json(put(INQ + "/" + inquiryId + "/items/" + itemId + "/actual-model"), write(Map.of("actualModel", value))), admin);
    }

    private String quotationModel(long quotationId, int lineNo) {
        return jdbc.queryForObject("select model from quotation_item where quotation_id = ? and line_no = ? and is_current = 1",
                String.class, quotationId, lineNo);
    }

    @Test
    void quotation_draftFollows_sentOnlyHints_reviseTakesActual() throws Exception {
        long c = customer("PRIME CONTROLS", "United States");
        long draft = quotation(c, 1, "USD", null, l("LC2D18B7", 20, "96", "18.50"), l("6ES7214-1AG40-0XB0", 1, "2640", "468"));
        long sent = quotation(c, 2, "USD", null, l("LC2D18B8", 5, "96", "18.50"));

        // 业务员在询盘详情填写：返回单据型号、客户型号与修改人
        JsonNode saved = ok(setActual(inquiryOf(draft), inquiryItem(draft, 1), "  LC2D18B7C "));
        assertEquals("LC2D18B7C", saved.path("actualModel").asText());
        assertEquals("LC2D18B7C", saved.path("docModel").asText());
        assertEquals("LC2D18B7", saved.path("customerModel").asText());
        assertFalse(saved.path("actualModelBy").asText().isEmpty(), "记录修改人");
        assertFalse(saved.path("actualModelAt").isNull(), "记录修改时间");

        // 草稿跟着改，详情显示客户型号；没填的行不显示客户型号
        assertEquals("LC2D18B7C", quotationModel(draft, 1));
        JsonNode detail = ok(call(get(QT + "/" + draft), admin));
        assertEquals("LC2D18B7", detail.path("items").get(0).path("customerModel").asText());
        assertEquals("", detail.path("items").get(1).path("customerModel").asText(""));

        // 只差大小写与符号视为同一型号，不显示客户型号
        ok(setActual(inquiryOf(draft), inquiryItem(draft, 2), "6es7214 1ag40 0xb0"));
        assertEquals("", ok(call(get(QT + "/" + draft), admin)).path("items").get(1).path("customerModel").asText(""));

        // 报价单草稿里清空：型号回到确认型号
        long line1 = jdbc.queryForObject("select id from quotation_item where quotation_id = ? and line_no = 1", Long.class, draft);
        JsonNode cleared = ok(call(json(put(QT + "/" + draft + "/items/" + line1 + "/actual-model"), write(Map.of("actualModel", ""))), admin));
        assertEquals("LC2D18B7", cleared.path("docModel").asText());
        assertEquals("", cleared.path("actualModelBy").asText());
        assertEquals("LC2D18B7", quotationModel(draft, 1));

        // 已发送的报价单：不能在报价单里改；从询盘改后内容不变、详情提示，出新版本时取真实型号
        long sentLine = jdbc.queryForObject("select id from quotation_item where quotation_id = ?", Long.class, sent);
        assertEquals("报价单已发送，请出新版本后再改真实型号", fail(call(json(put(QT + "/" + sent + "/items/" + sentLine + "/actual-model"),
                write(Map.of("actualModel", "X"))), admin)).path("message").asText());
        ok(setActual(inquiryOf(sent), inquiryItem(sent, 1), "LC2D18B8C"));
        assertEquals("LC2D18B8", quotationModel(sent, 1));
        assertEquals("LC2D18B8C", ok(call(get(QT + "/" + sent), admin)).path("items").get(0).path("modelUpdatedTo").asText());
        JsonNode rev = ok(call(post(QT + "/" + sent + "/revise"), admin));
        assertEquals("LC2D18B8C", rev.path("items").get(0).path("model").asText());
        assertEquals("LC2D18B8", rev.path("items").get(0).path("customerModel").asText());

        // 询盘已取消：拒绝
        jdbc.update("update customer_inquiry set status = 10 where id = ?", inquiryOf(draft));
        assertEquals("询盘已取消，不能修改", fail(setActual(inquiryOf(draft), inquiryItem(draft, 1), "LC2D18B7C")).path("message").asText());
    }

    @Test
    void piOrderPurchase_followUntilSentOrOrdered_descriptionCarried() throws Exception {
        long c = customer("PRIME CONTROLS", "United States");
        long q = quotation(c, 2, "USD", null, l("LC2D18B7", 20, "96", "18.50"), l("E3Z-D61", 10, "85", "16.20"));
        jdbc.update("update quotation_item set description = '接触器 18A，线圈 AC24V' where quotation_id = ? and line_no = 1", q);
        long inq = inquiryOf(q);
        long item1 = inquiryItem(q, 1);
        long item2 = inquiryItem(q, 2);
        long pi = sentPi(quotationItemIds(q), null);

        // 已发送的 PI：不改，提示
        ok(setActual(inq, item1, "LC2D18B7C"));
        JsonNode piDetail = ok(call(get(PI + "/" + pi), admin));
        assertEquals("LC2D18B7", piDetail.path("version").path("items").get(0).path("model").asText());
        assertEquals("LC2D18B7C", piDetail.path("version").path("items").get(0).path("modelUpdatedTo").asText());

        // PI 的新版本（编辑中）跟着改
        JsonNode revised = ok(call(post(PI + "/" + pi + "/revise"), admin));
        assertEquals("LC2D18B7C", revised.path("version").path("items").get(0).path("model").asText());
        assertEquals("LC2D18B7", revised.path("version").path("items").get(0).path("customerModel").asText());
        ok(setActual(inq, item1, "LC2D18B7D"));
        assertEquals("LC2D18B7D", ok(call(get(PI + "/" + pi), admin)).path("version").path("items").get(0).path("model").asText());
        ok(call(post(PI + "/" + pi + "/abandon"), admin));

        // 转订单：订单按真实型号建，需求带描述
        ok(uploadSlip(pi, "532.00", "2026-11-18", admin));
        JsonNode so = ok(call(post(PI + "/" + pi + "/convert"), admin));
        long soId = so.path("id").asLong();
        assertEquals("LC2D18B7D", so.path("items").get(0).path("model").asText());
        assertEquals("LC2D18B7", so.path("items").get(0).path("customerModel").asText());
        assertEquals("接触器 18A，线圈 AC24V", so.path("items").get(0).path("description").asText());
        List<Long> reqs = requirementIds(soId);
        assertEquals("接触器 18A，线圈 AC24V", jdbc.queryForObject("select description from purchase_requirement where id = ?", String.class, reqs.get(0)));

        // 第 1 行下单给供应商，第 2 行留在草稿
        long sup = supplier("上海锐控电气", null);
        long ordered = generate(sup, List.of(reqs.get(0)), admin);
        assertEquals("接触器 18A，线圈 AC24V", po(ordered, admin).path("items").get(0).path("description").asText());
        priceAndConfirm(ordered, "92", admin);
        long draftPo = generate(sup, List.of(reqs.get(1)), admin);

        // 订单行与需求跟着改；已下单的采购订单不改，详情提示并写日志
        ok(setActual(inq, item1, "LC2D18B7E"));
        assertEquals("LC2D18B7E", jdbc.queryForObject("select model from sales_order_item where so_id = ? and line_no = 1", String.class, soId));
        assertEquals("LC2D18B7E", jdbc.queryForObject("select model from purchase_requirement where id = ?", String.class, reqs.get(0)));
        JsonNode orderedPo = po(ordered, admin);
        assertEquals("LC2D18B7D", orderedPo.path("items").get(0).path("model").asText());
        assertEquals("LC2D18B7E", orderedPo.path("items").get(0).path("modelUpdatedTo").asText());
        assertTrue(jdbc.queryForObject("select count(*) from purchase_order_log where po_id = ? and action = '真实型号更新' and content like '%本单未修改%'",
                Integer.class, ordered) == 1);

        // 草稿采购订单跟着改并写日志
        ok(setActual(inq, item2, "E3Z-D61X"));
        assertEquals("E3Z-D61X", po(draftPo, admin).path("items").get(0).path("model").asText());
        assertEquals(1, jdbc.queryForObject("select count(*) from purchase_order_log where po_id = ? and action = '真实型号更新'",
                Integer.class, draftPo));

        // 已出库的订单行不改
        jdbc.update("insert into outbound_order (tenant_id, ob_no, status) values (0, 'ITOB1', 1)");
        long ob = jdbc.queryForObject("select id from outbound_order where ob_no = 'ITOB1'", Long.class);
        long soItem2 = jdbc.queryForObject("select id from sales_order_item where so_id = ? and line_no = 2", Long.class, soId);
        jdbc.update("insert into outbound_order_item (tenant_id, outbound_id, so_item_id, model, brand, quantity) values (0, ?, ?, 'E3Z-D61X', 'Siemens', 10)",
                ob, soItem2);
        ok(setActual(inq, item2, "E3Z-D61Y"));
        assertEquals("E3Z-D61X", jdbc.queryForObject("select model from sales_order_item where id = ?", String.class, soItem2));
    }

    @Test
    void downstreamFailure_rollsBackEverything() throws Exception {
        long c = customer("PRIME CONTROLS", "United States");
        long q = quotation(c, 2, "USD", null, l("LC2D18B7", 20, "96", "18.50"));
        long pi = sentPi(quotationItemIds(q), null);
        ok(uploadSlip(pi, "370.00", "2026-11-18", admin));
        long soId = ok(call(post(PI + "/" + pi + "/convert"), admin)).path("id").asLong();
        doThrow(new IllegalStateException("采购同步失败")).when(requirementLifecycle).onSoItemModelChanged(anyMap(), any());
        try {
            assertTrue(call(json(put(INQ + "/" + inquiryOf(q) + "/items/" + inquiryItem(q, 1) + "/actual-model"),
                    write(Map.of("actualModel", "LC2D18B7C"))), admin).path("code").asInt() != 0);
        } finally {
            reset(requirementLifecycle);
        }
        assertEquals("", jdbc.queryForObject("select actual_model from inquiry_item where id = ?", String.class, inquiryItem(q, 1)));
        assertEquals("LC2D18B7", jdbc.queryForObject("select model from sales_order_item where so_id = ?", String.class, soId));
    }
}

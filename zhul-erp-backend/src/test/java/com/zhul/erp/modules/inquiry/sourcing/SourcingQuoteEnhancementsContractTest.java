package com.zhul.erp.modules.inquiry.sourcing;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.modules.inquiry.InquiryContractSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** spec inquiry/sourcing-quote：回价包邮与附件、我的询价搜索、历史询价；purchase/purchase-order：详情行现货 / 期货 */
class SourcingQuoteEnhancementsContractTest extends InquiryContractSupport {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R'};
    private static final String MODEL = "6ES7214-1AG40-0XB0";

    private String admin;

    @AfterEach
    void cleanupOrders() {
        jdbc.update("delete from purchase_order_item where tenant_id = 0");
        jdbc.update("delete from purchase_order where tenant_id = 0");
        jdbc.update("delete from sales_order_item where tenant_id = 0");
        jdbc.update("delete from biz_attachment where tenant_id = 0 and owner_type = 'SOURCING_QUOTE'");
        jdbc.update("delete from media_item where tenant_id = 0 and category = 4");
    }

    private long inquiry(String model) throws Exception {
        long c = customer("Wingrid Rocha", "Brazil");
        long id = ok(call(json(post(INQ), "{\"customerId\":" + c + ",\"source\":1,\"rawContent\":\"rfq\"}"), admin)).path("id").asLong();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("brand", "Siemens");
        row.put("category", "PLC");
        row.put("confirmedModel", model);
        row.put("quantity", 2);
        ok(call(json(post(INQ + "/" + id + "/confirm"), write(Map.of("rows", List.of(row)))), admin));
        return id;
    }

    private long task(long inquiryId) throws Exception {
        long task = jdbc.queryForObject("select id from sourcing_task where customer_inquiry_id = ?", Long.class, inquiryId);
        ok(call(json(post(BOARD + "/assign"), "{\"taskIds\":[" + task + "],\"assigneeId\":" + BUYER_LIN + "}"), admin));
        return task;
    }

    private long item(long task) {
        return jdbc.queryForObject("select id from inquiry_item where sourcing_task_id = ?", Long.class, task);
    }

    private String quote(long itemId, String price, Boolean freeShipping, List<Long> files) throws Exception {
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("channel", 2);
        q.put("shopName", "明和电气");
        q.put("unitPrice", new BigDecimal(price));
        q.put("itemCondition", 1);
        q.put("leadTime", 1);
        q.put("freeShipping", freeShipping);
        q.put("attachmentIds", files);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("itemId", itemId);
        item.put("quotes", List.of(q));
        return write(Map.of("submit", true, "items", List.of(item)));
    }

    private long upload(String token) throws Exception {
        return ok(call(multipart("/api/v1/attachments").file(new MockMultipartFile("file", "店铺截图.png", "image/png", PNG))
                .param("ownerType", "SOURCING_QUOTE"), token)).path("id").asLong();
    }

    @Test
    void freeShippingAndAttachments_keptOnResubmit_visibleInPriceRecords() throws Exception {
        loginAsAdmin("it_sqe_admin");
        admin = token("it_sqe_admin");
        long inq = inquiry(MODEL);
        long task = task(inq);
        long item = item(task);
        String lin = token("it_lin");

        long file = upload(lin);
        ok(call(json(put(MY + "/" + task + "/quotes"), quote(item, "1050", false, List.of(file))), lin));
        JsonNode q = ok(call(get(MY + "/" + task), lin)).path("items").get(0).path("quotes").get(0);
        assertFalse(q.path("freeShipping").asBoolean());
        assertEquals(file, q.path("attachments").get(0).path("id").asLong());

        // 改价重新提交：附件跟到新版本
        ok(call(json(put(MY + "/" + task + "/quotes"), quote(item, "1020", false, List.of(file))), lin));
        JsonNode q2 = ok(call(get(MY + "/" + task), lin)).path("items").get(0).path("quotes").get(0);
        assertEquals(0, new BigDecimal("1020").compareTo(q2.path("unitPrice").decimalValue()));
        assertEquals(file, q2.path("attachments").get(0).path("id").asLong());
        assertEquals(q2.path("id").asLong(), jdbc.queryForObject("select owner_id from biz_attachment where id = ?", Long.class, file));
        // 回价图片进素材库「供方实拍」，默认仅内部；换到新版本后仍只有一条
        assertEquals(List.of("4:0"), jdbc.queryForList("select concat(category, ':', can_send) from media_item "
                + "where source_table = 'biz_attachment' and source_id = ? and deleted_at is null", String.class, file));

        JsonNode bad = call(json(put(MY + "/" + task + "/quotes"), quote(item, "1020", true, List.of(987654321L))), lin);
        assertEquals("附件不存在，请重新上传", bad.path("message").asText());

        // 业务员看到的回价带不包邮与附件；默认（不传）为包邮
        JsonNode selected = ok(call(get(INQ + "/" + inq), admin)).path("items").get(0).path("selectedQuote");
        assertFalse(selected.path("freeShipping").asBoolean());
        assertEquals(1, selected.path("attachments").size());
        ok(call(json(put(MY + "/" + task + "/quotes"), quote(item, "1020", null, List.of())), lin));
        assertTrue(ok(call(get(MY + "/" + task), lin)).path("items").get(0).path("quotes").get(0).path("freeShipping").asBoolean());
    }

    @Test
    void searchMyTasks_andItemHistoryPrefersDeal() throws Exception {
        loginAsAdmin("it_sqe_admin");
        admin = token("it_sqe_admin");
        long first = inquiry(MODEL);
        long t1 = task(first);
        long item1 = item(t1);
        String lin = token("it_lin");
        ok(call(json(put(MY + "/" + t1 + "/quotes"), quote(item1, "1120", true, List.of())), lin));

        long second = inquiry("6ES7 214-1AG40-0XB0");
        long t2 = task(second);
        long item2 = item(t2);

        // 搜索：按型号（忽略分隔符）、询盘单编号；待处理与已回价都能搜
        assertEquals(1, ok(call(get(MY).param("keyword", "6es72141ag40"), lin)).size());
        assertEquals(1, ok(call(get(MY).param("done", "true").param("keyword", "6ES7214"), lin)).size());
        String code = jdbc.queryForObject("select inquiry_code from customer_inquiry where id = ?", String.class, second);
        assertEquals(t2, ok(call(get(MY).param("keyword", code), lin)).get(0).path("id").asLong());
        assertEquals(0, ok(call(get(MY).param("keyword", "nothing-like-this"), lin)).size());

        // 没有成交：显示最近一次询价（不含本任务）
        JsonNode row = ok(call(get(MY + "/" + t2), lin)).path("items").get(0);
        assertEquals("QUOTE", row.path("historyKind").asText());
        assertEquals(0, new BigDecimal("1120").compareTo(row.path("historyPrice").decimalValue()));
        JsonNode own = ok(call(get(MY + "/" + t1), lin)).path("items").get(0);
        assertTrue(own.path("historyKind").isMissingNode() || own.path("historyKind").isNull(), "本任务的回价不算历史");

        // 第一张询盘转成订单并下了采购订单：显示最近成交
        jdbc.update("insert into sales_order_item (tenant_id, so_id, line_no, inquiry_item_id, model, brand, quantity, stock_type) "
                + "values (0, 1, 1, ?, ?, 'Siemens', 2, 2)", item1, MODEL);
        long soItem = jdbc.queryForObject("select max(id) from sales_order_item where tenant_id = 0", Long.class);
        jdbc.update("insert into purchase_order (tenant_id, po_no, shop_name, status, order_date, currency_code, exchange_rate) "
                + "values (0, 'ITPO1', '明和电气', 2, ?, 'CNY', 1)", LocalDate.now().minusDays(3));
        long po = jdbc.queryForObject("select max(id) from purchase_order where tenant_id = 0", Long.class);
        jdbc.update("insert into purchase_order_item (tenant_id, po_id, so_item_id, model, brand, quantity, unit_price, net_price_cny) "
                + "values (0, ?, ?, ?, 'Siemens', 2, 1050, 1050)", po, soItem, MODEL);
        row = ok(call(get(MY + "/" + t2), lin)).path("items").get(0);
        assertEquals("DEAL", row.path("historyKind").asText());
        assertEquals(0, new BigDecimal("1050").compareTo(row.path("historyPrice").decimalValue()));
        JsonNode history = ok(call(get(MY + "/" + t2 + "/items/" + item2 + "/history"), lin));
        assertEquals("ITPO1", history.path("deals").get(0).path("poNo").asText());
        assertEquals("明和电气", history.path("deals").get(0).path("supplierName").asText());
        assertEquals(1, history.path("quotes").size());

        // 采购订单详情行带现货 / 期货
        JsonNode poDetail = ok(call(get("/api/v1/purchase/orders/" + po), admin));
        assertEquals(2, poDetail.path("items").get(0).path("stockType").asInt());
    }
}

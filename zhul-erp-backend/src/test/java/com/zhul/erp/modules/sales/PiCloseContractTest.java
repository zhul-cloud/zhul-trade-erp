package com.zhul.erp.modules.sales;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** spec sales/proforma-invoice「PI 内容」有效期、「PI 列表」过期标记、「关闭 PI」，以及 system/workbench「过期未收款 PI 卡片」 */
class PiCloseContractTest extends SalesContractSupport {

    private static final String QT = "/api/v1/quotations";
    private static final L[] TWO = {l("6ES7214-1AG40-0XB0", 1, "4500", "740.44"), l("6ES7231-4HD32-0XB0", 2, "1230", "180.00")};

    private String reason() {
        return jdbc.queryForObject("select item_code from dict_item where dict_type = 'quotation_lost_reason' and item_code <> 'OTHER' "
                + "order by sort_order limit 1", String.class);
    }

    private JsonNode close(long id, String body) throws Exception {
        return call(json(post(PI + "/" + id + "/close"), body), admin);
    }

    private void expire(long piId, int days) {
        LocalDate d = LocalDate.now().minusDays(days);
        jdbc.update("update proforma_invoice set valid_until = ? where id = ?", d, piId);
        jdbc.update("update proforma_invoice_version set valid_until = ? where pi_id = ?", d, piId);
    }

    private int quotationStatus(long id) {
        return jdbc.queryForObject("select status from quotation where id = ?", Integer.class, id);
    }

    private int inquiryStatus(long id) {
        return jdbc.queryForObject("select status from customer_inquiry where id = ?", Integer.class, id);
    }

    @Test
    void defaultValidity_expiredMarker_slipNotExpired_overdueCardMatchesList() throws Exception {
        long c = customer("Hoorain HTF", "Bangladesh");
        JsonNode draft = ok(createPi(quotationItemIds(quotation(c, 2, "USD", null, TWO))));
        assertEquals(LocalDate.now().plusDays(60).toString(), draft.path("version").path("validUntil").asText(), "默认开 PI 当天 + 60 天");
        Map<String, Object> body = saveBody(draft);
        body.put("validUntil", LocalDate.now().minusDays(1).toString());
        assertEquals("有效期不能早于 PI 日期", fail(savePi(draft.path("id").asLong(), body)).path("message").asText());
        body.put("validUntil", LocalDate.now().plusDays(10).toString());
        assertEquals(LocalDate.now().plusDays(10).toString(), ok(savePi(draft.path("id").asLong(), body)).path("version").path("validUntil").asText());

        long id = sentPi(quotationItemIds(quotation(c, 2, "USD", null, TWO)), null);
        expire(id, 3);
        JsonNode filtered = ok(call(json(post(PI + "/page"), "{\"expiredUnpaid\":true}"), admin));
        assertEquals(1, filtered.path("total").asInt());
        JsonNode row = filtered.path("records").get(0);
        assertTrue(row.path("expired").asBoolean());
        assertEquals(3, row.path("expiredDays").asInt());
        JsonNode card = ok(call(get(PI + "/overdue"), admin));
        assertEquals(1, card.path("count").asInt(), "卡片与列表一致");
        assertEquals(id, card.path("top").get(0).path("id").asLong());
        assertEquals("USD", card.path("totals").get(0).path("currencyCode").asText());

        // 有水单待确认：不算过期
        ok(uploadSlip(id, "100", LocalDate.now().toString(), admin));
        assertEquals(0, ok(call(json(post(PI + "/page"), "{\"expiredUnpaid\":true}"), admin)).path("total").asInt());
        assertFalse(ok(call(get(PI + "/" + id), admin)).path("expired").asBoolean());
        assertEquals(0, ok(call(get(PI + "/overdue"), admin)).path("count").asInt());
        assertEquals("PI 已有水单或到账记录，不能关闭", fail(close(id, "{\"reason\":\"" + reason() + "\"}")).path("message").asText());
        // 卡片只统计自己数据范围内的 PI
        long others = sentPi(quotationItemIds(quotation(c, 2, "USD", null, TWO)), null);
        expire(others, 5);
        assertEquals(1, ok(call(get(PI + "/overdue"), admin)).path("count").asInt());
        jdbc.update("update proforma_invoice set owner_id = ? where id = ?", BUYER_LIN, others);
        loginWithResources("it_pi_overdue", MENU_PI);
        assertEquals(0, ok(call(get(PI + "/overdue"), token("it_pi_overdue"))).path("count").asInt(), "只看自己数据范围内的 PI");
    }

    @Test
    void closeMarksQuotationLost_blocksReceipts_reopenRestores() throws Exception {
        long c = customer("ACROBOT", "India");
        long q = quotation(c, 2, "USD", null, TWO);
        long inquiry = inquiryOf(q);
        jdbc.update("update customer_inquiry set status = 7 where id = ?", inquiry);
        long id = sentPi(quotationItemIds(q), null);

        assertEquals("请选择关闭原因", fail(close(id, "{}")).path("message").asText());
        assertEquals("选择「其他」时请填写说明", fail(close(id, "{\"reason\":\"OTHER\"}")).path("message").asText());
        JsonNode closed = ok(close(id, write(Map.of("reason", reason(), "note", "跟进 3 次未回复"))));
        assertEquals(5, closed.path("status").asInt());
        assertEquals("跟进 3 次未回复", closed.path("closeNote").asText());
        assertFalse(closed.path("closeReasonName").asText().isEmpty());
        assertEquals(4, quotationStatus(q), "来源报价单标为未成交");
        assertEquals(id, jdbc.queryForObject("select lost_by_pi_id from quotation where id = ?", Long.class, q));
        assertEquals(9, inquiryStatus(inquiry), "客户询盘随之未成交");

        String msg = "PI 已关闭，需要继续请先重新打开";
        assertEquals(msg, fail(uploadSlip(id, "100", LocalDate.now().toString(), admin)).path("message").asText());
        assertEquals(msg, fail(call(post(PI + "/" + id + "/revise"), admin)).path("message").asText());
        assertEquals(5, ok(call(json(post(PI + "/page"), "{\"status\":5}"), admin)).path("records").get(0).path("status").asInt());

        JsonNode reopened = ok(call(json(post(PI + "/" + id + "/reopen"),
                write(Map.of("validUntil", LocalDate.now().plusDays(30).toString()))), admin));
        assertEquals(2, reopened.path("status").asInt());
        assertEquals(LocalDate.now().plusDays(30).toString(), reopened.path("validUntil").asText());
        assertEquals(2, quotationStatus(q), "报价单回到已发送");
        assertTrue(jdbc.queryForObject("select lost_by_pi_id is null from quotation where id = ?", Boolean.class, q));
        assertEquals(7, inquiryStatus(inquiry), "客户询盘回到已报价");
        ok(uploadSlip(id, "100", LocalDate.now().toString(), admin));
    }

    @Test
    void quotationWithOtherLivePiStays_uncheckedKeepsQuotationSent() throws Exception {
        long c = customer("Pacific Controls", "Australia");
        long q = quotation(c, 2, "USD", null, TWO);
        List<Long> lines = quotationItemIds(q);
        long first = sentPi(List.of(lines.get(0)), null);
        long second = sentPi(List.of(lines.get(1)), null);
        String secondNo = jdbc.queryForObject("select pi_no from proforma_invoice where id = ?", String.class, second);

        JsonNode closed = ok(close(first, "{\"reason\":\"" + reason() + "\"}"));
        assertEquals(2, quotationStatus(q));
        assertTrue(closed.path("notices").get(0).asText().contains("还有有效的 PI " + secondNo), closed.path("notices").toString());

        // 不勾选：报价单保持已发送；两张 PI 都关闭后报价单可以出新版本
        ok(close(second, "{\"reason\":\"" + reason() + "\",\"markQuotationLost\":false}"));
        assertEquals(2, quotationStatus(q));
        assertEquals(2, ok(call(post(QT + "/" + q + "/revise"), admin)).path("versionNo").asInt(), "关闭的 PI 不挡出新版本");
    }
}

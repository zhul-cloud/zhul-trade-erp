package com.zhul.erp.modules.inquiry;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.modules.inquiry.customerinquiry.service.CustomerInquiryService;
import com.zhul.erp.modules.inquiry.support.PriceKeys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** spec inquiry/inquiry-intake「追加型号」 */
class InquiryAppendContractTest extends InquiryContractSupport {

    private static final long ADMIN_ID = 99000002L;

    @Autowired private CustomerInquiryService inquiryService;
    @Autowired private PriceKeys priceKeys;

    @AfterEach
    void cleanupAppends() {
        jdbc.update("delete from customer_inquiry_append where tenant_id = 0");
        jdbc.update("delete from sourcing_quote where tenant_id = 0");
    }

    private static Map<String, Object> row(String brand, String model, int qty, Long reuse) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("brand", brand);
        m.put("category", "传感器");
        m.put("confirmedModel", model);
        m.put("quantity", qty);
        m.put("reuseQuoteId", reuse);
        return m;
    }

    private long quote(String brand, String model, String price) {
        jdbc.update("insert into sourcing_quote (tenant_id, brand, brand_key, model, model_key, channel, shop_name, currency_code, unit_price, "
                        + "exchange_rate, unit_price_cny, item_condition, quoted_by, quoted_at, status) "
                        + "values (0, ?, ?, ?, ?, 2, '明和电气', 'CNY', ?, 1, ?, 1, ?, NOW(), 2)",
                brand, priceKeys.brand(brand).brandKey(), model, PriceKeys.model(model), price, price, BUYER_LIN);
        return jdbc.queryForObject("select max(id) from sourcing_quote", Long.class);
    }

    private long confirmed(String admin, List<Map<String, Object>> rows) throws Exception {
        long c = customer("Wingrid Rocha", "Brazil");
        long id = ok(call(json(post(INQ), "{\"customerId\":" + c + ",\"source\":1,\"rawContent\":\"rfq\"}"), admin)).path("id").asLong();
        ok(call(json(post(INQ + "/" + id + "/confirm"), write(Map.of("rows", rows))), admin));
        return id;
    }

    private int status(long id) {
        return jdbc.queryForObject("select status from customer_inquiry where id = ?", Integer.class, id);
    }

    @Test
    void aiAppend_parsesOnlyNewContent_continuesLinesAndTasks_backToSourcing() throws Exception {
        loginAsAdmin("it_app_admin");
        String admin = token("it_app_admin");
        long id = confirmed(admin, List.of(row("Siemens", "6ES7214-1AG40-0XB0", 1, null), row("Siemens", "6ES7221-1BF32-0XB0", 1, null),
                row("Siemens", "6ES7222-1BF32-0XB0", 2, null)));
        String code = jdbc.queryForObject("select inquiry_code from customer_inquiry where id = ?", String.class, id);
        assertEquals(5, status(id));

        // 待解析的询盘单不能追加
        long fresh = ok(call(json(post(INQ), "{\"customerId\":" + customer("Delta", "Chile") + ",\"source\":1,\"rawContent\":\"x\"}"), admin))
                .path("id").asLong();
        assertEquals("只有询价中、可报价、已报价的询盘单可以追加型号",
                call(json(post(INQ + "/" + fresh + "/appends"), "{\"rawContent\":\"x\"}"), admin).path("message").asText());
        assertEquals("请粘贴追加的内容或上传附件", call(json(post(INQ + "/" + id + "/appends"), "{}"), admin).path("message").asText());

        JsonNode append = ok(call(json(post(INQ + "/" + id + "/appends"), "{\"rawContent\":\"Omron E3Z-D61 x10, Omron E2E-X5ME1 x5\"}"), admin));
        long appendId = append.path("id").asLong();
        assertEquals(1, append.path("status").asInt(), "解析中");
        assertEquals(5, status(id), "追加解析期间询盘单状态不变");
        assertEquals(appendId, ok(call(get(INQ + "/" + id), admin)).path("openAppend").path("id").asLong());
        assertEquals("这张询盘单还有一次追加没有完成，请先确认或放弃",
                call(json(post(INQ + "/" + id + "/appends"), "{\"rawContent\":\"y\"}"), admin).path("message").asText());

        // 模拟解析成功回调（换成测试自己的 AI 任务，避开真实提交失败的异步回调）
        String output = "{\"groups\":[{\"brand\":\"Omron\",\"category\":\"传感器\",\"items\":["
                + "{\"originalModel\":\"E3Z-D61\",\"confidence\":1,\"quantity\":10},"
                + "{\"originalModel\":\"E2E-X5ME1\",\"confidence\":1,\"quantity\":5}]}]}";
        jdbc.update("insert into ai_task (tenant_id, skill_id, status, output, requested_by) values (0, 'inquiry-parse-and-split', 3, ?, ?)",
                output, ADMIN_ID);
        long task = jdbc.queryForObject("select max(id) from ai_task", Long.class);
        jdbc.update("update customer_inquiry_append set status = 1, ai_task_id = ? where id = ?", task, appendId);
        inquiryService.applyParseSuccess(task, output);
        assertEquals(5, status(id), "回调只改追加批次");

        JsonNode draft = ok(call(get(INQ + "/" + id + "/appends/" + appendId + "/draft"), admin));
        assertEquals(2, draft.path("rows").size(), "只解析追加的内容");
        assertEquals(appendId, draft.path("appendId").asLong());

        long history = quote("Omron", "E2E-X5ME1", "88");
        ok(call(json(post(INQ + "/" + id + "/appends/" + appendId + "/confirm"),
                write(Map.of("rows", List.of(row("Omron", "E3Z-D61", 10, null), row("Omron", "E2E-X5ME1", 5, history))))), admin));
        List<Integer> lines = jdbc.queryForList("select line_no from inquiry_item where customer_inquiry_id = ? order by line_no", Integer.class, id);
        assertEquals(List.of(1, 2, 3, 4, 5), lines);
        List<String> tasks = jdbc.queryForList("select task_code from sourcing_task where customer_inquiry_id = ? order by id", String.class, id);
        assertEquals(List.of(code + "A", code + "B"), tasks, "新任务编号接着原有字母");
        Map<String, Object> inq = jdbc.queryForMap("select status, total_item_count, total_quantity, priced_item_count, total_order_count "
                + "from customer_inquiry where id = ?", id);
        assertEquals(5, ((Number) inq.get("status")).intValue());
        assertEquals(5, ((Number) inq.get("total_item_count")).intValue());
        assertEquals(19, ((Number) inq.get("total_quantity")).intValue());
        assertEquals(1, ((Number) inq.get("priced_item_count")).intValue());
        assertEquals(2, ((Number) inq.get("total_order_count")).intValue());
        assertEquals(4, jdbc.queryForObject("select status from customer_inquiry_append where id = ?", Integer.class, appendId));
        assertTrue(ok(call(get(INQ + "/" + id), admin)).path("openAppend").isMissingNode()
                || ok(call(get(INQ + "/" + id), admin)).path("openAppend").isNull());
    }

    @Test
    void manualAppend_allReusedKeepsReadyToQuote_discardAndFailure() throws Exception {
        loginAsAdmin("it_app_admin");
        String admin = token("it_app_admin");
        long q1 = quote("Omron", "E3Z-D61", "52");
        long id = confirmed(admin, List.of(row("Omron", "E3Z-D61", 10, q1)));
        assertEquals(6, status(id), "全部复用历史价，可报价");

        JsonNode manual = ok(call(json(post(INQ + "/" + id + "/appends"), "{\"manual\":true}"), admin));
        assertEquals(2, manual.path("status").asInt(), "手动录入直接待确认");
        assertEquals(0, ok(call(get(INQ + "/" + id + "/appends/" + manual.path("id").asLong() + "/draft"), admin)).path("rows").size());
        long q2 = quote("Omron", "E2E-X5ME1", "88");
        ok(call(json(post(INQ + "/" + id + "/appends/" + manual.path("id").asLong() + "/confirm"),
                write(Map.of("rows", List.of(row("Omron", "E2E-X5ME1", 5, q2))))), admin));
        assertEquals(6, status(id), "全部复用时仍为可报价");
        assertEquals(0, jdbc.queryForObject("select count(*) from sourcing_task where customer_inquiry_id = ?", Integer.class, id));
        assertEquals(2, jdbc.queryForObject("select total_item_count from customer_inquiry where id = ?", Integer.class, id));

        // 解析失败后放弃
        long failed = ok(call(json(post(INQ + "/" + id + "/appends"), "{\"rawContent\":\"blurry\"}"), admin)).path("id").asLong();
        jdbc.update("insert into ai_task (tenant_id, skill_id, status, requested_by) values (0, 'inquiry-parse-and-split', 4, ?)", ADMIN_ID);
        long task = jdbc.queryForObject("select max(id) from ai_task", Long.class);
        jdbc.update("update customer_inquiry_append set status = 1, ai_task_id = ? where id = ?", task, failed);
        inquiryService.applyParseFailure(task, "图片看不清");
        JsonNode open = ok(call(get(INQ + "/" + id), admin)).path("openAppend");
        assertEquals(3, open.path("status").asInt());
        assertEquals("图片看不清", open.path("parseError").asText());
        ok(call(post(INQ + "/" + id + "/appends/" + failed + "/discard"), admin));
        assertEquals(5, jdbc.queryForObject("select status from customer_inquiry_append where id = ?", Integer.class, failed));
        assertEquals("这次追加已经结束", call(post(INQ + "/" + id + "/appends/" + failed + "/discard"), admin).path("message").asText());
        assertEquals(6, status(id));
    }
}

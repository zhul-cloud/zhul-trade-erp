package com.zhul.erp.modules.inquiry.sourcing;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.modules.inquiry.InquiryContractSupport;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** spec inquiry/sourcing-quote「粘贴报价快速回填」与「我的询价任务」排序 */
class QuotePasteContractTest extends InquiryContractSupport {

    private String admin;

    private long inquiryWithTask(boolean urgent, String brand, String... models) throws Exception {
        long c = customer("Wingrid Rocha", "Brazil");
        long id = ok(call(json(post(INQ), "{\"customerId\":" + c + ",\"source\":1,\"urgent\":" + urgent + ",\"rawContent\":\"rfq\"}"), admin))
                .path("id").asLong();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String m : models) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("brand", brand);
            r.put("category", "伺服驱动器");
            r.put("confirmedModel", m);
            r.put("quantity", 2);
            r.put("unit", "台");
            rows.add(r);
        }
        ok(call(json(post(INQ + "/" + id + "/confirm"), write(Map.of("rows", rows))), admin));
        long task = jdbc.queryForObject("select id from sourcing_task where customer_inquiry_id = ?", Long.class, id);
        ok(call(json(post(BOARD + "/assign"), "{\"taskIds\":[" + task + "],\"assigneeId\":" + BUYER_LIN + "}"), admin));
        return task;
    }

    private JsonNode preview(long task, String text, String token) throws Exception {
        return call(json(post(MY + "/" + task + "/paste-preview"), write(Map.of("text", text))), token);
    }

    @Test
    void preview_matchesTaskModels_notSaved() throws Exception {
        loginAsAdmin("it_paste_admin");
        admin = token("it_paste_admin");
        long task = inquiryWithTask(false, "Schneider", "LXM32AD30N4", "LXM32AD18N4", "BMH1003P16A2A", "BMH0702P12A2A");
        String lin = token("it_lin");
        JsonNode r = ok(preview(task, """
                LXM32AD30N4，要 1 台    3140  现货
                LXM32AD18N4，要 2 台    2237 现货
                BMH1003P16A2A，要 1 台   2562  5周
                BMH0702P12A2A，要 2 台   3263  5周
                BMH0701 拆机 1800
                未税包邮  全新原装正品 假一罚万
                """, lin));
        JsonNode rows = r.path("rows");
        assertEquals(5, rows.size());
        assertEquals("LXM32AD30N4", rows.get(0).path("model").asText());
        assertEquals(3140, rows.get(0).path("unitPrice").asInt());
        assertEquals("MATCHED", rows.get(0).path("status").asText());
        assertFalse(rows.get(0).path("taxIncluded").asBoolean());
        assertFalse(rows.get(0).path("locked").asBoolean());
        assertEquals("未税包邮  全新原装正品 假一罚万", rows.get(0).path("note").asText());
        assertEquals("UNMATCHED", rows.get(4).path("status").asText());
        assertEquals(0, jdbc.queryForObject("select count(*) from sourcing_quote where task_id = ?", Integer.class, task), "预览不落库");

        assertEquals("请粘贴店家回复的报价", preview(task, "  ", lin).path("message").asText());
        assertEquals("询价任务不存在", preview(task, "LXM32AD30N4 100", token("it_jiang")).path("message").asText(), "他人任务");
    }

    @Test
    void myTasks_timeoutUrgentFirst_thenLatestUpdated() throws Exception {
        loginAsAdmin("it_paste_admin");
        admin = token("it_paste_admin");
        long a = inquiryWithTask(false, "Schneider", "A-1");
        long b = inquiryWithTask(false, "Omron", "B-1");
        long urgent = inquiryWithTask(true, "Delta", "C-1");
        jdbc.update("update sourcing_task set update_time = now() - interval 2 hour where id in (?, ?, ?)", a, b, urgent);
        jdbc.update("update sourcing_task set update_time = now() - interval 1 hour where id = ?", b);
        String lin = token("it_lin");
        assertEquals(List.of(urgent, b, a), ids(ok(call(get(MY), lin))), "紧急置顶，其余按更新时间倒序");

        long itemA = jdbc.queryForObject("select id from inquiry_item where sourcing_task_id = ?", Long.class, a);
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("channel", 1);
        q.put("shopName", "工控店");
        q.put("unitPrice", 100);
        q.put("itemCondition", 1);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("itemId", itemA);
        item.put("quotes", List.of(q));
        ok(call(json(put(MY + "/" + a + "/quotes"), write(Map.of("submit", false, "items", List.of(item)))), lin));
        JsonNode list = ok(call(get(MY), lin));
        assertEquals(List.of(urgent, a, b), ids(list), "保存回价草稿后排到普通任务最前");
        assertFalse(list.get(1).path("updateTime").isNull());
    }

    private static List<Long> ids(JsonNode list) {
        List<Long> out = new ArrayList<>();
        list.forEach(n -> out.add(n.path("id").asLong()));
        return out;
    }
}

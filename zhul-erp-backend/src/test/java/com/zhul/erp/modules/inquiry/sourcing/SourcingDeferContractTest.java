package com.zhul.erp.modules.inquiry.sourcing;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.modules.inquiry.InquiryContractSupport;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** spec inquiry/sourcing-task「延后分配」与 inquiry/part-time-quote-review「兼职的审核中页签」 */
class SourcingDeferContractTest extends InquiryContractSupport {

    private static final int DEFERRED_TAB = 8;

    private String admin;

    private void login() {
        loginAsAdmin("it_df_admin");
        admin = token("it_df_admin");
    }

    private static Map<String, Object> row(String brand, String category, String model) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("brand", brand);
        m.put("category", category);
        m.put("confirmedModel", model);
        m.put("quantity", 1);
        return m;
    }

    @SafeVarargs
    private long inquiry(Map<String, Object>... rows) throws Exception {
        long c = customer("Defer Co", "Kenya");
        long id = ok(call(json(post(INQ), "{\"customerId\":" + c + ",\"source\":1,\"rawContent\":\"rfq\"}"), admin)).path("id").asLong();
        ok(call(json(post(INQ + "/" + id + "/confirm"), write(Map.of("rows", List.of(rows)))), admin));
        return id;
    }

    private List<Long> tasksOf(long inquiryId) {
        return jdbc.queryForList("select id from sourcing_task where customer_inquiry_id = ? order by id", Long.class, inquiryId);
    }

    private JsonNode board(int status) throws Exception {
        return ok(call(get(BOARD).param("status", String.valueOf(status)), admin));
    }

    private static List<Long> ids(JsonNode tasks) {
        List<Long> out = new ArrayList<>();
        tasks.forEach(t -> out.add(t.path("id").asLong()));
        return out;
    }

    private JsonNode defer(String body) throws Exception {
        return call(json(post(BOARD + "/defer"), body), admin);
    }

    @Test
    void deferTask_leavesUnassigned_listedInDeferredTab_returnsOnDay() throws Exception {
        login();
        long inq = inquiry(row("Siemens", "PLC", "6ES7315-2AH14-0AB0"), row("Omron", "传感器", "E2E-X5ME1"));
        List<Long> ts = tasksOf(inq);
        assertEquals(2, ts.size());
        long t1 = ts.get(0);
        long t2 = ts.get(1);
        int before = board(1).path("stats").path("unassigned").asInt();
        String tomorrow = LocalDate.now().plusDays(1).toString();

        // 只能延后到今天之后、最多 180 天
        assertTrue(defer("{\"taskIds\":[" + t1 + "],\"deferUntil\":\"" + LocalDate.now() + "\"}")
                .path("message").asText().contains("晚于今天"));
        assertTrue(defer("{\"taskIds\":[" + t1 + "],\"deferUntil\":\"" + LocalDate.now().plusDays(181) + "\"}")
                .path("message").asText().contains("180"));

        ok(defer("{\"taskIds\":[" + t1 + "],\"deferUntil\":\"" + tomorrow + "\",\"reason\":\"等客户确认数量\"}"));
        JsonNode pool = board(1);
        assertFalse(ids(pool.path("tasks")).contains(t1), "延后的任务不在待分配");
        assertTrue(ids(pool.path("tasks")).contains(t2));
        assertEquals(before - 1, pool.path("stats").path("unassigned").asInt());
        assertTrue(pool.path("stats").path("deferred").asInt() >= 1);
        assertTrue(pool.path("tabs").path("deferred").asInt() >= 1);

        JsonNode deferred = board(DEFERRED_TAB);
        JsonNode group = null;
        for (JsonNode g : deferred.path("deferredGroups")) {
            if (g.path("customerInquiryId").asLong() == inq) {
                group = g;
            }
        }
        assertEquals(tomorrow, group.path("deferUntil").asText());
        assertEquals(1, group.path("taskCount").asInt());
        assertFalse(group.path("wholeInquiry").asBoolean(), "同询盘还有没延后的任务");
        assertEquals("等客户确认数量", group.path("reason").asText());

        // 询盘详情的任务带延后日期
        JsonNode detail = ok(call(get(INQ + "/" + inq), admin));
        boolean shown = false;
        for (JsonNode t : detail.path("tasks")) {
            if (t.path("id").asLong() == t1) {
                shown = tomorrow.equals(t.path("deferUntil").asText());
            }
        }
        assertTrue(shown, detail.path("tasks").toString());

        // 到了延后当天：回到待分配，等待时长从当天 0 点算
        jdbc.update("update sourcing_task set defer_until = ?, pool_entered_at = ? where id = ?",
                LocalDate.now(), LocalDate.now().atStartOfDay(), t1);
        assertTrue(ids(board(1).path("tasks")).contains(t1), "到期回到待分配");
    }

    @Test
    void deferWholeInquiry_preview_undefer_andAssignClears() throws Exception {
        login();
        long inq = inquiry(row("Siemens", "PLC", "6ES7315-2AH14-0AB0"), row("Omron", "传感器", "E2E-X5ME1"));
        List<Long> ts = tasksOf(inq);
        ok(call(json(post(BOARD + "/assign"), "{\"taskIds\":[" + ts.get(1) + "],\"assigneeId\":" + BUYER_LIN + "}"), admin));

        JsonNode preview = ok(call(get(BOARD + "/defer-preview").param("customerInquiryId", String.valueOf(inq)), admin));
        assertEquals(1, preview.path("taskCount").asInt());
        assertEquals(1, preview.path("otherCount").asInt(), "询价中的任务不受影响");

        String in3 = LocalDate.now().plusDays(3).toString();
        ok(defer("{\"customerInquiryId\":" + inq + ",\"deferUntil\":\"" + in3 + "\"}"));
        assertEquals(in3, jdbc.queryForObject("select defer_until from sourcing_task where id = ?", String.class, ts.get(0)));
        assertNull(jdbc.queryForObject("select defer_until from sourcing_task where id = ?", String.class, ts.get(1)));
        assertTrue(defer("{\"customerInquiryId\":" + inq + ",\"deferUntil\":\"" + in3 + "\"}")
                .path("message").asText().contains("没有待分配"), "都延后了就没有可延后的");

        // 询价中的任务不能延后
        assertTrue(defer("{\"taskIds\":[" + ts.get(1) + "],\"deferUntil\":\"" + in3 + "\"}")
                .path("message").asText().contains("已经分配过了"));

        // 现在处理：马上回到待分配
        ok(call(json(post(BOARD + "/undefer"), "{\"taskIds\":[" + ts.get(0) + "]}"), admin));
        assertNull(jdbc.queryForObject("select defer_until from sourcing_task where id = ?", String.class, ts.get(0)));
        assertTrue(ids(board(1).path("tasks")).contains(ts.get(0)));

        // 分配时清空延后
        ok(defer("{\"taskIds\":[" + ts.get(0) + "],\"deferUntil\":\"" + in3 + "\"}"));
        ok(call(json(post(BOARD + "/assign"), "{\"taskIds\":[" + ts.get(0) + "],\"assigneeId\":" + BUYER_LIN + "}"), admin));
        assertNull(jdbc.queryForObject("select defer_until from sourcing_task where id = ?", String.class, ts.get(0)));
        assertEquals("", jdbc.queryForObject("select defer_reason from sourcing_task where id = ?", String.class, ts.get(0)));
    }

    private void submit(String token, long task, long itemId) throws Exception {
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("channel", 2);
        q.put("shopName", "三菱配件专营");
        q.put("unitPrice", new BigDecimal("100"));
        q.put("itemCondition", 1);
        q.put("leadTime", 1);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("itemId", itemId);
        item.put("quotes", List.of(q));
        ok(call(json(put(MY + "/" + task + "/quotes"), write(Map.of("submit", true, "items", List.of(item)))), token));
    }

    private JsonNode mine(String token, long task) throws Exception {
        for (JsonNode t : ok(call(get(MY), token))) {
            if (t.path("id").asLong() == task) {
                return t;
            }
        }
        return null;
    }

    @Test
    void partTimer_reviewingOnlyWhenNothingLeftToFill_backToPendingOnReject() throws Exception {
        login();
        long inq = inquiry(row("Mitsubishi", "伺服电机", "HG-SN202BJ"), row("Mitsubishi", "伺服电机", "HG-KN43J-S100"));
        long task = tasksOf(inq).get(0);
        ok(call(json(post(BOARD + "/assign"), "{\"taskIds\":[" + task + "],\"assigneeId\":" + PART_TIMER + "}"), admin));
        List<Long> items = jdbc.queryForList("select id from inquiry_item where sourcing_task_id = ? order by line_no", Long.class, task);
        String wang = token("it_wang");

        submit(wang, task, items.get(0));
        JsonNode partial = mine(wang, task);
        assertFalse(partial.path("reviewing").asBoolean(), "还有没填的型号，留在待处理");
        assertEquals(1, partial.path("pendingReviewCount").asInt());

        submit(wang, task, items.get(1));
        assertTrue(mine(wang, task).path("reviewing").asBoolean(), "全部提交待审核，归审核中");

        ok(call(json(post(BOARD + "/reviews/" + task + "/reject"),
                write(Map.of("items", List.of(Map.of("itemId", items.get(1), "quotedBy", PART_TIMER)), "reason", "价格不对"))), admin));
        assertFalse(mine(wang, task).path("reviewing").asBoolean(), "被退回的回到待处理");

        // 不需要审核的账号没有这个分类
        long other = tasksOf(inquiry(row("Siemens", "PLC", "6ES7315-2AH14-0AB0"))).get(0);
        ok(call(json(post(BOARD + "/assign"), "{\"taskIds\":[" + other + "],\"assigneeId\":" + BUYER_LIN + "}"), admin));
        JsonNode lin = mine(token("it_lin"), other).path("reviewing");
        assertTrue(lin.isNull() || lin.isMissingNode());
    }
}

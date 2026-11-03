package com.zhul.erp.modules.sales;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.modules.aftersales.support.WorkHours;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** spec sales/after-sales「处理时限」「跟进售后单」「售后管理页」 */
class AfterSalesSlaContractTest extends SalesContractSupport {

    private static final String AS = "/api/v1/sales/after-sales";

    private long create(String level, LocalDateTime received, LocalDateTime nextUpdate) throws Exception {
        long c = customer("Siam Automation", "Thailand");
        Map<String, Object> b = new HashMap<>();
        b.put("customerId", c);
        b.put("items", List.of(Map.of("model", "MR-J4-70A", "quantity", 1)));
        b.put("levelCode", level);
        b.put("symptom", "伺服报警");
        b.put("receivedAt", received.toString());
        if (nextUpdate != null) {
            b.put("nextUpdateAt", nextUpdate.toString());
        }
        return ok(call(json(post(AS), write(b)), admin)).path("id").asLong();
    }

    private JsonNode detail(long id) throws Exception {
        return ok(call(get(AS + "/" + id), admin));
    }

    private JsonNode note(long id, boolean notified) throws Exception {
        return ok(call(json(post(AS + "/" + id + "/notes"), write(Map.of("content", "已和客户沟通", "customerNotified", notified))), admin));
    }

    private static LocalDateTime at(JsonNode n) {
        return LocalDateTime.parse(n.asText().replace(' ', 'T'));
    }

    private static LocalDateTime now() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    }

    @Test
    void firstResponse_notify_levelChange_waitingPause_listFilter() throws Exception {
        // S1 收到 3 小时前 → 首次响应已逾期
        // 收到时间与断言共用一个时刻：两次取 now() 可能跨秒（audit DOC-007）
        LocalDateTime s1Received = now().minusHours(3);
        long s1 = create("S1", s1Received, null);
        JsonNode a = detail(s1);
        assertEquals(1, a.path("dueType").asInt());
        assertEquals("首次响应", a.path("dueTypeName").asText());
        assertEquals("OVERDUE", a.path("dueState").asText());
        assertTrue(a.path("overdue").asBoolean());
        assertEquals(s1Received.plusHours(1), at(a.path("dueAt")).truncatedTo(ChronoUnit.SECONDS));

        // 内部备注不算告知客户
        a = note(s1, false);
        assertEquals(1, a.path("dueType").asInt());
        assertFalse(a.path("logs").get(0).path("customerNotified").asBoolean());

        // 勾选已告知客户：首次响应完成，2 小时后更新客户
        LocalDateTime before = now();
        a = note(s1, true);
        assertTrue(a.path("logs").get(0).path("customerNotified").asBoolean());
        assertTrue(a.hasNonNull("respondedAt"));
        assertEquals(2, a.path("dueType").asInt());
        assertEquals("NORMAL", a.path("dueState").asText());
        LocalDateTime due = at(a.path("dueAt"));
        assertTrue(!due.isBefore(before.plusHours(2)) && due.isBefore(before.plusHours(2).plusMinutes(1)), due.toString());

        // 降级为 S3：从上次告知客户起 2 个工作日
        LocalDateTime start = at(a.path("lastContactAt"));
        a = ok(call(json(post(AS + "/" + s1 + "/plan"), write(Map.of("levelCode", "S3", "levelNote", "风险已排除"))), admin));
        assertEquals(WorkHours.add(start, 18 * 60), at(a.path("dueAt")));
        assertEquals(at(a.path("dueAt")), at(a.path("levelDueAt")));

        // 手填更早
        LocalDateTime manual = now().plusMinutes(30);
        Map<String, Object> plan = new HashMap<>();
        plan.put("nextAction", "发参数说明");
        plan.put("nextUpdateAt", manual.toString());
        a = ok(call(json(post(AS + "/" + s1 + "/plan"), write(plan)), admin));
        assertEquals(3, a.path("dueType").asInt());
        assertEquals("SOON", a.path("dueState").asText());

        // 待客户确认：只看手填；去掉手填后不提醒
        a = ok(call(json(post(AS + "/" + s1 + "/status"), write(Map.of("status", 2))), admin));
        assertEquals(3, a.path("dueType").asInt());
        plan.put("nextUpdateAt", null);
        a = ok(call(json(post(AS + "/" + s1 + "/plan"), write(plan)), admin));
        assertFalse(a.hasNonNull("dueAt"), "待客户确认且没有手填时间不提醒");
        assertFalse(a.path("overdue").asBoolean());

        // 改回处理中：从此刻重新起算 S3 间隔
        LocalDateTime back = now();
        a = ok(call(json(post(AS + "/" + s1 + "/status"), write(Map.of("status", 1))), admin));
        assertEquals(2, a.path("dueType").asInt());
        assertFalse(at(a.path("dueAt")).isBefore(WorkHours.add(back, 18 * 60)));

        // 列表与统计：另一张 S2 首次响应逾期
        long s2 = create("S2", now().minusDays(7), null);
        JsonNode page = ok(call(json(post(AS + "/page"), write(Map.of("overdue", true))), admin));
        assertEquals(1, page.path("total").asInt());
        assertEquals(s2, page.path("records").get(0).path("id").asLong());
        assertEquals("OVERDUE", page.path("records").get(0).path("dueState").asText());
        assertEquals(1, ok(call(get(AS + "/counts"), admin)).path("overdue").asInt());

        // 结案后不提醒
        JsonNode closed = ok(call(json(post(AS + "/" + s2 + "/close"), write(Map.of("reasonCode", "QUALITY", "result", "已解决"))), admin));
        assertFalse(closed.hasNonNull("dueAt"));
        assertEquals(0, ok(call(get(AS + "/counts"), admin)).path("overdue").asInt());
    }

    @Test
    void dueSoonFilter_includesOverdueAndSoon() throws Exception {
        long soon = create("S5", now().minusDays(30), now().plusMinutes(20));
        jdbc.update("update after_sales set responded_at = create_time where id = ?", soon);
        ok(call(json(post(AS + "/" + soon + "/notes"), write(Map.of("content", "内部备注"))), admin));
        long late = create("S1", now().minusHours(5), null);
        create("S4", now(), null);
        JsonNode page = ok(call(json(post(AS + "/page"), write(Map.of("dueSoon", true))), admin));
        assertEquals(2, page.path("total").asInt());
        List<Long> ids = List.of(page.path("records").get(0).path("id").asLong(), page.path("records").get(1).path("id").asLong());
        assertTrue(ids.contains(soon) && ids.contains(late), ids.toString());
        assertEquals(1, ok(call(get(AS + "/counts"), admin)).path("dueSoon").asInt());
    }
}

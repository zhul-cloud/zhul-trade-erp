package com.zhul.erp.modules.inquiry.sourcing;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.modules.inquiry.InquiryContractSupport;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
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

/** 兼职回价审核（spec inquiry/part-time-quote-review 及 sourcing-quote、sourcing-task、price-ledger 的相关修改） */
class QuoteReviewContractTest extends InquiryContractSupport {

    private static final int REVIEW_TAB = 5;

    private String admin;

    private void login() {
        loginAsAdmin("it_rv_admin");
        admin = token("it_rv_admin");
    }

    private static Map<String, Object> row(String model) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("brand", "Mitsubishi");
        m.put("category", "伺服电机");
        m.put("confirmedModel", model);
        m.put("quantity", 1);
        return m;
    }

    /** 业务员录入并确认询盘，把唯一的任务分给 assignee，返回任务 ID */
    @SafeVarargs
    private long task(long assignee, Map<String, Object>... rows) throws Exception {
        long c = customer("Wingrid Rocha", "Brazil");
        long id = ok(call(json(post(INQ), "{\"customerId\":" + c + ",\"source\":1,\"urgent\":false,\"rawContent\":\"rfq\"}"), admin)).path("id").asLong();
        ok(call(json(post(INQ + "/" + id + "/confirm"), write(Map.of("rows", List.of(rows)))), admin));
        long task = jdbc.queryForObject("select id from sourcing_task where customer_inquiry_id = ?", Long.class, id);
        ok(call(json(post(BOARD + "/assign"), "{\"taskIds\":[" + task + "],\"assigneeId\":" + assignee + "}"), admin));
        return task;
    }

    private List<Long> items(long task) {
        return jdbc.queryForList("select id from inquiry_item where sourcing_task_id = ? order by line_no", Long.class, task);
    }

    private static Map<String, Object> entry(int channel, String shop, String price, int condition, boolean recommended) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("channel", channel);
        m.put("shopName", shop);
        m.put("unitPrice", new BigDecimal(price));
        m.put("itemCondition", condition);
        m.put("leadTime", 1);
        m.put("recommended", recommended);
        return m;
    }

    private void submit(String token, long task, long itemId, Map<String, Object>... entries) throws Exception {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("itemId", itemId);
        item.put("quotes", List.of(entries));
        ok(call(json(put(MY + "/" + task + "/quotes"), write(Map.of("submit", true, "items", List.of(item)))), token));
    }

    private long quoteId(long itemId, String price, int status) {
        return jdbc.queryForObject("select id from sourcing_quote where inquiry_item_id = ? and unit_price = ? and status = ? and deleted_at is null",
                Long.class, itemId, new BigDecimal(price), status);
    }

    private static Map<String, Object> approveItem(long itemId, Long recommended, Long... voids) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("itemId", itemId);
        m.put("quotedBy", PART_TIMER);
        m.put("recommendedQuoteId", recommended);
        List<Map<String, Object>> v = new ArrayList<>();
        for (Long id : voids) {
            v.add(Map.of("quoteId", id, "reason", "二手且无质保"));
        }
        m.put("voids", v);
        return m;
    }

    private JsonNode approve(long task, Map<String, Object>... items) throws Exception {
        return call(json(post(BOARD + "/reviews/" + task + "/approve"), write(Map.of("items", List.of(items)))), admin);
    }

    private JsonNode historyOf(String model) throws Exception {
        return ok(call(json(post(HISTORY + "/page"), "{\"model\":\"" + model + "\"}"), admin));
    }

    @Test
    void partTimerSubmit_hiddenUntilApproved_thenRecommendedBecomesCost() throws Exception {
        login();
        long task = task(PART_TIMER, row("HG-SN202BJ"), row("HG-KN43J-S100"));
        long a = items(task).get(0);
        long b = items(task).get(1);
        String wang = token("it_wang");
        submit(wang, task, a, entry(2, "三菱配件专营", "2640", 1, false), entry(3, "个人卖家", "1980", 4, true));

        // 兼职传了推荐也不算；审核前业务员侧看不到任何价格
        assertEquals(0, jdbc.queryForObject("select count(*) from sourcing_quote where inquiry_item_id = ? and recommended = 1", Integer.class, a));
        assertEquals(1, jdbc.queryForObject("select quote_status from inquiry_item where id = ?", Integer.class, a), "仍为待询价");
        assertNull(jdbc.queryForObject("select selected_quote_id from inquiry_item where id = ?", Long.class, a));
        assertEquals(0, historyOf("HG-SN202BJ").path("total").asInt(), "待审核的价格不进历史询价");
        assertEquals(0, ok(call(get(INQ + "/price-match").param("brand", "Mitsubishi").param("model", "HG-SN202BJ"), admin))
                .path("sameBrand").size(), "解析确认的历史价复用也看不到");

        JsonNode detail = ok(call(get(MY + "/" + task), wang));
        assertTrue(detail.path("reviewRequired").asBoolean(), "兼职采购的录入页不显示推荐列");
        assertEquals(1, detail.path("items").get(0).path("reviewStatus").asInt(), "兼职看到自己的待审核记录");
        assertEquals(2, detail.path("items").get(0).path("quotes").size());

        // 工作台：从询价中移到待审核
        JsonNode sourcing = ok(call(get(BOARD).param("status", "2"), admin));
        assertTrue(sourcing.path("tasks").findValues("id").stream().noneMatch(n -> n.asLong() == task), "待审核的任务不再出现在询价中");
        JsonNode review = ok(call(get(BOARD).param("status", String.valueOf(REVIEW_TAB)), admin));
        assertEquals(1, review.path("stats").path("pendingReview").asInt());
        JsonNode t = review.path("tasks").get(0);
        assertEquals(task, t.path("id").asLong());
        assertEquals(1, t.path("reviewItemCount").asInt());
        assertEquals("王某", t.path("reviewBuyerNames").get(0).asText());
        JsonNode rd = ok(call(get(BOARD + "/reviews/" + task), admin));
        assertEquals(1, rd.path("items").size());
        assertEquals(2, rd.path("items").get(0).path("quotes").size());
        assertEquals(1, rd.path("unsubmittedCount").asInt(), "HG-KN43J-S100 还没回价");
        assertFalse(rd.path("task").path("reviewSubmittedAt").isMissingNode(), "详情带最早一批提交时间");

        long good = quoteId(a, "2640", 3);
        long bad = quoteId(a, "1980", 3);
        assertTrue(approve(task, approveItem(a, null)).path("message").asText().contains("请为 HG-SN202BJ 选一条推荐报价"));
        assertTrue(approve(task, approveItem(a, null, good, bad)).path("message").asText().contains("都作废了"));
        Map<String, Object> tampered = approveItem(a, good, bad);
        tampered.put("unitPrice", "1");
        ok(approve(task, tampered));
        assertEquals(0, new BigDecimal("2640").compareTo(jdbc.queryForObject("select unit_price from sourcing_quote where id = ?", BigDecimal.class, good)),
                "审核不能改价格");
        assertEquals(4, jdbc.queryForObject("select status from sourcing_quote where id = ?", Integer.class, bad));
        assertEquals(good, jdbc.queryForObject("select selected_quote_id from inquiry_item where id = ?", Long.class, a), "推荐即采购成本价");
        JsonNode h = historyOf("HG-SN202BJ").path("records").get(0);
        assertEquals(1, h.path("recordCount").asInt(), "作废的不进历史询价");
        assertTrue(approve(task, approveItem(a, good)).path("message").asText().contains("已经审核过"), "重复审核提示刷新");

        JsonNode log = ok(call(get(BOARD + "/items/" + a + "/history"), admin));
        assertTrue(log.findValuesAsText("action").contains("审核通过兼职回价"), log.toString());
        assertTrue(log.findValuesAsText("note").stream().anyMatch(n -> n.contains("推荐 ¥2640") && n.contains("作废 1 条")), log.toString());

        // 兼职回齐但还没审核：任务不变为已回价
        submit(wang, task, b, entry(2, "工控直营", "1260", 1, false));
        assertEquals(2, jdbc.queryForObject("select status from sourcing_task where id = ?", Integer.class, task));
        ok(approve(task, approveItem(b, quoteId(b, "1260", 3))));
        assertEquals(3, jdbc.queryForObject("select status from sourcing_task where id = ?", Integer.class, task));
        assertEquals(0, ok(call(get(BOARD).param("status", String.valueOf(REVIEW_TAB)), admin)).path("tasks").size());
    }

    @Test
    void partialApprove_reject_andEditAfterApproval() throws Exception {
        login();
        long task = task(PART_TIMER, row("MR-J4-70A"), row("MR-J4-350A"), row("MR-PWCNS4"));
        List<Long> ids = items(task);
        String wang = token("it_wang");
        for (int i = 0; i < 3; i++) {
            submit(wang, task, ids.get(i), entry(2, "店" + i, String.valueOf(100 + i), 1, false));
        }
        ok(approve(task, approveItem(ids.get(0), quoteId(ids.get(0), "100", 3)), approveItem(ids.get(1), quoteId(ids.get(1), "101", 3))));
        assertEquals(2, jdbc.queryForObject("select quote_status from inquiry_item where id = ?", Integer.class, ids.get(0)), "通过的型号立即可见");
        JsonNode review = ok(call(get(BOARD).param("status", String.valueOf(REVIEW_TAB)), admin)).path("tasks").get(0);
        assertEquals(1, review.path("reviewItemCount").asInt(), "部分审核后任务仍在待审核");

        // 退回：原因必填；退回后回到兼职草稿并带原因
        JsonNode empty = call(json(post(BOARD + "/reviews/" + task + "/reject"),
                write(Map.of("items", List.of(Map.of("itemId", ids.get(2), "quotedBy", PART_TIMER)), "reason", " "))), admin);
        assertTrue(empty.path("message").asText().contains("请填写退回原因"), empty.toString());
        ok(call(json(post(BOARD + "/reviews/" + task + "/reject"),
                write(Map.of("items", List.of(Map.of("itemId", ids.get(2), "quotedBy", PART_TIMER)), "reason", "请再找全新原装"))), admin));
        assertEquals(1, jdbc.queryForObject("select status from sourcing_quote where inquiry_item_id = ? and deleted_at is null", Integer.class, ids.get(2)));
        JsonNode mine = ok(call(get(MY + "/" + task), wang)).path("items").get(2);
        assertEquals(3, mine.path("reviewStatus").asInt());
        assertEquals("请再找全新原装", mine.path("reviewNote").asText());
        assertEquals(0, ok(call(get(BOARD).param("status", String.valueOf(REVIEW_TAB)), admin)).path("tasks").size(), "退回后不在待审核");

        // 修改已通过的回价：新版本审核前业务员仍看到旧价，通过后才换
        long old = quoteId(ids.get(0), "100", 2);
        submit(wang, task, ids.get(0), entry(2, "店0", "90", 1, false));
        assertEquals(old, jdbc.queryForObject("select selected_quote_id from inquiry_item where id = ?", Long.class, ids.get(0)));
        assertEquals(1, ok(call(get(MY + "/" + task), wang)).path("items").get(0).path("quotes").size(), "兼职只看到正在审核的新版本");
        long fresh = quoteId(ids.get(0), "90", 3);
        ok(approve(task, approveItem(ids.get(0), fresh)));
        assertEquals(fresh, jdbc.queryForObject("select selected_quote_id from inquiry_item where id = ?", Long.class, ids.get(0)));
        assertTrue(jdbc.queryForObject("select deleted_at is not null from sourcing_quote where id = ?", Boolean.class, old), "旧版本此时才替换");
        JsonNode versions = ok(call(get(BOARD + "/items/" + ids.get(0) + "/history"), admin));
        assertEquals(2, versions.findValues("current").size(), "修改记录保留两个版本：" + versions);
    }

    @Test
    void fullTimeBuyerUnchanged_andReviewNeedsPermission() throws Exception {
        login();
        long task = task(BUYER_LIN, row("E3Z-D61"));
        long item = items(task).get(0);
        submit(token("it_lin"), task, item, entry(2, "欧姆龙专营", "85", 1, true));
        assertEquals(2, jdbc.queryForObject("select status from sourcing_quote where inquiry_item_id = ?", Integer.class, item), "正式采购提交直接生效");
        assertEquals(1, jdbc.queryForObject("select recommended from sourcing_quote where inquiry_item_id = ?", Integer.class, item));
        assertFalse(ok(call(get(MY + "/" + task), token("it_lin"))).path("reviewRequired").asBoolean());

        long ptTask = task(PART_TIMER, row("E3Z-D62"));
        long ptItem = items(ptTask).get(0);
        submit(token("it_wang"), ptTask, ptItem, entry(2, "欧姆龙专营", "88", 1, false));
        loginWithResources("it_rv_viewer", MENU_BOARD);
        String viewer = token("it_rv_viewer");
        assertEquals(200, perform(get(BOARD + "/reviews/" + ptTask), viewer).getStatus(), "没有审核权限也能查看");
        assertEquals(403, perform(json(post(BOARD + "/reviews/" + ptTask + "/approve"),
                write(Map.of("items", List.of(approveItem(ptItem, quoteId(ptItem, "88", 3)))))), viewer).getStatus());
        assertEquals(3, jdbc.queryForObject("select status from sourcing_quote where inquiry_item_id = ?", Integer.class, ptItem));
    }
}

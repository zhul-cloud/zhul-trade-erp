package com.zhul.erp.modules.inquiry.sourcing;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.modules.inquiry.InquiryContractSupport;
import com.zhul.erp.modules.inquiry.sourcing.service.impl.SalesWindowJob;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** 业务员优先询价（openspec/changes/sales-first-sourcing）：待业务处理、认领与转采购、超时流转、按型号退回、统计 */
class SalesFirstSourcingContractTest extends InquiryContractSupport {

    private static final String INQ = "/api/v1/inquiry/customer-inquiries";
    private static final String BOARD = "/api/v1/inquiry/sourcing-board";
    private static final String MY = "/api/v1/inquiry/my-tasks";
    private static final String STATS = "/api/v1/inquiry/sourcing-stats";
    private static final long SELF = 99000002L;
    private static final int TASK_UNASSIGNED = 1;
    private static final int TASK_SOURCING = 2;
    private static final int TASK_DONE = 3;
    private static final int TASK_SALES = 6;

    @Autowired private SalesWindowJob job;
    @Autowired private com.zhul.erp.modules.inquiry.sourcing.service.SourcingTaskService taskService;

    private String admin;

    @BeforeEach
    void setWindow() {
        window(120, 30);
        loginAsAdmin("it_src_admin");
        admin = token("it_src_admin");
    }

    @AfterEach
    void resetRules() {
        jdbc.update("update sys_config set config_value = 'false' where tenant_id = 0 and config_key = 'inquiry.sourcing.auto-assign'");
        jdbc.update("delete from sourcing_assign_rule where tenant_id = 0");
    }

    private void window(int normal, int urgent) {
        jdbc.update("update sys_config set config_value = ? where tenant_id = 0 and config_key = 'inquiry.sourcing.sales-window-minutes'", String.valueOf(normal));
        jdbc.update("update sys_config set config_value = ? where tenant_id = 0 and config_key = 'inquiry.sourcing.sales-window-urgent-minutes'", String.valueOf(urgent));
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
    private long inquiry(boolean urgent, Map<String, Object>... rows) throws Exception {
        long c = customer("Wingrid Rocha", "Brazil");
        long id = ok(call(json(post(INQ), "{\"customerId\":" + c + ",\"source\":1,\"urgent\":" + urgent
                + ",\"rawContent\":\"x\"}"), admin)).path("id").asLong();
        ok(call(json(post(INQ + "/" + id + "/confirm"), write(Map.of("rows", List.of(rows)))), admin));
        return id;
    }

    private long task(long inquiryId, String brand) {
        return jdbc.queryForObject("select id from sourcing_task where customer_inquiry_id = ? and brand = ?", Long.class, inquiryId, brand);
    }

    private int status(long taskId) {
        return jdbc.queryForObject("select status from sourcing_task where id = ?", Integer.class, taskId);
    }

    private List<Long> items(long taskId) {
        return jdbc.queryForList("select id from inquiry_item where sourcing_task_id = ? order by line_no", Long.class, taskId);
    }

    private int itemStatus(long itemId) {
        return jdbc.queryForObject("select quote_status from inquiry_item where id = ?", Integer.class, itemId);
    }

    private void claim(long taskId) throws Exception {
        ok(call(json(post(MY + "/claim"), "{\"taskIds\":[" + taskId + "]}"), admin));
    }

    private void transfer(long taskId) throws Exception {
        ok(call(json(post(INQ + "/sourcing-tasks/transfer"), "{\"taskIds\":[" + taskId + "]}"), admin));
    }

    private void assign(long taskId, long assignee) throws Exception {
        ok(call(json(post(BOARD + "/assign"), "{\"taskIds\":[" + taskId + "],\"assigneeId\":" + assignee + "}"), admin));
    }

    private JsonNode price(String token, long taskId, long itemId, String price) throws Exception {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("channel", 2);
        entry.put("shopName", "华南工控");
        entry.put("unitPrice", new BigDecimal(price));
        entry.put("itemCondition", 1);
        entry.put("leadTime", 1);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("itemId", itemId);
        item.put("noStock", false);
        item.put("quotes", List.of(entry));
        return call(json(put(MY + "/" + taskId + "/quotes"), write(Map.of("submit", true, "items", List.of(item)))), token);
    }

    private JsonNode noStock(String token, long taskId, long itemId) throws Exception {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("itemId", itemId);
        item.put("noStock", true);
        item.put("noStockNote", "淘宝没货");
        return call(json(put(MY + "/" + taskId + "/quotes"), write(Map.of("submit", true, "items", List.of(item)))), token);
    }

    private JsonNode giveBack(String token, long taskId, List<Long> itemIds) throws Exception {
        return call(json(post(MY + "/" + taskId + "/return"),
                write(Map.of("reason", 2, "note", "德国现货渠道可能有", "itemIds", itemIds))), token);
    }

    private List<String> boardCodes(int tab) throws Exception {
        return ok(call(get(BOARD).param("status", String.valueOf(tab)), admin)).path("tasks").findValuesAsText("taskCode");
    }

    // ------------------------------------------------------------ 待业务处理

    @Test
    void confirmedTaskWaitsForOwner_hiddenFromBoard() throws Exception {
        long t = task(inquiry(false, row("Lenze", "变频器", "A1")), "Lenze");
        assertEquals(TASK_SALES, status(t));
        assertTrue(boardCodes(TASK_UNASSIGNED).isEmpty(), "待业务处理的任务不进入待分配池");
        JsonNode pending = ok(call(get(MY + "/sales-pending"), admin)).path("tasks");
        assertEquals(1, pending.size());
        long minutes = pending.get(0).path("remainingMinutes").asLong();
        assertTrue(minutes > 110 && minutes <= 120, "普通任务默认 2 小时：" + minutes);
    }

    @Test
    void boardReturnsOnePage_brandSummaryCoversAllPages() throws Exception {
        window(0, 0);
        long l1 = task(inquiry(false, row("Lenze", "变频器", "P1")), "Lenze");
        long l2 = task(inquiry(false, row("Lenze", "变频器", "P2")), "Lenze");
        long abb = task(inquiry(true, row("ABB", "变频器", "P3")), "ABB");
        JsonNode first = ok(call(get(BOARD).param("page", "1").param("pageSize", "2"), admin));
        assertEquals(3, first.path("total").asInt());
        assertEquals(3, first.path("stats").path("unassigned").asInt(), "统计按全部任务");
        assertEquals(2, first.path("tasks").size(), "只返回本页");
        assertEquals(abb, first.path("tasks").get(0).path("id").asLong(), "紧急在前");
        JsonNode lenze = null;
        for (JsonNode b : first.path("brands")) {
            if ("Lenze".equals(b.path("brand").asText())) {
                lenze = b;
            }
        }
        assertEquals(2, lenze.path("total").asInt(), "品牌汇总覆盖全部页");
        assertEquals(1, first.path("brands").get(0).path("urgent").asInt());
        JsonNode second = ok(call(get(BOARD).param("page", "2").param("pageSize", "2"), admin));
        assertEquals(1, second.path("tasks").size());
        assertTrue(java.util.Set.of(l1, l2).contains(second.path("tasks").get(0).path("id").asLong()));
        JsonNode beyond = ok(call(get(BOARD).param("page", "9").param("pageSize", "2"), admin));
        assertEquals(0, beyond.path("tasks").size());
        assertEquals(3, beyond.path("total").asInt());
        assertEquals(1, ok(call(get(BOARD).param("pageSize", "0"), admin)).path("tasks").size(), "每页条数小于 1 时按 1 条");
    }

    @Test
    void salesPendingPaged_urgentFirst() throws Exception {
        long normal = task(inquiry(false, row("Lenze", "变频器", "Q1")), "Lenze");
        long urgent = task(inquiry(true, row("ABB", "变频器", "Q2")), "ABB");
        JsonNode page1 = ok(call(get(MY + "/sales-pending").param("page", "1").param("pageSize", "1"), admin));
        assertEquals(2, page1.path("total").asInt());
        assertEquals(2, page1.path("brands").size());
        assertEquals(urgent, page1.path("tasks").get(0).path("id").asLong(), "紧急在前");
        JsonNode page2 = ok(call(get(MY + "/sales-pending").param("page", "2").param("pageSize", "1"), admin));
        assertEquals(normal, page2.path("tasks").get(0).path("id").asLong());
        assertTrue(page2.path("tasks").get(0).path("inquiryCode").asText().startsWith("IQ"));
    }

    @Test
    void urgentWindowAndZeroWindow() throws Exception {
        long urgent = task(inquiry(true, row("Lenze", "变频器", "A2")), "Lenze");
        assertTrue(ok(call(get(MY + "/sales-pending"), admin)).path("tasks").get(0).path("remainingMinutes").asLong() <= 30);
        assertEquals(TASK_SALES, status(urgent));
        window(0, 0);
        assertEquals(TASK_UNASSIGNED, status(task(inquiry(false, row("ABB", "变频器", "A3")), "ABB")), "时限为 0 直接进入待分配");
    }

    @Test
    void markingUrgentRecomputesDeadline_cancelCancels() throws Exception {
        long inq = inquiry(false, row("Lenze", "变频器", "A4"));
        long t = task(inq, "Lenze");
        jdbc.update("update sourcing_task set create_time = date_sub(now(), interval 40 minute) where id = ?", t);
        taskService.syncUrgent(inq, 1);
        job.moveDueTasks();
        assertEquals(TASK_UNASSIGNED, status(t), "标记紧急后已超过 30 分钟，流转到待分配");

        long other = inquiry(false, row("ABB", "变频器", "A4b"));
        long o = task(other, "ABB");
        taskService.cancelByInquiry(other);
        assertEquals(4, status(o), "询盘取消时一并取消");
    }

    @Test
    void autoAssignWaitsUntilEnteringPool() throws Exception {
        jdbc.update("update sys_config set config_value = 'true' where tenant_id = 0 and config_key = 'inquiry.sourcing.auto-assign'");
        ok(call(json(post(BOARD + "/rules"), "{\"matchType\":1,\"matchValues\":[\"Delta\"],\"assigneeId\":" + BUYER_LIN + "}"), admin));
        long t = task(inquiry(false, row("Delta", "HMI", "A5")), "Delta");
        assertEquals(TASK_SALES, status(t), "开启自动分配时也不抢先分配");
        transfer(t);
        assertEquals(TASK_SOURCING, status(t), "转采购时才按规则分配");
        assertEquals(BUYER_LIN, jdbc.queryForObject("select assignee_id from sourcing_task_assignee where task_id = ? and active = 1", Long.class, t));
    }

    // ------------------------------------------------------------ 认领与转采购

    @Test
    void ownerClaims_selfSourcingHiddenAndNoReview() throws Exception {
        long inq = inquiry(false, row("Lenze", "变频器", "B1"), row("ABB", "变频器", "B2"));
        long mine = task(inq, "Lenze");
        long other = task(inq, "ABB");
        claim(mine);
        transfer(other);
        assertEquals(TASK_SOURCING, status(mine));
        assertEquals(TASK_UNASSIGNED, status(other));
        assertEquals(1, ok(call(get(MY), admin)).size(), "认领的任务进入我的询价");
        List<String> pool = boardCodes(TASK_UNASSIGNED);
        assertEquals(1, pool.size());
        assertFalse(boardCodes(TASK_SOURCING).contains(jdbc.queryForObject("select task_code from sourcing_task where id = ?", String.class, mine)));

        ok(price(admin, mine, items(mine).get(0), "860"));
        assertEquals(2, jdbc.queryForObject("select status from sourcing_quote where task_id = ?", Integer.class, mine), "自询不审核");
        assertEquals(TASK_DONE, status(mine));
        JsonNode done = ok(call(get(BOARD).param("status", "3"), admin)).path("tasks").get(0);
        assertFalse(done.path("selfSourcedBy").isMissingNode() || done.path("selfSourcedBy").isNull(), "已回价页签标明业务员自询：" + done);
    }

    @Test
    void onlyCurrentOwnerCanAct() throws Exception {
        long inq = inquiry(false, row("Lenze", "变频器", "C1"));
        long t = task(inq, "Lenze");
        String lin = token("it_lin");
        assertNotEquals(0, call(json(post(MY + "/claim"), "{\"taskIds\":[" + t + "]}"), lin).path("code").asInt());
        assertEquals(TASK_SALES, status(t));
        assertEquals(0, ok(call(get(MY + "/sales-pending"), lin)).path("tasks").size());

        jdbc.update("update customer_inquiry set owner_id = ? where id = ?", BUYER_LIN, inq);
        assertEquals(1, ok(call(get(MY + "/sales-pending"), lin)).path("tasks").size(), "负责人变更后由新负责人处理");
        assertNotEquals(0, call(json(post(MY + "/claim"), "{\"taskIds\":[" + t + "]}"), admin).path("code").asInt(), "原负责人不能再处理");
        ok(call(json(post(MY + "/claim"), "{\"taskIds\":[" + t + "]}"), lin));
    }

    @Test
    void movedByTimeout_claimFails() throws Exception {
        long t = task(inquiry(false, row("Lenze", "变频器", "D1")), "Lenze");
        jdbc.update("update sourcing_task set sales_deadline = date_sub(now(), interval 1 minute) where id = ?", t);
        job.moveDueTasks();
        assertEquals(TASK_UNASSIGNED, status(t));
        assertNotNull(jdbc.queryForObject("select pool_entered_at from sourcing_task where id = ?", java.time.LocalDateTime.class, t));
        JsonNode res = call(json(post(MY + "/claim"), "{\"taskIds\":[" + t + "]}"), admin);
        assertTrue(res.path("message").asText().contains("已转给采购"), res.toString());
    }

    @Test
    void notDueStaysPut() throws Exception {
        long t = task(inquiry(false, row("Lenze", "变频器", "D2")), "Lenze");
        job.moveDueTasks();
        assertEquals(TASK_SALES, status(t));
    }

    private static void assertNotNull(Object o) {
        assertFalse(o == null, "应有值");
    }

    @Test
    void salesWithoutAcceptIsNotCandidate_selfSourcingNotInFamiliarity() throws Exception {
        jdbc.update("delete from role_resource where role_code = ? and resource_id = ?", BUYER_ROLE, BTN_ACCEPT);
        JsonNode res = call(json(post(BOARD + "/assign"), "{\"taskIds\":[1],\"assigneeId\":" + BUYER_LIN + "}"), admin);
        assertTrue(res.path("message").asText().contains("不是采购人员"), res.toString());
        jdbc.update("insert into role_resource (role_code, resource_id, resource_code) values (?, ?, '')", BUYER_ROLE, BTN_ACCEPT);

        // 自询完成的任务不计入熟悉度：下一个同品牌任务不推荐给业务员，也不增加熟悉度
        long t = task(inquiry(false, row("Lenze", "变频器", "E1")), "Lenze");
        claim(t);
        ok(price(admin, t, items(t).get(0), "100"));
        assertEquals(0, jdbc.queryForObject("select count(*) from sourcing_task_assignee a join sourcing_task k on k.id = a.task_id"
                + " where k.status = 3 and a.assign_mode <> 6 and k.brand = 'Lenze'", Integer.class));
    }

    // ------------------------------------------------------------ 按型号退回

    @Test
    void noStockHandedOver_purchaserFindsPrice() throws Exception {
        long inq = inquiry(false, row("Lenze", "变频器", "F1"), row("Lenze", "变频器", "F2"));
        long t = task(inq, "Lenze");
        List<Long> it = items(t);
        claim(t);
        ok(price(admin, t, it.get(0), "860"));
        ok(noStock(admin, t, it.get(1)));
        assertEquals(TASK_DONE, status(t), "无货也算回价");
        assertEquals(6, jdbc.queryForObject("select status from customer_inquiry where id = ?", Integer.class, inq));

        JsonNode preview = ok(call(get(MY + "/" + t + "/return-preview"), admin));
        assertFalse(preview.path("pendingReview").asBoolean());
        assertEquals(2, preview.path("rows").size());

        ok(giveBack(admin, t, List.of(it.get(1))));
        assertEquals(TASK_UNASSIGNED, status(t));
        assertEquals(2, itemStatus(it.get(0)), "有价的型号不变");
        assertEquals(1, itemStatus(it.get(1)), "无货的型号回到待询价，重算也不会变回无货");
        assertEquals(5, jdbc.queryForObject("select status from customer_inquiry where id = ?", Integer.class, inq), "询盘回到询价中");
        assertNull(jdbc.queryForObject("select first_assigned_at from sourcing_task where id = ?", java.time.LocalDateTime.class, t), "超时重新计时");
        assertEquals(1, jdbc.queryForObject("select count(*) from sourcing_quote where inquiry_item_id = ? and no_stock = 1 and status = 2", Integer.class, it.get(1)), "无货记录保留");
        assertTrue(ok(call(get(MY), admin)).isEmpty(), "退回后不再看到任务");

        assign(t, BUYER_LIN);
        String lin = token("it_lin");
        JsonNode detail = ok(call(get(MY + "/" + t), lin));
        assertEquals("停产无货", detail.path("task").path("returnReasonLabel").asText());
        JsonNode prev = detail.path("items").get(1).path("previous");
        assertEquals(1, prev.size(), detail.toString());
        assertTrue(prev.get(0).path("noStock").asBoolean());
        assertEquals(0, new BigDecimal("860").compareTo(detail.path("items").get(0).path("previous").get(0).path("unitPriceCny").decimalValue()), "正式采购看得到价格");

        ok(price(lin, t, it.get(1), "2500"));
        assertEquals(2, itemStatus(it.get(1)));
        assertEquals(TASK_DONE, status(t));
    }

    @Test
    void comparingKeepsOldPrice_bothNoStockIsNoStock() throws Exception {
        long t = task(inquiry(false, row("Lenze", "变频器", "G1"), row("Lenze", "变频器", "G2")), "Lenze");
        List<Long> it = items(t);
        claim(t);
        ok(price(admin, t, it.get(0), "860"));
        ok(noStock(admin, t, it.get(1)));
        ok(giveBack(admin, t, it));
        assertEquals(1, itemStatus(it.get(0)), "勾选比价的型号也回到待询价");
        assertNull(jdbc.queryForObject("select selected_quote_id from inquiry_item where id = ?", Long.class, it.get(0)), "回到待询价期间没有成本价");

        assign(t, BUYER_LIN);
        String lin = token("it_lin");
        ok(noStock(lin, t, it.get(0)));
        ok(noStock(lin, t, it.get(1)));
        assertEquals(2, itemStatus(it.get(0)), "新一轮无货时仍用上一轮的价格");
        assertEquals(3, itemStatus(it.get(1)), "两轮都无货才是无货");
        assertEquals(TASK_DONE, status(t));
    }

    @Test
    void pendingItemsAlwaysHandedOver_unselectedNoStockKept() throws Exception {
        long t = task(inquiry(false, row("Lenze", "变频器", "H1"), row("Lenze", "变频器", "H2")), "Lenze");
        List<Long> it = items(t);
        claim(t);
        ok(noStock(admin, t, it.get(0)));
        ok(giveBack(admin, t, List.of()));
        assertEquals(3, itemStatus(it.get(0)), "没勾选的无货型号保持无货");
        assertEquals(1, itemStatus(it.get(1)));
        assertEquals(1, jdbc.queryForObject("select count(*) from sourcing_item_handoff where task_id = ?", Integer.class, t));
    }

    @Test
    void nothingToHand_pendingReviewAndQuotedBlocked() throws Exception {
        long t = task(inquiry(false, row("Lenze", "变频器", "I1")), "Lenze");
        List<Long> it = items(t);
        transfer(t);
        assign(t, PART_TIMER);
        String wang = token("it_wang");
        ok(price(wang, t, it.get(0), "100"));
        JsonNode blocked = giveBack(wang, t, List.of());
        assertTrue(blocked.path("message").asText().contains("等审核"), blocked.toString());
        assertTrue(ok(call(get(MY + "/" + t + "/return-preview"), wang)).path("pendingReview").asBoolean());

        jdbc.update("update sourcing_quote set status = 2 where task_id = ?", t);
        jdbc.update("update inquiry_item set quote_status = 2 where id = ?", it.get(0));
        jdbc.update("update sourcing_task set status = 3 where id = ?", t);
        JsonNode nothing = giveBack(wang, t, List.of());
        assertTrue(nothing.path("message").asText().contains("没有可以交出"), nothing.toString());
    }

    @Test
    void partTimerNeverSeesPreviousPrice() throws Exception {
        long t = task(inquiry(false, row("Lenze", "变频器", "J1"), row("Lenze", "变频器", "J2")), "Lenze");
        List<Long> it = items(t);
        claim(t);
        ok(price(admin, t, it.get(0), "860"));
        ok(noStock(admin, t, it.get(1)));
        ok(giveBack(admin, t, List.of(it.get(1))));
        assign(t, PART_TIMER);
        String raw = perform(get(MY + "/" + t), token("it_wang")).getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        JsonNode detail = objectMapper.readTree(raw).path("data");
        JsonNode first = detail.path("items").get(0).path("previous").get(0);
        assertEquals("华南工控", first.path("shopName").asText(), "能看到店铺");
        assertTrue(first.path("unitPriceCny").isNull() || first.path("unitPriceCny").isMissingNode(), "看不到价格：" + first);
        assertFalse(raw.contains("860"), "响应里不含上一轮价格");
    }

    @Test
    void multiAssignee_returnKeepsSourcing() throws Exception {
        long t = task(inquiry(false, row("Lenze", "变频器", "K1")), "Lenze");
        transfer(t);
        assign(t, BUYER_LIN);
        ok(call(json(post(BOARD + "/tasks/" + t + "/assignees"), "{\"assigneeId\":" + BUYER_JIANG + "}"), admin));
        ok(giveBack(token("it_lin"), t, List.of()));
        assertEquals(TASK_SOURCING, status(t), "还有其他处理人时保持询价中");
        assertTrue(ok(call(get(MY), token("it_lin"))).isEmpty());
        assertEquals(1, ok(call(get(MY), token("it_jiang"))).size());
    }

    // ------------------------------------------------------------ 统计

    @Test
    void stats_monthlyWorkloadAndPersonRows() throws Exception {
        long t = task(inquiry(false, row("Lenze", "变频器", "L1"), row("Lenze", "变频器", "L2"), row("Lenze", "变频器", "L3")), "Lenze");
        List<Long> it = items(t);
        claim(t);
        ok(price(admin, t, it.get(0), "100"));
        ok(giveBack(admin, t, List.of()));
        assign(t, BUYER_LIN);
        String lin = token("it_lin");
        ok(price(lin, t, it.get(1), "200"));
        ok(noStock(lin, t, it.get(2)));

        JsonNode stats = ok(call(get(STATS), admin));
        JsonNode total = stats.path("workloadTotal");
        assertEquals(3, total.path("selfItems").asInt(), stats.toString());
        assertEquals(2, total.path("purchaseItems").asInt(), "接手人只算交给他的型号");
        assertEquals(0, new BigDecimal("60.00").compareTo(total.path("selfRate").decimalValue()));
        assertEquals(1, stats.path("workload").size());

        JsonNode sales = null;
        JsonNode buyer = null;
        for (JsonNode r : stats.path("purchasers")) {
            if (r.path("purchaserId").asLong() == SELF) {
                sales = r;
            } else {
                buyer = r;
            }
        }
        assertEquals("SALES", sales.path("role").asText());
        assertEquals(3, sales.path("items").asInt());
        assertEquals(1, sales.path("priced").asInt());
        assertEquals(2, sales.path("transferred").asInt(), "转出不计入有价、无货、待回价");
        assertEquals(0, new BigDecimal("100.00").compareTo(sales.path("pricedRate").decimalValue()), "有价率扣除转出");
        assertEquals("PURCHASER", buyer.path("role").asText());
        assertEquals(2, buyer.path("items").asInt(), "不需要找的型号不算到接手人头上");
        assertEquals(0, buyer.path("transferred").asInt());
    }

    @Test
    void stats_comparingCountsOwnResult() throws Exception {
        long t = task(inquiry(false, row("Lenze", "变频器", "M1")), "Lenze");
        List<Long> it = items(t);
        claim(t);
        ok(price(admin, t, it.get(0), "860"));
        ok(giveBack(admin, t, it));
        assign(t, BUYER_LIN);
        ok(noStock(token("it_lin"), t, it.get(0)));
        JsonNode rows = ok(call(get(STATS), admin)).path("purchasers");
        for (JsonNode r : rows) {
            if (r.path("purchaserId").asLong() == BUYER_LIN) {
                assertEquals(1, r.path("noStock").asInt(), r.toString());
                assertEquals(0, r.path("priced").asInt());
            } else {
                assertEquals(1, r.path("transferred").asInt(), r.toString());
            }
        }
    }

    @Test
    void migrationAndTemplateGrantAcceptPermission() throws Exception {
        assertEquals(1, jdbc.queryForObject("select count(*) from role_resource where role_code = 'ROLE_PTBUYER' and resource_id = 110219", Integer.class), "兼职采购有可接询价任务");
        JsonNode roles = objectMapper.readTree(new org.springframework.core.io.ClassPathResource("tenant-init/fouwell.json").getInputStream()).path("roles");
        for (JsonNode r : roles) {
            List<Integer> menus = objectMapper.convertValue(r.path("menuIds"), new com.fasterxml.jackson.core.type.TypeReference<List<Integer>>() { });
            if ("业务员".equals(r.path("name").asText())) {
                assertTrue(menus.contains(100055), "业务员模板能用我的询价");
                assertFalse(menus.contains(110219), "业务员模板不是采购人员");
            }
            if ("采购员".equals(r.path("name").asText())) {
                assertTrue(menus.contains(110219), "采购员模板可接询价任务");
            }
        }
    }

    @Test
    void windowSettingFallsBackAndClamps() throws Exception {
        window(-5, 0);
        assertEquals(TASK_UNASSIGNED, status(task(inquiry(false, row("Lenze", "变频器", "N1")), "Lenze")), "负数按 0 处理");
        jdbc.update("update sys_config set config_value = 'abc' where tenant_id = 0 and config_key = 'inquiry.sourcing.sales-window-minutes'");
        assertEquals(TASK_SALES, status(task(inquiry(false, row("ABB", "变频器", "N2")), "ABB")), "填错时用默认值");
    }

    @Test
    void stats_noDataGivesNullRate() throws Exception {
        JsonNode rate = ok(call(get(STATS), admin)).path("workloadTotal").path("selfRate");
        assertTrue(rate.isNull() || rate.isMissingNode(), "分母为 0 时没有比率：" + rate);
    }
}

package com.zhul.erp.modules.inquiry.sourcing;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.modules.inquiry.InquiryContractSupport;
import com.zhul.erp.modules.inquiry.support.PriceKeys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** 询价任务与询价记录（spec inquiry/sourcing-task、sourcing-quote、price-ledger）：分配、推荐、改派、比价、录入、退回、超时、规则、历史询价 */
class SourcingContractTest extends InquiryContractSupport {

    @Autowired private PriceKeys priceKeys;

    private String admin;

    private static Map<String, Object> row(String brand, String category, String model, int qty) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("brand", brand);
        m.put("category", category);
        m.put("confirmedModel", model);
        m.put("quantity", qty);
        return m;
    }

    /** 业务员录入并确认一条询盘，返回客户询盘 ID */
    private long inquiry(boolean urgent, Map<String, Object>... rows) throws Exception {
        long c = customer("Wingrid Rocha", "Brazil");
        long id = ok(call(json(post(INQ), "{\"customerId\":" + c + ",\"source\":1,\"urgent\":" + urgent + ",\"rawContent\":\"rfq\"}"), admin)).path("id").asLong();
        ok(call(json(post(INQ + "/" + id + "/confirm"), write(Map.of("rows", List.of(rows)))), admin));
        return id;
    }

    private long taskId(long inquiryId, String brand) {
        return jdbc.queryForObject("select id from sourcing_task where customer_inquiry_id = ? and brand = ?", Long.class, inquiryId, brand);
    }

    private List<Long> itemIds(long taskId) {
        return jdbc.queryForList("select id from inquiry_item where sourcing_task_id = ? order by line_no", Long.class, taskId);
    }

    private void assign(long taskId, long assignee) throws Exception {
        ok(call(json(post(BOARD + "/assign"), "{\"taskIds\":[" + taskId + "],\"assigneeId\":" + assignee + "}"), admin));
    }

    private static Map<String, Object> entry(int channel, String shop, String price, int condition, int lead) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("channel", channel);
        m.put("shopName", shop);
        m.put("unitPrice", new BigDecimal(price));
        m.put("itemCondition", condition);
        m.put("leadTime", lead);
        return m;
    }

    private String quotes(boolean submit, long itemId, Boolean noStock, String note, List<Map<String, Object>> entries) throws Exception {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("itemId", itemId);
        item.put("noStock", noStock);
        item.put("noStockNote", note);
        item.put("quotes", entries);
        return write(Map.of("submit", submit, "items", List.of(item)));
    }

    private void login() {
        loginAsAdmin("it_src_admin");
        admin = token("it_src_admin");
    }

    @Test
    void board_recommendAssignReassignAndPermissions() throws Exception {
        login();
        String mitsubishi = priceKeys.brand("Mitsubishi").brandKey();
        for (int i = 0; i < 3; i++) {
            jdbc.update("insert into sourcing_task (tenant_id, task_code, brand, brand_key, status) values (0, ?, 'Mitsubishi', ?, 3)", "ITHIST" + i, mitsubishi);
            long t = jdbc.queryForObject("select max(id) from sourcing_task", Long.class);
            jdbc.update("insert into sourcing_task_assignee (tenant_id, task_id, assignee_id, assigned_at, active) values (0, ?, ?, now(), 1)", t,
                    i < 2 ? BUYER_LIN : BUYER_JIANG);
        }
        long inq = inquiry(false, row("Mitsubishi", "伺服电机", "HG-SN302BJ", 1), row("Delta", "HMI", "DOP-110WS", 5));
        long mTask = taskId(inq, "Mitsubishi");
        long dTask = taskId(inq, "Delta");

        JsonNode board = ok(call(get(BOARD), admin));
        assertEquals(2, board.path("stats").path("unassigned").asInt());
        JsonNode m = board.path("tasks").get(0).path("brand").asText().equals("Mitsubishi") ? board.path("tasks").get(0) : board.path("tasks").get(1);
        assertEquals(BUYER_LIN, m.path("recommendedId").asLong(), "近 180 天三菱询得最多");
        assertEquals("Mitsubishi 询过 2 次 · 进行中 0", m.path("recommendReason").asText());
        JsonNode purchasers = board.path("purchasers");
        assertEquals(3, purchasers.size(), "带「我的询价任务」菜单的账号才是采购");
        assertTrue(purchasers.get(2).path("partTime").asBoolean());

        loginWithResources("it_src_viewer", MENU_BOARD);
        assertEquals(403, perform(json(post(BOARD + "/assign"), "{\"taskIds\":[" + mTask + "],\"assigneeId\":" + BUYER_LIN + "}"),
                token("it_src_viewer")).getStatus(), "没有分配按钮权限");
        login();
        assertEquals("该账号不是采购人员，不能分配询价任务",
                call(json(post(BOARD + "/assign"), "{\"taskIds\":[" + mTask + "],\"assigneeId\":99000002}"), admin).path("message").asText());

        ok(call(json(post(BOARD + "/assign-recommended"), "{\"taskIds\":[" + mTask + "]}"), admin));
        assign(dTask, PART_TIMER);
        assertEquals(1, ok(call(get(MY), token("it_lin"))).size());
        assertEquals(dTask, ok(call(get(MY), token("it_wang"))).get(0).path("id").asLong(), "兼职采购只看到自己的任务");
        assertEquals("任务 " + jdbc.queryForObject("select task_code from sourcing_task where id = ?", String.class, mTask) + " 已经分配过了，请刷新后再试",
                call(json(post(BOARD + "/assign"), "{\"taskIds\":[" + mTask + "],\"assigneeId\":" + BUYER_JIANG + "}"), admin).path("message").asText());

        ok(call(json(post(BOARD + "/tasks/" + mTask + "/reassign"), "{\"assigneeId\":" + BUYER_JIANG + "}"), admin));
        assertEquals(0, ok(call(get(MY), token("it_lin"))).size(), "改派后原采购看不到");
        assertEquals("询价任务不存在", call(get(MY + "/" + mTask), token("it_lin")).path("message").asText());
        ok(call(json(post(BOARD + "/tasks/" + mTask + "/assignees"), "{\"assigneeId\":" + BUYER_LIN + "}"), admin));
        assertTrue(ok(call(get(MY), token("it_lin"))).get(0).path("shared").asBoolean(), "追加比价后两人都能看到");
        assertEquals(1, ok(call(get(BOARD), admin)).path("stats").path("multiAssigned").asInt());
    }

    @Test
    void purchasersSeeLevelSalesAndCustomer_partTimerNoCustomerName() throws Exception {
        login();
        long normal = inquiry(false, row("Omron", "传感器", "E3Z-D61", 1));
        long important = inquiry(false, row("Delta", "HMI", "DOP-110WS", 2));
        ok(call(json(put(INQ + "/" + important + "/level"), "{\"level\":1}"), admin));
        long nTask = taskId(normal, "Omron");
        long sTask = taskId(important, "Delta");

        JsonNode tasks = ok(call(get(BOARD), admin)).path("tasks");
        assertEquals(sTask, tasks.get(0).path("id").asLong(), "同样未超时、不紧急时，S 级询盘的任务排在前面");
        assertEquals(1, tasks.get(0).path("level").asInt());
        assertEquals("IT", tasks.get(0).path("salesName").asText(), "显示所属业务员");
        assertEquals("Wingrid Rocha", tasks.get(0).path("customerName").asText());
        assertEquals(1, tasks.get(0).path("customerType").asInt());

        JsonNode detail = ok(call(get(BOARD + "/tasks/" + sTask), admin));
        assertEquals("DOP-110WS", detail.path("items").get(0).path("model").asText());
        assertEquals(2, detail.path("items").get(0).path("quantity").asInt());
        assertEquals("Wingrid Rocha", detail.path("task").path("customerName").asText());
        assertTrue(detail.toString().indexOf("rawContent") < 0, "任务详情不含原始询盘内容");

        assign(sTask, BUYER_LIN);
        assign(nTask, PART_TIMER);
        JsonNode linTask = ok(call(get(MY), token("it_lin"))).get(0);
        assertEquals("Wingrid Rocha", linTask.path("customerName").asText(), "正式采购能看到客户名称");
        assertEquals(1, linTask.path("level").asInt());
        JsonNode wangTask = ok(call(get(MY), token("it_wang"))).get(0);
        assertTrue(wangTask.path("customerName").isMissingNode(), "兼职采购看不到客户名称：" + wangTask);
        assertEquals(1, wangTask.path("customerType").asInt(), "兼职采购仍能看到新老客户");
        assertTrue(ok(call(get(MY + "/" + nTask), token("it_wang"))).path("task").path("customerName").isMissingNode());
    }

    @Test
    void quotes_partialSubmitNoStockProgressAndPriceHistory() throws Exception {
        login();
        long inq = inquiry(false, row("Lenze", "变频器", "E84DGDVB55142PS", 1), row("Lenze", "变频器", "E82EV551K2C", 2));
        long task = taskId(inq, "Lenze");
        List<Long> items = itemIds(task);
        assign(task, BUYER_LIN);
        String lin = token("it_lin");

        String detail = perform(get(MY + "/" + task), lin).getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(detail.contains("Wingrid"), "正式采购能看到客户名称，用来判断优先级");
        assertFalse(detail.contains("rawContent"), "采购看不到原始询盘内容");
        JsonNode d = objectMapper.readTree(detail).path("data");
        assertTrue(d.path("items").get(0).path("inquiryScript").asText().startsWith("帮我查一下 Lenze 的 E84DGDVB55142PS"));
        assertEquals(2, d.path("items").get(0).path("searchKeywords").size());

        JsonNode negative = call(json(put(MY + "/" + task + "/quotes"), quotes(true, items.get(0), false, null,
                List.of(entry(2, "x", "-5", 1, 1)))), lin);
        assertTrue(negative.path("message").asText().contains("单价不能为负"), negative.toString());

        ok(call(json(put(MY + "/" + task + "/quotes"), quotes(false, items.get(0), false, null,
                List.of(entry(2, "明和电气", "3200", 1, 1)))), lin));
        assertEquals(1, ok(call(get(MY), lin)).get(0).path("filledCount").asInt(), "草稿也算已填");
        assertEquals(0, jdbc.queryForObject("select priced_item_count from customer_inquiry where id = ?", Integer.class, inq), "草稿不计入回价");

        ok(call(json(put(MY + "/" + task + "/quotes"), quotes(true, items.get(0), false, null,
                List.of(entry(2, "明和电气", "3200", 1, 1), entry(3, "个人", "1800", 4, 3)))), lin));
        JsonNode afterFirst = ok(call(get(INQ + "/" + inq), admin));
        assertEquals(1, afterFirst.path("inquiry").path("pricedItemCount").asInt(), "部分提交计入回价进度");
        assertEquals(5, afterFirst.path("inquiry").path("status").asInt());
        assertEquals(0, new BigDecimal("3200").compareTo(afterFirst.path("items").get(0).path("selectedQuote").path("unitPriceCny").decimalValue()),
                "默认选全新原装");
        assertEquals(1, afterFirst.path("items").get(0).path("selectedQuote").path("leadTime").asInt(), "货期码值 1-现货");
        assertEquals("明和电气", afterFirst.path("items").get(0).path("selectedQuote").path("shopName").asText(), "有货源权限可以看到店铺");
        assertEquals(1, afterFirst.path("tasks").get(0).path("pricedCount").asInt());

        assertTrue(call(json(put(MY + "/" + task + "/quotes"), quotes(false, items.get(1), false, null,
                List.of(entry(2, "x", "100", 1, 99)))), lin).path("message").asText().contains("货期不在可选值内"), "货期只能从字典里选");
        Map<String, Object> discontinued = new LinkedHashMap<>();
        discontinued.put("itemId", items.get(1));
        discontinued.put("noStock", true);
        discontinued.put("noStockNote", "全网无现货");
        discontinued.put("quotes", List.of());
        discontinued.put("lifecycle", 9);
        assertTrue(call(json(put(MY + "/" + task + "/quotes"), write(Map.of("submit", false, "items", List.of(discontinued)))), lin)
                .path("message").asText().contains("生产状态不在可选值内"), "生产状态只能取字典里启用的选项");
        discontinued.put("lifecycle", 2);
        discontinued.put("replacementModel", "E84AVSCE5534SB0");
        ok(call(json(put(MY + "/" + task + "/quotes"), write(Map.of("submit", true, "items", List.of(discontinued)))), lin));
        assertEquals(2, jdbc.queryForObject("select lifecycle from inquiry_item where id = ?", Integer.class, items.get(1)), "采购核实的生产状态写回型号");
        assertEquals("E84AVSCE5534SB0", jdbc.queryForObject("select replacement_model from inquiry_item where id = ?", String.class, items.get(1)));
        assertEquals(task, jdbc.queryForObject("select sourcing_task_id from inquiry_item where id = ?", Long.class, items.get(1)),
                "改生产状态不能把型号的所属任务清空");
        assertEquals(2, ok(call(get(INQ + "/" + inq), admin)).path("tasks").get(0).path("pricedCount").asInt(), "询价任务的回价数正确");
        JsonNode done = ok(call(get(INQ + "/" + inq), admin));
        assertEquals(6, done.path("inquiry").path("status").asInt(), "全部有价格或无货后进入可报价");
        assertEquals(3, done.path("items").get(1).path("quoteStatus").asInt());
        assertEquals("全网无现货", done.path("items").get(1).path("noStockNote").asText());
        assertEquals(3, done.path("tasks").get(0).path("status").asInt());

        // 业务员不能再改选价格：原来的改选接口已下线
        assertTrue(perform(get(INQ + "/items/" + items.get(0) + "/prices"), admin).getStatus() >= 400, "业务员改选价格的接口已下线");
        // 采购负责人在任务详情里看到全部询价记录，可以指定采购成本价
        JsonNode prices = ok(call(get(BOARD + "/tasks/" + task), admin)).path("items").get(0).path("quotes");
        assertEquals(2, prices.size());
        long used = prices.get(0).path("unitPriceCny").decimalValue().compareTo(new BigDecimal("1800")) == 0
                ? prices.get(0).path("id").asLong() : prices.get(1).path("id").asLong();
        ok(call(json(put(BOARD + "/items/" + items.get(0) + "/cost-quote"), "{\"quoteId\":" + used + "}"), admin));
        assertEquals(used, ok(call(get(INQ + "/" + inq), admin)).path("items").get(0).path("selectedQuote").path("id").asLong());
        assertTrue(ok(call(get(BOARD + "/tasks/" + task), admin)).path("items").get(0).path("costManual").asBoolean());

        JsonNode history = ok(call(json(post(HISTORY + "/page"), "{\"model\":\"e84 dgdv\"}"), admin));
        assertEquals(1, history.path("total").asInt(), "按归一化型号模糊搜索");
        assertEquals(0, new BigDecimal("1800").compareTo(history.path("records").get(0).path("minPriceCny").decimalValue()));
        assertEquals(2, history.path("records").get(0).path("recordCount").asInt());
        assertEquals("变频器", history.path("records").get(0).path("category").asText(), "品类取自型号明细");
        assertTrue(history.path("records").get(0).path("records").findValuesAsText("shopName").contains("明和电气"), "管理员可以看到店铺");
        JsonNode masked = ok(call(json(post(HISTORY + "/page"), "{\"model\":\"e84 dgdv\"}"), lin)).path("records").get(0).path("records");
        assertEquals(2, masked.size());
        assertTrue(masked.findValues("channel").isEmpty(), "没有「查看货源信息」看不到平台：" + masked);
        assertTrue(masked.findValues("shopName").isEmpty(), "没有「查看货源信息」看不到店铺：" + masked);
        assertEquals(403, perform(json(post(HISTORY + "/page"), "{}"), token("it_wang")).getStatus(), "兼职采购看不到历史询价");
    }

    private long recommendedCost(long inq) throws Exception {
        return ok(call(get(INQ + "/" + inq), admin)).path("items").get(0).path("selectedQuote").path("unitPriceCny").decimalValue().longValue();
    }

    private static Map<String, Object> rec(Map<String, Object> entry) {
        entry.put("recommended", true);
        return entry;
    }

    @Test
    void costPrice_recommendedEditWithLogSupplierAndLock() throws Exception {
        login();
        long inq = inquiry(false, row("Omron", "传感器", "E3Z-D61", 10));
        long task = taskId(inq, "Omron");
        long item = itemIds(task).get(0);
        assign(task, BUYER_LIN);
        // 多人比价：分配后再追加一人（任务还在询价中时才能追加）
        ok(call(json(post(BOARD + "/tasks/" + task + "/assignees"), "{\"assigneeId\":" + BUYER_JIANG + "}"), admin));
        String lin = token("it_lin");
        String jiang = token("it_jiang");

        assertTrue(call(json(put(MY + "/" + task + "/quotes"), quotes(true, item, false, null,
                List.of(rec(entry(1, "a", "1000", 1, 1)), rec(entry(2, "b", "900", 1, 1))))), lin).path("message").asText()
                .contains("只能标一条推荐报价"));
        ok(call(json(put(MY + "/" + task + "/quotes"), quotes(true, item, false, null,
                List.of(entry(1, "a", "800", 4, 1), rec(entry(2, "b", "900", 1, 1))))), lin));
        assertEquals(900, recommendedCost(inq), "采购标的推荐报价就是采购成本价，即使有更便宜的二手");
        assertTrue(ok(call(get(MY + "/" + task), lin)).path("items").get(0).path("quotes").findValues("recommended").stream()
                .anyMatch(JsonNode::asBoolean));

        // 修改已回价：旧版本软删除并留痕，历史询价只留最新
        String logSql = "select count(*) from sys_log where operation = '修改回价'";
        int logs = jdbc.queryForObject(logSql, Integer.class);
        ok(call(json(put(MY + "/" + task + "/quotes"), quotes(true, item, false, null, List.of(rec(entry(2, "b", "950", 1, 1))))), lin));
        assertEquals(950, recommendedCost(inq));
        assertEquals(logs + 1, jdbc.queryForObject(logSql, Integer.class), "修改回价留痕");
        assertEquals(1, jdbc.queryForObject("select count(*) from sourcing_quote where inquiry_item_id = ? and deleted_at is null", Integer.class, item));
        assertEquals(2, jdbc.queryForObject("select count(*) from sourcing_quote where inquiry_item_id = ? and deleted_at is not null and status = 2",
                Integer.class, item), "旧版本软删除");

        // 供应商渠道必须选供应商主数据，店铺名以主数据为准
        assertTrue(call(json(put(MY + "/" + task + "/quotes"), quotes(true, item, false, null, List.of(rec(entry(4, "手填", "940", 1, 1))))), lin)
                .path("message").asText().contains("请从供应商列表中选择"));
        jdbc.update("delete from supplier where supplier_code = 'ITSUP001'");
        jdbc.update("insert into supplier (tenant_id, supplier_code, name) values (0, 'ITSUP001', '深圳明和电气')");
        long supplierId = jdbc.queryForObject("select id from supplier where supplier_code = 'ITSUP001'", Long.class);
        Map<String, Object> bySupplier = rec(entry(4, "随便写", "940", 1, 1));
        bySupplier.put("supplierId", supplierId);
        ok(call(json(put(MY + "/" + task + "/quotes"), quotes(true, item, false, null, List.of(bySupplier))), lin));
        assertEquals("深圳明和电气", jdbc.queryForObject("select shop_name from sourcing_quote where inquiry_item_id = ? and deleted_at is null",
                String.class, item));

        // 多人比价：默认取各自推荐里最低；采购负责人指定后，别人再改也不变；被指定的记录改掉后回到自动
        ok(call(json(put(MY + "/" + task + "/quotes"), quotes(true, item, false, null, List.of(rec(entry(2, "c", "880", 1, 1))))), jiang));
        assertEquals(880, recommendedCost(inq), "各自推荐里取最低");
        long linQuote = jdbc.queryForObject("select id from sourcing_quote where inquiry_item_id = ? and quoted_by = ? and deleted_at is null",
                Long.class, item, BUYER_LIN);
        ok(call(json(put(BOARD + "/items/" + item + "/cost-quote"), "{\"quoteId\":" + linQuote + "}"), admin));
        ok(call(json(put(MY + "/" + task + "/quotes"), quotes(true, item, false, null, List.of(rec(entry(2, "c", "870", 1, 1))))), jiang));
        assertEquals(940, recommendedCost(inq), "采购负责人指定的成本价不被别人的修改覆盖");
        ok(call(json(put(MY + "/" + task + "/quotes"), quotes(true, item, false, null, List.of(rec(entry(2, "b", "960", 1, 1))))), lin));
        assertEquals(870, recommendedCost(inq), "被指定的记录改掉后回到按推荐自动取");
        ok(call(json(put(BOARD + "/items/" + item + "/cost-quote"), "{\"quoteId\":null}"), admin));

        // 修改记录：林熙 4 版（1 次首次回价 + 3 次修改）、江晓晞 2 版、采购负责人指定 1 次 + 恢复自动 1 次
        JsonNode history = ok(call(get(BOARD + "/items/" + item + "/history"), admin));
        assertEquals(8, history.size(), history.toString());
        // 测试里所有操作都在同一两秒内发生，时间相同的记录先后无法区分，这里按内容断言
        List<String> notes = history.findValuesAsText("note");
        assertTrue(notes.stream().anyMatch(n -> n.startsWith("指定为 ¥940.00 林熙")), notes.toString());
        assertTrue(notes.stream().anyMatch(n -> n.startsWith("恢复按推荐报价自动取，当前为 ¥870.00")), notes.toString());
        assertEquals(2, history.findValuesAsText("action").stream().filter("首次回价"::equals).count(), "两位采购各有一次首次回价");
        assertEquals(2, history.findValues("current").stream().filter(JsonNode::asBoolean).count(), "每位采购只有一版当前有效");
        JsonNode linFirst = null;
        for (JsonNode e : history) {
            if ("首次回价".equals(e.path("action").asText()) && "林熙".equals(e.path("operatorName").asText())) {
                linFirst = e;
            }
        }
        assertEquals(2, linFirst.path("quotes").size(), "首次回价那一版保留了两条记录");
        assertEquals(6, ok(call(get(BOARD + "/tasks/" + task), admin)).path("items").get(0).path("changeCount").asInt(),
                "修改次数 = 修改回价 4 次 + 调整成本价 2 次");

        // 已回价页签：业务员报价前列出，报价后不再列出
        JsonNode doneBoard = ok(call(get(BOARD).param("status", "3"), admin));
        assertTrue(doneBoard.path("tasks").findValuesAsText("taskCode").contains(
                jdbc.queryForObject("select task_code from sourcing_task where id = ?", String.class, task)), "已回价的任务出现在已回价页签");
        assertTrue(doneBoard.path("stats").path("done").asInt() >= 1);

        // 业务员报价后：已回价页签不再列出；型号出现在已发送的报价单中后回价与采购成本价只读
        jdbc.update("update customer_inquiry set status = 7 where id = ?", inq);
        assertFalse(ok(call(get(BOARD).param("status", "3"), admin)).path("tasks").findValuesAsText("id").contains(String.valueOf(task)),
                "业务员报价后不再出现在已回价页签");
        jdbc.update("insert into quotation (tenant_id, quotation_no, customer_id, status) values (0, 'QTIT00000001', 0, 2)");
        long sentQuotation = jdbc.queryForObject("select id from quotation where quotation_no = 'QTIT00000001'", Long.class);
        jdbc.update("insert into quotation_item (tenant_id, quotation_id, customer_inquiry_id, inquiry_item_id, model) values (0, ?, ?, ?, 'x')",
                sentQuotation, inq, item);
        String model = jdbc.queryForObject("select confirmed_model from inquiry_item where id = ?", String.class, item);
        assertEquals(model + " 已报给客户，回价不能再修改", call(json(put(MY + "/" + task + "/quotes"), quotes(true, item, false, null,
                List.of(rec(entry(2, "b", "1", 1, 1))))), lin).path("message").asText());
        assertEquals("型号已报给客户，采购成本价不能再修改", call(json(put(BOARD + "/items/" + item + "/cost-quote"),
                "{\"quoteId\":" + linQuote + "}"), admin).path("message").asText());
        assertTrue(ok(call(get(MY + "/" + task), lin)).path("items").get(0).path("locked").asBoolean(), "已报出的型号标为只读");
        jdbc.update("delete from quotation_item where quotation_id = ?", sentQuotation);
        jdbc.update("delete from quotation where id = ?", sentQuotation);
        jdbc.update("delete from supplier where supplier_code = 'ITSUP001'");
    }

    @Test
    void taxIncludedQuote_comparedByPriceExcludingTax() throws Exception {
        login();
        long inq = inquiry(false, row("Delta", "HMI", "DOP-107BV", 1));
        long task = taskId(inq, "Delta");
        long item = itemIds(task).get(0);
        assign(task, BUYER_LIN);
        String lin = token("it_lin");
        Map<String, Object> taxed = entry(1, "含税店", "113", 1, 1);
        taxed.put("taxIncluded", true);
        taxed.put("taxRate", 7);
        assertTrue(call(json(put(MY + "/" + task + "/quotes"), quotes(true, item, false, null, List.of(taxed))), lin)
                .path("message").asText().contains("税率不在可选值内"));
        taxed.put("taxRate", 13);
        ok(call(json(put(MY + "/" + task + "/quotes"), quotes(true, item, false, null, List.of(taxed, entry(2, "不含税店", "105", 1, 1)))), lin));
        JsonNode cost = ok(call(get(INQ + "/" + inq), admin)).path("items").get(0).path("selectedQuote");
        assertEquals(0, new BigDecimal("100.00").compareTo(cost.path("unitPriceCny").decimalValue()), "含税 113 ÷ 1.13 = 100，比 105 便宜");
        assertEquals(0, new BigDecimal("113.00").compareTo(cost.path("unitPrice").decimalValue()), "原价保留");
        assertTrue(cost.path("taxIncluded").asBoolean());
        assertEquals(0, new BigDecimal("13").compareTo(cost.path("taxRate").decimalValue()));
    }

    @Test
    void returnTimeoutAndReview() throws Exception {
        login();
        long inq = inquiry(false, row("Mitsubishi", "其他", "MR-PWCNS4", 5));
        long task = taskId(inq, "Mitsubishi");
        assign(task, BUYER_LIN);
        jdbc.update("update sourcing_task set first_assigned_at = date_sub(now(), interval 25 hour) where id = ?", task);
        assertEquals(1, ok(call(get(BOARD).param("status", "2"), admin)).path("stats").path("timeout").asInt());
        assertTrue(ok(call(get(MY), token("it_lin"))).get(0).path("timeout").asBoolean());
        assertEquals(1, ok(call(json(post(INQ + "/page"), "{\"timeoutOnly\":true}"), admin)).path("total").asInt());
        assertEquals(1, ok(call(json(post(INQ + "/page"), "{}"), admin)).path("records").get(0).path("timeoutTaskCount").asInt());

        long urgent = inquiry(true, row("Omron", "传感器", "E3Z-D61", 10));
        long uTask = taskId(urgent, "Omron");
        assign(uTask, BUYER_JIANG);
        jdbc.update("update sourcing_task set first_assigned_at = date_sub(now(), interval 5 hour) where id = ?", uTask);
        assertEquals(2, ok(call(get(BOARD).param("status", "2"), admin)).path("stats").path("timeout").asInt(), "紧急任务 4 小时超时");

        ok(call(json(post(MY + "/" + task + "/return"), "{\"reason\":1,\"note\":\"像是少写了后缀\"}"), token("it_lin")));
        JsonNode pool = ok(call(get(BOARD), admin));
        JsonNode returned = pool.path("tasks").get(0);
        assertEquals(task, returned.path("id").asLong(), "退回后回到待分配池");
        assertEquals("型号存疑", returned.path("returnReasonLabel").asText());
        assertEquals("林熙", returned.path("returnedByName").asText());
        assertEquals(1, pool.path("stats").path("returnedForDoubt").asInt());
        assertTrue(ok(call(get(INQ + "/" + inq), admin)).path("inquiry").path("needsReview").asBoolean(), "型号存疑提醒业务员核实");
        ok(call(post(INQ + "/" + inq + "/reviewed"), admin));
        assertFalse(ok(call(get(INQ + "/" + inq), admin)).path("inquiry").path("needsReview").asBoolean());

        ok(call(json(post(BOARD + "/tasks/" + uTask + "/assignees"), "{\"assigneeId\":" + BUYER_LIN + "}"), admin));
        ok(call(json(post(MY + "/" + uTask + "/return"), "{\"reason\":3}"), token("it_lin")));
        assertEquals(2, jdbc.queryForObject("select status from sourcing_task where id = ?", Integer.class, uTask), "比价时只退出自己");
    }

    @Test
    void rules_previewManualTrialAndAutoAssign() throws Exception {
        login();
        ok(call(json(post(BOARD + "/rules"), "{\"matchType\":1,\"matchValues\":[\"delta\"],\"assigneeId\":" + BUYER_JIANG + "}"), admin));
        JsonNode rules = ok(call(get(BOARD + "/rules"), admin));
        assertFalse(rules.path("autoAssign").asBoolean(), "自动分配默认关闭");
        assertEquals("江晓晞", rules.path("rules").get(0).path("assigneeName").asText());

        long inq = inquiry(false, row("Delta", "HMI", "DOP-110WS", 5), row("Mitsubishi", "伺服电机", "HG-SN302BJ", 1));
        long dTask = taskId(inq, "Delta");
        long mTask = taskId(inq, "Mitsubishi");
        assertEquals(1, jdbc.queryForObject("select status from sourcing_task where id = ?", Integer.class, dTask), "开关关闭时不自动分配");
        JsonNode preview = ok(call(json(post(BOARD + "/rule-preview"), "{\"taskIds\":[" + dTask + "," + mTask + "]}"), admin));
        for (JsonNode p : preview) {
            if (p.path("taskId").asLong() == dTask) {
                assertEquals(BUYER_JIANG, p.path("assigneeId").asLong());
                assertEquals("规则 1", p.path("basis").asText());
            } else {
                assertEquals("没有命中规则，按推荐", p.path("basis").asText());
            }
        }
        assertEquals(1, jdbc.queryForObject("select status from sourcing_task where id = ?", Integer.class, dTask), "预览不分配");
        ok(call(json(post(BOARD + "/assign-by-rule"), "{\"taskIds\":[" + dTask + "]}"), admin));
        assertEquals(BUYER_JIANG, jdbc.queryForObject("select assignee_id from sourcing_task_assignee where task_id = ? and active = 1", Long.class, dTask));

        ok(call(json(put(BOARD + "/rules/auto-assign"), "{\"enabled\":true}"), admin));
        long next = inquiry(false, row("Delta", "HMI", "DOP-107BV", 2));
        long auto = taskId(next, "Delta");
        assertEquals(2, jdbc.queryForObject("select status from sourcing_task where id = ?", Integer.class, auto), "开关打开后自动分配");
        assertEquals(BUYER_JIANG, jdbc.queryForObject("select assignee_id from sourcing_task_assignee where task_id = ?", Long.class, auto));
    }

    @Test
    void concurrentSubmitsKeepProgressConsistent() throws Exception {
        login();
        long inq = inquiry(false, row("ABB", "变频器", "ACS580-01-12A7-4", 1), row("ABB", "变频器", "ACS580-01-17A0-4", 1));
        long task = taskId(inq, "ABB");
        List<Long> items = itemIds(task);
        assign(task, BUYER_LIN);
        ok(call(json(post(BOARD + "/tasks/" + task + "/assignees"), "{\"assigneeId\":" + BUYER_JIANG + "}"), admin));
        String lin = token("it_lin");
        String jiang = token("it_jiang");
        String b1 = quotes(true, items.get(0), false, null, List.of(entry(2, "a", "4100", 1, 1)));
        String b2 = quotes(true, items.get(1), false, null, List.of(entry(1, "b", "5200", 1, 1)));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Callable<JsonNode>> jobs = new ArrayList<>();
        jobs.add(() -> {
            start.await();
            return call(json(put(MY + "/" + task + "/quotes"), b1), lin);
        });
        jobs.add(() -> {
            start.await();
            return call(json(put(MY + "/" + task + "/quotes"), b2), jiang);
        });
        List<Future<JsonNode>> futures = new ArrayList<>();
        for (Callable<JsonNode> j : jobs) {
            futures.add(pool.submit(j));
        }
        start.countDown();
        for (Future<JsonNode> f : futures) {
            ok(f.get());
        }
        pool.shutdown();
        assertEquals(2, jdbc.queryForObject("select priced_item_count from customer_inquiry where id = ?", Integer.class, inq));
        assertEquals(6, jdbc.queryForObject("select status from customer_inquiry where id = ?", Integer.class, inq));
        assertEquals(3, jdbc.queryForObject("select status from sourcing_task where id = ?", Integer.class, task));
    }
}

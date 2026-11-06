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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** spec inquiry/sourcing-task：分配工作台搜索与筛选、全部询价任务 */
class SourcingBoardSearchContractTest extends InquiryContractSupport {

    private String admin;

    private long inquiry(String model) throws Exception {
        long c = customer("Board Search Co", "Kenya");
        long id = ok(call(json(post(INQ), "{\"customerId\":" + c + ",\"source\":1,\"rawContent\":\"rfq\"}"), admin)).path("id").asLong();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("brand", "Siemens");
        row.put("category", "PLC");
        row.put("confirmedModel", model);
        row.put("quantity", 1);
        ok(call(json(post(INQ + "/" + id + "/confirm"), write(Map.of("rows", List.of(row)))), admin));
        return id;
    }

    private long taskOf(long inquiryId) {
        return jdbc.queryForObject("select id from sourcing_task where customer_inquiry_id = ?", Long.class, inquiryId);
    }

    private JsonNode board(int status, String keyword, Long purchaser) throws Exception {
        var req = get(BOARD).param("status", String.valueOf(status));
        if (keyword != null) {
            req.param("keyword", keyword);
        }
        if (purchaser != null) {
            req.param("purchaserId", String.valueOf(purchaser));
        }
        return ok(call(req, admin));
    }

    private static List<Long> ids(JsonNode tasks) {
        List<Long> out = new ArrayList<>();
        tasks.forEach(t -> out.add(t.path("id").asLong()));
        return out;
    }

    @Test
    void boardFiltersByKeywordAndPurchaser_statsUnfiltered() throws Exception {
        loginAsAdmin("it_sbs_admin");
        admin = token("it_sbs_admin");
        long t1 = taskOf(inquiry("6ES7315-2AH14-0AB0"));
        long t2 = taskOf(inquiry("6AV2124-0GC01-0AX0"));
        ok(call(json(post(BOARD + "/assign"), "{\"taskIds\":[" + t1 + "],\"assigneeId\":" + BUYER_LIN + "}"), admin));

        JsonNode all = board(1, null, null);
        int unassignedAll = all.path("stats").path("unassigned").asInt();
        assertTrue(ids(all.path("tasks")).contains(t2));

        // 关键词：型号带空格也能搜到；页签数字跟着变，顶部统计不变
        JsonNode hit = board(2, "6ES7 315", null);
        assertEquals(List.of(t1), ids(hit.path("tasks")));
        assertEquals(1, hit.path("tabs").path("sourcing").asInt());
        assertEquals(0, hit.path("tabs").path("unassigned").asInt());
        assertEquals(unassignedAll, hit.path("stats").path("unassigned").asInt(), "顶部统计不受筛选影响");
        assertEquals(List.of(t2), ids(board(1, "6AV2", null).path("tasks")));

        // 采购员：只看当前在办；待分配为 0
        JsonNode lin = board(2, null, BUYER_LIN);
        assertTrue(ids(lin.path("tasks")).contains(t1));
        assertEquals(0, lin.path("tabs").path("unassigned").asInt());
        assertFalse(ids(board(2, null, BUYER_JIANG).path("tasks")).contains(t1));
        ok(call(json(post(BOARD + "/tasks/" + t1 + "/reassign"), "{\"assigneeId\":" + BUYER_JIANG + "}"), admin));
        assertTrue(ids(board(2, null, BUYER_JIANG).path("tasks")).contains(t1));
        assertFalse(ids(board(2, null, BUYER_LIN).path("tasks")).contains(t1), "改派后原采购不再在办");
    }

    @Test
    void historyListsAllTasks_withStates_formerPurchaser() throws Exception {
        loginAsAdmin("it_sbs_admin");
        admin = token("it_sbs_admin");
        long inq1 = inquiry("6ES7315-2AH14-0AB0");
        long t1 = taskOf(inq1);
        long t2 = taskOf(inquiry("6AV2124-0GC01-0AX0"));
        ok(call(json(post(BOARD + "/assign"), "{\"taskIds\":[" + t1 + "],\"assigneeId\":" + BUYER_LIN + "}"), admin));
        ok(call(json(post(BOARD + "/tasks/" + t1 + "/reassign"), "{\"assigneeId\":" + BUYER_JIANG + "}"), admin));

        JsonNode page = ok(call(get(BOARD + "/all"), admin));
        List<Long> listed = ids(page.path("records"));
        assertTrue(listed.contains(t1) && listed.contains(t2));
        JsonNode r1 = null;
        for (JsonNode r : page.path("records")) {
            if (r.path("id").asLong() == t1) {
                r1 = r;
            }
        }
        assertEquals("询价中", r1.path("stateName").asText());
        assertEquals(2, r1.path("purchaserNames").size(), "改派掉的采购也列出，当前在办的在前");

        // 改派掉的林熙按采购员也能查到
        assertTrue(ids(ok(call(get(BOARD + "/all").param("purchaserId", String.valueOf(BUYER_LIN)), admin)).path("records")).contains(t1));
        assertEquals(List.of(t2), ids(ok(call(get(BOARD + "/all").param("state", "1").param("keyword", "6AV2"), admin)).path("records")));

        // 回价后询盘已报价：「已报价」；询盘取消：「已关闭」
        jdbc.update("update sourcing_task set status = 3 where id = ?", t1);
        jdbc.update("update customer_inquiry set status = 7 where id = ?", inq1);
        JsonNode quoted = ok(call(get(BOARD + "/all").param("state", "4"), admin));
        assertEquals(List.of(t1), ids(quoted.path("records")));
        assertEquals("已报价", quoted.path("records").get(0).path("stateName").asText());
        jdbc.update("update customer_inquiry set status = 10 where id = ?", inq1);
        assertTrue(ids(ok(call(get(BOARD + "/all").param("state", "5"), admin)).path("records")).contains(t1));
        assertFalse(ids(ok(call(get(BOARD + "/all").param("state", "4"), admin)).path("records")).contains(t1));

        // 日期范围外查不到
        assertEquals(0, ok(call(get(BOARD + "/all").param("dateFrom", "2020-01-01").param("dateTo", "2020-01-31"), admin))
                .path("total").asInt());
    }
}

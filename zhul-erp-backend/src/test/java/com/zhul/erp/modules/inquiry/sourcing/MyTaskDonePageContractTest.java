package com.zhul.erp.modules.inquiry.sourcing;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.modules.inquiry.InquiryContractSupport;
import com.zhul.erp.modules.inquiry.support.InquiryLookups;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** spec inquiry/sourcing-quote「我的询价」已回价分页：按本人最后回价时间倒序，只组装当前页（audit OPS-010） */
class MyTaskDonePageContractTest extends InquiryContractSupport {

    @SpyBean private InquiryLookups lookups;

    private String admin;

    private long doneTask(String model, String lin) throws Exception {
        long c = customer("Wingrid Rocha", "Brazil");
        long inquiry = ok(call(json(post(INQ), "{\"customerId\":" + c + ",\"source\":1,\"rawContent\":\"rfq\"}"), admin)).path("id").asLong();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("brand", "Siemens");
        row.put("category", "PLC");
        row.put("confirmedModel", model);
        row.put("quantity", 2);
        ok(call(json(post(INQ + "/" + inquiry + "/confirm"), write(Map.of("rows", List.of(row)))), admin));
        long task = jdbc.queryForObject("select id from sourcing_task where customer_inquiry_id = ?", Long.class, inquiry);
        ok(call(json(post(BOARD + "/assign"), "{\"taskIds\":[" + task + "],\"assigneeId\":" + BUYER_LIN + "}"), admin));
        long item = jdbc.queryForObject("select id from inquiry_item where sourcing_task_id = ?", Long.class, task);
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("channel", 2);
        q.put("shopName", "明和电气");
        q.put("unitPrice", new BigDecimal("100"));
        q.put("itemCondition", 1);
        q.put("leadTime", 1);
        ok(call(json(put(MY + "/" + task + "/quotes"), write(Map.of("submit", true,
                "items", List.of(Map.of("itemId", item, "quotes", List.of(q)))))), lin));
        return task;
    }

    @Test
    void donePage_sortedByLastQuote_assemblesOnlyCurrentPage() throws Exception {
        loginAsAdmin("it_done_admin");
        admin = token("it_done_admin");
        String lin = token("it_lin");
        long t1 = doneTask("6ES7214-1AG40-0XB0", lin);
        long t2 = doneTask("6ES7215-1AG40-0XB0", lin);
        long t3 = doneTask("6ES7217-1AG40-0XB0", lin);
        // 回价时间各不相同，任务本身更早更新：按回价时间倒序为 t2、t3、t1
        jdbc.update("update sourcing_task set update_time = date_sub(now(), interval 5 hour) where id in (?, ?, ?)", t1, t2, t3);
        jdbc.update("update sourcing_quote set update_time = date_sub(now(), interval 3 hour) where task_id = ?", t1);
        jdbc.update("update sourcing_quote set update_time = date_sub(now(), interval 1 hour) where task_id = ?", t2);
        jdbc.update("update sourcing_quote set update_time = date_sub(now(), interval 2 hour) where task_id = ?", t3);

        List<Long> order = new ArrayList<>();
        for (int page = 1; page <= 3; page++) {
            clearInvocations(lookups);
            JsonNode res = ok(call(get(MY + "/done").param("page", String.valueOf(page)).param("pageSize", "1"), lin));
            assertEquals(3, res.path("total").asInt());
            assertEquals(1, res.path("records").size());
            order.add(res.path("records").get(0).path("id").asLong());
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Collection<Long>> ids = ArgumentCaptor.forClass(Collection.class);
            verify(lookups, atLeastOnce()).briefs(ids.capture(), anyBoolean());
            assertTrue(ids.getAllValues().stream().allMatch(c -> c.size() <= 1), "只为当前页的任务组装：" + ids.getAllValues());
        }
        assertEquals(List.of(t2, t3, t1), order);
        assertEquals(0, ok(call(get(MY + "/done").param("page", "4").param("pageSize", "1"), lin)).path("records").size());
    }
}

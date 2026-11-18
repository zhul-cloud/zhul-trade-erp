package com.zhul.erp.modules.sales;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** spec sales/sales-order「再下一单」：按原订单预填新建手动订单 */
class ReorderContractTest extends SalesContractSupport {

    @Test
    void reorder_prefillsCustomerLinesAndFees_flagsUnarchivedModels() throws Exception {
        long c = customer("ACROBOT", "India");
        long product = manualProduct("REORDER-M1");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("customerId", c);
        m.put("currencyCode", "EUR");
        m.put("items", List.of(Map.of("productId", product, "quantity", 3, "unitPrice", "12.50", "costPrice", "60", "stockType", 2)));
        m.put("fees", List.of(Map.of("feeName", "Shipping Cost", "amount", "35")));
        long so = ok(call(json(post(SO), write(m)), admin)).path("id").asLong();
        // 再加一行没有商品档案的型号（如由 PI 转成、询盘型号未建档的行）
        jdbc.update("insert into sales_order_item (tenant_id, so_id, line_no, model, brand, category, quantity, original_quantity, "
                + "cancelled_quantity, unit_price, amount, stock_type, progress_code) "
                + "values (0, ?, 2, 'NO-ARCHIVE-1', 'Siemens', 'PLC', 2, 2, 0, 9.90, 19.80, 1, 'PENDING_PURCHASE')", so);
        // 已全部取消的行不带
        jdbc.update("insert into sales_order_item (tenant_id, so_id, line_no, model, brand, category, quantity, original_quantity, "
                + "cancelled_quantity, unit_price, amount, stock_type, progress_code) "
                + "values (0, ?, 3, 'CANCELLED-1', 'Siemens', 'PLC', 0, 1, 1, 5, 0, 1, 'CANCELLED')", so);

        JsonNode d = ok(call(get(SO + "/" + so + "/reorder"), admin));
        assertEquals(c, d.path("customerId").asLong());
        assertEquals("EUR", d.path("currencyCode").asText());
        assertEquals(2, d.path("items").size(), "已全部取消的行不带");
        JsonNode first = d.path("items").get(0);
        assertEquals(product, first.path("productId").asLong());
        assertEquals("REORDER-M1", first.path("mpnDisplay").asText());
        assertEquals(3, first.path("quantity").asInt());
        money("12.50", first.path("unitPrice"));
        money("60", first.path("costPrice"));
        assertEquals(2, first.path("stockType").asInt());
        JsonNode second = d.path("items").get(1);
        assertTrue(second.path("productId").isNull() || second.path("productId").isMissingNode(), "没有商品档案");
        assertEquals("NO-ARCHIVE-1", second.path("model").asText());
        assertEquals(1, d.path("fees").size());
        assertEquals("Shipping Cost", d.path("fees").get(0).path("feeName").asText());
    }

    @Test
    void reorder_requiresManualCreatePermissionAndVisibility() throws Exception {
        long c = customer("ACROBOT", "India");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("customerId", c);
        m.put("items", List.of(Map.of("productId", manualProduct("REORDER-M2"), "quantity", 1, "unitPrice", "1")));
        long so = ok(call(json(post(SO), write(m)), admin)).path("id").asLong();
        loginWithResources("it_reorder_staff", MENU_SO);
        assertTrue(call(get(SO + "/" + so + "/reorder"), token("it_reorder_staff")).path("code").asInt() != 0,
                "没有手动创建订单权限不能再下一单");
        jdbc.update("update sales_order set deleted_at = now() where id = ?", so);
        // 测试账号只有一个，上面换成了业务员，这里换回管理员
        loginAsAdmin("it_sales_admin");
        admin = token("it_sales_admin");
        assertEquals("订单已删除", fail(call(get(SO + "/" + so + "/reorder"), admin)).path("message").asText());
    }
}

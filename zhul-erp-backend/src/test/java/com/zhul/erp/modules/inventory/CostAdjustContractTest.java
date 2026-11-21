package com.zhul.erp.modules.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.modules.sales.SalesContractSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** 成本调整页接口（openspec inventory-costing tasks 4.4，spec finance/inventory-cost「成本调整页」） */
class CostAdjustContractTest extends SalesContractSupport {

    private static final String CA = "/api/v1/finance/cost-adjustments";

    private long first;
    private long second;
    private long product;

    @BeforeEach
    void seed() {
        product = manualProduct("6ES7 214-1AG40-0XB0");
        jdbc.update("insert into inv_batch (tenant_id, batch_no, product_id, source_type, in_date, unit_price_cny) values (0, 'GR20261201001-1', ?, 1, '2026-12-01', 72)",
                product);
        long batch = jdbc.queryForObject("select max(id) from inv_batch where tenant_id = 0", Long.class);
        first = adjust("CA20261205001", "2026-12", 1, "PY20261205003", "12.00", "18.00");
        second = adjust("CA20261102001", "2026-11", 7, "", "-3.00", "0.00");
        jdbc.update("insert into inv_cost_adjust_item (tenant_id, adjust_id, batch_id, product_id, part, warehouse_id, quantity, old_unit_cost,"
                + " new_unit_cost, diff_goods, amount_goods, amount_cny) values (0, ?, ?, ?, 1, 1, 6, 70, 72, 2, 12.00, 12.00)", first, batch, product);
        jdbc.update("insert into inv_cost_adjust_item (tenant_id, adjust_id, batch_id, product_id, part, doc_type, doc_id, doc_no, quantity,"
                + " old_unit_cost, new_unit_cost, diff_goods, amount_goods, amount_cny) values (0, ?, ?, ?, 2, 'SH', 7, 'SH20261203001', 9,"
                + " 70, 72, 2, 18.00, 18.00)", first, batch, product);
    }

    private long adjust(String no, String month, int reason, String source, String stock, String cogs) {
        jdbc.update("insert into inv_cost_adjust (tenant_id, adjust_no, adjust_date, period_month, reason, source_doc_type, source_doc_no,"
                        + " batch_count, first_model, stock_amount, cogs_amount) values (0, ?, ?, ?, ?, ?, ?, 1, '6ES7 214-1AG40-0XB0', ?, ?)",
                no, month + "-05", month, reason, source.isEmpty() ? "" : "PY", source, new BigDecimal(stock), new BigDecimal(cogs));
        return jdbc.queryForObject("select id from inv_cost_adjust where tenant_id = 0 and adjust_no = ?", Long.class, no);
    }

    private JsonNode page(String body) throws Exception {
        return ok(call(json(post(CA + "/page"), body), admin));
    }

    @Test
    void 列表按月份_原因_关键词筛选_带审计字段() throws Exception {
        JsonNode all = page("{}");
        assertEquals(2, all.path("total").asInt());
        JsonNode row = all.path("records").get(0);
        assertEquals("外币付款", page("{\"month\":\"2026-12\"}").path("records").get(0).path("reasonName").asText());
        assertEquals(1, page("{\"reason\":7}").path("total").asInt());
        assertEquals(1, page("{\"keyword\":\"PY20261205003\"}").path("total").asInt());
        assertEquals(1, page("{\"keyword\":\"214-1AG40\"}").path("total").asInt(), "按型号找");
        assertEquals(1, page("{\"keyword\":\"GR20261201001\"}").path("total").asInt(), "按批次号找");
        for (String f : new String[]{"createTime", "createBy", "updateTime", "updateBy"}) {
            assertEquals(false, row.path(f).isMissingNode(), f);
        }
    }

    @Test
    void 详情按批次展开在库与已出运() throws Exception {
        JsonNode d = ok(call(get(CA + "/" + first), admin));
        assertEquals("CA20261205001", d.path("adjustNo").asText());
        assertEquals(0, new BigDecimal("30.00").compareTo(d.path("totalAmount").decimalValue()));
        JsonNode b = d.path("batches").get(0);
        assertEquals("GR20261201001-1", b.path("batchNo").asText());
        assertEquals(15, b.path("quantity").asInt());
        assertEquals("在库", b.path("parts").get(0).path("partName").asText());
        assertEquals("SH20261203001", b.path("parts").get(1).path("docNo").asText());
        assertEquals(9, b.path("parts").get(1).path("quantity").asInt());

        JsonNode r = ok(call(json(put(CA + "/" + first + "/remark"), "{\"remark\":\"12 月付清\"}"), admin));
        assertEquals("12 月付清", r.path("remark").asText());
        assertEquals("修改备注", r.path("logs").get(r.path("logs").size() - 1).path("action").asText());
    }

    @Test
    void 没有成本调整菜单的人看不到() throws Exception {
        String lin = token("it_lin");
        assertEquals(403, perform(json(post(CA + "/page"), "{}"), lin).getStatus());
        assertEquals(403, perform(get(CA + "/" + first), lin).getStatus());
    }
}

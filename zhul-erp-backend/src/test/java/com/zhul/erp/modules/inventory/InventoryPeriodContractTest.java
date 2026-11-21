package com.zhul.erp.modules.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.inventory.constants.InvConstants;
import com.zhul.erp.modules.inventory.entity.InvBatchDO;
import com.zhul.erp.modules.inventory.service.InventoryPosting;
import com.zhul.erp.modules.inventory.service.InventoryPosting.CostMove;
import com.zhul.erp.modules.inventory.service.InventoryPosting.Doc;
import com.zhul.erp.modules.inventory.service.InventoryPosting.Line;
import com.zhul.erp.modules.inventory.service.InventoryPosting.Loc;
import com.zhul.erp.modules.inventory.service.InventoryWarehouses;
import com.zhul.erp.modules.inventory.service.InventoryWarehouses.Price;
import com.zhul.erp.modules.sales.SalesContractSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** 库存月结（openspec inventory-costing tasks 6.2、6.3，spec finance/period-close） */
class InventoryPeriodContractTest extends SalesContractSupport {

    private static final String P = "/api/v1/finance/periods";
    private static final String LEDGER = "/api/v1/warehouse/ledger";

    @Autowired private InventoryPosting posting;
    @Autowired private InventoryWarehouses warehouses;
    @Autowired private TransactionTemplate tx;

    private final YearMonth m1 = YearMonth.now().minusMonths(2);
    private final YearMonth m2 = YearMonth.now().minusMonths(1);
    private long product;
    private Loc loc;

    /** m1 入库 4 个、单价 710（期末 ¥2,840.00） */
    @BeforeEach
    void stock() {
        product = manualProduct("6ES7 315-2AH14-0AB0");
        TenantContext.runAs(0, () -> tx.executeWithoutResult(s -> {
            InvBatchDO b = warehouses.newBatch("GR-PERIOD-1", product, "", InvConstants.BATCH_PURCHASE, 1L, 1L, null, null,
                    m1.atDay(5), Price.cny(new BigDecimal("710")));
            loc = new Loc(warehouses.own().getId(), product, b.getId(), InvConstants.GOOD);
            posting.post(List.of(Line.of(loc, InvConstants.OP_IN, 4, null,
                    Doc.of(InvConstants.BIZ_PURCHASE_IN, "GR", 1L, "GR-PERIOD").date(m1.atDay(5)))));
        }));
    }

    private JsonNode close(YearMonth m) throws Exception {
        return call(post(P + "/" + m + "/close"), admin);
    }

    private JsonNode reopen(YearMonth m, String reason) throws Exception {
        return call(json(post(P + "/" + m + "/reopen"), write(java.util.Map.of("reason", reason))), admin);
    }

    @Test
    void 按顺序结账_只能结已过完的月份() throws Exception {
        JsonNode list = ok(call(get(P), admin));
        assertEquals(m2.toString(), list.get(0).path("month").asText(), "月份倒序，到上个月为止");
        assertEquals(m1.toString(), list.get(1).path("month").asText());
        assertEquals("请先结 " + m1, list.get(0).path("closeBlock").asText());

        assertEquals("请先结 " + m1, fail(close(m2)).path("message").asText());
        assertEquals(YearMonth.now() + " 还没过完", fail(close(YearMonth.now())).path("message").asText());
        JsonNode closed = ok(close(m1));
        assertEquals("已结账", closed.path("statusName").asText());
        assertEquals(0, new BigDecimal("2840.00").compareTo(closed.path("closingAmount").decimalValue()));
        assertFalse(closed.path("closedByName").asText().isEmpty());
        ok(close(m2));
        assertEquals(m1 + " 已结账", fail(close(m1)).path("message").asText());
    }

    @Test
    void 结账前的提醒_不阻止结账() throws Exception {
        jdbc.update("insert into purchase_receipt (tenant_id, gr_no, status, received_date) values (0, 'GR-UNLINKED', 1, ?)", m1.atDay(8));
        long r = jdbc.queryForObject("select max(id) from purchase_receipt where tenant_id = 0", Long.class);
        jdbc.update("insert into purchase_receipt_item (tenant_id, receipt_id, model, qualified_qty) values (0, ?, 'X-1', 2)", r);
        JsonNode d = ok(call(get(P + "/" + m1), admin));
        assertTrue(d.path("reminders").toString().contains("1 条采购入库行还没关联商品"), d.toString());
        ok(close(m1));
    }

    @Test
    void 结账锁定_结账后的调整记当前月_已结月份台账读快照() throws Exception {
        ok(close(m1));
        // 已结月份不能补登
        JsonNode err = fail(call(post(P + "/" + m1 + "/close"), admin));
        assertTrue(err.path("message").asText().contains("已结账"));
        TenantContext.runAs(0, () -> {
            com.zhul.erp.common.exception.BizException e = org.junit.jupiter.api.Assertions.assertThrows(
                    com.zhul.erp.common.exception.BizException.class, () -> tx.executeWithoutResult(s -> posting.post(List.of(
                            Line.of(loc, InvConstants.OP_OUT, 1, null, Doc.of(InvConstants.BIZ_OTHER_OUT, "OO", 1L, "OO-OLD").date(m1.atDay(20)))))));
            assertEquals("PERIOD_CLOSED", e.getErrorCode());
            // 当月外币付清：在库成本 +12.00（成本调整流水，记当天）
            tx.executeWithoutResult(s -> posting.postCostAdjust(List.of(new CostMove(loc, new BigDecimal("713"), new BigDecimal("12.00"),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, Doc.of(InvConstants.BIZ_COST_ADJUST, "CA", 1L, "CA-TEST")))));
        });
        JsonNode closedLedger = ok(call(json(post(LEDGER + "/page"), "{\"month\":\"" + m1 + "\"}"), admin)).path("records").get(0);
        assertEquals(0, new BigDecimal("2840.00").compareTo(closedLedger.path("closingAmount").decimalValue()));
        assertTrue(ok(call(get(LEDGER + "/closed?month=" + m1), admin)).asBoolean());
        JsonNode now = ok(call(json(post(LEDGER + "/page"), "{\"month\":\"" + YearMonth.now() + "\"}"), admin)).path("records").get(0);
        assertEquals(0, new BigDecimal("2840.00").compareTo(now.path("openingAmount").decimalValue()));
        assertEquals(0, new BigDecimal("12.00").compareTo(now.path("adjustAmount").decimalValue()));
        assertEquals(0, new BigDecimal("2852.00").compareTo(now.path("closingAmount").decimalValue()));
        assertFalse(ok(call(get(LEDGER + "/closed"), admin)).asBoolean());
    }

    @Test
    void 只能反结最近一个月_必填原因_快照作废() throws Exception {
        ok(close(m1));
        ok(close(m2));
        JsonNode list = ok(call(get(P), admin));
        assertTrue(list.get(0).path("reopenable").asBoolean());
        assertFalse(list.get(1).path("reopenable").asBoolean());
        assertEquals("只能反结最近一个已结账月份（" + m2 + "）", fail(reopen(m1, "补登出运单")).path("message").asText());
        fail(reopen(m2, " "));
        long snapshots = jdbc.queryForObject("select count(*) from inv_period_ledger where tenant_id = 0 and period_month = ?"
                + " and deleted_at is null", Long.class, m2.toString());
        assertTrue(snapshots > 0);
        JsonNode r = ok(reopen(m2, "补登出运单"));
        assertEquals("未结账", r.path("statusName").asText());
        assertEquals("补登出运单", r.path("reopenReason").asText());
        assertEquals(0L, jdbc.queryForObject("select count(*) from inv_period_ledger where tenant_id = 0 and period_month = ?"
                + " and deleted_at is null", Long.class, m2.toString()));
        assertEquals(snapshots, jdbc.queryForObject("select count(*) from inv_period_ledger where tenant_id = 0 and period_month = ?",
                Long.class, m2.toString()), "快照保留记录");
        // 反结账后可以在该月补登
        TenantContext.runAs(0, () -> tx.executeWithoutResult(s -> posting.post(List.of(Line.of(loc, InvConstants.OP_OUT, 1, null,
                Doc.of(InvConstants.BIZ_OTHER_OUT, "OO", 2L, "OO-M2").date(m2.atDay(20)))))));
    }

    @Test
    void 没有权限不能看_不能结账() throws Exception {
        String lin = token("it_lin");
        assertEquals(403, perform(get(P), lin).getStatus());
        assertEquals(403, perform(post(P + "/" + m1 + "/close"), lin).getStatus());
        assertEquals(403, perform(json(post(P + "/" + m1 + "/reopen"), "{\"reason\":\"x\"}"), lin).getStatus());
    }

    @Test
    void 月份详情销售成本页签_出运结转() throws Exception {
        long soItem = 990777001L;
        TenantContext.runAs(0, () -> tx.executeWithoutResult(s -> {
            posting.post(List.of(Line.of(loc, InvConstants.OP_IN, 2, InventoryPosting.Target.soItem(soItem),
                    Doc.of(InvConstants.BIZ_PURCHASE_IN, "GR", 2L, "GR-PERIOD-2").date(m1.atDay(6)))));
            posting.post(List.of(Line.of(loc, InvConstants.OP_OUT, 2, InventoryPosting.Target.soItem(soItem),
                    Doc.of(InvConstants.BIZ_SALES_OUT, "SH", 5L, "SH-M1").date(m1.atDay(28)))));
        }));
        JsonNode cogs = ok(call(get(P + "/" + m1 + "/cogs"), admin));
        assertEquals(1, cogs.path("total").asInt());
        JsonNode row = cogs.path("records").get(0);
        assertEquals("SH-M1", row.path("docNo").asText());
        assertEquals(2, row.path("quantity").asInt());
        assertEquals(0, new BigDecimal("1420.00").compareTo(row.path("amountCny").decimalValue()));
        assertEquals(0, new BigDecimal("1420.00").compareTo(ok(call(get(P + "/" + m1), admin)).path("cogsAmount").decimalValue()));
        JsonNode d = ok(call(get(P + "/" + m1), admin));
        assertEquals(0, BigDecimal.ZERO.compareTo(d.path("openingAmount").decimalValue()));
        assertEquals(0, new BigDecimal("4260.00").compareTo(d.path("inAmount").decimalValue()), "入库 4 + 2 个 × 710");
        assertEquals(0, new BigDecimal("1420.00").compareTo(d.path("outAmount").decimalValue()));
        ok(close(m1));
        assertEquals(1, ok(call(get(P + "/" + m1 + "/cogs"), admin)).path("total").asInt(), "已结账读快照");
        byte[] xlsx = perform(post(P + "/" + m1 + "/export"), admin).getContentAsByteArray();
        assertEquals('P', (char) xlsx[0], "xlsx");
    }
}

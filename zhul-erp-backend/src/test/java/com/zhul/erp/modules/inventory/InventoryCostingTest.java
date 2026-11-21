package com.zhul.erp.modules.inventory;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.inventory.constants.InvConstants;
import com.zhul.erp.modules.inventory.entity.InvBatchDO;
import com.zhul.erp.modules.inventory.entity.InvCostAdjustDO;
import com.zhul.erp.modules.inventory.service.InventoryCosting;
import com.zhul.erp.modules.inventory.service.InventoryCosting.Trigger;
import com.zhul.erp.modules.inventory.service.InventoryPosting;
import com.zhul.erp.modules.inventory.service.InventoryPosting.Doc;
import com.zhul.erp.modules.inventory.service.InventoryPosting.Line;
import com.zhul.erp.modules.inventory.service.InventoryPosting.Loc;
import com.zhul.erp.modules.inventory.service.InventoryPosting.Target;
import com.zhul.erp.modules.inventory.service.InventoryWarehouses;
import com.zhul.erp.modules.inventory.service.InventoryWarehouses.Price;
import com.zhul.erp.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 批次成本与成本调整（openspec inventory-costing tasks 3.2、3.3、4.1、6.1）：
 * spec finance/inventory-cost「批次成本」「开票与费用分到批次」「外币付清后调整」「人民币付款不生成」，
 * finance/period-close「已结月份不能冲销」「成本调整不受结账影响」。
 */
class InventoryCostingTest extends IntegrationTestBase {

    private static final int TENANT = 990552;
    private static final long PRODUCT = 990552001L;
    private static final long PO = 990552100L;
    private static final long PO_ITEM_A = 990552101L;
    private static final long PO_ITEM_B = 990552102L;
    private static final long SO_ITEM = 990552201L;
    private static final long GR_ITEM = 990552301L;

    @Autowired private InventoryCosting costing;
    @Autowired private InventoryPosting posting;
    @Autowired private InventoryWarehouses warehouses;
    @Autowired private TransactionTemplate tx;

    @BeforeEach
    void clean() {
        wipe();
        TenantContext.setTenantId(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        wipe();
    }

    private void wipe() {
        for (String t : List.of("inv_move", "inv_balance", "inv_reservation", "inv_batch", "inv_warehouse", "inv_cost_adjust_item",
                "inv_cost_adjust", "inv_period", "purchase_order", "purchase_order_item", "fin_payable", "receiving_discrepancy")) {
            jdbc.update("delete from " + t + " where tenant_id = ?", TENANT);
        }
    }

    // ---------------------------------------------------------------- 准备数据

    /** 采购订单：币种、下单汇率、货款合计、采购费用、开票成本；应付 = 货款 + 费用 */
    private void po(String currency, String rate, String items, String fee, String invoice) {
        BigDecimal total = new BigDecimal(items).add(new BigDecimal(fee));
        jdbc.update("insert into purchase_order (id, tenant_id, po_no, currency_code, exchange_rate, item_amount, fee_amount, invoice_cost,"
                        + " total_amount, status) values (?, ?, 'PO-COST-1', ?, ?, ?, ?, ?, ?, 2)",
                PO, TENANT, currency, new BigDecimal(rate), new BigDecimal(items), new BigDecimal(fee), new BigDecimal(invoice), total);
        jdbc.update("insert into fin_payable (tenant_id, payable_no, source_type, source_id, source_no, currency_code, exchange_rate, amount,"
                        + " paid_amount, paid_cny) values (?, 'AP-COST-1', 1, ?, 'PO-COST-1', ?, ?, ?, 0, 0)",
                TENANT, PO, currency, new BigDecimal(rate), total);
    }

    private void poItem(long id, String unitPrice, int qty) {
        jdbc.update("insert into purchase_order_item (id, tenant_id, po_id, unit_price, quantity) values (?, ?, ?, ?, ?)",
                id, TENANT, PO, new BigDecimal(unitPrice), qty);
    }

    private void paid(String amount, String cny) {
        jdbc.update("update fin_payable set paid_amount = ?, paid_cny = ? where tenant_id = ? and source_id = ?",
                new BigDecimal(amount), new BigDecimal(cny), TENANT, PO);
    }

    /** 采购批次（按新口径定成本）并入库 qty 个，返回批次 */
    private InvBatchDO receive(long poItemId, long receiptItemId, int qty) {
        return tx.execute(s -> {
            InvBatchDO b = warehouses.newBatch("GR-COST-" + poItemId, PRODUCT, "", InvConstants.BATCH_PURCHASE, 1L, receiptItemId, poItemId,
                    SO_ITEM, LocalDate.now(), Price.ZERO);
            // 入库时预占给型号行，出运时按预占出库
            posting.post(List.of(Line.of(stock(b), InvConstants.OP_IN, qty, Target.soItem(SO_ITEM),
                    Doc.of(InvConstants.BIZ_PURCHASE_IN, "GR", 1L, "GR-COST").item(receiptItemId))));
            costing.initialize(b);
            return b;
        });
    }

    private Loc stock(InvBatchDO b) {
        return new Loc(warehouses.own().getId(), PRODUCT, b.getId(), InvConstants.GOOD);
    }

    private void ship(InvBatchDO b, int qty) {
        tx.executeWithoutResult(s -> posting.post(List.of(Line.of(stock(b), InvConstants.OP_OUT, qty, Target.soItem(SO_ITEM),
                Doc.of(InvConstants.BIZ_SALES_OUT, "SH", 7L, "SH-COST")))));
    }

    private InvCostAdjustDO recost(int reason) {
        return tx.execute(s -> costing.recost(List.of(PO), Trigger.of(reason)));
    }

    private BigDecimal[] cost(InvBatchDO b) {
        return jdbc.queryForObject("select cost_goods, cost_invoice, cost_fee, cost_claim, unit_price_cny from inv_batch where id = ?",
                (rs, i) -> new BigDecimal[]{rs.getBigDecimal(1), rs.getBigDecimal(2), rs.getBigDecimal(3), rs.getBigDecimal(4), rs.getBigDecimal(5)},
                b.getId());
    }

    private static void money(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), () -> "期望 " + expected + "，实际 " + actual);
    }

    // ---------------------------------------------------------------- 批次成本

    @Test
    void 开票与费用按行金额比例分到批次_质量索赔按批次数量() {
        po("CNY", "1", "400.00", "40.00", "20.00");
        poItem(PO_ITEM_A, "10.00", 10);
        poItem(PO_ITEM_B, "30.00", 10);
        jdbc.update("insert into receiving_discrepancy (tenant_id, receipt_item_id, status, resolution, discount_amount) values (?, ?, 2, 5, 5.00)",
                TENANT, GR_ITEM);
        InvBatchDO a = receive(PO_ITEM_A, GR_ITEM, 10);
        InvBatchDO b = receive(PO_ITEM_B, GR_ITEM + 1, 10);

        BigDecimal[] ca = cost(a);
        money("10", ca[0]);
        money("0.5", ca[1]);   // 20 × 10/400
        money("1", ca[2]);     // 40 × 10/400
        money("-0.5", ca[3]);  // 折价 5 ÷ 入库 10
        money("11", ca[4]);
        BigDecimal[] cb = cost(b);
        money("30", cb[0]);
        money("1.5", cb[1]);
        money("3", cb[2]);
        money("0", cb[3]);
        money("34.5", cb[4]);
    }

    @Test
    void 流水写四项金额_合计为流水金额() {
        po("CNY", "1", "100.00", "10.00", "5.00");
        poItem(PO_ITEM_A, "10.00", 10);
        InvBatchDO a = receive(PO_ITEM_A, GR_ITEM, 10);
        ship(a, 3);
        Map<String, Object> m = jdbc.queryForMap("select amount_goods, amount_invoice, amount_fee, amount_claim, amount_cny, biz_date"
                + " from inv_move where tenant_id = ? and biz_type = 'SALES_OUT'", TENANT);
        money("-30.00", (BigDecimal) m.get("amount_goods"));
        money("-1.50", (BigDecimal) m.get("amount_invoice"));
        money("-3.00", (BigDecimal) m.get("amount_fee"));
        money("0", (BigDecimal) m.get("amount_claim"));
        money("-34.50", (BigDecimal) m.get("amount_cny"));
        assertEquals(LocalDate.now().toString(), m.get("biz_date").toString());
    }

    // ---------------------------------------------------------------- 成本调整

    @Test
    void 外币付清后调整_在库调库存_已出运调销售成本_重复执行不再生成() {
        po("USD", "7.000000", "150.00", "0.00", "0.00");
        poItem(PO_ITEM_A, "10.00", 15);
        InvBatchDO a = receive(PO_ITEM_A, GR_ITEM, 15);
        money("70", cost(a)[4]);
        ship(a, 9);

        paid("150.00", "1080.00"); // 实际汇率 7.2
        InvCostAdjustDO adj = recost(InvConstants.CA_PAYMENT);

        assertNotNull(adj);
        money("12.00", adj.getStockAmount());
        money("18.00", adj.getCogsAmount());
        money("0", adj.getOtherAmount());
        assertEquals(1, adj.getBatchCount());
        assertEquals(InventoryCostingTest.monthOf(LocalDate.now()), adj.getPeriodMonth());
        money("72", cost(a)[4]);
        // 在库部分有一条「成本调整」流水：数量 0、金额 +12.00，余额不变
        Map<String, Object> mv = jdbc.queryForMap("select quantity, amount_cny, amount_goods, op_type from inv_move where tenant_id = ?"
                + " and biz_type = 'COST_ADJUST'", TENANT);
        assertEquals(0, ((Number) mv.get("quantity")).intValue());
        assertEquals(InvConstants.OP_COST_ADJUST, ((Number) mv.get("op_type")).intValue());
        money("12.00", (BigDecimal) mv.get("amount_cny"));
        money("12.00", (BigDecimal) mv.get("amount_goods"));
        assertEquals(6, jdbc.queryForObject("select on_hand from inv_balance where tenant_id = ? and batch_id = ?", Integer.class,
                TENANT, a.getId()));
        // 已出运部分记到出运单与型号行
        Map<String, Object> shipped = jdbc.queryForMap("select doc_no, so_item_id, quantity, amount_cny from inv_cost_adjust_item"
                + " where adjust_id = ? and part = 2", adj.getId());
        assertEquals("SH-COST", shipped.get("doc_no"));
        assertEquals(SO_ITEM, ((Number) shipped.get("so_item_id")).longValue());
        assertEquals(9, ((Number) shipped.get("quantity")).intValue());

        assertNull(recost(InvConstants.CA_DAILY), "同样的单据再算一次不应再生成调整");
        InventoryConsistency.check(jdbc, TENANT);
    }

    @Test
    void 人民币付款不生成调整() {
        po("CNY", "1", "100.00", "0.00", "0.00");
        poItem(PO_ITEM_A, "10.00", 10);
        receive(PO_ITEM_A, GR_ITEM, 10);
        paid("100.00", "100.00");
        assertNull(recost(InvConstants.CA_PAYMENT));
        assertEquals(0, jdbc.queryForObject("select count(*) from inv_cost_adjust where tenant_id = ?", Integer.class, TENANT));
    }

    @Test
    void 其他出库部分单列() {
        po("CNY", "1", "100.00", "0.00", "0.00");
        poItem(PO_ITEM_A, "10.00", 10);
        InvBatchDO a = receive(PO_ITEM_A, GR_ITEM, 10);
        tx.executeWithoutResult(s -> posting.post(List.of(Line.of(stock(a), InvConstants.OP_OUT, 2, Target.soItem(SO_ITEM),
                Doc.of(InvConstants.BIZ_OTHER_OUT, "OO", 9L, "OO-COST")))));
        jdbc.update("update purchase_order set fee_amount = 10.00, total_amount = 110.00 where id = ?", PO); // 补登费用 +1.00/个
        InvCostAdjustDO adj = recost(InvConstants.CA_PO_FEE);
        money("8.00", adj.getStockAmount());
        money("0", adj.getCogsAmount());
        money("2.00", adj.getOtherAmount());
    }

    // ---------------------------------------------------------------- 结账锁定

    @Test
    void 已结月份不能过账_成本调整不受影响() {
        LocalDate last = LocalDate.now().minusMonths(1).withDayOfMonth(10);
        jdbc.update("insert into inv_period (tenant_id, period_month, status) values (?, ?, 1)", TENANT, monthOf(last));
        po("USD", "7.000000", "100.00", "0.00", "0.00");
        poItem(PO_ITEM_A, "10.00", 10);
        InvBatchDO a = receive(PO_ITEM_A, GR_ITEM, 10);

        BizException e = assertThrows(BizException.class, () -> tx.executeWithoutResult(s -> posting.post(List.of(
                Line.of(stock(a), InvConstants.OP_OUT, 1, Target.soItem(SO_ITEM),
                        Doc.of(InvConstants.BIZ_SALES_OUT, "SH", 8L, "SH-OLD").date(last))))));
        assertEquals("PERIOD_CLOSED", e.getErrorCode());

        paid("100.00", "720.00");
        assertNotNull(recost(InvConstants.CA_PAYMENT), "结账后的调整记当前月，不受锁定");
    }

    static String monthOf(LocalDate d) {
        return d.toString().substring(0, 7);
    }
}

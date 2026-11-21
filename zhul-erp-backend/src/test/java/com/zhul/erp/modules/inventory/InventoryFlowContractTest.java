package com.zhul.erp.modules.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.modules.sales.SalesContractSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * spec warehouse/inventory「现有单据的自动过账」「预占」等：各流程跑完后核对库存余额、预占与流水
 * （openspec inventory-foundation tasks 3.x）。
 */
class InventoryFlowContractTest extends SalesContractSupport {

    @org.springframework.beans.factory.annotation.Autowired
    private com.zhul.erp.modules.product.merge.service.ProductMergeService productMerge;
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.transaction.support.TransactionTemplate tx;
    @org.springframework.beans.factory.annotation.Autowired
    private com.zhul.erp.modules.finance.service.OrderCostService orderCost;
    @org.springframework.beans.factory.annotation.Autowired
    private com.zhul.erp.modules.sales.repository.SalesOrderMapper soMapper;

    private static final String SHIP = "/api/v1/purchase/shipments";
    private static final String GR = "/api/v1/warehouse/receipts";
    private static final String DIFF = "/api/v1/purchase/discrepancies";
    private static final String PRN = "/api/v1/purchase/returns";
    private static final String OB = "/api/v1/logistics/outbounds";
    private static final String SH = "/api/v1/logistics/shipments";
    private static final String OO = "/api/v1/warehouse/other-outbounds";
    private static final long KEEPER = 99000041L;
    private static final int MENU_PO = 100088;
    private static final int MENU_SHIP = 100089;
    private static final int[] BUYER_BUTTONS = {110189, 110190, 110191, 110192, 110232, 110233, 100125, 110250};

    private String lin;
    private String keeper;

    @BeforeEach
    void people() {
        jdbc.update("insert into role_resource (role_code, resource_id, resource_code) values (?, ?, '')", BUYER_ROLE, MENU_PO);
        jdbc.update("insert into role_resource (role_code, resource_id, resource_code) values (?, ?, '')", BUYER_ROLE, MENU_SHIP);
        for (int id : BUYER_BUTTONS) {
            jdbc.update("insert into role_resource (role_code, resource_id, resource_code) values (?, ?, '')", BUYER_ROLE, id);
        }
        jdbc.update("insert into user_basic (id, tenant_id, name, username, role_code, status) values (?, 0, '陈晨', 'it_keeper', 'ROLE_WH', 1)", KEEPER);
        jdbc.update("insert into account (id, tenant_id, user_id, username, admin_flag) values (?, 0, ?, 'it_keeper', 0)", KEEPER, KEEPER);
        lin = token("it_lin");
        keeper = token("it_keeper");
    }

    @AfterEach
    void cleanup() {
        InventoryConsistency.check(jdbc, 0);
        jdbc.update("delete from account where id = ?", KEEPER);
        jdbc.update("delete from user_basic where id = ?", KEEPER);
    }

    // ---------------------------------------------------------------- 准备数据

    private JsonNode orderedPo(long supplierId, L... lines) throws Exception {
        long c = customer("ACROBOT", "India");
        List<Long> items = quotationItemIds(quotation(c, 2, "USD", null, lines));
        for (Long i : items) {
            long inquiryItem = jdbc.queryForObject("select inquiry_item_id from quotation_item where id = ?", Long.class, i);
            jdbc.update("insert into sourcing_quote (tenant_id, inquiry_item_id, quoted_by, supplier_id, channel, shop_name) values (0, ?, ?, ?, 4, '')",
                    inquiryItem, BUYER_LIN, supplierId);
            long quote = jdbc.queryForObject("select max(id) from sourcing_quote where inquiry_item_id = ?", Long.class, inquiryItem);
            jdbc.update("update quotation_item set cost_quote_id = ? where id = ?", quote, i);
        }
        long pi = sentPi(items, null);
        ok(uploadSlip(pi, "1.00", "2026-10-07", admin));
        ok(call(json(post(PI + "/" + pi + "/convert"), "{}"), admin));
        long draft = jdbc.queryForObject("select max(id) from purchase_order where tenant_id = 0 and supplier_id = ? and status = 1 and deleted_at is null",
                Long.class, supplierId);
        return priceAndConfirm(draft, "800", admin);
    }

    private static long itemOf(JsonNode po, String model) {
        for (JsonNode i : po.path("items")) {
            if (model.equals(i.path("model").asText())) {
                return i.path("id").asLong();
            }
        }
        throw new IllegalStateException("没有型号 " + model);
    }

    private static long item(JsonNode po, int index) {
        return po.path("items").get(index).path("id").asLong();
    }

    private long ship(long poId, Map<Long, Integer> qty) throws Exception {
        List<Map<String, Object>> items = new ArrayList<>();
        qty.forEach((k, v) -> items.add(Map.of("poItemId", k, "quantity", v)));
        Map<String, Object> body = new HashMap<>();
        body.put("poId", poId);
        body.put("carrier", "顺丰");
        body.put("trackingNo", "SF1234568812");
        body.put("shipDate", LocalDate.now().toString());
        body.put("items", items);
        return ok(call(json(post(SHIP), write(body)), lin)).path("shipment").path("id").asLong();
    }

    /** 每个发货行 {实收, 合格, 不良}；返回采购入库单 ID */
    private long accept(long shipmentId, int[]... qty) throws Exception {
        JsonNode s = ok(call(get(GR + "/shipments/" + shipmentId), keeper));
        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < qty.length; i++) {
            int[] q = qty[i];
            items.add(Map.of("shipmentItemId", s.path("shipment").path("items").get(i).path("id").asLong(),
                    "receivedQty", q[0], "qualifiedQty", q[1], "defectiveQty", q[2]));
        }
        return ok(call(json(post(GR), write(Map.of("shipmentId", shipmentId, "receivedDate", LocalDate.now().toString(), "items", items))),
                keeper)).path("receipt").path("id").asLong();
    }

    /** 按采购订单行验收：{实收, 合格, 不良}；返回采购入库单 ID */
    private long acceptBy(long shipmentId, Map<Long, int[]> byPoItem) throws Exception {
        JsonNode s = ok(call(get(GR + "/shipments/" + shipmentId), keeper));
        List<Map<String, Object>> items = new ArrayList<>();
        for (JsonNode i : s.path("shipment").path("items")) {
            int[] q = byPoItem.get(i.path("poItemId").asLong());
            items.add(Map.of("shipmentItemId", i.path("id").asLong(), "receivedQty", q[0], "qualifiedQty", q[1], "defectiveQty", q[2]));
        }
        return ok(call(json(post(GR), write(Map.of("shipmentId", shipmentId, "receivedDate", LocalDate.now().toString(), "items", items))),
                keeper)).path("receipt").path("id").asLong();
    }

    // ---------------------------------------------------------------- 核对

    private com.zhul.erp.modules.finance.dto.OrderCostVO.Line orderCostLine(long soItem) {
        long soId = jdbc.queryForObject("select so_id from sales_order_item where id = ?", Long.class, soItem);
        com.zhul.erp.modules.finance.dto.OrderCostVO[] out = new com.zhul.erp.modules.finance.dto.OrderCostVO[1];
        com.zhul.erp.framework.tenant.TenantContext.runAs(0, () -> out[0] = orderCost.of(soMapper.selectById(soId)));
        return out[0].getLines().stream().filter(l -> l.getSoItemId() == soItem).findFirst().orElseThrow();
    }

    /** 型号行采购货款、开票成本、采购费用、质量索赔 */
    private String purchaseParts(long soItem) {
        com.zhul.erp.modules.finance.dto.OrderCostVO.Line l = orderCostLine(soItem);
        return l.getPurchase() + "/" + l.getInvoice() + "/" + l.getPoFee() + "/" + l.getClaim();
    }

    /** 某商品在某类仓库的 {合格结存, 不良结存, 预占} */
    private int[] stock(long productId, int warehouseType) {
        return jdbc.queryForObject("select coalesce(sum(case when b.stock_status = 1 then b.on_hand end), 0),"
                        + " coalesce(sum(case when b.stock_status = 2 then b.on_hand end), 0), coalesce(sum(b.reserved), 0)"
                        + " from inv_balance b join inv_warehouse w on w.id = b.warehouse_id"
                        + " where b.tenant_id = 0 and b.product_id = ? and w.type = ?",
                (rs, i) -> new int[]{rs.getInt(1), rs.getInt(2), rs.getInt(3)}, productId, warehouseType);
    }

    private int reservedFor(long soItemId) {
        return jdbc.queryForObject("select coalesce(sum(quantity), 0) from inv_reservation where tenant_id = 0 and target_type = 1 and target_id = ?",
                Integer.class, soItemId);
    }

    private long productOfPoItem(long poItemId) {
        return jdbc.queryForObject("select product_id from purchase_order_item where id = ?", Long.class, poItemId);
    }

    private long soItemOfPoItem(long poItemId) {
        return jdbc.queryForObject("select so_item_id from purchase_order_item where id = ?", Long.class, poItemId);
    }

    private static void assertStock(int[] expected, int[] actual, String message) {
        assertEquals(expected[0] + "/" + expected[1] + "/" + expected[2], actual[0] + "/" + actual[1] + "/" + actual[2], message);
    }

    // ---------------------------------------------------------------- 采购入库

    /** spec「入库按订单预占」：合格按型号行需要的数量预占，不良入不良；批次号为入库单号-行序号，单价取采购订单行人民币不含税单价 */
    @Test
    void purchaseIn_reservesForOrder_defectiveSeparate() throws Exception {
        long sup = supplier("华控自动化", null);
        JsonNode po = orderedPo(sup, l("A-1", 3, "1000", "200"));
        long poItem = item(po, 0);
        long product = productOfPoItem(poItem);
        long soItem = soItemOfPoItem(poItem);
        long gr = accept(ship(po.path("id").asLong(), Map.of(poItem, 3)), new int[]{3, 2, 1});

        assertStock(new int[]{2, 1, 2}, stock(product, 1), "合格 2 预占给订单，不良 1");
        assertEquals(2, reservedFor(soItem));
        String grNo = jdbc.queryForObject("select gr_no from purchase_receipt where id = ?", String.class, gr);
        Map<String, Object> batch = jdbc.queryForMap("select batch_no, unit_price_cny, so_item_id from inv_batch where tenant_id = 0 and product_id = ?",
                product);
        assertEquals(grNo + "-1", batch.get("batch_no"));
        assertEquals(soItem, ((Number) batch.get("so_item_id")).longValue());
        java.math.BigDecimal net = jdbc.queryForObject("select net_price_cny from purchase_order_item where id = ?", java.math.BigDecimal.class, poItem);
        assertEquals(0, net.compareTo((java.math.BigDecimal) batch.get("unit_price_cny")));
        assertEquals(2, jdbc.queryForObject("select count(*) from inv_move where tenant_id = 0 and doc_type = 'GR' and doc_id = ?"
                + " and biz_type = 'PURCHASE_IN' and op_type = 1", Integer.class, gr), "合格、不良各一笔入库");
    }

    /** 多发：超出型号行需要的合格数量不预占，成为可用库存 */
    @Test
    void purchaseIn_overflowIsAvailable() throws Exception {
        long sup = supplier("华控自动化", null);
        JsonNode po = orderedPo(sup, l("A-1", 2, "1000", "200"));
        long poItem = item(po, 0);
        ok(call(json(post(GR + "/direct"), write(Map.of("poId", po.path("id").asLong(), "receivedDate", LocalDate.now().toString(),
                "items", List.of(Map.of("poItemId", poItem, "receivedQty", 3, "qualifiedQty", 3, "defectiveQty", 0))))), keeper));
        assertStock(new int[]{3, 0, 2}, stock(productOfPoItem(poItem), 1), "需要 2 预占 2，多出的 1 可用");
    }

    /** spec「冲销采购入库」：按原流水反向，余额与预占归零 */
    @Test
    void purchaseIn_reverse() throws Exception {
        long sup = supplier("华控自动化", null);
        JsonNode po = orderedPo(sup, l("A-1", 2, "1000", "200"));
        long poItem = item(po, 0);
        long sid = ship(po.path("id").asLong(), Map.of(poItem, 2));
        long gr = accept(sid, new int[]{2, 1, 1});
        ok(call(json(post(GR + "/" + gr + "/reverse"), "{\"reason\":\"数量录错\"}"), keeper));
        assertStock(new int[]{0, 0, 0}, stock(productOfPoItem(poItem), 1), "冲销后归零");
        assertEquals(2, jdbc.queryForObject("select count(*) from inv_move where tenant_id = 0 and doc_id = ? and biz_type = 'PURCHASE_IN_REVERSE'"
                + " and op_type = 2 and reverse_of is not null", Integer.class, gr));
        // 重新验收
        accept(sid, new int[]{2, 2, 0});
        assertStock(new int[]{2, 0, 2}, stock(productOfPoItem(poItem), 1), "重新验收后再预占");
    }

    // ---------------------------------------------------------------- 到货差异与采购退货

    private long diffOf(long gr, int type) {
        return jdbc.queryForObject("select id from receiving_discrepancy where tenant_id = 0 and receipt_id = ? and type = ?", Long.class, gr, type);
    }

    private JsonNode handle(long diff, Map<String, Object> body) throws Exception {
        return call(json(post(DIFF + "/" + diff + "/handle"), write(body)), lin);
    }

    /** spec「折价接收」：不良转合格并预占；重新打开反向 */
    @Test
    void discount_convertsDefectiveToGood_reopenReverses() throws Exception {
        long sup = supplier("华控自动化", null);
        JsonNode po = orderedPo(sup, l("A-1", 3, "1000", "200"));
        long poItem = item(po, 0);
        long product = productOfPoItem(poItem);
        long gr = accept(ship(po.path("id").asLong(), Map.of(poItem, 3)), new int[]{3, 1, 2});
        long diff = diffOf(gr, 2);
        ok(handle(diff, Map.of("resolution", 5, "discountAmount", "50")));
        assertStock(new int[]{3, 0, 3}, stock(product, 1), "不良 2 转合格并预占");
        assertEquals(2, jdbc.queryForObject("select count(*) from inv_move where tenant_id = 0 and doc_type = 'RD' and doc_id = ?"
                + " and op_type in (5, 6)", Integer.class, diff), "转出、转入各一笔");
        ok(call(post(DIFF + "/" + diff + "/reopen"), lin));
        assertStock(new int[]{1, 2, 1}, stock(product, 1), "重新打开：合格转回不良");
    }

    /** spec「退货预占」：退货换货生成采购退货单时锁住不良，寄出时出库；作废释放 */
    @Test
    void defectReturn_reservedThenShippedOut_voidReleases() throws Exception {
        long sup = supplier("华控自动化", null);
        JsonNode po = orderedPo(sup, l("A-1", 4, "1000", "200"));
        long poItem = item(po, 0);
        long product = productOfPoItem(poItem);
        long gr = accept(ship(po.path("id").asLong(), Map.of(poItem, 4)), new int[]{4, 2, 2});
        long diff = diffOf(gr, 2);
        ok(handle(diff, Map.of("resolution", 3, "note", "换新")));
        assertStock(new int[]{2, 2, 4}, stock(product, 1), "合格 2 订单预占、不良 2 退货预占");
        long pr = jdbc.queryForObject("select purchase_return_id from receiving_discrepancy where id = ?", Long.class, diff);
        long prItem = jdbc.queryForObject("select id from purchase_return_item where purchase_return_id = ?", Long.class, pr);
        assertEquals(2, jdbc.queryForObject("select coalesce(sum(quantity), 0) from inv_reservation where tenant_id = 0 and target_type = 2"
                + " and target_id = ?", Integer.class, prItem));

        // 作废采购退货单：差异重新打开，退货预占释放
        ok(call(json(post(PRN + "/" + pr + "/void"), "{\"reason\":\"供应商同意折价\"}"), lin));
        assertStock(new int[]{2, 2, 2}, stock(product, 1), "作废后不良不再锁住");

        ok(handle(diff, Map.of("resolution", 3, "note", "还是退")));
        long pr2 = jdbc.queryForObject("select purchase_return_id from receiving_discrepancy where id = ?", Long.class, diff);
        ok(call(json(post(PRN + "/" + pr2 + "/ship"), write(Map.of("shipDate", LocalDate.now().toString(), "carrier", "顺丰",
                "trackingNo", "SF99887766", "companyPaid", true))), lin));
        assertStock(new int[]{2, 0, 2}, stock(product, 1), "寄出：不良 2 出库");
        assertEquals(-2, jdbc.queryForObject("select sum(quantity) from inv_move where tenant_id = 0 and doc_type = 'PR' and doc_id = ?"
                + " and biz_type = 'PURCHASE_RETURN_OUT'", Integer.class, pr2));
    }

    // ---------------------------------------------------------------- 交货代与出运

    private long forwarder() {
        long id = supplier("深圳捷运", null);
        jdbc.update("update supplier set supplier_type = 3, volume_divisor = 5000 where id = ?", id);
        return id;
    }

    /** 建出库通知、打包、交快递；返回销售出库单 ID */
    private long handOver(long soItemId, long fwd, int qty) throws Exception {
        long soId = jdbc.queryForObject("select so_id from sales_order_item where id = ?", Long.class, soItemId);
        JsonNode ob = ok(call(json(post(OB + "/notice"), write(Map.of("soId", soId, "forwarderId", fwd,
                "items", List.of(Map.of("soItemId", soItemId, "quantity", qty))))), admin));
        long obId = ob.path("id").asLong();
        long obItem = ob.path("items").get(0).path("id").asLong();
        ok(call(json(put(OB + "/" + obId + "/pack"), write(Map.of("boxes", List.of(Map.of("length", 50, "width", 40, "height", 30,
                "grossWeight", "10", "items", List.of(Map.of("outboundItemId", obItem, "quantity", qty))))))), keeper));
        JsonNode r = handOverCall(obId);
        ok(r);
        return obId;
    }

    private JsonNode handOverCall(long obId) throws Exception {
        return call(json(post(OB + "/hand-over"), write(Map.of("outboundIds", List.of(obId), "carrier", "顺丰", "trackingNo", "SF1500",
                "sentDate", LocalDate.now().toString(), "freight", "30"))), keeper);
    }

    /** spec「交货代调拨」「登记出运销售出库」：预占随货到货代仓；撤销交货代退回自有仓；出运后从货代仓出库 */
    @Test
    void handover_transfersReserved_undo_thenShipOut() throws Exception {
        long sup = supplier("华控自动化", null);
        JsonNode po = orderedPo(sup, l("A-1", 4, "1000", "200"));
        long poItem = item(po, 0);
        long product = productOfPoItem(poItem);
        long soItem = soItemOfPoItem(poItem);
        accept(ship(po.path("id").asLong(), Map.of(poItem, 4)), new int[]{4, 4, 0});
        long fwd = forwarder();

        long obId = handOver(soItem, fwd, 4);
        assertStock(new int[]{0, 0, 0}, stock(product, 1), "自有仓调出");
        assertStock(new int[]{4, 0, 4}, stock(product, 2), "货代仓调入，预占随货");

        ok(call(json(post(OB + "/" + obId + "/undo-hand-over"), "{\"reason\":\"快递退回\"}"), keeper));
        assertStock(new int[]{4, 0, 4}, stock(product, 1), "撤销：退回自有仓，仍预占给订单");
        assertStock(new int[]{0, 0, 0}, stock(product, 2), "货代仓清空");

        ok(handOverCall(obId));
        String before = purchaseParts(soItem);
        long customerId = ok(call(get(SH + "/pending"), admin)).get(0).path("customerId").asLong();
        long shId = ok(call(json(post(SH), write(Map.of("customerId", customerId, "forwarderId", fwd,
                "outboundIds", List.of(obId)))), admin)).path("id").asLong();
        ok(call(json(post(SH + "/" + shId + "/ship"), write(Map.of("carrier", "DHL", "waybillNo", "1234567890",
                "shippedDate", LocalDate.now().toString(), "freight", "100"))), admin));
        assertStock(new int[]{0, 0, 0}, stock(product, 2), "出运：货代仓出库");
        // spec finance/order-cost「已出运的读结转成本」：全部出运后采购货款、开票、采购费用、质量索赔与出运前一致，来源列出运单
        assertEquals(before, purchaseParts(soItem), "全部出运后订单成本与改动前一致");
        assertTrue(orderCostLine(soItem).getSources().get("purchase").stream().anyMatch(x -> "SHIPMENT".equals(x.getType())));
        assertEquals(0, reservedFor(soItem));
        assertEquals(-4, jdbc.queryForObject("select sum(quantity) from inv_move where tenant_id = 0 and biz_type = 'SALES_OUT'"
                + " and op_type = 2 and target_id = ?", Integer.class, soItem));
    }

    /** spec「无货源模式自动补入库」：交货代时库存不够，补系统外入库（单价 0）；库存模式报库存不足 */
    @Test
    void handover_shortage_dropshipAutoIn_stockModeRejects() throws Exception {
        long sup = supplier("华控自动化", null);
        JsonNode po = orderedPo(sup, l("A-1", 3, "1000", "200"));
        long poItem = item(po, 0);
        long product = productOfPoItem(poItem);
        long soItem = soItemOfPoItem(poItem);
        accept(ship(po.path("id").asLong(), Map.of(poItem, 3)), new int[]{3, 3, 0});
        // 模拟上线前已入库、期初没推出来的货（这张入库单当作历史单据，不参与一致性核对）
        for (String t : List.of("inv_move", "inv_balance", "inv_reservation", "inv_batch")) {
            jdbc.update("delete from " + t + " where tenant_id = 0");
        }
        jdbc.update("update purchase_receipt_item set product_id = null where tenant_id = 0");
        long fwd = forwarder();

        jdbc.update("insert into sys_config (tenant_id, config_key, config_name, config_value, config_type, is_builtin, is_encrypted,"
                + " config_group, remark) values (0, 'inventory.mode', '库存模式', 'stock', 'STRING', 0, 0, '库存', '')");
        long soId = jdbc.queryForObject("select so_id from sales_order_item where id = ?", Long.class, soItem);
        JsonNode ob = ok(call(json(post(OB + "/notice"), write(Map.of("soId", soId, "forwarderId", fwd,
                "items", List.of(Map.of("soItemId", soItem, "quantity", 3))))), admin));
        long obId = ob.path("id").asLong();
        ok(call(json(put(OB + "/" + obId + "/pack"), write(Map.of("boxes", List.of(Map.of("length", 50, "width", 40, "height", 30,
                "grossWeight", "10", "items", List.of(Map.of("outboundItemId", ob.path("items").get(0).path("id").asLong(),
                        "quantity", 3))))))), keeper));
        JsonNode err = fail(handOverCall(obId));
        String msg = err.path("message").asText();
        assertTrue(msg.startsWith("库存不足：A-1 × 缺 3"), msg);
        // 前端「不能交货代」弹窗逐行显示（原型 I8）
        assertEquals("STOCK_SHORTAGE", err.path("data").path("errorCode").asText());
        assertEquals("A-1", err.path("data").path("detail").path("items").get(0).path("model").asText());
        assertEquals(3, err.path("data").path("detail").path("items").get(0).path("shortage").asInt());

        jdbc.update("update sys_config set config_value = 'dropship' where tenant_id = 0 and config_key = 'inventory.mode'");
        ok(handOverCall(obId));
        assertStock(new int[]{0, 0, 0}, stock(product, 1), "系统外入库后立即调出");
        assertStock(new int[]{3, 0, 3}, stock(product, 2), "货代仓预占给订单");
        String batchNo = jdbc.queryForObject("select batch_no from inv_batch where tenant_id = 0 and source_type = 3", String.class);
        assertTrue(batchNo.startsWith("SYS-") && batchNo.endsWith("-1"), batchNo);
        assertEquals(0, jdbc.queryForObject("select unit_price_cny from inv_batch where tenant_id = 0 and source_type = 3",
                java.math.BigDecimal.class).signum());
    }

    // ---------------------------------------------------------------- 库存退回

    private JsonNode fromStock(long sup, long batchId, int status, int qty) throws Exception {
        return call(json(post(PRN + "/from-stock"), write(Map.of("supplierId", sup, "lines",
                List.of(Map.of("batchId", batchId, "stockStatus", status, "quantity", qty))))), lin);
    }

    /** spec purchase/purchase-return「库存退回」：只能退没有预占的；退货待寄出锁住，作废释放，寄出出库 */
    @Test
    void stockReturn_reservesFreeStock_voidReleases_shipOut() throws Exception {
        long sup = supplier("华控自动化", null);
        JsonNode po = orderedPo(sup, l("A-1", 4, "1000", "200"));
        long poItem = item(po, 0);
        long product = productOfPoItem(poItem);
        accept(ship(po.path("id").asLong(), Map.of(poItem, 4)), new int[]{4, 3, 1});
        long batch = jdbc.queryForObject("select id from inv_batch where tenant_id = 0 and product_id = ? and source_type = 1", Long.class, product);
        String batchNo = jdbc.queryForObject("select batch_no from inv_batch where id = ?", String.class, batch);

        String msg = fail(fromStock(sup, batch, 1, 1)).path("message").asText();
        assertEquals("库存不足：批次 " + batchNo + " 没有预占的只有 0，要退 1", msg, "合格 3 都预占给订单");

        JsonNode pr = ok(fromStock(sup, batch, 2, 1));
        assertEquals("库存退回", pr.path("sourceTypeName").asText());
        assertEquals(batchNo, pr.path("sourceNo").asText());
        assertEquals("不良", pr.path("items").get(0).path("stockStatusName").asText());
        assertStock(new int[]{3, 1, 4}, stock(product, 1), "不良 1 退货预占");

        ok(call(json(post(PRN + "/" + pr.path("id").asLong() + "/void"), "{\"reason\":\"供应商不收\"}"), lin));
        assertStock(new int[]{3, 1, 3}, stock(product, 1), "作废：释放");

        long pr2 = ok(fromStock(sup, batch, 2, 1)).path("id").asLong();
        ok(call(json(post(PRN + "/" + pr2 + "/ship"), write(Map.of("shipDate", LocalDate.now().toString(), "carrier", "顺丰",
                "trackingNo", "SF99887766", "companyPaid", true))), lin));
        assertStock(new int[]{3, 0, 3}, stock(product, 1), "寄出：不良 1 出库");
    }

    // ---------------------------------------------------------------- 仓库档案与库存模式

    /** spec warehouse/inventory「库存模式」「仓库档案」：改模式写操作日志；自有仓可改名，货代仓不能 */
    @Test
    void warehouseArchive_modeLogged_renameOwnOnly() throws Exception {
        String WH = "/api/v1/warehouse/warehouses";
        long logsBefore = jdbc.queryForObject("select count(*) from sys_log where menu = '仓库档案' and operation = '修改库存模式'", Long.class);
        assertEquals("dropship", ok(call(get(WH + "/mode"), admin)).path("mode").asText(), "默认无货源");
        try {
            assertEquals("库存模式", ok(call(json(put(WH + "/mode"), "{\"mode\":\"stock\"}"), admin)).path("modeName").asText());
            assertEquals(logsBefore + 1, jdbc.queryForObject("select count(*) from sys_log where menu = '仓库档案' and operation = '修改库存模式'",
                    Long.class));
            ok(call(json(put(WH + "/mode"), "{\"mode\":\"stock\"}"), admin));
            assertEquals(logsBefore + 1, jdbc.queryForObject("select count(*) from sys_log where menu = '仓库档案' and operation = '修改库存模式'",
                    Long.class), "没变不记");
            assertEquals("库存模式不正确", fail(call(json(put(WH + "/mode"), "{\"mode\":\"x\"}"), admin)).path("message").asText());
        } finally {
            ok(call(json(put(WH + "/mode"), "{\"mode\":\"dropship\"}"), admin));
        }

        jdbc.update("insert into inv_warehouse (tenant_id, code, name, type, forwarder_id) values (0, 'WH9901', '深圳捷运', 2, ?)", forwarder());
        JsonNode list = ok(call(get(WH), admin));
        JsonNode own = null;
        JsonNode fwd = null;
        for (JsonNode w : list) {
            if (w.path("type").asInt() == 1) {
                own = w;
            } else if ("深圳捷运".equals(w.path("name").asText())) {
                fwd = w;
            }
        }
        assertEquals("自有仓", own.path("typeName").asText());
        assertTrue(own.path("code").asText().startsWith("WH"));
        assertEquals("货代仓", fwd.path("typeName").asText());
        String name = own.path("name").asText();
        try {
            ok(call(json(put(WH + "/" + own.path("id").asLong()), "{\"name\":\"深圳仓\"}"), admin));
            assertEquals("深圳仓", jdbc.queryForObject("select name from inv_warehouse where id = ?", String.class, own.path("id").asLong()));
        } finally {
            jdbc.update("update inv_warehouse set name = ? where id = ?", name, own.path("id").asLong());
        }
        assertEquals("货代仓名称跟着货代，请在货代管理修改",
                fail(call(json(put(WH + "/" + fwd.path("id").asLong()), "{\"name\":\"x\"}"), admin)).path("message").asText());
        assertEquals(403, perform(json(put(WH + "/mode"), "{\"mode\":\"stock\"}"), keeper).getStatus(), "仓库档案只给租户管理员");
    }

    // ---------------------------------------------------------------- 查询、流水、台账

    /** spec warehouse/inventory「查看可用库存」「按单号查流水」「月度台账」 */
    @Test
    void query_stocksBatches_movesByDocNo_monthlyLedger() throws Exception {
        long sup = supplier("华控自动化", null);
        JsonNode po = orderedPo(sup, l("A-1", 2, "1000", "200"));
        long poItem = item(po, 0);
        long product = productOfPoItem(poItem);
        long soItem = soItemOfPoItem(poItem);
        ok(call(json(post(GR + "/direct"), write(Map.of("poId", po.path("id").asLong(), "receivedDate", LocalDate.now().toString(),
                "items", List.of(Map.of("poItemId", poItem, "receivedQty", 5, "qualifiedQty", 5, "defectiveQty", 0))))), keeper));
        String soNo = jdbc.queryForObject("select so.so_no from sales_order so join sales_order_item i on i.so_id = so.id where i.id = ?",
                String.class, soItem);
        java.math.BigDecimal net = jdbc.queryForObject("select net_price_cny from purchase_order_item where id = ?", java.math.BigDecimal.class, poItem);
        // 另一张采购订单同型号发货未收：在途 1
        JsonNode po2 = orderedPo(sup, l("A-1", 1, "1000", "200"));
        ship(po2.path("id").asLong(), Map.of(item(po2, 0), 1));

        JsonNode page = ok(call(json(post("/api/v1/warehouse/stocks/page"), write(Map.of("onlyAvailable", true, "keyword", "A-1"))), keeper));
        assertEquals(1, page.path("total").asInt());
        JsonNode row = page.path("records").get(0);
        assertEquals("公司仓库", row.path("warehouseName").asText());
        assertEquals("5/2/3/1", row.path("good").asInt() + "/" + row.path("orderReserved").asInt() + "/" + row.path("available").asInt()
                + "/" + row.path("inTransit").asInt(), "合格 5、订单预占 2、可用 3、在途 1");
        assertEquals(0, net.multiply(java.math.BigDecimal.valueOf(5)).setScale(2, java.math.RoundingMode.HALF_UP)
                .compareTo(new java.math.BigDecimal(row.path("amountCny").asText())));
        assertEquals(0, ok(call(json(post("/api/v1/warehouse/stocks/page"), write(Map.of("onlyDefective", true))), keeper)).path("total").asInt());

        JsonNode batches = ok(call(get("/api/v1/warehouse/stocks/batches").param("warehouseId", row.path("warehouseId").asText())
                .param("productId", String.valueOf(product)), keeper));
        assertEquals(1, batches.size());
        assertEquals("采购入库", batches.get(0).path("sourceTypeName").asText());
        assertEquals(3, batches.get(0).path("free").asInt());
        assertEquals(soNo + ":2", batches.get(0).path("reservations").get(0).path("docNo").asText() + ":"
                + batches.get(0).path("reservations").get(0).path("quantity").asInt());
        // 退回供应商默认批次的采购订单供应商
        assertEquals(sup, batches.get(0).path("supplierId").asLong());
        assertEquals("华控自动化", batches.get(0).path("supplierName").asText());
        assertEquals(po.path("id").asLong(), batches.get(0).path("poId").asLong());

        // 顶部汇总与列表同样的筛选
        JsonNode sum = ok(call(json(post("/api/v1/warehouse/stocks/summary"), write(Map.of("keyword", "A-1"))), keeper));
        assertEquals("1/1/3/0/0", sum.path("productCount").asInt() + "/" + sum.path("warehouseCount").asInt() + "/"
                + sum.path("available").asInt() + "/" + sum.path("defective").asInt() + "/" + sum.path("returnPending").asInt());
        assertEquals(0, new java.math.BigDecimal(row.path("amountCny").asText()).compareTo(new java.math.BigDecimal(sum.path("amountCny").asText())));

        // 交货代后撤销：按出库单号查到调出、调入与两笔反向
        long fwd = forwarder();
        long obId = handOver(soItem, fwd, 2);
        ok(call(json(post(OB + "/" + obId + "/undo-hand-over"), "{\"reason\":\"快递退回\"}"), keeper));
        String obNo = jdbc.queryForObject("select ob_no from outbound_order where id = ?", String.class, obId);
        JsonNode moves = ok(call(json(post("/api/v1/warehouse/moves/page"), write(Map.of("keyword", obNo))), admin));
        assertEquals(4, moves.path("total").asInt(), moves.toString());
        List<String> ops = new ArrayList<>();
        moves.path("records").forEach(m -> ops.add(m.path("warehouseName").asText() + m.path("opName").asText() + m.path("quantity").asInt()));
        assertEquals(List.of("公司仓库调入2", "深圳捷运调出-2"), ops.subList(0, 2).stream().sorted().toList(), "后发生的反向在前");
        assertEquals(List.of("公司仓库调出-2", "深圳捷运调入2"), ops.subList(2, 4).stream().sorted().toList());
        assertEquals(0, ok(call(json(post("/api/v1/warehouse/moves/page"), write(Map.of("keyword", obNo, "opType", 7))), admin))
                .path("total").asInt(), "交货代不动预占流水");
        long withReserve = ok(call(json(post("/api/v1/warehouse/moves/page"), write(Map.of("productId", product, "includeReservation", true))),
                admin)).path("total").asLong();
        long withoutReserve = ok(call(json(post("/api/v1/warehouse/moves/page"), write(Map.of("productId", product))), admin)).path("total").asLong();
        assertTrue(withReserve >= withoutReserve);

        // 台账：公司仓库 入库 5 + 调入 2、出库（调出）2，期末 5；全部仓库调拨抵消：入 5、出 0
        long own = row.path("warehouseId").asLong();
        JsonNode ownLedger = ok(call(json(post("/api/v1/warehouse/ledger/page"), write(Map.of("warehouseId", own, "keyword", "A-1"))), admin))
                .path("records").get(0);
        assertEquals("0/7/2/5", ownLedger.path("openingQty").asInt() + "/" + ownLedger.path("inQty").asInt() + "/"
                + ownLedger.path("outQty").asInt() + "/" + ownLedger.path("closingQty").asInt());
        JsonNode allLedger = ok(call(json(post("/api/v1/warehouse/ledger/page"), write(Map.of("keyword", "A-1"))), admin)).path("records").get(0);
        assertEquals("0/5/0/5", allLedger.path("openingQty").asInt() + "/" + allLedger.path("inQty").asInt() + "/"
                + allLedger.path("outQty").asInt() + "/" + allLedger.path("closingQty").asInt());
        assertEquals("全部仓库", allLedger.path("warehouseName").asText());
        String next = java.time.YearMonth.now().plusMonths(1).toString();
        JsonNode nextMonth = ok(call(json(post("/api/v1/warehouse/ledger/page"), write(Map.of("month", next, "keyword", "A-1"))), admin))
                .path("records").get(0);
        assertEquals("5/0/0/5", nextMonth.path("openingQty").asInt() + "/" + nextMonth.path("inQty").asInt() + "/"
                + nextMonth.path("outQty").asInt() + "/" + nextMonth.path("closingQty").asInt(), "下月期初 = 本月期末");
        assertEquals(0, nextMonth.path("openingAmount").decimalValue().compareTo(allLedger.path("closingAmount").decimalValue()));
        assertEquals("月份格式应为 2026-11", fail(call(json(post("/api/v1/warehouse/ledger/page"), "{\"month\":\"2026/11\"}"), admin))
                .path("message").asText());
        var export = perform(json(post("/api/v1/warehouse/ledger/export"), "{}"), admin);
        assertEquals(200, export.getStatus());
        assertTrue(export.getContentAsByteArray().length > 100, "xlsx");
    }

    // ---------------------------------------------------------------- 期初

    @org.springframework.beans.factory.annotation.Autowired
    private com.zhul.erp.modules.inventory.service.InventoryOpening opening;

    private void wipeInventory() {
        for (String t : List.of("inv_move", "inv_balance", "inv_reservation", "inv_batch")) {
            jdbc.update("delete from " + t + " where tenant_id = 0");
        }
        jdbc.update("delete from sys_config where tenant_id = 0 and config_key = 'inventory.opening.done'");
    }

    /** spec warehouse/inventory「期初的已交货代」：上线前已交货代未出运的进货代仓并预占；公司仓库没有；只执行一次 */
    @Test
    void opening_handedOverGoesToForwarder_reservedOnce() throws Exception {
        long sup = supplier("华控自动化", null);
        JsonNode po = orderedPo(sup, l("A-1", 6, "1000", "200"), l("A-2", 3, "500", "100"));
        long itemA = itemOf(po, "A-1");
        long itemB = itemOf(po, "A-2");
        long gr = acceptBy(ship(po.path("id").asLong(), Map.of(itemA, 6, itemB, 3)), Map.of(itemA, new int[]{6, 6, 0}, itemB, new int[]{3, 2, 1}));
        long soA = soItemOfPoItem(itemA);
        handOver(soA, forwarder(), 6);
        Map<String, Object> before = jdbc.queryForMap("select (select group_concat(batch_no order by batch_no) from inv_batch where tenant_id = 0) b");
        // 模拟上线前的单据：库存表清空、期初未执行
        wipeInventory();
        try {
            com.zhul.erp.modules.inventory.service.InventoryOpening.Report r = opening.openTenant(0);
            assertEquals(List.of(), r.negatives());
            assertEquals(6 + 3, r.quantity(), "A 货代仓 6，B 合格 2、不良 1");
            assertStock(new int[]{0, 0, 0}, stock(productOfPoItem(itemA), 1), "公司仓库没有 A");
            assertStock(new int[]{6, 0, 6}, stock(productOfPoItem(itemA), 2), "货代仓 A × 6，预占给型号行");
            assertEquals(6, reservedFor(soA));
            assertStock(new int[]{2, 1, 2}, stock(productOfPoItem(itemB), 1), "B 合格 2 预占给订单，不良 1");
            assertEquals(before.get("b"), jdbc.queryForObject("select group_concat(batch_no order by batch_no) from inv_batch where tenant_id = 0",
                    String.class), "批次号与过账钩子一致");
            assertEquals(0, jdbc.queryForObject("select count(*) from inv_move where tenant_id = 0 and biz_type <> 'OPENING'", Integer.class));
            assertEquals(0, opening.openTenant(0).quantity(), "只执行一次");
            // 期初后照常出运：从货代仓出库
            long customerId = ok(call(get(SH + "/pending"), admin)).get(0).path("customerId").asLong();
            long fwd = jdbc.queryForObject("select forwarder_id from outbound_order where tenant_id = 0 order by id desc limit 1", Long.class);
            long obId = jdbc.queryForObject("select id from outbound_order where tenant_id = 0 order by id desc limit 1", Long.class);
            long shId = ok(call(json(post(SH), write(Map.of("customerId", customerId, "forwarderId", fwd, "outboundIds", List.of(obId)))), admin))
                    .path("id").asLong();
            ok(call(json(post(SH + "/" + shId + "/ship"), write(Map.of("carrier", "DHL", "waybillNo", "1234567890",
                    "shippedDate", LocalDate.now().toString(), "freight", "100"))), admin));
            assertStock(new int[]{0, 0, 0}, stock(productOfPoItem(itemA), 2), "出运后货代仓清空，没有补系统外入库");
            assertEquals(0, jdbc.queryForObject("select count(*) from inv_batch where tenant_id = 0 and source_type = 3", Integer.class));
        } finally {
            jdbc.update("delete from sys_config where tenant_id = 0 and config_key = 'inventory.opening.done'");
        }
        assertTrue(gr > 0);
    }

    /**
     * audit BUS-089：期初时没关联商品的采购入库行，之后在「待关联单据」关联上商品，按期初规则补进库存——
     * 已交货代未出运的进货代仓，其余在公司仓库，都预占给订单；同一型号行已进期初的批次先扣交货代；重复执行不再补
     */
    @Test
    void lateLink_postsSkippedReceiptLineOnce() throws Exception {
        long sup = supplier("华控自动化", null);
        JsonNode po = orderedPo(sup, l("A-1", 6, "1000", "200"), l("A-2", 3, "500", "100"));
        long itemA = itemOf(po, "A-1");
        long itemB = itemOf(po, "A-2");
        acceptBy(ship(po.path("id").asLong(), Map.of(itemA, 6, itemB, 3)), Map.of(itemA, new int[]{6, 6, 0}, itemB, new int[]{3, 3, 0}));
        long soA = soItemOfPoItem(itemA);
        long productA = productOfPoItem(itemA);
        handOver(soA, forwarder(), 4);
        long grItemA = jdbc.queryForObject("select id from purchase_receipt_item where tenant_id = 0 and po_item_id = ?", Long.class, itemA);
        // 上线前的单据：A 的入库行当时没关联商品，期初跳过
        wipeInventory();
        jdbc.update("update purchase_receipt_item set product_id = null where id = ?", grItemA);
        try {
            opening.openTenant(0);
            assertStock(new int[]{0, 0, 0}, stock(productA, 1), "期初跳过没关联商品的行");
            assertStock(new int[]{0, 0, 0}, stock(productA, 2), "期初跳过没关联商品的行");
            assertStock(new int[]{3, 0, 3}, stock(productOfPoItem(itemB), 1), "B 照常进期初");

            JsonNode list = ok(call(get("/api/v1/product/candidates/unlinked").param("table", "purchase_receipt_item"), admin));
            String key = null;
            for (JsonNode g : list.path("records")) {
                if ("A-1".equals(g.path("model").asText())) {
                    key = g.path("key").asText();
                }
            }
            assertNotNull(key, "待关联单据里有 A-1");
            ok(call(json(post("/api/v1/product/candidates/unlinked/link"), write(Map.of("key", key, "productId", productA))), admin));

            assertStock(new int[]{2, 0, 2}, stock(productA, 1), "公司仓库 2，预占给型号行");
            assertStock(new int[]{4, 0, 4}, stock(productA, 2), "已交货代未出运的 4 进货代仓");
            assertEquals(6, reservedFor(soA));
            assertEquals("期初 · 补关联商品", jdbc.queryForObject("select distinct biz_label from inv_move where tenant_id = 0"
                    + " and product_id = ?", String.class, productA));
            int moves = jdbc.queryForObject("select count(*) from inv_move where tenant_id = 0", Integer.class);
            opening.catchUpAll();
            assertEquals(moves, jdbc.queryForObject("select count(*) from inv_move where tenant_id = 0", Integer.class), "已补的不重复补");
        } finally {
            jdbc.update("delete from sys_config where tenant_id = 0 and config_key = 'inventory.opening.done'");
        }
    }

    /** audit BUS-089：同一型号行有已进期初的批次时，交货代先扣它，补关联的行只拿剩下的 */
    @Test
    void lateLink_otherBatchOfSameLineConsumedFirst() throws Exception {
        long sup = supplier("华控自动化", null);
        JsonNode po = orderedPo(sup, l("A-1", 6, "1000", "200"));
        long itemA = itemOf(po, "A-1");
        // 分两次到货：第一次 2（没关联商品），第二次 4
        acceptBy(ship(po.path("id").asLong(), Map.of(itemA, 2)), Map.of(itemA, new int[]{2, 2, 0}));
        acceptBy(ship(po.path("id").asLong(), Map.of(itemA, 4)), Map.of(itemA, new int[]{4, 4, 0}));
        long soA = soItemOfPoItem(itemA);
        long productA = productOfPoItem(itemA);
        handOver(soA, forwarder(), 4);
        long first = jdbc.queryForObject("select min(id) from purchase_receipt_item where tenant_id = 0 and po_item_id = ?", Long.class, itemA);
        wipeInventory();
        jdbc.update("update purchase_receipt_item set product_id = null where id = ?", first);
        try {
            opening.openTenant(0);
            assertStock(new int[]{0, 0, 0}, stock(productA, 1), "第二批 4 个全部交货代");
            assertStock(new int[]{4, 0, 4}, stock(productA, 2), "第二批 4 个全部交货代");
            jdbc.update("update purchase_receipt_item set product_id = ? where id = ?", productA, first);
            opening.catchUpAll();
            assertStock(new int[]{2, 0, 2}, stock(productA, 1), "第一批 2 个仍在公司仓库");
            assertStock(new int[]{4, 0, 4}, stock(productA, 2), "货代仓不重复");
            assertEquals(6, reservedFor(soA));
        } finally {
            jdbc.update("delete from sys_config where tenant_id = 0 and config_key = 'inventory.opening.done'");
        }
    }

    // ---------------------------------------------------------------- 其他出库单

    private JsonNode otherOut(int type, long warehouseId, long batchId, int status, int qty, String reason) throws Exception {
        return call(json(post(OO), write(Map.of("outboundType", type, "warehouseId", warehouseId, "reason", reason,
                "lines", List.of(Map.of("batchId", batchId, "stockStatus", status, "quantity", qty))))), keeper);
    }

    private long warehouseOf(int type) {
        return jdbc.queryForObject("select id from inv_warehouse where tenant_id = 0 and type = ? order by id desc limit 1", Long.class, type);
    }

    /** spec warehouse/other-outbound「报废可用库存」「作废报废单」：只能出没有预占的；作废货回原批次 */
    @Test
    void otherOutbound_scrapFree_voidRestores() throws Exception {
        long sup = supplier("华控自动化", null);
        JsonNode po = orderedPo(sup, l("A-1", 2, "1000", "200"));
        long poItem = item(po, 0);
        long product = productOfPoItem(poItem);
        ok(call(json(post(GR + "/direct"), write(Map.of("poId", po.path("id").asLong(), "receivedDate", LocalDate.now().toString(),
                "items", List.of(Map.of("poItemId", poItem, "receivedQty", 5, "qualifiedQty", 5, "defectiveQty", 0))))), keeper));
        long batch = jdbc.queryForObject("select id from inv_batch where tenant_id = 0 and product_id = ?", Long.class, product);
        String batchNo = jdbc.queryForObject("select batch_no from inv_batch where id = ?", String.class, batch);
        String soNo = jdbc.queryForObject("select so.so_no from sales_order so join sales_order_item i on i.so_id = so.id where i.id = ?",
                String.class, soItemOfPoItem(poItem));
        long own = warehouseOf(1);

        assertEquals("货代丢失只能从货代仓出", fail(otherOut(3, own, batch, 1, 1, "丢了")).path("message").asText());
        assertEquals("A-1 在公司仓库（批次 " + batchNo + "，合格）可出 3 个，其余预占给了订单，先对 " + soNo + " 部分取消再出库",
                fail(otherOut(1, own, batch, 1, 4, "外壳破损")).path("message").asText());
        assertEquals("库存不足：A-1 在公司仓库（批次 " + batchNo + "，不良）可出 0 个，要出 1",
                fail(otherOut(1, own, batch, 2, 1, "外壳破损")).path("message").asText());

        JsonNode oo = ok(otherOut(1, own, batch, 1, 2, "外壳破损"));
        assertTrue(oo.path("outboundNo").asText().startsWith("OO"));
        assertEquals("已出库", oo.path("statusName").asText());
        assertEquals("报废", oo.path("outboundTypeName").asText());
        assertEquals("公司仓库", oo.path("warehouseName").asText());
        java.math.BigDecimal net = jdbc.queryForObject("select net_price_cny from purchase_order_item where id = ?", java.math.BigDecimal.class, poItem);
        assertEquals(0, net.multiply(java.math.BigDecimal.valueOf(2)).setScale(2, java.math.RoundingMode.HALF_UP).compareTo(
                new java.math.BigDecimal(oo.path("amountCny").asText())), "金额 = 批次单价 × 2");
        assertEquals(batchNo, oo.path("items").get(0).path("batchNo").asText());
        assertEquals(1, oo.path("moves").size());
        assertEquals(-2, oo.path("moves").get(0).path("quantity").asInt());
        assertEquals("其他出库 · 报废", oo.path("moves").get(0).path("bizLabel").asText());
        assertEquals("新建", oo.path("logs").get(0).path("action").asText());
        assertTrue(oo.path("voidable").asBoolean());
        assertStock(new int[]{3, 0, 2}, stock(product, 1), "可用 3 出 2");

        long id = oo.path("id").asLong();
        JsonNode page = ok(call(json(post(OO + "/page"), write(Map.of("keyword", "A-1"))), keeper));
        assertEquals(1, page.path("total").asInt());
        assertEquals("A-1", page.path("records").get(0).path("firstModel").asText());
        assertEquals(2, page.path("records").get(0).path("totalQuantity").asInt());
        ok(call(json(put(OO + "/" + id + "/remark"), "{\"remark\":\"拍照存档\"}"), keeper));

        assertEquals("请填写作废原因", fail(call(json(post(OO + "/" + id + "/void"), "{}"), keeper)).path("message").asText());
        JsonNode voided = ok(call(json(post(OO + "/" + id + "/void"), "{\"reason\":\"误报废，货还在\"}"), keeper));
        assertEquals("已作废", voided.path("statusName").asText());
        assertEquals(2, voided.path("moves").size(), "原流水 + 反向流水");
        assertStock(new int[]{5, 0, 2}, stock(product, 1), "作废：可用加回 2");
        assertEquals("其他出库单已作废", fail(call(json(post(OO + "/" + id + "/void"), "{\"reason\":\"x\"}"), keeper)).path("message").asText());
        assertEquals("其他出库单已作废", fail(call(json(put(OO + "/" + id + "/remark"), "{\"remark\":\"x\"}"), keeper)).path("message").asText());
        assertEquals(403, perform(json(post(OO + "/page"), "{}"), lin).getStatus(), "采购员没有其他出库菜单");
    }

    /** spec warehouse/other-outbound「货代丢件」：货代仓的货都预占给了订单，提示先部分取消 */
    @Test
    void otherOutbound_forwarderLost_reservedRejected() throws Exception {
        long sup = supplier("华控自动化", null);
        JsonNode po = orderedPo(sup, l("A-1", 2, "1000", "200"));
        long poItem = item(po, 0);
        long soItem = soItemOfPoItem(poItem);
        accept(ship(po.path("id").asLong(), Map.of(poItem, 2)), new int[]{2, 2, 0});
        long fwd = forwarder();
        handOver(soItem, fwd, 2);
        long batch = jdbc.queryForObject("select id from inv_batch where tenant_id = 0 and product_id = ?", Long.class, productOfPoItem(poItem));
        String soNo = jdbc.queryForObject("select so.so_no from sales_order so join sales_order_item i on i.so_id = so.id where i.id = ?",
                String.class, soItem);
        long fwdWh = warehouseOf(2);
        assertEquals("报废只能从公司仓库出", fail(otherOut(1, fwdWh, batch, 1, 1, "坏了")).path("message").asText());
        assertEquals("A-1 在深圳捷运的货都预占给了订单，先对 " + soNo + " 部分取消再出库",
                fail(otherOut(3, fwdWh, batch, 1, 1, "货代弄丢")).path("message").asText());
    }

    // ---------------------------------------------------------------- 订单取消

    /** spec warehouse/inventory「预占」：整单取消后预占全部释放，货成为可用库存（公司仓库与货代仓） */
    @Test
    void orderCancel_releasesAllReservations() throws Exception {
        long sup = supplier("华控自动化", null);
        JsonNode po = orderedPo(sup, l("A-1", 4, "1000", "200"));
        long poItem = item(po, 0);
        long product = productOfPoItem(poItem);
        long soItem = soItemOfPoItem(poItem);
        accept(ship(po.path("id").asLong(), Map.of(poItem, 4)), new int[]{4, 4, 0});
        long fwd = forwarder();
        // 交货代 1 个，3 个留在公司仓库
        handOver(soItem, fwd, 1);
        long soId = jdbc.queryForObject("select so_id from sales_order_item where id = ?", Long.class, soItem);
        String soNo = jdbc.queryForObject("select so_no from sales_order where id = ?", String.class, soId);
        ok(call(json(post(SO + "/" + soId + "/cancel"), "{\"reason\":\"客户取消\"}"), admin));
        assertEquals(0, reservedFor(soItem));
        assertStock(new int[]{3, 0, 0}, stock(product, 1), "公司仓库 3 个成为可用");
        assertStock(new int[]{1, 0, 0}, stock(product, 2), "货代仓 1 个成为可用");
        assertEquals(2, jdbc.queryForObject("select count(*) from inv_move where tenant_id = 0 and op_type = 8 and target_id = ?"
                + " and biz_label = ?", Integer.class, soItem, "订单取消 · " + soNo), "先公司仓库后货代仓各释放一笔");
    }

    // ---------------------------------------------------------------- 商品合并

    /** spec warehouse/inventory 商品合并：被并入商品的余额转到主商品（同批次），预占一起转；之后冲销入库记在主商品上 */
    @Test
    void productMerge_movesStockAndReservations() throws Exception {
        long sup = supplier("华控自动化", null);
        JsonNode po = orderedPo(sup, l("A-1", 3, "1000", "200"), l("A-2", 1, "1000", "200"));
        long itemA = itemOf(po, "A-1");
        long itemB = itemOf(po, "A-2");
        long from = productOfPoItem(itemA);
        long to = productOfPoItem(itemB);
        long soItemA = soItemOfPoItem(itemA);
        long gr = acceptBy(ship(po.path("id").asLong(), Map.of(itemA, 3, itemB, 1)), Map.of(itemA, new int[]{3, 2, 1}, itemB, new int[]{1, 1, 0}));
        // 商品是全局数据，测试结束还原两个商品，不影响其他测试
        Map<String, Object> fromRow = jdbc.queryForMap("select archive_status, brand_key from product where id = ?", from);
        Integer toStatus = jdbc.queryForObject("select archive_status from product where id = ?", Integer.class, to);
        try {
            jdbc.update("update product set archive_status = 2 where id = ?", to);
            assertStock(new int[]{2, 1, 2}, stock(from, 1), "合并前 A-1");
            assertStock(new int[]{1, 0, 1}, stock(to, 1), "合并前 A-2");
            com.zhul.erp.framework.tenant.TenantContext.runAs(0, () -> tx.executeWithoutResult(st -> productMerge.merge(from, to, "同一型号")));
            assertMerged(from, to, soItemA, gr);
        } finally {
            jdbc.update("update product set archive_status = ?, brand_key = ?, merged_into_id = null where id = ?",
                    fromRow.get("archive_status"), fromRow.get("brand_key"), from);
            jdbc.update("update product set archive_status = ? where id = ?", toStatus, to);
            jdbc.update("delete from product_merge where from_product_id = ?", from);
        }
    }

    private void assertMerged(long from, long to, long soItemA, long gr) throws Exception {
        assertStock(new int[]{0, 0, 0}, stock(from, 1), "原商品没有库存了");
        assertStock(new int[]{3, 1, 3}, stock(to, 1), "主商品：合格 1+2、不良 1，预占 1+2");
        assertEquals(2, reservedFor(soItemA), "A-1 型号行的预占跟着转");
        assertEquals(to, jdbc.queryForObject("select product_id from inv_reservation where tenant_id = 0 and target_id = ? and quantity > 0",
                Long.class, soItemA));
        assertEquals(4, jdbc.queryForObject("select count(*) from inv_move where tenant_id = 0 and biz_type = 'MERGE'", Integer.class),
                "合格（预占 2）、不良 1 各转出、转入一笔");

        ok(call(json(post(GR + "/" + gr + "/reverse"), "{\"reason\":\"录错\"}"), keeper));
        assertStock(new int[]{0, 0, 0}, stock(to, 1), "冲销入库：反向记在主商品上");
    }
}

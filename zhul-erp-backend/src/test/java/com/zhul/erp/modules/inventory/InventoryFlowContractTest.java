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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * spec warehouse/inventory「现有单据的自动过账」「预占」等：各流程跑完后核对库存余额、预占与流水
 * （openspec inventory-foundation tasks 3.x）。
 */
class InventoryFlowContractTest extends SalesContractSupport {

    private static final String SHIP = "/api/v1/purchase/shipments";
    private static final String GR = "/api/v1/warehouse/receipts";
    private static final String DIFF = "/api/v1/purchase/discrepancies";
    private static final String PRN = "/api/v1/purchase/returns";
    private static final long KEEPER = 99000041L;
    private static final int MENU_PO = 100088;
    private static final int MENU_SHIP = 100089;
    private static final int[] BUYER_BUTTONS = {110189, 110190, 110191, 110192, 110232, 110233};

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

    // ---------------------------------------------------------------- 核对

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
}

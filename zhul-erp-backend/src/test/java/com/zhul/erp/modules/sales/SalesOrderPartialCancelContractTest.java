package com.zhul.erp.modules.sales;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * spec sales/sales-order「按型号部分取消」「部分取消后的采购需求」、sales/payment-receipt「订单部分取消后的收款」、
 * warehouse/stock-hold「闲置库存」。
 */
class SalesOrderPartialCancelContractTest extends SalesContractSupport {

    private static final String CREDIT = "/api/v1/finance/credits";
    private static final String SHIP = "/api/v1/purchase/shipments";
    private static final String GR = "/api/v1/warehouse/receipts";
    private static final String HOLD = "/api/v1/warehouse/holds";

    // ---------------------------------------------------------------- 准备数据

    /** PI 转成的订单：型号的采购成本价来自对 supplier 的回价（有供应商时会排入草稿）；按 paid 登记到账；返回订单 ID */
    private long order(Long supplierId, Integer discountType, String discountValue, String paid, L... lines) throws Exception {
        long c = customer("ACROBOT", "Brazil");
        List<Long> items = quotationItemIds(quotation(c, 2, "USD", null, lines));
        if (supplierId != null) {
            for (Long i : items) {
                long inquiryItem = jdbc.queryForObject("select inquiry_item_id from quotation_item where id = ?", Long.class, i);
                jdbc.update("insert into sourcing_quote (tenant_id, inquiry_item_id, quoted_by, supplier_id, channel, shop_name) values (0, ?, ?, ?, 4, '')",
                        inquiryItem, ADMIN_USER, supplierId);
                jdbc.update("update quotation_item set cost_quote_id = (select max(id) from sourcing_quote where inquiry_item_id = ?) where id = ?",
                        inquiryItem, i);
            }
        }
        bankAccount("USD");
        JsonNode created = ok(createPi(items));
        long pi = created.path("id").asLong();
        Map<String, Object> body = saveBody(created);
        body.put("bankAccountId", jdbc.queryForObject("select min(id) from tenant_bank_account where tenant_id = 0", Integer.class));
        if (discountType != null) {
            body.put("discountType", discountType);
            body.put("discountValue", discountValue);
        }
        ok(savePi(pi, body));
        ok(send(pi));
        ok(confirmReceipt(pi, paid, null, false, admin));
        ok(call(json(post(PI + "/" + pi + "/convert"), "{}"), admin));
        return jdbc.queryForObject("select max(id) from sales_order where tenant_id = 0 and pi_id = ?", Long.class, pi);
    }

    private JsonNode detail(long so) throws Exception {
        return ok(call(get(SO + "/" + so), admin));
    }

    private static Map<String, Object> body(String reason, Object... itemQty) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < itemQty.length; i += 2) {
            items.add(Map.of("itemId", itemQty[i], "quantity", itemQty[i + 1]));
        }
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("items", items);
        b.put("reason", reason);
        b.put("remark", "供应商停产");
        return b;
    }

    private JsonNode cancel(long so, Map<String, Object> body) throws Exception {
        return call(json(post(SO + "/" + so + "/partial-cancel"), write(body)), admin);
    }

    private JsonNode preview(long so, Map<String, Object> body) throws Exception {
        return call(json(post(SO + "/" + so + "/partial-cancel/preview"), write(body)), admin);
    }

    private long itemId(JsonNode so, int index) {
        return so.path("items").get(index).path("id").asLong();
    }

    private long draftPo(long supplierId) {
        return jdbc.queryForObject("select max(id) from purchase_order where tenant_id = 0 and supplier_id = ? and status = 1 and deleted_at is null",
                Long.class, supplierId);
    }

    private JsonNode receiveAll(long poId, int qualified) throws Exception {
        JsonNode p = po(poId, admin);
        List<Map<String, Object>> items = new ArrayList<>();
        p.path("items").forEach(i -> items.add(Map.of("poItemId", i.path("id").asLong(), "quantity", i.path("quantity").asInt())));
        JsonNode s = ok(call(json(post(SHIP), write(Map.of("poId", poId, "carrier", "顺丰", "trackingNo", "SF" + System.nanoTime(),
                "shipDate", LocalDate.now().toString(), "items", items))), admin));
        long sid = s.path("shipment").path("id").asLong();
        JsonNode form = ok(call(get(GR + "/shipments/" + sid), admin));
        List<Map<String, Object>> lines = new ArrayList<>();
        form.path("shipment").path("items").forEach(i -> lines.add(Map.of("shipmentItemId", i.path("id").asLong(),
                "receivedQty", i.path("quantity").asInt(), "qualifiedQty", Math.min(qualified, i.path("quantity").asInt()), "defectiveQty", 0)));
        return ok(call(json(post(GR), write(Map.of("shipmentId", sid, "receivedDate", LocalDate.now().toString(), "items", lines))), admin));
    }

    // ---------------------------------------------------------------- 场景

    @Test
    void shortage_reducesOrderedPo_overpaidPending_keptPurchaseTurnsExcessIntoHold() throws Exception {
        long sup = supplier("华控自动化", null);
        long so = order(sup, null, null, "1000", l("A-1", 10, "200", "50"), l("B-1", 10, "200", "50"));
        long draft = draftPo(sup);
        JsonNode po = priceAndConfirm(draft, "200", admin);
        JsonNode d = detail(so);
        money("1000", d.path("totalAmount"));
        assertEquals(10, d.path("items").get(0).path("cancellableQty").asInt());
        assertTrue(d.path("partialCancelable").asBoolean());
        long a = itemId(d, 0);

        // 确认前：已下单的 5 个落在采购订单上
        JsonNode pv = ok(preview(so, body("OUT_OF_STOCK", a, 5)));
        money("1000", pv.path("totalBefore"));
        money("750", pv.path("totalAfter"));
        money("250", pv.path("overpaidAfter"));
        JsonNode line = pv.path("items").get(0);
        assertEquals(0, line.path("unplaced").asInt());
        assertEquals(0, line.path("held").asInt());
        assertEquals(5, line.path("ordered").get(0).path("quantity").asInt());
        assertEquals(po.path("poNo").asText(), line.path("ordered").get(0).path("poNo").asText());
        money("1000", detail(so).path("totalAmount"));

        JsonNode after = ok(cancel(so, body("OUT_OF_STOCK", a, 5)));
        money("750", after.path("totalAmount"));
        money("1000", after.path("piTotalAmount"));
        JsonNode ai = after.path("items").get(0);
        assertEquals(5, ai.path("quantity").asInt());
        assertEquals(10, ai.path("originalQuantity").asInt());
        assertEquals(5, ai.path("cancelledQuantity").asInt());
        money("250", ai.path("amount"));
        money("1787.50", ai.path("amountCny"));
        JsonNode rec = after.path("partialCancels").get(0);
        assertEquals("供应商缺货", rec.path("reasonName").asText());
        money("250", rec.path("amount"));
        assertTrue(rec.path("result").asText().contains("来源订单减少 5 个"), rec.path("result").asText());
        assertEquals("已到账", after.path("receiptStatusName").asText());
        assertEquals(5, ai.path("goodsPendingShip").asInt(), "采购单上还挂着 10 个，待发货按订单需要的 5 个");

        // 采购订单行标记、需求数量减少、采购日志
        JsonNode p = po(draft, admin);
        assertEquals(5, p.path("items").get(0).path("soReducedQuantity").asInt());
        assertEquals(10, p.path("items").get(0).path("quantity").asInt(), "采购数量不自动改");
        assertEquals(5, jdbc.queryForObject("select quantity from purchase_requirement where so_item_id = ?", Integer.class, a));
        assertEquals(1, jdbc.queryForObject("select count(*) from purchase_order_log where po_id = ? and action = '来源订单部分取消'", Integer.class, draft));

        // 多收进入待处理多收（按订单合计），不算退款
        JsonNode over = ok(call(get(CREDIT + "/overpaid"), admin));
        assertEquals(1, over.size());
        money("250", over.get(0).path("pending"));
        money("750", over.get(0).path("total"));
        assertEquals(0, jdbc.queryForObject("select count(*) from fin_customer_refund where tenant_id = 0", Integer.class));

        // 采购员保留：到货 10 个，超出订单需要的 5 个转闲置库存，订单型号按 5 个算已入库
        JsonNode gr = receiveAll(draft, 10);
        JsonNode holds = ok(call(json(post(HOLD + "/page"), write(Map.of("sourceType", 2))), admin));
        assertEquals(1, holds.path("total").asInt());
        JsonNode h = holds.path("records").get(0);
        assertEquals(5, h.path("quantity").asInt());
        assertEquals("订单部分取消", h.path("sourceName").asText());
        assertEquals(after.path("soNo").asText(), h.path("soNo").asText());
        money("200", h.path("costPrice"));
        JsonNode got = detail(so);
        assertEquals(5, got.path("items").get(0).path("purchaseReceivedQty").asInt());
        assertEquals(5, got.path("items").get(0).path("goodsInWarehouse").asInt(), "在仓按订单需要的 5 个");
        assertEquals("RECEIVED", got.path("items").get(0).path("progressCode").asText());
        // 按关键字搜订单号能找到
        assertEquals(1, ok(call(json(post(HOLD + "/page"), write(Map.of("keyword", after.path("soNo").asText()))), admin)).path("total").asInt());
        // 冲销入库：这张入库单转入的闲置库存一并撤回
        ok(call(json(post(GR + "/" + gr.path("receipt").path("id").asLong() + "/reverse"), "{\"reason\":\"录错\"}"), admin));
        assertEquals(0, ok(call(json(post(HOLD + "/page"), write(Map.of("sourceType", 2))), admin)).path("total").asInt());
        assertEquals(0, detail(so).path("items").get(0).path("purchaseReceivedQty").asInt());
    }

    @Test
    void cancelFromUnplacedDraftAndWarehouse_inOrder() throws Exception {
        long sup = supplier("华东工控", null);
        long so = order(sup, null, null, "1000", l("A-1", 10, "100", "50"), l("B-1", 10, "100", "50"));
        long draft = draftPo(sup);
        JsonNode d = detail(so);
        long a = itemId(d, 0);
        long b = itemId(d, 1);
        // 草稿行把 A 改成 6 个，剩 4 个没排单
        JsonNode p = po(draft, admin);
        Map<String, Object> pb = poBody(p, "100");
        item(pb, 0).put("quantity", 6);
        ok(savePo(draft, pb, admin));

        JsonNode pv = ok(preview(so, body("CUSTOMER", a, 7)));
        JsonNode line = pv.path("items").get(0);
        assertEquals(4, line.path("unplaced").asInt());
        assertEquals(3, line.path("drafts").get(0).path("quantity").asInt());

        ok(cancel(so, body("CUSTOMER", a, 7)));
        assertEquals(3, jdbc.queryForObject("select quantity from purchase_order_item where po_id = ? and so_item_id = ? and deleted_at is null",
                Integer.class, draft, a));
        assertEquals(3, jdbc.queryForObject("select quantity from purchase_requirement where so_item_id = ?", Integer.class, a));

        // B 全部到货入库后取消 4 个：转闲置库存，成本取采购单价
        priceAndConfirm(draft, "100", admin);
        receiveAll(draft, 100);
        JsonNode pv2 = ok(preview(so, body("OTHER", b, 4)));
        assertEquals(4, pv2.path("items").get(0).path("held").asInt());
        JsonNode after = ok(cancel(so, body("OTHER", b, 4)));
        assertEquals(6, after.path("items").get(1).path("purchaseReceivedQty").asInt());
        JsonNode holds = ok(call(json(post(HOLD + "/page"), write(Map.of("status", 1))), admin));
        assertEquals(1, holds.path("total").asInt());
        assertEquals(4, holds.path("records").get(0).path("quantity").asInt());
        money("100", holds.path("records").get(0).path("costPrice"));
        assertEquals(6, jdbc.queryForObject("select quantity from purchase_requirement where so_item_id = ?", Integer.class, b));
    }

    @Test
    void limitsReasonsAndWholeCancel_andPercentDiscountRecalculated() throws Exception {
        long so = order(null, 1, "10", "900", l("A-1", 10, "100", "50"), l("B-1", 10, "100", "50"));
        JsonNode d = detail(so);
        money("100", d.path("discountAmount"));
        money("900", d.path("totalAmount"));
        long a = itemId(d, 0);
        long b = itemId(d, 1);
        assertEquals("请选择取消原因", fail(cancel(so, body("NOPE", a, 1))).path("message").asText());
        assertEquals("请填写要取消的型号和数量", fail(cancel(so, body("CUSTOMER", a, 0))).path("message").asText());
        Map<String, Object> other = body("OTHER", a, 1);
        other.put("remark", " ");
        assertEquals("原因选「其他」时请填写说明", fail(cancel(so, other)).path("message").asText());
        assertEquals("A-1 最多还能取消 10 个", fail(cancel(so, body("CUSTOMER", a, 11))).path("message").asText());
        assertEquals("所有型号都要取消，请用「取消订单」", fail(cancel(so, body("CUSTOMER", a, 10, b, 10))).path("message").asText());
        assertEquals("同一个型号不能重复填写", fail(cancel(so, body("CUSTOMER", a, 1, a, 2))).path("message").asText());
        assertEquals("型号不属于这张订单", fail(cancel(so, body("CUSTOMER", 999999999L, 1))).path("message").asText());
        // 出库占用的数量不能取消：B 已交货代 3、已出运 1、出库中 2
        jdbc.update("insert into logistics_shipment (tenant_id, status) values (0, 2)");
        long ls = jdbc.queryForObject("select max(id) from logistics_shipment where tenant_id = 0", Long.class);
        jdbc.update("insert into outbound_order (tenant_id, ob_no, so_id, status) values (0, 'ITOB1', ?, 3)", so);
        long handed = jdbc.queryForObject("select max(id) from outbound_order where tenant_id = 0", Long.class);
        jdbc.update("insert into outbound_order (tenant_id, ob_no, so_id, status, logistics_id) values (0, 'ITOB2', ?, 3, ?)", so, ls);
        long shipped = jdbc.queryForObject("select max(id) from outbound_order where tenant_id = 0", Long.class);
        jdbc.update("insert into outbound_order (tenant_id, ob_no, so_id, status) values (0, 'ITOB3', ?, 1)", so);
        long pending = jdbc.queryForObject("select max(id) from outbound_order where tenant_id = 0", Long.class);
        jdbc.update("insert into outbound_order_item (tenant_id, outbound_id, so_item_id, quantity) values (0, ?, ?, 3), (0, ?, ?, 1), (0, ?, ?, 2)",
                handed, b, shipped, b, pending, b);
        assertEquals("B-1 最多还能取消 4 个（已交货代 3 个、已出运 1 个、出库中 2 个）",
                fail(cancel(so, body("CUSTOMER", b, 5))).path("message").asText());
        JsonNode withOut = detail(so).path("items").get(1);
        assertEquals(6, withOut.path("outboundQty").asInt());
        assertEquals(4, withOut.path("cancellableQty").asInt());
        jdbc.update("delete from outbound_order_item where tenant_id = 0");
        jdbc.update("delete from outbound_order where tenant_id = 0");
        assertEquals(3, ok(call(get(SO + "/partial-cancel-reasons"), admin)).size());

        // 百分比折扣按新小计重算：小计 1000 → 500，折扣 100 → 50
        JsonNode after = ok(cancel(so, body("CUSTOMER", a, 10)));
        money("500", after.path("itemAmount"));
        money("50", after.path("discountAmount"));
        money("450", after.path("totalAmount"));
        JsonNode gone = after.path("items").get(0);
        assertEquals(0, gone.path("quantity").asInt());
        assertEquals(0, gone.path("cancellableQty").asInt());
        money("0", gone.path("amount"));
        assertEquals(0, jdbc.queryForObject("select count(*) from purchase_requirement where so_item_id = ? and status = 1", Integer.class, a));
        assertEquals("A-1 已全部取消，不能修改", fail(call(json(post(SO + "/" + so + "/stock-type"),
                write(Map.of("itemIds", List.of(a), "stockType", 2))), admin)).path("message").asText());
        // 已到账 900 > 订单合计 450：多收 450
        money("450", ok(call(get(CREDIT + "/overpaid"), admin)).get(0).path("pending"));

        // 整单取消后 PI 的应收回到 PI 合计 900：多收消失、收款状态按 PI 合计为已到账
        ok(call(json(post(SO + "/" + so + "/cancel"), "{\"reason\":\"客户不要了\"}"), admin));
        assertTrue(ok(call(get(CREDIT + "/overpaid"), admin)).isEmpty());
        assertEquals(4, jdbc.queryForObject("select receipt_status from proforma_invoice where id = (select pi_id from sales_order where id = ?)",
                Integer.class, so));
        assertEquals("订单已取消", fail(cancel(so, body("CUSTOMER", b, 1))).path("message").asText());
    }

    @Test
    void fixedDiscountCapped_completedOrderGoesToAfterSales_manualOrderUsesOrderTotal() throws Exception {
        long so = order(null, 2, "300", "700", l("A-1", 4, "100", "50"), l("B-1", 10, "100", "50"));
        JsonNode d = detail(so);
        money("400", d.path("totalAmount"));
        // 固定金额折扣不变：小计 700 → 450，折扣 300
        JsonNode x = ok(cancel(so, body("CUSTOMER", itemId(d, 1), 5)));
        money("300", x.path("discountAmount"));
        money("150", x.path("totalAmount"));
        // 超过小计时封顶：小计 200 → 折扣 200，合计 0 —— 但还要留一个型号，取消 B 的 5 个后小计 200
        JsonNode y = ok(cancel(so, body("CUSTOMER", itemId(d, 1), 5)));
        money("200", y.path("itemAmount"));
        money("200", y.path("discountAmount"));
        money("0", y.path("totalAmount"));

        // 已完成的订单不能部分取消
        jdbc.update("update sales_order set progress_code = 'COMPLETED' where id = ?", so);
        assertEquals("订单已完成，请走售后", fail(cancel(so, body("CUSTOMER", itemId(d, 0), 1))).path("message").asText());

        // 手动订单：收款挂在订单上，多收按订单合计
        long c = customer("Pacific Controls", "Australia");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("customerId", c);
        m.put("items", List.of(Map.of("productId", manualProduct("M-1"), "quantity", 4, "unitPrice", "100"), Map.of("productId", manualProduct("M-2"), "quantity", 1, "unitPrice", "100")));
        JsonNode manual = ok(call(json(post(SO), write(m)), admin));
        long mid = manual.path("id").asLong();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("amount", "500");
        r.put("receiptDate", LocalDate.now().toString());
        r.put("bankAccountId", jdbc.queryForObject("select min(id) from tenant_bank_account where tenant_id = 0", Integer.class));
        ok(call(json(post(SO + "/" + mid + "/receipts"), write(r)), admin));
        JsonNode mp = ok(preview(mid, body("OUT_OF_STOCK", manual.path("items").get(0).path("id").asLong(), 2)));
        money("500", mp.path("received"));
        money("200", mp.path("overpaidAfter"));
        JsonNode mc = ok(cancel(mid, body("OUT_OF_STOCK", manual.path("items").get(0).path("id").asLong(), 2)));
        money("300", mc.path("totalAmount"));
        assertTrue(mc.path("netProfit").isNull() || mc.path("netProfit").isMissingNode(), "手动订单没有净利润");
        assertEquals("已到账", mc.path("receiptStatusName").asText());
        // 前一张 PI 订单合计降到 0，已到账 700 全部是多收
        JsonNode over = ok(call(get(CREDIT + "/overpaid"), admin));
        assertEquals(2, over.size());
        JsonNode piRow = over.get(0).path("ownerType").asText().equals("PI") ? over.get(0) : over.get(1);
        JsonNode soRow = over.get(0).path("ownerType").asText().equals("SO") ? over.get(0) : over.get(1);
        money("700", piRow.path("pending"));
        money("200", soRow.path("pending"));
        assertFalse(mc.path("partialCancels").isEmpty());
    }

    @Test
    void untrackedOnlyChangesOrder_handledExcessHoldBlocksReversal() throws Exception {
        long sup = supplier("华南工控", null);
        long so = order(sup, null, null, "1000", l("A-1", 10, "100", "50"), l("B-1", 10, "100", "50"));
        long draft = draftPo(sup);
        JsonNode d = detail(so);
        long a = itemId(d, 0);
        long b = itemId(d, 1);
        // B 改成系统外采购：没有采购需求，只改订单
        jdbc.update("update purchase_requirement set deleted_at = NOW() where so_item_id = ?", b);
        JsonNode pv = ok(preview(so, body("CUSTOMER", b, 3)));
        assertFalse(pv.path("items").get(0).path("tracked").asBoolean());
        assertEquals(3, pv.path("items").get(0).path("untracked").asInt());
        JsonNode after = ok(cancel(so, body("CUSTOMER", b, 3)));
        assertEquals("系统外采购，只改订单", after.path("partialCancels").get(0).path("result").asText());

        // A 下单后取消 4 个，保留采购；到货转入的闲置库存处置后，这张入库单不能冲销
        priceAndConfirm(draft, "100", admin);
        ok(cancel(so, body("OUT_OF_STOCK", a, 4)));
        JsonNode gr = receiveAll(draft, 100);
        // B 被取消 3 个后整批到货，超出的 3 个同样转入；A 超出 4 个
        JsonNode rows = ok(call(json(post(HOLD + "/page"), write(Map.of("sourceType", 2))), admin)).path("records");
        assertEquals(2, rows.size());
        JsonNode h = rows.get(0).path("model").asText().equals("A-1") ? rows.get(0) : rows.get(1);
        assertEquals(4, h.path("quantity").asInt());
        ok(call(json(post(HOLD + "/" + h.path("id").asLong() + "/handle"), write(Map.of("status", 4, "note", "留作样品"))), admin));
        assertEquals("这张入库单转入的闲置库存已处置，不能冲销",
                fail(call(json(post(GR + "/" + gr.path("receipt").path("id").asLong() + "/reverse"), "{\"reason\":\"录错\"}"), admin))
                        .path("message").asText());
    }
}

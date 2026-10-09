package com.zhul.erp.modules.warehouse;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.modules.sales.SalesContractSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * spec purchase/supplier-shipment、purchase/receiving-discrepancy、warehouse/*、system/file-attachment
 * 与 sales/sales-order「订单进度」的入库联动。
 */
class ReceivingContractTest extends SalesContractSupport {

    private static final String SHIP = "/api/v1/purchase/shipments";
    private static final String DIFF = "/api/v1/purchase/discrepancies";
    private static final String GR = "/api/v1/warehouse/receipts";
    private static final String HOLD = "/api/v1/warehouse/holds";
    private static final String SHOOT = "/api/v1/warehouse/shoots";
    private static final String FILE = "/api/v1/attachments";
    private static final long KEEPER = 99000041L;
    private static final int MENU_PO = 100088;
    private static final int MENU_SHIP = 100089;
    private static final int[] BUYER_BUTTONS = {110189, 110190, 110191, 110192};
    private static final byte[] MP4 = {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm', 0, 0, 2, 0};

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

    /** 一张订单：每行的采购回价来自林熙对 supplier 的报价；采购单确认下单后返回采购单详情 */
    private JsonNode orderedPo(long supplierId, L... lines) throws Exception {
        return priceAndConfirm(draftPo(supplierId, lines), "800", admin);
    }

    /** 同上，但停在草稿，返回草稿 ID */
    private long draftPo(long supplierId, L... lines) throws Exception {
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
        return jdbc.queryForObject("select max(id) from purchase_order where tenant_id = 0 and supplier_id = ? and status = 1 and deleted_at is null",
                Long.class, supplierId);
    }

    private static long item(JsonNode po, int index) {
        return po.path("items").get(index).path("id").asLong();
    }

    private JsonNode ship(long poId, Map<Long, Integer> qty, String token) throws Exception {
        List<Map<String, Object>> items = new ArrayList<>();
        qty.forEach((k, v) -> items.add(Map.of("poItemId", k, "quantity", v)));
        Map<String, Object> body = new HashMap<>();
        body.put("poId", poId);
        body.put("carrier", "顺丰");
        body.put("trackingNo", "SF1234568812");
        body.put("shipDate", LocalDate.now().toString());
        body.put("items", items);
        return call(json(post(SHIP), write(body)), token);
    }

    /** 每个发货行 {实收, 合格, 不良} */
    private JsonNode accept(long shipmentId, List<int[]> qty, String token) throws Exception {
        JsonNode s = ok(call(get(GR + "/shipments/" + shipmentId), keeper));
        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < qty.size(); i++) {
            int[] q = qty.get(i);
            items.add(Map.of("shipmentItemId", s.path("shipment").path("items").get(i).path("id").asLong(),
                    "receivedQty", q[0], "qualifiedQty", q[1], "defectiveQty", q[2]));
        }
        return call(json(post(GR), write(Map.of("shipmentId", shipmentId, "receivedDate", LocalDate.now().toString(), "items", items))), token);
    }

    private JsonNode order(long soId) throws Exception {
        return ok(call(get(SO + "/" + soId), admin));
    }

    private long soOf(JsonNode po) {
        return po.path("items").get(0).path("soId").asLong();
    }

    private JsonNode diffs(String token, Map<String, Object> query) throws Exception {
        return ok(call(json(post(DIFF + "/page"), write(query)), token));
    }

    private JsonNode handle(long id, Map<String, Object> body) throws Exception {
        return call(json(post(DIFF + "/" + id + "/handle"), write(body)), lin);
    }

    private long upload(String ownerType, String name, byte[] bytes, String token) throws Exception {
        return ok(call(multipart(FILE).file(new MockMultipartFile("file", name, "application/octet-stream", bytes)).param("ownerType", ownerType),
                token)).path("id").asLong();
    }

    // ---------------------------------------------------------------- 供应商发货

    @Test
    void shipment_batches_limits_editVoid_andPoRestrictions() throws Exception {
        long sup = supplier("华控自动化", null);
        long c = customer("Siam", "Thailand");
        List<Long> qi = quotationItemIds(quotation(c, 2, "USD", null, l("A-1", 60, "1000", "200"), l("C-1", 10, "500", "100")));
        for (Long i : qi) {
            long inquiryItem = jdbc.queryForObject("select inquiry_item_id from quotation_item where id = ?", Long.class, i);
            jdbc.update("insert into sourcing_quote (tenant_id, inquiry_item_id, quoted_by, supplier_id, channel, shop_name) values (0, ?, ?, ?, 4, '')",
                    inquiryItem, BUYER_LIN, sup);
            jdbc.update("update quotation_item set cost_quote_id = (select max(id) from sourcing_quote where inquiry_item_id = ?) where id = ?", inquiryItem, i);
        }
        long pi = sentPi(qi, null);
        ok(uploadSlip(pi, "1.00", "2026-10-07", admin));
        ok(call(json(post(PI + "/" + pi + "/convert"), "{}"), admin));
        long draft = jdbc.queryForObject("select id from purchase_order where tenant_id = 0 and status = 1 and deleted_at is null", Long.class);
        JsonNode d = po(draft, lin);
        assertEquals("确认下单后才能登记发货", fail(ship(draft, Map.of(item(d, 0), 1), lin)).path("message").asText());

        JsonNode po = priceAndConfirm(draft, "800", admin);
        long a = item(po, 0);
        long cc = item(po, 1);
        JsonNode s1 = ok(ship(draft, Map.of(a, 40), lin));
        assertEquals("SD" + TODAY + "001", s1.path("shipment").path("sdNo").asText(), "内部单据不带前缀");
        assertEquals("采购员登记", s1.path("shipment").path("sourceName").asText());
        assertEquals("在途", s1.path("shipment").path("statusName").asText());

        JsonNode form = ok(call(get(SHIP + "/form").param("poId", String.valueOf(draft)), lin));
        assertEquals(2, form.path("lines").size(), "默认列出还没发完的型号");
        assertEquals(20, form.path("lines").get(0).path("quantity").asInt(), "默认为未发数量");
        assertEquals(10, form.path("lines").get(1).path("quantity").asInt());
        assertEquals("A-1 最多还能发 20 个", fail(ship(draft, Map.of(a, 30), lin)).path("message").asText());
        JsonNode future = call(json(post(SHIP), write(Map.of("poId", draft, "shipDate", LocalDate.now().plusDays(1).toString(),
                "items", List.of(Map.of("poItemId", cc, "quantity", 1))))), lin);
        assertEquals("发货日期不能晚于今天", fail(future).path("message").asText());

        // 采购单：数量不能改到小于已发，有发货不能取消
        JsonNode p = po(draft, lin);
        assertEquals(40, p.path("items").get(0).path("shippedQty").asInt());
        assertEquals(20, p.path("items").get(0).path("unshippedQty").asInt());
        assertTrue(p.path("hasShipments").asBoolean());
        assertEquals(1, p.path("shipments").size());
        Map<String, Object> body = poBody(p, null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) body.get("items");
        lines.get(0).put("quantity", 30);
        assertEquals("A-1 已发 40 个，数量不能小于 40", fail(savePo(draft, body, admin)).path("message").asText());
        assertEquals("已有发货，请在到货差异里处理少发或退货",
                fail(call(json(post(PO + "/" + draft + "/cancel"), "{\"reason\":\"缺货\"}"), admin)).path("message").asText());

        // 修改：本单原数量计入可发上限；作废后数量回到未发
        long sid = s1.path("shipment").path("id").asLong();
        JsonNode edited = ok(call(json(put(SHIP + "/" + sid), write(Map.of("carrier", "中通", "trackingNo", "ZT001", "shipDate", LocalDate.now().toString(),
                "items", List.of(Map.of("poItemId", a, "quantity", 60))))), lin));
        assertEquals(60, edited.path("shipment").path("totalQuantity").asInt());
        assertEquals("ZT001", edited.path("shipment").path("trackingNo").asText());
        assertEquals("请填写原因", fail(call(json(post(SHIP + "/" + sid + "/void"), "{\"reason\":\"\"}"), lin)).path("message").asText());
        JsonNode voided = ok(call(json(post(SHIP + "/" + sid + "/void"), "{\"reason\":\"供应商发错单号\"}"), lin));
        assertEquals("已作废", voided.path("shipment").path("statusName").asText());
        assertEquals(60, po(draft, lin).path("items").get(0).path("unshippedQty").asInt());
        assertFalse(po(draft, lin).path("hasShipments").asBoolean());
        List<String> actions = new ArrayList<>();
        po(draft, lin).path("logs").forEach(l -> actions.add(l.path("action").asText()));
        assertTrue(actions.containsAll(List.of("登记发货", "修改发货", "作废发货")), actions.toString());

        // 列表：按快递单号末四位找到；状态筛选
        ok(ship(draft, Map.of(a, 10), lin));
        JsonNode page = ok(call(json(post(SHIP + "/page"), write(Map.of("keyword", "8812"))), lin));
        assertEquals(1, page.path("total").asInt());
        assertEquals(1, ok(call(json(post(SHIP + "/page"), write(Map.of("status", 3))), lin)).path("total").asInt());
        JsonNode pending = ok(call(json(post(GR + "/pending"), write(Map.of("keyword", "8812"))), keeper));
        assertEquals(1, pending.path("total").asInt(), "仓库在待收货里按快递单号找到货");
        assertEquals("林熙", pending.path("records").get(0).path("purchaserName").asText());
    }

    @Test
    void shipment_dataScope_andWarehouseCannotOpenPurchasePages() throws Exception {
        long sup = supplier("华控自动化", null);
        JsonNode po = orderedPo(sup, l("A-1", 2, "1000", "200"));
        long sid = ok(ship(po.path("id").asLong(), Map.of(item(po, 0), 2), lin)).path("shipment").path("id").asLong();
        jdbc.update("update purchase_order set purchaser_id = ? where id = ?", BUYER_JIANG, po.path("id").asLong());
        assertEquals(0, ok(call(json(post(SHIP + "/page"), "{}"), lin)).path("total").asInt(), "负责人为采购单的采购员");
        assertEquals("发货单不存在", fail(call(get(SHIP + "/" + sid), lin)).path("message").asText());
        assertEquals(403, perform(json(post(SHIP + "/page"), "{}"), keeper).getStatus(), "仓库不能打开供应商发货");
        assertEquals(1, ok(call(json(post(GR + "/pending"), "{}"), keeper)).path("total").asInt(), "仓库看全部在途发货单");
        assertEquals(403, perform(json(post(GR + "/pending"), "{}"), lin).getStatus(), "采购员不能打开入库验收");
    }

    // ---------------------------------------------------------------- 验收入库

    @Test
    void accept_allQualified_progressReceived_shootTask_onceOnly() throws Exception {
        long sup = supplier("华控自动化", null);
        JsonNode po = orderedPo(sup, l("A-1", 2, "1000", "200"));
        long poId = po.path("id").asLong();
        long soId = soOf(po);
        assertEquals("已下单", order(soId).path("items").get(0).path("progressName").asText());
        long sid = ok(ship(poId, Map.of(item(po, 0), 2), lin)).path("shipment").path("id").asLong();

        assertEquals(403, perform(get(GR + "/orders"), lin).getStatus(), "采购员没有验收入库权限");
        assertEquals("A-1：合格数量不能多于实收", fail(accept(sid, List.of(new int[] {2, 3, 0}), keeper)).path("message").asText());
        JsonNode gr = ok(accept(sid, List.of(new int[] {2, 2, 0}), keeper));
        assertEquals("GR" + TODAY + "001", gr.path("receipt").path("grNo").asText());
        assertEquals(0, gr.path("discrepancies").size(), "全部合格没有差异");
        assertTrue(gr.path("reversible").asBoolean());
        assertEquals("这张发货单已经验收入库了", fail(accept(sid, List.of(new int[] {2, 2, 0}), keeper)).path("message").asText());
        assertEquals("已入库的发货单不能作废，有问题请在到货差异里处理",
                fail(call(json(post(SHIP + "/" + sid + "/void"), "{\"reason\":\"x\"}"), lin)).path("message").asText());

        JsonNode o = order(soId);
        assertEquals("已入库", o.path("items").get(0).path("progressName").asText(), "合格数到齐自动变为已入库");
        assertEquals(2, o.path("items").get(0).path("purchaseReceivedQty").asInt());
        JsonNode p = po(poId, lin);
        assertEquals(2, p.path("items").get(0).path("receivedQty").asInt());
        assertEquals("已入库", p.path("shipments").get(0).path("statusName").asText());
        assertEquals(1, p.path("receipts").size());

        JsonNode shoots = ok(call(json(post(SHOOT + "/page"), write(Map.of("status", 1))), keeper));
        assertEquals(1, shoots.path("total").asInt(), "合格入库的型号生成拍摄任务");
        assertEquals("A-1", shoots.path("records").get(0).path("model").asText());
        assertEquals(o.path("soNo").asText(), shoots.path("records").get(0).path("soNo").asText());

        // 手动推进：「已入库」及之后到「已出运」都由系统推进
        long itemId = o.path("items").get(0).path("id").asLong();
        assertEquals("「待采购」「已下单」「已入库」「已交货代」「已出运」由采购、入库、出库与出运自动推进，不能手动选择",
                fail(call(json(post(SO + "/" + soId + "/progress"), write(Map.of("itemIds", List.of(itemId), "progressCode", "RECEIVED"))), admin))
                        .path("message").asText());
        assertEquals("已入库", order(soId).path("items").get(0).path("progressName").asText());

        // 已入库的列表：四个审计列与合格数
        JsonNode list = ok(call(json(post(GR + "/page"), "{}"), keeper));
        assertEquals(1, list.path("total").asInt());
        assertEquals(2, list.path("records").get(0).path("qualifiedQty").asInt());
        assertEquals("陈晨", list.path("records").get(0).path("receivedByName").asText());
        assertTrue(list.path("records").get(0).has("updateBy"));
    }

    @Test
    void partialReceipt_keepsOrdered_manualAdvanceBlockedUntilReceived() throws Exception {
        long sup = supplier("华控自动化", null);
        JsonNode po = orderedPo(sup, l("A-1", 5, "1000", "200"));
        long soId = soOf(po);
        long sid = ok(ship(po.path("id").asLong(), Map.of(item(po, 0), 3), lin)).path("shipment").path("id").asLong();
        ok(accept(sid, List.of(new int[] {3, 3, 0}), keeper));
        JsonNode o = order(soId);
        assertEquals("已下单", o.path("items").get(0).path("progressName").asText(), "部分入库进度不变");
        assertEquals(3, o.path("items").get(0).path("purchaseReceivedQty").asInt());
        long itemId = o.path("items").get(0).path("id").asLong();
        assertEquals("「待采购」「已下单」「已入库」「已交货代」「已出运」由采购、入库、出库与出运自动推进，不能手动选择",
                fail(call(json(post(SO + "/" + soId + "/progress"), write(Map.of("itemIds", List.of(itemId), "progressCode", "TO_FORWARDER"))), admin))
                        .path("message").asText(), "交货代由出库推进，不能手动跳过");
    }

    @Test
    void directReceive_createsWarehouseShipment_overflowIsOver() throws Exception {
        long sup = supplier("华控自动化", null);
        JsonNode po = orderedPo(sup, l("A-1", 2, "1000", "200"));
        long poId = po.path("id").asLong();
        JsonNode found = ok(call(get(GR + "/orders").param("keyword", "A-1"), keeper));
        assertEquals(1, found.size());
        assertEquals(2, found.get(0).path("lines").get(0).path("unshippedQty").asInt());
        JsonNode gr = ok(call(json(post(GR + "/direct"), write(Map.of("poId", poId, "receivedDate", LocalDate.now().toString(),
                "items", List.of(Map.of("poItemId", item(po, 0), "receivedQty", 3, "qualifiedQty", 3, "defectiveQty", 0))))), keeper));
        assertEquals(1, gr.path("discrepancies").size());
        assertEquals("多发", gr.path("discrepancies").get(0).path("typeName").asText());
        assertEquals(1, gr.path("discrepancies").get(0).path("quantity").asInt(), "超出未发数量的部分计为多发");
        JsonNode p = po(poId, lin);
        assertEquals("仓库补登", p.path("shipments").get(0).path("sourceName").asText());
        assertEquals(2, p.path("shipments").get(0).path("totalQuantity").asInt(), "发货数量不超过未发数量");
        assertEquals(2, p.path("items").get(0).path("receivedQty").asInt(), "多发不计入合格数");
        assertEquals("已入库", order(soOf(po)).path("items").get(0).path("progressName").asText());
    }

    // ---------------------------------------------------------------- 到货差异

    @Test
    void discrepancy_shortAndDefective_noResend_discount_reopen() throws Exception {
        long sup = supplier("华控自动化", null);
        JsonNode po = orderedPo(sup, l("A-1", 60, "1000", "200"));
        long poId = po.path("id").asLong();
        long soId = soOf(po);
        long sid = ok(ship(poId, Map.of(item(po, 0), 60), lin)).path("shipment").path("id").asLong();
        JsonNode gr = ok(accept(sid, List.of(new int[] {58, 56, 2}), keeper));
        assertEquals(2, gr.path("discrepancies").size(), "一行同时少发和不良");
        assertEquals(2, ok(call(get(DIFF + "/counts"), lin)).path("pending").asInt());
        JsonNode list = diffs(lin, Map.of("status", 1));
        assertEquals(2, list.path("total").asInt(), "采购员看自己的待办");
        assertEquals("林熙", list.path("records").get(0).path("purchaserName").asText());
        long shortId = gr.path("discrepancies").get(0).path("id").asLong();
        long badId = gr.path("discrepancies").get(1).path("id").asLong();
        assertEquals("少发", gr.path("discrepancies").get(0).path("typeName").asText());

        assertEquals("少发不能选择「折价接收」", fail(handle(shortId, Map.of("resolution", 5))).path("message").asText());
        // 少发不补：订购数量 60 → 58，2 个回到需求
        JsonNode h = ok(handle(shortId, Map.of("resolution", 2, "note", "供应商缺货")));
        assertEquals("已处理", h.path("statusName").asText());
        assertEquals("不补了", h.path("resolutionName").asText());
        JsonNode p = po(poId, lin);
        assertEquals(58, p.path("items").get(0).path("quantity").asInt());
        money("46400.00", p.path("totalAmount"));
        long req = p.path("items").get(0).path("requirementId").asLong();
        JsonNode reqs = ok(call(json(post(REQ + "/page"), write(Map.of("view", "all"))), admin));
        JsonNode r = null;
        for (JsonNode x : reqs.path("records")) {
            if (x.path("id").asLong() == req) {
                r = x;
            }
        }
        assertEquals(2, r.path("availableQty").asInt(), "需求可下单数量增加 2");
        assertEquals("待采购", order(soId).path("items").get(0).path("progressName").asText(), "订单型号回到待采购");
        assertEquals("差异已处理，需要更正请先重新打开", fail(handle(shortId, Map.of("resolution", 1))).path("message").asText());

        // 不良折价接收：合格数 56 + 2；没下齐时仍为待采购
        assertEquals("请填写折价金额", fail(handle(badId, Map.of("resolution", 5))).path("message").asText());
        JsonNode bad = ok(handle(badId, Map.of("resolution", 5, "discountAmount", "100")));
        money("100.00", bad.path("discountAmount"));
        assertEquals(58, po(poId, lin).path("items").get(0).path("receivedQty").asInt());

        // 入库单差异已处理，不能冲销
        long grId = gr.path("receipt").path("id").asLong();
        assertFalse(ok(call(get(GR + "/" + grId), keeper)).path("reversible").asBoolean());
        assertEquals("到货差异已经处理，不能冲销；请先让采购员重新打开差异",
                fail(call(json(post(GR + "/" + grId + "/reverse"), "{\"reason\":\"录错\"}"), keeper)).path("message").asText());

        // 重新打开「不补了」：数量回到 60；需求的 2 个还没被别的采购单用掉
        JsonNode reopened = ok(call(post(DIFF + "/" + shortId + "/reopen"), lin));
        assertEquals("待处理", reopened.path("statusName").asText());
        assertEquals(60, po(poId, lin).path("items").get(0).path("quantity").asInt());
        assertEquals("已下单", order(soId).path("items").get(0).path("progressName").asText(), "合格 56 + 折价 2 = 58，少发的 2 个还没到");
        // 等补发：少发的 2 个计为未发
        ok(handle(shortId, Map.of("resolution", 1)));
        assertEquals(2, po(poId, lin).path("items").get(0).path("unshippedQty").asInt());
    }

    @Test
    void discrepancy_returnExchange_countsAsUnshipped_reopenBlockedAfterResend() throws Exception {
        long sup = supplier("华控自动化", null);
        JsonNode po = orderedPo(sup, l("A-1", 10, "1000", "200"));
        long poId = po.path("id").asLong();
        long sid = ok(ship(poId, Map.of(item(po, 0), 10), lin)).path("shipment").path("id").asLong();
        JsonNode gr = ok(accept(sid, List.of(new int[] {10, 8, 2}), keeper));
        long badId = gr.path("discrepancies").get(0).path("id").asLong();
        JsonNode h = ok(handle(badId, Map.of("resolution", 3, "returnCarrier", "顺丰", "returnTrackingNo", "SF9", "returnFreight", "12.5")));
        money("12.50", h.path("returnFreight"));
        assertEquals(2, po(poId, lin).path("items").get(0).path("unshippedQty").asInt(), "退货换货的不良数量计为未发");
        ok(ship(poId, Map.of(item(po, 0), 2), lin));
        assertEquals("补发已经登记，不能重新打开", fail(call(post(DIFF + "/" + badId + "/reopen"), lin)).path("message").asText());
    }

    // ---------------------------------------------------------------- 暂存货

    @Test
    void over_hold_freeOfCharge_handleOnce_reopenBlocked() throws Exception {
        long sup = supplier("华控自动化", null);
        JsonNode po = orderedPo(sup, l("A-1", 2, "1000", "200"));
        long sid = ok(ship(po.path("id").asLong(), Map.of(item(po, 0), 2), lin)).path("shipment").path("id").asLong();
        JsonNode gr = ok(accept(sid, List.of(new int[] {5, 5, 0}), keeper));
        long overId = gr.path("discrepancies").get(0).path("id").asLong();
        assertEquals("多发不能选择「等补发」", fail(handle(overId, Map.of("resolution", 1))).path("message").asText());
        ok(handle(overId, Map.of("resolution", 7, "freeOfCharge", true, "locationNote", "2 号架")));

        JsonNode holds = ok(call(json(post(HOLD + "/page"), write(Map.of("status", 1))), keeper));
        assertEquals(1, holds.path("total").asInt());
        JsonNode h = holds.path("records").get(0);
        assertEquals(3, h.path("quantity").asInt());
        money("0.00", h.path("costPrice"));
        assertEquals("2 号架", h.path("locationNote").asText());
        assertEquals(po.path("poNo").asText(), h.path("poNo").asText());

        long holdId = h.path("id").asLong();
        JsonNode done = ok(call(json(post(HOLD + "/" + holdId + "/handle"), write(Map.of("status", 4, "note", "给业务员做展示"))), keeper));
        assertEquals("已转样品", done.path("statusName").asText());
        assertEquals(0, ok(call(json(post(HOLD + "/page"), write(Map.of("status", 1))), keeper)).path("total").asInt());
        assertEquals("暂存货已处置，不能再处置",
                fail(call(json(post(HOLD + "/" + holdId + "/handle"), write(Map.of("status", 3, "note", "x"))), keeper)).path("message").asText());
        assertEquals("暂存货已转样品，不能重新打开", fail(call(post(DIFF + "/" + overId + "/reopen"), lin)).path("message").asText());
    }

    @Test
    void over_hold_costFromNetPrice() throws Exception {
        long sup = supplier("华控自动化", null);
        JsonNode po = orderedPo(sup, l("A-1", 2, "1000", "200"));
        long sid = ok(ship(po.path("id").asLong(), Map.of(item(po, 0), 2), lin)).path("shipment").path("id").asLong();
        long overId = ok(accept(sid, List.of(new int[] {3, 3, 0}), keeper)).path("discrepancies").get(0).path("id").asLong();
        ok(handle(overId, Map.of("resolution", 7)));
        JsonNode h = ok(call(json(post(HOLD + "/page"), "{}"), keeper)).path("records").get(0);
        money("800.00", h.path("costPrice"));
    }

    // ---------------------------------------------------------------- 冲销

    @Test
    void reverse_backToInTransit_voidsDiffAndShoot_progressBack() throws Exception {
        long sup = supplier("华控自动化", null);
        JsonNode po = orderedPo(sup, l("A-1", 2, "1000", "200"));
        long sid = ok(ship(po.path("id").asLong(), Map.of(item(po, 0), 2), lin)).path("shipment").path("id").asLong();
        long grId = ok(accept(sid, List.of(new int[] {2, 1, 1}), keeper)).path("receipt").path("id").asLong();
        JsonNode r = ok(call(json(post(GR + "/" + grId + "/reverse"), "{\"reason\":\"数量录错\"}"), keeper));
        assertEquals("已冲销", r.path("receipt").path("statusName").asText());
        assertEquals("在途", po(po.path("id").asLong(), lin).path("shipments").get(0).path("statusName").asText());
        assertEquals(0, diffs(lin, Map.of()).path("total").asInt(), "差异作废");
        assertEquals(0, ok(call(json(post(SHOOT + "/page"), "{}"), keeper)).path("total").asInt(), "拍摄任务作废");
        assertEquals("已下单", order(soOf(po)).path("items").get(0).path("progressName").asText());
        // 回到在途后可以重新验收
        ok(accept(sid, List.of(new int[] {2, 2, 0}), keeper));
        assertEquals("已入库", order(soOf(po)).path("items").get(0).path("progressName").asText());
    }

    // ---------------------------------------------------------------- 拍摄任务与附件

    @Test
    void shoot_uploadComplete_deleteBack_reuse_skip_attachmentAccess() throws Exception {
        long sup = supplier("华控自动化", null);
        JsonNode po = orderedPo(sup, l("A-1", 4, "1000", "200"));
        long poId = po.path("id").asLong();
        long s1 = ok(ship(poId, Map.of(item(po, 0), 2), lin)).path("shipment").path("id").asLong();
        ok(accept(s1, List.of(new int[] {2, 2, 0}), keeper));
        long task = ok(call(json(post(SHOOT + "/page"), "{}"), keeper)).path("records").get(0).path("id").asLong();

        // 伪装的文件：扩展名 mp4，内容不是视频
        JsonNode fake = call(multipart(FILE).file(new MockMultipartFile("file", "a.mp4", "video/mp4", "MZ\u0090\u0000exe".getBytes()))
                .param("ownerType", "SHOOT"), keeper);
        assertEquals("只支持 MP4、MOV 视频", fail(fake).path("message").asText());

        long unbox = upload("SHOOT", "拆箱.mp4", MP4, keeper);
        long inspect = upload("SHOOT", "验货.mp4", MP4, keeper);
        assertEquals("实物图请上传图片", fail(call(json(post(SHOOT + "/" + task + "/media"),
                write(Map.of("attachmentId", unbox, "mediaType", 3))), keeper)).path("message").asText());
        ok(call(json(post(SHOOT + "/" + task + "/media"), write(Map.of("attachmentId", unbox, "mediaType", 1))), keeper));
        ok(call(json(post(SHOOT + "/" + task + "/media"), write(Map.of("attachmentId", inspect, "mediaType", 2))), keeper));
        JsonNode t = null;
        for (int i = 0; i < 6; i++) {
            long img = upload("SHOOT", "p" + i + ".png", PNG, keeper);
            t = ok(call(json(post(SHOOT + "/" + task + "/media"), write(Map.of("attachmentId", img, "mediaType", 3))), keeper));
            assertEquals(i < 5 ? "待拍摄" : "已完成", t.path("task").path("statusName").asText(), "拍够 6 张图才完成");
        }
        assertEquals("陈晨", t.path("task").path("shooterName").asText());
        long photo = t.path("media").get(2).path("id").asLong();
        JsonNode back = ok(call(delete(SHOOT + "/" + task + "/media/" + photo), keeper));
        assertEquals("待拍摄", back.path("task").path("statusName").asText(), "删到不齐回到待拍摄");
        assertEquals(5, back.path("task").path("photoCount").asInt());
        long img = upload("SHOOT", "p6.png", PNG, keeper);
        ok(call(json(post(SHOOT + "/" + task + "/media"), write(Map.of("attachmentId", img, "mediaType", 3))), keeper));

        // 附件访问：本人能预览视频（Range），没有拍摄任务菜单的不行
        MockHttpServletResponse res = perform(get(FILE + "/" + unbox).header("Range", "bytes=0-3"), keeper);
        assertEquals(206, res.getStatus(), "视频支持分段读取");
        assertEquals(4, res.getContentAsByteArray().length);
        assertEquals(403, perform(get(FILE + "/" + unbox), lin).getStatus(), "没有拍摄任务菜单的人不能看");

        // 同型号再入库：可以复用；另一个跳过
        long s2 = ok(ship(poId, Map.of(item(po, 0), 2), lin)).path("shipment").path("id").asLong();
        ok(accept(s2, List.of(new int[] {2, 2, 0}), keeper));
        JsonNode pending = ok(call(json(post(SHOOT + "/page"), write(Map.of("status", 1))), keeper));
        assertEquals(1, pending.path("total").asInt());
        JsonNode second = pending.path("records").get(0);
        assertTrue(second.path("reusable").asBoolean(), "同型号已有素材，可复用");
        JsonNode reused = ok(call(post(SHOOT + "/" + second.path("id").asLong() + "/reuse"), keeper));
        assertEquals("已完成", reused.path("task").path("statusName").asText());
        assertEquals(8, reused.path("media").size(), "复用引用已有素材");
        assertEquals("这个任务复用了已有素材，不需要再上传", fail(call(json(post(SHOOT + "/" + second.path("id").asLong() + "/media"),
                write(Map.of("attachmentId", upload("SHOOT", "x.png", PNG, keeper), "mediaType", 3))), keeper)).path("message").asText());
        assertEquals("只有待拍摄的任务可以跳过",
                fail(call(json(post(SHOOT + "/" + task + "/skip"), "{\"reason\":\"重复入库\"}"), keeper)).path("message").asText());
        assertEquals(0, ok(call(get(SHOOT + "/counts"), keeper)).path("pending").asInt());

        // 拍摄任务开始后入库单不能冲销
        long gr1 = jdbc.queryForObject("select receipt_id from shoot_task where id = ?", Long.class, task);
        assertEquals("拍摄任务已经开始，不能冲销",
                fail(call(json(post(GR + "/" + gr1 + "/reverse"), "{\"reason\":\"x\"}"), keeper)).path("message").asText());
    }

    @Test
    void shoot_concurrentPhotos_stillCompletes() throws Exception {
        long sup = supplier("华控自动化", null);
        JsonNode po = orderedPo(sup, l("A-1", 1, "1000", "200"));
        long sid = ok(ship(po.path("id").asLong(), Map.of(item(po, 0), 1), lin)).path("shipment").path("id").asLong();
        ok(accept(sid, List.of(new int[] {1, 1, 0}), keeper));
        long task = ok(call(json(post(SHOOT + "/page"), "{}"), keeper)).path("records").get(0).path("id").asLong();
        ok(call(json(post(SHOOT + "/" + task + "/media"), write(Map.of("attachmentId", upload("SHOOT", "a.mp4", MP4, keeper), "mediaType", 1))), keeper));
        ok(call(json(post(SHOOT + "/" + task + "/media"), write(Map.of("attachmentId", upload("SHOOT", "b.mp4", MP4, keeper), "mediaType", 2))), keeper));
        List<Long> imgs = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            imgs.add(upload("SHOOT", "p" + i + ".png", PNG, keeper));
        }
        // 6 张图同时挂上：拿到行锁后要看到别人已提交的素材
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(6);
        try {
            List<java.util.concurrent.Future<JsonNode>> all = new ArrayList<>();
            for (Long img : imgs) {
                all.add(pool.submit(() -> call(json(post(SHOOT + "/" + task + "/media"), write(Map.of("attachmentId", img, "mediaType", 3))), keeper)));
            }
            for (java.util.concurrent.Future<JsonNode> f : all) {
                ok(f.get());
            }
        } finally {
            pool.shutdown();
        }
        assertEquals("已完成", ok(call(get(SHOOT + "/" + task), keeper)).path("task").path("statusName").asText());
    }

    @Test
    void shoot_skip_andShipmentAttachments() throws Exception {
        long sup = supplier("华控自动化", null);
        JsonNode po = orderedPo(sup, l("A-1", 1, "1000", "200"));
        long att = upload("SHIPMENT", "发货.png", PNG, lin);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("poId", po.path("id").asLong());
        body.put("shipDate", LocalDate.now().toString());
        body.put("items", List.of(Map.of("poItemId", item(po, 0), "quantity", 1)));
        body.put("attachmentIds", List.of(att));
        JsonNode s = ok(call(json(post(SHIP), write(body)), lin));
        assertEquals(1, s.path("attachments").size());
        assertEquals(1, s.path("shipment").path("attachmentCount").asInt());
        long sid = s.path("shipment").path("id").asLong();
        assertEquals(200, perform(get(FILE + "/" + att), keeper).getStatus(), "仓库可以看发货单附件");
        ok(accept(sid, List.of(new int[] {1, 1, 0}), keeper));
        long task = ok(call(json(post(SHOOT + "/page"), "{}"), keeper)).path("records").get(0).path("id").asLong();
        assertEquals("这个型号还没有可复用的素材", fail(call(post(SHOOT + "/" + task + "/reuse"), keeper)).path("message").asText());
        JsonNode skipped = ok(call(json(post(SHOOT + "/" + task + "/skip"), "{\"reason\":\"客户不需要\"}"), keeper));
        assertEquals("已跳过", skipped.path("task").path("statusName").asText());
        assertEquals("客户不需要", skipped.path("task").path("skipReason").asText());
    }

    // ---------------------------------------------------------------- 发货跟踪：预计发货日期、发货进度、预计到货、货物状态

    private static final String TRANSIT = "/api/v1/system/transit-times";

    @Test
    void expectedShipDate_requiredOnConfirm_editableAndLogged() throws Exception {
        long sup = supplier("华控自动化", null);
        long draft = draftPo(sup, l("A-1", 2, "1000", "200"));
        ok(savePo(draft, poBody(po(draft, admin), "800"), admin));
        String today = LocalDate.now().toString();
        assertEquals("请填写预计发货日期", fail(call(json(post(PO + "/" + draft + "/confirm"), write(Map.of("orderDate", today))), admin))
                .path("message").asText());
        assertEquals("预计发货日期不能早于下单日期", fail(call(json(post(PO + "/" + draft + "/confirm"),
                write(Map.of("orderDate", today, "expectedShipDate", LocalDate.now().minusDays(1).toString()))), admin)).path("message").asText());
        String d1 = LocalDate.now().plusDays(2).toString();
        JsonNode p = ok(call(json(post(PO + "/" + draft + "/confirm"), write(Map.of("orderDate", today, "expectedShipDate", d1))), admin));
        assertEquals(d1, p.path("expectedShipDate").asText());
        assertEquals("未发货", p.path("shipProgressName").asText());

        String d2 = LocalDate.now().plusDays(5).toString();
        Map<String, Object> body = poBody(p, null);
        body.put("expectedShipDate", d2);
        assertEquals(d2, ok(savePo(draft, body, admin)).path("expectedShipDate").asText());
        assertTrue(jdbc.queryForObject("select count(*) from purchase_order_log where po_id = ? and content like ?", Integer.class,
                draft, "%预计发货日期 " + d1 + " → " + d2 + "%") > 0, "供应商改期写日志");
        body.remove("expectedShipDate");
        assertEquals(d2, ok(savePo(draft, body, admin)).path("expectedShipDate").asText(), "已下单的不传表示不改");
    }

    @Test
    void shipProgress_filterStatsAndOverdue() throws Exception {
        long sup = supplier("华控自动化", null);
        JsonNode po = orderedPo(sup, l("A-1", 5, "1000", "200"));
        long poId = po.path("id").asLong();
        jdbc.update("update purchase_order set expected_ship_date = ? where id = ?", LocalDate.now().minusDays(2), poId);
        JsonNode list = ok(call(json(post(PO + "/page"), write(Map.of("shipProgress", "OVERDUE"))), lin));
        assertEquals(1, list.path("total").asInt(), "逾期未发");
        assertEquals(2, list.path("records").get(0).path("overdueDays").asInt());
        assertEquals("未发货", list.path("records").get(0).path("shipProgressName").asText());
        JsonNode stats = ok(call(get(PO + "/stats"), lin));
        assertEquals(1, stats.path("pendingShip").asInt());
        assertEquals(1, stats.path("overdueShip").asInt());
        assertEquals(1, ok(call(json(post(PO + "/page"), write(Map.of("shipProgress", "UNSHIPPED"))), lin)).path("total").asInt());

        ok(ship(poId, Map.of(item(po, 0), 2), lin));
        assertEquals(1, ok(call(json(post(PO + "/page"), write(Map.of("shipProgress", "PARTIAL"))), lin)).path("total").asInt());
        assertEquals(0, ok(call(json(post(PO + "/page"), write(Map.of("shipProgress", "UNSHIPPED"))), lin)).path("total").asInt());
        JsonNode partial = ok(call(json(post(PO + "/page"), "{}"), lin)).path("records").get(0);
        assertEquals("部分发货", partial.path("shipProgressName").asText());
        assertEquals(2, partial.path("shippedQty").asInt());
        assertEquals(5, partial.path("totalQty").asInt());

        long s2 = ok(ship(poId, Map.of(item(po, 0), 3), lin)).path("shipment").path("id").asLong();
        assertEquals(0, ok(call(json(post(PO + "/page"), write(Map.of("shipProgress", "OVERDUE"))), lin)).path("total").asInt(), "全部发出就不算逾期");
        assertEquals(0, ok(call(get(PO + "/stats"), lin)).path("pendingShip").asInt());
        JsonNode shipped = ok(call(json(post(PO + "/page"), write(Map.of("shipProgress", "SHIPPED"))), lin));
        assertEquals(1, shipped.path("total").asInt());
        assertTrue(shipped.path("records").get(0).hasNonNull("earliestArrival"), "在途最早预计到货");

        long s1 = jdbc.queryForObject("select min(id) from supplier_shipment where po_id = ?", Long.class, poId);
        ok(accept(s1, List.of(new int[] {2, 2, 0}), keeper));
        ok(accept(s2, List.of(new int[] {3, 3, 0}), keeper));
        assertEquals("已入库", po(poId, lin).path("shipProgressName").asText());
        assertEquals(1, ok(call(json(post(PO + "/page"), write(Map.of("shipProgress", "RECEIVED"))), lin)).path("total").asInt());
    }

    @Test
    void arrivalEstimate_rulesDefaultsAndOverdue() throws Exception {
        long sup = supplier("华控自动化", null);
        jdbc.update("update supplier set region = '上海市/上海市/松江区' where id = ?", sup);
        JsonNode po = orderedPo(sup, l("A-1", 4, "1000", "200"));
        long poId = po.path("id").asLong();
        String d = "2026-10-10";
        JsonNode e = ok(call(get(SHIP + "/estimate").param("poId", String.valueOf(poId)).param("carrier", "顺丰").param("shipDate", d), lin));
        assertEquals("2026-10-12", e.path("date").asText());
        assertEquals("顺丰默认 2 天（上海没有单独规则）", e.path("basis").asText(), "初始规则只有顺丰默认、福建与偏远省份");

        String admin2 = admin;
        ok(call(json(post(TRANSIT), write(Map.of("carrier", "顺丰", "originProvince", "上海市", "days", 1))), admin2));
        assertEquals("顺丰 · 上海 已有规则", fail(call(json(post(TRANSIT), write(Map.of("carrier", "顺丰", "originProvince", "上海", "days", 2))), admin2))
                .path("message").asText());
        e = ok(call(get(SHIP + "/estimate").param("poId", String.valueOf(poId)).param("carrier", "顺丰").param("shipDate", d), lin));
        assertEquals("2026-10-11", e.path("date").asText());
        assertEquals("顺丰 · 上海 → 福州 1 天", e.path("basis").asText());
        e = ok(call(get(SHIP + "/estimate").param("poId", String.valueOf(poId)).param("carrier", "某某快运").param("shipDate", d), lin));
        assertEquals("某某快运没有时效规则，按默认 3 天", e.path("basis").asText());
        assertEquals(403, perform(get(TRANSIT), lin).getStatus(), "采购员不能维护快递时效");

        // 不填预计到货时按规则补上；填了不能早于发货日期
        Map<String, Object> body = new HashMap<>();
        body.put("poId", poId);
        body.put("carrier", "顺丰");
        body.put("shipDate", LocalDate.now().toString());
        body.put("items", List.of(Map.of("poItemId", item(po, 0), "quantity", 1)));
        JsonNode s = ok(call(json(post(SHIP), write(body)), lin));
        assertEquals(LocalDate.now().plusDays(1).toString(), s.path("shipment").path("expectedArrivalDate").asText());
        body.put("expectedArrivalDate", LocalDate.now().minusDays(1).toString());
        assertEquals("预计到货日期不能早于发货日期", fail(call(json(post(SHIP), write(body)), lin)).path("message").asText());

        // 在途且过了预计到货：超时未到
        jdbc.update("update supplier_shipment set expected_arrival_date = ? where id = ?", LocalDate.now().minusDays(1),
                s.path("shipment").path("id").asLong());
        assertTrue(ok(call(json(post(SHIP + "/page"), "{}"), lin)).path("records").get(0).path("arrivalOverdue").asBoolean());
        assertTrue(ok(call(json(post(GR + "/pending"), "{}"), keeper)).path("records").get(0).path("arrivalOverdue").asBoolean());

        // 默认运输天数（平台模板，测试后恢复）
        try {
            ok(call(json(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(TRANSIT + "/default-days"), "{\"days\":5}"), admin2));
            e = ok(call(get(SHIP + "/estimate").param("poId", String.valueOf(poId)).param("shipDate", d), lin));
            assertEquals("没填快递公司，按默认 5 天", e.path("basis").asText());
            assertEquals("2026-10-15", e.path("date").asText());
        } finally {
            jdbc.update("update sys_config set config_value = '3' where tenant_id = 0 and config_key = 'transit.default-days'");
        }
    }

    @Test
    void salesOrderGoods_listAndDetail() throws Exception {
        long sup = supplier("华控自动化", null);
        JsonNode po = orderedPo(sup, l("A-1", 10, "1000", "200"));
        long soId = soOf(po);
        long sid = ok(ship(po.path("id").asLong(), Map.of(item(po, 0), 3), lin)).path("shipment").path("id").asLong();
        JsonNode it = order(soId).path("items").get(0);
        assertEquals(3, it.path("goodsInTransit").asInt());
        assertEquals(7, it.path("goodsPendingShip").asInt());
        assertEquals(0, it.path("goodsReceived").asInt());
        assertEquals(0, it.path("goodsPendingPurchase").asInt());
        assertEquals(1, it.path("transits").size());
        assertTrue(it.path("transits").get(0).hasNonNull("expectedArrivalDate"));
        assertTrue(it.path("purchaseOrders").get(0).hasNonNull("expectedShipDate"));

        ok(accept(sid, List.of(new int[] {3, 2, 1}), keeper));
        JsonNode row = null;
        for (JsonNode r : ok(call(json(post(SO + "/page"), "{}"), admin)).path("records")) {
            if (r.path("id").asLong() == soId) {
                row = r;
            }
        }
        JsonNode g = row.path("goods");
        assertEquals(2, g.path("received").asInt(), "不良的 1 个不算入库");
        assertEquals(0, g.path("inTransit").asInt());
        assertEquals(7, g.path("pendingShip").asInt());
        assertEquals(10, g.path("total").asInt());
        assertEquals("已下单", row.path("progressName").asText(), "订单状态口径不变");
        assertTrue(row.has("updateBy"), "列表审计列");
    }
}

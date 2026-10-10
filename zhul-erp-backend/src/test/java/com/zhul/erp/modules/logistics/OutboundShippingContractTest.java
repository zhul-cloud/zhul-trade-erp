package com.zhul.erp.modules.logistics;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.modules.sales.SalesContractSupport;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * spec warehouse/outbound、logistics/shipment、logistics/shipping-documents、logistics/freight-allocation、
 * logistics/forwarder-statement 与 sales/sales-order「订单进度」的出库、出运联动。
 */
class OutboundShippingContractTest extends SalesContractSupport {

    private static final String SHIP = "/api/v1/purchase/shipments";
    private static final String GR = "/api/v1/warehouse/receipts";
    private static final String OB = "/api/v1/logistics/outbounds";
    private static final String SH = "/api/v1/logistics/shipments";
    private static final String ST = "/api/v1/logistics/statements";
    private static final long KEEPER = 99000041L;

    private String keeper;
    private String lin;
    private long forwarder;

    @BeforeEach
    void people() {
        for (int id : new int[] {100088, 100089, 110189, 110190, 110191, 110192}) {
            jdbc.update("insert into role_resource (role_code, resource_id, resource_code) values (?, ?, '')", BUYER_ROLE, id);
        }
        jdbc.update("insert into user_basic (id, tenant_id, name, username, role_code, status) values (?, 0, '陈晨', 'it_keeper', 'ROLE_WH', 1)", KEEPER);
        jdbc.update("insert into account (id, tenant_id, user_id, username, admin_flag) values (?, 0, ?, 'it_keeper', 0)", KEEPER, KEEPER);
        keeper = token("it_keeper");
        lin = token("it_lin");
        forwarder = forwarder("深圳捷运", 5000);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("delete from account where id = ?", KEEPER);
        jdbc.update("delete from user_basic where id = ?", KEEPER);
    }

    // ---------------------------------------------------------------- 准备数据

    private long forwarder(String name, int divisor) {
        long id = supplier(name, null);
        jdbc.update("update supplier set supplier_type = 3, volume_divisor = ? where id = ?", divisor, id);
        return id;
    }

    /** 订单型号 A × qty：采购单确认下单；received 为已验收合格的数量（0 表示还没发货） */
    private JsonNode orderedPo(long supplierId, int qty) throws Exception {
        long c = customer("Hoorain HTF", "Bangladesh");
        List<Long> items = quotationItemIds(quotation(c, 2, "USD", null, l("A-1", qty, "100", "20")));
        for (Long i : items) {
            long inquiryItem = jdbc.queryForObject("select inquiry_item_id from quotation_item where id = ?", Long.class, i);
            jdbc.update("insert into sourcing_quote (tenant_id, inquiry_item_id, quoted_by, supplier_id, channel, shop_name) values (0, ?, ?, ?, 4, '')",
                    inquiryItem, BUYER_LIN, supplierId);
            jdbc.update("update quotation_item set cost_quote_id = (select max(id) from sourcing_quote where inquiry_item_id = ?) where id = ?", inquiryItem, i);
        }
        long pi = sentPi(items, null);
        ok(uploadSlip(pi, "1.00", "2026-10-07", admin));
        ok(call(json(post(PI + "/" + pi + "/convert"), "{}"), admin));
        long draft = jdbc.queryForObject("select max(id) from purchase_order where tenant_id = 0 and supplier_id = ? and status = 1 and deleted_at is null",
                Long.class, supplierId);
        return priceAndConfirm(draft, "80", admin);
    }

    private long receive(JsonNode po, int qty) throws Exception {
        long poItem = po.path("items").get(0).path("id").asLong();
        long sid = ok(call(json(post(SHIP), write(Map.of("poId", po.path("id").asLong(), "shipDate", LocalDate.now().toString(),
                "items", List.of(Map.of("poItemId", poItem, "quantity", qty))))), lin)).path("shipment").path("id").asLong();
        long item = ok(call(get(GR + "/shipments/" + sid), keeper)).path("shipment").path("items").get(0).path("id").asLong();
        ok(call(json(post(GR), write(Map.of("shipmentId", sid, "receivedDate", LocalDate.now().toString(),
                "items", List.of(Map.of("shipmentItemId", item, "receivedQty", qty, "qualifiedQty", qty))))), keeper));
        return sid;
    }

    private long soOf(JsonNode po) {
        return po.path("items").get(0).path("soId").asLong();
    }

    private long soItem(long soId) {
        return jdbc.queryForObject("select id from sales_order_item where so_id = ? and deleted_at is null order by line_no limit 1", Long.class, soId);
    }

    private JsonNode notice(long soId, int qty) throws Exception {
        return call(json(post(OB + "/notice"), write(Map.of("soId", soId, "forwarderId", forwarder,
                "items", List.of(Map.of("soItemId", soItem(soId), "quantity", qty))))), admin);
    }

    /** 每箱 {长, 宽, 高, 毛重×100, 数量} */
    private JsonNode pack(JsonNode ob, int[]... boxes) throws Exception {
        long item = ob.path("items").get(0).path("id").asLong();
        List<Map<String, Object>> list = new ArrayList<>();
        for (int[] b : boxes) {
            list.add(Map.of("length", b[0], "width", b[1], "height", b[2], "grossWeight", BigDecimal.valueOf(b[3], 2),
                    "items", List.of(Map.of("outboundItemId", item, "quantity", b[4]))));
        }
        return call(json(put(OB + "/" + ob.path("id").asLong() + "/pack"), write(Map.of("boxes", list))), keeper);
    }

    private JsonNode handOver(List<Long> ids, String freight) throws Exception {
        return call(json(post(OB + "/hand-over"), write(Map.of("outboundIds", ids, "carrier", "顺丰", "trackingNo", "SF1500",
                "sentDate", LocalDate.now().toString(), "freight", freight))), keeper);
    }

    private JsonNode order(long soId) throws Exception {
        return ok(call(get(SO + "/" + soId), admin));
    }

    private BigDecimal allocated(int sourceType, long sourceId) {
        BigDecimal v = jdbc.queryForObject("select sum(amount) from freight_allocation where source_type = ? and source_id = ? and deleted_at is null",
                BigDecimal.class, sourceType, sourceId);
        return v == null ? BigDecimal.ZERO : v;
    }

    // ---------------------------------------------------------------- 发货通知、打包、交快递

    @Test
    void notice_pack_handOver_progress_undo() throws Exception {
        JsonNode po = orderedPo(supplier("华控自动化", null), 10);
        long soId = soOf(po);
        receive(po, 10);
        JsonNode form = ok(call(get(OB + "/notice-form").param("soId", String.valueOf(soId)), admin));
        assertEquals(10, form.path("lines").get(0).path("available").asInt());
        assertEquals("A-1 最多可以出库 10 个", fail(notice(soId, 12)).path("message").asText());
        assertEquals(403, perform(get(OB + "/notice-form").param("soId", String.valueOf(soId)), keeper).getStatus(), "仓库不能发通知");

        JsonNode ob1 = ok(notice(soId, 6));
        assertEquals("OB" + TODAY + "001", ob1.path("obNo").asText());
        assertEquals("待打包", ob1.path("statusName").asText());
        assertEquals("A-1 最多可以出库 4 个", fail(notice(soId, 5)).path("message").asText(), "已出库的占用可出库数量");
        JsonNode ob2 = ok(notice(soId, 4));
        assertEquals(2, ok(call(get(OB + "/counts"), keeper)).path("pending").asInt());

        assertEquals("A-1 还差 1 个没装箱", fail(pack(ob1, new int[] {55, 40, 30, 3000, 5})).path("message").asText());
        JsonNode packed = ok(pack(ob1, new int[] {55, 40, 30, 3000, 4}, new int[] {40, 30, 30, 600, 2}));
        assertEquals("已打包", packed.path("statusName").asText());
        money("37.20", packed.path("chargeableWeight"));
        assertEquals(ob2.path("obNo").asText() + " 还没打包",
                fail(handOver(List.of(ob1.path("id").asLong(), ob2.path("id").asLong()), "80")).path("message").asText());
        ok(pack(ob2, new int[] {20, 20, 20, 1000, 4}));
        ok(handOver(List.of(ob1.path("id").asLong(), ob2.path("id").asLong()), "80"));
        long waybill = jdbc.queryForObject("select courier_waybill_id from outbound_order where id = ?", Long.class, ob1.path("id").asLong());
        assertEquals(new BigDecimal("80.00"), allocated(1, waybill), "国内运费分摊合计等于实际运费");
        JsonNode o = order(soId);
        assertEquals("已交货代", o.path("items").get(0).path("progressName").asText(), "6 + 4 都交货代");
        assertEquals(10, o.path("items").get(0).path("goodsHanded").asInt());

        assertEquals("请填写原因", fail(call(json(post(OB + "/" + ob2.path("id").asLong() + "/undo-hand-over"), "{\"reason\":\"\"}"), keeper))
                .path("message").asText());
        ok(call(json(post(OB + "/" + ob2.path("id").asLong() + "/undo-hand-over"), "{\"reason\":\"快递单号录错\"}"), keeper));
        assertEquals("已入库", order(soId).path("items").get(0).path("progressName").asText(), "撤销后回到按入库算出的进度");
        assertEquals(new BigDecimal("80.00"), allocated(1, waybill), "剩下的出库单分到全部运费");

        assertEquals("仓库已经打包，不能撤回", fail(call(json(post(OB + "/" + ob1.path("id").asLong() + "/withdraw"), "{\"reason\":\"x\"}"), admin))
                .path("message").asText());
    }

    @Test
    void handOver_differentForwarders_rejected_andWithdrawFreesQuantity() throws Exception {
        JsonNode po = orderedPo(supplier("华控自动化", null), 4);
        long soId = soOf(po);
        receive(po, 4);
        JsonNode ob1 = ok(notice(soId, 2));
        long other = forwarder("广州速达", 6000);
        JsonNode ob2 = ok(call(json(post(OB + "/notice"), write(Map.of("soId", soId, "forwarderId", other,
                "items", List.of(Map.of("soItemId", soItem(soId), "quantity", 2))))), admin));
        ok(pack(ob1, new int[] {10, 10, 10, 100, 2}));
        ok(pack(ob2, new int[] {10, 10, 10, 100, 2}));
        assertEquals("只能把同一家货代的出库单合成一票", fail(handOver(List.of(ob1.path("id").asLong(), ob2.path("id").asLong()), "10"))
                .path("message").asText());
        JsonNode ob3 = notice(soId, 1);
        assertEquals("A-1 还没有可以出库的数量", fail(ob3).path("message").asText());
    }

    // ---------------------------------------------------------------- 出运单、CI/PL、对账

    @Test
    void shipment_ship_freight_docs_statement() throws Exception {
        JsonNode po = orderedPo(supplier("华控自动化", null), 10);
        long soId = soOf(po);
        receive(po, 10);
        JsonNode ob = ok(notice(soId, 10));
        ok(pack(ob, new int[] {55, 40, 30, 3000, 6}, new int[] {40, 30, 30, 600, 4}));
        ok(handOver(List.of(ob.path("id").asLong()), "50"));

        JsonNode pending = ok(call(get(SH + "/pending"), admin));
        assertEquals(1, pending.size());
        assertEquals(1, pending.get(0).path("outbounds").size());
        long customerId = pending.get(0).path("customerId").asLong();
        JsonNode sh = ok(call(json(post(SH), write(Map.of("customerId", customerId, "forwarderId", forwarder,
                "outboundIds", List.of(ob.path("id").asLong())))), admin));
        assertEquals("SH" + TODAY + "001", sh.path("shNo").asText());
        assertEquals("待出运", sh.path("statusName").asText());
        assertEquals(0, ok(call(get(SH + "/pending"), admin)).size(), "放进出运单后不在待出运里");
        long shId = sh.path("id").asLong();
        assertEquals("出库单已放进出运单，请先从出运单里移出",
                fail(call(json(post(OB + "/" + ob.path("id").asLong() + "/undo-hand-over"), "{\"reason\":\"x\"}"), keeper)).path("message").asText());

        // CI / PL：合成一组，编号共用主体
        JsonNode withDocs = ok(call(json(post(SH + "/" + shId + "/docs"), "{\"mode\":\"MERGED\",\"paymentRef\":\"TT No.1\"}"), admin));
        JsonNode group = withDocs.path("docGroups").get(0);
        assertEquals(group.path("ciNo").asText().replace("CI", "PL"), group.path("plNo").asText());
        JsonNode again = ok(call(json(post(SH + "/" + shId + "/docs"), "{\"mode\":\"MERGED\"}"), admin));
        assertEquals(group.path("ciNo").asText(), again.path("docGroups").get(0).path("ciNo").asText(), "重新生成编号不变");
        MockHttpServletResponse ci = perform(get(SH + "/docs/" + group.path("id").asLong() + "/export").param("kind", "ci"), admin);
        assertEquals(200, ci.getStatus());
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(ci.getContentAsByteArray()))) {
            assertEquals("A-1", wb.getSheetAt(0).getRow(9).getCell(1).getStringCellValue(), "CI 明细行");
            assertEquals(10, (int) wb.getSheetAt(0).getRow(9).getCell(4).getNumericCellValue());
        }
        MockHttpServletResponse pl = perform(get(SH + "/docs/" + group.path("id").asLong() + "/export").param("kind", "pl"), admin);
        assertEquals(200, pl.getStatus());
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(pl.getContentAsByteArray()))) {
            assertEquals(30.0, wb.getSheetAt(0).getRow(9).getCell(5).getNumericCellValue(), "第 1 箱毛重");
            assertEquals("40x30x30", wb.getSheetAt(0).getRow(10).getCell(6).getStringCellValue(), "第 2 箱尺寸");
        }

        // 登记出运：国际运费分摊，订单型号已出运
        JsonNode shipped = ok(call(json(post(SH + "/" + shId + "/ship"), write(Map.of("carrier", "DHL", "waybillNo", "1234567890",
                "shippedDate", LocalDate.now().toString(), "freight", "100"))), admin));
        assertEquals("已出运", shipped.path("statusName").asText());
        assertEquals(new BigDecimal("100.00"), allocated(2, shId));
        JsonNode o = order(soId);
        assertEquals("已出运", o.path("items").get(0).path("progressName").asText());
        assertEquals(10, o.path("items").get(0).path("goodsShipped").asInt());
        assertEquals(0, o.path("items").get(0).path("goodsInWarehouse").asInt());
        assertEquals(1, ok(call(json(post(SH + "/page"), write(Map.of("keyword", "1234567890"))), admin)).path("total").asInt());

        // 对账：差额要说明；确认后运费更新、重新分摊，不能再改
        String period = LocalDate.now().toString().substring(0, 7);
        JsonNode st = ok(call(json(post(ST), write(Map.of("forwarderId", forwarder, "period", period))), admin));
        assertEquals(1, st.path("lines").size());
        long line = st.path("lines").get(0).path("id").asLong();
        assertEquals("这个月没有要对账的出运单", fail(call(json(post(ST), write(Map.of("forwarderId", forwarder, "period", period))), admin))
                .path("message").asText(), "已在对账单上的不再列出");
        assertTrue(fail(call(json(post(ST + "/" + st.path("id").asLong() + "/confirm"),
                write(Map.of("lines", List.of(Map.of("id", line, "statementAmount", "150"))))), admin)).path("message").asText().endsWith("有差额，请填写说明"));
        JsonNode done = ok(call(json(post(ST + "/" + st.path("id").asLong() + "/confirm"),
                write(Map.of("lines", List.of(Map.of("id", line, "statementAmount", "150", "note", "燃油附加费"))))), admin));
        assertEquals("已确认", done.path("statusName").asText());
        money("50.00", done.path("diffTotal"));
        assertEquals(new BigDecimal("150.00"), allocated(2, shId), "对账金额重新分摊");
        assertEquals("已对账的出运单不能改运费", fail(call(json(put(SH + "/" + shId + "/shipping"), write(Map.of("carrier", "DHL", "waybillNo", "1234567890",
                "shippedDate", LocalDate.now().toString(), "freight", "100"))), admin)).path("message").asText());
        assertEquals(200, perform(get(ST + "/" + st.path("id").asLong() + "/export"), admin).getStatus());
    }

    // ---------------------------------------------------------------- 直发货代

    @Test
    void directToForwarder_confirmCreatesReceiptAndOutbound() throws Exception {
        JsonNode po = orderedPo(supplier("华强电子", null), 5);
        long soId = soOf(po);
        long poItem = po.path("items").get(0).path("id").asLong();
        Map<String, Object> body = new HashMap<>();
        body.put("poId", po.path("id").asLong());
        body.put("directForwarderId", forwarder);
        body.put("shipDate", LocalDate.now().toString());
        body.put("items", List.of(Map.of("poItemId", poItem, "quantity", 5)));
        long sid = ok(call(json(post(SHIP), write(body)), lin)).path("shipment").path("id").asLong();
        assertEquals(0, ok(call(json(post(GR + "/pending"), "{}"), keeper)).path("total").asInt(), "直发货不进仓库待收货");

        JsonNode pending = ok(call(get(SH + "/pending"), admin));
        assertEquals(1, pending.get(0).path("directs").size());
        long customerId = pending.get(0).path("customerId").asLong();
        long shId = ok(call(json(post(SH), write(Map.of("customerId", customerId, "forwarderId", forwarder,
                "directShipmentIds", List.of(sid)))), admin)).path("id").asLong();
        assertEquals("还有 1 批直发货没确认实收", fail(call(json(post(SH + "/" + shId + "/ship"), write(Map.of("carrier", "DHL", "waybillNo", "1",
                "shippedDate", LocalDate.now().toString(), "freight", "10"))), admin)).path("message").asText());

        long shipItem = ok(call(get(SH + "/" + shId), admin)).path("directs").get(0).path("items").get(0).path("shipmentItemId").asLong();
        JsonNode confirmed = ok(call(json(post(SH + "/" + shId + "/directs/" + sid + "/confirm"), write(Map.of(
                "lines", List.of(Map.of("shipmentItemId", shipItem, "quantity", 4)),
                "boxes", List.of(Map.of("length", 35, "width", 30, "height", 25, "grossWeight", "4.20",
                        "items", List.of(Map.of("shipmentItemId", shipItem, "quantity", 4))))))), admin));
        assertTrue(confirmed.path("directs").get(0).path("confirmed").asBoolean());
        assertEquals(1, confirmed.path("outbounds").size());
        assertEquals("直发货代", confirmed.path("outbounds").get(0).path("sourceName").asText());
        assertEquals(1, jdbc.queryForObject("select count(*) from receiving_discrepancy where po_id = ? and type = 1 and quantity = 1 and deleted_at is null",
                Integer.class, po.path("id").asLong()), "货代少收 1 个，生成少发差异");
        assertEquals(0, jdbc.queryForObject("select count(*) from shoot_task where deleted_at is null and receipt_id in "
                + "(select id from purchase_receipt where po_id = ?)", Integer.class, po.path("id").asLong()), "直发货不拍摄");
        JsonNode it = order(soId).path("items").get(0);
        assertEquals(4, it.path("goodsHanded").asInt());
        assertEquals("已下单", it.path("progressName").asText(), "5 个只交了 4 个");

        ok(call(json(post(SH + "/" + shId + "/ship"), write(Map.of("carrier", "DHL", "waybillNo", "1",
                "shippedDate", LocalDate.now().toString(), "freight", "10"))), admin));
        assertEquals(4, order(soId).path("items").get(0).path("goodsShipped").asInt());
        assertFalse(order(soId).path("items").get(0).path("progressName").asText().equals("已出运"));
    }
}

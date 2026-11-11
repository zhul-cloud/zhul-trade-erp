package com.zhul.erp.modules.sales;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.zhul.erp.support.TestDates.today;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * spec sales/after-sales、sales/sales-return、warehouse/sales-return、purchase/purchase-return、finance/refund-request，
 * 以及 finance/order-cost「售后退货冲减订单成本」、
 * finance/expense「费用单关联售后单」、finance/customer-refund「退款单关联售后」、warehouse/stock-hold「售后退货转入闲置库存」。
 */
class AfterSalesContractTest extends SalesContractSupport {

    private static final String AS = "/api/v1/sales/after-sales";
    private static final String TH = "/api/v1/sales/returns";
    private static final String RT = "/api/v1/warehouse/returns";
    private static final String PRS = "/api/v1/purchase/returns";
    private static final String RQ = "/api/v1/finance/refund-requests";
    private static final String REFUND = "/api/v1/finance/refunds";
    private static final String FE = "/api/v1/finance/expenses";
    private static final String HOLD = "/api/v1/warehouse/holds";
    private static final String SHIP = "/api/v1/purchase/shipments";
    private static final String GR = "/api/v1/warehouse/receipts";

    @AfterEach
    void resetApproval() {
        jdbc.update("update sys_config set config_value = 'false' where tenant_id = 0 and config_key = 'finance.refund-request.approval'");
    }

    // ---------------------------------------------------------------- 准备数据

    /** 订单 A-1 × 10（售价 USD 50、采购 ¥200），已到账 paid，向供应商下单、全部合格入库，并全部出运；返回订单 ID */
    private long shippedOrder(long sup, String paid) throws Exception {
        long c = customer("Siam Automation", "Thailand");
        List<Long> items = quotationItemIds(quotation(c, 2, "USD", null, l("A-1", 10, "200", "50")));
        for (Long i : items) {
            long inquiryItem = jdbc.queryForObject("select inquiry_item_id from quotation_item where id = ?", Long.class, i);
            jdbc.update("insert into sourcing_quote (tenant_id, inquiry_item_id, quoted_by, supplier_id, channel, shop_name) values (0, ?, ?, ?, 4, '')",
                    inquiryItem, ADMIN_USER, sup);
            jdbc.update("update quotation_item set cost_quote_id = (select max(id) from sourcing_quote where inquiry_item_id = ?) where id = ?", inquiryItem, i);
        }
        long pi = sentPi(items, null);
        ok(confirmReceipt(pi, paid, null, false, admin));
        ok(call(json(post(PI + "/" + pi + "/convert"), "{}"), admin));
        long so = jdbc.queryForObject("select max(id) from sales_order where tenant_id = 0 and pi_id = ?", Long.class, pi);
        long draft = jdbc.queryForObject("select max(id) from purchase_order where tenant_id = 0 and supplier_id = ? and status = 1", Long.class, sup);
        JsonNode po = priceAndConfirm(draft, "200", admin);
        List<Map<String, Object>> lines = new ArrayList<>();
        po.path("items").forEach(i -> lines.add(Map.of("poItemId", i.path("id").asLong(), "quantity", i.path("quantity").asInt())));
        JsonNode s = ok(call(json(post(SHIP), write(Map.of("poId", draft, "carrier", "顺丰", "trackingNo", "SF" + System.nanoTime(),
                "shipDate", LocalDate.now().toString(), "items", lines))), admin));
        long sid = s.path("shipment").path("id").asLong();
        JsonNode form = ok(call(get(GR + "/shipments/" + sid), admin));
        List<Map<String, Object>> acc = new ArrayList<>();
        form.path("shipment").path("items").forEach(i -> acc.add(Map.of("shipmentItemId", i.path("id").asLong(), "receivedQty", 10,
                "qualifiedQty", 10, "defectiveQty", 0)));
        ok(call(json(post(GR), write(Map.of("shipmentId", sid, "receivedDate", LocalDate.now().toString(), "items", acc))), admin));
        // 出运：销售出库单已交货代、出运单已出运
        long item = soItem(so);
        jdbc.update("insert into logistics_shipment (tenant_id, sh_no, status) values (0, ?, 2)", "ITSH" + System.nanoTime());
        long ls = jdbc.queryForObject("select max(id) from logistics_shipment where tenant_id = 0", Long.class);
        jdbc.update("insert into outbound_order (tenant_id, ob_no, so_id, status, logistics_id) values (0, ?, ?, 3, ?)", "ITOB" + so, so, ls);
        long ob = jdbc.queryForObject("select max(id) from outbound_order where tenant_id = 0", Long.class);
        jdbc.update("insert into outbound_order_item (tenant_id, outbound_id, so_item_id, quantity) values (0, ?, ?, 10)", ob, item);
        return so;
    }

    private long soItem(long so) {
        return jdbc.queryForObject("select id from sales_order_item where so_id = ? order by line_no limit 1", Long.class, so);
    }

    private JsonNode create(long so, int qty, String level) throws Exception {
        long customer = jdbc.queryForObject("select customer_id from sales_order where id = ?", Long.class, so);
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("customerId", customer);
        b.put("soId", so);
        b.put("items", List.of(Map.of("soItemId", soItem(so), "quantity", qty)));
        b.put("levelCode", level);
        b.put("symptom", "伺服报警 AL.16，轴无法运行");
        b.put("customerRequest", "尽快换新");
        b.put("nextAction", "等供应商检测");
        b.put("nextUpdateAt", LocalDateTime.now().minusHours(2).withNano(0).toString());
        return call(json(post(AS), write(b)), admin);
    }

    private JsonNode plan(long id, Map<String, Object> body) throws Exception {
        return call(json(post(AS + "/" + id + "/plan"), write(body)), admin);
    }

    /** 售后单选「仅退货」：生成待确认的销售退货单 */
    private JsonNode newReturn(long asId, long soItem, int qty) throws Exception {
        return call(json(post(TH), write(Map.of("afterSalesId", asId, "handling", 1, "items", List.of(Map.of("soItemId", soItem, "quantity", qty)),
                "carrier", "DHL", "trackingNo", "1234567890"))), admin);
    }

    /** 确认销售退货单，返回生成的退货入库单 */
    private JsonNode confirmReturn(long thId) throws Exception {
        JsonNode th = ok(call(json(post(TH + "/" + thId + "/confirm"), "{}"), admin));
        assertEquals("待入库", th.path("statusName").asText());
        return ok(call(get(RT + "/" + th.path("receipt").path("id").asLong()), admin));
    }

    private JsonNode apply(long so, Long asId, String amount, int method) throws Exception {
        Map<String, Object> b = new HashMap<>();
        b.put("soId", so);
        b.put("afterSalesId", asId);
        b.put("amount", amount);
        b.put("reason", "QUALITY");
        b.put("method", method);
        return call(json(post(RQ), write(b)), admin);
    }

    // ---------------------------------------------------------------- 场景

    @Test
    void afterSalesFlow_returnDisposition_refundRequest_expense_close() throws Exception {
        long sup = supplier("华控自动化", null);
        long so = shippedOrder(sup, "500");
        long item = soItem(so);

        // 登记：负责人默认订单业务员，状态处理中，下次更新已过期
        assertEquals("A-1 最多 10 个", fail(create(so, 11, "S2")).path("message").asText());
        JsonNode a = ok(create(so, 2, "S2"));
        long asId = a.path("id").asLong();
        assertEquals("AS" + today() + "001", a.path("asNo").asText());
        assertEquals("处理中", a.path("statusName").asText());
        assertEquals(ADMIN_USER, a.path("ownerId").asLong());
        assertTrue(a.path("overdue").asBoolean());
        assertEquals(10, a.path("items").get(0).path("shippedQty").asInt());
        assertEquals(10, a.path("items").get(0).path("returnableQty").asInt());
        assertEquals(1, ok(call(get(AS + "/counts"), admin)).path("overdue").asInt());
        assertEquals(1, ok(call(json(post(AS + "/page"), write(Map.of("keyword", "A-1"))), admin)).path("total").asInt());

        // 改等级要写原因并留痕；原因、方案
        assertEquals("请填写等级调整的原因", fail(plan(asId, Map.of("levelCode", "S3"))).path("message").asText());
        JsonNode p = ok(plan(asId, Map.of("levelCode", "S3", "levelNote", "客户恢复了生产", "reasonCode", "QUALITY",
                "solutions", List.of("RETURN_REPAIR", "EXCHANGE"))));
        assertEquals("S3", p.path("levelCode").asText());
        assertEquals(List.of("退回检测或维修", "换货"), List.of(p.path("solutionNames").get(0).asText(), p.path("solutionNames").get(1).asText()));
        assertTrue(p.path("logs").toString().contains("等级 S2 → S3：客户恢复了生产"), p.path("logs").toString());
        ok(call(json(post(AS + "/" + asId + "/notes"), write(Map.of("content", "供应商要求寄回检测，RMA-88213"))), admin));
        ok(call(json(post(AS + "/" + asId + "/status"), write(Map.of("status", 2))), admin));

        // 销售退货单：可退 = 已出运 − 其他销售退货单
        JsonNode r = ok(newReturn(asId, item, 2));
        long thId = r.path("id").asLong();
        assertEquals("TH" + today() + "001", r.path("returnNo").asText());
        assertEquals("待确认", r.path("statusName").asText());
        assertEquals("仅退货", r.path("handlingName").asText());
        assertEquals(ADMIN_USER, r.path("ownerId").asLong(), "负责人取售后单负责人");
        assertEquals("A-1 最多还能退 8 个", fail(newReturn(asId, item, 9)).path("message").asText());
        assertEquals(8, ok(call(get(AS + "/" + asId), admin)).path("items").get(0).path("returnableQty").asInt(), "可退扣掉未作废的销售退货单");
        assertEquals("销售退货单 " + r.path("returnNo").asText() + " 待确认", fail(call(json(post(AS + "/" + asId + "/close"),
                write(Map.of("reasonCode", "QUALITY", "result", "换新"))), admin)).path("message").asText());
        ok(call(json(put(TH + "/" + thId), write(Map.of("items", List.of(Map.of("soItemId", item, "quantity", 2)), "carrier", "DHL",
                "trackingNo", "999"))), admin));
        // 确认：生成退货入库单
        JsonNode rr = confirmReturn(thId);
        long rid = rr.path("id").asLong();
        assertEquals("RR" + today() + "001", rr.path("receiptNo").asText());
        assertEquals("待入库", rr.path("statusName").asText());
        assertEquals(r.path("returnNo").asText(), rr.path("returnNo").asText());
        long rItem = rr.path("items").get(0).path("id").asLong();
        assertEquals("销售退货单待入库，不能确认", fail(call(json(post(TH + "/" + thId + "/confirm"), "{}"), admin)).path("message").asText());

        // 验收：实收 2、合格 1；销售退货单变为已入库
        Map<String, Object> rec = new HashMap<>();
        rec.put("receivedDate", LocalDate.now().toString());
        rec.put("items", List.of(Map.of("itemId", rItem, "receivedQty", 2, "qualifiedQty", 3)));
        assertEquals("A-1：合格数量不能多于实收", fail(call(json(post(RT + "/" + rid + "/receive"), write(rec)), admin)).path("message").asText());
        rec.put("items", List.of(Map.of("itemId", rItem, "receivedQty", 2, "qualifiedQty", 1)));
        JsonNode received = ok(call(json(post(RT + "/" + rid + "/receive"), write(rec)), admin));
        assertEquals("待处理", received.path("statusName").asText());
        money("200", received.path("items").get(0).path("unitCost"));
        assertEquals(sup, received.path("items").get(0).path("supplierId").asLong());
        long poId = received.path("items").get(0).path("poId").asLong();
        assertEquals("已入库", ok(call(get(TH + "/" + thId), admin)).path("statusName").asText());
        assertEquals("已入库的退货单不能作废", fail(call(json(post(TH + "/" + thId + "/void"), "{\"reason\":\"x\"}"), admin))
                .path("message").asText());
        // 撤回入库再验收
        ok(call(json(post(RT + "/" + rid + "/unreceive"), "{\"reason\":\"数量录错\"}"), admin));
        assertEquals("待入库", ok(call(get(TH + "/" + thId), admin)).path("statusName").asText());
        ok(call(json(post(RT + "/" + rid + "/receive"), write(rec)), admin));

        // 去向：不良不能转闲置；可以分几次处理，累计不超过实收
        Map<String, Object> toSupplier = new HashMap<>(Map.of("itemId", rItem, "disposition", 1, "quantity", 1, "supplierId", sup,
                "poId", poId));
        Map<String, Object> toHold = new HashMap<>(Map.of("itemId", rItem, "disposition", 2, "quantity", 2));
        assertEquals("只有合格的才能转闲置库存（合格 1 个）", fail(call(json(post(RT + "/" + rid + "/dispose"),
                write(Map.of("lines", List.of(toHold)))), admin)).path("message").asText());
        toHold.put("quantity", 1);
        JsonNode half = ok(call(json(post(RT + "/" + rid + "/dispose"), write(Map.of("lines", List.of(toHold)))), admin));
        assertEquals("待处理", half.path("statusName").asText(), "还有 1 个没处理");
        assertEquals("A-1 还有 1 个没处理，去向数量不能多于它", fail(call(json(post(RT + "/" + rid + "/dispose"),
                write(Map.of("lines", List.of(new HashMap<>(Map.of("itemId", rItem, "disposition", 3, "quantity", 2)))))), admin))
                .path("message").asText());
        JsonNode done = ok(call(json(post(RT + "/" + rid + "/dispose"), write(Map.of("lines", List.of(toSupplier)))), admin));
        assertEquals("已处理", done.path("statusName").asText());
        money("200", done.path("costCny"));
        assertEquals("已经处理过去向，不能撤回入库", fail(call(json(post(RT + "/" + rid + "/unreceive"), "{\"reason\":\"x\"}"), admin))
                .path("message").asText());

        // 退回供应商生成采购退货单：寄出登记运费 45，完成时供应商退款 ¥150，冲减应付与订单成本
        assertEquals(1, done.path("purchaseReturns").size());
        long prId = done.path("purchaseReturns").get(0).path("id").asLong();
        JsonNode pr = ok(call(get(PRS + "/" + prId), admin));
        assertEquals("PR" + today() + "001", pr.path("returnNo").asText());
        assertEquals("待寄出", pr.path("statusName").asText());
        assertEquals(rr.path("receiptNo").asText(), pr.path("sourceNo").asText());
        assertEquals(0, new java.math.BigDecimal("2000").compareTo(jdbc.queryForObject("select sum(amount) from fin_payable where source_type = 1 and source_id = ? and deleted_at is null", java.math.BigDecimal.class, poId)));
        ok(call(json(post(PRS + "/" + prId + "/ship"), write(Map.of("shipDate", LocalDate.now().toString(), "carrier", "顺丰",
                "trackingNo", "SF7788", "freightCny", 45, "companyPaid", true))), admin));
        assertEquals("请填写供应商退款或抵扣金额（没有就填 0）", fail(call(json(post(PRS + "/" + prId + "/complete"),
                write(Map.of("signedDate", LocalDate.now().toString()))), admin)).path("message").asText());
        JsonNode prDone = ok(call(json(post(PRS + "/" + prId + "/complete"), write(Map.of("signedDate", LocalDate.now().toString(),
                "refundAmount", 150, "settleNote", "抵扣下次货款"))), admin));
        assertEquals("已完成", prDone.path("statusName").asText());
        assertEquals(0, new java.math.BigDecimal("1850").compareTo(jdbc.queryForObject("select sum(amount) from fin_payable where source_type = 1 and source_id = ? and deleted_at is null", java.math.BigDecimal.class, poId)));
        JsonNode rrAfter = ok(call(get(RT + "/" + rid), admin));
        money("350", rrAfter.path("costCny"));
        money("45", rrAfter.path("freightCny"));

        // 闲置库存来源「售后退货」，来源单号为退货入库单；订单成本冲减 150 + 1 × 200、退回运费 45
        JsonNode hold = ok(call(json(post(HOLD + "/page"), write(Map.of("sourceType", 3))), admin)).path("records").get(0);
        assertEquals("售后退货", hold.path("sourceName").asText());
        assertEquals(rr.path("receiptNo").asText(), hold.path("returnNo").asText());
        money("200", hold.path("costPrice"));
        JsonNode cost = ok(call(get(SO + "/" + so), admin)).path("cost").path("lines").get(0);
        money("350", cost.path("salesReturnCredit"));
        money("45", cost.path("returnFreight"));
        money("1695", cost.path("cost"));
        JsonNode srcs = cost.path("sources").path("salesReturn");
        assertEquals(2, srcs.size(), "退回供应商与转闲置各一条");
        assertTrue(srcs.toString().contains(pr.path("returnNo").asText()) && srcs.toString().contains(rr.path("receiptNo").asText()), srcs.toString());

        // 退款申请：可申请 = 已到账 − 已退 − 待处理申请
        JsonNode rq = ok(apply(so, asId, "50", 3));
        assertEquals("待财务处理", rq.path("statusName").asText());
        assertEquals("最多还能申请 USD 450.00", fail(apply(so, asId, "460", 3)).path("message").asText());
        money("450", ok(call(get(RQ + "/refundable/" + so), admin)).path("available"));
        assertEquals(1, ok(call(get(RQ + "/counts"), admin)).path("pendingFinance").asInt());
        // 执行：生成退款单并关联售后单
        Map<String, Object> exe = new HashMap<>();
        exe.put("refundDate", LocalDate.now().toString());
        exe.put("amount", "60");
        exe.put("method", 3);
        assertEquals("执行金额不能超过申请金额 USD 50.00", fail(call(json(post(RQ + "/" + rq.path("id").asLong() + "/execute"), write(exe)), admin))
                .path("message").asText());
        exe.put("amount", "50");
        JsonNode executed = ok(call(json(post(RQ + "/" + rq.path("id").asLong() + "/execute"), write(exe)), admin));
        assertEquals("已执行", executed.path("statusName").asText());
        long refundId = executed.path("refundId").asLong();
        JsonNode refund = ok(call(get(REFUND + "/" + refundId), admin));
        assertEquals(a.path("asNo").asText(), refund.path("asNo").asText());
        assertEquals(rq.path("requestNo").asText(), refund.path("requestNo").asText());
        money("357.50", ok(call(get(AS + "/" + asId), admin)).path("refundCny"));
        // 作废退款单：申请回到待财务处理；财务驳回
        ok(call(json(post(REFUND + "/" + refundId + "/void"), "{\"reason\":\"去向选错\"}"), admin));
        assertEquals("待财务处理", ok(call(get(RQ + "/" + rq.path("id").asLong()), admin)).path("statusName").asText());
        assertEquals("请填写驳回原因", fail(call(json(post(RQ + "/" + rq.path("id").asLong() + "/reject"), "{\"reason\":\" \"}"), admin))
                .path("message").asText());
        JsonNode rejected = ok(call(json(post(RQ + "/" + rq.path("id").asLong() + "/reject"), "{\"reason\":\"客户改成换货\"}"), admin));
        assertEquals("已驳回", rejected.path("statusName").asText());
        assertEquals("退款申请已驳回，不能这样操作", fail(call(json(post(RQ + "/" + rq.path("id").asLong() + "/withdraw"), "{}"), admin))
                .path("message").asText());

        // 补寄运费费用单关联售后单
        Map<String, Object> fe = new HashMap<>();
        fe.put("name", "补寄运费");
        fe.put("occurDate", LocalDate.now().toString());
        fe.put("payeeType", 4);
        fe.put("payeeName", "顺丰");
        fe.put("currencyCode", "CNY");
        fe.put("amount", 120);
        fe.put("method", 1);
        fe.put("allocations", List.of(Map.of("soItemId", item, "amount", 120)));
        fe.put("afterSalesId", asId);
        JsonNode expense = ok(call(json(post(FE), write(fe)), admin));
        assertEquals(a.path("asNo").asText(), expense.path("asNo").asText());
        JsonNode detail = ok(call(get(AS + "/" + asId), admin));
        money("120", detail.path("expenseCny"));
        money("350", detail.path("returnCreditCny"));
        assertTrue(detail.path("logs").toString().contains("费用单"));
        assertEquals(1, ok(call(get(AS + "/order/" + so), admin)).size());
        assertEquals(1, detail.path("purchaseReturns").size());
        assertEquals(rr.path("receiptNo").asText(), detail.path("returns").get(0).path("receiptNo").asText());

        // 结案：原因不能是调查中；已有单据不能撤销
        assertEquals("结案时请确认最终原因与责任，不能是「调查中」", fail(call(json(post(AS + "/" + asId + "/close"),
                write(Map.of("reasonCode", "INVESTIGATING", "result", "换新"))), admin)).path("message").asText());
        assertEquals("已有销售退货单，不能撤销", fail(call(json(post(AS + "/" + asId + "/cancel"), "{\"reason\":\"误建\"}"), admin))
                .path("message").asText());
        // 案例沉淀选填，最多 500 字；结案后显示在详情和客户跟进时间线（第二期 tasks 4.4）
        assertEquals("案例沉淀不能超过 500 字", fail(call(json(post(AS + "/" + asId + "/close"), write(Map.of("reasonCode", "QUALITY",
                "result", "换新", "caseNote", "长".repeat(501)))), admin)).path("message").asText());
        JsonNode closed = ok(call(json(post(AS + "/" + asId + "/close"), write(Map.of("reasonCode", "QUALITY",
                "result", "供应商换新，客户确认正常", "caseNote", " 该型号运输需加泡沫护角 "))), admin));
        assertEquals("已结案", closed.path("statusName").asText());
        assertEquals("该型号运输需加泡沫护角", closed.path("caseNote").asText());
        // 售后单号每天从 001 起，限定本单客户，避免同一天重跑时数到之前留下的记录
        assertEquals(1, jdbc.queryForObject("select count(*) from customer_followup where record_type = 2 and title like ?"
                + " and raw_content = '案例沉淀：该型号运输需加泡沫护角'"
                + " and customer_id = (select customer_id from sales_order where id = ?)",
                Integer.class, "售后单 % 结案 · 供应商换新，客户确认正常", so));
        assertFalse(closed.path("overdue").asBoolean());
        assertEquals("售后单已结案，只能添加跟进记录", fail(plan(asId, Map.of("reasonCode", "TRANSPORT"))).path("message").asText());
        ok(call(json(post(AS + "/" + asId + "/notes"), write(Map.of("content", "客户回访正常"))), admin));
        assertEquals(1, ok(call(get(AS + "/counts"), admin)).path("closedThisMonth").asInt());
        assertEquals("售后单已结案，不能发起退货", fail(newReturn(asId, item, 1)).path("message").asText());
    }

    @Test
    void approvalSwitch_withdraw_noOrderAfterSales_cancel_voidReturn() throws Exception {
        long sup = supplier("华东工控", null);
        long so = shippedOrder(sup, "500");
        long asId = ok(create(so, 1, "PENDING")).path("id").asLong();

        // 打开审批：提交后待审批，审批通过后待财务处理；审批人驳回
        jdbc.update("update sys_config set config_value = 'true' where tenant_id = 0 and config_key = 'finance.refund-request.approval'");
        assertTrue(ok(call(get(RQ + "/refundable/" + so), admin)).path("approvalRequired").asBoolean());
        JsonNode q1 = ok(apply(so, asId, "30", 2));
        assertEquals("待审批", q1.path("statusName").asText());
        assertEquals("退款申请待审批，不能这样操作", fail(call(json(post(RQ + "/" + q1.path("id").asLong() + "/reject"), "{\"reason\":\"x\"}"), admin))
                .path("message").asText());
        assertEquals("待财务处理", ok(call(json(post(RQ + "/" + q1.path("id").asLong() + "/approve"), "{}"), admin)).path("statusName").asText());
        JsonNode q2 = ok(apply(so, asId, "20", 2));
        assertEquals("已驳回", ok(call(json(post(RQ + "/" + q2.path("id").asLong() + "/reject-approval"), "{\"reason\":\"先换货\"}"), admin))
                .path("statusName").asText());
        // 撤回
        assertEquals("已撤回", ok(call(json(post(RQ + "/" + q1.path("id").asLong() + "/withdraw"), "{}"), admin)).path("statusName").asText());
        assertEquals(2, ok(call(json(post(RQ + "/page"), write(Map.of("keyword", ok(call(get(AS + "/" + asId), admin)).path("asNo").asText()))), admin))
                .path("total").asInt());

        // 退货单作废后可以撤销售后单？有撤回的申请也不算单据，但作废的退货单不阻挡
        long rid = ok(newReturn(asId, soItem(so), 1)).path("id").asLong();
        assertEquals("已作废", ok(call(json(post(TH + "/" + rid + "/void"), "{\"reason\":\"客户不寄了\"}"), admin)).path("statusName").asText());
        assertEquals("已有退款申请，不能撤销", fail(call(json(post(AS + "/" + asId + "/cancel"), "{\"reason\":\"误建\"}"), admin))
                .path("message").asText());

        // 没有订单的售后单：手填型号，不能发起退货；没有单据可以撤销
        long customer = jdbc.queryForObject("select customer_id from sales_order where id = ?", Long.class, so);
        Map<String, Object> b = new HashMap<>();
        b.put("customerId", customer);
        b.put("items", List.of(Map.of("model", "MR-J4-70A", "brand", "MITSUBISHI", "quantity", 1)));
        b.put("symptom", "参数怎么设置");
        b.put("levelCode", "S5");
        JsonNode noOrder = ok(call(json(post(AS), write(b)), admin));
        assertTrue(noOrder.path("soId").isNull() || noOrder.path("soId").isMissingNode());
        assertEquals("售后单没有关联订单，不能发起退货", fail(call(json(post(TH), write(Map.of("afterSalesId", noOrder.path("id").asLong(),
                "handling", 1, "items", List.of(Map.of("soItemId", soItem(so), "quantity", 1))))), admin)).path("message").asText());
        JsonNode cancelled = ok(call(json(post(AS + "/" + noOrder.path("id").asLong() + "/cancel"), "{\"reason\":\"重复登记\"}"), admin));
        assertEquals("已撤销", cancelled.path("statusName").asText());
        assertEquals(1, ok(call(json(post(AS + "/page"), write(Map.of("status", 4))), admin)).path("total").asInt());
        assertEquals("订单不属于这个客户", fail(call(json(post(AS), write(Map.of("customerId", customer("Other", "US"), "soId", so,
                "symptom", "x"))), admin)).path("message").asText());
    }

    @Test
    void disposeToShop_whenOriginPurchaseOrderIsOnlineShop() throws Exception {
        long sup = supplier("苏州伺服", null);
        long so = shippedOrder(sup, "500");
        long item = soItem(so);
        long asId = ok(create(so, 1, "S2")).path("id").asLong();
        JsonNode rr = confirmReturn(ok(newReturn(asId, item, 1)).path("id").asLong());
        long rid = rr.path("id").asLong();
        long rItem = rr.path("items").get(0).path("id").asLong();
        long poId = rr.path("items").get(0).path("poId").asLong();
        // 原采购订单改成线上店铺采购：没有供应商（supplier_id 为 0），显示店铺名
        jdbc.update("update purchase_order set supplier_id = 0, channel = 1, shop_name = '瓦韦尔电气' where id = ?", poId);
        ok(call(json(post(RT + "/" + rid + "/receive"), write(Map.of("receivedDate", LocalDate.now().toString(),
                "items", List.of(Map.of("itemId", rItem, "receivedQty", 1, "qualifiedQty", 1))))), admin));
        JsonNode got = ok(call(get(RT + "/" + rid), admin)).path("items").get(0);
        assertEquals(0, got.path("supplierId").asLong());
        assertEquals("淘宝 · 瓦韦尔电气", got.path("supplierName").asText());

        String d = RT + "/" + rid + "/dispose";
        assertEquals("供应商不存在", fail(call(json(post(d), write(Map.of("lines", List.of(Map.of("itemId", rItem, "disposition", 1,
                "quantity", 1, "supplierId", 0))))), admin)).path("message").asText(), "没有店铺采购订单时不能退回供应商 0");
        JsonNode done = ok(call(json(post(d), write(Map.of("lines", List.of(Map.of("itemId", rItem, "disposition", 1,
                "quantity", 1, "supplierId", 0, "poId", poId))))), admin));
        assertEquals("淘宝 · 瓦韦尔电气", done.path("dispositions").get(0).path("supplierName").asText());
        JsonNode pr = ok(call(get(PRS + "/" + done.path("purchaseReturns").get(0).path("id").asLong()), admin));
        assertEquals("淘宝 · 瓦韦尔电气", pr.path("supplierName").asText());
        assertEquals(poId, pr.path("poId").asLong());

        // 采购退货单没完成不能结案
        assertEquals("采购退货单 " + pr.path("returnNo").asText() + " 还没完成", fail(call(json(post(AS + "/" + asId + "/close"),
                write(Map.of("reasonCode", "QUALITY", "result", "退回店铺"))), admin)).path("message").asText());
        // 退款申请分项合计要等于申请金额
        Map<String, Object> b = new HashMap<>(Map.of("soId", so, "afterSalesId", asId, "amount", 10, "reason", "QUALITY", "method", 2,
                "lines", List.of(Map.of("itemName", "补偿", "amount", 6), Map.of("itemName", "运费", "amount", 3))));
        assertEquals("分项合计要等于申请金额 USD 10.00", fail(call(json(post(RQ), write(b)), admin)).path("message").asText());
        // 没有已出运的数量不能退货
        jdbc.update("update outbound_order_item set quantity = 0 where so_item_id = ?", item);
        assertEquals("A-1 还没有可以退的数量（已出运的才能退）", fail(newReturn(asId, item, 1)).path("message").asText());
    }

    @Test
    void returnRefund_pendingReceipt_refundFirst_purchaseReturnQtyAndVoid() throws Exception {
        long sup = supplier("苏州伺服", null);
        long so = shippedOrder(sup, "500");
        long item = soItem(so);
        long asId = ok(create(so, 2, "S2")).path("id").asLong();

        // 退货退款：确认时按退货数量 × 成交单价生成退款申请（待验收，财务不能执行）
        JsonNode th = ok(call(json(post(TH), write(Map.of("afterSalesId", asId, "handling", 2, "items",
                List.of(Map.of("soItemId", item, "quantity", 2))))), admin));
        long thId = th.path("id").asLong();
        assertEquals("退货退款", th.path("handlingName").asText());
        assertEquals("请填写退款信息", fail(call(json(post(TH + "/" + thId + "/confirm"), "{}"), admin)).path("message").asText());
        JsonNode confirmed = ok(call(json(post(TH + "/" + thId + "/confirm"), write(Map.of("refund", Map.of("method", 3)))), admin));
        long rqId = confirmed.path("refunds").get(0).path("id").asLong();
        JsonNode rq = ok(call(get(RQ + "/" + rqId), admin));
        assertEquals("待验收", rq.path("statusName").asText());
        money("100", rq.path("amount"));
        assertEquals(th.path("returnNo").asText(), rq.path("sourceNo").asText());
        assertEquals(1, rq.path("items").size());
        assertEquals("A-1", rq.path("items").get(0).path("itemName").asText());
        money("50", rq.path("items").get(0).path("unitPrice"));
        assertEquals(1, ok(call(get(RQ + "/counts"), admin)).path("pendingReceipt").asInt());
        Map<String, Object> exe = new HashMap<>(Map.of("refundDate", LocalDate.now().toString(), "amount", "100"));
        assertEquals("退款申请待验收，不能这样操作", fail(call(json(post(RQ + "/" + rqId + "/execute"), write(exe)), admin))
                .path("message").asText());
        assertEquals("销售退货单 " + th.path("returnNo").asText() + " 待入库", fail(call(json(post(AS + "/" + asId + "/close"),
                write(Map.of("reasonCode", "QUALITY", "result", "退款"))), admin)).path("message").asText());

        // 验收（实收少于退货数量）后进入待财务处理并提示；撤回入库回到待验收
        long rid = confirmed.path("receipt").path("id").asLong();
        JsonNode rr = ok(call(get(RT + "/" + rid), admin));
        long rItem = rr.path("items").get(0).path("id").asLong();
        Map<String, Object> rec = new HashMap<>(Map.of("receivedDate", LocalDate.now().toString(),
                "items", List.of(Map.of("itemId", rItem, "receivedQty", 1, "qualifiedQty", 1))));
        ok(call(json(post(RT + "/" + rid + "/receive"), write(rec)), admin));
        rq = ok(call(get(RQ + "/" + rqId), admin));
        assertEquals("待财务处理", rq.path("statusName").asText());
        assertEquals("实收 1 个，少于退货数量 2 个", rq.path("receivedHint").asText());
        ok(call(json(post(RT + "/" + rid + "/unreceive"), "{\"reason\":\"数错了\"}"), admin));
        assertEquals("待验收", ok(call(get(RQ + "/" + rqId), admin)).path("statusName").asText());
        ok(call(json(post(RT + "/" + rid + "/receive"), write(rec)), admin));
        assertEquals("已执行", ok(call(json(post(RQ + "/" + rqId + "/execute"), write(exe)), admin)).path("statusName").asText());

        // 先退款：确认后直接待财务处理，逐行改金额；作废销售退货单时撤回申请、作废退货入库单
        JsonNode th2 = ok(call(json(post(TH), write(Map.of("afterSalesId", asId, "handling", 2, "refundFirst", true, "items",
                List.of(Map.of("soItemId", item, "quantity", 1))))), admin));
        long th2Item = th2.path("items").get(0).path("id").asLong();
        JsonNode c2 = ok(call(json(post(TH + "/" + th2.path("id").asLong() + "/confirm"), write(Map.of("refund", Map.of("method", 3,
                "lines", List.of(Map.of("returnItemId", th2Item, "amount", "40")))))), admin));
        long rq2 = c2.path("refunds").get(0).path("id").asLong();
        assertEquals("待财务处理", ok(call(get(RQ + "/" + rq2), admin)).path("statusName").asText());
        money("40", ok(call(get(RQ + "/" + rq2), admin)).path("amount"));
        ok(call(json(post(TH + "/" + th2.path("id").asLong() + "/void"), "{\"reason\":\"客户不退了\"}"), admin));
        assertEquals("已撤回", ok(call(get(RQ + "/" + rq2), admin)).path("statusName").asText());
        assertEquals("已作废", ok(call(get(RT + "/" + c2.path("receipt").path("id").asLong()), admin)).path("statusName").asText());

        // 采购退货单：只能减少数量，减少的回到退货入库单待处理；作废后全部回到待处理
        JsonNode th3 = ok(newReturn(asId, item, 2));
        JsonNode rr3 = confirmReturn(th3.path("id").asLong());
        long rid3 = rr3.path("id").asLong();
        long rItem3 = rr3.path("items").get(0).path("id").asLong();
        ok(call(json(post(RT + "/" + rid3 + "/receive"), write(Map.of("receivedDate", LocalDate.now().toString(),
                "items", List.of(Map.of("itemId", rItem3, "receivedQty", 2, "qualifiedQty", 0))))), admin));
        JsonNode disposed = ok(call(json(post(RT + "/" + rid3 + "/dispose"), write(Map.of("lines", List.of(Map.of("itemId", rItem3,
                "disposition", 1, "quantity", 2, "supplierId", sup))))), admin));
        long prId = disposed.path("purchaseReturns").get(0).path("id").asLong();
        long prItem = ok(call(get(PRS + "/" + prId), admin)).path("items").get(0).path("id").asLong();
        assertEquals("A-1 只能减少，不能多于 2 个", fail(call(json(put(PRS + "/" + prId + "/quantities"),
                write(Map.of("items", List.of(Map.of("itemId", prItem, "quantity", 3))))), admin)).path("message").asText());
        ok(call(json(put(PRS + "/" + prId + "/quantities"), write(Map.of("items", List.of(Map.of("itemId", prItem, "quantity", 1))))), admin));
        JsonNode back = ok(call(get(RT + "/" + rid3), admin));
        assertEquals("待处理", back.path("statusName").asText());
        assertEquals(1, back.path("items").get(0).path("disposedQty").asInt());
        ok(call(json(post(PRS + "/" + prId + "/void"), "{\"reason\":\"供应商不收\"}"), admin));
        assertEquals(0, ok(call(get(RT + "/" + rid3), admin)).path("items").get(0).path("disposedQty").asInt());
        assertEquals("已作废", ok(call(get(PRS + "/" + prId), admin)).path("statusName").asText());
    }

    /** 手动订单（没有采购订单）：A-9 × 4 售价 USD 100、采购成本价 ¥300，已到账 400，全部出运；返回订单 ID */
    private long manualShipped(long customer) throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("customerId", customer);
        m.put("items", List.of(Map.of("productId", manualProduct("A-9"), "quantity", 4, "unitPrice", "100", "costPrice", "300"),
                Map.of("productId", manualProduct("B-9"), "quantity", 2, "unitPrice", "50")));
        long so = ok(call(json(post(SO), write(m)), admin)).path("id").asLong();
        Map<String, Object> r = new HashMap<>();
        r.put("amount", "500");
        r.put("receiptDate", LocalDate.now().toString());
        r.put("bankAccountId", jdbc.queryForObject("select min(id) from tenant_bank_account where tenant_id = 0", Integer.class));
        ok(call(json(post(SO + "/" + so + "/receipts"), write(r)), admin));
        long item = soItem(so);
        jdbc.update("insert into logistics_shipment (tenant_id, sh_no, status) values (0, ?, 2)", "ITSH" + System.nanoTime());
        long ls = jdbc.queryForObject("select max(id) from logistics_shipment where tenant_id = 0", Long.class);
        jdbc.update("insert into outbound_order (tenant_id, ob_no, so_id, status, logistics_id) values (0, ?, ?, 3, ?)", "ITOBM" + so, so, ls);
        long ob = jdbc.queryForObject("select max(id) from outbound_order where tenant_id = 0", Long.class);
        jdbc.update("insert into outbound_order_item (tenant_id, outbound_id, so_item_id, quantity) values (0, ?, ?, 4)", ob, item);
        long item2 = jdbc.queryForObject("select id from sales_order_item where so_id = ? order by line_no desc limit 1", Long.class, so);
        jdbc.update("insert into outbound_order_item (tenant_id, outbound_id, so_item_id, quantity) values (0, ?, ?, 2)", ob, item2);
        return so;
    }

    private String msg(JsonNode res) {
        return fail(res).path("message").asText();
    }

    @Test
    void validations_lists_noPurchaseOrderCost_dataScope() throws Exception {
        bankAccount("USD");
        long c = customer("Pacific Controls", "Australia");
        long so = manualShipped(c);
        long item = soItem(so);

        // ---- 售后单登记的校验
        Map<String, Object> b = new HashMap<>();
        b.put("customerId", 999999999L);
        b.put("symptom", "x");
        assertEquals("客户不存在", msg(call(json(post(AS), write(b)), admin)));
        b.put("customerId", c);
        b.put("soId", so);
        b.put("items", List.of(Map.of("soItemId", 999999999L, "quantity", 1)));
        assertEquals("型号不属于这张订单", msg(call(json(post(AS), write(b)), admin)));
        b.put("items", List.of(Map.of("soItemId", item, "quantity", 1), Map.of("soItemId", item, "quantity", 1)));
        assertEquals("同一个型号不能重复填写", msg(call(json(post(AS), write(b)), admin)));
        b.remove("soId");
        b.put("items", List.of(Map.of("model", " ", "quantity", 1)));
        assertEquals("请填写型号", msg(call(json(post(AS), write(b)), admin)));
        b.put("items", List.of(Map.of("model", "X-1", "quantity", 1)));
        b.put("levelCode", "S9");
        assertEquals("问题等级不正确", msg(call(json(post(AS), write(b)), admin)));
        b.put("levelCode", "S4");
        b.put("ownerId", 123456789L);
        assertEquals("负责人不存在", msg(call(json(post(AS), write(b)), admin)));

        // 数量为 0 的行跳过；指定负责人
        b.put("soId", so);
        b.put("ownerId", ADMIN_USER);
        b.put("items", List.of(Map.of("soItemId", item, "quantity", 2), Map.of("model", "跳过", "quantity", 0)));
        JsonNode a = ok(call(json(post(AS), write(b)), admin));
        long asId = a.path("id").asLong();
        assertEquals(1, a.path("items").size());

        // 处理：方案编码不正确、改负责人、状态不正确
        assertEquals("处理方案不正确", msg(plan(asId, Map.of("solutions", List.of("NOPE")))));
        assertTrue(ok(plan(asId, Map.of("ownerId", BUYER_LIN))).path("logs").toString().contains("负责人"));
        assertEquals("状态不正确", msg(call(json(post(AS + "/" + asId + "/status"), write(Map.of("status", 3))), admin)));

        // ---- 销售退货单的校验
        assertEquals("请从售后单发起退货", msg(call(json(post(TH), write(Map.of("handling", 1, "items",
                List.of(Map.of("soItemId", item, "quantity", 1))))), admin)));
        assertEquals("请选择处理方式", msg(call(json(post(TH), write(Map.of("afterSalesId", asId, "items",
                List.of(Map.of("soItemId", item, "quantity", 1))))), admin)));
        assertEquals("型号不属于这张订单", msg(newReturn(asId, 999999999L, 1)));
        assertEquals("同一个型号不能重复填写", msg(call(json(post(TH), write(Map.of("afterSalesId", asId, "handling", 1, "items",
                List.of(Map.of("soItemId", item, "quantity", 1), Map.of("soItemId", item, "quantity", 1))))), admin)));
        assertEquals("请填写退回数量", msg(newReturn(asId, item, 0)));
        JsonNode r = ok(newReturn(asId, item, 3));
        long thId = r.path("id").asLong();
        assertEquals("请填写作废原因", msg(call(json(post(TH + "/" + thId + "/void"), "{\"reason\":\" \"}"), admin)));
        JsonNode rr = confirmReturn(thId);
        long rid = rr.path("id").asLong();
        long rItem = rr.path("items").get(0).path("id").asLong();
        assertEquals("退货入库单还没入库", msg(call(json(post(RT + "/" + rid + "/unreceive"), "{\"reason\":\"x\"}"), admin)));
        assertEquals("请填写撤回原因", msg(call(json(post(RT + "/" + rid + "/unreceive"), "{\"reason\":\"\"}"), admin)));
        assertEquals("退货入库单还没验收", msg(call(json(post(RT + "/" + rid + "/dispose"),
                write(Map.of("lines", List.of(Map.of("itemId", rItem, "disposition", 3, "quantity", 3))))), admin)));

        Map<String, Object> rec = new HashMap<>();
        rec.put("receivedDate", LocalDate.now().plusDays(1).toString());
        rec.put("items", List.of(Map.of("itemId", rItem, "receivedQty", 3, "qualifiedQty", 2)));
        assertEquals("收货日期不能晚于今天", msg(call(json(post(RT + "/" + rid + "/receive"), write(rec)), admin)));
        rec.put("receivedDate", LocalDate.now().toString());
        rec.put("items", List.of(Map.of("itemId", 999999999L, "receivedQty", 1, "qualifiedQty", 1)));
        assertEquals("退货行不正确", msg(call(json(post(RT + "/" + rid + "/receive"), write(rec)), admin)));
        rec.put("items", List.of());
        assertTrue(call(json(post(RT + "/" + rid + "/receive"), write(rec)), admin).path("code").asInt() != 0, "必须填验收数量");
        rec.put("items", List.of(Map.of("itemId", rItem, "receivedQty", -1, "qualifiedQty", 0)));
        assertEquals("A-9 的数量不能为负数", msg(call(json(post(RT + "/" + rid + "/receive"), write(rec)), admin)));
        rec.put("items", List.of(Map.of("itemId", rItem, "receivedQty", 4, "qualifiedQty", 0)));
        assertEquals("A-9 实收不能多于退货数量 3", msg(call(json(post(RT + "/" + rid + "/receive"), write(rec)), admin)));
        rec.put("items", List.of(Map.of("itemId", rItem, "receivedQty", 0, "qualifiedQty", 0)));
        assertEquals("实收数量都是 0，客户还没寄回时请不要验收", msg(call(json(post(RT + "/" + rid + "/receive"), write(rec)), admin)));
        rec.put("items", List.of(Map.of("itemId", rItem, "receivedQty", 3, "qualifiedQty", 2)));
        JsonNode got = ok(call(json(post(RT + "/" + rid + "/receive"), write(rec)), admin));
        // 没有采购订单：原采购成本取订单型号的采购成本价
        money("300", got.path("items").get(0).path("unitCost"));
        assertTrue(got.path("items").get(0).path("supplierId").isNull() || got.path("items").get(0).path("supplierId").isMissingNode());
        assertEquals("退货入库单已验收", msg(call(json(post(RT + "/" + rid + "/receive"), write(rec)), admin)));
        assertEquals("销售退货单已入库，不能修改", msg(call(json(put(TH + "/" + thId), write(Map.of("items",
                List.of(Map.of("soItemId", item, "quantity", 1))))), admin)));
        assertEquals("已入库的退货单不能作废", msg(call(json(post(TH + "/" + thId + "/void"), "{\"reason\":\"x\"}"), admin)));

        // 去向校验
        String d = RT + "/" + rid + "/dispose";
        assertEquals("退货行不正确", msg(call(json(post(d), write(Map.of("lines", List.of(Map.of("itemId", 999999999L, "disposition", 3,
                "quantity", 3))))), admin)));
        assertEquals("去向不正确", msg(call(json(post(d), write(Map.of("lines", List.of(Map.of("itemId", rItem, "disposition", 9,
                "quantity", 3))))), admin)));
        assertEquals("A-9 的数量不能为负数", msg(call(json(post(d), write(Map.of("lines", List.of(Map.of("itemId", rItem, "disposition", 3,
                "quantity", -1))))), admin)));
        assertEquals("退回供应商时请选择供应商", msg(call(json(post(d), write(Map.of("lines", List.of(Map.of("itemId", rItem,
                "disposition", 1, "quantity", 1))))), admin)));
        assertEquals("请填写去向数量", msg(call(json(post(d), write(Map.of("lines", List.of(Map.of("itemId", rItem,
                "disposition", 3, "quantity", 0))))), admin)));
        assertEquals("供应商不存在", msg(call(json(post(d), write(Map.of("lines", List.of(
                Map.of("itemId", rItem, "disposition", 1, "quantity", 1, "supplierId", 999999999L),
                Map.of("itemId", rItem, "disposition", 2, "quantity", 2))))), admin)));
        // 转闲置 2 个按 ¥300 冲减；报废 1 个带原因；数量为 0 的行跳过
        JsonNode done = ok(call(json(post(d), write(Map.of("lines", List.of(
                Map.of("itemId", rItem, "disposition", 2, "quantity", 2),
                Map.of("itemId", rItem, "disposition", 3, "quantity", 1, "remark", "烧坏"),
                Map.of("itemId", rItem, "disposition", 1, "quantity", 0, "supplierId", 1))))), admin));
        money("600", done.path("costCny"));
        assertEquals(2, done.path("dispositions").size());
        assertTrue(done.path("dispositions").toString().contains("烧坏"));
        assertEquals("退货已处理完", msg(call(json(post(d), write(Map.of("lines", List.of(Map.of("itemId", rItem, "disposition", 3,
                "quantity", 3))))), admin)));
        assertEquals("退货入库单不存在", msg(call(get(RT + "/999999999"), admin)));
        assertEquals("销售退货单不存在", msg(call(get(TH + "/999999999"), admin)));

        // ---- 退货入库列表、计数，售后单的销售退货单
        long r2 = ok(newReturn(asId, item, 1)).path("id").asLong();
        long rr2 = confirmReturn(r2).path("id").asLong();
        assertEquals(1, ok(call(get(RT + "/counts"), admin)).path("pending").asInt());
        assertEquals(2, ok(call(json(post(RT + "/page"), write(Map.of("keyword", a.path("asNo").asText()))), admin)).path("total").asInt());
        String soNo = jdbc.queryForObject("select so_no from sales_order where id = ?", String.class, so);
        assertEquals(1, ok(call(json(post(RT + "/page"), write(Map.of("keyword", soNo, "status", 3))), admin)).path("total").asInt());
        assertEquals(0, ok(call(json(post(RT + "/page"), write(Map.of("keyword", "不存在的单号"))), admin)).path("total").asInt());
        assertEquals(2, ok(call(json(post(TH + "/page"), write(Map.of("keyword", soNo))), admin)).path("total").asInt());
        assertEquals(2, ok(call(get(AS + "/" + asId + "/returns"), admin)).size());
        // 作废销售退货单：待入库的退货入库单随之作废
        ok(call(json(post(TH + "/" + r2 + "/void"), "{\"reason\":\"客户不寄了\"}"), admin));
        assertEquals("已作废", ok(call(get(RT + "/" + rr2), admin)).path("statusName").asText());
        assertEquals("退货入库单已作废", msg(call(json(post(RT + "/" + rr2 + "/receive"), write(rec)), admin)));

        // ---- 售后单列表的筛选、负责人候选
        assertTrue(ok(call(get(AS + "/owners"), admin)).size() > 0);
        assertEquals(1, ok(call(json(post(AS + "/page"), write(Map.of("customerId", c, "levelCode", "S4", "ownerId", BUYER_LIN,
                "keyword", soNo))), admin)).path("total").asInt());
        assertEquals(0, ok(call(json(post(AS + "/page"), write(Map.of("levelCode", "S1"))), admin)).path("total").asInt());

        // ---- 退款申请的校验
        assertTrue(ok(call(get(RQ + "/order/" + so), admin)).isEmpty());
        assertEquals("退款原因不存在", msg(call(json(post(RQ), write(Map.of("soId", so, "amount", "10", "reason", "NOPE", "method", 3))), admin)));
        assertEquals("退款去向不正确", msg(call(json(post(RQ), write(Map.of("soId", so, "amount", "10", "reason", "QUALITY", "method", 9))), admin)));
        assertEquals("销售订单不存在", msg(call(get(RQ + "/refundable/999999999"), admin)));
        assertEquals("退款申请不存在", msg(call(get(RQ + "/999999999"), admin)));
        // 从订单详情提出（不挂售后单），按订单列出；执行时必须填退款日期、去向默认按申请
        JsonNode q = ok(apply(so, null, "40", 3));
        assertEquals(1, ok(call(get(RQ + "/order/" + so), admin)).size());
        Map<String, Object> exe = new HashMap<>();
        exe.put("amount", "40");
        assertEquals("请填写退款日期", msg(call(json(post(RQ + "/" + q.path("id").asLong() + "/execute"), write(exe)), admin)));
        exe.put("refundDate", LocalDate.now().toString());
        JsonNode executed = ok(call(json(post(RQ + "/" + q.path("id").asLong() + "/execute"), write(exe)), admin));
        assertEquals("已执行", executed.path("statusName").asText());
        assertEquals(1, ok(call(json(post(RQ + "/page"), write(Map.of("status", 3, "keyword", soNo))), admin)).path("total").asInt());
        assertEquals(0, ok(call(json(post(RQ + "/page"), write(Map.of("keyword", "不存在的单号"))), admin)).path("total").asInt());

        // 有待处理申请时不能结案；撤回后结案；已结案不能再申请
        JsonNode q2 = ok(apply(so, asId, "10", 3));
        assertEquals("退款申请 " + q2.path("requestNo").asText() + " 还没处理完", msg(call(json(post(AS + "/" + asId + "/close"),
                write(Map.of("reasonCode", "QUALITY", "result", "好了"))), admin)));
        ok(call(json(post(RQ + "/" + q2.path("id").asLong() + "/withdraw"), "{}"), admin));
        ok(call(json(post(AS + "/" + asId + "/close"), write(Map.of("reasonCode", "QUALITY", "result", "好了"))), admin));
        assertEquals("售后单已结案", msg(apply(so, asId, "5", 3)));

        // ---- 撤销：只有退款或费用也不能撤销；撤销后不能再关联、不能再改
        b.put("items", List.of(Map.of("soItemId", item, "quantity", 1)));
        long as2 = ok(call(json(post(AS), write(b)), admin)).path("id").asLong();
        assertEquals("请填写撤销原因", msg(call(json(post(AS + "/" + as2 + "/cancel"), "{\"reason\":\"\"}"), admin)));
        Map<String, Object> fe = new HashMap<>();
        fe.put("name", "检测费");
        fe.put("occurDate", LocalDate.now().toString());
        fe.put("payeeType", 4);
        fe.put("payeeName", "检测站");
        fe.put("currencyCode", "CNY");
        fe.put("amount", 50);
        fe.put("method", 1);
        fe.put("allocations", List.of(Map.of("soItemId", item, "amount", 50)));
        fe.put("afterSalesId", as2);
        long feId = ok(call(json(post(FE), write(fe)), admin)).path("id").asLong();
        assertEquals("已有关联的退款或费用，不能撤销", msg(call(json(post(AS + "/" + as2 + "/cancel"), "{\"reason\":\"误建\"}"), admin)));
        ok(call(json(post(FE + "/" + feId + "/void"), "{\"reason\":\"录错\"}"), admin));
        ok(call(json(post(AS + "/" + as2 + "/cancel"), "{\"reason\":\"误建\"}"), admin));
        assertEquals("售后单已撤销", msg(plan(as2, Map.of("nextAction", "x"))));
        fe.put("afterSalesId", as2);
        assertEquals("售后单已撤销", msg(call(json(post(FE), write(fe)), admin)));
        // 退款单关联别的订单的售后单
        long other = manualShipped(c);
        Map<String, Object> refund = new HashMap<>();
        refund.put("soId", other);
        refund.put("refundDate", LocalDate.now().toString());
        refund.put("reason", "QUALITY");
        refund.put("amount", "10");
        refund.put("method", 3);
        refund.put("afterSalesId", asId);
        assertEquals("售后单不属于这张订单", msg(call(json(post(REFUND), write(refund)), admin)));

        // 撤销的售后单不能关联退款单；没有退货的售后单、没有售后单的订单列表为空
        refund.put("soId", so);
        refund.put("afterSalesId", as2);
        assertEquals("售后单已撤销", msg(call(json(post(REFUND), write(refund)), admin)));
        assertTrue(ok(call(get(AS + "/" + as2 + "/returns"), admin)).isEmpty());
        assertTrue(ok(call(get(AS + "/order/" + other), admin)).isEmpty());
        assertEquals("售后单不存在", msg(call(get(AS + "/999999999"), admin)));
        // 两个型号的退货单：验收要填每个型号
        long item2 = jdbc.queryForObject("select id from sales_order_item where so_id = ? order by line_no desc limit 1", Long.class, other);
        b.put("soId", other);
        b.put("items", List.of(Map.of("soItemId", soItem(other), "quantity", 1), Map.of("soItemId", item2, "quantity", 1)));
        long as3 = ok(call(json(post(AS), write(b)), admin)).path("id").asLong();
        JsonNode twoTh = ok(call(json(post(TH), write(Map.of("afterSalesId", as3, "handling", 1, "items",
                List.of(Map.of("soItemId", soItem(other), "quantity", 1), Map.of("soItemId", item2, "quantity", 1))))), admin));
        JsonNode two = confirmReturn(twoTh.path("id").asLong());
        rec.put("items", List.of(Map.of("itemId", two.path("items").get(0).path("id").asLong(), "receivedQty", 1, "qualifiedQty", 1)));
        assertEquals("请填写每个型号的验收数量", msg(call(json(post(RT + "/" + two.path("id").asLong() + "/receive"), write(rec)), admin)));
        ok(call(json(post(TH + "/" + twoTh.path("id").asLong() + "/void"), "{\"reason\":\"重复\"}"), admin));
        // 平台收款的订单只能原路退回
        jdbc.update("update payment_receipt set channel = 2 where so_id = ?", other);
        assertEquals("平台收款的订单只能原路退回", msg(apply(other, null, "10", 3)));

        // 订单取消后不能登记售后、不能申请退款
        ok(call(json(post(SO + "/" + other + "/cancel"), "{\"reason\":\"客户不要了\"}"), admin));
        assertEquals("订单已取消，不能申请退款", msg(apply(other, null, "10", 1)));
        b.put("soId", other);
        b.put("items", List.of());
        assertEquals("订单已取消，不能登记售后", msg(call(json(post(AS), write(b)), admin)));

        // 订单被删除后不能再发起退货
        jdbc.update("update sales_order set deleted_at = NOW() where id = ?", other);
        assertEquals("销售订单不存在", msg(newReturn(as3, soItem(other), 1)));

        // ---- 数据范围：负责人与订单业务员都不在范围内的售后单看不到
        jdbc.update("update after_sales set owner_id = ? where id = ?", BUYER_LIN, as2);
        jdbc.update("update sales_order set owner_id = ? where id = ?", BUYER_LIN, so);
        loginWithResources("it_as_staff", 100120, 100079);
        String staff = token("it_as_staff");
        assertEquals("售后单不存在", call(get(AS + "/" + as2), staff).path("message").asText());
        assertEquals(0, ok(call(json(post(AS + "/page"), "{\"status\":4}"), staff)).path("total").asInt());
    }
}

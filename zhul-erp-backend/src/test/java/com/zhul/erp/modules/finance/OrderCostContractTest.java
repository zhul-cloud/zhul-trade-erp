package com.zhul.erp.modules.finance;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.modules.sales.SalesContractSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** spec finance/order-cost、finance/profit-stats、purchase/bargain-stats 与 sales/sales-order「订单毛利」 */
class OrderCostContractTest extends SalesContractSupport {

    private static final String SO = "/api/v1/sales/orders";
    private static final String FX = "/api/v1/finance/fx-settlements";

    private int cnyBank;

    @BeforeEach
    void banks() {
        bankAccount("USD");
        jdbc.update("insert into tenant_bank_account (tenant_id, currency_code, bank_name, account_name, account_no, is_default) "
                + "values (0, 'CNY', '招商银行', '基本户', '6225888866886688', 0)");
        cnyBank = jdbc.queryForObject("select max(id) from tenant_bank_account where tenant_id = 0", Integer.class);
    }

    /** 报价 → PI；型号成本价 1000，指定供应商回价为采购成本价 */
    private long pi(long supplierId, String model, String price) throws Exception {
        long c = customer("Surya Prima Mandiri CV", "Indonesia");
        List<Long> items = quotationItemIds(quotation(c, 2, "USD", null, l(model, 1, "1000", price)));
        for (Long i : items) {
            long inquiryItem = jdbc.queryForObject("select inquiry_item_id from quotation_item where id = ?", Long.class, i);
            jdbc.update("insert into sourcing_quote (tenant_id, inquiry_item_id, quoted_by, supplier_id, channel, shop_name) values (0, ?, ?, ?, 4, '')",
                    inquiryItem, BUYER_LIN, supplierId);
            jdbc.update("update quotation_item set cost_quote_id = (select max(id) from sourcing_quote where inquiry_item_id = ?) where id = ?",
                    inquiryItem, i);
        }
        return sentPi(items, null);
    }

    private long convert(long pi) throws Exception {
        JsonNode so = ok(call(json(post(PI + "/" + pi + "/convert"), "{}"), admin));
        return so.path("id").isMissingNode() ? jdbc.queryForObject("select max(id) from sales_order where tenant_id = 0", Long.class)
                : so.path("id").asLong();
    }

    /** 草稿采购订单填单价、开票 3%、确认下单 */
    private long orderPo(long supplierId, String price) throws Exception {
        long draft = jdbc.queryForObject("select max(id) from purchase_order where tenant_id = 0 and supplier_id = ? and status = 1 "
                + "and deleted_at is null", Long.class, supplierId);
        Map<String, Object> body = poBody(po(draft, admin), price);
        body.put("invoiceRequired", true);
        body.put("taxPoint", 3);
        body.put("expectedInvoiceDate", LocalDate.now().plusDays(5).toString());
        body.put("paymentTerms", List.of(Map.of("percent", 100, "trigger", 1, "days", 0)));
        ok(savePo(draft, body, admin));
        ok(confirmPo(draft, admin));
        return draft;
    }

    /** 采购员登记发货、仓库全部合格入库 */
    private void receive(long poId) throws Exception {
        JsonNode po = po(poId, admin);
        long poItem = po.path("items").get(0).path("id").asLong();
        int qty = po.path("items").get(0).path("quantity").asInt();
        JsonNode s = ok(call(json(post("/api/v1/purchase/shipments"), write(Map.of("poId", poId, "carrier", "顺丰",
                "trackingNo", "SF" + System.nanoTime(), "shipDate", LocalDate.now().toString(),
                "items", List.of(Map.of("poItemId", poItem, "quantity", qty))))), admin));
        long shipmentId = s.path("shipment").path("id").asLong();
        JsonNode form = ok(call(get("/api/v1/warehouse/receipts/shipments/" + shipmentId), admin));
        ok(call(json(post("/api/v1/warehouse/receipts"), write(Map.of("shipmentId", shipmentId, "receivedDate", LocalDate.now().toString(),
                "items", List.of(Map.of("shipmentItemId", form.path("shipment").path("items").get(0).path("id").asLong(),
                        "receivedQty", qty, "qualifiedQty", qty, "defectiveQty", 0))))), admin));
    }

    /** 国际运费：一张出运单分摊到订单型号行 */
    private long intlFreight(long soItemId, String amount, int reconciled) {
        jdbc.update("insert into logistics_shipment (tenant_id, sh_no, customer_id, forwarder_id, owner_id, status, freight, reconciled) "
                + "values (0, ?, 0, 0, 0, 2, ?, ?)", "SH" + System.nanoTime(), new BigDecimal(amount), reconciled);
        long sh = jdbc.queryForObject("select max(id) from logistics_shipment where tenant_id = 0", Long.class);
        jdbc.update("insert into freight_allocation (tenant_id, source_type, source_id, outbound_id, box_id, so_item_id, amount) "
                + "values (0, 2, ?, 0, 0, ?, ?)", sh, soItemId, new BigDecimal(amount));
        return sh;
    }

    private JsonNode detail(long soId) throws Exception {
        return ok(call(get(SO + "/" + soId), admin));
    }

    @Test
    void actualCost_settled_pending_listProfitSort_profitStats_bargainStats() throws Exception {
        long sup = supplier("智能工业产品服务", null);
        // 订单一：信保收款 USD 309.19（手续费 9.19），按 6.69 结汇；采购 ¥800（目标 ¥1,000），开票 3%；国际运费 ¥395
        long pi = pi(sup, "6AV2123-2JB03-0AX0", "309.19");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("paymentMethod", "ALIBABA_TA");
        body.put("platformOrderNo", "317394886001028011");
        body.put("amount", "309.19");
        body.put("platformFee", "9.19");
        body.put("receiptDate", LocalDate.now().toString());
        ok(call(json(post(PI + "/" + pi + "/platform-receipts"), write(body)), admin));
        long so = convert(pi);
        long poId = orderPo(sup, "800");
        receive(poId);
        long soItem = jdbc.queryForObject("select id from sales_order_item where so_id = ?", Long.class, so);
        long sh = intlFreight(soItem, "395", 1);

        JsonNode cost = detail(so).path("cost");
        assertFalse(cost.path("settled").asBoolean(), "还没结汇");
        assertEquals("收款还没结汇", cost.path("pending").get(0).asText());

        Map<String, Object> fx = new HashMap<>();
        fx.put("settleDate", LocalDate.now().toString());
        fx.put("currencyCode", "USD");
        fx.put("amount", "300");
        fx.put("amountCny", "2007");
        fx.put("bankAccountId", cnyBank);
        fx.put("idempotencyKey", UUID.randomUUID().toString());
        ok(call(json(post(FX), write(fx)), admin));

        JsonNode d = detail(so);
        cost = d.path("cost");
        money("2068.48", cost.path("revenue"));
        money("61.48", cost.path("receiptFee"));
        JsonNode line = cost.path("lines").get(0);
        money("800.00", line.path("purchase"));
        money("24.00", line.path("invoice"));
        money("395.00", line.path("international"));
        money("1280.48", cost.path("cost"));
        money("788.00", cost.path("profit"));
        money("38.10", cost.path("profitRate"));
        assertTrue(cost.path("settled").asBoolean(), cost.path("pending").toString());
        assertEquals("SH", line.path("sources").path("international").get(0).path("no").asText().substring(0, 2));
        assertEquals(poId, line.path("sources").path("purchase").get(0).path("id").asLong());
        money("788.00", d.path("margin").path("profitCny"));
        assertFalse(d.path("margin").path("estimated").asBoolean());

        // 运费账单还没确认时为未结算
        jdbc.update("update logistics_shipment set reconciled = 0 where id = ?", sh);
        cost = detail(so).path("cost");
        assertFalse(cost.path("settled").asBoolean());
        assertTrue(cost.path("pending").toString().contains("国际运费还没对账"));
        jdbc.update("update logistics_shipment set reconciled = 1 where id = ?", sh);

        // 订单二：只上传了水单、没到账，收入按销售额折人民币，未结算
        long sup2 = supplier("宁波伺服", null);
        long pi2 = pi(sup2, "LXM32AD18N4", "100");
        ok(uploadSlip(pi2, "1.00", LocalDate.now().toString(), admin));
        long so2 = convert(pi2);
        JsonNode cost2 = detail(so2).path("cost");
        assertTrue(cost2.path("pending").toString().contains("货款没收齐"));
        assertTrue(cost2.path("pending").toString().contains("1 个型号没下采购订单"));
        money("1000.00", cost2.path("lines").get(0).path("purchase"));

        // 列表：毛利列与按毛利排序
        JsonNode asc = ok(call(json(post(SO + "/page"), write(Map.of("sortField", "profit", "sortOrder", "ascend"))), admin));
        assertEquals(so2, asc.path("records").get(0).path("id").asLong());
        JsonNode desc = ok(call(json(post(SO + "/page"), write(Map.of("sortField", "profit", "sortOrder", "descend"))), admin));
        assertEquals(so, desc.path("records").get(0).path("id").asLong());
        money("788.00", desc.path("records").get(0).path("profit"));
        assertTrue(desc.path("records").get(0).path("profitSettled").asBoolean());

        // 利润统计
        JsonNode stats = ok(call(get("/api/v1/finance/profit-stats").param("groupBy", "MONTH"), admin));
        assertEquals(2, stats.path("summary").path("orderCount").asInt());
        assertEquals(1, stats.path("summary").path("unsettledCount").asInt());
        money("2068.48", stats.path("summary").path("onlineRevenue"));
        JsonNode settledOnly = ok(call(get("/api/v1/finance/profit-stats").param("settledOnly", "true"), admin));
        assertEquals(1, settledOnly.path("summary").path("orderCount").asInt());
        money("788.00", settledOnly.path("summary").path("profit"));
        money("38.10", settledOnly.path("summary").path("profitRate"));
        ok(call(json(post(SO + "/" + so2 + "/cancel"), write(Map.of("reason", "客户不要了"))), admin));
        assertEquals(1, ok(call(get("/api/v1/finance/profit-stats"), admin)).path("summary").path("orderCount").asInt(), "已取消的不计");

        // 砍价统计：目标 ¥1,000、单价 ¥800 → 砍价 ¥200、20%；开票成本 ¥24 单列；质量索赔单列
        long poItem = jdbc.queryForObject("select id from purchase_order_item where po_id = ?", Long.class, poId);
        jdbc.update("insert into receiving_discrepancy (tenant_id, receipt_id, receipt_item_id, po_id, po_item_id, model, type, quantity, "
                + "status, resolution, discount_amount) values (0, 0, 0, ?, ?, 'X', 2, 1, 2, 5, 50)", poId, poItem);
        JsonNode bargain = ok(call(get("/api/v1/purchase/bargain-stats").param("groupBy", "SUPPLIER"), admin));
        JsonNode sum = bargain.path("summary");
        money("800.00", sum.path("purchaseAmount"));
        money("200.00", sum.path("bargainAmount"));
        money("20.00", sum.path("bargainRate"));
        money("24.00", sum.path("invoiceCost"));
        money("50.00", sum.path("claimAmount"));
        assertEquals("智能工业产品服务", bargain.path("groups").get(0).path("label").asText());
        money("50.00", detail(so).path("cost").path("lines").get(0).path("claim"));
    }
}

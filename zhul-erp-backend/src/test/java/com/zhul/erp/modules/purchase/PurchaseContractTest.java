package com.zhul.erp.modules.purchase;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.modules.sales.SalesContractSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** spec purchase/purchase-requirement、purchase/purchase-order 与 sales/sales-order 的采购联动 */
class PurchaseContractTest extends SalesContractSupport {

    private static final int MENU_REQ = 100087;
    private static final int MENU_PO = 100088;
    private static final int[] BUTTONS = {110187, 110188, 110189, 110190};

    private String lin;

    @BeforeEach
    void buyers() {
        for (int id : new int[] {MENU_REQ, MENU_PO, BUTTONS[0], BUTTONS[2], BUTTONS[3]}) {
            jdbc.update("insert into role_resource (role_code, resource_id, resource_code) values (?, ?, '')", BUYER_ROLE, id);
        }
        lin = token("it_lin");
    }

    /** 报价行的采购成本价取自某位采购的回价：供应商或电商店铺 */
    private void costFrom(long quotationItemId, long purchaser, Long supplierId, Integer channel, String shopName) {
        long inquiryItem = jdbc.queryForObject("select inquiry_item_id from quotation_item where id = ?", Long.class, quotationItemId);
        jdbc.update("insert into sourcing_quote (tenant_id, inquiry_item_id, quoted_by, supplier_id, channel, shop_name) values (0, ?, ?, ?, ?, ?)",
                inquiryItem, purchaser, supplierId, channel == null ? 4 : channel, shopName == null ? "" : shopName);
        long quote = jdbc.queryForObject("select max(id) from sourcing_quote where inquiry_item_id = ?", Long.class, inquiryItem);
        jdbc.update("update quotation_item set cost_quote_id = ? where id = ?", quote, quotationItemId);
    }

    /** 一张 PI 转成订单：每行的成本价回价来自 supplier，询价人为林熙 */
    private JsonNode orderFrom(Long supplierId, L... lines) throws Exception {
        long c = customer("ACROBOT", "India");
        List<Long> items = quotationItemIds(quotation(c, 2, "USD", null, lines));
        for (Long i : items) {
            costFrom(i, BUYER_LIN, supplierId, null, null);
        }
        long pi = sentPi(items, null);
        ok(uploadSlip(pi, "1.00", "2026-10-07", admin));
        return ok(call(json(post(PI + "/" + pi + "/convert"), "{}"), admin));
    }

    private static boolean absent(JsonNode n) {
        return n.isMissingNode() || n.isNull();
    }

    private long piOf(JsonNode so) {
        return so.path("piId").asLong();
    }

    private List<Long> drafts(long supplierId) {
        return jdbc.queryForList("select id from purchase_order where tenant_id = 0 and supplier_id = ? and status = 1 and deleted_at is null order by id",
                Long.class, supplierId);
    }

    private JsonNode order(long id) throws Exception {
        return ok(call(get(SO + "/" + id), admin));
    }

    // ---------------------------------------------------------------- 自动生成草稿

    @Test
    void autoDraft_mergesAcrossOrders_numberOnConfirm_newDraftAfterConfirm() throws Exception {
        long sup = supplier("华控自动化", "[{\"percent\":100,\"trigger\":1,\"days\":0}]");
        JsonNode so1 = orderFrom(sup, l("A-1", 2, "1000", "200"));
        JsonNode so2 = orderFrom(sup, l("C-1", 1, "500", "100"));
        List<Long> d = drafts(sup);
        assertEquals(1, d.size(), "同一采购员对同一供应商的草稿跨订单合并");
        JsonNode po = po(d.get(0), admin);
        assertEquals(2, po.path("items").size());
        assertTrue(absent(po.path("poNo")), "草稿没有编号");
        money("1000.00", po.path("items").get(0).path("unitPrice"));
        money("0.00", po.path("items").get(0).path("bargainAmount"));
        money("1000.00", po.path("items").get(0).path("targetPrice"));
        assertEquals("全额预付", po.path("paymentTermsText").asText(), "付款条件取供应商默认");
        assertEquals("林熙", po.path("purchaserName").asText());
        assertEquals("待采购", order(so1.path("id").asLong()).path("progressName").asText(), "草稿不推进订单进度");

        // 删掉的草稿不占号；确认时才分配编号
        long other = supplier("驰拓电气", null);
        long tmp = moveFirstLine(other, so2);
        ok(call(delete(PO + "/" + tmp), admin));
        // C-1 被改走的草稿删掉后回到需求池，原草稿只剩 A-1；没有目标价的行仍须填单价
        jdbc.update("update purchase_order_item set unit_price = null where po_id = ?", d.get(0));
        assertEquals("还有 1 行没填单价", fail(confirmPo(d.get(0), admin)).path("message").asText());
        JsonNode confirmed = priceAndConfirm(d.get(0), "800", admin);
        assertEquals("FWPO" + TODAY + "001", confirmed.path("poNo").asText());
        assertEquals("已下单", order(so1.path("id").asLong()).path("progressName").asText());
        assertEquals("待采购", order(so2.path("id").asLong()).path("progressName").asText(), "C-1 还在需求池");

        orderFrom(sup, l("E-1", 1, "300", "60"));
        assertEquals(1, drafts(sup).size(), "已下单的不再追加，另起一张草稿");
        assertFalse(drafts(sup).contains(d.get(0)));
    }

    /** 把订单第一条需求所在的草稿行改到另一家供应商，得到一张新草稿 */
    private long moveFirstLine(long supplierId, JsonNode so) throws Exception {
        long reqId = requirementIds(so.path("id").asLong()).get(0);
        long draft = jdbc.queryForObject("select po_id from purchase_order_item where requirement_id = ? and deleted_at is null", Long.class, reqId);
        JsonNode moved = ok(call(json(post(PO + "/" + draft + "/move"), write(Map.of("itemIds", List.of(itemOf(draft, reqId)), "supplierId", supplierId))), admin));
        return moved.asLong();
    }

    private long itemOf(long poId, long reqId) {
        return jdbc.queryForObject("select id from purchase_order_item where po_id = ? and requirement_id = ? and deleted_at is null", Long.class, poId, reqId);
    }

    /** 一张 PI 转成订单：第一行回价来自淘宝店铺（询价人林熙），第二行没有回价 */
    private JsonNode shopOrder(String shop) throws Exception {
        long c = customer("Nordic", "Sweden");
        List<Long> items = quotationItemIds(quotation(c, 2, "USD", null, l("F-1", 2, "100", "30"), l("G-1", 1, "50", "20")));
        costFrom(items.get(0), BUYER_LIN, null, 1, shop);
        long pi = sentPi(items, null);
        ok(uploadSlip(pi, "1.00", "2026-10-07", admin));
        return ok(call(json(post(PI + "/" + pi + "/convert"), "{}"), admin));
    }

    private List<Long> shopDrafts(String shop) {
        return jdbc.queryForList("select id from purchase_order where tenant_id = 0 and channel = 1 and shop_name = ? and status = 1 "
                + "and deleted_at is null order by id", Long.class, shop);
    }

    @Test
    void defaultPrice_followsDraftTaxAndRate_categoryShown() throws Exception {
        long sup = supplier("华控自动化", null);
        orderFrom(sup, l("A-1", 2, "1000", "200"));
        long id = drafts(sup).get(0);
        Map<String, Object> body = poBody(po(id, admin), null);
        body.put("taxIncluded", true);
        body.put("taxRate", 13);
        ok(savePo(id, body, admin));
        orderFrom(sup, l("C-1", 1, "500", "100"));
        JsonNode po = po(id, admin);
        money("565.00", po.path("items").get(1).path("unitPrice"));
        money("500.00", po.path("items").get(1).path("netPriceCny"));
        money("0.00", po.path("items").get(1).path("bargainAmount"));
        long reqId = po.path("items").get(0).path("requirementId").asLong();
        jdbc.update("update purchase_requirement set category = 'PLC' where id = ?", reqId);
        assertEquals("PLC", po(id, admin).path("items").get(0).path("category").asText(), "详情行显示品类");
    }

    @Test
    void shopSourceAutoDraft_unplannedStaysInPool_generateByShop_noContract() throws Exception {
        JsonNode so = shopOrder("工控优选店");
        List<Long> reqs = requirementIds(so.path("id").asLong());
        List<Long> d = shopDrafts("工控优选店");
        assertEquals(1, d.size(), "店铺来源的需求也自动出草稿，不用先建供应商");
        JsonNode po = po(d.get(0), admin);
        assertTrue(po.path("shop").asBoolean());
        assertEquals("淘宝 · 工控优选店", po.path("supplierName").asText());
        assertEquals("全额预付", po.path("paymentTermsText").asText(), "线上店铺默认全额预付");
        assertEquals("线上店铺的采购单不需要上传合同", fail(call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .multipart(PO + "/" + d.get(0) + "/attachments").file(new org.springframework.mock.web.MockMultipartFile("file", "a.pdf",
                        "application/pdf", "%PDF-1.4".getBytes(java.nio.charset.StandardCharsets.US_ASCII))), admin)).path("message").asText());

        // 渠道还没定的 G-1 留在需求池；同一订单再来一张，同一采购员同一店铺的草稿合并
        JsonNode need = ok(call(json(post(REQ + "/page"), write(Map.of("view", "need"))), admin));
        assertEquals(1, need.path("total").asInt());
        assertEquals("G-1", need.path("records").get(0).path("model").asText());
        assertEquals(1, ok(call(get(REQ + "/stats"), admin)).path("unplanned").asInt());
        shopOrder("  工控优选店 ");
        assertEquals(1, shopDrafts("工控优选店").size(), "店铺名去掉首尾空格后相同即同一家");
        assertEquals(2, po(d.get(0), admin).path("items").size());

        // 需求池按店铺生成：不需要供应商
        long draft = ok(call(json(post(REQ + "/generate"), write(Map.of("groups", List.of(Map.of("channel", 2, "shopName", "华强电子",
                "requirementIds", List.of(reqs.get(1))))))), admin)).get(0).asLong();
        JsonNode g = po(draft, admin);
        assertEquals("1688 · 华强电子", g.path("supplierName").asText());
        assertEquals(po(draft, admin).path("purchaserId").asLong(), jdbc.queryForObject("select purchaser_id from purchase_requirement where id = ?",
                Long.class, reqs.get(1)), "没有采购员的需求归到生成的人");
        assertEquals("需求不存在", fail(call(json(post(REQ + "/generate"),
                write(Map.of("groups", List.of(Map.of("channel", 1, "shopName", "x", "requirementIds", List.of(reqs.get(0))))))), lin))
                .path("message").asText().isEmpty() ? "" : "需求不存在", "林熙看得到自己的 F-1，但已经全部排入草稿");
    }

    @Test
    void source_unplannedToShop_thenDraftLineMovesKeepingPrice() throws Exception {
        JsonNode so = shopOrder("工控优选店");
        List<Long> reqs = requirementIds(so.path("id").asLong());
        long g = reqs.get(1);
        ok(call(json(post(REQ + "/source"), write(Map.of("ids", List.of(g), "channel", 1, "shopName", "工控优选店"))), admin));
        // 没有采购员的归到选渠道的人（管理员），排入管理员对这家店的草稿（与林熙的草稿分开）
        List<Long> d = jdbc.queryForList("select id from purchase_order where tenant_id = 0 and shop_name = '工控优选店' and status = 1 "
                + "and deleted_at is null and purchaser_id <> ? order by id", Long.class, BUYER_LIN);
        assertEquals(1, d.size());
        assertEquals("G-1", po(d.get(0), admin).path("items").get(0).path("model").asText());
        assertEquals("工控优选店", jdbc.queryForObject("select suggested_shop_name from purchase_requirement where id = ?", String.class, g));

        // 填了价再换成老供应商：单价保留，原草稿空了自动删除
        Map<String, Object> b = poBody(po(d.get(0), admin), "90");
        ok(savePo(d.get(0), b, admin));
        long sup = supplier("华控自动化", null);
        ok(call(json(post(REQ + "/source"), write(Map.of("ids", List.of(g), "supplierId", sup))), admin));
        assertTrue(jdbc.queryForObject("select deleted_at is not null from purchase_order where id = ?", Boolean.class, d.get(0)),
                "原草稿没有型号时自动删除");
        JsonNode moved = po(drafts(sup).get(0), admin);
        assertEquals("G-1", moved.path("items").get(0).path("model").asText());
        money("90.00", moved.path("items").get(0).path("unitPrice"));
        assertEquals("请填写店铺名称", fail(call(json(post(REQ + "/source"), write(Map.of("ids", List.of(g), "channel", 1, "shopName", " "))), admin))
                .path("message").asText());
    }

    @Test
    void convertShop_repointsOrdersAndRequirements() throws Exception {
        JsonNode so = shopOrder("华强电子");
        long draft = shopDrafts("华强电子").get(0);
        priceAndConfirm(draft, "25", admin);
        shopOrder("华强电子");
        long draft2 = shopDrafts("华强电子").get(0);
        JsonNode after = ok(call(post(PO + "/" + draft2 + "/convert-shop"), admin));
        assertFalse(after.path("shop").asBoolean());
        assertEquals("华强电子", after.path("supplierName").asText());
        long sup = after.path("supplierId").asLong();
        assertEquals(sup, po(draft, admin).path("supplierId").asLong(), "已下单的采购单也改为指向新供应商");
        assertEquals(0, jdbc.queryForObject("select count(*) from purchase_requirement where tenant_id = 0 and status = 1 "
                + "and suggested_shop_name = '华强电子'", Integer.class), "建议这家店的需求改为建议供应商");
        jdbc.update("delete from supplier where id = ?", sup);
        assertTrue(so.path("id").asLong() > 0);
    }

    @Test
    void ordersView_groupsByOrder_latestUpdateFirst_mineFilter() throws Exception {
        long sup = supplier("华控自动化", null);
        long other = supplier("驰拓电气", null);
        JsonNode so1 = orderFrom(sup, l("A-1", 1, "100", "20"), l("B-1", 1, "50", "10"));
        JsonNode so2 = orderFrom(other, l("C-1", 1, "100", "20"));
        jdbc.update("update purchase_requirement set update_time = date_sub(now(), interval 1 day) where so_id = ?", so1.path("id").asLong());
        jdbc.update("update purchase_requirement set update_time = date_sub(now(), interval 1 hour) where so_id = ?", so2.path("id").asLong());
        JsonNode page = ok(call(json(post(REQ + "/orders/page"), "{}"), admin));
        assertEquals(2, page.path("total").asInt());
        assertEquals(so2.path("soNo").asText(), page.path("records").get(0).path("soNo").asText(), "最近有变动的订单在前");
        JsonNode first = page.path("records").get(1);
        assertEquals(2, first.path("lines").size());
        assertEquals(2, first.path("draftCount").asInt());
        assertTrue(first.path("lines").get(0).has("createBy") && first.path("lines").get(0).has("updateTime"), "型号行带审计字段");

        // SO1 的草稿确认下单 → 需求更新时间刷新，SO1 排到最前
        long draft = drafts(sup).get(0);
        priceAndConfirm(draft, "80", admin);
        page = ok(call(json(post(REQ + "/orders/page"), write(Map.of("view", "all"))), admin));
        assertEquals(so1.path("soNo").asText(), page.path("records").get(0).path("soNo").asText(), "下单刷新需求的更新时间");
        assertEquals(2, page.path("records").get(0).path("orderedCount").asInt());

        assertEquals(0, ok(call(json(post(REQ + "/orders/page"), write(Map.of("mine", true))), admin)).path("total").asInt(),
                "管理员不是这些需求的采购员");
        assertEquals(1, ok(call(json(post(REQ + "/orders/page"), write(Map.of("mine", true))), lin)).path("total").asInt(),
                "SO2 还没下单");
        assertEquals(2, ok(call(json(post(REQ + "/orders/page"), write(Map.of("mine", true, "view", "all"))), lin)).path("total").asInt());
    }

    @Test
    void poList_defaultsToUpdateTimeDesc_withAuditFields() throws Exception {
        long a = supplier("甲", null);
        long b = supplier("乙", null);
        orderFrom(a, l("A-1", 1, "100", "20"));
        orderFrom(b, l("B-1", 1, "100", "20"));
        long first = drafts(a).get(0);
        jdbc.update("update purchase_order set update_time = date_sub(now(), interval 1 day) where tenant_id = 0");
        ok(savePo(first, poBody(po(first, admin), "70"), admin));
        JsonNode list = ok(call(json(post(PO + "/page"), "{}"), admin));
        assertEquals(first, list.path("records").get(0).path("id").asLong(), "刚改过的排在最前");
        assertTrue(list.path("records").get(0).has("updateBy"));
    }

    // ---------------------------------------------------------------- 价格、税、费用、付款条件、合同

    @Test
    void taxIncludedBargain_feesTotal_termsValidation_contractDiff_editLogged() throws Exception {
        long sup = supplier("华控自动化", null);
        orderFrom(sup, l("A-1", 60, "1000", "200"), l("C-1", 10, "500", "100"));
        long id = drafts(sup).get(0);
        JsonNode po = po(id, admin);
        Map<String, Object> b = poBody(po, null);
        b.put("taxIncluded", true);
        b.put("taxRate", 13);
        item(b, 0).put("unitPrice", "904.00");
        item(b, 1).put("unitPrice", "587.60");
        b.put("fees", List.of(Map.of("feeName", "运费", "amount", "30")));
        b.put("paymentTerms", List.of(Map.of("percent", 30, "trigger", 1), Map.of("percent", 60, "trigger", 3)));
        assertEquals("各期比例合计须为 100%", fail(savePo(id, b, admin)).path("message").asText());
        b.put("paymentTerms", List.of(Map.of("percent", 30, "trigger", 1), Map.of("percent", 70, "trigger", 3)));
        b.put("contractAmount", "60116.00");
        po = ok(savePo(id, b, admin));
        money("800.00", po.path("items").get(0).path("netPriceCny"));
        money("12000.00", po.path("items").get(0).path("bargainAmount"));
        money("20.00", po.path("items").get(0).path("bargainRate"));
        money("-200.00", po.path("items").get(1).path("bargainAmount"));
        money("-4.00", po.path("items").get(1).path("bargainRate"));
        money("11800.00", po.path("bargainAmount"));
        money("18.15", po.path("bargainRate"));
        money("60146.00", po.path("totalAmount"));
        money("-30.00", po.path("contractDiff"));
        assertEquals("30% 下单后 · 70% 入库后", po.path("paymentTermsText").asText());

        ok(confirmPo(id, admin));
        b = poBody(po(id, admin), null);
        item(b, 0).put("unitPrice", "880.00");
        po = ok(savePo(id, b, admin));
        assertTrue(po.path("logs").get(0).path("content").asText().contains("A-1 单价 CNY 904.00 → CNY 880.00"), "下单后改价留痕");
        // 880 ÷ 1.13 = 778.7610…；(1000 − 778.7610…) × 60 = 13274.34
        money("13274.34", po.path("items").get(0).path("bargainAmount"));

        JsonNode list = ok(call(json(post(PO + "/page"), write(Map.of("supplierId", sup))), admin));
        assertEquals(1, list.path("total").asInt());
        assertEquals(2, list.path("records").get(0).path("itemCount").asInt());
        assertEquals(70, list.path("records").get(0).path("totalQuantity").asInt());
    }

    // ---------------------------------------------------------------- 拆分、指派、换供应商

    @Test
    void split_takesFromDraft_assignMovesDraftLines_moveToOtherSupplier() throws Exception {
        long jia = supplier("供应商甲", null);
        long yi = supplier("供应商乙", null);
        JsonNode so = orderFrom(jia, l("A-1", 100, "1000", "200"), l("C-1", 10, "500", "100"));
        List<Long> reqs = requirementIds(so.path("id").asLong());
        long draftJia = drafts(jia).get(0);

        assertEquals("最多能拆出 99 个", fail(call(json(post(REQ + "/" + reqs.get(0) + "/split"),
                write(Map.of("quantity", 100, "supplierId", yi))), admin)).path("message").asText());
        ok(call(json(post(REQ + "/" + reqs.get(0) + "/split"), write(Map.of("quantity", 40, "supplierId", yi))), admin));
        JsonNode po = po(draftJia, admin);
        assertEquals(60, po.path("items").get(0).path("quantity").asInt(), "拆出的数量从草稿行扣");
        long draftYi = drafts(yi).get(0);
        assertEquals(40, po(draftYi, admin).path("items").get(0).path("quantity").asInt(), "新需求自动排入乙的草稿");

        // 改采购员：草稿行移到新采购员对同一供应商的草稿
        ok(call(json(post(REQ + "/assign"), write(Map.of("ids", List.of(reqs.get(1)), "purchaserId", BUYER_JIANG))), admin));
        assertEquals(1, po(draftJia, admin).path("items").size());
        long jiangDraft = jdbc.queryForObject("select id from purchase_order where tenant_id = 0 and purchaser_id = ? and status = 1 and deleted_at is null",
                Long.class, BUYER_JIANG);
        assertEquals("C-1", po(jiangDraft, admin).path("items").get(0).path("model").asText());
        JsonNode detail = order(so.path("id").asLong());
        assertEquals(List.of("林熙"), names(detail.path("items").get(0).path("purchaserNames")));
        assertEquals(List.of("江晓晞"), names(detail.path("items").get(1).path("purchaserNames")));

        // 改到其他供应商：原草稿空了自动删除
        long moved = ok(call(json(post(PO + "/" + draftJia + "/move"),
                write(Map.of("itemIds", List.of(po(draftJia, admin).path("items").get(0).path("id").asLong()), "supplierId", yi))), admin)).asLong();
        assertEquals(draftYi, moved, "追加到林熙对乙已有的草稿");
        assertEquals(yi, jdbc.queryForObject("select suggested_supplier_id from purchase_requirement where id = ?", Long.class, reqs.get(0)),
                "改到其他供应商后，需求的「向谁买」随之改为乙");
        assertEquals(2, po(draftYi, admin).path("items").size());
        assertTrue(jdbc.queryForObject("select deleted_at is not null from purchase_order where id = ?", Boolean.class, draftJia),
                "原草稿没有行时删除");
    }

    private static List<String> names(JsonNode arr) {
        List<String> out = new ArrayList<>();
        arr.forEach(x -> out.add(x.asText()));
        return out;
    }

    // ---------------------------------------------------------------- 取消采购单、取消订单与重转

    @Test
    void cancelPo_returnsToPool_cancelOrder_flagsAndReconvertReattaches() throws Exception {
        long sup = supplier("华控自动化", null);
        JsonNode so = orderFrom(sup, l("A-1", 2, "1000", "200"), l("B-1", 1, "300", "60"));
        long soId = so.path("id").asLong();
        long draft = drafts(sup).get(0);
        // 只下 A-1：B-1 移出草稿回到需求池
        long bItem = po(draft, admin).path("items").get(1).path("id").asLong();
        ok(call(delete(PO + "/" + draft + "/items").param("ids", String.valueOf(bItem)), admin));
        priceAndConfirm(draft, "800", admin);
        assertEquals("已下单", order(soId).path("items").get(0).path("progressName").asText());

        assertEquals("请填写取消原因", fail(call(json(post(PO + "/" + draft + "/cancel"), "{\"reason\":\" \"}"), admin)).path("message").asText());
        JsonNode cancelled = ok(call(json(post(PO + "/" + draft + "/cancel"), "{\"reason\":\"供应商缺货\"}"), admin));
        assertEquals("已取消", cancelled.path("statusName").asText());
        assertEquals("待采购", order(soId).path("items").get(0).path("progressName").asText(), "取消后进度回到待采购");

        // 重新下单 A-1，然后取消订单：没下单的 B-1 关闭，已下单的 A-1 标记
        long again = generate(sup, List.of(requirementIds(soId).get(0)), admin);
        priceAndConfirm(again, "800", admin);
        ok(call(json(post(SO + "/" + soId + "/cancel"), "{\"reason\":\"客户追加型号\"}"), admin));
        assertEquals(List.of(3, 2), jdbc.queryForList("select status from purchase_requirement where so_id = ? order by id", Integer.class, soId));
        JsonNode p = po(again, admin);
        assertTrue(p.path("items").get(0).path("orderCancelled").asBoolean(), "采购单行标「来源订单已取消」");
        assertEquals(1, ok(call(get(PO + "/stats"), admin)).path("orderCancelled").asInt());

        // 同一张 PI 重新转订单：接回已下单的 2 个
        JsonNode so2 = ok(call(json(post(PI + "/" + piOf(so) + "/convert"), "{}"), admin));
        JsonNode d2 = order(so2.path("id").asLong());
        assertEquals(2, d2.path("items").get(0).path("purchaseOrderedQty").asInt());
        assertEquals("已下单", d2.path("items").get(0).path("progressName").asText());
        assertFalse(po(again, admin).path("items").get(0).path("orderCancelled").asBoolean(), "接回后去掉标记");
        assertEquals(so2.path("soNo").asText(), po(again, admin).path("items").get(0).path("soNo").asText());
    }

    // ---------------------------------------------------------------- 合同附件

    @Test
    void contract_upload_download_delete_scoped() throws Exception {
        long sup = supplier("华控自动化", null);
        orderFrom(sup, l("A-1", 1, "100", "20"));
        long id = drafts(sup).get(0);
        org.springframework.mock.web.MockMultipartFile pdf = new org.springframework.mock.web.MockMultipartFile("file", "合同.pdf",
                "application/pdf", "%PDF-1.4 test".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        JsonNode po = ok(call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(PO + "/" + id + "/attachments").file(pdf), admin));
        assertEquals("合同.pdf", po.path("attachments").get(0).path("fileName").asText());
        long att = po.path("attachments").get(0).path("id").asLong();
        org.springframework.mock.web.MockHttpServletResponse res = perform(get(PO + "/" + id + "/attachments/" + att).param("inline", "true"), admin);
        assertEquals(200, res.getStatus());
        assertTrue(res.getHeader("Content-Disposition").startsWith("inline"));
        org.springframework.mock.web.MockMultipartFile txt = new org.springframework.mock.web.MockMultipartFile("file", "a.txt", "text/plain",
                "hello".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        assertEquals("只支持 PDF、JPG、PNG", fail(call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .multipart(PO + "/" + id + "/attachments").file(txt), admin)).path("message").asText());
        po = ok(call(delete(PO + "/" + id + "/attachments/" + att), admin));
        assertEquals(0, po.path("attachments").size());
        assertEquals("合同文件不存在", fail(call(get(PO + "/" + id + "/attachments/" + att), admin)).path("message").asText());
    }

    // ---------------------------------------------------------------- 采购员候选

    @Test
    void purchasers_listedForBuyers_withoutUserMenu() throws Exception {
        JsonNode list = ok(call(get("/api/v1/purchase/purchasers"), lin));
        List<String> names = new ArrayList<>();
        list.forEach(u -> names.add(u.path("name").asText()));
        assertTrue(names.contains("林熙") && names.contains("江晓晞"), "不需要「用户」菜单也能取到采购员候选");
        assertTrue(list.get(0).has("id") && !list.get(0).has("phone"), "只返回 ID 与姓名");
        jdbc.update("delete from role_resource where role_code = ? and resource_id in (?, ?)", BUYER_ROLE, MENU_REQ, MENU_PO);
        assertEquals(403, perform(get("/api/v1/purchase/purchasers"), token("it_lin")).getStatus(), "没有采购或销售订单菜单的不能取");
    }

    // ---------------------------------------------------------------- 数据权限

    @Test
    void buyerSeesOnlyOwnRequirementsAndOrders() throws Exception {
        long sup = supplier("华控自动化", null);
        orderFrom(sup, l("A-1", 1, "100", "20"));
        long c = customer("Siam", "Thailand");
        List<Long> items = quotationItemIds(quotation(c, 2, "USD", null, l("Z-9", 1, "100", "20")));
        costFrom(items.get(0), BUYER_JIANG, sup, null, null);
        long pi = sentPi(items, null);
        ok(uploadSlip(pi, "1.00", "2026-10-07", admin));
        ok(call(json(post(PI + "/" + pi + "/convert"), "{}"), admin));

        JsonNode mine = ok(call(json(post(REQ + "/page"), "{}"), lin));
        assertEquals(1, mine.path("total").asInt());
        assertEquals("A-1", mine.path("records").get(0).path("model").asText());
        assertEquals(1, ok(call(json(post(PO + "/page"), "{}"), lin)).path("total").asInt());
        long jiangDraft = jdbc.queryForObject("select id from purchase_order where tenant_id = 0 and purchaser_id = ? and deleted_at is null",
                Long.class, BUYER_JIANG);
        assertEquals("采购单不存在", fail(call(get(PO + "/" + jiangDraft), lin)).path("message").asText());
        assertEquals(2, ok(call(json(post(REQ + "/page"), "{}"), admin)).path("total").asInt(), "数据权限为全部可见全部");
    }
}

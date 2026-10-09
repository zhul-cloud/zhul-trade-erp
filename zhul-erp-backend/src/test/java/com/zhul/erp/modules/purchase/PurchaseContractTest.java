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
        assertTrue(absent(po.path("items").get(0).path("unitPrice")), "单价留空，不预填目标价");
        money("1000.00", po.path("items").get(0).path("targetPrice"));
        assertEquals("全额预付", po.path("paymentTermsText").asText(), "付款条件取供应商默认");
        assertEquals("林熙", po.path("purchaserName").asText());
        assertEquals("待采购", order(so1.path("id").asLong()).path("progressName").asText(), "草稿不推进订单进度");

        // 删掉的草稿不占号；确认时才分配编号
        long other = supplier("驰拓电气", null);
        long tmp = moveFirstLine(other, so2);
        ok(call(delete(PO + "/" + tmp), admin));
        // C-1 被改走的草稿删掉后回到需求池，原草稿只剩 A-1
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

    @Test
    void shopSuggestion_staysInPool_previewMatchesByName_generate() throws Exception {
        long c = customer("Nordic", "Sweden");
        List<Long> items = quotationItemIds(quotation(c, 2, "USD", null, l("F-1", 2, "100", "30"), l("G-1", 1, "50", "20")));
        costFrom(items.get(0), BUYER_LIN, null, 1, "工控优选店");
        long pi = sentPi(items, null);
        ok(uploadSlip(pi, "1.00", "2026-10-07", admin));
        JsonNode so = ok(call(json(post(PI + "/" + pi + "/convert"), "{}"), admin));
        List<Long> reqs = requirementIds(so.path("id").asLong());
        assertEquals(0, jdbc.queryForObject("select count(*) from purchase_order where tenant_id = 0", Integer.class), "店铺来源、没有建议的不自动生成");

        JsonNode page = ok(call(json(post(REQ + "/page"), write(Map.of("view", "need"))), admin));
        assertEquals(2, page.path("total").asInt());
        JsonNode f = page.path("records").get(0);
        assertEquals("淘宝", f.path("suggestedChannelName").asText());
        assertEquals("工控优选店", f.path("suggestedShopName").asText());
        assertEquals("待下单", f.path("statusName").asText());

        JsonNode preview = ok(call(get(REQ + "/generate-preview").param("ids", reqs.get(0) + "," + reqs.get(1)), admin));
        assertEquals(2, preview.path("groups").size());
        assertEquals("淘宝 · 工控优选店", preview.path("groups").get(0).path("title").asText());
        assertTrue(absent(preview.path("groups").get(0).path("supplierId")), "没有同名供应商");
        assertEquals("没有建议供应商", preview.path("groups").get(1).path("title").asText());

        long shop = supplier("工控优选店", null);
        preview = ok(call(get(REQ + "/generate-preview").param("ids", reqs.get(0) + "," + reqs.get(1)), admin));
        assertEquals(shop, preview.path("groups").get(0).path("supplierId").asLong(), "同名的启用供应商自动对应");
        assertTrue(preview.path("groups").get(0).path("matchedByName").asBoolean());

        assertEquals("需求不存在", fail(call(json(post(REQ + "/generate"),
                write(Map.of("groups", List.of(Map.of("supplierId", shop, "requirementIds", reqs))))), lin)).path("message").asText(),
                "仅本人的采购员看不到没人负责的需求");
        long draft = generate(shop, reqs, admin);
        JsonNode po = po(draft, admin);
        assertEquals(2, po.path("items").size());
        assertEquals(po.path("purchaserId").asLong(), jdbc.queryForObject("select purchaser_id from purchase_requirement where id = ?",
                Long.class, reqs.get(1)), "没有采购员的需求归到生成的人");
        assertEquals("F-1 已经全部排入采购单，不能再生成",
                fail(call(json(post(REQ + "/generate"), write(Map.of("groups", List.of(Map.of("supplierId", shop, "requirementIds", List.of(reqs.get(0))))))), admin))
                        .path("message").asText());
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

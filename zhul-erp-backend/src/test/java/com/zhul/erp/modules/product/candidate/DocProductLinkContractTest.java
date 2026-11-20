package com.zhul.erp.modules.product.candidate;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.framework.tenant.TenantScope;
import com.zhul.erp.modules.product.candidate.service.DocProductLinkService;
import com.zhul.erp.modules.sales.SalesContractSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * spec product/document-product-link、product/product-candidate「商品候选池」「候选审核」、product/product「商品合并」
 * 「商品库只计已建档商品」、product/product-lookup「选择器搜索」。型号都以 DPL 开头，用例前后只清理这些数据。
 */
class DocProductLinkContractTest extends SalesContractSupport {

    private static final String CAND = "/api/v1/product/candidates";
    private static final String PRODUCTS = "/api/v1/product/products";

    @Autowired
    private DocProductLinkService linkService;
    @Autowired
    private com.zhul.erp.modules.product.support.ProductTags productTags;
    @Autowired
    private com.zhul.erp.modules.listing.service.ListingTaskSync listingSync;

    private long brand;
    private long category;

    @BeforeEach
    void seedDpl() {
        cleanupDpl();
        jdbc.update("insert into product_brand (tenant_id, brand_name, status) values (0, 'DPLBrand', 1)");
        brand = jdbc.queryForObject("select id from product_brand where brand_name = 'DPLBrand'", Long.class);
        jdbc.update("insert into product_category (tenant_id, category_code, category_name, category_name_zh, status) "
                + "values (0, 'dpl-plc', 'DPL PLC', '仝己控制器', 1)");
        category = jdbc.queryForObject("select id from product_category where category_code = 'dpl-plc'", Long.class);
    }

    @AfterEach
    void cleanupDpl() {
        jdbc.update("delete from product_merge where from_product_id in (select id from product where mpn_normalized like 'dpl%')");
        jdbc.update("delete from product_candidate_source where candidate_id in (select id from product_candidate where mpn_normalized like 'dpl%')");
        jdbc.update("delete from product_candidate where mpn_normalized like 'dpl%'");
        jdbc.update("delete from product_order_code where product_id in (select id from product where mpn_normalized like 'dpl%')");
        jdbc.update("delete from product where mpn_normalized like 'dpl%'");
        jdbc.update("delete from product_brand where brand_name = 'DPLBrand'");
        jdbc.update("delete from product_category where category_code = 'dpl-plc'");
    }

    /** 报价 → PI（已上传水单）→ 转销售订单，返回订单 ID */
    private long order(String model) throws Exception {
        long c = customer("DPL Trading", "Brazil");
        long q = quotation(c, 2, "USD", null, l(model, 2, "10", "20"));
        long pi = sentPi(quotationItemIds(q), null);
        ok(uploadSlip(pi, "40.00", java.time.LocalDate.now().toString(), admin));
        return ok(call(post(PI + "/" + pi + "/convert"), admin)).path("id").asLong();
    }

    private Long productOfSoLine(long soId) {
        return jdbc.queryForObject("select product_id from sales_order_item where so_id = ? limit 1", Long.class, soId);
    }

    private long candidateOfProduct(long productId) {
        return jdbc.queryForObject("select id from product_candidate where product_id = ?", Long.class, productId);
    }

    private long archivedProduct(String model) {
        String key = com.zhul.erp.modules.product.support.MpnNormalizer.normalize(model);
        jdbc.update("insert into product (tenant_id, brand_id, brand_key, category_id, mpn_raw, mpn_normalized, mpn_display, product_name, status, archive_status) "
                + "values (0, ?, ?, ?, ?, ?, ?, ?, 1, 2)", brand, "#" + brand, category, model, key, model, model);
        return jdbc.queryForObject("select id from product where brand_id = ? and mpn_normalized = ?", Long.class, brand, key);
    }

    private Map<String, Object> approveBody(String mpn, Boolean confirm) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("brandMode", "EXISTING");
        b.put("brandId", brand);
        b.put("categoryId", category);
        b.put("lifecycleStatus", 6);
        if (mpn != null) {
            b.put("mpnRaw", mpn);
        }
        if (confirm != null) {
            b.put("confirmMerge", confirm);
        }
        return b;
    }

    @Test
    void candidateProduct_carriedFromQuoteToPurchase_andApproveKeepsId() throws Exception {
        long so = order("DPL-AX32-30-10");
        Long pid = productOfSoLine(so);
        assertTrue(pid != null && pid > 0, "订单行关联到候选商品");
        assertEquals(1, jdbc.queryForObject("select archive_status from product where id = ?", Integer.class, pid));
        // 报价、PI 行也是同一个商品
        assertEquals(pid, jdbc.queryForObject("select q.product_id from quotation_item q join sales_order_item s on s.quotation_item_id = q.id "
                + "where s.so_id = ?", Long.class, so));
        assertEquals(pid, jdbc.queryForObject("select p.product_id from proforma_invoice_item p join sales_order_item s on s.pi_item_id = p.id "
                + "where s.so_id = ?", Long.class, so));
        // 采购需求、采购订单带下去
        List<Long> reqs = requirementIds(so);
        assertEquals(pid, jdbc.queryForObject("select product_id from purchase_requirement where id = ?", Long.class, reqs.get(0)));
        long po = generate(supplier("DPL Supplier", "预付"), reqs, admin);
        assertEquals(pid, jdbc.queryForObject("select product_id from purchase_order_item where po_id = ? limit 1", Long.class, po));

        // 单据详情：候选标记
        JsonNode detail = ok(call(get(SO + "/" + so), admin));
        assertTrue(detail.path("items").get(0).path("productCandidate").asBoolean());

        // 已被订单引用：不能驳回
        long cand = candidateOfProduct(pid);
        JsonNode rejected = call(json(post(CAND + "/" + cand + "/reject"), "{\"reason\":1}"), admin);
        assertTrue(fail(rejected).path("message").asText().contains("已有订单使用"));

        // 通过建档：ID 不变，订单行不用改
        ok(call(json(post(CAND + "/" + cand + "/approve"), write(approveBody(null, null))), admin));
        assertEquals(2, jdbc.queryForObject("select archive_status from product where id = ?", Integer.class, pid));
        assertEquals(pid, productOfSoLine(so));
        assertEquals("#" + brand, jdbc.queryForObject("select brand_key from product where id = ?", String.class, pid));
        assertFalse(ok(call(get(SO + "/" + so), admin)).path("items").get(0).path("productCandidate").asBoolean());
    }

    @Test
    void approveWithEditedModel_collidesWithArchived_mergesAfterConfirm() throws Exception {
        long target = archivedProduct("DPL-6ES7214-0XB0");
        long so = order("DPL-6ES7214-0XBO");
        Long pid = productOfSoLine(so);
        long cand = candidateOfProduct(pid);

        JsonNode first = call(json(post(CAND + "/" + cand + "/approve"), write(approveBody("DPL-6ES7214-0XB0", null))), admin);
        JsonNode err = fail(first);
        JsonNode data = err.path("data");
        assertEquals("CANDIDATE_WILL_MERGE", data.path("errorCode").asText(), err.toString());
        assertEquals(target, data.path("detail").path("targetId").asLong());
        assertEquals(1, data.path("detail").path("references").path("销售订单").asInt());

        ok(call(json(post(CAND + "/" + cand + "/approve"), write(approveBody("DPL-6ES7214-0XB0", true))), admin));
        assertEquals(target, productOfSoLine(so));
        assertEquals(3, jdbc.queryForObject("select archive_status from product where id = ?", Integer.class, pid));
        assertEquals(target, jdbc.queryForObject("select merged_into_id from product where id = ?", Long.class, pid));
        assertEquals(1, jdbc.queryForObject("select count(*) from product_merge where from_product_id = ? and to_product_id = ?",
                Integer.class, pid, target));
        assertEquals(3, jdbc.queryForObject("select status from product_candidate where id = ?", Integer.class, cand));
        // 按原 ID 查得到主商品
        assertEquals(target, productTags.of(List.of(pid)).get(pid).productId());
    }

    @Test
    void actualModel_syncsQuoteAndPi_rejectClearsThem() throws Exception {
        long c = customer("DPL Trading", "Brazil");
        long q = quotation(c, 2, "USD", null, l("DPL ACT", 1, "10", "20"));
        long pi = ok(createPi(quotationItemIds(q))).path("id").asLong();
        long item = jdbc.queryForObject("select inquiry_item_id from quotation_item where quotation_id = ?", Long.class, q);
        ok(call(json(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                INQ + "/" + inquiryOf(q) + "/items/" + item + "/actual-model"), write(Map.of("actualModel", "DPL-ACT-1"))), admin));

        Long pid = jdbc.queryForObject("select product_id from inquiry_item where id = ?", Long.class, item);
        assertTrue(pid != null && pid > 0, "回填真实型号后进池生成候选商品");
        assertEquals(pid, jdbc.queryForObject("select product_id from quotation_item where quotation_id = ?", Long.class, q));
        assertEquals(pid, jdbc.queryForObject("select product_id from proforma_invoice_item where pi_id = ? and deleted_at is null limit 1",
                Long.class, pi));

        // 没有订单使用：可以驳回，报价与 PI 行随之清空
        ok(call(json(post(CAND + "/" + candidateOfProduct(pid) + "/reject"), "{\"reason\":2}"), admin));
        assertNull(jdbc.queryForObject("select product_id from quotation_item where quotation_id = ?", Long.class, q));
        assertNull(jdbc.queryForObject("select product_id from proforma_invoice_item where pi_id = ? and deleted_at is null limit 1",
                Long.class, pi));
        assertEquals(4, jdbc.queryForObject("select archive_status from product where id = ?", Integer.class, pid));
    }

    @Test
    void candidateOfOtherTenant_hiddenInSelector_exactMatchReturnsIdOnly() throws Exception {
        jdbc.update("insert into product (tenant_id, brand_id, brand_key, category_id, mpn_raw, mpn_normalized, mpn_display, product_name, status, archive_status) "
                + "values (0, ?, ?, ?, 'DPL-VIS-1', 'dplvis1', 'DPL-VIS-1', '别家询盘带来的名称', 1, 1)", brand, "#" + brand, category);
        long pid = jdbc.queryForObject("select id from product where mpn_normalized = 'dplvis1'", Long.class);
        jdbc.update("insert into product_candidate (tenant_id, brand_id, brand_text, brand_key, mpn_raw, mpn_normalized, status, level, source_count, product_id) "
                + "values (0, ?, 'DPLBrand', ?, 'DPL-VIS-1', 'dplvis1', 1, 1, 1, ?)", brand, "#" + brand, pid);
        long cand = candidateOfProduct(pid);
        jdbc.update("insert into product_candidate_source (tenant_id, candidate_id, source_type) values (1002, ?, 1)", cand);

        String t1001 = jwtUtils.generateToken(Map.of("tenantId", 1001), "it_sales_admin", 3600);
        redis.opsForValue().set(com.zhul.erp.common.constants.RedisKeyConstants.TOKEN_PREFIX + t1001, "1");
        JsonNode found = ok(call(get(PRODUCTS + "/search").param("keyword", "DPL-VIS"), t1001));
        found.forEach(r -> assertFalse(r.path("id").asLong() == pid, "别家公司的候选不出现在选择器里"));

        JsonNode match = ok(call(get(PRODUCTS + "/match").param("brand", "DPLBrand").param("mpn", "dpl vis 1"), t1001));
        assertEquals(pid, match.path("exact").path("id").asLong(), "候选商品参与精确匹配");
        assertTrue(match.path("exact").path("candidate").asBoolean());
        assertTrue(match.path("exact").path("productName").isNull() || match.path("exact").path("productName").isMissingNode(),
                "别家公司的候选只给 ID 与型号");
        // 有本公司来源的看得到
        String t1002 = jwtUtils.generateToken(Map.of("tenantId", 1002), "it_sales_admin", 3600);
        redis.opsForValue().set(com.zhul.erp.common.constants.RedisKeyConstants.TOKEN_PREFIX + t1002, "1");
        List<Long> ids = new java.util.ArrayList<>();
        ok(call(get(PRODUCTS + "/search").param("keyword", "DPL-VIS"), t1002)).forEach(r -> ids.add(r.path("id").asLong()));
        assertTrue(ids.contains(pid));
    }

    @Test
    void notAModel_cannotConvertToOrder() throws Exception {
        long c = customer("DPL Trading", "Brazil");
        long q = quotation(c, 2, "USD", null, l("Contactor, 32A DPL", 1, "10", "20"));
        long pi = sentPi(quotationItemIds(q), null);
        ok(uploadSlip(pi, "20.00", java.time.LocalDate.now().toString(), admin));
        JsonNode res = call(post(PI + "/" + pi + "/convert"), admin);
        assertTrue(fail(res).path("message").asText().contains("先回填真实型号"));
        assertEquals(0, jdbc.queryForObject("select count(*) from sales_order where pi_id = ?", Integer.class, pi));
    }

    @Test
    void productLibraryShowsArchivedOnly_selectorMarksCandidates() throws Exception {
        long archived = archivedProduct("DPL-SEL-100");
        long so = order("DPL-SEL-200");
        Long candidate = productOfSoLine(so);

        JsonNode page = ok(call(get(PRODUCTS).param("keyword", "DPL-SEL").param("page", "1").param("page_size", "50"), admin));
        List<Long> listed = new java.util.ArrayList<>();
        page.path("records").forEach(r -> listed.add(r.path("id").asLong()));
        assertTrue(listed.contains(archived));
        assertFalse(listed.contains(candidate), "候选商品不出现在商品列表");
        // 发品任务只按已建档商品生成
        int[] created = {-1};
        com.zhul.erp.framework.tenant.TenantContext.runAs(0, () -> created[0] = listingSync.sync(0, List.of(candidate)));
        assertEquals(0, created[0]);
        assertEquals(0, jdbc.queryForObject("select count(*) from listing_task where product_id = ?", Integer.class, candidate));

        // 测试造的询盘型号键不是归一化写法（dpl-sel-200），按共同前缀 DPL 搜
        JsonNode found = ok(call(get(PRODUCTS + "/search").param("keyword", "DPL").param("limit", "50"), admin));
        List<Long> ids = new java.util.ArrayList<>();
        found.forEach(r -> ids.add(r.path("id").asLong()));
        assertTrue(ids.indexOf(archived) >= 0 && ids.indexOf(candidate) > ids.indexOf(archived), "已建档的排在候选前面：" + found);
        assertTrue(found.get(ids.indexOf(candidate)).path("candidate").asBoolean());
        assertFalse(found.get(ids.indexOf(archived)).path("candidate").asBoolean());
    }

    @Test
    void historyBackfill_matchesByModel_andUnlinkedListLinks() throws Exception {
        long archived = archivedProduct("DPL-FX3U-48MR");
        long so = order("DPL-HIST-1");
        long po = generate(supplier("DPL Supplier", "预付"), requirementIds(so), admin);
        // 模拟上线前的历史行（没有来源行、没有商品）：一行能按型号匹配，一行写法不像型号
        jdbc.update("insert into purchase_order_item (tenant_id, po_id, model, brand, description, quantity, unit_price, amount, sort_order) "
                + "values (0, ?, 'dpl fx3u 48mr', 'DPLBrand', '', 1, 1, 1, 8), (0, ?, 'DPL relay, 24V coil', 'DPLBrand', '', 1, 1, 1, 9), "
                + "(0, ?, 'DPL-FX3U-48MR', '', '', 1, 1, 1, 7)", po, po, po);

        TenantScope.platform(() -> linkService.backfill());
        assertEquals(archived, jdbc.queryForObject("select product_id from purchase_order_item where po_id = ? and sort_order = 8",
                Long.class, po));
        assertEquals(archived, jdbc.queryForObject("select product_id from purchase_order_item where po_id = ? and sort_order = 7",
                Long.class, po), "没写品牌：型号在商品库里唯一时直接匹配");
        assertNull(jdbc.queryForObject("select product_id from purchase_order_item where po_id = ? and sort_order = 9", Long.class, po));

        JsonNode list = ok(call(get(CAND + "/unlinked").param("keyword", "relay"), admin));
        assertEquals(1, list.path("total").asInt());
        String key = list.path("records").get(0).path("key").asText();
        assertEquals(1, list.path("records").get(0).path("docs").path("采购订单").asInt());

        ok(call(json(post(CAND + "/unlinked/link"), write(Map.of("key", key, "productId", archived))), admin));
        assertEquals(archived, jdbc.queryForObject("select product_id from purchase_order_item where po_id = ? and sort_order = 9", Long.class, po));
        assertEquals(0, ok(call(get(CAND + "/unlinked").param("keyword", "relay"), admin)).path("total").asInt());
    }
}

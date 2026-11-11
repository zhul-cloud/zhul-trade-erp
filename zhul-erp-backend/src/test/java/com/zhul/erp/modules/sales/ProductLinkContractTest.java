package com.zhul.erp.modules.sales;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 手动订单型号从商品档案选择（spec sales/sales-order「手动创建销售订单」），
 * 报价单、PI、销售订单明细补出商品档案与订货号（spec 各单据「型号明细格式与商品档案跳转」）。
 */
class ProductLinkContractTest extends SalesContractSupport {

    private static final String QT = "/api/v1/quotations";

    private JsonNode createManual(long customer, Map<String, Object> line) throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("customerId", customer);
        m.put("items", List.of(line));
        return call(json(post(SO), write(m)), admin);
    }

    @Test
    void manualOrder_modelMustComeFromProduct_brandAndCategoryFromProduct() throws Exception {
        long customer = customer("Link Buyer", "US");

        // 只有型号文字、没有商品档案：不能下单
        JsonNode noProduct = createManual(customer, Map.of("model", "FREE-TEXT-1", "quantity", 1, "unitPrice", "10"));
        assertEquals("第 1 行请从商品档案选择型号", fail(noProduct).path("message").asText());

        // 已停用、不存在、已删除的商品：不能下单
        long disabled = manualProduct("LINK-OFF-1");
        jdbc.update("update product set status = 0 where id = ?", disabled);
        assertEquals("第 1 行的商品不存在或已停用，请重新选择",
                fail(createManual(customer, Map.of("productId", disabled, "quantity", 1, "unitPrice", "10"))).path("message").asText());
        assertEquals("第 1 行的商品不存在或已停用，请重新选择",
                fail(createManual(customer, Map.of("productId", 987654321L, "quantity", 1, "unitPrice", "10"))).path("message").asText());
        long deleted = manualProduct("LINK-DEL-1");
        jdbc.update("update product set deleted_at = now() where id = ?", deleted);
        assertEquals("第 1 行的商品不存在或已停用，请重新选择",
                fail(createManual(customer, Map.of("productId", deleted, "quantity", 1, "unitPrice", "10"))).path("message").asText());

        // 正常建单：型号、品牌、品类以商品档案为准，传来的型号文字不用
        long product = manualProduct("LINK-OK-1");
        jdbc.update("update product set mpn_display = 'LINK-OK-1 (A)' where id = ?", product);
        long so = ok(createManual(customer, Map.of("productId", product, "model", "随便写的", "quantity", 2, "unitPrice", "59.81")))
                .path("id").asLong();
        JsonNode item = ok(call(get(SO + "/" + so), admin)).path("items").get(0);
        assertEquals("LINK-OK-1 (A)", item.path("model").asText());
        assertEquals("Manual Order Test", item.path("brand").asText());
        assertEquals(product, item.path("productId").asLong());
        assertEquals(product, jdbc.queryForObject("select product_id from sales_order_item where so_id = ?", Long.class, so));
    }

    @Test
    void quotationPiOrderDetail_resolveProduct_fromInquiryItemThenBrandModelMatch() throws Exception {
        long customer = customer("Link Detail Buyer", "US");
        long q = quotation(customer, 2, "USD", null, l("LINK-INQ-1", 1, "100", "200"), l("LINK-MATCH-1", 1, "100", "200"),
                l("LINK-NONE-1", 1, "100", "200"));
        List<Long> qItems = quotationItemIds(q);
        List<Long> inquiryItems = jdbc.queryForList("select inquiry_item_id from quotation_item where quotation_id = ? order by line_no",
                Long.class, q);

        // 第 1 行：询盘型号已建档、有订货号
        long archived = manualProduct("LINK-INQ-1");
        jdbc.update("update inquiry_item set product_id = ?, order_code = 'ORD-001' where id = ?", archived, inquiryItems.get(0));
        // 第 2 行：询盘型号没建档，按品牌 + 型号（忽略大小写、分隔符）匹配商品档案
        jdbc.update("insert into product_brand (tenant_id, brand_name) select 0, 'Siemens' from dual "
                + "where not exists (select 1 from product_brand where tenant_id = 0 and brand_name = 'Siemens')");
        long siemens = jdbc.queryForObject("select min(id) from product_brand where tenant_id = 0 and brand_name = 'Siemens'", Long.class);
        jdbc.update("insert into product (tenant_id, brand_id, mpn_raw, mpn_normalized, mpn_display, product_name, status) "
                + "select 0, ?, 'link match 1', 'linkmatch1', 'LINK MATCH 1', 'x', 1 from dual "
                + "where not exists (select 1 from product where brand_id = ? and mpn_normalized = 'linkmatch1')", siemens, siemens);
        long matched = jdbc.queryForObject("select id from product where brand_id = ? and mpn_normalized = 'linkmatch1'", Long.class, siemens);

        JsonNode quotation = ok(call(get(QT + "/" + q), admin)).path("items");
        assertEquals(archived, quotation.get(0).path("productId").asLong());
        assertEquals("ORD-001", quotation.get(0).path("orderCode").asText());
        assertEquals(matched, quotation.get(1).path("productId").asLong());
        assertTrue(quotation.get(2).path("productId").asLong() == 0, "匹配不到时没有商品档案");

        JsonNode pi = ok(createPi(qItems));
        JsonNode piItems = ok(call(get(PI + "/" + pi.path("id").asLong()), admin)).path("version").path("items");
        assertEquals(archived, piItems.get(0).path("productId").asLong());
        assertEquals("ORD-001", piItems.get(0).path("orderCode").asText());
        assertEquals(matched, piItems.get(1).path("productId").asLong());
        assertEquals(0, piItems.get(2).path("productId").asLong());
    }

    @Test
    void sourceDocRefs_quotationInquiriesAndPiQuotations_carryIdAndNo() throws Exception {
        long customer = customer("Source Ref Buyer", "US");
        long q = quotation(customer, 2, "USD", null, l("SRC-REF-1", 1, "100", "200"));
        long inquiry = inquiryOf(q);
        String inquiryCode = jdbc.queryForObject("select inquiry_code from customer_inquiry where id = ?", String.class, inquiry);
        String quotationNo = jdbc.queryForObject("select quotation_no from quotation where id = ?", String.class, q);

        // 报价单详情与列表：来源单号是询盘单，带 ID 可点开
        JsonNode detail = ok(call(get(QT + "/" + q), admin)).path("inquiries");
        assertEquals(1, detail.size());
        assertEquals(inquiry, detail.get(0).path("id").asLong());
        assertEquals(inquiryCode, detail.get(0).path("no").asText());
        JsonNode qRow = ok(call(json(post(QT + "/page"), write(Map.of("keyword", quotationNo, "page", 1, "pageSize", 20))), admin))
                .path("records").get(0);
        assertEquals(inquiry, qRow.path("inquiries").get(0).path("id").asLong());
        assertEquals(inquiryCode, qRow.path("inquiries").get(0).path("no").asText());

        // 形式发票列表：来源单号是报价单
        JsonNode pi = ok(createPi(quotationItemIds(q)));
        String piNo = jdbc.queryForObject("select pi_no from proforma_invoice where id = ?", String.class, pi.path("id").asLong());
        JsonNode piRow = ok(call(json(post(PI + "/page"), write(Map.of("keyword", piNo, "page", 1, "pageSize", 20))), admin))
                .path("records").get(0);
        assertEquals(q, piRow.path("quotations").get(0).path("id").asLong());
        assertEquals(quotationNo, piRow.path("quotations").get(0).path("no").asText());
    }
}

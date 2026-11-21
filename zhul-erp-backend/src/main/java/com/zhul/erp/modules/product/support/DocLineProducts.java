package com.zhul.erp.modules.product.support;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 单据明细的商品由上一张单据带下来（spec product/document-product-link「单据明细关联商品」）。
 * 生成下游单据时按来源行 ID 取它的 product_id；只按行 ID 定位（行 ID 本身属于某个租户），所以不再追加租户条件。
 */
@Component
@RequiredArgsConstructor
public class DocLineProducts {

    private final NamedParameterJdbcTemplate jdbc;

    public Map<Long, Long> ofInquiryItems(Collection<Long> ids) {
        return of("inquiry_item", ids);
    }

    public Map<Long, Long> ofQuotationItems(Collection<Long> ids) {
        return of("quotation_item", ids);
    }

    public Map<Long, Long> ofSoItems(Collection<Long> ids) {
        return of("sales_order_item", ids);
    }

    public Map<Long, Long> ofRequirements(Collection<Long> ids) {
        return of("purchase_requirement", ids);
    }

    public Map<Long, Long> ofPoItems(Collection<Long> ids) {
        return of("purchase_order_item", ids);
    }

    public Map<Long, Long> ofReceiptItems(Collection<Long> ids) {
        return of("purchase_receipt_item", ids);
    }

    /** 单个来源行的商品，没有时为 null */
    public Long ofSoItem(Long id) {
        return id == null ? null : ofSoItems(List.of(id)).get(id);
    }

    public Long ofPoItem(Long id) {
        return id == null ? null : ofPoItems(List.of(id)).get(id);
    }

    public Long ofReceiptItem(Long id) {
        return id == null ? null : ofReceiptItems(List.of(id)).get(id);
    }

    /**
     * 询盘型号的商品变了（改确认型号、回填真实型号、候选建档 / 驳回）：同步到它还没转成销售订单的报价行与 PI 行。
     * 已转订单的不动，订单及之后的单据按订单当时的商品走。
     */
    public void followInquiryItem(Long inquiryItemId, Long productId) {
        if (inquiryItemId == null) {
            return;
        }
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("item", inquiryItemId).addValue("pid", productId);
        jdbc.update("UPDATE quotation_item SET product_id = :pid WHERE inquiry_item_id = :item AND deleted_at IS NULL"
                + " AND NOT (product_id <=> :pid) AND NOT EXISTS (SELECT 1 FROM sales_order_item s"
                + " WHERE s.quotation_item_id = quotation_item.id AND s.deleted_at IS NULL)", p);
        jdbc.update("UPDATE proforma_invoice_item SET product_id = :pid WHERE inquiry_item_id = :item AND deleted_at IS NULL"
                + " AND NOT (product_id <=> :pid) AND NOT EXISTS (SELECT 1 FROM sales_order_item s"
                + " WHERE s.pi_item_id = proforma_invoice_item.id AND s.deleted_at IS NULL)", p);
    }

    private Map<Long, Long> of(String table, Collection<Long> ids) {
        List<Long> list = ids == null ? List.of() : ids.stream().filter(Objects::nonNull).distinct().toList();
        Map<Long, Long> out = new HashMap<>();
        if (list.isEmpty()) {
            return out;
        }
        jdbc.query("SELECT id, product_id FROM `" + table + "` WHERE id IN (:ids) AND product_id IS NOT NULL",
                new MapSqlParameterSource("ids", list), rs -> {
                    out.put(rs.getLong("id"), rs.getLong("product_id"));
                });
        return out;
    }
}

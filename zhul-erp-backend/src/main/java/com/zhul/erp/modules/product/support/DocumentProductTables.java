package com.zhul.erp.modules.product.support;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 记录 product_id 的单据明细表登记处（spec product/document-product-link）。商品合并、驳回检查、历史回填、
 * 待关联清单都按这里的清单处理。新增带 product_id 的业务表必须登记在 {@link #ENTRIES}，或写明理由放进
 * {@link #NOT_DOCUMENT}，否则 DocumentProductTablesContractTest 失败（做法同 TenantTables）。
 */
public final class DocumentProductTables {

    private DocumentProductTables() {
    }

    /** 来源：本表的 column 指向 parentTable.id，回填时从那一行取 product_id */
    public record Link(String column, String parentTable) {
    }

    /**
     * 一张明细表。
     *
     * @param afterOrder 是否属于销售订单及之后的单据（被引用时候选不能驳回）
     * @param chain      回填时依次尝试的来源
     */
    public record Entry(String table, String label, boolean afterOrder, List<Link> chain) {
    }

    public static final List<Entry> ENTRIES = List.of(
            new Entry("inquiry_item", "询盘型号", false, List.of()),
            new Entry("quotation_item", "报价", false, List.of(new Link("inquiry_item_id", "inquiry_item"))),
            new Entry("proforma_invoice_item", "PI", false,
                    List.of(new Link("quotation_item_id", "quotation_item"), new Link("inquiry_item_id", "inquiry_item"))),
            new Entry("sales_order_item", "销售订单", true,
                    List.of(new Link("pi_item_id", "proforma_invoice_item"), new Link("quotation_item_id", "quotation_item"),
                            new Link("inquiry_item_id", "inquiry_item"))),
            new Entry("purchase_requirement", "采购需求", true, List.of(new Link("so_item_id", "sales_order_item"))),
            new Entry("purchase_order_item", "采购订单", true,
                    List.of(new Link("requirement_id", "purchase_requirement"), new Link("so_item_id", "sales_order_item"))),
            new Entry("supplier_shipment_item", "供应商发货", true,
                    List.of(new Link("po_item_id", "purchase_order_item"), new Link("so_item_id", "sales_order_item"))),
            new Entry("purchase_receipt_item", "采购入库", true,
                    List.of(new Link("po_item_id", "purchase_order_item"), new Link("shipment_item_id", "supplier_shipment_item"),
                            new Link("so_item_id", "sales_order_item"))),
            new Entry("receiving_discrepancy", "到货差异", true,
                    List.of(new Link("receipt_item_id", "purchase_receipt_item"), new Link("po_item_id", "purchase_order_item"))),
            new Entry("outbound_order_item", "销售出库", true, List.of(new Link("so_item_id", "sales_order_item"))),
            new Entry("after_sales_item", "售后单", true, List.of(new Link("so_item_id", "sales_order_item"))),
            new Entry("sales_return_item", "销售退货", true, List.of(new Link("so_item_id", "sales_order_item"))),
            new Entry("return_receipt_item", "退货入库", true, List.of(new Link("so_item_id", "sales_order_item"))),
            new Entry("purchase_return_item", "采购退货", true,
                    List.of(new Link("receipt_item_id", "purchase_receipt_item"), new Link("po_item_id", "purchase_order_item"),
                            new Link("so_item_id", "sales_order_item"))));

    /** 带 product_id 但不是单据明细的表（以 product 开头的商品资料表另行排除） */
    public static final Map<String, String> NOT_DOCUMENT = Map.of(
            "listing_task", "发品任务：只能选已建档商品，候选不会出现在这里，合并不涉及");

    public static Optional<Entry> find(String table) {
        return ENTRIES.stream().filter(e -> e.table().equals(table)).findFirst();
    }
}

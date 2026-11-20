-- 单据统一关联商品（openspec doc-product-link）：报价、PI、采购、仓库、售后、退货的明细记录所引用的商品，由上一张单据带下来
ALTER TABLE `quotation_item`
    ADD COLUMN `product_id` bigint NULL COMMENT '引用的商品ID，关联product.id（报价明细由上一张单据带下来；NULL 表示还没关联）',
    ADD KEY `idx_product_id` (`product_id`);
ALTER TABLE `proforma_invoice_item`
    ADD COLUMN `product_id` bigint NULL COMMENT '引用的商品ID，关联product.id（PI明细由上一张单据带下来；NULL 表示还没关联）',
    ADD KEY `idx_product_id` (`product_id`);
ALTER TABLE `purchase_requirement`
    ADD COLUMN `product_id` bigint NULL COMMENT '引用的商品ID，关联product.id（采购需求明细由上一张单据带下来；NULL 表示还没关联）',
    ADD KEY `idx_product_id` (`product_id`);
ALTER TABLE `purchase_order_item`
    ADD COLUMN `product_id` bigint NULL COMMENT '引用的商品ID，关联product.id（采购订单明细由上一张单据带下来；NULL 表示还没关联）',
    ADD KEY `idx_product_id` (`product_id`);
ALTER TABLE `supplier_shipment_item`
    ADD COLUMN `product_id` bigint NULL COMMENT '引用的商品ID，关联product.id（供应商发货明细由上一张单据带下来；NULL 表示还没关联）',
    ADD KEY `idx_product_id` (`product_id`);
ALTER TABLE `purchase_receipt_item`
    ADD COLUMN `product_id` bigint NULL COMMENT '引用的商品ID，关联product.id（采购入库明细由上一张单据带下来；NULL 表示还没关联）',
    ADD KEY `idx_product_id` (`product_id`);
ALTER TABLE `receiving_discrepancy`
    ADD COLUMN `product_id` bigint NULL COMMENT '引用的商品ID，关联product.id（到货差异明细由上一张单据带下来；NULL 表示还没关联）',
    ADD KEY `idx_product_id` (`product_id`);
ALTER TABLE `outbound_order_item`
    ADD COLUMN `product_id` bigint NULL COMMENT '引用的商品ID，关联product.id（销售出库明细由上一张单据带下来；NULL 表示还没关联）',
    ADD KEY `idx_product_id` (`product_id`);
ALTER TABLE `after_sales_item`
    ADD COLUMN `product_id` bigint NULL COMMENT '引用的商品ID，关联product.id（售后单明细由上一张单据带下来；NULL 表示还没关联）',
    ADD KEY `idx_product_id` (`product_id`);
ALTER TABLE `sales_return_item`
    ADD COLUMN `product_id` bigint NULL COMMENT '引用的商品ID，关联product.id（销售退货明细由上一张单据带下来；NULL 表示还没关联）',
    ADD KEY `idx_product_id` (`product_id`);
ALTER TABLE `return_receipt_item`
    ADD COLUMN `product_id` bigint NULL COMMENT '引用的商品ID，关联product.id（退货入库明细由上一张单据带下来；NULL 表示还没关联）',
    ADD KEY `idx_product_id` (`product_id`);
ALTER TABLE `purchase_return_item`
    ADD COLUMN `product_id` bigint NULL COMMENT '引用的商品ID，关联product.id（采购退货明细由上一张单据带下来；NULL 表示还没关联）',
    ADD KEY `idx_product_id` (`product_id`);

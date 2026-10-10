-- ===========================
-- 采购对象可以是线上店铺；采购需求记录品类
-- 见 openspec/changes/rework-purchase-requirements
-- ===========================
ALTER TABLE `purchase_order`
    ADD COLUMN `channel`   tinyint(2)   NOT NULL DEFAULT 4  COMMENT '采购对象类型（4-供应商；1-淘宝、2-1688、3-闲鱼、5-其他为线上店铺）' AFTER `supplier_id`,
    ADD COLUMN `shop_name` varchar(100) NOT NULL DEFAULT '' COMMENT '线上店铺名称（采购对象为线上店铺时），供应商为空' AFTER `channel`,
    MODIFY COLUMN `supplier_id` bigint NOT NULL DEFAULT 0 COMMENT '供应商ID，关联supplier.id；采购对象为线上店铺时为 0',
    ADD KEY `idx_po_shop` (`tenant_id`, `channel`, `shop_name`);

ALTER TABLE `purchase_requirement`
    ADD COLUMN `category` varchar(32) NOT NULL DEFAULT '' COMMENT '品类（订单型号行快照）' AFTER `brand`;

UPDATE `purchase_requirement` r
JOIN `sales_order_item` i ON i.`id` = r.`so_item_id`
SET r.`category` = i.`category`;

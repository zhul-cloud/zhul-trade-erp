-- 仓库物流单据化（change warehouse-logistics-docs）：发货单、采购入库单、销售出库单、出运单的备注放宽到 500 字
-- 只放宽字段，可回退：改回 300 前先确认没有超过 300 字的备注

ALTER TABLE `supplier_shipment`
    MODIFY COLUMN `note` varchar(500) NOT NULL DEFAULT '' COMMENT '备注';

ALTER TABLE `purchase_receipt`
    MODIFY COLUMN `note` varchar(500) NOT NULL DEFAULT '' COMMENT '差异说明 / 备注';

ALTER TABLE `outbound_order`
    MODIFY COLUMN `note` varchar(500) NOT NULL DEFAULT '' COMMENT '备注';

ALTER TABLE `logistics_shipment`
    MODIFY COLUMN `note` varchar(500) NOT NULL DEFAULT '' COMMENT '备注';

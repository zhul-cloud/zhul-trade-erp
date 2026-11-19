-- V1.5.50：型号备注（openspec item-remark）
-- 业务员在询盘型号上写的备注（如线圈电压、规格要求），只在系统内显示，随型号显示在询价、报价、PI、订单与采购单上。
-- 询盘型号的备注存 inquiry_item.remark（原有字段，之前只有历史迁移写过「成色：…」）；
-- 手动新建、没有询盘的订单型号存 sales_order_item.remark（原有字段，之前没有使用）。
-- 采购单下单后备注有更新时，采购单行标红提醒采购与供应商核对，采购核对后清除。

ALTER TABLE `inquiry_item`
    ADD COLUMN `remark_by` varchar(32) NOT NULL DEFAULT '' COMMENT '型号备注最后修改人' AFTER `remark`,
    ADD COLUMN `remark_at` datetime NULL COMMENT '型号备注最后修改时间' AFTER `remark_by`;

ALTER TABLE `sales_order_item`
    ADD COLUMN `remark_by` varchar(32) NOT NULL DEFAULT '' COMMENT '型号备注最后修改人（只用于没有询盘的手动订单型号）' AFTER `remark`,
    ADD COLUMN `remark_at` datetime NULL COMMENT '型号备注最后修改时间' AFTER `remark_by`;

ALTER TABLE `purchase_order_item`
    ADD COLUMN `remark_alert` tinyint(2) NOT NULL DEFAULT 0 COMMENT '下单后型号备注有更新、待采购核对（0-否、1-是）' AFTER `sort_order`;

-- 回退：
-- ALTER TABLE `inquiry_item` DROP COLUMN `remark_at`, DROP COLUMN `remark_by`;
-- ALTER TABLE `sales_order_item` DROP COLUMN `remark_at`, DROP COLUMN `remark_by`;
-- ALTER TABLE `purchase_order_item` DROP COLUMN `remark_alert`;

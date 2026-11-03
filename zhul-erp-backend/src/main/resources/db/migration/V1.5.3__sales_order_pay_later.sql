-- 先采购后收款（spec sales/sales-order「由 PI 转成销售订单」）：PI 没有水单也没有到账时，选择先采购后收款并填写原因才能转订单
ALTER TABLE `sales_order`
    ADD COLUMN `pay_later` tinyint(2) NOT NULL DEFAULT 0 COMMENT '先采购后收款（0-否、1-是）' AFTER `receipt_status`,
    ADD COLUMN `pay_later_reason` varchar(200) NOT NULL DEFAULT '' COMMENT '先采购后收款的原因' AFTER `pay_later`,
    ADD COLUMN `pay_later_by` bigint NULL COMMENT '选择先采购后收款的用户ID' AFTER `pay_later_reason`,
    ADD COLUMN `pay_later_at` datetime NULL COMMENT '选择先采购后收款的时间' AFTER `pay_later_by`,
    ADD INDEX `idx_sales_order_pay_later` (`pay_later`);

-- 回退：
-- ALTER TABLE `sales_order` DROP INDEX `idx_sales_order_pay_later`, DROP COLUMN `pay_later`, DROP COLUMN `pay_later_reason`,
--     DROP COLUMN `pay_later_by`, DROP COLUMN `pay_later_at`;

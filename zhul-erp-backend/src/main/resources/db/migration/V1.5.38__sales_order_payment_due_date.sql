-- 约定付款日（spec sales/sales-order「订单约定付款日」）：手动订单、费用订单的收款记在订单上，过了约定付款日还没付清算逾期
ALTER TABLE `sales_order`
    ADD COLUMN `payment_due_date` date NULL COMMENT '约定付款日（只用于手动订单、费用订单；NULL 为未约定，不判断逾期）' AFTER `sales_date`,
    ADD INDEX `idx_sales_order_payment_due_date` (`payment_due_date`);

-- 回退：
-- ALTER TABLE `sales_order` DROP INDEX `idx_sales_order_payment_due_date`, DROP COLUMN `payment_due_date`;

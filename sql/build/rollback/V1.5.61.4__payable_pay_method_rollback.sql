-- 应付付款方式 V1.5.61.4 的反向 SQL（备用，只在需要回退该版本时手工执行，执行后还需从 flyway_schema_history 删除 1.5.61.4 的记录）。
ALTER TABLE `fin_payable` DROP KEY `idx_pay_method`, DROP COLUMN `pay_method`;
ALTER TABLE `purchase_order` DROP COLUMN `pay_method`;

-- 采购取消退款 V1.5.51 的反向 SQL（备用，只在需要回退该版本时手工执行，执行后还需从 flyway_schema_history 删除 1.5.51 的记录）。
-- 两列都是新增标记，删除后不影响付款、应付金额；代码回退到上一版即可。
ALTER TABLE `fin_payable` DROP INDEX `idx_fin_payable_refund_expected`, DROP COLUMN `refund_expected_at`;
ALTER TABLE `purchase_order` DROP COLUMN `cancel_refund`;

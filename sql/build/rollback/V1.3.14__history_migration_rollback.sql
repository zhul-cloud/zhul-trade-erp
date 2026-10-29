-- 飞书历史数据迁移 V1.3.14 的反向 SQL（备用，只在需要回退该版本时手工执行，执行后还需从 flyway_schema_history 删除 1.3.14 的记录）。
-- 先按批次回滚迁移数据（软删除）再执行本脚本；fin_fx_settlement.migrated 为 V1.2.61 所加，这里不删。

DROP TABLE IF EXISTS `sales_order_cost_snapshot`;
DROP TABLE IF EXISTS `migration_id_map`;
DROP TABLE IF EXISTS `migration_batch`;

ALTER TABLE `opportunity` DROP COLUMN `migrated`;
ALTER TABLE `customer_inquiry` DROP COLUMN `migrated`;
ALTER TABLE `sourcing_task` DROP COLUMN `migrated`;
ALTER TABLE `sourcing_quote` DROP COLUMN `migrated`;
ALTER TABLE `sales_order` DROP COLUMN `migrated`;
ALTER TABLE `purchase_order` DROP COLUMN `migrated`;
ALTER TABLE `supplier_shipment` DROP COLUMN `migrated`;
ALTER TABLE `logistics_shipment` DROP COLUMN `migrated`;
ALTER TABLE `payment_receipt` DROP COLUMN `migrated`;
ALTER TABLE `fin_customer_refund` DROP COLUMN `migrated`;
ALTER TABLE `fin_expense` DROP COLUMN `migrated`;
ALTER TABLE `fin_other_income` DROP COLUMN `migrated`;
ALTER TABLE `fin_payment` DROP COLUMN `migrated`;

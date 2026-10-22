-- 事业部核算迁移 V1.3.7 的反向 SQL（备用，只在需要回退该版本时手工执行，执行后还需从 flyway_schema_history 删除 1.3.7 的记录）。

DELETE FROM `sys_config` WHERE `config_key` = 'finance.bu.min-share-percent';

ALTER TABLE `fin_other_income` DROP INDEX `idx_business_unit_id`, DROP COLUMN `business_unit_id`;
ALTER TABLE `fin_expense` DROP INDEX `idx_business_unit_id`, DROP COLUMN `business_unit_id`;
ALTER TABLE `fin_fx_settlement` DROP INDEX `idx_business_unit_id`, DROP COLUMN `business_unit_id`;
ALTER TABLE `tenant_bank_account` DROP INDEX `idx_business_unit_id`, DROP COLUMN `business_unit_id`;
ALTER TABLE `payment_receipt` DROP INDEX `idx_business_unit_id`, DROP COLUMN `bu_mismatch`, DROP COLUMN `business_unit_id`;
ALTER TABLE `sales_order` DROP INDEX `idx_business_unit_id`, DROP COLUMN `business_unit_id`;
ALTER TABLE `proforma_invoice` DROP INDEX `idx_business_unit_id`, DROP COLUMN `business_unit_id`;
ALTER TABLE `quotation` DROP INDEX `idx_business_unit_id`, DROP COLUMN `business_unit_id`;
ALTER TABLE `customer_inquiry` DROP INDEX `idx_business_unit_id`, DROP COLUMN `business_unit_id`;
ALTER TABLE `opportunity` DROP INDEX `idx_business_unit_id`, DROP COLUMN `business_unit_id`;
ALTER TABLE `customer` DROP INDEX `idx_business_unit_id`, DROP COLUMN `business_unit_id`;
ALTER TABLE `department` DROP COLUMN `bu_start_month`, DROP COLUMN `is_business_unit`;

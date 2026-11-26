-- 供应商所属采购员 V1.5.61.6 的反向 SQL（备用，只在需要回退该版本时手工执行，执行后还需从 flyway_schema_history 删除 1.5.61.6 的记录）。
ALTER TABLE `supplier` DROP KEY `idx_purchaser_id`, DROP COLUMN `purchaser_id`;

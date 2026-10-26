-- 商品所属租户 V1.3.13 的反向 SQL（备用，只在需要回退该版本时手工执行，执行后还需从 flyway_schema_history 删除 1.3.13 的记录）。

ALTER TABLE `product` DROP INDEX `idx_owner_tenant_id`, DROP COLUMN `owner_tenant_id`;

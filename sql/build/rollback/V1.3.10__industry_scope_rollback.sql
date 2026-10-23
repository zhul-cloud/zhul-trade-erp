-- 商品主数据按行业划分 V1.3.10 的反向 SQL（备用，只在需要回退该版本时手工执行，执行后还需从 flyway_schema_history 删除 1.3.10 的记录）。

DROP TABLE IF EXISTS `product_brand_industry`;
ALTER TABLE `product_category` DROP INDEX `idx_industry_code`, DROP COLUMN `industry_code`;
ALTER TABLE `tenant` DROP INDEX `idx_industry_code`, DROP COLUMN `industry_code`;
DELETE FROM `dict_item` WHERE `tenant_id` = 0 AND `dict_type` = 'industry';
DELETE FROM `dict_type` WHERE `tenant_id` = 0 AND `dict_type` = 'industry';

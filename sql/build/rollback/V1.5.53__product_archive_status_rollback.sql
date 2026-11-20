-- 单据统一关联商品 V1.5.53 的反向 SQL（备用，只在需要回退该版本时手工执行，执行后还需从 flyway_schema_history 删除 1.5.53 的记录）。
-- 注意：候选、已并入、已驳回的商品回退后不再区分，先软删除并给归一化型号加上「~ID」后缀，避开旧唯一键（品牌没识别的候选 brand_id 都是 0）；
-- 单据明细上引用它们的 product_id 由 V1.5.54 的回退一并去掉。
-- 先把旧版本认不得的引用还原：指向已并入商品的改到并入目标，指向候选、已驳回商品的置空（旧版本里候选没有商品）
UPDATE `inquiry_item` i JOIN `product` p ON p.`id` = i.`product_id` SET i.`product_id` = p.`merged_into_id` WHERE p.`archive_status` = 3;
UPDATE `inquiry_item` i JOIN `product` p ON p.`id` = i.`product_id` SET i.`product_id` = NULL WHERE p.`archive_status` IN (1, 4);
UPDATE `sales_order_item` i JOIN `product` p ON p.`id` = i.`product_id` SET i.`product_id` = p.`merged_into_id` WHERE p.`archive_status` = 3;
UPDATE `sales_order_item` i JOIN `product` p ON p.`id` = i.`product_id` SET i.`product_id` = NULL WHERE p.`archive_status` IN (1, 4);
UPDATE `product_candidate` c JOIN `product` p ON p.`id` = c.`product_id` SET c.`product_id` = NULL WHERE p.`archive_status` IN (1, 4);
UPDATE `product` SET `deleted_at` = COALESCE(`deleted_at`, NOW()), `mpn_normalized` = LEFT(CONCAT(`mpn_normalized`, '~', `id`), 128)
WHERE `archive_status` <> 2;
ALTER TABLE `product` DROP INDEX `uk_tenant_brandkey_mpn`, ADD UNIQUE KEY `uk_tenant_brand_mpn` (`tenant_id`, `brand_id`, `mpn_normalized`);
ALTER TABLE `product` DROP KEY `idx_archive_status`, DROP KEY `idx_merged_into_id`,
    DROP COLUMN `merged_into_id`, DROP COLUMN `archive_status`, DROP COLUMN `brand_key`;
-- 合并记录不删：改名留存，重新升级时 V1.5.53 会新建空表，需要时再手工迁回
RENAME TABLE `product_merge` TO `product_merge_bak_v1553`;

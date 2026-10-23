-- 租户维护本公司商品（openspec tenant-product-editing）：商品增加所属租户。
-- 0 为平台；租户新建（含候选审核建档）记该租户，只有所属租户（及平台）能修改。
-- 现有商品按创建人账号推断：create_by 匹配到租户账号（tenant_id <> 0）的记该租户，其余记平台。可重复执行（只推断仍为 0 的）。

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product' AND COLUMN_NAME = 'owner_tenant_id');
SET @s := IF(@c = 0, 'ALTER TABLE `product` ADD COLUMN `owner_tenant_id` int NOT NULL DEFAULT 0 COMMENT ''所属租户（0-平台）；只有所属租户与平台可以修改'' AFTER `tenant_id`, ADD INDEX `idx_owner_tenant_id` (`owner_tenant_id`)', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

UPDATE `product` p
JOIN (SELECT `username`, MIN(`tenant_id`) AS `tenant_id` FROM `account` WHERE `tenant_id` <> 0 GROUP BY `username`) a
  ON a.`username` = p.`create_by`
SET p.`owner_tenant_id` = a.`tenant_id`
WHERE p.`owner_tenant_id` = 0;

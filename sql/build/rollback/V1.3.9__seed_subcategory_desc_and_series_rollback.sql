-- 细分品类简介与系列重整 V1.3.9 的反向 SQL（备用，只在需要回退该版本时手工执行，执行后还需从 flyway_schema_history 删除 1.3.9 的记录）。
-- 按 bak_v139_* 备份恢复：品类简介、系列名称（改名的改回）、简介、状态与软删除时间回到迁移前；
-- 本次新建的系列（不在备份表里）无商品使用的软删除，有商品使用的停用（不物理删除）。

UPDATE `product_category` c JOIN `bak_v139_category_desc` b ON b.`id` = c.`id`
SET c.`description` = b.`description`, c.`description_zh` = b.`description_zh`, c.`update_time` = NOW(), c.`update_by` = 'sys';

UPDATE `product_series` s JOIN `bak_v139_product_series` b ON b.`id` = s.`id`
SET s.`series_name` = b.`series_name`, s.`description` = b.`description`, s.`status` = b.`status`, s.`deleted_at` = b.`deleted_at`,
    s.`update_time` = NOW(), s.`update_by` = 'sys';

UPDATE `product_series` s SET s.`deleted_at` = NOW(), s.`update_time` = NOW(), s.`update_by` = 'sys'
WHERE s.`tenant_id` = 0 AND s.`deleted_at` IS NULL
  AND NOT EXISTS (SELECT 1 FROM `bak_v139_product_series` b WHERE b.`id` = s.`id`)
  AND NOT EXISTS (SELECT 1 FROM `product` p WHERE p.`series_id` = s.`id` AND p.`deleted_at` IS NULL);
UPDATE `product_series` s SET s.`status` = 0, s.`update_time` = NOW(), s.`update_by` = 'sys'
WHERE s.`tenant_id` = 0 AND s.`deleted_at` IS NULL
  AND NOT EXISTS (SELECT 1 FROM `bak_v139_product_series` b WHERE b.`id` = s.`id`);

DROP TABLE IF EXISTS `bak_v139_category_desc`;
DROP TABLE IF EXISTS `bak_v139_product_series`;

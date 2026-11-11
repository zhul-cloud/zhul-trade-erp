-- 平台品牌 Others V1.5.26 的反向 SQL（备用，只在需要回退该版本时手工执行，执行后还需从 flyway_schema_history 删除 1.5.26 的记录）。
-- 先确认没有候选、商品引用 Others；有引用时改为禁用（status=0），不要删除：
--   SELECT COUNT(*) FROM product_candidate WHERE brand_id = (SELECT id FROM product_brand WHERE tenant_id = 0 AND brand_name = 'Others');
--   SELECT COUNT(*) FROM product WHERE brand_id = (SELECT id FROM product_brand WHERE tenant_id = 0 AND brand_name = 'Others');
DELETE FROM `product_brand_industry`
WHERE `brand_id` = (SELECT `id` FROM (SELECT `id` FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Others') t);
DELETE FROM `product_brand` WHERE `tenant_id` = 0 AND `brand_name` = 'Others';

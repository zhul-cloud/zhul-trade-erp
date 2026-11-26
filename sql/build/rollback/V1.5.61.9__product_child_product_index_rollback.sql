-- 商品子表索引 V1.5.61.9 的反向 SQL（备用，只在需要回退该版本时手工执行，执行后还需从 flyway_schema_history 删除 1.5.61.9 的记录）。
ALTER TABLE `product_logistics` DROP KEY `idx_product_id`;
ALTER TABLE `product_customs` DROP KEY `idx_product_id`;
ALTER TABLE `product_reference_price` DROP KEY `idx_product_id`;

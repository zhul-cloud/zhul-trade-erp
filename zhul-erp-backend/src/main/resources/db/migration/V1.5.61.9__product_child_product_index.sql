-- 商品物流、海关、参考价按商品 ID 查询的索引（openspec product-list-media-code）。
-- 三张表只有 (tenant_id, product_id) 唯一键，完整度排序与缺项筛选按 product_id 关联、不带租户条件时用不上，
-- 生产 1.1 万个商品时完整度排序约 5.9 秒；补上单列索引后约 0.15 秒。
ALTER TABLE `product_logistics` ADD KEY `idx_product_id` (`product_id`);
ALTER TABLE `product_customs` ADD KEY `idx_product_id` (`product_id`);
ALTER TABLE `product_reference_price` ADD KEY `idx_product_id` (`product_id`);

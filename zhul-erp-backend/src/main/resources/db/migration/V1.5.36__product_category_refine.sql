-- 商品品类细分（OpenSpec product-category-refine）：AI 细分品类的建议存在商品上，人工采纳后才改商品品类。
ALTER TABLE `product`
    ADD COLUMN `ai_category_id` bigint NOT NULL DEFAULT 0 COMMENT 'AI 建议的细分品类ID，关联product_category.id；0 表示没有建议' AFTER `category_id`,
    ADD COLUMN `ai_category_state` tinyint(2) NOT NULL DEFAULT 0 COMMENT 'AI 细分品类状态（0-无、1-待确认建议、2-已忽略）' AFTER `ai_category_id`,
    ADD COLUMN `ai_category_at` datetime NULL COMMENT 'AI 给出建议的时间；已忽略时为忽略时间' AFTER `ai_category_state`,
    ADD KEY `idx_ai_category_state` (`ai_category_state`);

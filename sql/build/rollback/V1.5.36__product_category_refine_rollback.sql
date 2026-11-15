-- 商品品类细分 V1.5.36 的反向 SQL（备用，只在需要回退该版本时手工执行，执行后还需从 flyway_schema_history 删除 1.5.36 的记录）。
-- 会丢弃尚未采纳的 AI 建议与忽略标记；已采纳的品类保留在 product.category_id，供应链已补齐的需求品类也保留。
ALTER TABLE `product`
    DROP KEY `idx_ai_category_state`,
    DROP COLUMN `ai_category_at`,
    DROP COLUMN `ai_category_state`,
    DROP COLUMN `ai_category_id`;

-- V1.5.26：平台品牌 Others（OpenSpec candidate-batch-edit-others-brand）。
-- 商品候选批量 AI 解析品牌时，AI 没给出品牌或给出的名称在品牌库匹配不上，候选品牌统一填为 Others；
-- 标为非原厂（is_genuine=0），下游不对它使用正品类表述。关联字典 industry 的全部行业，各行业租户都能用；以后新增行业需在品牌维护里补选。
-- 可重复执行；代码按名称「Others」查找，请勿改名。
-- 回退：sql/build/rollback/V1.5.26__brand_others_rollback.sql。
INSERT IGNORE INTO `product_brand` (`tenant_id`, `brand_name`, `is_genuine`, `status`, `create_by`, `update_by`)
VALUES (0, 'Others', 0, 1, 'sys', 'sys');

INSERT IGNORE INTO `product_brand_industry` (`brand_id`, `industry_code`)
SELECT b.`id`, i.`item_code`
FROM `product_brand` b
JOIN `dict_item` i ON i.`dict_type` = 'industry' AND i.`tenant_id` = 0 AND i.`deleted_at` IS NULL
WHERE b.`tenant_id` = 0 AND b.`brand_name` = 'Others' AND b.`deleted_at` IS NULL;

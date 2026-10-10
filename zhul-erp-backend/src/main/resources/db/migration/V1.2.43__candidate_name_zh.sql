-- 商品候选的建议名称改用中文描述（系统内中文）：只改待审核、且有中文描述的候选
UPDATE `product_candidate`
SET `product_name` = LEFT(`description`, 255), `update_time` = NOW()
WHERE `status` = 1 AND `deleted_at` IS NULL AND `description` <> '';

-- 品类匹配到细分品类（openspec match-sub-category）：询盘型号与询价任务关联品类；历史数据不回填，保留原品类文字
ALTER TABLE `inquiry_item`
    ADD COLUMN `category_id` bigint DEFAULT NULL COMMENT '品类ID，关联product_category.id，细分或一级品类；关联不上时为空，只保留品类文字' AFTER `category`,
    MODIFY COLUMN `category` varchar(32) NOT NULL DEFAULT '' COMMENT '品类：关联了品类时为其中文名，否则为原文',
    ADD KEY `idx_category_id` (`category_id`);

ALTER TABLE `sourcing_task`
    ADD COLUMN `category_id` bigint DEFAULT NULL COMMENT '一级品类ID，关联product_category.id；型号没关联品类时为空，按品类文字分组',
    MODIFY COLUMN `category` varchar(32) NOT NULL DEFAULT '' COMMENT '品类：关联了一级品类时为其中文名，否则为型号的品类文字',
    ADD KEY `idx_category_id` (`category_id`);

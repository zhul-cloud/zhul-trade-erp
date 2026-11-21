-- 库存三级账（OpenSpec inventory-category-ledger）：月结快照记下商品结账时所属的一级品类，已结账月份的类目账按它汇总。
-- 一级品类：商品品类没有上级即为一级；有上级取上级；商品没有品类或品类已删除记 0（未分类）。

ALTER TABLE `inv_period_ledger`
    ADD COLUMN `category_l1_id` bigint NOT NULL DEFAULT 0 COMMENT '结账时商品所属的一级品类ID，关联product_category.id；0 表示未分类' AFTER `product_id`,
    ADD KEY `idx_category_l1_id` (`category_l1_id`);

ALTER TABLE `inv_period_cogs`
    ADD COLUMN `category_l1_id` bigint NOT NULL DEFAULT 0 COMMENT '结账时商品所属的一级品类ID，关联product_category.id；0 表示未分类' AFTER `product_id`,
    ADD KEY `idx_category_l1_id` (`category_l1_id`);

-- 已有快照（上线前结过的月份）按商品当前品类回填
UPDATE `inv_period_ledger` l
    JOIN `product` p ON p.`id` = l.`product_id`
    JOIN `product_category` c ON c.`id` = p.`category_id` AND c.`deleted_at` IS NULL
SET l.`category_l1_id` = COALESCE(c.`parent_id`, c.`id`), l.`update_time` = NOW();

UPDATE `inv_period_cogs` g
    JOIN `product` p ON p.`id` = g.`product_id`
    JOIN `product_category` c ON c.`id` = p.`category_id` AND c.`deleted_at` IS NULL
SET g.`category_l1_id` = COALESCE(c.`parent_id`, c.`id`), g.`update_time` = NOW();

-- 回退（手动执行）：
-- ALTER TABLE `inv_period_ledger` DROP KEY `idx_category_l1_id`, DROP COLUMN `category_l1_id`;
-- ALTER TABLE `inv_period_cogs` DROP KEY `idx_category_l1_id`, DROP COLUMN `category_l1_id`;

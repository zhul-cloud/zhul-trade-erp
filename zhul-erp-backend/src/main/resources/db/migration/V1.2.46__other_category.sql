-- 兜底一级品类「其他」（openspec add-other-category）：匹配不到 16 个一级品类的型号归入它；没有二级品类
-- 按编码更新或插入（含已软删除的同编码行，如早期挂在「备件」下的 other），可重复执行
INSERT INTO `product_category` (`tenant_id`, `category_code`, `category_name`, `category_name_zh`, `parent_id`, `sort_order`, `status`, `create_by`, `update_by`)
SELECT * FROM (SELECT 0 AS t, 'other' AS code, 'Others' AS en, '其他' AS zh, NULL AS pid, 170 AS so, 1 AS st, 'sys' AS cb, 'sys' AS ub) AS v
ON DUPLICATE KEY UPDATE `category_name` = v.en, `category_name_zh` = v.zh, `parent_id` = NULL, `sort_order` = v.so,
    `status` = 1, `deleted_at` = NULL, `update_time` = NOW(), `update_by` = 'sys';

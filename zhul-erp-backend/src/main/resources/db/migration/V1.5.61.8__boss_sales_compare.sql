-- 总经理工作台加业务员对比（openspec boss-workbench-sales-compare）：老板模板在最后追加「业务员对比」整行组件，已有时不重复追加。
-- 回退：从 boss 模板的 widgets 里去掉 sales-compare 即可，不涉及其他数据。
UPDATE `workbench_template`
SET `widgets` = JSON_ARRAY_APPEND(`widgets`, '$', JSON_OBJECT('key', 'sales-compare', 'size', 'full')),
    `update_time` = NOW()
WHERE `code` = 'boss'
  AND JSON_SEARCH(`widgets`, 'one', 'sales-compare', NULL, '$[*].key') IS NULL;

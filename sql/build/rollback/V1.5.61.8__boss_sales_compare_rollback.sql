-- 老板工作台业务员对比 V1.5.61.8 的反向 SQL（备用，只在需要回退该版本时手工执行，执行后还需从 flyway_schema_history 删除 1.5.61.8 的记录）。
UPDATE `workbench_template`
SET `widgets` = JSON_REMOVE(`widgets`, JSON_UNQUOTE(JSON_SEARCH(`widgets`, 'one', 'sales-compare', NULL, '$[*].key')) ),
    `update_time` = NOW()
WHERE `code` = 'boss'
  AND JSON_SEARCH(`widgets`, 'one', 'sales-compare', NULL, '$[*].key') IS NOT NULL;

-- 系统参数规范化 V1.3.15 的反向 SQL（备用，只在需要回退该版本时手工执行，执行后还需从 flyway_schema_history 删除 1.3.15 的记录）。
-- 只把本迁移改过的两个键恢复为 NUMBER，其他 INTEGER 配置不动。

UPDATE `sys_config`
SET `config_key` = 'transit.default-days', `update_time` = NOW()
WHERE `config_key` = 'purchase.transit.default-days';

UPDATE `sys_config`
SET `config_type` = 'NUMBER', `update_time` = NOW()
WHERE `config_key` IN ('transit.default-days', 'finance.bu.min-share-percent');

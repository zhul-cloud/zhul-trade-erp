-- 配置分组 V1.5.64 的反向 SQL（备用，只在需要回退该版本时手工执行，执行后还需从 flyway_schema_history 删除 1.5.64 的记录；后端代码需同时回退）。
UPDATE `sys_config`
SET `config_group` = '库存',
    `update_time`  = NOW()
WHERE `config_group` = 'inventory';

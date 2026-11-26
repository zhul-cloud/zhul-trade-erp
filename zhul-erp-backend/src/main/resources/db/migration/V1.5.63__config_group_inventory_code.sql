-- 配置分组统一存小写英文代码（前端 groupLabel 显示中文名）：库存模块原来把分组写成中文「库存」，改为 inventory（openspec ui-redesign 复审 S3）。
-- 只改分组，不动配置键和值；回退见 sql/build/rollback/V1.5.63__config_group_inventory_code_rollback.sql。

UPDATE `sys_config`
SET `config_group` = 'inventory',
    `update_time`  = NOW()
WHERE `config_group` = '库存';

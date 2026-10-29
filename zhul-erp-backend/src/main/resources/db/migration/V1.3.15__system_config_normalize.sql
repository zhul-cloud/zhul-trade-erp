-- 系统参数规范化（openspec system-config-normalize）：
-- 1. 内置配置的键以分组代码为第一段：运输天数 transit.default-days 改名为 purchase.transit.default-days，
--    平台配置与租户副本一起改，已设的天数不变。
-- 2. NUMBER 类型前后端都不支持（可以存进非数字），两项 NUMBER 配置实际按整数读取，统一改为 INTEGER。

UPDATE `sys_config`
SET `config_key` = 'purchase.transit.default-days', `update_time` = NOW()
WHERE `config_key` = 'transit.default-days';

UPDATE `sys_config`
SET `config_type` = 'INTEGER', `update_time` = NOW()
WHERE `config_type` = 'NUMBER';

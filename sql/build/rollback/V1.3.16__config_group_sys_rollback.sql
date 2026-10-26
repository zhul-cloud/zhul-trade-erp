-- 外观配置分组改名 V1.3.16 的反向 SQL（备用，只在需要回退该版本时手工执行，执行后还需从 flyway_schema_history 删除 1.3.16 的记录）。

UPDATE `sys_config`
SET `config_group` = 'appearance', `update_time` = NOW()
WHERE `tenant_id` = 0
  AND `config_group` = 'sys'
  AND `config_key` IN ('sys.site.name', 'sys.site.subtitle', 'sys.site.logo', 'sys.login.background', 'sys.login.footer');

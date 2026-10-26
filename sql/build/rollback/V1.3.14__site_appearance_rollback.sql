-- 外观 V1.3.14 的反向 SQL（备用，只在需要回退该版本时手工执行，执行后还需从 flyway_schema_history 删除 1.3.14 的记录）。
-- 内置配置不走软删除，这里物理删除本迁移写入的平台级外观配置；只删 create_by='sys' 的行，管理员手工建的不受影响。
-- 注意：删除后平台管理员在外观中改过的值也会一并丢失，回退前请先记下。

DELETE FROM `sys_config`
WHERE `tenant_id` = 0
  AND `create_by` = 'sys'
  AND `config_key` IN ('sys.site.name', 'sys.site.subtitle', 'sys.site.logo', 'sys.login.background', 'sys.login.footer');

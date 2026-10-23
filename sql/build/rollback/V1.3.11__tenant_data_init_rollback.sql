-- 租户数据初始化 V1.3.11 的反向 SQL（备用，只在需要回退该版本时手工执行，执行后还需从 flyway_schema_history 删除 1.3.11 的记录）。

DELETE FROM `role_resource` WHERE `resource_id` = 110065;
DELETE FROM `resource` WHERE `id` = 110065;

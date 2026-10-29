-- 使用平台订阅权限 V1.3.22 的反向 SQL（备用，只在需要回退该版本时手工执行，执行后还需从 flyway_schema_history 删除 1.3.22 的记录）。
DELETE FROM `role_resource` WHERE `resource_id` = 110215;
UPDATE `tenant_package` SET `menu_ids` = (
    SELECT JSON_ARRAYAGG(x.`id`) FROM JSON_TABLE(`tenant_package`.`menu_ids`, '$[*]' COLUMNS (`id` INT PATH '$')) x
    WHERE x.`id` <> 110215)
WHERE JSON_CONTAINS(`menu_ids`, '110215');
DELETE FROM `resource` WHERE `id` = 110215;

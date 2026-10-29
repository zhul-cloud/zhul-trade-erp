-- AI 设置菜单 V1.3.18 的反向 SQL（备用，只在需要回退该版本时手工执行，执行后还需从 flyway_schema_history 删除 1.3.18 的记录）。
-- 回退后 AI 设置页面不可进入；sys_ai_config 中各租户的配置保留，AI 任务仍按已有配置运行。

DELETE FROM `role_resource` WHERE `resource_id` IN (100118, 110214);
UPDATE `tenant_package` SET `menu_ids` = (
    SELECT JSON_ARRAYAGG(x.`id`) FROM JSON_TABLE(`tenant_package`.`menu_ids`, '$[*]' COLUMNS (`id` INT PATH '$')) x
    WHERE x.`id` NOT IN (100118, 110214))
WHERE JSON_CONTAINS(`menu_ids`, '100118') OR JSON_CONTAINS(`menu_ids`, '110214');
-- 菜单与按钮由本版本新建，直接删除；这样回退后再次迁移能重新插入
DELETE FROM `resource` WHERE `id` IN (100118, 110214);

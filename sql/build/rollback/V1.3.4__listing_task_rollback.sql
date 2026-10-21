-- 发品任务迁移 V1.3.4 的反向 SQL（备用，只在需要回退该版本时手工执行，执行后还需从 flyway_schema_history 删除 1.3.4 的记录）。
-- 注意：发品记录（平台链接、发布人等）会随表一起删除，回退前先导出 listing_task。

DELETE FROM `role_resource` WHERE `resource_id` IN (100117, 110213);
UPDATE `tenant_package` SET `menu_ids` = (
    SELECT JSON_ARRAYAGG(x.`id`) FROM JSON_TABLE(`tenant_package`.`menu_ids`, '$[*]' COLUMNS (`id` INT PATH '$')) x
    WHERE x.`id` NOT IN (100117, 110213))
WHERE JSON_CONTAINS(`menu_ids`, '100117') OR JSON_CONTAINS(`menu_ids`, '110213');
-- 菜单与按钮由本版本新建，直接删除；这样回退后再次迁移能重新插入（只停用的话迁移的「不存在才插入」会跳过）
DELETE FROM `resource` WHERE `id` IN (100117, 110213);
UPDATE `resource` SET `sort` = 4, `update_time` = NOW(), `update_by` = 'sys' WHERE `id` = 100116 AND `sort` = 5;

DROP TABLE IF EXISTS `listing_task`;

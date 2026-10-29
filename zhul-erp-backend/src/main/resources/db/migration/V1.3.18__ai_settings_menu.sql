-- AI 设置菜单（openspec ai-config-per-tenant）：
-- 菜单「业务设置 → AI 设置」与按钮权限「编辑 AI 设置」，排在快递时效之后；加进所有已有套餐和内置管理员角色。

INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`)
SELECT 100118, 100084, 'RS2100118', 'AI 设置', 2, 7, 'robot', '', '', '', '/system/ai-settings', '', 1, '', 'sys', 'sys'
WHERE NOT EXISTS (SELECT 1 FROM `resource` WHERE `id` = 100118);

INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`)
SELECT 110214, 100118, 'RS3110214', '编辑 AI 设置', 3, 1, '', '', '', '', '', 'system:ai-config:edit', 1, '', 'sys', 'sys'
WHERE NOT EXISTS (SELECT 1 FROM `resource` WHERE `id` = 110214);

UPDATE `tenant_package` SET `menu_ids` = JSON_ARRAY_APPEND(`menu_ids`, '$', 100118)
WHERE `menu_ids` IS NOT NULL AND JSON_VALID(`menu_ids`) AND NOT JSON_CONTAINS(`menu_ids`, '100118');
UPDATE `tenant_package` SET `menu_ids` = JSON_ARRAY_APPEND(`menu_ids`, '$', 110214)
WHERE `menu_ids` IS NOT NULL AND JSON_VALID(`menu_ids`) AND NOT JSON_CONTAINS(`menu_ids`, '110214');
UPDATE `tenant_package` SET `menu_ids` = JSON_ARRAY_APPEND(`menu_ids`, '$', 100084)
WHERE `menu_ids` IS NOT NULL AND JSON_VALID(`menu_ids`) AND NOT JSON_CONTAINS(`menu_ids`, '100084');

INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT r.`code`, s.`id`, s.`code`
FROM `role` r
JOIN `resource` s ON s.`id` IN (100084, 100118, 110214)
WHERE r.`code` = 'ROLE_ADMIN' AND r.`is_built_in` = 1
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = r.`code` AND y.`resource_id` = s.`id`);

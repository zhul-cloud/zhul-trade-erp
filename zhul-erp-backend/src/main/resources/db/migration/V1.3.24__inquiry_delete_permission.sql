-- V1.3.24：询盘单列表的按钮权限「删除询盘」。没有下游单据的询盘可以删除（软删除）。
-- 加进含询盘单列表的套餐，只给内置管理员角色；各公司的其他角色由管理员在角色管理里授权。
INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`)
SELECT 110216, 100051, 'RS3110216', '删除询盘', 3, 1, '', '', '', '', '', 'inquiry:customer:delete', 1, '', 'sys', 'sys'
WHERE NOT EXISTS (SELECT 1 FROM `resource` WHERE `id` = 110216);

UPDATE `tenant_package` SET `menu_ids` = JSON_ARRAY_APPEND(`menu_ids`, '$', 110216)
WHERE `menu_ids` IS NOT NULL AND JSON_VALID(`menu_ids`) AND JSON_CONTAINS(`menu_ids`, '100051') AND NOT JSON_CONTAINS(`menu_ids`, '110216');

INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT r.`code`, s.`id`, s.`code`
FROM `role` r
JOIN `resource` s ON s.`id` = 110216
WHERE r.`code` = 'ROLE_ADMIN' AND r.`is_built_in` = 1
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = r.`code` AND y.`resource_id` = s.`id`);

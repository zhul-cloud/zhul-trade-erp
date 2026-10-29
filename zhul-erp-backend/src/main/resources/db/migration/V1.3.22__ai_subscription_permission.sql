-- V1.3.22：按钮资源「使用平台订阅」（openspec ai-provider-profiles-and-subscription-access）。
-- 平台在套餐中勾选即授权该套餐的公司使用平台订阅；只给平台内置管理员角色，不加入任何现有套餐。
INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`)
SELECT 110215, 100118, 'RS3110215', '使用平台订阅', 3, 2, '', '', '', '', '', 'system:ai-config:use-subscription', 1, '', 'sys', 'sys'
WHERE NOT EXISTS (SELECT 1 FROM `resource` WHERE `id` = 110215);

INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT r.`code`, s.`id`, s.`code`
FROM `role` r
JOIN `resource` s ON s.`id` = 110215
WHERE r.`code` = 'ROLE_ADMIN' AND r.`is_built_in` = 1 AND r.`tenant_id` = 0
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = r.`code` AND y.`resource_id` = s.`id`);

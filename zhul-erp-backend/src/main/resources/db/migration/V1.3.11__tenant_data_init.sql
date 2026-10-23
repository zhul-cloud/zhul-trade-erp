-- 租户数据初始化（openspec tenant-data-init）：租户列表新增按钮权限「数据初始化」tenant:list:init，
-- 与租户列表其他按钮一样授予内置的租户管理员角色（平台超管按 admin_flag 放行）。可重复执行。

INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`)
SELECT 110065, 100021, 'RS3110065', '数据初始化', 3, 5, '', '', '', '', '', 'tenant:list:init', 1, '', 'sys', 'sys'
WHERE NOT EXISTS (SELECT 1 FROM `resource` WHERE `id` = 110065);

INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT r.`code`, s.`id`, s.`code`
FROM `role` r
JOIN `resource` s ON s.`id` = 110065
WHERE r.`code` = 'ROLE_ADMIN' AND r.`is_built_in` = 1
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = r.`code` AND y.`resource_id` = s.`id`);

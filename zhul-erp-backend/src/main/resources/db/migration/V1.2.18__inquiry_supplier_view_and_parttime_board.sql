-- ===========================
-- 1. 货源信息（询价平台、店铺）属于供应链内部信息，加按钮权限「查看货源信息」控制
-- 2. 兼职采购单独的工作台：本人询价统计 + 待办入口
-- ===========================
INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`)
SELECT v.* FROM (
    SELECT 100057 AS id, 100005 AS pid, 'RS2100057' AS code, '兼职工作台' AS name, 2 AS type, 0 AS sort, 'dashboard' AS light_icon,
           '' AS lsi, '' AS di, '' AS dsi, '/inquiry/part-time-board' AS path, '' AS permission, 1 AS status, '' AS micro_app, 'sys' AS cb, 'sys' AS ub
    UNION ALL SELECT 110174, 100056, 'RS3110174', '查看货源信息', 3, 1, '', '', '', '', '', 'inquiry:supplier:view', 1, '', 'sys', 'sys'
) v
WHERE NOT EXISTS (SELECT 1 FROM `resource` r WHERE r.`id` = v.id);

UPDATE `tenant_package`
SET `menu_ids` = JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(`menu_ids`, '$', 100057), '$', 110174)
WHERE JSON_CONTAINS(`menu_ids`, '100055') AND NOT JSON_CONTAINS(`menu_ids`, '100057');

-- 只给内置角色授权：租户管理员与兼职采购。租户自建的角色（采购、业务员等）一律不动，
-- 由管理员在角色管理中按需勾选「查看货源信息」——不能按角色名称或现有菜单去猜谁是采购
INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT r.`code`, 110174, 'RS3110174'
FROM `role` r
WHERE r.`code` IN ('ROLE_ADMIN', 'ROLE_PTBUYER') AND r.`is_built_in` = 1
  AND NOT EXISTS (SELECT 1 FROM `role_resource` x WHERE x.`role_code` = r.`code` AND x.`resource_id` = 110174);

-- 兼职采购：工作台作为登录后首页（历史询价是公司整体价格，不对兼职开放）
INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT 'ROLE_PTBUYER', r.`id`, r.`code` FROM `resource` r
WHERE r.`id` = 100057
  AND NOT EXISTS (SELECT 1 FROM `role_resource` x WHERE x.`role_code` = 'ROLE_PTBUYER' AND x.`resource_id` = r.`id`);

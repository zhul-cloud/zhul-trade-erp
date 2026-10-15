-- 销售统计与询价统计（openspec add-sales-funnel-stats）：只读统计页，不加按钮。
-- 销售统计授给有「销售订单」菜单的角色，询价统计授给有「询价分配」菜单的角色，租户管理员都授。

INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`)
SELECT v.* FROM (
              SELECT 100113 AS id, 100081 AS pid, 'RS2100113' AS code, '销售统计' AS name, 2 AS type, 7 AS sort, 'bar-chart' AS li, '' AS lsi, '' AS di, '' AS dsi,
                     '/sales/stats' AS path, '' AS permission, 1 AS status, '' AS micro_app, 'sys' AS cb, 'sys' AS ub
    UNION ALL SELECT 100114, 100082, 'RS2100114', '询价统计', 2, 9, 'pie-chart', '', '', '', '/inquiry/sourcing-stats', '', 1, '', 'sys', 'sys'
) v
WHERE NOT EXISTS (SELECT 1 FROM `resource` r WHERE r.`id` = v.id);

-- 采购管理：询价统计排在砍价统计之前
UPDATE `resource` SET `sort` = 10, `update_time` = NOW() WHERE `id` = 100108;

UPDATE `tenant_package`
SET `menu_ids` = JSON_ARRAY_APPEND(`menu_ids`, '$', 100113)
WHERE JSON_CONTAINS(`menu_ids`, '100079') AND NOT JSON_CONTAINS(`menu_ids`, '100113');

UPDATE `tenant_package`
SET `menu_ids` = JSON_ARRAY_APPEND(`menu_ids`, '$', 100114)
WHERE JSON_CONTAINS(`menu_ids`, '100054') AND NOT JSON_CONTAINS(`menu_ids`, '100114');

INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT x.`role_code`, s.`id`, s.`code`
FROM `role_resource` x
JOIN `resource` s ON s.`id` = 100113
WHERE x.`resource_id` = 100079
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = x.`role_code` AND y.`resource_id` = s.`id`);

INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT x.`role_code`, s.`id`, s.`code`
FROM `role_resource` x
JOIN `resource` s ON s.`id` = 100114
WHERE x.`resource_id` = 100054
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = x.`role_code` AND y.`resource_id` = s.`id`);

INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT r.`code`, s.`id`, s.`code`
FROM `role` r
JOIN `resource` s ON s.`id` IN (100113, 100114)
WHERE r.`code` = 'ROLE_ADMIN' AND r.`is_built_in` = 1
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = r.`code` AND y.`resource_id` = s.`id`);

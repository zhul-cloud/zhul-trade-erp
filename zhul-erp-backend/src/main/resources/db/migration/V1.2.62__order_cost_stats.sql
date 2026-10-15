-- 订单实际成本与统计（openspec add-order-actual-cost，供应链第③期 ③c）：
-- 菜单「采购管理 → 砍价统计」「财务管理 → 利润统计」（只读，不加按钮）。
-- 砍价统计授给有「询价分配」菜单的角色（采购负责人），利润统计授给有「收款单」菜单的角色，租户管理员都授。

INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`)
SELECT v.* FROM (
              SELECT 100108 AS id, 100082 AS pid, 'RS2100108' AS code, '砍价统计' AS name, 2 AS type, 9 AS sort, 'fall' AS li, '' AS lsi, '' AS di, '' AS dsi,
                     '/purchase/bargain-stats' AS path, '' AS permission, 1 AS status, '' AS micro_app, 'sys' AS cb, 'sys' AS ub
    UNION ALL SELECT 100109, 100083, 'RS2100109', '利润统计', 2, 7, 'line-chart', '', '', '', '/finance/profit-stats', '', 1, '', 'sys', 'sys'
) v
WHERE NOT EXISTS (SELECT 1 FROM `resource` r WHERE r.`id` = v.id);

-- 套餐：含询价分配的补砍价统计；含收款单的补利润统计
UPDATE `tenant_package`
SET `menu_ids` = JSON_ARRAY_APPEND(`menu_ids`, '$', 100108)
WHERE JSON_CONTAINS(`menu_ids`, '100054') AND NOT JSON_CONTAINS(`menu_ids`, '100108');

UPDATE `tenant_package`
SET `menu_ids` = JSON_ARRAY_APPEND(`menu_ids`, '$', 100109)
WHERE JSON_CONTAINS(`menu_ids`, '100085') AND NOT JSON_CONTAINS(`menu_ids`, '100109');

-- 角色
INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT x.`role_code`, s.`id`, s.`code`
FROM `role_resource` x
JOIN `resource` s ON s.`id` = 100108
WHERE x.`resource_id` = 100054
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = x.`role_code` AND y.`resource_id` = s.`id`);

INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT x.`role_code`, s.`id`, s.`code`
FROM `role_resource` x
JOIN `resource` s ON s.`id` = 100109
WHERE x.`resource_id` = 100085
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = x.`role_code` AND y.`resource_id` = s.`id`);

INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT r.`code`, s.`id`, s.`code`
FROM `role` r
JOIN `resource` s ON s.`id` IN (100108, 100109)
WHERE r.`code` = 'ROLE_ADMIN' AND r.`is_built_in` = 1
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = r.`code` AND y.`resource_id` = s.`id`);

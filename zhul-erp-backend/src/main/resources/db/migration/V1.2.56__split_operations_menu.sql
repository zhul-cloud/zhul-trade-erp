-- 运营管理拆分（openspec split-operations-menu）：
-- 新增一级菜单「运营管理」→「商品运营」与按钮「营销内容维护」；商品资料下「内容任务」改名「资料任务」并改路径。
-- 授权迁移保证现有能力不变：有「内容任务」菜单的同时获得运营菜单，有「商品内容维护」的同时获得「营销内容维护」。

INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`)
SELECT v.* FROM (
              SELECT 100101 AS id, 0 AS pid, 'RS1100101' AS code, '运营管理' AS name, 1 AS type, 6 AS sort, 'fund' AS li, '' AS lsi, '' AS di, '' AS dsi,
                     '/operations' AS path, '' AS permission, 1 AS status, '' AS micro_app, 'sys' AS cb, 'sys' AS ub
    UNION ALL SELECT 100102, 100101, 'RS2100102', '商品运营', 2, 1, 'shop', '', '', '', '/operations/products', '', 1, '', 'sys', 'sys'
    UNION ALL SELECT 110203, 100102, 'RS3110203', '营销内容维护', 3, 1, '', '', '', '', '', 'operations:marketing:edit', 1, '', 'sys', 'sys'
) v
WHERE NOT EXISTS (SELECT 1 FROM `resource` r WHERE r.`id` = v.id);

UPDATE `resource`
SET `name` = '资料任务', `path` = '/product/fact-tasks', `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = 100100;

-- 套餐：含内容任务的补运营管理与商品运营，含商品内容维护的补营销内容维护
UPDATE `tenant_package`
SET `menu_ids` = JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(`menu_ids`, '$', 100101), '$', 100102)
WHERE JSON_CONTAINS(`menu_ids`, '100100') AND NOT JSON_CONTAINS(`menu_ids`, '100102');

UPDATE `tenant_package`
SET `menu_ids` = JSON_ARRAY_APPEND(`menu_ids`, '$', 110203)
WHERE JSON_CONTAINS(`menu_ids`, '110202') AND NOT JSON_CONTAINS(`menu_ids`, '110203');

-- 角色：有内容任务菜单的补运营菜单；有商品内容维护的补营销内容维护
INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT x.`role_code`, s.`id`, s.`code`
FROM `role_resource` x
JOIN `resource` s ON s.`id` IN (100101, 100102)
WHERE x.`resource_id` = 100100
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = x.`role_code` AND y.`resource_id` = s.`id`);

INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT x.`role_code`, s.`id`, s.`code`
FROM `role_resource` x
JOIN `resource` s ON s.`id` = 110203
WHERE x.`resource_id` = 110202
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = x.`role_code` AND y.`resource_id` = s.`id`);

-- 内置租户管理员：全部
INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT r.`code`, s.`id`, s.`code`
FROM `role` r
JOIN `resource` s ON s.`id` IN (100101, 100102, 110203)
WHERE r.`code` = 'ROLE_ADMIN' AND r.`is_built_in` = 1
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = r.`code` AND y.`resource_id` = s.`id`);

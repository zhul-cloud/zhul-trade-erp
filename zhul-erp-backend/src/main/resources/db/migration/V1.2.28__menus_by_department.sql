-- ===========================
-- 菜单按部门分组：只改分组、名称、排序与隐藏，页面路径与接口权限不变
-- 见 openspec/changes/reorganize-menus-by-department（回滚 SQL 在变更目录 snapshots/rollback.sql）
-- 侧边栏会自动补出已授权页面的全部上级，所以新分组不需要单独授权；
-- 侧边栏不看 status，只看 is_hidden，所以旧分组同时停用并隐藏
-- ===========================

-- ---------------------------------------------------------------- 新分组、新菜单与按钮
INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`)
SELECT v.* FROM (
    SELECT 100081 AS id, 0 AS pid, 'RS1100081' AS code, '业务管理' AS name, 1 AS type, 2 AS sort, 'solution' AS li, '' AS lsi, '' AS di, '' AS dsi,
           '/business' AS path, '' AS permission, 1 AS status, '' AS micro_app, 'sys' AS cb, 'sys' AS ub
    UNION ALL SELECT 100082, 0,      'RS1100082', '采购管理', 1, 3, 'shoppingCart', '', '', '', '/purchase', '', 1, '', 'sys', 'sys'
    UNION ALL SELECT 100083, 0,      'RS1100083', '财务管理', 1, 5, 'wallet',       '', '', '', '/finance',  '', 1, '', 'sys', 'sys'
    UNION ALL SELECT 100084, 0,      'RS1100084', '业务设置', 1, 7, 'control',      '', '', '', '/settings', '', 1, '', 'sys', 'sys'
    UNION ALL SELECT 100085, 100083, 'RS2100085', '到账登记', 2, 1, 'audit',        '', '', '', '/finance/receipts', '', 1, '', 'sys', 'sys'
    UNION ALL SELECT 100086, 100084, 'RS2100086', '单据编号', 2, 5, 'number',       '', '', '', '/system/document-numbering', '', 1, '', 'sys', 'sys'
    UNION ALL SELECT 110182, 100086, 'RS3110182', '编辑单据编号', 3, 1, '', '', '', '', '', 'system:document-numbering:edit', 1, '', 'sys', 'sys'
) v
WHERE NOT EXISTS (SELECT 1 FROM `resource` r WHERE r.`id` = v.id);

-- ---------------------------------------------------------------- 菜单改挂、改名与排序
UPDATE `resource` r
JOIN (
              SELECT 100001 AS id, 0 AS pid, '工作台' AS name, 1 AS sort, 0 AS hidden
    -- 业务管理
    UNION ALL SELECT 100053, 100081, '商机',     1, 0
    UNION ALL SELECT 100061, 100081, '客户',     2, 0
    UNION ALL SELECT 100051, 100081, '客户询盘', 3, 0
    UNION ALL SELECT 100073, 100081, '报价单',   4, 0
    UNION ALL SELECT 100078, 100081, 'PI',       5, 0
    UNION ALL SELECT 100079, 100081, '销售订单', 6, 0
    UNION ALL SELECT 100071, 100081, '商机统计', 9, 1
    -- 采购管理
    UNION ALL SELECT 100057, 100082, '兼职看板', 1, 0
    UNION ALL SELECT 100054, 100082, '询价分配', 2, 0
    UNION ALL SELECT 100055, 100082, '我的询价', 3, 0
    UNION ALL SELECT 100056, 100082, '历史询价', 4, 0
    UNION ALL SELECT 100062, 100082, '供应商',   5, 0
    -- 商品资料
    UNION ALL SELECT 100004, 0,      '商品资料', 6, 0
    UNION ALL SELECT 100034, 100004, '商品',     1, 0
    UNION ALL SELECT 100031, 100004, '品牌',     2, 0
    UNION ALL SELECT 100032, 100004, '品类',     3, 0
    UNION ALL SELECT 100033, 100004, '系列',     4, 0
    -- 业务设置
    UNION ALL SELECT 100074, 100084, '定价策略', 1, 0
    UNION ALL SELECT 100075, 100084, '汇率',     2, 0
    UNION ALL SELECT 100080, 100084, '收款账户', 3, 0
    UNION ALL SELECT 100076, 100084, '单据模版', 4, 0
    -- 系统管理
    UNION ALL SELECT 100002, 0,      '系统管理', 98, 0
    UNION ALL SELECT 100011, 100002, '用户',     1, 0
    UNION ALL SELECT 100012, 100002, '角色',     2, 0
    UNION ALL SELECT 100014, 100002, '部门',     3, 0
    UNION ALL SELECT 100015, 100002, '岗位',     4, 0
    UNION ALL SELECT 100013, 100002, '菜单',     5, 0
    UNION ALL SELECT 100016, 100002, '字典',     6, 0
    UNION ALL SELECT 100017, 100002, '系统设置', 7, 0
    UNION ALL SELECT 100018, 100002, '操作日志', 8, 0
    UNION ALL SELECT 100019, 100002, '登录日志', 9, 0
    -- 租户管理（平台）
    UNION ALL SELECT 100003, 0,      '租户管理', 99, 0
    UNION ALL SELECT 100021, 100003, '租户',     1, 0
    UNION ALL SELECT 100022, 100003, '套餐',     2, 0
) m ON m.id = r.`id`
SET r.`pid` = m.pid, r.`name` = m.name, r.`sort` = m.sort, r.`is_hidden` = m.hidden, r.`update_time` = NOW(), r.`update_by` = 'sys';

-- 旧分组（商机管理、询盘管理、报价中心、销售管理、客户管理、供应商管理）停用并隐藏，保留记录便于回滚
UPDATE `resource` SET `status` = 0, `is_hidden` = 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` IN (100007, 100005, 100072, 100077, 100006, 100008);

-- ---------------------------------------------------------------- 套餐与授权
UPDATE `tenant_package`
SET `menu_ids` = JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(`menu_ids`, '$', 100085), '$', 100086), '$', 110182)
WHERE `name` IN ('标准版', '旗舰版') AND NOT JSON_CONTAINS(`menu_ids`, '100085');

-- 到账登记：内置租户管理员，以及已有「登记到账」按钮的角色（现阶段由总经理兼任财务）
INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT r.`code`, 100085, 'RS2100085'
FROM `role` r
WHERE ((r.`code` = 'ROLE_ADMIN' AND r.`is_built_in` = 1)
       OR EXISTS (SELECT 1 FROM `role_resource` x WHERE x.`role_code` = r.`code` AND x.`resource_id` = 110180))
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = r.`code` AND y.`resource_id` = 100085);

-- 单据编号与编辑按钮：内置租户管理员，以及原来能改单据前缀（有「编辑配置」）的角色
INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT r.`code`, s.`id`, s.`code`
FROM `role` r
JOIN `resource` s ON s.`id` IN (100086, 110182)
WHERE ((r.`code` = 'ROLE_ADMIN' AND r.`is_built_in` = 1)
       OR EXISTS (SELECT 1 FROM `role_resource` x WHERE x.`role_code` = r.`code` AND x.`resource_id` = 110072))
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = r.`code` AND y.`resource_id` = s.`id`);

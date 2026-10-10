-- V1.2.14：「客商管理」拆为两个一级菜单「客户管理」「供应商管理」，排在询盘管理之后，
-- 后续客户评分、供应商评分分别挂到各自目录下。
-- 原目录 100006 改为「客户管理」目录（编号不变，已有角色授权继续有效）；新增 100008「供应商管理」目录。

UPDATE `resource`
SET `name` = '客户管理', `light_icon` = 'solution', `path` = '/customer', `sort` = 4,
    `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = 100006;

INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`) VALUES
(100008, 0, 'RS1100008', '供应商管理', 1, 5, 'shop', '', '', '', '/supplier', '', 1, '', 'sys', 'sys');

UPDATE `resource`
SET `name` = '客户列表', `light_icon` = 'unorderedList', `path` = '/customer/list', `sort` = 1,
    `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = 100061;

UPDATE `resource`
SET `pid` = 100008, `name` = '供应商列表', `light_icon` = 'unorderedList', `path` = '/supplier/list', `sort` = 1,
    `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = 100062;

-- 套餐里有供应商菜单的，补上新目录
UPDATE `tenant_package`
SET `menu_ids` = JSON_ARRAY_APPEND(`menu_ids`, '$', 100008)
WHERE JSON_CONTAINS(`menu_ids`, '100062') AND NOT JSON_CONTAINS(`menu_ids`, '100008');

-- 能看供应商的角色补上新目录，升级后看到的内容不变
INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT rr.`role_code`, 100008, 'RS1100008'
FROM `role_resource` rr
WHERE rr.`resource_id` = 100062
  AND NOT EXISTS (SELECT 1 FROM `role_resource` x WHERE x.`role_code` = rr.`role_code` AND x.`resource_id` = 100008);

-- 原目录现在只代表「客户管理」：只授权了供应商、没授权客户的角色，去掉这条目录授权，
-- 否则侧边栏会多出一个没有子菜单的「客户管理」
DELETE rr FROM `role_resource` rr
WHERE rr.`resource_id` = 100006
  AND NOT EXISTS (SELECT 1 FROM (SELECT `role_code` FROM `role_resource` WHERE `resource_id` = 100061) c
                  WHERE c.`role_code` = rr.`role_code`);

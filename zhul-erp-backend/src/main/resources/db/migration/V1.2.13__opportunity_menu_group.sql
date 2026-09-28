-- V1.2.13：商机管理从「询盘管理」下拆出为独立的一级菜单，下设「商机列表」「商机统计」，排在询盘管理之前。
-- 见 openspec/changes/add-opportunity-management/design.md 第 8 节。

INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`) VALUES
(100007, 0,      'RS1100007', '商机管理', 1, 2, 'userAdd', '', '', '', '/crm',                    '', 1, '', 'sys', 'sys'),
(100071, 100007, 'RS2100071', '商机统计', 2, 2, 'barChart', '', '', '', '/crm/opportunity-stats', '', 1, '', 'sys', 'sys');

-- 原「商机管理」菜单改为「商机列表」挂到新目录下；编号不变，已有的角色授权与按钮权限继续有效
UPDATE `resource`
SET `pid` = 100007, `name` = '商机列表', `sort` = 1, `light_icon` = 'unorderedList', `path` = '/crm/opportunities',
    `update_time` = NOW(), `update_by` = 'sys'
WHERE `id` = 100053;

-- 询盘管理、客商管理顺延一位
UPDATE `resource` SET `sort` = 3, `update_time` = NOW(), `update_by` = 'sys' WHERE `id` = 100005;
UPDATE `resource` SET `sort` = 4, `update_time` = NOW(), `update_by` = 'sys' WHERE `id` = 100006;

UPDATE `tenant_package`
SET `menu_ids` = JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(`menu_ids`, '$', 100007), '$', 100071)
WHERE `name` IN ('标准版', '旗舰版');

-- 已经能看商机列表的角色，同时授予新目录与商机统计，升级后看到的内容不变
INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT rr.`role_code`, r.`id`, r.`code`
FROM `role_resource` rr
JOIN `resource` r ON r.`id` IN (100007, 100071)
WHERE rr.`resource_id` = 100053
  AND NOT EXISTS (SELECT 1 FROM `role_resource` x WHERE x.`role_code` = rr.`role_code` AND x.`resource_id` = r.`id`);

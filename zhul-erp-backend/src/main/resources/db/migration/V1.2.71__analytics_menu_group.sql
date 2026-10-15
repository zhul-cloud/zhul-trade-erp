-- 菜单调整（openspec analytics-menu-group）：
-- 1. 新增分组「统计分析」，各业务分组下的只读统计归到这里：销售统计、询价统计、砍价统计、利润统计、经营统计、商机统计（商机统计改为显示）；
-- 2. 商品候选、资料任务从商品管理移到运营管理，「商品运营」改名「营销任务」。
-- 只改归属、排序与名称，页面地址、权限码与已有授权不变；补授分组菜单本身，保证侧边栏能看到分组。

INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`)
SELECT 100115, 0, 'RS1100115', '统计分析', 1, 7, 'barChart', '', '', '', '/analytics', '', 1, '', 'sys', 'sys'
WHERE NOT EXISTS (SELECT 1 FROM `resource` WHERE `id` = 100115);

-- 统计分析排在运营管理之后、业务设置之前
UPDATE `resource` SET `sort` = 8, `update_time` = NOW(), `update_by` = 'sys' WHERE `id` = 100084;

UPDATE `resource` SET `pid` = 100115, `sort` = 1, `update_time` = NOW(), `update_by` = 'sys' WHERE `id` = 100113;
UPDATE `resource` SET `pid` = 100115, `sort` = 2, `update_time` = NOW(), `update_by` = 'sys' WHERE `id` = 100114;
UPDATE `resource` SET `pid` = 100115, `sort` = 3, `update_time` = NOW(), `update_by` = 'sys' WHERE `id` = 100108;
UPDATE `resource` SET `pid` = 100115, `sort` = 4, `update_time` = NOW(), `update_by` = 'sys' WHERE `id` = 100109;
UPDATE `resource` SET `pid` = 100115, `sort` = 5, `update_time` = NOW(), `update_by` = 'sys' WHERE `id` = 100112;
UPDATE `resource` SET `pid` = 100115, `sort` = 6, `is_hidden` = 0, `update_time` = NOW(), `update_by` = 'sys' WHERE `id` = 100071;

UPDATE `resource` SET `pid` = 100101, `sort` = 1, `update_time` = NOW(), `update_by` = 'sys' WHERE `id` = 100099;
UPDATE `resource` SET `pid` = 100101, `sort` = 2, `update_time` = NOW(), `update_by` = 'sys' WHERE `id` = 100100;
UPDATE `resource` SET `name` = '营销任务', `sort` = 3, `update_time` = NOW(), `update_by` = 'sys' WHERE `id` = 100102;

-- 套餐：含任一统计菜单的补「统计分析」；含商品候选或资料任务的补「运营管理」
UPDATE `tenant_package`
SET `menu_ids` = JSON_ARRAY_APPEND(`menu_ids`, '$', 100115)
WHERE (JSON_CONTAINS(`menu_ids`, '100113') OR JSON_CONTAINS(`menu_ids`, '100114') OR JSON_CONTAINS(`menu_ids`, '100108')
       OR JSON_CONTAINS(`menu_ids`, '100109') OR JSON_CONTAINS(`menu_ids`, '100112') OR JSON_CONTAINS(`menu_ids`, '100071'))
  AND NOT JSON_CONTAINS(`menu_ids`, '100115');

UPDATE `tenant_package`
SET `menu_ids` = JSON_ARRAY_APPEND(`menu_ids`, '$', 100101)
WHERE (JSON_CONTAINS(`menu_ids`, '100099') OR JSON_CONTAINS(`menu_ids`, '100100'))
  AND NOT JSON_CONTAINS(`menu_ids`, '100101');

-- 角色：同上补分组菜单
INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT x.`role_code`, s.`id`, s.`code`
FROM `role_resource` x
JOIN `resource` s ON s.`id` = 100115
WHERE x.`resource_id` IN (100113, 100114, 100108, 100109, 100112, 100071)
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = x.`role_code` AND y.`resource_id` = s.`id`);

INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT x.`role_code`, s.`id`, s.`code`
FROM `role_resource` x
JOIN `resource` s ON s.`id` = 100101
WHERE x.`resource_id` IN (100099, 100100)
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = x.`role_code` AND y.`resource_id` = s.`id`);

INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT r.`code`, s.`id`, s.`code`
FROM `role` r
JOIN `resource` s ON s.`id` IN (100115, 100101)
WHERE r.`code` = 'ROLE_ADMIN' AND r.`is_built_in` = 1
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = r.`code` AND y.`resource_id` = s.`id`);

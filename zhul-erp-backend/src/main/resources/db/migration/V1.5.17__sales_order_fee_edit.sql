-- V1.5.17：销售订单按钮权限「修改订单费用」（2026-11-05 反馈第 15 条）。
-- 手动创建的订单可以事后修改费用行（如漏填的运费），每次修改写操作日志。
-- 加进含销售订单菜单的套餐；授给内置租户管理员和名称含「总经理」的角色，其他角色由管理员在角色管理里授权。
-- 回退：DELETE FROM `role_resource` WHERE `resource_id` = 110229;
--       从各套餐的 menu_ids 里去掉 110229（与加入时相反）；
--       DELETE FROM `resource` WHERE `id` = 110229;
INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`)
SELECT 110229, 100079, 'RS3110229', '修改订单费用', 3, 5, '', '', '', '', '', 'sales:order:fee-edit', 1, '', 'sys', 'sys'
WHERE NOT EXISTS (SELECT 1 FROM `resource` WHERE `id` = 110229);

UPDATE `tenant_package` SET `menu_ids` = JSON_ARRAY_APPEND(`menu_ids`, '$', 110229)
WHERE `menu_ids` IS NOT NULL AND JSON_VALID(`menu_ids`) AND JSON_CONTAINS(`menu_ids`, '100079') AND NOT JSON_CONTAINS(`menu_ids`, '110229');

INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT r.`code`, 110229, 'RS3110229'
FROM `role` r
WHERE ((r.`code` = 'ROLE_ADMIN' AND r.`is_built_in` = 1) OR r.`name` LIKE '%总经理%')
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = r.`code` AND y.`resource_id` = 110229);

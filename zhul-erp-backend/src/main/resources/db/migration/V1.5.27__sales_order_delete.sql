-- V1.5.27：销售订单删除（2026-11-11 反馈「销售订单支持删除，需要按钮级别权限控制」，OpenSpec sales-order-delete）。
-- sales_order 已有 deleted_at，补删除人、删除原因；新增按钮权限「删除订单」挂在销售订单菜单 100079 下。
-- 加进含销售订单菜单的套餐；授给内置租户管理员和名称含「总经理」的角色，其他角色由管理员在角色管理里授权。
-- 回退见 sql/build/rollback/V1.5.27__sales_order_delete_rollback.sql
ALTER TABLE `sales_order`
    ADD COLUMN `deleted_by`    bigint       NULL               COMMENT '删除人用户ID' AFTER `deleted_at`,
    ADD COLUMN `delete_reason` varchar(200) NOT NULL DEFAULT '' COMMENT '删除原因' AFTER `deleted_by`;

INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`)
SELECT 110234, 100079, 'RS3110234', '删除订单', 3, 6, '', '', '', '', '', 'sales:order:delete', 1, '', 'sys', 'sys'
WHERE NOT EXISTS (SELECT 1 FROM `resource` WHERE `id` = 110234);

UPDATE `tenant_package` SET `menu_ids` = JSON_ARRAY_APPEND(`menu_ids`, '$', 110234)
WHERE `menu_ids` IS NOT NULL AND JSON_VALID(`menu_ids`) AND JSON_CONTAINS(`menu_ids`, '100079') AND NOT JSON_CONTAINS(`menu_ids`, '110234');

INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT r.`code`, 110234, 'RS3110234'
FROM `role` r
WHERE ((r.`code` = 'ROLE_ADMIN' AND r.`is_built_in` = 1) OR r.`name` LIKE '%总经理%')
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = r.`code` AND y.`resource_id` = 110234);

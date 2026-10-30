-- V1.3.27：销售订单附件（PI、合同、客户 PO 等）。
-- 附件表的文件类型放宽到 128：Excel（xlsx）的类型名有 65 个字符。
ALTER TABLE `biz_attachment` MODIFY `content_type` varchar(128) NOT NULL DEFAULT '' COMMENT '文件类型';

-- 销售订单的按钮权限「维护附件与备注」：订单详情里补传、删除附件（PI、合同、客户 PO 等）与修改备注。
-- 加进含销售订单菜单的套餐；授给已有「手动创建订单」按钮的角色（建单的人一般也要补资料），其他角色由管理员在角色管理里授权。
INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`)
SELECT 110218, 100079, 'RS3110218', '维护附件与备注', 3, 4, '', '', '', '', '', 'sales:order:attachment', 1, '', 'sys', 'sys'
WHERE NOT EXISTS (SELECT 1 FROM `resource` WHERE `id` = 110218);

UPDATE `tenant_package` SET `menu_ids` = JSON_ARRAY_APPEND(`menu_ids`, '$', 110218)
WHERE `menu_ids` IS NOT NULL AND JSON_VALID(`menu_ids`) AND JSON_CONTAINS(`menu_ids`, '100079') AND NOT JSON_CONTAINS(`menu_ids`, '110218');

INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT x.`role_code`, s.`id`, s.`code`
FROM `role_resource` x
JOIN `resource` s ON s.`id` = 110218
WHERE x.`resource_id` = 110185
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = x.`role_code` AND y.`resource_id` = s.`id`);

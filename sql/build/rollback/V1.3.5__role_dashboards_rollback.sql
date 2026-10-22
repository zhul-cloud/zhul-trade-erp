-- 按角色的工作台看板迁移 V1.3.5 的反向 SQL（备用，只在需要回退该版本时手工执行，执行后还需从 flyway_schema_history 删除 1.3.5 的记录）。

-- 恢复兼职看板菜单：启用菜单，兼职采购重新拥有，加回原来包含它的套餐（套餐无法区分，按包含「我的询价」的套餐加回）
UPDATE `resource` SET `status` = 1, `is_hidden` = 0, `update_time` = NOW(), `update_by` = 'sys' WHERE `id` = 100057;
INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT 'ROLE_PTBUYER', s.`id`, s.`code` FROM `resource` s
WHERE s.`id` = 100057
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = 'ROLE_PTBUYER' AND y.`resource_id` = 100057);
UPDATE `tenant_package` SET `menu_ids` = JSON_ARRAY_APPEND(`menu_ids`, '$', 100057)
WHERE JSON_VALID(`menu_ids`) AND JSON_CONTAINS(`menu_ids`, '100055') AND NOT JSON_CONTAINS(`menu_ids`, '100057');
-- 兼职采购原来没有工作台菜单
DELETE FROM `role_resource` WHERE `role_code` = 'ROLE_PTBUYER' AND `resource_id` = 100001;

ALTER TABLE `role` DROP COLUMN `workbench_template`;
DROP TABLE IF EXISTS `workbench_template`;

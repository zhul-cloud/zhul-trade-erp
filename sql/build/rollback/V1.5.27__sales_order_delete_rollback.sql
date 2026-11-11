-- 销售订单删除 V1.5.27 的反向 SQL（备用，只在需要回退该版本时手工执行，执行后还需从 flyway_schema_history 删除 1.5.27 的记录）。
-- 已删除的订单保持 deleted_at，不自动恢复；删除人、删除原因随列一起丢弃，回退前如需保留请先导出：
--   SELECT `id`, `so_no`, `deleted_at`, `deleted_by`, `delete_reason` FROM `sales_order` WHERE `deleted_by` IS NOT NULL;
DELETE FROM `role_resource` WHERE `resource_id` = 110234;
UPDATE `tenant_package` SET `menu_ids` = (
    SELECT JSON_ARRAYAGG(x.`id`) FROM JSON_TABLE(`tenant_package`.`menu_ids`, '$[*]' COLUMNS (`id` INT PATH '$')) x
    WHERE x.`id` <> 110234)
WHERE JSON_CONTAINS(`menu_ids`, '110234');
DELETE FROM `resource` WHERE `id` = 110234;
ALTER TABLE `sales_order` DROP COLUMN `delete_reason`, DROP COLUMN `deleted_by`;

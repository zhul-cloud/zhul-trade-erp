-- 采购需求删除 V1.5.52 的反向 SQL（备用，只在需要回退该版本时手工执行，执行后还需从 flyway_schema_history 删除 1.5.52 的记录）。
-- 注意：回退后已删除的数量不再计为「不需采购」，这些型号行的进度会按型号数量重新判断；整条删除的需求仍是软删除状态。
DELETE FROM `role_resource` WHERE `resource_id` = 110247;
UPDATE `tenant_package` SET `menu_ids` = COALESCE((SELECT JSON_ARRAYAGG(v) FROM JSON_TABLE(`menu_ids`, '$[*]' COLUMNS(v INT PATH '$')) t
    WHERE v <> 110247), JSON_ARRAY())
WHERE JSON_VALID(`menu_ids`) AND JSON_CONTAINS(`menu_ids`, '110247');
DELETE FROM `resource` WHERE `id` = 110247;
ALTER TABLE `purchase_requirement` DROP COLUMN `remove_reason`, DROP COLUMN `removed_qty`;

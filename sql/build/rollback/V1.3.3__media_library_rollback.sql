-- 素材库迁移 V1.3.3 的反向 SQL（备用，只在需要回退该版本时手工执行，执行后还需从 flyway_schema_history 删除 1.3.3 的记录）。
-- 依据：media_asset 在迁移中没有改动，仍记录着每个实拍素材原来的附件与任务。

-- 拆箱、验货视频挂回原实拍任务；被合并任务的营销图挂回原任务
UPDATE `biz_attachment` a
JOIN `media_asset` ma ON ma.`attachment_id` = a.`id` AND ma.`deleted_at` IS NULL
SET a.`owner_type` = 'SHOOT', a.`owner_id` = ma.`task_id`, a.`update_time` = NOW(), a.`update_by` = 'sys'
WHERE a.`owner_type` IN ('RECEIPT', 'SHOOT');

-- 迁移中跳过的待拍摄任务恢复为待拍摄
UPDATE `shoot_task` SET `status` = 1, `skip_reason` = '', `update_time` = NOW(), `update_by` = 'sys'
WHERE `status` = 3 AND (`skip_reason` = '已有营销图' OR `skip_reason` LIKE '合并到 %');

-- 菜单、权限与套餐
DELETE FROM `role_resource` WHERE `resource_id` IN (100116, 110211, 110212);
-- menu_ids 里是数字，按 JSON_TABLE 展开后去掉这三项再聚合回去
UPDATE `tenant_package` SET `menu_ids` = (
    SELECT JSON_ARRAYAGG(x.`id`) FROM JSON_TABLE(`tenant_package`.`menu_ids`, '$[*]' COLUMNS (`id` INT PATH '$')) x
    WHERE x.`id` NOT IN (100116, 110211, 110212))
WHERE JSON_CONTAINS(`menu_ids`, '100116') OR JSON_CONTAINS(`menu_ids`, '110211') OR JSON_CONTAINS(`menu_ids`, '110212');
UPDATE `resource` SET `status` = 0, `update_time` = NOW(), `update_by` = 'sys' WHERE `id` IN (100116, 110211, 110212);

-- 新表（本版本新增，回退时连同数据一起放弃）
DROP TABLE IF EXISTS `media_item`;
DROP TABLE IF EXISTS `biz_attachment_model`;

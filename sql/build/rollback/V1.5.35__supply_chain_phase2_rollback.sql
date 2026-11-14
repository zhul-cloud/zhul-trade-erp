-- 供应链管理第二期 V1.5.35 的反向 SQL（备用，只在需要回退该版本时手工执行，执行后还需从 flyway_schema_history 删除 1.5.35 的记录）。
-- 会丢弃全部主动拓展范围与候选供应商；带开发目标的专项退化为普通专项，候选的处理记录一并删除。回退前如需保留请先导出这两张表。
DELETE FROM `role_resource` WHERE `resource_id` = 110242;
UPDATE `tenant_package` SET `menu_ids` = (
    SELECT JSON_ARRAYAGG(x.`id`) FROM JSON_TABLE(`tenant_package`.`menu_ids`, '$[*]' COLUMNS (`id` INT PATH '$')) x
    WHERE x.`id` <> 110242)
WHERE JSON_CONTAINS(`menu_ids`, '110242');
DELETE FROM `resource` WHERE `id` = 110242;
DELETE FROM `sc_followup_log` WHERE `object_type` = 2;
ALTER TABLE `sc_followup_log`
    DROP KEY `idx_object_type`,
    DROP COLUMN `candidate_ids`,
    DROP COLUMN `object_type`;
ALTER TABLE `sc_gap_project` DROP COLUMN `supplier_target`;
DELETE FROM `document_log` WHERE `doc_type` = 'SX';
DROP TABLE `sc_dev_candidate`;
DROP TABLE `sc_expansion_scope`;

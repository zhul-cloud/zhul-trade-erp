-- 供应链管理第一期 V1.5.34 的反向 SQL（备用，只在需要回退该版本时手工执行，执行后还需从 flyway_schema_history 删除 1.5.34 的记录）。
-- 会丢弃全部需求信号、需求对象、覆盖渠道、处理记录与专项；回退前如需保留请先导出这六张表。
DELETE FROM `role_resource` WHERE `resource_id` BETWEEN 110235 AND 110241;
UPDATE `tenant_package` SET `menu_ids` = (
    SELECT JSON_ARRAYAGG(x.`id`) FROM JSON_TABLE(`tenant_package`.`menu_ids`, '$[*]' COLUMNS (`id` INT PATH '$')) x
    WHERE x.`id` NOT BETWEEN 110235 AND 110241)
WHERE JSON_CONTAINS(`menu_ids`, '110235');
DELETE FROM `resource` WHERE `id` BETWEEN 110235 AND 110241;
UPDATE `resource` SET `sort` = `sort` - 1 WHERE `pid` = 0 AND `sort` >= 5 AND `id` NOT IN (100002, 100003);
DELETE FROM `sys_config` WHERE `config_key` = 'supply.evidence-valid-days';
DELETE FROM `sop_stage` WHERE `board` = 6;
ALTER TABLE `sop_stage`
    DROP COLUMN `quality_checks`,
    MODIFY COLUMN `board` tinyint(2) NOT NULL DEFAULT 2 COMMENT '板块（1-主动开发、2-商机成交、3-订单履约、4-售后评价、5-客户经营）';
DELETE FROM `document_log` WHERE `doc_type` IN ('SC_DEMAND', 'SC_GAP_PROJECT');
DROP TABLE `sc_gap_project_item`;
DROP TABLE `sc_gap_project`;
DROP TABLE `sc_followup_log`;
DROP TABLE `sc_coverage_channel`;
DROP TABLE `sc_demand`;
DROP TABLE `sc_demand_signal`;

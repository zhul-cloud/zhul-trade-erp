-- V1.5.21：客户跟进快速操作（2026-11-09 反馈，openspec followup-quick-actions）。
-- 跟进记录增加「快速记录」标记和实际沟通日期；记录类型增加 3-调整阶段（不算一次跟进）。
-- 回退：代码回退后字段不再被读取；需要删除时
--   ALTER TABLE `customer_followup` DROP COLUMN `quick`, DROP COLUMN `contact_date`;
--   记录类型为 3 的行保留即可（旧代码按「全部」时间线照常显示）。
ALTER TABLE `customer_followup`
    MODIFY COLUMN `record_type` tinyint(2) NOT NULL DEFAULT 1 COMMENT '记录类型（1-业务员跟进、2-系统事件、3-调整阶段）',
    ADD COLUMN `quick`        tinyint(2) NOT NULL DEFAULT 0 COMMENT '是否快速记录（0-否、1-是）' AFTER `record_type`,
    ADD COLUMN `contact_date` date       NULL               COMMENT '实际沟通日期，快速记录时填写，NULL 表示同记录时间' AFTER `quick`;

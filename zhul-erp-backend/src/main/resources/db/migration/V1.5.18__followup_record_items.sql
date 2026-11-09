-- V1.5.18：一次跟进涉及的单据（2026-11-09 反馈第 5–7 项，客户跟进按客户分组）。
-- 对客户记一次跟进可以覆盖同一板块的多张单据：customer_followup 仍写一条，涉及的每张单据各写一行到本表。
-- customer_followup.followup_item_id 继续写第一张单据，原有读取不受影响；历史记录不回填（只涉及一张单据，按 followup_item_id 即可）。
-- 回退：代码回退后本表不再被读取，可保留；需要删除时 DROP TABLE `customer_followup_item`;
CREATE TABLE IF NOT EXISTS `customer_followup_item` (
    `id`                bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`         int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID',
    `followup_id`       bigint        NOT NULL DEFAULT 0  COMMENT '跟进记录ID，关联customer_followup.id',
    `followup_item_id`  bigint        NOT NULL DEFAULT 0  COMMENT '跟进对象ID，关联followup_item.id',
    `doc_no`            varchar(64)   NOT NULL DEFAULT '' COMMENT '记录时的单据编号',
    `stage_code`        varchar(16)   NOT NULL DEFAULT '' COMMENT '记录时单据所在阶段',
    `due_date`          date          NULL                COMMENT '记录时单据的下一步日期',
    `deleted_at`        datetime      NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`       datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`         varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`       datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`         varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_followup_item` (`followup_id`, `followup_item_id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_followup_item_id` (`followup_item_id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='一次跟进涉及的单据';

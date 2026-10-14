-- 询盘单追加型号（openspec inquiry-append-items）：确认后客户补的型号单独解析、确认，接在原型号后面

CREATE TABLE IF NOT EXISTS `customer_inquiry_append` (
    `id`                  bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`           int(11)      NOT NULL DEFAULT 0 COMMENT '租户ID',
    `customer_inquiry_id` bigint       NOT NULL DEFAULT 0 COMMENT '询盘单ID',
    `raw_content`         text         NULL COMMENT '追加的原始内容',
    `ai_task_id`          bigint       NULL COMMENT 'AI 解析任务ID',
    `status`              tinyint(2)   NOT NULL DEFAULT 1 COMMENT '状态（1-解析中、2-待确认、3-解析失败、4-已确认、5-已放弃）',
    `parse_mode`          tinyint(2)   NOT NULL DEFAULT 1 COMMENT '解析方式（1-AI、2-手动）',
    `parse_error`         varchar(300) NOT NULL DEFAULT '' COMMENT '解析失败原因',
    `item_count`          int(11)      NOT NULL DEFAULT 0 COMMENT '确认后追加的型号数',
    `deleted_at`          datetime     NULL COMMENT '软删除时间，NULL表示未删除',
    `create_time`         datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`           varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`         datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`           varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_customer_inquiry_id` (`customer_inquiry_id`),
    KEY `idx_ai_task_id` (`ai_task_id`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='询盘单追加型号批次';

ALTER TABLE `customer_inquiry_attachment`
    ADD COLUMN `append_id` bigint NOT NULL DEFAULT 0 COMMENT '追加批次ID（0-录入询盘单时的附件）' AFTER `customer_inquiry_id`;

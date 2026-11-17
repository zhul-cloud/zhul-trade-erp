-- V1.5.44：资料任务、营销任务的 AI 生成（openspec product-content-ai）
-- 一个商品一条 AI 生成记录：排队、生成、待确认、已导入、失败、已取消；结果原文保存到确认或放弃为止。
-- 上传记录增加来源：手动上传 / AI 生成。

CREATE TABLE `product_content_ai_job`
(
    `id`                 bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`          int(11)      NOT NULL DEFAULT 0 COMMENT '租户ID（发起的公司；资料写共享商品库，营销写本公司）',
    `product_id`         bigint       NOT NULL COMMENT '商品ID（共享商品库）',
    `status`             tinyint(2)   NOT NULL DEFAULT 1 COMMENT '状态（1-排队中、2-生成中、3-待确认、4-已导入、5-失败、6-已取消）',
    `ai_task_id`         bigint       NULL COMMENT 'AI 任务ID，排队中为空',
    `facts_file_name`    varchar(255) NOT NULL DEFAULT '' COMMENT '资料包文件名（与任务包下载的文件名一致）',
    `facts_markdown`     mediumtext   NULL COMMENT 'AI 生成的资料包原文',
    `marketing_file_name` varchar(255) NOT NULL DEFAULT '' COMMENT '营销包文件名',
    `marketing_markdown` mediumtext   NULL COMMENT 'AI 生成的营销包原文',
    `facts_imported_at`  datetime     NULL COMMENT '资料包写入时间，未写入为空',
    `marketing_imported_at` datetime  NULL COMMENT '营销包写入时间，未写入为空',
    `self_score`         int(11)      NULL COMMENT 'AI 自评分（0-100）',
    `summary`            varchar(1000) NOT NULL DEFAULT '' COMMENT 'AI 对核实情况的说明',
    `fail_reason`        varchar(500) NOT NULL DEFAULT '' COMMENT '失败或取消原因',
    `skill_version`      varchar(64)  NOT NULL DEFAULT '' COMMENT '技能来源版本（zhul-skills 提交号）',
    `requested_by`       bigint       NULL COMMENT '发起人（user_basic.id）',
    `started_at`         datetime     NULL COMMENT '开始生成时间',
    `completed_at`       datetime     NULL COMMENT '生成结束时间（成功或失败）',
    `confirmed_by`       varchar(32)  NOT NULL DEFAULT '' COMMENT '最后确认写入的人',
    `confirmed_at`       datetime     NULL COMMENT '最后确认写入时间',
    `deleted_at`         datetime     NULL COMMENT '软删除时间，NULL表示未删除',
    `create_time`        datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`          varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`        datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`          varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_pcaj_tenant` (`tenant_id`),
    KEY `idx_pcaj_product` (`product_id`),
    KEY `idx_pcaj_status` (`status`),
    KEY `idx_pcaj_ai_task` (`ai_task_id`),
    KEY `idx_pcaj_create_time` (`create_time`),
    KEY `idx_pcaj_deleted_at` (`deleted_at`)
) ENGINE = InnoDB
  AUTO_INCREMENT = 10000
  DEFAULT CHARSET = utf8mb4 COMMENT ='商品内容 AI 生成记录（每个商品每次一条）';

ALTER TABLE `product_content_import`
    ADD COLUMN `source` tinyint(2) NOT NULL DEFAULT 1 COMMENT '来源（1-手动上传、2-AI 生成）' AFTER `confirmed_by`;

-- 回退：
-- DROP TABLE `product_content_ai_job`;
-- ALTER TABLE `product_content_import` DROP COLUMN `source`;

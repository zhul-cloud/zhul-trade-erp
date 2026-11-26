-- 待关联单据的标记（openspec unlinked-docs-enhance）：按「租户 + 归并键」记录 AI 识别建议与「标为不是商品」。
-- 单据明细本身不改；清单实时计算后按归并键合并这张表。
CREATE TABLE `doc_unlinked_mark` (
    `id`          bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`   int(11)      NOT NULL DEFAULT 0  COMMENT '租户ID',
    `group_key`   varchar(300) NOT NULL DEFAULT '' COMMENT '归并键（品牌原文小写 | 归一化型号）',
    `ignored`     tinyint(1)   NOT NULL DEFAULT 0  COMMENT '是否标为不是商品（0-否、1-是），标记后不再列入待关联清单',
    `ignored_by`  varchar(32)  NOT NULL DEFAULT '' COMMENT '标记人',
    `ignored_at`  datetime     NULL                COMMENT '标记时间',
    `ai_kind`     tinyint(2)   NOT NULL DEFAULT 0  COMMENT 'AI 判断（0-未识别、1-商品型号、2-费用或非商品、3-看不出）',
    `ai_brand`    varchar(64)  NOT NULL DEFAULT '' COMMENT 'AI 建议的品牌',
    `ai_model`    varchar(128) NOT NULL DEFAULT '' COMMENT 'AI 建议的真实型号',
    `ai_task_id`  bigint(20)   NULL                COMMENT '给出建议的 AI 任务',
    `ai_at`       datetime     NULL                COMMENT 'AI 建议时间',
    `deleted_at`  datetime     NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`   varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`   varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_group` (`tenant_id`, `group_key`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='待关联单据标记（AI 建议、不是商品）';

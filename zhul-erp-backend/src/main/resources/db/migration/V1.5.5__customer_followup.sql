-- 客户跟进（openspec change customer-followup）：四张新表与画像字段来源扩展
-- 1. followup_item      跟进对象：由单据事件维护，每晚核对兜底（design D1）
-- 2. customer_followup  跟进记录：业务员记的跟进与系统事件，只增不改（design D8）
-- 3. sop_stage          SOP 阶段作战卡（平台数据，tenant_id = 0，design D12）
-- 4. sop_blocker        SOP 卡点字典与应对办法（平台数据，tenant_id = 0）
-- 5. customer_field_source 新增来源 4-跟进记录，记录来源跟进记录 ID

CREATE TABLE `followup_item` (
    `id`                 bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`          int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID',
    `board`              tinyint(2)    NOT NULL DEFAULT 2  COMMENT '板块（1-主动开发、2-商机成交、3-订单履约、4-售后评价、5-客户经营）',
    `target_type`        tinyint(2)    NOT NULL DEFAULT 1  COMMENT '来源单据类型（1-商机、2-询盘、3-报价单、4-PI）',
    `target_id`          bigint        NOT NULL DEFAULT 0  COMMENT '来源单据ID',
    `doc_no`             varchar(32)   NOT NULL DEFAULT '' COMMENT '来源单据编号（显示用快照）',
    `source_key`         varchar(255)  NOT NULL DEFAULT '' COMMENT '报价单的来源询盘ID（升序逗号分隔），同一来源只保留最新报价单；其他类型为空',
    `customer_id`        bigint        NOT NULL DEFAULT 0  COMMENT '客户ID，关联customer.id',
    `opportunity_id`     bigint        NOT NULL DEFAULT 0  COMMENT '客户进行中的商机ID，0 为没有（老客户）',
    `owner_id`           bigint        NOT NULL DEFAULT 0  COMMENT '负责业务员，关联user_basic.id',
    `stage_code`         varchar(16)   NOT NULL DEFAULT '' COMMENT '阶段编码（S1–S7）',
    `stage_group`        tinyint(2)    NOT NULL DEFAULT 1  COMMENT '阶段分组（1-报价前、2-报价后、3-PI后）',
    `currency_code`      varchar(8)    NOT NULL DEFAULT '' COMMENT '单据币种',
    `amount`             decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '单据原币金额（询盘为0）',
    `received_amount`    decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT 'PI 已收金额（原币），其他类型为0',
    `amount_cny`         decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '排序用本位币金额：PI/报价单取本位币总额，询盘取预估价值',
    `sent_at`            datetime      NULL                COMMENT '单据发送时间（报价单、PI）',
    `cadence_from`       date          NULL                COMMENT '系统提醒节奏起算日：单据事件生成的为发送或创建日期，核对任务补生成的为补生成当天',
    `blocker_code`       varchar(32)   NOT NULL DEFAULT '' COMMENT '当前卡点，关联sop_blocker.code，空为无',
    `next_action`        varchar(200)  NOT NULL DEFAULT '' COMMENT '下一步要做什么',
    `next_action_date`   date          NULL                COMMENT '下一步日期，NULL 为没有待办（如建议转培育）',
    `next_action_source` tinyint(2)    NOT NULL DEFAULT 1  COMMENT '下一步来源（1-系统节奏，每天按节奏刷新、2-业务员定的、3-系统一次性提醒，如培育到期或延后的系统提醒，不再按节奏刷新）',
    `snooze_count`       int(11)       NOT NULL DEFAULT 0  COMMENT '连续延后次数，记跟进后清零',
    `expected_pay_date`  date          NULL                COMMENT 'PI：客户说哪天付',
    `last_followup_id`   bigint        NOT NULL DEFAULT 0  COMMENT '上次跟进记录ID，关联customer_followup.id',
    `last_followup_at`   datetime      NULL                COMMENT '上次跟进时间',
    `no_reply_streak`    int(11)       NOT NULL DEFAULT 0  COMMENT '连续没回复次数',
    `feedback_seen`      tinyint(2)    NOT NULL DEFAULT 0  COMMENT '是否收到过客户反馈（0-否、1-是）',
    `nurture_reason`     varchar(32)   NOT NULL DEFAULT '' COMMENT '培育原因，关联sop_blocker.code；空为不在培育',
    `nurture_note`       varchar(200)  NOT NULL DEFAULT '' COMMENT '培育说明',
    `nurture_until`      date          NULL                COMMENT '培育到期（重新激活）日期，NULL 为不在培育',
    `closed_at`          datetime      NULL                COMMENT '移出板块时间，NULL 为进行中',
    `close_reason`       tinyint(2)    NOT NULL DEFAULT 0  COMMENT '移出原因（0-未移出、1-单据结束、2-被新单据接替、3-商机结束、4-单据删除或作废）',
    `deleted_at`         datetime      NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`        datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`          varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`        datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`          varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_target` (`tenant_id`, `target_type`, `target_id`),
    KEY `idx_owner_todo` (`tenant_id`, `owner_id`, `closed_at`, `next_action_date`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_customer_id` (`customer_id`),
    KEY `idx_opportunity_id` (`opportunity_id`),
    KEY `idx_last_followup_id` (`last_followup_id`),
    KEY `idx_source_key` (`tenant_id`, `customer_id`, `source_key`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='跟进对象';

CREATE TABLE `customer_followup` (
    `id`                bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`         int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID',
    `record_type`       tinyint(2)    NOT NULL DEFAULT 1  COMMENT '记录类型（1-业务员跟进、2-系统事件）',
    `customer_id`       bigint        NOT NULL DEFAULT 0  COMMENT '客户ID，关联customer.id',
    `followup_item_id`  bigint        NOT NULL DEFAULT 0  COMMENT '跟进对象ID，关联followup_item.id，0 为不挂跟进对象',
    `target_type`       tinyint(2)    NOT NULL DEFAULT 0  COMMENT '关联单据类型（0-无、1-商机、2-询盘、3-报价单、4-PI）',
    `target_id`         bigint        NOT NULL DEFAULT 0  COMMENT '关联单据ID',
    `opportunity_id`    bigint        NOT NULL DEFAULT 0  COMMENT '关联商机ID，0 为无',
    `title`             varchar(200)  NOT NULL DEFAULT '' COMMENT '系统事件标题，如「发送报价单 FWQT…」；业务员跟进为空',
    `situation`         int(11)       NOT NULL DEFAULT 0  COMMENT '客户情况位掩码（1-客户给了反馈、2-客户在谈条件、4-没回复）',
    `blocker_code`      varchar(32)   NOT NULL DEFAULT '' COMMENT '主要卡点，关联sop_blocker.code，空为无',
    `next_action`       varchar(200)  NOT NULL DEFAULT '' COMMENT '下一步要做什么',
    `next_action_date`  date          NULL                COMMENT '下一步日期',
    `channel`           tinyint(2)    NOT NULL DEFAULT 0  COMMENT '方式（0-无、1-WhatsApp、2-邮件、3-电话、4-其他）',
    `raw_content`       mediumtext    NULL                COMMENT '粘贴的聊天或邮件原文',
    `ai_summary`        varchar(1000) NOT NULL DEFAULT '' COMMENT 'AI 整理的摘要（业务员可改）',
    `ai_task_id`        bigint        NOT NULL DEFAULT 0  COMMENT 'AI任务ID，关联ai_task.id，0 为未用 AI',
    `stage_before`      varchar(16)   NOT NULL DEFAULT '' COMMENT '记录前的阶段',
    `stage_after`       varchar(16)   NOT NULL DEFAULT '' COMMENT '记录后的阶段，与前相同表示没有变化',
    `expected_pay_date` date          NULL                COMMENT 'PI：客户说哪天付',
    `profile_fields`    json          NULL                COMMENT '写入档案的字段（数组：field、label、value）',
    `operator_id`       bigint        NOT NULL DEFAULT 0  COMMENT '记录人，关联user_basic.id，0 为系统',
    `idempotency_key`   varchar(64)   NULL                COMMENT '幂等键，防重复提交；系统事件为 NULL',
    `deleted_at`        datetime      NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`       datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`         varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`       datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`         varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_idempotency` (`tenant_id`, `idempotency_key`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_customer_id` (`customer_id`, `create_time`),
    KEY `idx_followup_item_id` (`followup_item_id`),
    KEY `idx_opportunity_id` (`opportunity_id`),
    KEY `idx_ai_task_id` (`ai_task_id`),
    KEY `idx_operator_id` (`operator_id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户跟进记录';

CREATE TABLE `sop_stage` (
    `id`              int(11)       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`       int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID，0 为平台内置，租户可覆盖',
    `board`           tinyint(2)    NOT NULL DEFAULT 2  COMMENT '板块（1-主动开发、2-商机成交、3-订单履约、4-售后评价、5-客户经营）',
    `stage_code`      varchar(16)   NOT NULL DEFAULT '' COMMENT '阶段编码，如 S1–S7',
    `name`            varchar(32)   NOT NULL DEFAULT '' COMMENT '阶段名称',
    `goal`            varchar(500)  NOT NULL DEFAULT '' COMMENT '这一步的目标',
    `checklist`       json          NULL                COMMENT '本阶段要拿到 / 要做的清单（字符串数组）',
    `gate_text`       varchar(500)  NOT NULL DEFAULT '' COMMENT '推进条件',
    `auto_hint`       varchar(200)  NOT NULL DEFAULT '' COMMENT '系统怎么自动推进的说明',
    `default_actions` json          NULL                COMMENT '系统提醒的默认动作（对象：节点 → 文字，如 d1、d3、d7、d14、pi）',
    `example_dialog`  text          NULL                COMMENT '示例对话',
    `sort_order`      int(11)       NOT NULL DEFAULT 0  COMMENT '排序',
    `status`          tinyint(2)    NOT NULL DEFAULT 1  COMMENT '状态（0-禁用、1-启用）',
    `create_time`     datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`       varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`     datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`       varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_stage` (`tenant_id`, `board`, `stage_code`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=1000 DEFAULT CHARSET=utf8mb4 COMMENT='SOP 阶段作战卡';

CREATE TABLE `sop_blocker` (
    `id`          int(11)       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`   int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID，0 为平台内置，租户可覆盖',
    `code`        varchar(32)   NOT NULL DEFAULT '' COMMENT '卡点编码，如 PRICE、DELIVERY',
    `name`        varchar(32)   NOT NULL DEFAULT '' COMMENT '卡点名称',
    `lost_reason` int(11)       NOT NULL DEFAULT 0  COMMENT '对应的商机输单原因码（11-价格、12-交期、15-选择了竞争对手、16-技术、17-付款、18-信任、20-预算、21-内部审批、22-项目延期、23-无回复），0 为不对应',
    `playbook`    json          NULL                COMMENT '应对办法（字符串数组）',
    `sort_order`  int(11)       NOT NULL DEFAULT 0  COMMENT '排序',
    `status`      tinyint(2)    NOT NULL DEFAULT 1  COMMENT '状态（0-禁用、1-启用）',
    `create_time` datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`   varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time` datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`   varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`tenant_id`, `code`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=1000 DEFAULT CHARSET=utf8mb4 COMMENT='SOP 卡点字典';

ALTER TABLE `customer_field_source`
    MODIFY COLUMN `source` tinyint NOT NULL DEFAULT '1' COMMENT '来源（1-人工、2-背调报告、3-迁移、4-跟进记录）',
    ADD COLUMN `followup_id` bigint NOT NULL DEFAULT 0 COMMENT '来源跟进记录ID，关联customer_followup.id，0 为无' AFTER `report_id`,
    ADD KEY `idx_followup_id` (`followup_id`);

-- 回退：
-- ALTER TABLE `customer_field_source` DROP KEY `idx_followup_id`, DROP COLUMN `followup_id`;
-- UPDATE `customer_field_source` SET `source` = 1 WHERE `source` = 4;
-- DROP TABLE `sop_blocker`; DROP TABLE `sop_stage`; DROP TABLE `customer_followup`; DROP TABLE `followup_item`;

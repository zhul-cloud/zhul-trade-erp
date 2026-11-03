-- 客户 AI 背调与档案补全（openspec change customer-background）
-- 1. 背调报告表
-- 2. ai_task 记录每次调用的用量（spec ai-platform/task-orchestration「记录 AI 调用用量」）
-- 3. 按钮权限「客户背调」
-- 4. 档案补全「只能问客户」各字段的英文问法（平台模板，租户可在系统参数里覆盖）
-- 编号 BG+日期+序号走 document_sequence，按需生成，不需要初始化数据。

CREATE TABLE `customer_background_report` (
    `id`               bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`        int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID',
    `report_no`        varchar(20)   NOT NULL DEFAULT '' COMMENT '报告编号，格式 BG+yyyyMMdd+3位序号',
    `customer_id`      bigint        NOT NULL DEFAULT 0  COMMENT '客户ID，关联customer.id',
    `source`           tinyint(2)    NOT NULL DEFAULT 1  COMMENT '来源（1-AI背调、2-上传）',
    `status`           tinyint(2)    NOT NULL DEFAULT 1  COMMENT '状态（1-背调中、2-已完成、3-失败）',
    `fail_reason`      varchar(500)  NOT NULL DEFAULT '' COMMENT '失败原因',
    `priority`         tinyint(2)    NOT NULL DEFAULT 0  COMMENT '跟进优先级（0-未出结论、1-立即重点跟进、2-正常跟进、3-低优先级、4-不建议跟进）',
    `score`            tinyint(2)    NOT NULL DEFAULT 0  COMMENT '综合评分 1–5，0 为未出结论',
    `main_conflict`    varchar(500)  NOT NULL DEFAULT '' COMMENT '主要矛盾',
    `first_action`     varchar(500)  NOT NULL DEFAULT '' COMMENT '第一步行动',
    `identity_note`    varchar(500)  NOT NULL DEFAULT '' COMMENT '客户身份',
    `supply_note`      varchar(500)  NOT NULL DEFAULT '' COMMENT '产品货源',
    `key_finding`      varchar(500)  NOT NULL DEFAULT '' COMMENT '关键发现',
    `obstacle`         varchar(500)  NOT NULL DEFAULT '' COMMENT '最大障碍',
    `report_markdown`  mediumtext    NULL                COMMENT '报告正文（Markdown）；上传的为原文',
    `suggestions`      json          NULL                COMMENT '字段建议及采纳情况（数组：module、field、value、evidence、sourceUrl、contact、state、operator、at）',
    `upload_file_key`  varchar(300)  NOT NULL DEFAULT '' COMMENT '上传的 PDF 原件存储键（私有目录）',
    `upload_file_name` varchar(200)  NOT NULL DEFAULT '' COMMENT '上传的 PDF 原文件名',
    `ai_task_id`       bigint        NOT NULL DEFAULT 0  COMMENT 'AI任务ID，关联ai_task.id',
    `model`            varchar(64)   NOT NULL DEFAULT '' COMMENT '用量快照：模型',
    `input_tokens`     int           NULL                COMMENT '用量快照：输入 token，NULL 为无记录',
    `output_tokens`    int           NULL                COMMENT '用量快照：输出 token，NULL 为无记录',
    `web_searches`     int           NULL                COMMENT '用量快照：联网次数，NULL 为无记录',
    `duration_seconds` int           NULL                COMMENT '耗时（秒）',
    `requested_by`     bigint        NOT NULL DEFAULT 0  COMMENT '发起人用户ID',
    `completed_at`     datetime      NULL                COMMENT '完成时间',
    `deleted_by`       bigint        NOT NULL DEFAULT 0  COMMENT '删除人用户ID',
    `deleted_at`       datetime      NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`        varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`        varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_customer_id` (`customer_id`),
    KEY `idx_status` (`status`),
    KEY `idx_ai_task_id` (`ai_task_id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户背调报告';

ALTER TABLE `ai_task`
    ADD COLUMN `model`         varchar(64) NOT NULL DEFAULT '' COMMENT '用量：模型名，空为无记录' AFTER `error_message`,
    ADD COLUMN `input_tokens`  int         NULL COMMENT '用量：输入 token，NULL 为无记录' AFTER `model`,
    ADD COLUMN `output_tokens` int         NULL COMMENT '用量：输出 token，NULL 为无记录' AFTER `input_tokens`,
    ADD COLUMN `web_searches`  int         NULL COMMENT '用量：联网搜索次数，NULL 为无记录' AFTER `output_tokens`;

-- 档案补全「今天已补完 N 个」：记录完整度变为 5/5 的时间与操作人（降到 5 以下时清空，再补齐重新计）
ALTER TABLE `customer`
    ADD COLUMN `profile_done_at` datetime NULL COMMENT '档案补齐（完整度变为5/5）的时间' AFTER `profile_mask`,
    ADD COLUMN `profile_done_by` bigint   NOT NULL DEFAULT 0 COMMENT '把档案补齐的用户ID，0为系统' AFTER `profile_done_at`,
    ADD KEY `idx_profile_done_at` (`profile_done_at`);

-- ---------------------------------------------------------------- 按钮权限：客户背调（有编辑客户权限的角色默认得到）
INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`)
SELECT 110225, 100061, 'RS3110225', '客户背调', 3, 8, '', '', '', '', '', 'partner:customer:background', 1, '', 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `resource` r WHERE r.`id` = 110225);

UPDATE `tenant_package` SET `menu_ids` = JSON_ARRAY_APPEND(`menu_ids`, '$', 110225)
WHERE JSON_CONTAINS(`menu_ids`, '110142') AND NOT JSON_CONTAINS(`menu_ids`, '110225');

INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT x.`role_code`, 110225, 'RS3110225' FROM `role_resource` x
WHERE x.`resource_id` = 110142
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = x.`role_code` AND y.`resource_id` = 110225);

-- ---------------------------------------------------------------- 档案补全：只能问客户的字段的英文问法
INSERT INTO `sys_config` (`tenant_id`, `config_key`, `config_name`, `config_value`, `config_type`, `is_builtin`, `config_group`, `remark`, `create_by`, `update_by`)
SELECT 0, k.config_key, k.config_name, k.config_value, 'STRING', 1, 'customer', '档案补全「只能问客户」显示的英文问法，可按公司习惯修改', 'sys', 'sys'
FROM (
              SELECT 'customer.ask.customs-ability' AS config_key, '问法：清关能力' AS config_name,
                     'Do you usually handle import customs clearance yourselves, or do you work with a forwarder / customs broker?' AS config_value
    UNION ALL SELECT 'customer.ask.payment-method', '问法：付款习惯',
                     'What payment terms do you usually use with your suppliers (e.g. T/T in advance, deposit + balance, L/C)?'
    UNION ALL SELECT 'customer.ask.current-brands', '问法：现有使用品牌',
                     'Which automation brands are you mainly using at the moment (e.g. Siemens, Schneider, ABB, Omron)?'
    UNION ALL SELECT 'customer.ask.design-ability', '问法：设计能力',
                     'Do you have your own engineers for programming and system design, or would technical support from us be helpful?'
    UNION ALL SELECT 'customer.ask.protocols', '问法：常用通讯协议',
                     'Which communication protocols do your systems mainly use (Modbus, Profinet, EtherCAT, EtherNet/IP, etc.)?'
    UNION ALL SELECT 'customer.ask.voltages', '问法：电源电压',
                     'What supply voltage and frequency do you use on site (e.g. 230V / 400V, 50Hz or 60Hz)?'
    UNION ALL SELECT 'customer.ask.repair-ability', '问法：售后维修能力',
                     'When a unit fails, do you usually repair it yourselves, replace it directly, or send it back for repair?'
    UNION ALL SELECT 'customer.ask.certifications', '问法：认证要求',
                     'Are there any certifications the products must have for your market or projects (CE, UL, RoHS, etc.)?'
    UNION ALL SELECT 'customer.ask.decision-role', '问法：决策权重',
                     'Besides you, who else is usually involved in deciding on a new supplier or approving the order?'
) k
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` c WHERE c.`tenant_id` = 0 AND c.`config_key` = k.config_key);

-- 回退：
-- DELETE FROM `sys_config` WHERE `tenant_id` = 0 AND `config_key` LIKE 'customer.ask.%';
-- DELETE FROM `role_resource` WHERE `resource_id` = 110225;
-- DELETE FROM `resource` WHERE `id` = 110225;
-- （tenant_package.menu_ids 中多出的 ID 不影响旧版本，可保留）
-- ALTER TABLE `ai_task` DROP COLUMN `web_searches`, DROP COLUMN `output_tokens`, DROP COLUMN `input_tokens`, DROP COLUMN `model`;
-- ALTER TABLE `customer` DROP INDEX `idx_profile_done_at`, DROP COLUMN `profile_done_by`, DROP COLUMN `profile_done_at`;
-- DROP TABLE `customer_background_report`;   -- 已采纳进档案的字段不受影响

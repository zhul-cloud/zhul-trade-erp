-- V1.2.12：商机管理（新客登记、阶段流转、每日渠道统计）。
-- 见 openspec/changes/add-opportunity-management/。

CREATE TABLE `opportunity_stage`
(
    `id`              int(11)     NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`       int(11)     NOT NULL DEFAULT 0  COMMENT '租户ID（0=平台级默认配置，本期所有租户共用）',
    `code`            varchar(16) NOT NULL DEFAULT '' COMMENT '阶段编码，如 S1、S7、WON、LOST、INVALID',
    `name`            varchar(32) NOT NULL DEFAULT '' COMMENT '阶段名称',
    `category`        tinyint(2)  NOT NULL DEFAULT 1  COMMENT '阶段类别（1-进行中、2-赢单、3-输单、4-无效）',
    `counts_as_valid` tinyint(1)  NOT NULL DEFAULT 0  COMMENT '进入该阶段即计为有效商机（0-否、1-是）',
    `sort_order`      int(11)     NOT NULL DEFAULT 0  COMMENT '排序；进行中阶段按此顺序流转',
    `status`          tinyint(2)  NOT NULL DEFAULT 1  COMMENT '状态（0-禁用、1-启用）',
    `create_time`     datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`       varchar(32) NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`     datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`       varchar(32) NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_code` (`tenant_id`, `code`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商机阶段配置表（第二期在阶段上挂 SOP 清单）';

INSERT INTO `opportunity_stage` (`tenant_id`, `code`, `name`, `category`, `counts_as_valid`, `sort_order`) VALUES
(0, 'S1', '新商机', 1, 0, 10),
(0, 'S2', '需求确认', 1, 0, 20),
(0, 'S3', '有效商机', 1, 1, 30),
(0, 'S4', '已报价', 1, 1, 40),
(0, 'S5', '报价反馈', 1, 1, 50),
(0, 'S6', '商务谈判', 1, 1, 60),
(0, 'S7', '成交推进', 1, 1, 70),
(0, 'WON', '赢单', 2, 1, 80),
(0, 'LOST', '输单', 3, 0, 90),
(0, 'INVALID', '无效', 4, 0, 100);

CREATE TABLE `opportunity`
(
    `id`                 bigint(20)    NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`          int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID',
    `opportunity_code`   varchar(32)   NOT NULL DEFAULT '' COMMENT '商机编号，格式OPP{yyyyMMdd}{NNN}，如OPP20260928001',
    `customer_id`        bigint(20)    NOT NULL DEFAULT 0  COMMENT '客户ID，关联customer.id；一个客户最多一条未删除商机（应用层保证）',
    `source_channel`     tinyint(2)    NOT NULL DEFAULT 0  COMMENT '来源渠道（1-阿里巴巴国际站、2-中国制造网、3-独立站、4-展会、5-社交媒体、6-老客户转介绍、7-主动开发、8-其他）',
    `first_contact_date` date          NOT NULL COMMENT '首次接触日期（统计归属日，不晚于登记当天）',
    `owner_id`           bigint(20)    NOT NULL DEFAULT 0  COMMENT '负责业务员ID，关联user_basic.id，登记时为登记人',
    `stage_code`         varchar(16)   NOT NULL DEFAULT 'S1' COMMENT '当前阶段编码，关联opportunity_stage.code',
    `stage_changed_at`   datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近一次阶段变更时间（用于「超过 2 天未推进」统计）',
    `reopen_stage_code`  varchar(16)   NOT NULL DEFAULT '' COMMENT '进入结束状态（赢单/输单/无效）前的阶段，重新打开时回到这里',
    `close_reason`       tinyint(2)    NOT NULL DEFAULT 0  COMMENT '结束原因（0-无；无效：1-同行套价、2-垃圾询盘、3-联系不上、4-需求不符、9-其他；输单：11-价格、12-交期、13-无货源、14-客户取消、15-选择了竞争对手、19-其他）',
    `close_note`         varchar(500)  NOT NULL DEFAULT '' COMMENT '结束说明',
    `closed_at`          datetime      NULL                COMMENT '进入结束状态的时间，重新打开后清空',
    `reached_valid`      tinyint(1)    NOT NULL DEFAULT 0  COMMENT '是否曾进入有效阶段（0-否、1-是），只会从0变1，统计「有效」用',
    `demand_summary`     varchar(2000) NOT NULL DEFAULT '' COMMENT '需求摘要',
    `deleted_at`         datetime      NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`        datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（登记时间）',
    `create_by`          varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`        datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`          varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_contact_date` (`tenant_id`, `first_contact_date`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_customer_id` (`customer_id`),
    KEY `idx_owner_id` (`owner_id`),
    KEY `idx_stage_code` (`stage_code`),
    KEY `idx_opportunity_code` (`opportunity_code`),
    KEY `idx_deleted_at` (`deleted_at`),
    KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商机表（新客户从首次接触到赢单/输单的获客过程）';

CREATE TABLE `opportunity_stage_log`
(
    `id`             bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`      int(11)      NOT NULL DEFAULT 0  COMMENT '租户ID',
    `opportunity_id` bigint(20)   NOT NULL DEFAULT 0  COMMENT '商机ID，关联opportunity.id',
    `from_stage`     varchar(16)  NOT NULL DEFAULT '' COMMENT '变更前阶段编码，登记时为空',
    `to_stage`       varchar(16)  NOT NULL DEFAULT '' COMMENT '变更后阶段编码',
    `action`         tinyint(2)   NOT NULL DEFAULT 1  COMMENT '动作（1-登记、2-阶段变更、3-标记结束、4-重新打开）',
    `reason`         tinyint(2)   NOT NULL DEFAULT 0  COMMENT '结束原因，同opportunity.close_reason',
    `note`           varchar(500) NOT NULL DEFAULT '' COMMENT '说明',
    `create_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（变更时间）',
    `create_by`      varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人（操作人）',
    `update_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`      varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_opportunity_id` (`opportunity_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商机阶段变更记录表（只增不改）';

CREATE TABLE `opportunity_attachment`
(
    `id`             bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`      int(11)      NOT NULL DEFAULT 0  COMMENT '租户ID',
    `opportunity_id` bigint(20)   NOT NULL DEFAULT 0  COMMENT '商机ID，关联opportunity.id',
    `file_name`      varchar(200) NOT NULL DEFAULT '' COMMENT '原文件名',
    `file_key`       varchar(200) NOT NULL DEFAULT '' COMMENT '私有存储目录下的相对路径（不对外公开，经下载接口访问）',
    `file_size`      bigint(20)   NOT NULL DEFAULT 0  COMMENT '文件大小（字节）',
    `content_type`   varchar(100) NOT NULL DEFAULT '' COMMENT '文件类型（image/jpeg、image/png、Excel、text/csv）',
    `deleted_at`     datetime     NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（上传时间）',
    `create_by`      varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人（上传人）',
    `update_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`      varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_opportunity_id` (`opportunity_id`),
    KEY `idx_deleted_at` (`deleted_at`),
    KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商机需求附件表';

-- 客户联系方式比较键：登记商机时按邮箱、WhatsApp、电话查重
ALTER TABLE `customer`
    ADD COLUMN `email_key`    varchar(100) NOT NULL DEFAULT '' COMMENT '邮箱比较键（去空格、转小写），用于查重' AFTER `contact_email`,
    ADD COLUMN `whatsapp_key` varchar(30)  NOT NULL DEFAULT '' COMMENT 'WhatsApp比较键（只留数字、去掉开头00；少于6位为空），用于查重' AFTER `whatsapp`,
    ADD COLUMN `phone_key`    varchar(32)  NOT NULL DEFAULT '' COMMENT '联系电话比较键（规则同WhatsApp），用于查重' AFTER `contact_phone`,
    ADD KEY `idx_tenant_email_key` (`tenant_id`, `email_key`),
    ADD KEY `idx_tenant_whatsapp_key` (`tenant_id`, `whatsapp_key`),
    ADD KEY `idx_tenant_phone_key` (`tenant_id`, `phone_key`);

UPDATE `customer` SET `email_key` = LOWER(REPLACE(TRIM(`contact_email`), ' ', ''));
UPDATE `customer` SET `whatsapp_key` = REGEXP_REPLACE(REGEXP_REPLACE(`whatsapp`, '[^0-9]', ''), '^00', '');
UPDATE `customer` SET `whatsapp_key` = '' WHERE CHAR_LENGTH(`whatsapp_key`) < 6;
UPDATE `customer` SET `phone_key` = REGEXP_REPLACE(REGEXP_REPLACE(`contact_phone`, '[^0-9]', ''), '^00', '');
UPDATE `customer` SET `phone_key` = '' WHERE CHAR_LENGTH(`phone_key`) < 6;

ALTER TABLE `customer_inquiry`
    ADD COLUMN `opportunity_id` bigint(20) NULL COMMENT '来源商机ID，关联opportunity.id；从商机创建时写入，可空' AFTER `customer_id`,
    ADD KEY `idx_opportunity_id` (`opportunity_id`);

-- 菜单「商机管理」排在询盘管理下第一位，及按钮权限
INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`) VALUES
(100053, 100005, 'RS2100053', '商机管理', 2, 0, 'userAdd', '', '', '', '/inquiry/opportunities', '', 1, '', 'sys', 'sys'),
(110161, 100053, 'RS3110161', '登记商机', 3, 1, '', '', '', '', '', 'crm:opportunity:add',  1, '', 'sys', 'sys'),
(110162, 100053, 'RS3110162', '编辑商机', 3, 2, '', '', '', '', '', 'crm:opportunity:edit', 1, '', 'sys', 'sys');

UPDATE `tenant_package`
SET `menu_ids` = JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(`menu_ids`, '$', 100053), '$', 110161), '$', 110162)
WHERE `name` IN ('标准版', '旗舰版');

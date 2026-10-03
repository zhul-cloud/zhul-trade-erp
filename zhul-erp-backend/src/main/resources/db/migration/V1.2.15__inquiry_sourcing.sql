-- V1.2.15：询价协作（redesign-inquiry-sourcing）。
-- 型号明细挂在客户询盘下，询价任务只引用明细；询价记录即「历史询价」。
-- 旧的 inquiry_order* 四张表停止使用但不删除，下个版本再清理。见 openspec/changes/redesign-inquiry-sourcing/design.md。

-- ---------------------------------------------------------------- 客户询盘改造
ALTER TABLE `customer_inquiry`
    ADD COLUMN `urgent`            tinyint(1)  NOT NULL DEFAULT 0 COMMENT '是否紧急（0-否、1-是）' AFTER `expected_reply_date`,
    ADD COLUMN `quote_deadline`    date        NULL               COMMENT '报价截止日期' AFTER `urgent`,
    ADD COLUMN `customer_type`     tinyint(2)  NOT NULL DEFAULT 1 COMMENT '新老客户（1-新客户、2-老客户），创建时按该客户此前是否有已成交询盘判断的快照' AFTER `quote_deadline`,
    ADD COLUMN `parse_mode`        tinyint(2)  NOT NULL DEFAULT 0 COMMENT '型号来源（0-未录入、1-AI解析、2-手动录入）' AFTER `customer_type`,
    ADD COLUMN `priced_item_count` int(11)     NOT NULL DEFAULT 0 COMMENT '已有价格或无货的型号数（回价进度分子，分母为total_item_count）' AFTER `total_item_count`,
    ADD COLUMN `needs_review`      tinyint(1)  NOT NULL DEFAULT 0 COMMENT '是否有型号被采购以「型号存疑」退回待核实（0-否、1-是）' AFTER `pending_verify_count`,
    ADD COLUMN `parse_error`       varchar(300) NOT NULL DEFAULT '' COMMENT 'AI解析失败原因' AFTER `ai_task_id`,
    ADD KEY `idx_quote_deadline` (`quote_deadline`),
    ADD KEY `idx_customer_type` (`customer_type`);

UPDATE `customer_inquiry` SET `quote_deadline` = `inquiry_date` WHERE `quote_deadline` IS NULL;
ALTER TABLE `customer_inquiry` MODIFY COLUMN `quote_deadline` date NOT NULL COMMENT '报价截止日期';

-- 旧测试询盘统一置为已取消；来源改为「其他」
UPDATE `customer_inquiry` SET `status` = 10, `source` = 6, `update_time` = NOW(), `update_by` = 'sys';

ALTER TABLE `customer_inquiry`
    MODIFY COLUMN `source` tinyint(2) NOT NULL DEFAULT 1 COMMENT '来源渠道（1-WhatsApp、2-邮件、3-阿里国际站、4-微信、5-电话、6-其他）',
    MODIFY COLUMN `status` tinyint(2) NOT NULL DEFAULT 1 COMMENT '状态（1-待解析、2-解析中、3-待确认、4-解析失败、5-询价中、6-可报价、7-已报价、8-已成交、9-未成交、10-已取消）',
    MODIFY COLUMN `total_order_count` int(11) NOT NULL DEFAULT 0 COMMENT '询价任务数',
    MODIFY COLUMN `raw_attachment_url` varchar(256) NOT NULL DEFAULT '' COMMENT '已停用：附件改存customer_inquiry_attachment';

CREATE TABLE `customer_inquiry_attachment` (
    `id`                  bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`           int(11)      NOT NULL DEFAULT 0  COMMENT '租户ID',
    `customer_inquiry_id` bigint       NOT NULL DEFAULT 0  COMMENT '客户询盘ID，关联customer_inquiry.id',
    `file_name`           varchar(200) NOT NULL DEFAULT '' COMMENT '原始文件名',
    `file_key`            varchar(200) NOT NULL DEFAULT '' COMMENT '私有存储键，格式{模块}/{租户}/{yyyyMM}/{uuid}.{ext}',
    `content_type`        varchar(100) NOT NULL DEFAULT '' COMMENT '文件类型',
    `file_size`           bigint       NOT NULL DEFAULT 0  COMMENT '文件大小（字节）',
    `sort`                int(11)      NOT NULL DEFAULT 0  COMMENT '排序',
    `deleted_at`          datetime     NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`         datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`           varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`         datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`           varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_customer_inquiry_id` (`customer_inquiry_id`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户询盘附件';

-- ---------------------------------------------------------------- 型号明细
CREATE TABLE `inquiry_item` (
    `id`                  bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`           int(11)      NOT NULL DEFAULT 0  COMMENT '租户ID',
    `customer_inquiry_id` bigint       NOT NULL DEFAULT 0  COMMENT '客户询盘ID，关联customer_inquiry.id',
    `line_no`             int(11)      NOT NULL DEFAULT 0  COMMENT '行号，从1开始',
    `brand`               varchar(64)  NOT NULL DEFAULT '' COMMENT '品牌（展示名）',
    `brand_id`            bigint       NULL                COMMENT '品牌ID，关联product_brand.id；按品牌及别名识别不到时为空',
    `brand_key`           varchar(80)  NOT NULL DEFAULT '' COMMENT '品牌匹配键：识别到品牌时为#品牌ID，否则为小写去首尾空格的品牌名',
    `category`            varchar(32)  NOT NULL DEFAULT '' COMMENT '品类',
    `original_model`      varchar(128) NOT NULL DEFAULT '' COMMENT '原始型号（客户原文）',
    `confirmed_model`     varchar(128) NOT NULL DEFAULT '' COMMENT '确认型号',
    `model_key`           varchar(128) NOT NULL DEFAULT '' COMMENT '归一化型号（全角转半角、转小写、去掉非字母数字，与商品主数据一致）',
    `product_id`          bigint       NULL                COMMENT '命中的商品ID，关联product.id',
    `confidence`          tinyint(2)   NOT NULL DEFAULT 1  COMMENT '置信度（1-确认、2-已纠正、3-待核实、4-未识别）',
    `correction_note`     varchar(300) NOT NULL DEFAULT '' COMMENT '纠正说明',
    `quantity`            int(11)      NOT NULL DEFAULT 1  COMMENT '数量',
    `unit`                varchar(16)  NOT NULL DEFAULT '' COMMENT '单位',
    `description`         varchar(300) NOT NULL DEFAULT '' COMMENT '描述',
    `lifecycle`           tinyint(2)   NOT NULL DEFAULT 3  COMMENT '生命周期（1-在产、2-停产、3-待查）',
    `replacement_model`   varchar(128) NOT NULL DEFAULT '' COMMENT '替代型号（停产时）',
    `difficulty`          tinyint(2)   NOT NULL DEFAULT 0  COMMENT '采购难度（0-未评估、1-简单、2-中等、3-困难）',
    `inquiry_script`      varchar(500) NOT NULL DEFAULT '' COMMENT '询价话术',
    `search_keywords`     varchar(500) NOT NULL DEFAULT '' COMMENT '货源搜索关键词，JSON数组，按使用顺序',
    `price_source`        tinyint(2)   NOT NULL DEFAULT 2  COMMENT '价格来源（1-复用历史价、2-询价）',
    `quote_status`        tinyint(2)   NOT NULL DEFAULT 1  COMMENT '回价状态（1-待询价、2-已有价格、3-无货）',
    `selected_quote_id`   bigint       NULL                COMMENT '选定价格，关联sourcing_quote.id',
    `sourcing_task_id`    bigint       NULL                COMMENT '询价任务ID，关联sourcing_task.id；复用历史价时为空',
    `remark`              varchar(300) NOT NULL DEFAULT '' COMMENT '备注',
    `deleted_at`          datetime     NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`         datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`           varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`         datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`           varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_customer_inquiry_id` (`customer_inquiry_id`),
    KEY `idx_sourcing_task_id` (`sourcing_task_id`),
    KEY `idx_selected_quote_id` (`selected_quote_id`),
    KEY `idx_product_id` (`product_id`),
    KEY `idx_brand_model` (`tenant_id`, `brand_key`, `model_key`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户询盘型号明细';

-- ---------------------------------------------------------------- 询价任务
CREATE TABLE `sourcing_task` (
    `id`                  bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`           int(11)      NOT NULL DEFAULT 0  COMMENT '租户ID',
    `task_code`           varchar(40)  NOT NULL DEFAULT '' COMMENT '任务编号，格式{客户询盘编号}{字母}',
    `customer_inquiry_id` bigint       NOT NULL DEFAULT 0  COMMENT '客户询盘ID，关联customer_inquiry.id',
    `brand`               varchar(64)  NOT NULL DEFAULT '' COMMENT '品牌',
    `brand_key`           varchar(80)  NOT NULL DEFAULT '' COMMENT '品牌匹配键（同inquiry_item.brand_key）',
    `category`            varchar(32)  NOT NULL DEFAULT '' COMMENT '品类',
    `item_count`          int(11)      NOT NULL DEFAULT 0  COMMENT '型号数',
    `urgent`              tinyint(1)   NOT NULL DEFAULT 0  COMMENT '是否紧急（0-否、1-是）',
    `status`              tinyint(2)   NOT NULL DEFAULT 1  COMMENT '状态（1-待分配、2-询价中、3-已回价、4-已取消）',
    `first_assigned_at`   datetime     NULL                COMMENT '首次分配时间（超时起算点）',
    `completed_at`        datetime     NULL                COMMENT '变为已回价的时间',
    `return_reason`       tinyint(2)   NOT NULL DEFAULT 0  COMMENT '最近一次退回原因（0-无、1-型号存疑、2-停产无货、3-超出能力、9-其他）',
    `return_note`         varchar(300) NOT NULL DEFAULT '' COMMENT '最近一次退回说明',
    `returned_by`         bigint       NULL                COMMENT '最近一次退回人，关联user_basic.id',
    `returned_at`         datetime     NULL                COMMENT '最近一次退回时间',
    `deleted_at`          datetime     NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`         datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`           varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`         datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`           varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_task_code` (`tenant_id`, `task_code`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_customer_inquiry_id` (`customer_inquiry_id`),
    KEY `idx_status` (`status`),
    KEY `idx_brand_key` (`brand_key`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='询价任务（按品牌+品类拆出的分配单元）';

CREATE TABLE `sourcing_task_assignee` (
    `id`            bigint      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`     int(11)     NOT NULL DEFAULT 0  COMMENT '租户ID',
    `task_id`       bigint      NOT NULL DEFAULT 0  COMMENT '询价任务ID，关联sourcing_task.id',
    `assignee_id`   bigint      NOT NULL DEFAULT 0  COMMENT '采购人员ID，关联user_basic.id',
    `assigned_by`   bigint      NOT NULL DEFAULT 0  COMMENT '分配人ID，关联user_basic.id；规则自动分配时为0',
    `assigned_at`   datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '分配时间',
    `assign_mode`   tinyint(2)  NOT NULL DEFAULT 1  COMMENT '分配方式（1-手动、2-按推荐、3-按规则、4-追加比价、5-改派）',
    `active`        tinyint(1)  NOT NULL DEFAULT 1  COMMENT '是否有效（0-已被改派或任务被退回、1-有效）',
    `submitted_at`  datetime    NULL                COMMENT '最近一次提交回价时间',
    `deleted_at`    datetime    NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`   datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`     varchar(32) NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`   datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`     varchar(32) NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_task_id` (`task_id`),
    KEY `idx_assignee_active` (`assignee_id`, `active`),
    KEY `idx_assigned_at` (`assigned_at`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='询价任务分配';

-- ---------------------------------------------------------------- 询价记录（历史询价）
CREATE TABLE `sourcing_quote` (
    `id`                  bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`           int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID',
    `inquiry_item_id`     bigint        NOT NULL DEFAULT 0  COMMENT '型号明细ID，关联inquiry_item.id',
    `task_id`             bigint        NOT NULL DEFAULT 0  COMMENT '询价任务ID，关联sourcing_task.id',
    `customer_inquiry_id` bigint        NOT NULL DEFAULT 0  COMMENT '客户询盘ID，关联customer_inquiry.id（冗余，便于追溯）',
    `brand`               varchar(64)   NOT NULL DEFAULT '' COMMENT '品牌（冗余）',
    `brand_key`           varchar(80)   NOT NULL DEFAULT '' COMMENT '品牌匹配键（同inquiry_item.brand_key）',
    `model`               varchar(128)  NOT NULL DEFAULT '' COMMENT '型号（冗余，确认型号）',
    `model_key`           varchar(128)  NOT NULL DEFAULT '' COMMENT '归一化型号',
    `channel`             tinyint(2)    NOT NULL DEFAULT 1  COMMENT '渠道（1-淘宝、2-1688、3-闲鱼、4-供应商、5-其他）',
    `shop_name`           varchar(128)  NOT NULL DEFAULT '' COMMENT '店铺或供应商名称',
    `supplier_id`         bigint        NULL                COMMENT '供应商ID，关联supplier.id；渠道为供应商且选了主数据时有值',
    `no_stock`            tinyint(1)    NOT NULL DEFAULT 0  COMMENT '是否无货（0-否、1-是）；无货时单价为空',
    `currency_code`       char(3)       NOT NULL DEFAULT 'CNY' COMMENT '币种（ISO 4217）',
    `unit_price`          decimal(18,2) NULL                COMMENT '单价（原币），无货时为空',
    `exchange_rate`       decimal(18,6) NULL                COMMENT '汇率（原币→CNY），人民币为1',
    `unit_price_cny`      decimal(18,2) NULL                COMMENT '单价（本位币CNY），HALF_UP保留2位',
    `tax_included`        tinyint(1)    NOT NULL DEFAULT 0  COMMENT '是否含税（0-不含税、1-含税）',
    `item_condition`      tinyint(2)    NOT NULL DEFAULT 0  COMMENT '货况（0-未填、1-全新原装、2-99新、3-翻新、4-二手、5-拆机件、6-国产替代、7-待确认）',
    `lead_time`           varchar(64)   NOT NULL DEFAULT '' COMMENT '货期',
    `note`                varchar(300)  NOT NULL DEFAULT '' COMMENT '备注（无货时为无货说明）',
    `quoted_by`           bigint        NOT NULL DEFAULT 0  COMMENT '询价人ID，关联user_basic.id',
    `quoted_at`           datetime      NULL                COMMENT '提交时间（询价日期）；草稿为空',
    `status`              tinyint(2)    NOT NULL DEFAULT 1  COMMENT '状态（1-草稿、2-已提交）',
    `entry_mode`          tinyint(2)    NOT NULL DEFAULT 1  COMMENT '录入方式（1-在线录入、2-导入询价结果）',
    `import_id`           bigint        NULL                COMMENT '导入记录ID，关联sourcing_import.id',
    `deleted_at`          datetime      NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`         datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`           varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`         datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`           varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_inquiry_item_id` (`inquiry_item_id`),
    KEY `idx_task_id` (`task_id`),
    KEY `idx_customer_inquiry_id` (`customer_inquiry_id`),
    KEY `idx_supplier_id` (`supplier_id`),
    KEY `idx_import_id` (`import_id`),
    KEY `idx_brand_model` (`tenant_id`, `brand_key`, `model_key`),
    KEY `idx_model_key` (`tenant_id`, `model_key`),
    KEY `idx_status` (`status`),
    KEY `idx_quoted_at` (`quoted_at`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='询价记录（历史询价）';

CREATE TABLE `sourcing_import` (
    `id`            bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`     int(11)      NOT NULL DEFAULT 0  COMMENT '租户ID',
    `task_id`       bigint       NOT NULL DEFAULT 0  COMMENT '询价任务ID，关联sourcing_task.id',
    `file_name`     varchar(200) NOT NULL DEFAULT '' COMMENT '原始文件名',
    `file_key`      varchar(200) NOT NULL DEFAULT '' COMMENT '私有存储键',
    `imported_by`   bigint       NOT NULL DEFAULT 0  COMMENT '导入人ID，关联user_basic.id',
    `on_behalf_of`  bigint       NOT NULL DEFAULT 0  COMMENT '代谁导入（询价人），关联user_basic.id；本人导入时与imported_by相同',
    `row_count`     int(11)      NOT NULL DEFAULT 0  COMMENT '入库行数',
    `deleted_at`    datetime     NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`     varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`     varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_task_id` (`task_id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='导入询价结果记录';

CREATE TABLE `sourcing_assign_rule` (
    `id`            bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`     int(11)      NOT NULL DEFAULT 0  COMMENT '租户ID',
    `priority`      int(11)      NOT NULL DEFAULT 0  COMMENT '优先级，越小越先匹配',
    `match_type`    tinyint(2)   NOT NULL DEFAULT 1  COMMENT '匹配方式（1-品牌、2-品类）',
    `match_values`  varchar(500) NOT NULL DEFAULT '' COMMENT '匹配值，JSON数组（品牌按名称及别名匹配，品类按名称匹配）',
    `assignee_id`   bigint       NOT NULL DEFAULT 0  COMMENT '分配给的采购人员ID，关联user_basic.id',
    `status`        tinyint(2)   NOT NULL DEFAULT 1  COMMENT '状态（0-停用、1-启用）',
    `deleted_at`    datetime     NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`     varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`     varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_status` (`status`),
    KEY `idx_assignee_id` (`assignee_id`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='询价任务分配规则';

-- ---------------------------------------------------------------- 菜单与权限
-- 「询盘单列表」（100051，实际是客户询盘）改名为「客户询盘」；原「订单管理」（100052，实际是旧询盘单）下线并隐藏
UPDATE `resource` SET `name` = '客户询盘', `light_icon` = 'inbox', `sort` = 1, `update_time` = NOW(), `update_by` = 'sys' WHERE `id` = 100051;
UPDATE `resource` SET `name` = '询盘单（已下线）', `status` = 0, `is_hidden` = 1, `sort` = 99, `update_time` = NOW(), `update_by` = 'sys' WHERE `id` = 100052;

INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`) VALUES
(100054, 100005, 'RS2100054', '分配工作台',   2, 2, 'apartment',     '', '', '', '/inquiry/sourcing-board', '', 1, '', 'sys', 'sys'),
(100055, 100005, 'RS2100055', '我的询价任务', 2, 3, 'solution',      '', '', '', '/inquiry/my-tasks',       '', 1, '', 'sys', 'sys'),
(100056, 100005, 'RS2100056', '历史询价',     2, 4, 'history',       '', '', '', '/inquiry/price-history',  '', 1, '', 'sys', 'sys'),
(110171, 100054, 'RS3110171', '分配任务',     3, 1, '', '', '', '', '', 'inquiry:task:assign',   1, '', 'sys', 'sys'),
(110172, 100054, 'RS3110172', '分配规则',     3, 2, '', '', '', '', '', 'inquiry:rule:edit',     1, '', 'sys', 'sys'),
(110173, 100054, 'RS3110173', '代他人导入',   3, 3, '', '', '', '', '', 'inquiry:import:proxy',  1, '', 'sys', 'sys');

-- 有询盘菜单的套餐补上新菜单与按钮
UPDATE `tenant_package`
SET `menu_ids` = JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(
        `menu_ids`, '$', 100054), '$', 100055), '$', 100056), '$', 110171), '$', 110172), '$', 110173)
WHERE JSON_CONTAINS(`menu_ids`, '100051') AND NOT JSON_CONTAINS(`menu_ids`, '100054');

-- 原来能看旧询盘单（采购）的角色：补「我的询价任务」「历史询价」；能看客户询盘（业务员）的角色：补「历史询价」。
-- 分配工作台只给采购负责人，由管理员在角色中单独授权
INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT rr.`role_code`, r.`id`, r.`code`
FROM `role_resource` rr JOIN `resource` r ON r.`id` IN (100055, 100056)
WHERE rr.`resource_id` = 100052
  AND NOT EXISTS (SELECT 1 FROM `role_resource` x WHERE x.`role_code` = rr.`role_code` AND x.`resource_id` = r.`id`);
INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT rr.`role_code`, 100056, 'RS2100056'
FROM `role_resource` rr
WHERE rr.`resource_id` = 100051
  AND NOT EXISTS (SELECT 1 FROM `role_resource` x WHERE x.`role_code` = rr.`role_code` AND x.`resource_id` = 100056);

-- 平台级内置角色「兼职采购」：只有我的询价任务，数据范围仅本人
INSERT INTO `role` (`tenant_id`, `code`, `name`, `permission_scope`, `status`, `is_built_in`, `remark`, `create_by`, `update_by`)
SELECT 0, 'ROLE_PTBUYER', '兼职采购', 3, 1, 1, '只能看到分配给自己的询价任务，下载询价包、导入询价结果', 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `role` WHERE `code` = 'ROLE_PTBUYER');
INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT 'ROLE_PTBUYER', r.`id`, r.`code` FROM `resource` r
WHERE r.`id` IN (100005, 100055)
  AND NOT EXISTS (SELECT 1 FROM `role_resource` x WHERE x.`role_code` = 'ROLE_PTBUYER' AND x.`resource_id` = r.`id`);

-- ---------------------------------------------------------------- 系统设置（平台模板，租户可覆盖）
INSERT INTO `sys_config` (`tenant_id`, `config_key`, `config_name`, `config_value`, `config_type`, `is_builtin`, `config_group`, `remark`, `create_by`, `update_by`)
SELECT 0, k.config_key, k.config_name, k.config_value, k.config_type, 1, 'inquiry', k.remark, 'sys', 'sys'
FROM (
    SELECT 'inquiry.sourcing.timeout-hours' AS config_key, '询价任务超时（小时）' AS config_name, '24' AS config_value, 'INTEGER' AS config_type, '分配后超过该时长仍未回齐即标记超时' AS remark
    UNION ALL SELECT 'inquiry.sourcing.urgent-timeout-hours', '紧急询价任务超时（小时）', '4', 'INTEGER', '紧急任务分配后超过该时长仍未回齐即标记超时'
    UNION ALL SELECT 'inquiry.sourcing.auto-assign', '询价任务自动分配', 'false', 'BOOLEAN', '开启后新任务按分配规则自动分配'
) k
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` c WHERE c.`tenant_id` = 0 AND c.`config_key` = k.config_key);

use zhul_erp;

-- ============================
-- v1.1.0  主数据域 & 询盘中心
-- 新增 8 张表，不修改任何 v1.0.0 已有表
-- 依赖：需先执行 schema_v1.sql
-- ============================

-- ----------------------------
-- 客户表
-- ----------------------------
DROP TABLE IF EXISTS `customer`;
CREATE TABLE `customer`
(
    `id`             bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`      int(11)      NOT NULL DEFAULT 0  COMMENT '租户ID',
    `name`           varchar(128) NOT NULL DEFAULT '' COMMENT '客户名称（公司名或个人姓名）',
    `country`        varchar(64)  NOT NULL DEFAULT '' COMMENT '国家/地区',
    `contact_name`   varchar(64)  NOT NULL DEFAULT '' COMMENT '联系人',
    `contact_phone`  varchar(32)  NOT NULL DEFAULT '' COMMENT '联系电话',
    `contact_email`  varchar(100) NOT NULL DEFAULT '' COMMENT '联系邮箱',
    `status`         tinyint(2)   NOT NULL DEFAULT 1  COMMENT '状态（0-禁用、1-启用）',
    `deleted_at`     datetime     NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`      varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`      varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_name` (`name`),
    KEY `idx_status` (`status`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '客户表';

-- ----------------------------
-- 供应商表
-- ----------------------------
DROP TABLE IF EXISTS `supplier`;
CREATE TABLE `supplier`
(
    `id`             bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`      int(11)      NOT NULL DEFAULT 0  COMMENT '租户ID',
    `name`           varchar(128) NOT NULL DEFAULT '' COMMENT '供应商名称',
    `country`        varchar(64)  NOT NULL DEFAULT '' COMMENT '国家/地区',
    `contact_name`   varchar(64)  NOT NULL DEFAULT '' COMMENT '联系人',
    `contact_phone`  varchar(32)  NOT NULL DEFAULT '' COMMENT '联系电话',
    `contact_email`  varchar(100) NOT NULL DEFAULT '' COMMENT '联系邮箱',
    `main_brands`    varchar(256) NOT NULL DEFAULT '' COMMENT '主营品牌（逗号分隔，仅辅助展示，不做强校验）',
    `status`         tinyint(2)   NOT NULL DEFAULT 1  COMMENT '状态（0-禁用、1-启用）',
    `deleted_at`     datetime     NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`      varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`      varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_name` (`name`),
    KEY `idx_status` (`status`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '供应商表';

-- ----------------------------
-- 客户询盘表
-- ----------------------------
DROP TABLE IF EXISTS `customer_inquiry`;
CREATE TABLE `customer_inquiry`
(
    `id`                    bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`             int(11)      NOT NULL DEFAULT 0  COMMENT '租户ID',
    `inquiry_code`          varchar(32)  NOT NULL DEFAULT '' COMMENT '询盘编号，格式IQ{YYYYMMDD}{NNN}，如IQ20260425001',
    `customer_id`           bigint(20)   NOT NULL DEFAULT 0  COMMENT '客户ID，关联customer.id',
    `source`                tinyint(2)   NOT NULL DEFAULT 1  COMMENT '询盘来源（1-文本输入、2-Excel文件、3-图片输入、4-微信群、5-邮件、6-平台、7-其他）',
    `raw_content`           text                  COMMENT '原始内容（文本原文，或文件/图片的AI提取摘要，≤300字）',
    `raw_attachment_url`    varchar(256) NOT NULL DEFAULT '' COMMENT '原始附件地址（Excel/图片输入时的原始文件）',
    `inquiry_date`          date         NOT NULL COMMENT '询盘日期',
    `expected_reply_date`   date         NULL                COMMENT '期望回复日期（人工填写，可空）',
    `status`                tinyint(2)   NOT NULL DEFAULT 1  COMMENT '状态（1-待解析、2-解析中、3-待确认、4-解析失败、5-已确认、6-待报价、7-报价中、8-已报价、9-已成交、10-已取消）',
    `total_order_count`     int(11)      NOT NULL DEFAULT 0  COMMENT '总询盘单数（拆单确认后生成的询盘单数量）',
    `total_item_count`      int(11)      NOT NULL DEFAULT 0  COMMENT '总产品数',
    `pending_verify_count`  int(11)      NOT NULL DEFAULT 0  COMMENT '待核实数（置信度为待核实或未识别的条数）',
    `owner_id`              bigint(20)   NOT NULL DEFAULT 0  COMMENT '负责人ID，关联user_basic.id',
    `ai_task_id`            bigint(20)   NULL                COMMENT 'AI任务ID，关联ai_task.id；提交后（待解析态）尚未创建，为空，点击"开始AI解析"后才回填',
    `remark`                varchar(500) NOT NULL DEFAULT '' COMMENT '备注',
    `deleted_at`            datetime     NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`             varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`             varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_inquiry_code` (`tenant_id`, `inquiry_code`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_customer_id` (`customer_id`),
    KEY `idx_owner_id` (`owner_id`),
    KEY `idx_ai_task_id` (`ai_task_id`),
    KEY `idx_status` (`status`),
    KEY `idx_inquiry_date` (`inquiry_date`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '客户询盘表';

-- ----------------------------
-- 询盘单表
-- ----------------------------
DROP TABLE IF EXISTS `inquiry_order`;
CREATE TABLE `inquiry_order`
(
    `id`                    bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`             int(11)      NOT NULL DEFAULT 0  COMMENT '租户ID',
    `inquiry_code`          varchar(40)  NOT NULL DEFAULT '' COMMENT '询盘单编号，格式{客户询盘编号}{字母}；手动创建无父级时为IQ{YYYYMMDD}{NNN}{字母}',
    `customer_inquiry_id`   bigint(20)   NULL                COMMENT '客户询盘ID，关联customer_inquiry.id；手动创建时为空',
    `customer_id`           bigint(20)   NULL                COMMENT '客户ID，关联customer.id；仅手动创建且选择了客户时有值，与customer_inquiry_id相互独立（不是同一个客户来源的两种表达，AI拆单产生的询盘单此字段始终为空，客户信息通过customer_inquiry_id间接获取）',
    `brand`                 varchar(64)  NOT NULL DEFAULT '' COMMENT '品牌',
    `category`              varchar(32)  NOT NULL DEFAULT '' COMMENT '品类',
    `item_count`            int(11)      NOT NULL DEFAULT 0  COMMENT '型号数量',
    `assignee_id`           bigint(20)   NULL                COMMENT '采购员ID，关联user_basic.id；初始为空，分配后有值',
    `status`                tinyint(2)   NOT NULL DEFAULT 1  COMMENT '状态（1-待分配、2-已分配、3-已发供应商、4-已收报价、5-已报客户、6-已成交、7-已取消）',
    `inquiry_template`      text                  COMMENT '询价话术模版（AI生成，可直接复制发送）',
    `email_template_cn`     text                  COMMENT '邮件模版（中文，AI生成）',
    `email_template_en`     text                  COMMENT '邮件模版（英文，AI生成）',
    `ai_task_id`            bigint(20)   NULL                COMMENT 'AI任务ID；手动创建时为空',
    `remark`                varchar(500) NOT NULL DEFAULT '' COMMENT '备注',
    `deleted_at`            datetime     NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`             varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`             varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_inquiry_code` (`tenant_id`, `inquiry_code`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_customer_inquiry_id` (`customer_inquiry_id`),
    KEY `idx_customer_id` (`customer_id`),
    KEY `idx_assignee_id` (`assignee_id`),
    KEY `idx_ai_task_id` (`ai_task_id`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '询盘单表';

-- ----------------------------
-- 询盘单明细表
-- ----------------------------
DROP TABLE IF EXISTS `inquiry_order_item`;
CREATE TABLE `inquiry_order_item`
(
    `id`                    bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`             int(11)      NOT NULL DEFAULT 0  COMMENT '租户ID',
    `item_code`             varchar(48)  NOT NULL DEFAULT '' COMMENT '明细编号，格式{询盘单编号}{NN}，如IQ20260425001A01',
    `inquiry_order_id`      bigint(20)   NOT NULL DEFAULT 0  COMMENT '询盘单ID，关联inquiry_order.id',
    `brand`                 varchar(64)  NOT NULL DEFAULT '' COMMENT '品牌（冗余存储）',
    `category`              varchar(32)  NOT NULL DEFAULT '' COMMENT '品类',
    `original_model`        varchar(128) NOT NULL DEFAULT '' COMMENT '原始型号（客户原文，永不修改）',
    `confirmed_model`       varchar(128) NOT NULL DEFAULT '' COMMENT '确认型号（纠正后型号；无纠正时与原始型号相同）',
    `confidence`            tinyint(2)   NOT NULL DEFAULT 1  COMMENT '置信度（1-确认、2-已纠正、3-待核实、4-未识别）；手动创建的明细固定为1',
    `correction_note`       varchar(300) NOT NULL DEFAULT '' COMMENT '纠正说明',
    `description`           varchar(200) NOT NULL DEFAULT '' COMMENT '产品描述（联网搜索得到的核心规格参数，≤50字）',
    `quantity`              int(11)      NOT NULL DEFAULT 0  COMMENT '数量',
    `unit`                  varchar(16)  NOT NULL DEFAULT '' COMMENT '单位（中文单位）',
    `delivery_requirement`  varchar(64)  NOT NULL DEFAULT '' COMMENT '交期要求',
    `remark`                varchar(300) NOT NULL DEFAULT '' COMMENT '备注',
    `deleted_at`            datetime     NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`             varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`             varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_item_code` (`tenant_id`, `item_code`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_inquiry_order_id` (`inquiry_order_id`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '询盘单明细表';

-- ----------------------------
-- 询盘单-供应商关联表
-- ----------------------------
DROP TABLE IF EXISTS `inquiry_order_supplier`;
CREATE TABLE `inquiry_order_supplier`
(
    `id`                bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`         int(11)      NOT NULL DEFAULT 0  COMMENT '租户ID',
    `inquiry_order_id`  bigint(20)   NOT NULL DEFAULT 0  COMMENT '询盘单ID，关联inquiry_order.id',
    `source_type`       tinyint(2)   NOT NULL DEFAULT 1  COMMENT '报价来源类型（1-正式供应商、2-电商询价渠道）',
    `supplier_id`       bigint(20)   NULL                COMMENT '供应商ID，关联supplier.id；source_type=1时必填，source_type=2时为空',
    `channel_platform`  tinyint(2)   NULL                COMMENT '电商平台（1-淘宝、2-1688、3-闲鱼、4-其他）；source_type=2时必填',
    `channel_name`      varchar(128) NOT NULL DEFAULT '' COMMENT '店铺/卖家名称；source_type=2时必填',
    `channel_link`      varchar(256) NOT NULL DEFAULT '' COMMENT '商品/店铺链接；source_type=2时可选',
    `sent_date`         date         NULL                COMMENT '发出日期',
    `reply_deadline`    date         NULL                COMMENT '回复截止日期',
    `status`            tinyint(2)   NOT NULL DEFAULT 1  COMMENT '状态（1-待发送、2-已发送、3-已回复、4-未回复）',
    `quote_file_url`    varchar(256) NOT NULL DEFAULT '' COMMENT '供应商报价单附件',
    `remark`            varchar(300) NOT NULL DEFAULT '' COMMENT '备注',
    `deleted_at`        datetime     NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`         varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`         varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_inquiry_order_id` (`inquiry_order_id`),
    KEY `idx_supplier_id` (`supplier_id`),
    KEY `idx_status` (`status`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '询盘单-供应商关联表';

-- ----------------------------
-- 型号×供应商报价表
-- ----------------------------
DROP TABLE IF EXISTS `inquiry_order_item_quote`;
CREATE TABLE `inquiry_order_item_quote`
(
    `id`                        bigint(20)     NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`                 int(11)        NOT NULL DEFAULT 0  COMMENT '租户ID',
    `inquiry_order_item_id`     bigint(20)     NOT NULL DEFAULT 0  COMMENT '询盘单明细ID，关联inquiry_order_item.id',
    `inquiry_order_supplier_id` bigint(20)     NOT NULL DEFAULT 0  COMMENT '询盘单-供应商关联ID，关联inquiry_order_supplier.id（不直接存supplier_id，正式供应商/电商询价渠道两种来源统一通过此外键取报价方信息）',
    `quote_price_original`      decimal(18, 2) NULL                COMMENT '报价（原币），未报价时为空',
    `currency_code`             char(3)        NULL                COMMENT '币种（ISO 4217，如CNY/USD），未报价时为空',
    `exchange_rate`             decimal(18, 6) NULL                COMMENT '汇率（原币→本位币CNY），币种非本位币且汇率未维护时为空',
    `quote_price_cny`           decimal(18, 2) NULL                COMMENT '报价（本位币），由原币金额与汇率计算得出；汇率未维护时为空（前端展示为"未计算"，不是0元）',
    `supplier_delivery`         varchar(64)    NOT NULL DEFAULT '' COMMENT '供应商货期',
    `quote_status`              tinyint(2)     NOT NULL DEFAULT 1  COMMENT '报价状态（1-待报价、2-已报价、3-无法报价、4-客户确认）',
    `deleted_at`                datetime       NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`               datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`                 varchar(32)    NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`               datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`                 varchar(32)    NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_item_supplier` (`inquiry_order_item_id`, `inquiry_order_supplier_id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_inquiry_order_supplier_id` (`inquiry_order_supplier_id`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '型号×供应商报价表';

-- ----------------------------
-- AI任务表（通用表，供多个skill复用）
-- ----------------------------
DROP TABLE IF EXISTS `ai_task`;
CREATE TABLE `ai_task`
(
    `id`             bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`      int(11)      NOT NULL DEFAULT 0  COMMENT '租户ID',
    `skill_id`       varchar(64)  NOT NULL DEFAULT '' COMMENT 'Skill标识，如inquiry-parse-and-split',
    `status`         tinyint(2)   NOT NULL DEFAULT 1  COMMENT '状态（1-排队中、2-处理中、3-已完成、4-失败）',
    `output`         json         NULL                COMMENT '处理结果（AI服务回传的结构化JSON）',
    `error_message`  varchar(500) NOT NULL DEFAULT '' COMMENT '失败原因',
    `requested_by`   bigint(20)   NOT NULL DEFAULT 0  COMMENT '发起人ID，关联user_basic.id',
    `started_at`     datetime     NULL                COMMENT '开始时间',
    `completed_at`   datetime     NULL                COMMENT '完成时间',
    `deleted_at`     datetime     NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`      varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`      varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_skill_id` (`skill_id`),
    KEY `idx_status` (`status`),
    KEY `idx_requested_by` (`requested_by`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI任务表';

-- ===========================
-- 报价中心：报价单、定价策略（SOP V6）、系统汇率、单据模版；字典英文名称与「未成交原因」
-- 见 openspec/changes/add-quotation-pricing
-- 定价策略默认值、单据示例模版放在平台（tenant_id=0），租户没有自己的设置时沿用平台值
-- ===========================

-- ---------------------------------------------------------------- 报价单
CREATE TABLE `quotation` (
    `id`                bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`         int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID',
    `quotation_no`      varchar(20)   NOT NULL DEFAULT '' COMMENT '报价单编号（QT+年月日+3位当日序号，如 QT20261004001）',
    `customer_id`       bigint        NOT NULL DEFAULT 0  COMMENT '客户ID，关联customer.id',
    `owner_id`          bigint        NOT NULL DEFAULT 0  COMMENT '创建人（业务员）用户ID，关联user_basic.id',
    `currency_code`     char(3)       NOT NULL DEFAULT 'USD' COMMENT '报价币种（ISO 4217）',
    `exchange_rate`     decimal(18,6) NOT NULL DEFAULT 1.000000 COMMENT '汇率快照（1外币=人民币），取系统汇率',
    `rate_time`         datetime      NULL                COMMENT '汇率快照对应的系统汇率更新时间',
    `incoterm`          varchar(16)   NOT NULL DEFAULT '' COMMENT '贸易术语（Incoterms 2020）',
    `incoterm_place`    varchar(64)   NOT NULL DEFAULT '' COMMENT '术语地点',
    `valid_until`       date          NULL                COMMENT '有效期至（默认新建日 + 15 天）',
    `remark`            varchar(500)  NOT NULL DEFAULT '' COMMENT '备注（显示在报价单上）',
    `status`            tinyint(2)    NOT NULL DEFAULT 1  COMMENT '状态（1-草稿、2-已发送、3-已成交、4-未成交、5-已作废）',
    `lost_reason`       varchar(64)   NOT NULL DEFAULT '' COMMENT '未成交原因（字典 quotation_lost_reason 的 item_code）',
    `lost_reason_name`  varchar(64)   NOT NULL DEFAULT '' COMMENT '未成交原因名称快照',
    `lost_note`         varchar(300)  NOT NULL DEFAULT '' COMMENT '未成交说明（选「其他」时必填）',
    `copied_from_id`    bigint        NULL                COMMENT '复制来源报价单ID，关联quotation.id',
    `item_amount`       decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '型号小计合计（报价币种）',
    `fee_amount`        decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '费用合计（报价币种）',
    `total_amount`      decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '合计（报价币种）',
    `total_amount_cny`  decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '合计（本位币CNY）= 合计 × 汇率，HALF_UP 2位',
    `net_profit`        decimal(18,2) NULL                COMMENT '合计净利润（报价币种），不含费用',
    `net_profit_cny`    decimal(18,2) NULL                COMMENT '合计净利润（CNY）',
    `margin_rate`       decimal(5,2)  NULL                COMMENT '合计毛利率（%），按有采购成本价的型号行计算',
    `sent_at`           datetime      NULL                COMMENT '首次标为已发送的时间',
    `closed_at`         datetime      NULL                COMMENT '标为成交 / 未成交 / 作废的时间',
    `deleted_at`        datetime      NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`       datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`         varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`       datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`         varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    UNIQUE KEY `uk_tenant_quotation_no` (`tenant_id`, `quotation_no`),
    KEY `idx_customer_id` (`customer_id`),
    KEY `idx_owner_id` (`owner_id`),
    KEY `idx_copied_from_id` (`copied_from_id`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB AUTO_INCREMENT=10000 DEFAULT CHARSET=utf8mb4 COMMENT='报价单';

CREATE TABLE `quotation_item` (
    `id`                  bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`           int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID',
    `quotation_id`        bigint        NOT NULL DEFAULT 0  COMMENT '报价单ID，关联quotation.id',
    `line_no`             int(11)       NOT NULL DEFAULT 0  COMMENT '行号',
    `customer_inquiry_id` bigint        NOT NULL DEFAULT 0  COMMENT '来源客户询盘ID，关联customer_inquiry.id',
    `inquiry_item_id`     bigint        NOT NULL DEFAULT 0  COMMENT '来源型号明细ID，关联inquiry_item.id',
    `cost_quote_id`       bigint        NULL                COMMENT '采购成本价对应的询价记录ID，关联sourcing_quote.id；无货为空',
    `model`               varchar(128)  NOT NULL DEFAULT '' COMMENT '型号快照',
    `brand`               varchar(64)   NOT NULL DEFAULT '' COMMENT '品牌快照',
    `brand_key`           varchar(80)   NOT NULL DEFAULT '' COMMENT '品牌匹配键（用于现货优势品牌判断）',
    `category`            varchar(32)   NOT NULL DEFAULT '' COMMENT '品类快照',
    `description`         varchar(300)  NOT NULL DEFAULT '' COMMENT '描述（英文，显示在报价单上）',
    `item_condition`      tinyint(2)    NOT NULL DEFAULT 0  COMMENT '货况（字典 inquiry_item_condition 码值，0-未填）',
    `lead_time`           tinyint(2)    NOT NULL DEFAULT 0  COMMENT '对客户的货期（字典 inquiry_lead_time 码值，0-未填）',
    `warranty`            varchar(32)   NOT NULL DEFAULT '1 year' COMMENT '质保',
    `quantity`            int(11)       NOT NULL DEFAULT 1  COMMENT '数量',
    `no_stock`            tinyint(1)    NOT NULL DEFAULT 0  COMMENT '是否无货（0-否、1-是）；无货时没有采购成本价，售价手填',
    `cost_price`          decimal(18,2) NULL                COMMENT '采购成本价快照（CNY，不含税）',
    `pricing_mode`        tinyint(2)    NOT NULL DEFAULT 1  COMMENT '定价方式（1-按毛利率、2-按加价、3-直接填外币售价）',
    `margin_rate`         decimal(5,2)  NULL                COMMENT '毛利率（%）= 1 − 采购成本价 ÷ (外币售价 × 汇率)',
    `markup_amount`       decimal(18,2) NULL                COMMENT '加价金额（CNY），按加价时有值',
    `suggested_margin`    decimal(5,2)  NULL                COMMENT '建议毛利率快照（%），品相待查时为空',
    `suggest_basis`       varchar(64)   NOT NULL DEFAULT '' COMMENT '建议依据快照，如「全新原装 10%」「低值耗材 ≤ CNY 100」',
    `floor_margin`        decimal(5,2)  NULL                COMMENT '红线快照（%）',
    `hints`               varchar(200)  NOT NULL DEFAULT '' COMMENT '建议主管核价提示码，逗号分隔（DISCONTINUED_URGENT、PREMIUM_BRAND、TO_CONFIRM）',
    `unit_price`          decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '售价（报价币种）',
    `unit_price_cny`      decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '售价（CNY）',
    `amount`              decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '小计（报价币种）= 售价 × 数量',
    `amount_cny`          decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '小计（CNY）= 小计 × 汇率',
    `net_profit`          decimal(18,2) NULL                COMMENT '净利润（报价币种）',
    `net_profit_cny`      decimal(18,2) NULL                COMMENT '净利润（CNY）',
    `deleted_at`          datetime      NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`         datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`           varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`         datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`           varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_quotation_id` (`quotation_id`),
    KEY `idx_customer_inquiry_id` (`customer_inquiry_id`),
    KEY `idx_inquiry_item_id` (`inquiry_item_id`),
    KEY `idx_cost_quote_id` (`cost_quote_id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报价单型号行';

CREATE TABLE `quotation_fee` (
    `id`            bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`     int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID',
    `quotation_id`  bigint        NOT NULL DEFAULT 0  COMMENT '报价单ID，关联quotation.id',
    `fee_name`      varchar(64)   NOT NULL DEFAULT '' COMMENT '费用名称（如 Shipping Cost、Bank Charge）',
    `amount`        decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '金额（报价币种）',
    `amount_cny`    decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '金额（CNY）',
    `sort_order`    int(11)       NOT NULL DEFAULT 0  COMMENT '排序',
    `deleted_at`    datetime      NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`   datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`     varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`   datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`     varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_quotation_id` (`quotation_id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报价单费用行（不计入毛利率与净利润）';

CREATE TABLE `quotation_send_log` (
    `id`            bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`     int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID',
    `quotation_id`  bigint        NOT NULL DEFAULT 0  COMMENT '报价单ID，关联quotation.id',
    `channel`       tinyint(2)    NOT NULL DEFAULT 1  COMMENT '发送方式（1-文字、2-Excel、3-PDF、4-图片）',
    `sent_by`       bigint        NOT NULL DEFAULT 0  COMMENT '操作人用户ID',
    `sent_at`       datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
    `deleted_at`    datetime      NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`   datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`     varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`   datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`     varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_quotation_id` (`quotation_id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报价单发送记录';

-- ---------------------------------------------------------------- 定价策略（平台默认值 tenant_id=0，租户可覆盖）
CREATE TABLE `pricing_condition_margin` (
    `id`             int(11)       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`      int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID（0-平台默认）',
    `item_condition` tinyint(2)    NOT NULL DEFAULT 0  COMMENT '货况（字典 inquiry_item_condition 码值）',
    `margin_rate`    decimal(5,2)  NULL                COMMENT '建议毛利率（%，0–95），为空表示不给建议值',
    `floor_rate`     decimal(5,2)  NULL                COMMENT '红线（%），不高于建议毛利率',
    `status`         tinyint(2)    NOT NULL DEFAULT 1  COMMENT '状态（0-禁用、1-启用）',
    `create_time`    datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`      varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`    datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`      varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_condition` (`tenant_id`, `item_condition`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=1000 DEFAULT CHARSET=utf8mb4 COMMENT='定价策略：品相毛利率与红线';

CREATE TABLE `pricing_amount_tier` (
    `id`           int(11)       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`    int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID（0-平台默认）',
    `max_cost`     decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '采购成本价上限（CNY，含）',
    `margin_rate`  decimal(5,2)  NOT NULL DEFAULT 0.00 COMMENT '毛利率（%）',
    `sort_order`   int(11)       NOT NULL DEFAULT 0  COMMENT '排序（按上限升序）',
    `status`       tinyint(2)    NOT NULL DEFAULT 1  COMMENT '状态（0-禁用、1-启用）',
    `create_time`  datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`    varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`  datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`    varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=1000 DEFAULT CHARSET=utf8mb4 COMMENT='定价策略：低值耗材金额分层（仅品相待确认且采购成本价不超过上限时）';

INSERT INTO `pricing_condition_margin` (`tenant_id`, `item_condition`, `margin_rate`, `floor_rate`, `create_by`, `update_by`)
SELECT 0, v.c, v.m, v.f, 'sys', 'sys' FROM (
    SELECT 1 AS c, 10.00 AS m, 10.00 AS f
    UNION ALL SELECT 2, 20.00, 15.00
    UNION ALL SELECT 3, 20.00, 15.00
    UNION ALL SELECT 4, 15.00, 10.00
    UNION ALL SELECT 5, 15.00, 10.00
    UNION ALL SELECT 6, 62.00, 62.00
    UNION ALL SELECT 7, NULL, NULL
) v
WHERE NOT EXISTS (SELECT 1 FROM `pricing_condition_margin` x WHERE x.tenant_id = 0 AND x.item_condition = v.c);

INSERT INTO `pricing_amount_tier` (`tenant_id`, `max_cost`, `margin_rate`, `sort_order`, `create_by`, `update_by`)
SELECT 0, v.mx, v.m, v.s, 'sys', 'sys' FROM (
    SELECT 100.00 AS mx, 50.00 AS m, 1 AS s
    UNION ALL SELECT 200.00, 30.00, 2
    UNION ALL SELECT 300.00, 20.00, 3
) v
WHERE NOT EXISTS (SELECT 1 FROM `pricing_amount_tier` x WHERE x.tenant_id = 0);

INSERT INTO `sys_config` (`tenant_id`, `config_key`, `config_name`, `config_value`, `config_type`, `is_builtin`, `config_group`, `remark`, `create_by`, `update_by`)
SELECT 0, k.config_key, k.config_name, k.config_value, k.config_type, 1, 'quotation', k.remark, 'sys', 'sys'
FROM (
    SELECT 'quotation.hint.discontinued-urgent' AS config_key, '提示：停产急件' AS config_name, 'true' AS config_value, 'BOOLEAN' AS config_type, '型号停产且询盘紧急时提示建议主管核价（SOP 10.4）' AS remark
    UNION ALL SELECT 'quotation.hint.premium-brand', '提示：现货优势品牌', 'true', 'BOOLEAN', '型号品牌在现货优势品牌清单中时提示建议主管核价（SOP 4.2.1）'
    UNION ALL SELECT 'quotation.hint.returning-customer', '提示：老客户', 'true', 'BOOLEAN', '老客户报价时提示参考历史成交毛利率（SOP 4.3）'
    UNION ALL SELECT 'quotation.hint.to-confirm', '提示：品相或生命周期待查', 'true', 'BOOLEAN', '品相待查或生命周期待查时提示建议主管核价'
    UNION ALL SELECT 'quotation.premium-brands', '现货优势品牌', '["VEGA"]', 'JSON', '品牌名称列表，按品牌及别名识别'
) k
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` c WHERE c.config_key = k.config_key AND c.tenant_id = 0);

-- ---------------------------------------------------------------- 系统汇率（按租户维护，没有平台默认值）
CREATE TABLE `exchange_rate` (
    `id`             int(11)       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`      int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID',
    `currency_code`  char(3)       NOT NULL DEFAULT '' COMMENT '外币币种（ISO 4217）',
    `rate`           decimal(18,6) NOT NULL DEFAULT 0.000000 COMMENT '汇率（1外币=人民币），6位小数，大于0',
    `source`         tinyint(2)    NOT NULL DEFAULT 1  COMMENT '来源（1-手动录入）',
    `updated_by_id`  bigint        NOT NULL DEFAULT 0  COMMENT '最后更新人用户ID',
    `rate_time`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '汇率更新时间',
    `status`         tinyint(2)    NOT NULL DEFAULT 1  COMMENT '状态（0-禁用、1-启用）',
    `create_time`    datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`      varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`    datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`      varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_currency` (`tenant_id`, `currency_code`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=1000 DEFAULT CHARSET=utf8mb4 COMMENT='系统汇率';

CREATE TABLE `exchange_rate_log` (
    `id`             bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`      int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID',
    `currency_code`  char(3)       NOT NULL DEFAULT '' COMMENT '币种',
    `old_rate`       decimal(18,6) NULL                COMMENT '原汇率，首次设置为空',
    `new_rate`       decimal(18,6) NOT NULL DEFAULT 0.000000 COMMENT '新汇率',
    `operator_id`    bigint        NOT NULL DEFAULT 0  COMMENT '操作人用户ID',
    `operated_at`    datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    `create_time`    datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`      varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`    datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`      varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_currency` (`tenant_id`, `currency_code`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统汇率变更记录';

-- ---------------------------------------------------------------- 单据模版（平台 tenant_id=0 放内置 V1，租户没有自己的默认版本时沿用）
CREATE TABLE `document_template` (
    `id`                  int(11)       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`           int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID（0-平台内置）',
    `doc_type`            tinyint(2)    NOT NULL DEFAULT 1  COMMENT '单据类型（1-报价单、2-形式发票PI、3-商业发票CI、4-装箱单PL、5-文字报价）',
    `default_version_id`  bigint        NULL                COMMENT '默认版本ID，关联document_template_version.id',
    `builtin_disabled`    tinyint(1)    NOT NULL DEFAULT 0  COMMENT '该租户是否停用平台内置版本（0-否、1-是）',
    `status`              tinyint(2)    NOT NULL DEFAULT 1  COMMENT '状态（0-禁用、1-启用）',
    `create_time`         datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`           varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`         datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`           varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_type` (`tenant_id`, `doc_type`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=1000 DEFAULT CHARSET=utf8mb4 COMMENT='单据模版（每租户每类一条，记默认版本）';

CREATE TABLE `document_template_version` (
    `id`           bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`    int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID（0-平台内置）',
    `doc_type`     tinyint(2)    NOT NULL DEFAULT 1  COMMENT '单据类型（同 document_template.doc_type）',
    `version_no`   int(11)       NOT NULL DEFAULT 1  COMMENT '版本号（同租户同类型内自增，平台内置为 1）',
    `note`         varchar(200)  NOT NULL DEFAULT '' COMMENT '版本说明',
    `file_key`     varchar(255)  NOT NULL DEFAULT '' COMMENT 'xlsx 文件（私有存储 key；内置模版为 classpath: 路径）',
    `file_name`    varchar(128)  NOT NULL DEFAULT '' COMMENT '原始文件名',
    `content`      text          NULL                COMMENT '文字报价模版正文（doc_type=5）',
    `uploaded_by`  bigint        NOT NULL DEFAULT 0  COMMENT '上传人用户ID（内置为 0）',
    `builtin`      tinyint(1)    NOT NULL DEFAULT 0  COMMENT '是否系统内置（0-否、1-是）',
    `status`       tinyint(2)    NOT NULL DEFAULT 1  COMMENT '状态（0-停用、1-启用）',
    `create_time`  datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`    varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`  datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`    varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_type_version` (`tenant_id`, `doc_type`, `version_no`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=10000 DEFAULT CHARSET=utf8mb4 COMMENT='单据模版版本（保存后不可修改）';

INSERT INTO `document_template_version` (`tenant_id`, `doc_type`, `version_no`, `note`, `file_key`, `file_name`, `content`, `builtin`, `create_by`, `update_by`)
SELECT 0, v.t, 1, v.note, v.fk, v.fn, v.content, 1, 'sys', 'sys' FROM (
    SELECT 1 AS t, '通用示例模版（系统内置）' AS note, 'classpath:document-template/quotation-v1.xlsx' AS fk, 'quotation-v1.xlsx' AS fn, NULL AS content
    UNION ALL SELECT 2, '通用示例模版（系统内置）', 'classpath:document-template/pi-v1.xlsx', 'pi-v1.xlsx', NULL
    UNION ALL SELECT 3, '通用示例模版（系统内置）', 'classpath:document-template/ci-v1.xlsx', 'ci-v1.xlsx', NULL
    UNION ALL SELECT 4, '通用示例模版（系统内置）', 'classpath:document-template/pl-v1.xlsx', 'pl-v1.xlsx', NULL
    UNION ALL SELECT 5, '通用示例模版（系统内置）', '', '',
        '{{#items}}\n${item.model} ${item.brand} ${item.qty} ${quotation.currencySymbol}${item.unitPriceShort} ${item.leadTimeEn} ${item.conditionEn} ${item.warranty} warranty time\n{{/items}}'
) v
WHERE NOT EXISTS (SELECT 1 FROM `document_template_version` x WHERE x.tenant_id = 0 AND x.doc_type = v.t AND x.version_no = 1);

INSERT INTO `document_template` (`tenant_id`, `doc_type`, `default_version_id`, `create_by`, `update_by`)
SELECT 0, v.doc_type, v.id, 'sys', 'sys' FROM `document_template_version` v
WHERE v.tenant_id = 0 AND v.version_no = 1
  AND NOT EXISTS (SELECT 1 FROM `document_template` x WHERE x.tenant_id = 0 AND x.doc_type = v.doc_type);

-- ---------------------------------------------------------------- 字典：英文名称、国产替代、未成交原因
ALTER TABLE `dict_item`
    ADD COLUMN `item_name_en` varchar(64) NOT NULL DEFAULT '' COMMENT '英文名称（对外单据与文字报价使用）' AFTER `item_name`;

UPDATE `dict_item` SET `item_name_en` = CASE `item_code`
        WHEN 'NEW' THEN 'original new'
        WHEN 'LIKE_NEW' THEN 'like new'
        WHEN 'REFURBISHED' THEN 'refurbished'
        WHEN 'USED' THEN 'used'
        WHEN 'DISMANTLED' THEN 'dismantled'
        WHEN 'DOMESTIC_ALT' THEN 'domestic alternative'
        WHEN 'TO_CONFIRM' THEN 'to be confirmed'
        ELSE `item_name_en` END,
    `update_time` = NOW(), `update_by` = 'sys'
WHERE `dict_type` = 'inquiry_item_condition';

-- SOP V6：对外统一使用「国产替代」，禁止使用「高仿」
UPDATE `dict_item` SET `item_name` = '国产替代', `update_time` = NOW(), `update_by` = 'sys'
WHERE `dict_type` = 'inquiry_item_condition' AND `item_code` = 'DOMESTIC_ALT';

UPDATE `dict_item` SET `item_name_en` = CASE `item_code`
        WHEN 'IN_STOCK' THEN 'in stock'
        WHEN 'DAYS_1_2' THEN '1-2 days'
        WHEN 'DAYS_2_3' THEN '2-3 days'
        WHEN 'DAYS_3_5' THEN '3-5 days'
        WHEN 'DAYS_5_7' THEN '5-7 days'
        WHEN 'WEEKS_1_2' THEN '1-2 weeks'
        WHEN 'WEEKS_2_4' THEN '2-4 weeks'
        WHEN 'WEEKS_4_8' THEN '4-8 weeks'
        WHEN 'WEEKS_8_PLUS' THEN 'over 8 weeks'
        ELSE `item_name_en` END,
    `update_time` = NOW(), `update_by` = 'sys'
WHERE `dict_type` = 'inquiry_lead_time';

INSERT INTO `dict_type` (`tenant_id`, `dict_type`, `dict_name`, `is_builtin`, `status`, `remark`, `create_by`, `update_by`)
SELECT 0, 'quotation_lost_reason', '未成交原因', 1, 1, '报价单未成交原因；名称中【】内为分组，界面按分组展示；码值 OTHER 为「其他」，选择时说明必填', 'sys', 'sys'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `dict_type` WHERE `dict_type` = 'quotation_lost_reason' AND `tenant_id` = 0);

INSERT INTO `dict_item` (`tenant_id`, `dict_type_id`, `dict_type`, `item_code`, `item_name`, `item_value`, `sort_order`, `is_default`, `status`, `remark`, `create_by`, `update_by`)
SELECT 0, t.id, t.dict_type, v.code, v.name, v.val, v.s, 0, 1, '', 'sys', 'sys'
FROM `dict_type` t
JOIN (
    SELECT 'PRICE_HIGH' AS code, '【价格/成本】产品价格超预期' AS name, '1' AS val, 1 AS s
    UNION ALL SELECT 'FREIGHT_HIGH', '【价格/成本】运费及清关成本过高', '2', 2
    UNION ALL SELECT 'PAYMENT_TERMS', '【价格/成本】付款方式未达成一致', '3', 3
    UNION ALL SELECT 'CANNOT_QUOTE', '【价格/成本】无法报价', '4', 4
    UNION ALL SELECT 'LEAD_TIME', '【交期/物流】交期无法满足需求', '5', 5
    UNION ALL SELECT 'OUT_OF_STOCK', '【交期/物流】缺货/停产无替代方案', '6', 6
    UNION ALL SELECT 'LOGISTICS', '【交期/物流】物流渠道/时效受限', '7', 7
    UNION ALL SELECT 'NON_CORE', '【交期/物流】非主营产品需求', '8', 8
    UNION ALL SELECT 'SPEC_MISMATCH', '【技术/资质】技术参数/选型不匹配', '9', 9
    UNION ALL SELECT 'NO_CERT', '【技术/资质】资质与认证缺失', '10', 10
    UNION ALL SELECT 'AUTHENTICITY', '【技术/资质】货源真伪/质保担忧', '11', 11
    UNION ALL SELECT 'PRICE_WAR', '【竞争对手】同行低价恶性竞争', '12', 12
    UNION ALL SELECT 'LOCAL_AGENT', '【竞争对手】输给客户当地代理商', '13', 13
    UNION ALL SELECT 'FAKE_INQUIRY', '【竞争对手】同行套价/虚假询盘', '14', 14
    UNION ALL SELECT 'NO_RESPONSE', '【客户/项目】客户失联/持续无回复', '15', 15
    UNION ALL SELECT 'PROJECT_HOLD', '【客户/项目】项目取消或搁置', '16', 16
    UNION ALL SELECT 'OTHER_SUPPLIER', '【客户/项目】决策受阻/选用其他供应商', '17', 17
    UNION ALL SELECT 'OTHER', '其他', '99', 99
) v
WHERE t.dict_type = 'quotation_lost_reason' AND t.tenant_id = 0
  AND NOT EXISTS (SELECT 1 FROM `dict_item` i WHERE i.dict_type_id = t.id AND i.item_code = v.code);

-- ---------------------------------------------------------------- 菜单与按钮权限
INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`)
SELECT v.* FROM (
    SELECT 100072 AS id, 0 AS pid, 'RS1100072' AS code, '报价中心' AS name, 1 AS type, 4 AS sort, 'fileText' AS li, '' AS lsi, '' AS di, '' AS dsi,
           '/quotation' AS path, '' AS permission, 1 AS status, '' AS micro_app, 'sys' AS cb, 'sys' AS ub
    UNION ALL SELECT 100073, 100072, 'RS2100073', '报价单',   2, 1, 'fileText',   '', '', '', '/quotation/quotations', '', 1, '', 'sys', 'sys'
    UNION ALL SELECT 100074, 100072, 'RS2100074', '定价策略', 2, 2, 'percentage', '', '', '', '/quotation/pricing',    '', 1, '', 'sys', 'sys'
    UNION ALL SELECT 100075, 100002, 'RS2100075', '汇率设置', 2, 7, 'transaction', '', '', '', '/system/exchange-rate', '', 1, '', 'sys', 'sys'
    UNION ALL SELECT 100076, 100002, 'RS2100076', '单据模版', 2, 8, 'fileExcel',  '', '', '', '/system/document-template', '', 1, '', 'sys', 'sys'
    UNION ALL SELECT 110176, 100074, 'RS3110176', '编辑定价策略', 3, 1, '', '', '', '', '', 'quotation:pricing:edit', 1, '', 'sys', 'sys'
    UNION ALL SELECT 110177, 100075, 'RS3110177', '修改汇率',     3, 1, '', '', '', '', '', 'system:exchange-rate:edit', 1, '', 'sys', 'sys'
    UNION ALL SELECT 110178, 100076, 'RS3110178', '管理单据模版', 3, 1, '', '', '', '', '', 'system:document-template:edit', 1, '', 'sys', 'sys'
) v
WHERE NOT EXISTS (SELECT 1 FROM `resource` r WHERE r.`id` = v.id);

-- 客户、供应商顺延；系统设置与日志顺延到汇率设置、单据模版之后
UPDATE `resource` SET `sort` = 5, `update_time` = NOW(), `update_by` = 'sys' WHERE `id` = 100006;
UPDATE `resource` SET `sort` = 6, `update_time` = NOW(), `update_by` = 'sys' WHERE `id` = 100008;
UPDATE `resource` SET `sort` = 9,  `update_time` = NOW(), `update_by` = 'sys' WHERE `id` = 100017;
UPDATE `resource` SET `sort` = 10, `update_time` = NOW(), `update_by` = 'sys' WHERE `id` = 100018;
UPDATE `resource` SET `sort` = 11, `update_time` = NOW(), `update_by` = 'sys' WHERE `id` = 100019;

UPDATE `tenant_package`
SET `menu_ids` = JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(
        `menu_ids`, '$', 100072), '$', 100073), '$', 100074), '$', 100075), '$', 100076), '$', 110176), '$', 110177)
WHERE `name` IN ('标准版', '旗舰版') AND NOT JSON_CONTAINS(`menu_ids`, '100072');
UPDATE `tenant_package`
SET `menu_ids` = JSON_ARRAY_APPEND(`menu_ids`, '$', 110178)
WHERE `name` IN ('标准版', '旗舰版') AND NOT JSON_CONTAINS(`menu_ids`, '110178');

-- 内置「租户管理员」获得全部新菜单与按钮
INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT r.`code`, s.`id`, s.`code`
FROM `role` r
JOIN `resource` s ON s.`id` IN (100072, 100073, 100074, 100075, 100076, 110176, 110177, 110178)
WHERE r.`code` = 'ROLE_ADMIN' AND r.`is_built_in` = 1
  AND NOT EXISTS (SELECT 1 FROM `role_resource` x WHERE x.`role_code` = r.`code` AND x.`resource_id` = s.`id`);

-- 能看客户询盘的角色（业务员）同时获得「报价中心 → 报价单 / 定价策略（只读）」；兼职采购没有客户询盘菜单，不会获得
INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT rr.`role_code`, s.`id`, s.`code`
FROM `role_resource` rr
JOIN `resource` s ON s.`id` IN (100072, 100073, 100074)
WHERE rr.`resource_id` = 100051
  AND NOT EXISTS (SELECT 1 FROM `role_resource` x WHERE x.`role_code` = rr.`role_code` AND x.`resource_id` = s.`id`);

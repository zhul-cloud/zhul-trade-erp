-- ===========================
-- 销售管理：PI（形式发票，多版本）、收款登记（水单 / 到账）、销售订单（最小闭环）、收款账户、全链路单据编号
-- 见 openspec/changes/add-proforma-invoice
-- ===========================

-- ---------------------------------------------------------------- PI
CREATE TABLE `proforma_invoice` (
    `id`                  bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`           int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID',
    `pi_no`               varchar(24)   NOT NULL DEFAULT '' COMMENT 'PI 编号（租户前缀 + PI + 年月日 + 当日流水，如 FWPI20261006001），各版本不变',
    `customer_id`         bigint        NOT NULL DEFAULT 0  COMMENT '客户ID，关联customer.id',
    `owner_id`            bigint        NOT NULL DEFAULT 0  COMMENT '业务员（创建人）用户ID，关联user_basic.id',
    `currency_code`       char(3)       NOT NULL DEFAULT 'USD' COMMENT '币种（ISO 4217），取来源报价单',
    `exchange_rate`       decimal(18,6) NOT NULL DEFAULT 1.000000 COMMENT '汇率快照（1外币=人民币），新建时的系统汇率',
    `rate_time`           datetime      NULL                COMMENT '汇率快照对应的系统汇率更新时间',
    `status`              tinyint(2)    NOT NULL DEFAULT 1  COMMENT '状态（1-草稿、2-已发送、3-已转订单、4-已作废）',
    `current_version_no`  int(11)       NOT NULL DEFAULT 0  COMMENT '当前有效版本号（最后一个已发送的版本），0 表示还没发送过',
    `editing_version_no`  int(11)       NULL                COMMENT '正在编辑的版本号（草稿或修改中的新版本），没有时为空',
    `item_count`          int(11)       NOT NULL DEFAULT 0  COMMENT '型号数（展示用，取有效版本，没有时取编辑中的版本）',
    `total_amount`        decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '合计（原币，取值规则同型号数）',
    `total_amount_cny`    decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '合计（本位币CNY）= 合计 × 汇率，HALF_UP 2位',
    `receipt_status`      tinyint(2)    NOT NULL DEFAULT 1  COMMENT '收款状态（1-未付款、2-待到账、3-部分到账、4-已到账）',
    `received_amount`     decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '已到账（原币，有效到账记录之和）',
    `fee_diff_amount`     decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '记为银行中转手续费的差额（原币）',
    `sent_at`             datetime      NULL                COMMENT '最近一次发送时间',
    `deleted_at`          datetime      NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`         datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`           varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`         datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`           varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_pi_no` (`tenant_id`, `pi_no`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_customer_id` (`customer_id`),
    KEY `idx_owner_id` (`owner_id`),
    KEY `idx_status` (`status`),
    KEY `idx_receipt_status` (`receipt_status`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB AUTO_INCREMENT=10000 DEFAULT CHARSET=utf8mb4 COMMENT='PI（形式发票），内容按版本保存在 proforma_invoice_version';

CREATE TABLE `proforma_invoice_version` (
    `id`                  bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`           int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID',
    `pi_id`               bigint        NOT NULL DEFAULT 0  COMMENT 'PI ID，关联proforma_invoice.id',
    `version_no`          int(11)       NOT NULL DEFAULT 1  COMMENT '版本号（Rev.1 起，同一 PI 内递增）',
    `status`              tinyint(2)    NOT NULL DEFAULT 1  COMMENT '版本状态（1-编辑中、2-已发送、3-已放弃）',
    `buyer_json`          text          NULL                COMMENT '买方快照 JSON（名称、地址、国家、税号、联系人、电话、邮箱）',
    `consignee_json`      text          NULL                COMMENT '收货人快照 JSON，可为空',
    `buyer_party_id`      bigint        NULL                COMMENT '买方来源单证主体ID，关联customer_party.id；手填时为空',
    `consignee_party_id`  bigint        NULL                COMMENT '收货人来源单证主体ID，关联customer_party.id；手填时为空',
    `delivery_time`       varchar(100)  NOT NULL DEFAULT '' COMMENT '交期（文字，如 3-5 days after payment）',
    `payment_term`        varchar(200)  NOT NULL DEFAULT '' COMMENT '付款条件（文字，如 T/T 100% in advance）',
    `incoterm`            varchar(16)   NOT NULL DEFAULT '' COMMENT '贸易术语',
    `incoterm_place`      varchar(64)   NOT NULL DEFAULT '' COMMENT '术语地点',
    `port_of_shipment`    varchar(64)   NOT NULL DEFAULT '' COMMENT '起运港',
    `remark`              varchar(500)  NOT NULL DEFAULT '' COMMENT '备注（显示在 PI 上）',
    `bank_account_id`     int(11)       NULL                COMMENT '收款账户ID，关联tenant_bank_account.id',
    `bank_account_json`   text          NULL                COMMENT '收款账户快照 JSON',
    `discount_type`       tinyint(2)    NOT NULL DEFAULT 0  COMMENT '整单折扣方式（0-无、1-按百分比、2-按金额）',
    `discount_value`      decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '折扣输入值（百分比时为百分数，金额时为原币金额）',
    `discount_amount`     decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '折扣金额（原币，正数，显示为负数行）',
    `discount_amount_cny` decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '折扣金额（CNY）',
    `item_amount`         decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '型号小计合计（原币）',
    `fee_amount`          decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '费用合计（原币）',
    `total_amount`        decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '合计 = 型号小计 + 费用 − 折扣（原币）',
    `total_amount_cny`    decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '合计（CNY）',
    `net_profit`          decimal(18,2) NULL                COMMENT '合计净利润（原币，折扣后，不含费用）',
    `net_profit_cny`      decimal(18,2) NULL                COMMENT '合计净利润（CNY）',
    `margin_rate`         decimal(5,2)  NULL                COMMENT '合计毛利率（%，折扣后）',
    `sent_at`             datetime      NULL                COMMENT '该版本首次发送时间',
    `deleted_at`          datetime      NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`         datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`           varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`         datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`           varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_pi_version` (`pi_id`, `version_no`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_pi_id` (`pi_id`),
    KEY `idx_bank_account_id` (`bank_account_id`),
    KEY `idx_status` (`status`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='PI 版本快照：每个版本保存完整内容，发送后只读';

CREATE TABLE `proforma_invoice_item` (
    `id`                  bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`           int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID',
    `pi_id`               bigint        NOT NULL DEFAULT 0  COMMENT 'PI ID，关联proforma_invoice.id',
    `version_id`          bigint        NOT NULL DEFAULT 0  COMMENT '版本ID，关联proforma_invoice_version.id',
    `line_no`             int(11)       NOT NULL DEFAULT 0  COMMENT '行号',
    `quotation_id`        bigint        NOT NULL DEFAULT 0  COMMENT '来源报价单ID，关联quotation.id',
    `quotation_item_id`   bigint        NOT NULL DEFAULT 0  COMMENT '来源报价行ID，关联quotation_item.id',
    `customer_inquiry_id` bigint        NOT NULL DEFAULT 0  COMMENT '来源客户询盘ID，关联customer_inquiry.id',
    `inquiry_item_id`     bigint        NOT NULL DEFAULT 0  COMMENT '来源询盘型号ID，关联inquiry_item.id',
    `model`               varchar(128)  NOT NULL DEFAULT '' COMMENT '型号快照',
    `brand`               varchar(64)   NOT NULL DEFAULT '' COMMENT '品牌快照',
    `category`            varchar(32)   NOT NULL DEFAULT '' COMMENT '品类快照',
    `description`         varchar(300)  NOT NULL DEFAULT '' COMMENT '描述（英文）',
    `item_condition`      tinyint(2)    NOT NULL DEFAULT 0  COMMENT '货况（字典 inquiry_item_condition 码值，0-未填）',
    `lead_time`           tinyint(2)    NOT NULL DEFAULT 0  COMMENT '货期（字典 inquiry_lead_time 码值，0-未填）',
    `warranty`            varchar(32)   NOT NULL DEFAULT '1 year' COMMENT '质保',
    `quantity`            int(11)       NOT NULL DEFAULT 1  COMMENT '数量（正整数）',
    `quoted_price`        decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '来源报价单的售价（原币），用于改价提示',
    `unit_price`          decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '单价（原币）',
    `unit_price_cny`      decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '单价（CNY）',
    `amount`              decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '小计（原币）= 单价 × 数量',
    `amount_cny`          decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '小计（CNY）',
    `cost_price`          decimal(18,2) NULL                COMMENT '采购成本价快照（CNY），无货时为空',
    `floor_margin`        decimal(5,2)  NULL                COMMENT '红线快照（%）',
    `margin_rate`         decimal(5,2)  NULL                COMMENT '毛利率（%）',
    `net_profit`          decimal(18,2) NULL                COMMENT '净利润（原币，未分摊折扣）',
    `net_profit_cny`      decimal(18,2) NULL                COMMENT '净利润（CNY，未分摊折扣）',
    `hs_code`             varchar(16)   NOT NULL DEFAULT '' COMMENT 'HS 编码（默认取商品主数据）',
    `origin_country`      varchar(64)   NOT NULL DEFAULT '' COMMENT '原产国（默认取商品主数据）',
    `remark`              varchar(300)  NOT NULL DEFAULT '' COMMENT '备注（显示在 PI 上）',
    `deleted_at`          datetime      NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`         datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`           varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`         datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`           varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_pi_id` (`pi_id`),
    KEY `idx_version_id` (`version_id`),
    KEY `idx_quotation_id` (`quotation_id`),
    KEY `idx_quotation_item_id` (`quotation_item_id`),
    KEY `idx_customer_inquiry_id` (`customer_inquiry_id`),
    KEY `idx_inquiry_item_id` (`inquiry_item_id`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='PI 型号行（挂在版本上）';

CREATE TABLE `proforma_invoice_fee` (
    `id`            bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`     int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID',
    `pi_id`         bigint        NOT NULL DEFAULT 0  COMMENT 'PI ID，关联proforma_invoice.id',
    `version_id`    bigint        NOT NULL DEFAULT 0  COMMENT '版本ID，关联proforma_invoice_version.id',
    `fee_name`      varchar(64)   NOT NULL DEFAULT '' COMMENT '费用名称（如 Shipping Cost、Bank Charge）',
    `amount`        decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '金额（原币）',
    `amount_cny`    decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '金额（CNY）',
    `remark`        varchar(300)  NOT NULL DEFAULT '' COMMENT '备注',
    `sort_order`    int(11)       NOT NULL DEFAULT 0  COMMENT '排序',
    `deleted_at`    datetime      NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`   datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`     varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`   datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`     varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_pi_id` (`pi_id`),
    KEY `idx_version_id` (`version_id`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='PI 费用行（挂在版本上，不计入毛利率与净利润）';

CREATE TABLE `proforma_invoice_send_log` (
    `id`            bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`     int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID',
    `pi_id`         bigint        NOT NULL DEFAULT 0  COMMENT 'PI ID，关联proforma_invoice.id',
    `version_no`    int(11)       NOT NULL DEFAULT 1  COMMENT '发送的版本号',
    `channel`       tinyint(2)    NOT NULL DEFAULT 3  COMMENT '发送方式（2-Excel、3-PDF、4-图片）',
    `sent_by`       bigint        NOT NULL DEFAULT 0  COMMENT '操作人用户ID',
    `sent_at`       datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
    `deleted_at`    datetime      NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`   datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`     varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`   datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`     varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_pi_id` (`pi_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='PI 发送记录';

-- ---------------------------------------------------------------- 收款登记
CREATE TABLE `payment_receipt` (
    `id`              bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`       int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID',
    `pi_id`           bigint        NOT NULL DEFAULT 0  COMMENT 'PI ID，关联proforma_invoice.id（收款记在 PI 上）',
    `kind`            tinyint(2)    NOT NULL DEFAULT 1  COMMENT '类型（1-付款水单、2-到账）',
    `currency_code`   char(3)       NOT NULL DEFAULT 'USD' COMMENT '币种（同 PI）',
    `amount`          decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '金额（原币）：水单为客户付款金额，到账为实际到账金额',
    `exchange_rate`   decimal(18,6) NOT NULL DEFAULT 1.000000 COMMENT '到账时的系统汇率快照（水单沿用 PI 汇率）',
    `amount_cny`      decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '金额（CNY）',
    `fee_diff`        decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '记为银行中转手续费的差额（原币，仅到账）',
    `receipt_date`    date          NULL                COMMENT '付款日期（水单）/ 到账日期（到账）',
    `bank_account_id` int(11)       NULL                COMMENT '收款账户ID（到账），关联tenant_bank_account.id',
    `slip_id`         bigint        NULL                COMMENT '到账对应的水单ID，关联payment_receipt.id',
    `file_keys`       varchar(1000) NOT NULL DEFAULT '[]' COMMENT '水单附件 JSON 数组（私有存储 key 与文件名），最多 5 个',
    `note`            varchar(300)  NOT NULL DEFAULT '' COMMENT '说明',
    `status`          tinyint(2)    NOT NULL DEFAULT 1  COMMENT '状态（1-有效、2-已作废）',
    `void_reason`     varchar(200)  NOT NULL DEFAULT '' COMMENT '作废原因',
    `operator_id`     bigint        NOT NULL DEFAULT 0  COMMENT '登记人用户ID',
    `deleted_at`      datetime      NULL                COMMENT '软删除时间（水单删除），NULL表示未删除',
    `create_time`     datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`       varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`     datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`       varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_pi_id` (`pi_id`),
    KEY `idx_slip_id` (`slip_id`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收款登记：业务员上传水单、财务登记到账';

-- ---------------------------------------------------------------- 销售订单
CREATE TABLE `sales_order` (
    `id`                  bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`           int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID',
    `so_no`               varchar(24)   NOT NULL DEFAULT '' COMMENT '订单编号（内部单据不带前缀，如 SO20261008001）',
    `pi_id`               bigint        NOT NULL DEFAULT 0  COMMENT '来源 PI ID，关联proforma_invoice.id',
    `pi_version_no`       int(11)       NOT NULL DEFAULT 1  COMMENT '转订单时 PI 的版本号',
    `customer_id`         bigint        NOT NULL DEFAULT 0  COMMENT '客户ID，关联customer.id',
    `owner_id`            bigint        NOT NULL DEFAULT 0  COMMENT '业务员用户ID，关联user_basic.id',
    `currency_code`       char(3)       NOT NULL DEFAULT 'USD' COMMENT '币种',
    `exchange_rate`       decimal(18,6) NOT NULL DEFAULT 1.000000 COMMENT '汇率（同 PI）',
    `buyer_json`          text          NULL                COMMENT '买方快照 JSON',
    `consignee_json`      text          NULL                COMMENT '收货人快照 JSON',
    `delivery_time`       varchar(100)  NOT NULL DEFAULT '' COMMENT '交期',
    `payment_term`        varchar(200)  NOT NULL DEFAULT '' COMMENT '付款条件',
    `incoterm`            varchar(16)   NOT NULL DEFAULT '' COMMENT '贸易术语',
    `incoterm_place`      varchar(64)   NOT NULL DEFAULT '' COMMENT '术语地点',
    `port_of_shipment`    varchar(64)   NOT NULL DEFAULT '' COMMENT '起运港',
    `remark`              varchar(500)  NOT NULL DEFAULT '' COMMENT '备注',
    `discount_amount`     decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '折扣金额（原币）',
    `discount_amount_cny` decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '折扣金额（CNY）',
    `item_amount`         decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '型号小计合计（原币）',
    `fee_amount`          decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '费用合计（原币）',
    `total_amount`        decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '合计（原币）',
    `total_amount_cny`    decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '合计（CNY）',
    `net_profit`          decimal(18,2) NULL                COMMENT '合计净利润（原币，折扣后）',
    `net_profit_cny`      decimal(18,2) NULL                COMMENT '合计净利润（CNY）',
    `margin_rate`         decimal(5,2)  NULL                COMMENT '合计毛利率（%，折扣后）',
    `status`              tinyint(2)    NOT NULL DEFAULT 1  COMMENT '状态（1-有效、2-已取消）',
    `cancel_reason`       varchar(200)  NOT NULL DEFAULT '' COMMENT '取消原因',
    `cancelled_by`        bigint        NULL                COMMENT '取消人用户ID',
    `cancelled_at`        datetime      NULL                COMMENT '取消时间',
    `deleted_at`          datetime      NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`         datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`           varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`         datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`           varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_so_no` (`tenant_id`, `so_no`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_pi_id` (`pi_id`),
    KEY `idx_customer_id` (`customer_id`),
    KEY `idx_owner_id` (`owner_id`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB AUTO_INCREMENT=10000 DEFAULT CHARSET=utf8mb4 COMMENT='销售订单：由 PI 转成，创建后不可修改，只能取消';

CREATE TABLE `sales_order_item` (
    `id`                  bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`           int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID',
    `so_id`               bigint        NOT NULL DEFAULT 0  COMMENT '订单ID，关联sales_order.id',
    `line_no`             int(11)       NOT NULL DEFAULT 0  COMMENT '行号',
    `pi_item_id`          bigint        NOT NULL DEFAULT 0  COMMENT '来源 PI 行ID，关联proforma_invoice_item.id',
    `quotation_id`        bigint        NOT NULL DEFAULT 0  COMMENT '来源报价单ID，关联quotation.id',
    `quotation_item_id`   bigint        NOT NULL DEFAULT 0  COMMENT '来源报价行ID，关联quotation_item.id',
    `customer_inquiry_id` bigint        NOT NULL DEFAULT 0  COMMENT '来源客户询盘ID，关联customer_inquiry.id',
    `inquiry_item_id`     bigint        NOT NULL DEFAULT 0  COMMENT '来源询盘型号ID，关联inquiry_item.id',
    `model`               varchar(128)  NOT NULL DEFAULT '' COMMENT '型号',
    `brand`               varchar(64)   NOT NULL DEFAULT '' COMMENT '品牌',
    `category`            varchar(32)   NOT NULL DEFAULT '' COMMENT '品类',
    `description`         varchar(300)  NOT NULL DEFAULT '' COMMENT '描述',
    `item_condition`      tinyint(2)    NOT NULL DEFAULT 0  COMMENT '货况（字典 inquiry_item_condition 码值）',
    `lead_time`           tinyint(2)    NOT NULL DEFAULT 0  COMMENT '货期（字典 inquiry_lead_time 码值）',
    `warranty`            varchar(32)   NOT NULL DEFAULT '' COMMENT '质保',
    `quantity`            int(11)       NOT NULL DEFAULT 1  COMMENT '数量',
    `unit_price`          decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '单价（原币）',
    `amount`              decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '小计（原币）',
    `amount_cny`          decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '小计（CNY）',
    `cost_price`          decimal(18,2) NULL                COMMENT '采购成本价快照（CNY）',
    `margin_rate`         decimal(5,2)  NULL                COMMENT '毛利率（%）',
    `net_profit`          decimal(18,2) NULL                COMMENT '净利润（原币）',
    `net_profit_cny`      decimal(18,2) NULL                COMMENT '净利润（CNY）',
    `hs_code`             varchar(16)   NOT NULL DEFAULT '' COMMENT 'HS 编码',
    `origin_country`      varchar(64)   NOT NULL DEFAULT '' COMMENT '原产国',
    `remark`              varchar(300)  NOT NULL DEFAULT '' COMMENT '备注',
    `deleted_at`          datetime      NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`         datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`           varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`         datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`           varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_so_id` (`so_id`),
    KEY `idx_pi_item_id` (`pi_item_id`),
    KEY `idx_quotation_id` (`quotation_id`),
    KEY `idx_quotation_item_id` (`quotation_item_id`),
    KEY `idx_customer_inquiry_id` (`customer_inquiry_id`),
    KEY `idx_inquiry_item_id` (`inquiry_item_id`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售订单型号行';

CREATE TABLE `sales_order_fee` (
    `id`            bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`     int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID',
    `so_id`         bigint        NOT NULL DEFAULT 0  COMMENT '订单ID，关联sales_order.id',
    `fee_name`      varchar(64)   NOT NULL DEFAULT '' COMMENT '费用名称',
    `amount`        decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '金额（原币）',
    `amount_cny`    decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '金额（CNY）',
    `remark`        varchar(300)  NOT NULL DEFAULT '' COMMENT '备注',
    `sort_order`    int(11)       NOT NULL DEFAULT 0  COMMENT '排序',
    `deleted_at`    datetime      NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`   datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`     varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`   datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`     varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_so_id` (`so_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售订单费用行';

-- ---------------------------------------------------------------- 收款账户
CREATE TABLE `tenant_bank_account` (
    `id`             int(11)       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`      int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID',
    `currency_code`  char(3)       NOT NULL DEFAULT 'USD' COMMENT '币种（USD、EUR、GBP、JPY、CNY）',
    `bank_name`      varchar(128)  NOT NULL DEFAULT '' COMMENT '银行名称',
    `account_name`   varchar(128)  NOT NULL DEFAULT '' COMMENT '账户名称',
    `account_no`     varchar(64)   NOT NULL DEFAULT '' COMMENT '账号（列表与日志中脱敏显示）',
    `swift_code`     varchar(16)   NOT NULL DEFAULT '' COMMENT 'SWIFT Code',
    `country`        varchar(64)   NOT NULL DEFAULT '' COMMENT '银行所在国家 / 地区',
    `bank_address`   varchar(300)  NOT NULL DEFAULT '' COMMENT '银行地址',
    `bank_code`      varchar(32)   NOT NULL DEFAULT '' COMMENT 'Bank Code',
    `branch_code`    varchar(32)   NOT NULL DEFAULT '' COMMENT 'Branch Code',
    `remark`         varchar(200)  NOT NULL DEFAULT '' COMMENT '备注',
    `is_default`     tinyint(1)    NOT NULL DEFAULT 0  COMMENT '是否该币种的默认账户（0-否、1-是）',
    `status`         tinyint(2)    NOT NULL DEFAULT 1  COMMENT '状态（0-停用、1-启用）',
    `create_time`    datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`      varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`    datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`      varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_tenant_currency` (`tenant_id`, `currency_code`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=1000 DEFAULT CHARSET=utf8mb4 COMMENT='收款银行账户（按租户，每币种一个默认）';

-- ---------------------------------------------------------------- 单据编号流水
CREATE TABLE `document_sequence` (
    `id`           int(11)       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`    int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID',
    `doc_type`     varchar(4)    NOT NULL DEFAULT '' COMMENT '单据类型代码（IQ、QT、PI、SO、PO、SH、CI、PL、DN、CN、ST）',
    `biz_date`     date          NOT NULL COMMENT '业务日期（编号中的年月日）',
    `last_no`      int(11)       NOT NULL DEFAULT 0  COMMENT '当日已用的最大流水号',
    `create_time`  datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`    varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`  datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`    varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_type_date` (`tenant_id`, `doc_type`, `biz_date`),
    KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='单据编号当日流水（按租户 / 类型 / 日期原子递增）';

-- 报价单改用编号服务：以已有报价单当日最大流水初始化，避免切换当天撞号
INSERT INTO `document_sequence` (`tenant_id`, `doc_type`, `biz_date`, `last_no`)
SELECT q.`tenant_id`, 'QT', DATE(q.`create_time`), MAX(CAST(SUBSTRING(q.`quotation_no`, -3) AS UNSIGNED))
FROM `quotation` q
WHERE q.`quotation_no` <> ''
GROUP BY q.`tenant_id`, DATE(q.`create_time`);

-- ---------------------------------------------------------------- 报价单：部分成交、行成交标记
ALTER TABLE `quotation`
    MODIFY COLUMN `status` tinyint(2) NOT NULL DEFAULT 1 COMMENT '状态（1-草稿、2-已发送、3-已成交、4-未成交、5-已作废、6-部分成交）；成交与部分成交由销售订单推进',
    MODIFY COLUMN `quotation_no` varchar(24) NOT NULL DEFAULT '' COMMENT '报价单编号（租户前缀 + QT + 年月日 + 当日流水，如 FWQT20261004001）';
ALTER TABLE `quotation_item`
    ADD COLUMN `won` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否已进入有效销售订单（0-否、1-是）' AFTER `net_profit_cny`;

-- ---------------------------------------------------------------- 系统配置
INSERT INTO `sys_config` (`tenant_id`, `config_key`, `config_name`, `config_value`, `config_type`, `is_builtin`, `config_group`, `remark`, `create_by`, `update_by`)
SELECT 0, k.config_key, k.config_name, k.config_value, k.config_type, 1, k.config_group, k.remark, 'sys', 'sys'
FROM (
    SELECT 'document.number-prefix' AS config_key, '单据编号前缀' AS config_name, '' AS config_value, 'STRING' AS config_type, 'document' AS config_group,
           '2–4 位大写字母，只加在对外单据（报价单、PI、CI、PL、采购单、借项 / 贷项通知单、对账单）上，可为空' AS remark
    UNION ALL SELECT 'sales.receipt.fee-tolerance', '到账差额可记为手续费的上限', '50', 'INTEGER', 'sales',
           '登记到账时，剩余金额不超过该值（PI 币种）可勾选「差额记为银行中转手续费」'
) k
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` c WHERE c.config_key = k.config_key AND c.tenant_id = 0);

-- ---------------------------------------------------------------- 菜单与按钮权限
INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`)
SELECT v.* FROM (
    SELECT 100077 AS id, 0 AS pid, 'RS1100077' AS code, '销售管理' AS name, 1 AS type, 5 AS sort, 'accountBook' AS li, '' AS lsi, '' AS di, '' AS dsi,
           '/sales' AS path, '' AS permission, 1 AS status, '' AS micro_app, 'sys' AS cb, 'sys' AS ub
    UNION ALL SELECT 100078, 100077, 'RS2100078', 'PI',       2, 1, 'fileDone',  '', '', '', '/sales/pi',          '', 1, '', 'sys', 'sys'
    UNION ALL SELECT 100079, 100077, 'RS2100079', '销售订单', 2, 2, 'container', '', '', '', '/sales/orders',      '', 1, '', 'sys', 'sys'
    UNION ALL SELECT 100080, 100002, 'RS2100080', '收款账户', 2, 9, 'bank',      '', '', '', '/system/bank-account', '', 1, '', 'sys', 'sys'
    UNION ALL SELECT 110179, 100078, 'RS3110179', '上传付款水单', 3, 1, '', '', '', '', '', 'sales:pi:receipt-slip', 1, '', 'sys', 'sys'
    UNION ALL SELECT 110180, 100078, 'RS3110180', '登记到账',     3, 2, '', '', '', '', '', 'sales:pi:receipt-confirm', 1, '', 'sys', 'sys'
    UNION ALL SELECT 110181, 100080, 'RS3110181', '管理收款账户', 3, 1, '', '', '', '', '', 'system:bank-account:edit', 1, '', 'sys', 'sys'
) v
WHERE NOT EXISTS (SELECT 1 FROM `resource` r WHERE r.`id` = v.id);

-- 客户、供应商顺延到销售管理之后；系统设置与日志顺延到收款账户之后
UPDATE `resource` SET `sort` = 6,  `update_time` = NOW(), `update_by` = 'sys' WHERE `id` = 100006;
UPDATE `resource` SET `sort` = 7,  `update_time` = NOW(), `update_by` = 'sys' WHERE `id` = 100008;
UPDATE `resource` SET `sort` = 10, `update_time` = NOW(), `update_by` = 'sys' WHERE `id` = 100017;
UPDATE `resource` SET `sort` = 11, `update_time` = NOW(), `update_by` = 'sys' WHERE `id` = 100018;
UPDATE `resource` SET `sort` = 12, `update_time` = NOW(), `update_by` = 'sys' WHERE `id` = 100019;

UPDATE `tenant_package`
SET `menu_ids` = JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(
        `menu_ids`, '$', 100077), '$', 100078), '$', 100079), '$', 100080), '$', 110179), '$', 110180), '$', 110181)
WHERE `name` IN ('标准版', '旗舰版') AND NOT JSON_CONTAINS(`menu_ids`, '100077');

-- 内置「租户管理员」获得全部新菜单与按钮（含登记到账；财务角色由租户自行授权）
INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT r.`code`, s.`id`, s.`code`
FROM `role` r
JOIN `resource` s ON s.`id` IN (100077, 100078, 100079, 100080, 110179, 110180, 110181)
WHERE r.`code` = 'ROLE_ADMIN' AND r.`is_built_in` = 1
  AND NOT EXISTS (SELECT 1 FROM `role_resource` x WHERE x.`role_code` = r.`code` AND x.`resource_id` = s.`id`);

-- 能看报价单的角色（业务员）获得「销售管理 → PI / 销售订单」与「上传付款水单」；不含登记到账
INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT rr.`role_code`, s.`id`, s.`code`
FROM `role_resource` rr
JOIN `resource` s ON s.`id` IN (100077, 100078, 100079, 110179)
WHERE rr.`resource_id` = 100073
  AND NOT EXISTS (SELECT 1 FROM `role_resource` x WHERE x.`role_code` = rr.`role_code` AND x.`resource_id` = s.`id`);

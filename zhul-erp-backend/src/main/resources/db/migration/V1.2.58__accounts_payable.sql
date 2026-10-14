-- 应付与付款（openspec add-accounts-payable，供应链单据模型第③期 ③a）：
-- 应付单（采购单、货代对账单各一张）、付款计划、付款记录（含供应商退款）、核销明细；付款截图用通用附件表（归属类型 PAYMENT）。
-- 菜单「财务管理 → 应付管理、付款记录」，按钮「付款登记」「垫付登记」；存量应付由启动任务补齐（Java 计算分期与到期日）。

CREATE TABLE IF NOT EXISTS `fin_payable` (
    `id`             bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`      int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID',
    `payable_no`     varchar(20)   NOT NULL DEFAULT '' COMMENT '应付单编号（AP + 年月日 + 流水）',
    `payee_type`     tinyint(2)    NOT NULL DEFAULT 1  COMMENT '收款方类型（1-供应商、2-线上店铺、3-货代）',
    `supplier_id`    bigint        NULL                COMMENT '供应商或货代ID（线上店铺为空）',
    `shop_platform`  varchar(32)   NOT NULL DEFAULT '' COMMENT '线上店铺平台',
    `shop_name`      varchar(128)  NOT NULL DEFAULT '' COMMENT '线上店铺名称',
    `payee_name`     varchar(128)  NOT NULL DEFAULT '' COMMENT '收款方显示名称（冗余，便于搜索）',
    `source_type`    tinyint(2)    NOT NULL DEFAULT 1  COMMENT '来源类型（1-采购单、2-货代对账单）',
    `source_id`      bigint        NOT NULL            COMMENT '来源单据ID',
    `source_no`      varchar(40)   NOT NULL DEFAULT '' COMMENT '来源单据编号（冗余）',
    `currency_code`  varchar(3)    NOT NULL DEFAULT 'CNY' COMMENT '币种',
    `amount`         decimal(18,2) NOT NULL DEFAULT 0  COMMENT '应付金额（原币）',
    `exchange_rate`  decimal(18,6) NOT NULL DEFAULT 1  COMMENT '应付汇率（来源单据汇率）',
    `amount_cny`     decimal(18,2) NOT NULL DEFAULT 0  COMMENT '应付金额（人民币，HALF_UP）',
    `paid_amount`    decimal(18,2) NOT NULL DEFAULT 0  COMMENT '已付金额（原币，含欠款抵扣转入）',
    `paid_cny`       decimal(18,2) NOT NULL DEFAULT 0  COMMENT '已付人民币（实付）',
    `credit_amount`  decimal(18,2) NOT NULL DEFAULT 0  COMMENT '供应商欠款（原币，已付超过应付的部分减去抵扣出去与退款）',
    `status`         tinyint(2)    NOT NULL DEFAULT 1  COMMENT '状态（1-未付、2-部分付款、3-已付清、4-已关闭）',
    `next_due_date`  date          NULL                COMMENT '最早一期有未付金额的到期日（待入库为空）',
    `deleted_at`     datetime      NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`    datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`      varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`    datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`      varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_payable_source` (`tenant_id`, `source_type`, `source_id`),
    KEY `idx_payable_tenant` (`tenant_id`),
    KEY `idx_payable_supplier` (`supplier_id`),
    KEY `idx_payable_status` (`status`),
    KEY `idx_payable_due` (`next_due_date`),
    KEY `idx_payable_create` (`create_time`),
    KEY `idx_payable_deleted` (`deleted_at`)
) ENGINE = InnoDB AUTO_INCREMENT = 10000 DEFAULT CHARSET = utf8mb4 COMMENT = '应付单（一张来源单据一张，不删除，取消时为已关闭）';

CREATE TABLE IF NOT EXISTS `fin_payable_installment` (
    `id`            bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`     int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID',
    `payable_id`    bigint        NOT NULL            COMMENT '应付单ID',
    `seq`           int           NOT NULL DEFAULT 1  COMMENT '期次（从 1 开始）',
    `ratio`         decimal(9,4)  NOT NULL DEFAULT 0  COMMENT '比例（百分比，如 30.0000）',
    `trigger_type`  tinyint(2)    NOT NULL DEFAULT 1  COMMENT '到期时点（1-下单后、2-发货前、3-入库后（offset_days 为入库后天数）、5-对账确认）',
    `offset_days`   int           NOT NULL DEFAULT 0  COMMENT '入库后 N 天的 N',
    `amount`        decimal(18,2) NOT NULL DEFAULT 0  COMMENT '本期金额（原币）',
    `due_date`      date          NULL                COMMENT '到期日（NULL 表示待入库）',
    `paid_amount`   decimal(18,2) NOT NULL DEFAULT 0  COMMENT '本期已付（由应付单已付按期次先后分配）',
    `status`        tinyint(2)    NOT NULL DEFAULT 1  COMMENT '状态（1-未付、2-部分付款、3-已付清）',
    `deleted_at`    datetime      NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`   datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`     varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`   datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`     varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_installment_tenant` (`tenant_id`),
    KEY `idx_installment_payable` (`payable_id`),
    KEY `idx_installment_status` (`status`),
    KEY `idx_installment_deleted` (`deleted_at`)
) ENGINE = InnoDB AUTO_INCREMENT = 10000 DEFAULT CHARSET = utf8mb4 COMMENT = '应付单付款计划（按付款条件分期，重算时整体替换）';

CREATE TABLE IF NOT EXISTS `fin_payment` (
    `id`               bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`        int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID',
    `payment_no`       varchar(20)   NOT NULL DEFAULT '' COMMENT '付款记录编号（PY + 年月日 + 流水）',
    `kind`             tinyint(2)    NOT NULL DEFAULT 1  COMMENT '类型（1-付款、2-供应商退款）',
    `payee_type`       tinyint(2)    NOT NULL DEFAULT 1  COMMENT '收款方类型（1-供应商、2-线上店铺、3-货代）',
    `supplier_id`      bigint        NULL                COMMENT '供应商或货代ID',
    `shop_platform`    varchar(32)   NOT NULL DEFAULT '' COMMENT '线上店铺平台',
    `shop_name`        varchar(128)  NOT NULL DEFAULT '' COMMENT '线上店铺名称',
    `payee_name`       varchar(128)  NOT NULL DEFAULT '' COMMENT '收款方显示名称',
    `method`           tinyint(2)    NOT NULL DEFAULT 1  COMMENT '付款方式（1-公司对公、2-员工垫付）；退款固定 1',
    `bank_account_id`  bigint        NULL                COMMENT '公司付款 / 收款账户ID（公司对公、退款）',
    `advance_user`     varchar(32)   NOT NULL DEFAULT '' COMMENT '垫付人账号（员工垫付）',
    `pay_date`         date          NOT NULL            COMMENT '付款日期 / 退款日期',
    `currency_code`    varchar(3)    NOT NULL DEFAULT 'CNY' COMMENT '币种',
    `amount`           decimal(18,2) NOT NULL DEFAULT 0  COMMENT '付款金额（原币，不含欠款抵扣）',
    `amount_cny`       decimal(18,2) NOT NULL DEFAULT 0  COMMENT '实付人民币',
    `actual_rate`      decimal(18,6) NOT NULL DEFAULT 1  COMMENT '实际汇率（实付人民币 ÷ 付款金额）',
    `credit_used`      decimal(18,2) NOT NULL DEFAULT 0  COMMENT '本笔使用的欠款抵扣（原币）',
    `remark`           varchar(500)  NOT NULL DEFAULT '' COMMENT '说明',
    `reimburse_status` tinyint(2)    NOT NULL DEFAULT 0  COMMENT '报销状态（0-不适用、1-待报销、2-已进报销单）；员工垫付登记时为 1',
    `idempotency_key`  varchar(64)   NOT NULL DEFAULT '' COMMENT '幂等键（同一次登记重复提交只生成一笔）',
    `void_at`          datetime      NULL                COMMENT '作废时间，NULL表示有效',
    `void_by`          varchar(32)   NOT NULL DEFAULT '' COMMENT '作废人',
    `void_reason`      varchar(255)  NOT NULL DEFAULT '' COMMENT '作废原因',
    `deleted_at`       datetime      NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（登记时间）',
    `create_by`        varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人（登记人）',
    `update_time`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`        varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_payment_idem` (`tenant_id`, `idempotency_key`),
    KEY `idx_payment_tenant` (`tenant_id`),
    KEY `idx_payment_supplier` (`supplier_id`),
    KEY `idx_payment_date` (`pay_date`),
    KEY `idx_payment_advance` (`advance_user`, `reimburse_status`),
    KEY `idx_payment_create` (`create_time`),
    KEY `idx_payment_deleted` (`deleted_at`)
) ENGINE = InnoDB AUTO_INCREMENT = 10000 DEFAULT CHARSET = utf8mb4 COMMENT = '付款记录（含供应商退款）；作废后保留';

CREATE TABLE IF NOT EXISTS `fin_payment_allocation` (
    `id`                 bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`          int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID',
    `payment_id`         bigint        NOT NULL            COMMENT '付款记录ID',
    `payable_id`         bigint        NOT NULL            COMMENT '核销到的应付单ID（退款为被冲减欠款的应付单）',
    `amount`             decimal(18,2) NOT NULL DEFAULT 0  COMMENT '核销金额（原币）',
    `amount_cny`         decimal(18,2) NOT NULL DEFAULT 0  COMMENT '核销人民币（按付款实际汇率；欠款抵扣为 0）',
    `from_credit`        tinyint(2)    NOT NULL DEFAULT 0  COMMENT '来源（0-实付、1-欠款抵扣、2-退款冲减欠款）',
    `credit_payable_id`  bigint        NULL                COMMENT '欠款抵扣的来源应付单ID',
    `deleted_at`         datetime      NULL                COMMENT '软删除时间，NULL表示未删除（付款作废时标记）',
    `create_time`        datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`          varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`        datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`          varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_allocation_tenant` (`tenant_id`),
    KEY `idx_allocation_payment` (`payment_id`),
    KEY `idx_allocation_payable` (`payable_id`),
    KEY `idx_allocation_credit` (`credit_payable_id`),
    KEY `idx_allocation_deleted` (`deleted_at`)
) ENGINE = InnoDB AUTO_INCREMENT = 10000 DEFAULT CHARSET = utf8mb4 COMMENT = '付款核销明细（含欠款抵扣与退款冲减）';

-- 菜单与按钮
INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`)
SELECT v.* FROM (
              SELECT 100103 AS id, 100083 AS pid, 'RS2100103' AS code, '应付管理' AS name, 2 AS type, 2 AS sort, 'account-book' AS li, '' AS lsi, '' AS di, '' AS dsi,
                     '/finance/payables' AS path, '' AS permission, 1 AS status, '' AS micro_app, 'sys' AS cb, 'sys' AS ub
    UNION ALL SELECT 100104, 100083, 'RS2100104', '付款记录', 2, 3, 'transaction', '', '', '', '/finance/payments', '', 1, '', 'sys', 'sys'
    UNION ALL SELECT 110204, 100103, 'RS3110204', '付款登记', 3, 1, '', '', '', '', '', 'finance:payment:register', 1, '', 'sys', 'sys'
    UNION ALL SELECT 110205, 100088, 'RS3110205', '垫付登记', 3, 9, '', '', '', '', '', 'finance:payment:advance', 1, '', 'sys', 'sys'
) v
WHERE NOT EXISTS (SELECT 1 FROM `resource` r WHERE r.`id` = v.id);

-- 套餐：含收款管理的补应付管理、付款记录、付款登记；含采购单的补垫付登记
UPDATE `tenant_package`
SET `menu_ids` = JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(`menu_ids`, '$', 100103), '$', 100104), '$', 110204)
WHERE JSON_CONTAINS(`menu_ids`, '100085') AND NOT JSON_CONTAINS(`menu_ids`, '100103');

UPDATE `tenant_package`
SET `menu_ids` = JSON_ARRAY_APPEND(`menu_ids`, '$', 110205)
WHERE JSON_CONTAINS(`menu_ids`, '100088') AND NOT JSON_CONTAINS(`menu_ids`, '110205');

-- 角色：有收款管理菜单的得到应付管理、付款记录、付款登记；有采购单菜单的得到垫付登记
INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT x.`role_code`, s.`id`, s.`code`
FROM `role_resource` x
JOIN `resource` s ON s.`id` IN (100103, 100104, 110204)
WHERE x.`resource_id` = 100085
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = x.`role_code` AND y.`resource_id` = s.`id`);

INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT x.`role_code`, s.`id`, s.`code`
FROM `role_resource` x
JOIN `resource` s ON s.`id` = 110205
WHERE x.`resource_id` = 100088
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = x.`role_code` AND y.`resource_id` = s.`id`);

-- 内置租户管理员：全部
INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT r.`code`, s.`id`, s.`code`
FROM `role` r
JOIN `resource` s ON s.`id` IN (100103, 100104, 110204, 110205)
WHERE r.`code` = 'ROLE_ADMIN' AND r.`is_built_in` = 1
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = r.`code` AND y.`resource_id` = s.`id`);

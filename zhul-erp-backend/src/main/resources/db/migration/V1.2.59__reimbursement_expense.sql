-- 报销单与费用单（openspec add-reimbursement-expense，供应链单据模型第③期 ③b）：
-- 报销单（BX）与明细（待报销项按来源实时汇总，明细记来源，release_seq 防重复报销）；费用单（FE）与订单型号分摊；
-- 到货差异退货运费加垫付人；菜单「财务管理 → 报销单、费用单」与按钮「报销付款」「费用单维护」。
-- fin_payable：source_type 增加 3-费用单，payee_type 增加 4-其他（只有名称）；fin_payment：kind 增加 3-报销付款，payee_type 增加 5-员工。

CREATE TABLE IF NOT EXISTS `fin_reimbursement` (
    `id`               bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`        int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID',
    `reimburse_no`     varchar(20)   NOT NULL DEFAULT '' COMMENT '报销单编号（BX + 年月日 + 流水）',
    `applicant_id`     bigint        NOT NULL            COMMENT '申请人用户ID',
    `applicant_name`   varchar(64)   NOT NULL DEFAULT '' COMMENT '申请人姓名（冗余）',
    `submitted_at`     datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
    `line_count`       int           NOT NULL DEFAULT 0  COMMENT '项数',
    `total_amount`     decimal(18,2) NOT NULL DEFAULT 0  COMMENT '合计（人民币，退款为负数项）',
    `status`           tinyint(2)    NOT NULL DEFAULT 1  COMMENT '状态（1-待付款、2-已报销、3-已撤回）',
    `withdraw_reason`  varchar(255)  NOT NULL DEFAULT '' COMMENT '撤回原因',
    `pay_account_id`   bigint        NULL                COMMENT '付款账户ID（公司账户）',
    `pay_date`         date          NULL                COMMENT '付款日期',
    `paid_by`          varchar(32)   NOT NULL DEFAULT '' COMMENT '登记已付的人',
    `paid_at`          datetime      NULL                COMMENT '登记已付时间',
    `pay_remark`       varchar(500)  NOT NULL DEFAULT '' COMMENT '付款说明',
    `payment_id`       bigint        NULL                COMMENT '报销付款对应的付款记录ID',
    `deleted_at`       datetime      NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`        varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`        varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_reimburse_tenant` (`tenant_id`),
    KEY `idx_reimburse_applicant` (`applicant_id`),
    KEY `idx_reimburse_status` (`status`),
    KEY `idx_reimburse_create` (`create_time`),
    KEY `idx_reimburse_deleted` (`deleted_at`)
) ENGINE = InnoDB AUTO_INCREMENT = 10000 DEFAULT CHARSET = utf8mb4 COMMENT = '报销单（员工提交，暂不审核；财务登记已付）';

CREATE TABLE IF NOT EXISTS `fin_reimbursement_line` (
    `id`               bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`        int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID',
    `reimbursement_id` bigint        NOT NULL            COMMENT '报销单ID',
    `source_type`      tinyint(2)    NOT NULL            COMMENT '来源（1-采购货款垫付、2-国内快递运费、3-退货运费、4-费用单、5-收到退款）',
    `source_id`        bigint        NOT NULL            COMMENT '来源记录ID',
    `source_no`        varchar(40)   NOT NULL DEFAULT '' COMMENT '来源单据编号（冗余）',
    `occur_date`       date          NULL                COMMENT '发生日期',
    `description`      varchar(255)  NOT NULL DEFAULT '' COMMENT '说明',
    `amount`           decimal(18,2) NOT NULL DEFAULT 0  COMMENT '金额（人民币，收到退款为负数）',
    `release_seq`      bigint        NOT NULL DEFAULT 0  COMMENT '0 表示有效；报销单撤回时改为本行ID，释放唯一位',
    `deleted_at`       datetime      NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`        varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`        varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_reimburse_source` (`tenant_id`, `source_type`, `source_id`, `release_seq`),
    KEY `idx_reimburse_line_tenant` (`tenant_id`),
    KEY `idx_reimburse_line_parent` (`reimbursement_id`),
    KEY `idx_reimburse_line_deleted` (`deleted_at`)
) ENGINE = InnoDB AUTO_INCREMENT = 10000 DEFAULT CHARSET = utf8mb4 COMMENT = '报销明细（一个来源同时只能在一张有效报销单里）';

CREATE TABLE IF NOT EXISTS `fin_expense` (
    `id`             bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`      int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID',
    `expense_no`     varchar(20)   NOT NULL DEFAULT '' COMMENT '费用单编号（FE + 年月日 + 流水）',
    `name`           varchar(64)   NOT NULL DEFAULT '' COMMENT '名称（如补发运费）',
    `occur_date`     date          NOT NULL            COMMENT '发生日期',
    `payee_type`     tinyint(2)    NOT NULL DEFAULT 4  COMMENT '收款方类型（1-供应商、2-线上店铺、3-货代、4-其他）',
    `supplier_id`    bigint        NULL                COMMENT '供应商或货代ID',
    `shop_platform`  varchar(32)   NOT NULL DEFAULT '' COMMENT '线上店铺平台',
    `shop_name`      varchar(128)  NOT NULL DEFAULT '' COMMENT '线上店铺名称',
    `payee_name`     varchar(128)  NOT NULL DEFAULT '' COMMENT '收款方名称',
    `currency_code`  varchar(3)    NOT NULL DEFAULT 'CNY' COMMENT '币种',
    `amount`         decimal(18,2) NOT NULL DEFAULT 0  COMMENT '金额（原币）',
    `exchange_rate`  decimal(18,6) NOT NULL DEFAULT 1  COMMENT '汇率（系统汇率）',
    `amount_cny`     decimal(18,2) NOT NULL DEFAULT 0  COMMENT '人民币金额（HALF_UP）',
    `method`         tinyint(2)    NOT NULL DEFAULT 1  COMMENT '付款方式（1-公司对公、2-员工垫付）',
    `advance_user_id` bigint       NULL                COMMENT '垫付人用户ID（员工垫付）',
    `remark`         varchar(500)  NOT NULL DEFAULT '' COMMENT '说明',
    `status`         tinyint(2)    NOT NULL DEFAULT 1  COMMENT '状态（1-有效、2-已作废）',
    `void_reason`    varchar(255)  NOT NULL DEFAULT '' COMMENT '作废原因',
    `deleted_at`     datetime      NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`    datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`      varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`    datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`      varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_expense_tenant` (`tenant_id`),
    KEY `idx_expense_supplier` (`supplier_id`),
    KEY `idx_expense_advance` (`advance_user_id`),
    KEY `idx_expense_status` (`status`),
    KEY `idx_expense_create` (`create_time`),
    KEY `idx_expense_deleted` (`deleted_at`)
) ENGINE = InnoDB AUTO_INCREMENT = 10000 DEFAULT CHARSET = utf8mb4 COMMENT = '费用单（偶发成本；公司对公生成应付，员工垫付进报销）';

CREATE TABLE IF NOT EXISTS `fin_expense_allocation` (
    `id`           bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`    int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID',
    `expense_id`   bigint        NOT NULL            COMMENT '费用单ID',
    `so_id`        bigint        NOT NULL            COMMENT '销售订单ID',
    `so_item_id`   bigint        NOT NULL            COMMENT '销售订单型号行ID',
    `amount`       decimal(18,2) NOT NULL DEFAULT 0  COMMENT '分摊金额（原币）',
    `amount_cny`   decimal(18,2) NOT NULL DEFAULT 0  COMMENT '分摊人民币（尾差放最后一行）',
    `deleted_at`   datetime      NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`  datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`    varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`  datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`    varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_expense_alloc_tenant` (`tenant_id`),
    KEY `idx_expense_alloc_expense` (`expense_id`),
    KEY `idx_expense_alloc_so` (`so_id`),
    KEY `idx_expense_alloc_item` (`so_item_id`),
    KEY `idx_expense_alloc_deleted` (`deleted_at`)
) ENGINE = InnoDB AUTO_INCREMENT = 10000 DEFAULT CHARSET = utf8mb4 COMMENT = '费用单分摊到销售订单型号行（③c 计算实际成本）';

ALTER TABLE `receiving_discrepancy`
    ADD COLUMN `return_payer_id` bigint NULL COMMENT '退货运费垫付人用户ID（进入待报销）' AFTER `return_freight`;

UPDATE `receiving_discrepancy`
SET `return_payer_id` = `handled_by`
WHERE `return_freight` > 0 AND `return_payer_id` IS NULL AND `handled_by` IS NOT NULL;

-- 菜单与按钮
INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`)
SELECT v.* FROM (
              SELECT 100105 AS id, 100083 AS pid, 'RS2100105' AS code, '报销单' AS name, 2 AS type, 4 AS sort, 'file-done' AS li, '' AS lsi, '' AS di, '' AS dsi,
                     '/finance/reimbursements' AS path, '' AS permission, 1 AS status, '' AS micro_app, 'sys' AS cb, 'sys' AS ub
    UNION ALL SELECT 100106, 100083, 'RS2100106', '费用单', 2, 5, 'profile', '', '', '', '/finance/expenses', '', 1, '', 'sys', 'sys'
    UNION ALL SELECT 110206, 100105, 'RS3110206', '报销付款', 3, 1, '', '', '', '', '', 'finance:reimburse:pay', 1, '', 'sys', 'sys'
    UNION ALL SELECT 110207, 100106, 'RS3110207', '费用单维护', 3, 1, '', '', '', '', '', 'finance:expense:edit', 1, '', 'sys', 'sys'
) v
WHERE NOT EXISTS (SELECT 1 FROM `resource` r WHERE r.`id` = v.id);

-- 套餐：含采购单、出库打包或收款管理的补报销单；含收款管理的补报销付款、费用单与维护
UPDATE `tenant_package`
SET `menu_ids` = JSON_ARRAY_APPEND(`menu_ids`, '$', 100105)
WHERE (JSON_CONTAINS(`menu_ids`, '100088') OR JSON_CONTAINS(`menu_ids`, '100095') OR JSON_CONTAINS(`menu_ids`, '100085'))
  AND NOT JSON_CONTAINS(`menu_ids`, '100105');

UPDATE `tenant_package`
SET `menu_ids` = JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(`menu_ids`, '$', 110206), '$', 100106), '$', 110207)
WHERE JSON_CONTAINS(`menu_ids`, '100085') AND NOT JSON_CONTAINS(`menu_ids`, '100106');

-- 角色：有采购单、出库打包或收款管理菜单的得到报销单；有收款管理的得到报销付款、费用单与维护
INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT x.`role_code`, s.`id`, s.`code`
FROM `role_resource` x
JOIN `resource` s ON s.`id` = 100105
WHERE x.`resource_id` IN (100088, 100095, 100085)
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = x.`role_code` AND y.`resource_id` = s.`id`);

INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT x.`role_code`, s.`id`, s.`code`
FROM `role_resource` x
JOIN `resource` s ON s.`id` IN (110206, 100106, 110207)
WHERE x.`resource_id` = 100085
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = x.`role_code` AND y.`resource_id` = s.`id`);

-- 内置角色：仓库得到报销单；租户管理员得到全部
INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT 'ROLE_WH', s.`id`, s.`code` FROM `resource` s
WHERE s.`id` = 100105
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = 'ROLE_WH' AND y.`resource_id` = s.`id`);

INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT r.`code`, s.`id`, s.`code`
FROM `role` r
JOIN `resource` s ON s.`id` IN (100105, 100106, 110206, 110207)
WHERE r.`code` = 'ROLE_ADMIN' AND r.`is_built_in` = 1
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = r.`code` AND y.`resource_id` = s.`id`);

-- 经营统计（openspec add-operating-report）：退款单（TK）、其他收入（OI）、费用单类别与分摊月数；
-- 菜单「财务管理 → 退款单、其他收入、经营统计」与按钮「退款单维护」「其他收入维护」。

-- ---------------------------------------------------------------- 退款单

CREATE TABLE IF NOT EXISTS `fin_customer_refund` (
    `id`               bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`        int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID',
    `refund_no`        varchar(24)   NOT NULL DEFAULT '' COMMENT '退款单编号（TK + 年月日 + 流水）',
    `so_id`            bigint        NOT NULL            COMMENT '销售订单ID',
    `refund_date`      date          NOT NULL            COMMENT '退款日期',
    `reason`           varchar(32)   NOT NULL DEFAULT '' COMMENT '退款原因（字典 refund_reason 编码）',
    `currency_code`    varchar(8)    NOT NULL            COMMENT '币种（订单币种）',
    `amount`           decimal(18,2) NOT NULL            COMMENT '退款金额（原币）',
    `exchange_rate`    decimal(18,6) NOT NULL            COMMENT '汇率 = 人民币金额 ÷ 退款金额',
    `amount_cny`       decimal(18,2) NOT NULL            COMMENT '实际退出人民币',
    `method`           tinyint(2)    NOT NULL DEFAULT 1  COMMENT '退款方式（1-原路退回平台、2-银行汇款）',
    `remark`           varchar(500)  NOT NULL DEFAULT '' COMMENT '说明',
    `status`           tinyint(2)    NOT NULL DEFAULT 1  COMMENT '状态（1-有效、2-已作废）',
    `void_reason`      varchar(255)  NOT NULL DEFAULT '' COMMENT '作废原因',
    `deleted_at`       datetime      NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`        varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`        varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_refund_tenant` (`tenant_id`),
    KEY `idx_refund_so` (`so_id`),
    KEY `idx_refund_status` (`status`),
    KEY `idx_refund_date` (`refund_date`),
    KEY `idx_refund_create` (`create_time`),
    KEY `idx_refund_deleted` (`deleted_at`)
) ENGINE = InnoDB AUTO_INCREMENT = 10000 DEFAULT CHARSET = utf8mb4 COMMENT = '客户退款单（冲减订单收入，按退款月份计入经营统计）';

-- ---------------------------------------------------------------- 其他收入

CREATE TABLE IF NOT EXISTS `fin_other_income` (
    `id`               bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`        int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID',
    `income_no`        varchar(24)   NOT NULL DEFAULT '' COMMENT '编号（OI + 年月日 + 流水）',
    `income_date`      date          NOT NULL            COMMENT '日期',
    `category`         varchar(32)   NOT NULL DEFAULT '' COMMENT '类别（字典 other_income_category 编码）',
    `amount`           decimal(18,2) NOT NULL            COMMENT '金额（人民币）',
    `remark`           varchar(500)  NOT NULL DEFAULT '' COMMENT '说明',
    `status`           tinyint(2)    NOT NULL DEFAULT 1  COMMENT '状态（1-有效、2-已作废）',
    `void_reason`      varchar(255)  NOT NULL DEFAULT '' COMMENT '作废原因',
    `deleted_at`       datetime      NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`        varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`        varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_income_tenant` (`tenant_id`),
    KEY `idx_income_status` (`status`),
    KEY `idx_income_date` (`income_date`),
    KEY `idx_income_create` (`create_time`),
    KEY `idx_income_deleted` (`deleted_at`)
) ENGINE = InnoDB AUTO_INCREMENT = 10000 DEFAULT CHARSET = utf8mb4 COMMENT = '其他收入（平台返点、利息、补贴等，按月计入经营统计）';

-- ---------------------------------------------------------------- 费用单类别与分摊

ALTER TABLE `fin_expense`
    ADD COLUMN `category`        varchar(32) NOT NULL DEFAULT 'OTHER' COMMENT '费用类别（字典 expense_category 编码；关联订单的默认 ORDER）' AFTER `name`,
    ADD COLUMN `amortize_months` int         NOT NULL DEFAULT 1       COMMENT '分摊月数（1–36，从发生日期所在月起平均分摊）' AFTER `category`;

UPDATE `fin_expense` e
SET e.`category` = CASE WHEN EXISTS (SELECT 1 FROM `fin_expense_allocation` a WHERE a.`expense_id` = e.`id` AND a.`deleted_at` IS NULL)
                        THEN 'ORDER' ELSE 'OTHER' END;

-- ---------------------------------------------------------------- 字典

INSERT INTO `dict_type` (`tenant_id`, `dict_type`, `dict_name`, `is_builtin`, `status`, `remark`, `create_by`, `update_by`)
SELECT 0, v.t, v.n, 1, 1, v.r, 'sys', 'sys'
FROM (
              SELECT 'refund_reason' AS t, '退款原因' AS n, '退款单的退款原因' AS r
    UNION ALL SELECT 'other_income_category', '其他收入类别', '其他收入的类别，按月计入经营统计'
    UNION ALL SELECT 'expense_category', '费用类别', '费用单类别；不关联订单的按类别计入经营统计的运营成本，ORDER 为订单费用'
) v
WHERE NOT EXISTS (SELECT 1 FROM `dict_type` d WHERE d.`dict_type` = v.t AND d.`tenant_id` = 0);

INSERT INTO `dict_item` (`tenant_id`, `dict_type_id`, `dict_type`, `item_code`, `item_name`, `item_name_en`, `item_value`, `sort_order`, `is_default`, `status`, `remark`, `create_by`, `update_by`)
SELECT 0, t.`id`, t.`dict_type`, v.code, v.name, '', v.code, v.s, v.d, 1, '', 'sys', 'sys'
FROM `dict_type` t
JOIN (
              SELECT 'refund_reason' AS dt, 'QUALITY' AS code, '质量问题' AS name, 1 AS s, 1 AS d
    UNION ALL SELECT 'refund_reason', 'SHORT',     '少发',         2, 0
    UNION ALL SELECT 'refund_reason', 'CANCEL',    '取消部分型号', 3, 0
    UNION ALL SELECT 'refund_reason', 'OTHER',     '其他',         4, 0
    UNION ALL SELECT 'other_income_category', 'REBATE',  '平台返点', 1, 1
    UNION ALL SELECT 'other_income_category', 'INTEREST', '利息',    2, 0
    UNION ALL SELECT 'other_income_category', 'SUBSIDY', '政府补贴', 3, 0
    UNION ALL SELECT 'other_income_category', 'OTHER',   '其他',     4, 0
    UNION ALL SELECT 'expense_category', 'ORDER',     '订单费用', 1, 0
    UNION ALL SELECT 'expense_category', 'RENT',      '房租',     2, 0
    UNION ALL SELECT 'expense_category', 'UTILITY',   '水电',     3, 0
    UNION ALL SELECT 'expense_category', 'SALARY',    '人员工资', 4, 0
    UNION ALL SELECT 'expense_category', 'PLATFORM',  '平台费',   5, 0
    UNION ALL SELECT 'expense_category', 'MARKETING', '营销费',   6, 0
    UNION ALL SELECT 'expense_category', 'CRM',       'CRM 系统', 7, 0
    UNION ALL SELECT 'expense_category', 'OTHER',     '其他支出', 8, 1
) v ON v.dt = t.`dict_type`
WHERE t.`tenant_id` = 0
  AND NOT EXISTS (SELECT 1 FROM `dict_item` i WHERE i.`dict_type_id` = t.`id` AND i.`item_code` = v.code);

-- ---------------------------------------------------------------- 菜单与按钮

INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`)
SELECT v.* FROM (
              SELECT 100110 AS id, 100083 AS pid, 'RS2100110' AS code, '退款单' AS name, 2 AS type, 2 AS sort, 'rollback' AS li, '' AS lsi, '' AS di, '' AS dsi,
                     '/finance/refunds' AS path, '' AS permission, 1 AS status, '' AS micro_app, 'sys' AS cb, 'sys' AS ub
    UNION ALL SELECT 100111, 100083, 'RS2100111', '其他收入', 2, 8, 'money-collect', '', '', '', '/finance/other-incomes', '', 1, '', 'sys', 'sys'
    UNION ALL SELECT 100112, 100083, 'RS2100112', '经营统计', 2, 10, 'fund', '', '', '', '/finance/operating-report', '', 1, '', 'sys', 'sys'
    UNION ALL SELECT 110209, 100110, 'RS3110209', '退款单维护', 3, 1, '', '', '', '', '', 'finance:refund:edit', 1, '', 'sys', 'sys'
    UNION ALL SELECT 110210, 100111, 'RS3110210', '其他收入维护', 3, 1, '', '', '', '', '', 'finance:income:edit', 1, '', 'sys', 'sys'
) v
WHERE NOT EXISTS (SELECT 1 FROM `resource` r WHERE r.`id` = v.id);

-- 财务管理排序：收款单、退款单、结汇单、应付单、付款单、报销单、费用单、其他收入、利润统计、经营统计
UPDATE `resource` SET `sort` = CASE `id`
    WHEN 100085 THEN 1 WHEN 100110 THEN 2 WHEN 100107 THEN 3 WHEN 100103 THEN 4 WHEN 100104 THEN 5
    WHEN 100105 THEN 6 WHEN 100106 THEN 7 WHEN 100111 THEN 8 WHEN 100109 THEN 9 WHEN 100112 THEN 10 END,
    `update_time` = NOW()
WHERE `id` IN (100085, 100110, 100107, 100103, 100104, 100105, 100106, 100111, 100109, 100112);

-- 套餐：含收款单的补三个菜单与两个按钮
UPDATE `tenant_package`
SET `menu_ids` = JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(`menu_ids`,
        '$', 100110), '$', 100111), '$', 100112), '$', 110209), '$', 110210)
WHERE JSON_CONTAINS(`menu_ids`, '100085') AND NOT JSON_CONTAINS(`menu_ids`, '100110');

-- 角色：有收款单菜单的得到三个菜单；有「登记到账」按钮的得到两个维护按钮；租户管理员全部
INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT x.`role_code`, s.`id`, s.`code`
FROM `role_resource` x
JOIN `resource` s ON s.`id` IN (100110, 100111, 100112)
WHERE x.`resource_id` = 100085
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = x.`role_code` AND y.`resource_id` = s.`id`);

INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT x.`role_code`, s.`id`, s.`code`
FROM `role_resource` x
JOIN `resource` s ON s.`id` IN (110209, 110210)
WHERE x.`resource_id` = 110180
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = x.`role_code` AND y.`resource_id` = s.`id`);

INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT r.`code`, s.`id`, s.`code`
FROM `role` r
JOIN `resource` s ON s.`id` IN (100110, 100111, 100112, 110209, 110210)
WHERE r.`code` = 'ROLE_ADMIN' AND r.`is_built_in` = 1
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = r.`code` AND y.`resource_id` = s.`id`);

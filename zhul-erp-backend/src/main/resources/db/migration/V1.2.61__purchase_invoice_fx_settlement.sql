-- 采购订单开票与结汇单（openspec purchase-invoice-and-fx-settlement）：
-- 采购订单「含税 + 税率」改为「开票 + 税点」，开票成本计入合计与应付，砍价按 单价 × 汇率 比较；
-- 结汇单（JH）按到账日期先进先出分摊到外币到账，未结汇部分按最近一次结汇汇率估算；菜单「财务管理 → 结汇单」与按钮「登记结汇」。
-- payment_receipt.rate_source 增加 3-估算（未结完）、4-结汇（已结完）。

-- ---------------------------------------------------------------- 采购订单开票

ALTER TABLE `purchase_order`
    ADD COLUMN `invoice_required`      tinyint(2)    NOT NULL DEFAULT 0 COMMENT '开票（0-不开票、1-开票）' AFTER `tax_rate`,
    ADD COLUMN `tax_point`             decimal(5,2)  NOT NULL DEFAULT 0 COMMENT '开票税点（百分点，0–100）' AFTER `invoice_required`,
    ADD COLUMN `invoice_amount`        decimal(18,2) NULL               COMMENT '开票金额（原币，默认等于货款合计）' AFTER `tax_point`,
    ADD COLUMN `invoice_cost`          decimal(18,2) NOT NULL DEFAULT 0 COMMENT '开票成本 = 开票金额 × 税点（原币，计入合计）' AFTER `invoice_amount`,
    ADD COLUMN `expected_invoice_date` date          NULL               COMMENT '预计开票日期' AFTER `invoice_cost`,
    ADD COLUMN `invoiced_date`         date          NULL               COMMENT '实际开票日期' AFTER `expected_invoice_date`,
    ADD COLUMN `invoice_status`        tinyint(2)    NOT NULL DEFAULT 0 COMMENT '开票状态（0-不开票、1-待开票、2-已开票）' AFTER `invoiced_date`,
    ADD KEY `idx_po_invoice_status` (`invoice_status`);

-- 旧的「含税」采购订单：改为开票、税点 0，单价保持原值
UPDATE `purchase_order`
SET `invoice_required` = 1,
    `tax_point` = 0,
    `invoice_amount` = `item_amount`,
    `invoice_cost` = 0,
    `invoice_status` = 1
WHERE `tax_included` = 1;

-- 砍价改按 单价 × 汇率：重算行的人民币单价与砍价，再汇总到单头
UPDATE `purchase_order_item` i
JOIN `purchase_order` p ON p.`id` = i.`po_id`
SET i.`net_price_cny` = ROUND(i.`unit_price` * p.`exchange_rate`, 2),
    i.`bargain_amount` = CASE
        WHEN i.`target_price` IS NULL THEN NULL
        ELSE ROUND((i.`target_price` - i.`unit_price` * p.`exchange_rate`) * i.`quantity`, 2) END
WHERE i.`unit_price` IS NOT NULL;

UPDATE `purchase_order` p
LEFT JOIN (
    SELECT `po_id`,
           SUM(`bargain_amount`) AS b,
           SUM(CASE WHEN `bargain_amount` IS NOT NULL THEN ROUND(`target_price` * `quantity`, 2) ELSE 0 END) AS t
    FROM `purchase_order_item`
    WHERE `deleted_at` IS NULL
    GROUP BY `po_id`
) x ON x.`po_id` = p.`id`
SET p.`bargain_amount` = COALESCE(x.b, 0),
    p.`target_amount` = COALESCE(x.t, 0);

-- ---------------------------------------------------------------- 结汇单

CREATE TABLE IF NOT EXISTS `fin_fx_settlement` (
    `id`               bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`        int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID',
    `settle_no`        varchar(24)   NOT NULL DEFAULT '' COMMENT '结汇单编号（JH + 年月日 + 流水；迁移生成的为 MIG + 到账ID）',
    `currency_code`    varchar(8)    NOT NULL            COMMENT '外币币种',
    `settle_date`      date          NOT NULL            COMMENT '结汇日期',
    `amount`           decimal(18,2) NOT NULL            COMMENT '结汇金额（外币）',
    `amount_cny`       decimal(18,2) NOT NULL            COMMENT '实际入账人民币',
    `exchange_rate`    decimal(18,6) NOT NULL            COMMENT '结汇汇率 = 入账人民币 ÷ 结汇金额',
    `bank_account_id`  bigint        NULL                COMMENT '入账账户ID（人民币收款账户）',
    `remark`           varchar(500)  NOT NULL DEFAULT '' COMMENT '说明',
    `migrated`         tinyint(2)    NOT NULL DEFAULT 0  COMMENT '迁移生成（0-否、1-是；迁移生成的不参与先进先出与估算汇率）',
    `status`           tinyint(2)    NOT NULL DEFAULT 1  COMMENT '状态（1-有效、2-已作废）',
    `void_reason`      varchar(255)  NOT NULL DEFAULT '' COMMENT '作废原因',
    `voided_by`        varchar(32)   NOT NULL DEFAULT '' COMMENT '作废人',
    `voided_at`        datetime      NULL                COMMENT '作废时间',
    `idempotency_key`  varchar(64)   NULL                COMMENT '幂等键（同一次登记重复提交只生成一张）',
    `deleted_at`       datetime      NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`        varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`        varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_fx_idem` (`tenant_id`, `idempotency_key`),
    KEY `idx_fx_tenant` (`tenant_id`),
    KEY `idx_fx_currency` (`currency_code`),
    KEY `idx_fx_status` (`status`),
    KEY `idx_fx_create` (`create_time`),
    KEY `idx_fx_deleted` (`deleted_at`)
) ENGINE = InnoDB AUTO_INCREMENT = 10000 DEFAULT CHARSET = utf8mb4 COMMENT = '结汇单（外币到账攒批结汇，先进先出分摊）';

CREATE TABLE IF NOT EXISTS `fin_fx_settlement_allocation` (
    `id`               bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`        int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID',
    `settlement_id`    bigint        NOT NULL            COMMENT '结汇单ID',
    `receipt_id`       bigint        NOT NULL            COMMENT '到账记录ID（payment_receipt）',
    `amount`           decimal(18,2) NOT NULL            COMMENT '分摊的外币金额',
    `amount_cny`       decimal(18,2) NOT NULL            COMMENT '分摊的人民币金额（尾差放最后一笔）',
    `sort_order`       int           NOT NULL DEFAULT 0  COMMENT '先进先出顺序',
    `deleted_at`       datetime      NULL                COMMENT '软删除时间（结汇单作废时标记），NULL表示有效',
    `create_time`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`        varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`        varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_fxa_tenant` (`tenant_id`),
    KEY `idx_fxa_settlement` (`settlement_id`),
    KEY `idx_fxa_receipt` (`receipt_id`),
    KEY `idx_fxa_create` (`create_time`),
    KEY `idx_fxa_deleted` (`deleted_at`)
) ENGINE = InnoDB AUTO_INCREMENT = 10000 DEFAULT CHARSET = utf8mb4 COMMENT = '结汇分摊（结汇单 → 到账）';

ALTER TABLE `payment_receipt`
    ADD COLUMN `settled_amount` decimal(18,2) NOT NULL DEFAULT 0 COMMENT '已结汇金额（原币，实收口径）' AFTER `rate_source`,
    ADD COLUMN `settle_status`  tinyint(2)    NOT NULL DEFAULT 0 COMMENT '结汇状态（0-不适用：人民币或水单、1-未结汇、2-部分结汇、3-已结汇）' AFTER `settled_amount`,
    ADD KEY `idx_receipt_settle` (`tenant_id`, `currency_code`, `settle_status`);

-- 存量外币到账：填过实际入账人民币的视为已结汇（每笔一张迁移结汇单，汇率为原汇率），其余为未结汇、按估算记
INSERT INTO `fin_fx_settlement` (`tenant_id`, `settle_no`, `currency_code`, `settle_date`, `amount`, `amount_cny`, `exchange_rate`,
                                 `remark`, `migrated`, `status`, `create_by`, `update_by`)
SELECT r.`tenant_id`, CONCAT('MIG', r.`id`), r.`currency_code`, r.`receipt_date`, r.`net_amount`, r.`net_amount_cny`, r.`exchange_rate`,
       '登记到账时已填实际入账人民币，迁移生成', 1, 1, 'sys', 'sys'
FROM `payment_receipt` r
WHERE r.`kind` = 2 AND r.`status` = 1 AND r.`deleted_at` IS NULL AND r.`currency_code` <> 'CNY' AND r.`rate_source` = 2
  AND r.`net_amount` > 0;

INSERT INTO `fin_fx_settlement_allocation` (`tenant_id`, `settlement_id`, `receipt_id`, `amount`, `amount_cny`, `sort_order`)
SELECT s.`tenant_id`, s.`id`, r.`id`, r.`net_amount`, r.`net_amount_cny`, 1
FROM `fin_fx_settlement` s
JOIN `payment_receipt` r ON s.`settle_no` = CONCAT('MIG', r.`id`) AND s.`tenant_id` = r.`tenant_id`
WHERE s.`migrated` = 1;

UPDATE `payment_receipt`
SET `settled_amount` = `net_amount`, `settle_status` = 3, `rate_source` = 4
WHERE `kind` = 2 AND `status` = 1 AND `deleted_at` IS NULL AND `currency_code` <> 'CNY' AND `rate_source` = 2;

UPDATE `payment_receipt`
SET `settle_status` = 1, `rate_source` = 3
WHERE `kind` = 2 AND `deleted_at` IS NULL AND `currency_code` <> 'CNY' AND `settle_status` = 0;

-- ---------------------------------------------------------------- 菜单与按钮

UPDATE `resource` SET `sort` = `sort` + 1, `update_time` = NOW()
WHERE `pid` = 100083 AND `id` IN (100103, 100104, 100105, 100106);

INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`)
SELECT v.* FROM (
              SELECT 100107 AS id, 100083 AS pid, 'RS2100107' AS code, '结汇单' AS name, 2 AS type, 2 AS sort, 'swap' AS li, '' AS lsi, '' AS di, '' AS dsi,
                     '/finance/fx-settlements' AS path, '' AS permission, 1 AS status, '' AS micro_app, 'sys' AS cb, 'sys' AS ub
    UNION ALL SELECT 110208, 100107, 'RS3110208', '登记结汇', 3, 1, '', '', '', '', '', 'finance:fx:settle', 1, '', 'sys', 'sys'
) v
WHERE NOT EXISTS (SELECT 1 FROM `resource` r WHERE r.`id` = v.id);

-- 套餐：含收款单的补结汇单与登记结汇
UPDATE `tenant_package`
SET `menu_ids` = JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(`menu_ids`, '$', 100107), '$', 110208)
WHERE JSON_CONTAINS(`menu_ids`, '100085') AND NOT JSON_CONTAINS(`menu_ids`, '100107');

-- 角色：有收款单菜单的得到结汇单；有「登记到账」按钮的得到登记结汇
INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT x.`role_code`, s.`id`, s.`code`
FROM `role_resource` x
JOIN `resource` s ON s.`id` = 100107
WHERE x.`resource_id` = 100085
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = x.`role_code` AND y.`resource_id` = s.`id`);

INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT x.`role_code`, s.`id`, s.`code`
FROM `role_resource` x
JOIN `resource` s ON s.`id` = 110208
WHERE x.`resource_id` = 110180
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = x.`role_code` AND y.`resource_id` = s.`id`);

INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT r.`code`, s.`id`, s.`code`
FROM `role` r
JOIN `resource` s ON s.`id` IN (100107, 110208)
WHERE r.`code` = 'ROLE_ADMIN' AND r.`is_built_in` = 1
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = r.`code` AND y.`resource_id` = s.`id`);

-- 业务凭证（openspec inventory-vouchers，spec finance/business-voucher）：
-- 库存月结结账时按单据生成凭证，导出给会计导入畅捷通 T+；科目设置只存会计改过的业务项，没改过的用代码里的默认科目。

CREATE TABLE IF NOT EXISTS `fin_voucher` (
    `id`               bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`        int           NOT NULL DEFAULT 0 COMMENT '租户ID',
    `period_id`        bigint        NOT NULL COMMENT '库存月结ID，关联inv_period.id',
    `period_month`     varchar(7)    NOT NULL COMMENT '月份（YYYY-MM）',
    `category`         tinyint       NOT NULL COMMENT '类别（1-采购入库、2-销售成本结转、3-其他出库、4-成本调整）',
    `source_doc_type`  varchar(16)   NOT NULL DEFAULT '' COMMENT '来源单据类型（GR-采购入库单、PR-采购退货单、SH-出运单、RR-退货入库单、OO-其他出库单、CA-成本调整单）',
    `source_doc_id`    bigint        NOT NULL COMMENT '来源单据ID',
    `source_doc_no`    varchar(32)   NOT NULL DEFAULT '' COMMENT '来源单号',
    `biz_type`         varchar(32)   NOT NULL DEFAULT '' COMMENT '库存流水业务类型（如 PURCHASE_IN、PURCHASE_IN_REVERSE；成本调整为 COST_ADJUST）',
    `voucher_date`     date          NOT NULL COMMENT '凭证日期（单据的业务日期，成本调整为调整日期）',
    `summary`          varchar(200)  NOT NULL DEFAULT '' COMMENT '摘要（动作 单号 往来单位）',
    `currency_code`    varchar(8)    NOT NULL DEFAULT 'CNY' COMMENT '金额币种（人民币）',
    `debit_total`      decimal(18,2) NOT NULL DEFAULT 0 COMMENT '借方合计（红字凭证为负）',
    `credit_total`     decimal(18,2) NOT NULL DEFAULT 0 COMMENT '贷方合计，等于借方合计',
    `status`           tinyint       NOT NULL DEFAULT 1 COMMENT '状态（1-有效、2-已作废：反结账时作废）',
    `deleted_at`       datetime      NULL COMMENT '软删除时间，NULL表示未删除',
    `create_time`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`        varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`        varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_period_id` (`period_id`),
    KEY `idx_period_month` (`period_month`),
    KEY `idx_status` (`status`),
    KEY `idx_source_doc` (`source_doc_type`, `source_doc_id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务凭证（一张来源单据的一次库存动作一张）';

CREATE TABLE IF NOT EXISTS `fin_voucher_entry` (
    `id`               bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`        int           NOT NULL DEFAULT 0 COMMENT '租户ID',
    `voucher_id`       bigint        NOT NULL COMMENT '凭证ID，关联fin_voucher.id',
    `line_no`          int           NOT NULL DEFAULT 0 COMMENT '行号',
    `item_key`         varchar(32)   NOT NULL COMMENT '业务项（STOCK-库存商品、COGS-主营业务成本、AP-应付账款、RETURN_IN-退货入库、OTHER_OUT:{出库类型}、ADJ_OFFSET:{触发原因}），导出时按科目设置取 T+ 科目',
    `partner_type`     tinyint       NOT NULL DEFAULT 0 COMMENT '往来单位类型（0-无、1-供应商）',
    `partner_id`       bigint        NULL COMMENT '往来单位ID（供应商为supplier.id）',
    `direction`        tinyint       NOT NULL COMMENT '方向（1-借、2-贷）',
    `currency_code`    varchar(8)    NOT NULL DEFAULT 'CNY' COMMENT '金额币种（人民币）',
    `amount`           decimal(18,2) NOT NULL DEFAULT 0 COMMENT '金额（红字为负）',
    `deleted_at`       datetime      NULL COMMENT '软删除时间，NULL表示未删除',
    `create_time`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`        varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`        varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_voucher_id` (`voucher_id`),
    KEY `idx_partner_id` (`partner_id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务凭证分录';

CREATE TABLE IF NOT EXISTS `fin_voucher_month` (
    `id`               bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`        int           NOT NULL DEFAULT 0 COMMENT '租户ID',
    `period_id`        bigint        NOT NULL COMMENT '库存月结ID，关联inv_period.id',
    `period_month`     varchar(7)    NOT NULL COMMENT '月份（YYYY-MM）',
    `generated_by`     bigint        NULL COMMENT '生成人ID（结账人或手动生成的人）',
    `generated_by_name` varchar(64)  NOT NULL DEFAULT '' COMMENT '生成人姓名',
    `generated_at`     datetime      NOT NULL COMMENT '生成时间',
    `export_count`     int           NOT NULL DEFAULT 0 COMMENT '导出次数',
    `exported_by`      bigint        NULL COMMENT '最近导出人ID',
    `exported_by_name` varchar(64)   NOT NULL DEFAULT '' COMMENT '最近导出人姓名',
    `exported_at`      datetime      NULL COMMENT '最近导出时间',
    `deleted_at`       datetime      NULL COMMENT '软删除时间（反结账作废），NULL表示有效',
    `create_time`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`        varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`        varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_period_id` (`period_id`),
    KEY `idx_period_month` (`period_month`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务凭证月份（生成与导出记录）';

CREATE TABLE IF NOT EXISTS `fin_voucher_account` (
    `id`               bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`        int           NOT NULL DEFAULT 0 COMMENT '租户ID',
    `item_key`         varchar(32)   NOT NULL COMMENT '业务项（同fin_voucher_entry.item_key）',
    `account_code`     varchar(32)   NOT NULL DEFAULT '' COMMENT 'T+ 科目编码（空表示还没设置，不能导出）',
    `account_name`     varchar(64)   NOT NULL DEFAULT '' COMMENT 'T+ 科目名称',
    `status`           tinyint       NOT NULL DEFAULT 1 COMMENT '状态（0-停用、1-启用）',
    `create_time`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`        varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`        varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_item` (`tenant_id`, `item_key`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务凭证科目设置（会计改过的业务项；没有记录的用默认科目）';

ALTER TABLE `supplier`
    ADD COLUMN `tplus_partner_code` varchar(32) NOT NULL DEFAULT '' COMMENT 'T+ 往来单位编码（业务凭证导出用；空则用供应商编码）' AFTER `supplier_code`;

-- 菜单：财务管理 → 业务凭证（库存月结之后），按钮：生成凭证与导出、科目设置
INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`)
SELECT v.* FROM (
              SELECT 100131 AS id, 100083 AS pid, 'RS2100131' AS code, '业务凭证' AS name, 2 AS type, 12 AS sort, 'book' AS li, '' AS lsi, '' AS di, '' AS dsi,
                     '/finance/vouchers' AS path, '' AS permission, 1 AS status, '' AS micro_app, 'sys' AS cb, 'sys' AS ub
    UNION ALL SELECT 110254, 100131, 'RS3110254', '生成凭证与导出', 3, 1, '', '', '', '', '', 'finance:voucher:export', 1, '', 'sys', 'sys'
    UNION ALL SELECT 110255, 100131, 'RS3110255', '科目设置',       3, 2, '', '', '', '', '', 'finance:voucher:account', 1, '', 'sys', 'sys'
) v
WHERE NOT EXISTS (SELECT 1 FROM `resource` r WHERE r.`id` = v.id);

-- 套餐：有库存月结的套餐加上业务凭证
UPDATE `tenant_package` SET `menu_ids` = JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(`menu_ids`,
        '$', 100131), '$', 110254), '$', 110255)
WHERE `menu_ids` IS NOT NULL AND JSON_VALID(`menu_ids`)
  AND JSON_CONTAINS(`menu_ids`, '100130')
  AND NOT JSON_CONTAINS(`menu_ids`, '100131');

-- 角色：有库存月结菜单的得到业务凭证与两个按钮；内置管理员都有
INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT x.`role_code`, s.`id`, s.`code`
FROM `role_resource` x
JOIN `resource` s ON s.`id` IN (100131, 110254, 110255)
WHERE x.`resource_id` = 100130
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = x.`role_code` AND y.`resource_id` = s.`id`);

INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT r.`code`, s.`id`, s.`code`
FROM `role` r
JOIN `resource` s ON s.`id` IN (100131, 110254, 110255)
WHERE r.`code` = 'ROLE_ADMIN' AND r.`is_built_in` = 1
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = r.`code` AND y.`resource_id` = s.`id`);

-- ---------------------------------------------------------------- 回退（手动执行）
-- 库存月结不受影响；已结账月份的凭证随表删除，重新上线后在页面上手动「生成凭证」。
-- 套餐 menu_ids 里追加的三个 ID 留着无害（资源已删，不会显示），重新执行本迁移时不会重复追加。
-- DELETE FROM `role_resource` WHERE `resource_id` IN (100131, 110254, 110255);
-- DELETE FROM `resource` WHERE `id` IN (100131, 110254, 110255);
-- DROP TABLE `fin_voucher_account`, `fin_voucher_month`, `fin_voucher_entry`, `fin_voucher`;
-- ALTER TABLE `supplier` DROP COLUMN `tplus_partner_code`;
-- DELETE FROM `flyway_schema_history` WHERE `version` = '1.5.58';

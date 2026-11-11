-- V1.5.25：售后退货单据化（openspec after-sales-return-docs）。
-- 售后 → 销售退货单 TH → 退货入库单 RR → 采购退货单 PR，退款申请 RA 增加「待验收」与明细；到货差异的退货类处理也生成采购退货单。
-- 1. 建表 return_receipt、return_receipt_item、purchase_return、purchase_return_item、fin_refund_request_item，相关表加列。
-- 2. 存量销售退货单拆出退货入库单（状态对照见 design.md「Migration Plan」），闲置库存的来源改指退货入库单。
-- 3. 退回供应商的去向、已处理的退货类到货差异，各生成一张已完成的采购退货单（标记为历史迁移，不冲减应付）；
--    报销明细「退货运费」与其截图改指采购退货单。
-- 4. 存量退款申请各补一行「退款」明细。
-- 5. 菜单「销售管理 → 销售退货」「采购管理 → 采购退货」、按钮「维护采购退货单」，角色与租户套餐授权。
-- 订单成本、应付、待报销的数字不变：退回供应商的冲减金额与运费仍记在去向行（由采购退货单同步写回），
-- 迁移生成的采购退货单 migrated = 1，不参与应付冲减。
-- 回退：见文件末尾注释（反向脚本，需与旧版本代码一起回退，手动执行）。

-- ---------------------------------------------------------------- 1. 表结构

ALTER TABLE `sales_return`
    MODIFY COLUMN `status` tinyint(2) NOT NULL DEFAULT 1 COMMENT '状态（1-待确认、2-待入库、3-已入库、4-已作废）',
    MODIFY COLUMN `remark` varchar(500) NOT NULL DEFAULT '' COMMENT '备注',
    MODIFY COLUMN `received_date` date NULL COMMENT '历史字段（验收改记在退货入库单），只读',
    MODIFY COLUMN `received_by` bigint NULL COMMENT '历史字段（验收改记在退货入库单），只读',
    MODIFY COLUMN `receive_note` varchar(300) NOT NULL DEFAULT '' COMMENT '历史字段（验收改记在退货入库单），只读',
    ADD COLUMN `handling` tinyint(2) NOT NULL DEFAULT 1 COMMENT '处理方式（1-仅退货、2-退货退款）' AFTER `so_id`,
    ADD COLUMN `refund_first` tinyint(1) NOT NULL DEFAULT 0 COMMENT '退货退款时是否先退款（0-否，验收后才能退款；1-是）' AFTER `handling`,
    ADD COLUMN `confirmed_at` datetime NULL COMMENT '确认退货时间' AFTER `status`,
    ADD COLUMN `confirmed_by` bigint NULL COMMENT '确认人用户ID' AFTER `confirmed_at`;

ALTER TABLE `sales_return_item`
    MODIFY COLUMN `received_qty` int(11) NULL COMMENT '历史字段（实收改记在退货入库单行），只读',
    MODIFY COLUMN `qualified_qty` int(11) NULL COMMENT '历史字段（合格改记在退货入库单行），只读';

CREATE TABLE `return_receipt` (
    `id`              bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`       int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID',
    `receipt_no`      varchar(32)   NOT NULL DEFAULT '' COMMENT '退货入库单编号（RR + 年月日 + 当日流水，如 RR20261112001）',
    `sales_return_id` bigint        NOT NULL            COMMENT '销售退货单ID，关联sales_return.id',
    `after_sales_id`  bigint        NOT NULL            COMMENT '售后单ID，关联after_sales.id',
    `so_id`           bigint        NOT NULL            COMMENT '销售订单ID，关联sales_order.id',
    `owner_id`        bigint        NOT NULL DEFAULT 0  COMMENT '负责人用户ID（处理去向的采购员；没有时为售后单负责人）',
    `status`          tinyint(2)    NOT NULL DEFAULT 1  COMMENT '状态（1-待入库、2-待处理、3-已处理、4-已作废）',
    `received_date`   date          NULL                COMMENT '收货日期',
    `received_by`     bigint        NULL                COMMENT '验收人用户ID',
    `receive_note`    varchar(300)  NOT NULL DEFAULT '' COMMENT '验收说明',
    `remark`          varchar(500)  NOT NULL DEFAULT '' COMMENT '备注',
    `void_reason`     varchar(200)  NOT NULL DEFAULT '' COMMENT '作废原因',
    `deleted_at`      datetime      NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`     datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`       varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`     datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`       varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_receipt_no` (`tenant_id`, `receipt_no`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_sales_return_id` (`sales_return_id`),
    KEY `idx_after_sales_id` (`after_sales_id`),
    KEY `idx_so_id` (`so_id`),
    KEY `idx_owner_id` (`owner_id`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退货入库单';

CREATE TABLE `return_receipt_item` (
    `id`              bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`       int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID',
    `receipt_id`      bigint        NOT NULL            COMMENT '退货入库单ID，关联return_receipt.id',
    `return_item_id`  bigint        NOT NULL            COMMENT '销售退货单行ID，关联sales_return_item.id',
    `so_item_id`      bigint        NOT NULL            COMMENT '订单型号行ID，关联sales_order_item.id',
    `model`           varchar(128)  NOT NULL DEFAULT '' COMMENT '型号',
    `brand`           varchar(64)   NOT NULL DEFAULT '' COMMENT '品牌',
    `quantity`        int(11)       NOT NULL            COMMENT '退货数量',
    `received_qty`    int(11)       NULL                COMMENT '实收数量（验收后）',
    `qualified_qty`   int(11)       NULL                COMMENT '合格数量（验收后）',
    `disposed_qty`    int(11)       NOT NULL DEFAULT 0  COMMENT '已处理去向的数量',
    `note`            varchar(300)  NOT NULL DEFAULT '' COMMENT '验收说明',
    `deleted_at`      datetime      NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`     datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`       varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`     datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`       varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_receipt_id` (`receipt_id`),
    KEY `idx_return_item_id` (`return_item_id`),
    KEY `idx_so_item_id` (`so_item_id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退货入库单行';

CREATE TABLE `purchase_return` (
    `id`              bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`       int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID',
    `return_no`       varchar(32)   NOT NULL DEFAULT '' COMMENT '采购退货单编号（PR + 年月日 + 当日流水，如 PR20261112001）',
    `supplier_id`     bigint        NOT NULL            COMMENT '供应商ID，关联supplier.id（线上店铺采购的采购订单为 0，显示采购订单的店铺）',
    `source_type`     tinyint(2)    NOT NULL            COMMENT '来源（1-售后退货、2-到货不良·退货换货、3-到货不良·退货不补、4-多发退回）',
    `po_id`           bigint        NULL                COMMENT '采购订单ID，关联purchase_order.id；售后来源可为空',
    `return_receipt_id` bigint      NULL                COMMENT '来源退货入库单ID（售后退货），关联return_receipt.id',
    `purchase_receipt_id` bigint    NULL                COMMENT '来源采购入库单ID（到货差异），关联purchase_receipt.id',
    `after_sales_id`  bigint        NULL                COMMENT '售后单ID（售后退货），关联after_sales.id',
    `so_id`           bigint        NULL                COMMENT '销售订单ID（售后退货），关联sales_order.id',
    `owner_id`        bigint        NOT NULL DEFAULT 0  COMMENT '负责人用户ID（采购员）',
    `status`          tinyint(2)    NOT NULL DEFAULT 1  COMMENT '状态（1-待寄出、2-已寄出、3-已完成、4-已作废）',
    `ship_date`       date          NULL                COMMENT '寄出日期',
    `carrier`         varchar(32)   NOT NULL DEFAULT '' COMMENT '快递公司',
    `tracking_no`     varchar(64)   NOT NULL DEFAULT '' COMMENT '快递单号',
    `freight_cny`     decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '运费（CNY，计入订单成本）',
    `payer_id`        bigint        NULL                COMMENT '运费垫付人用户ID（进入待报销）；为空表示公司支付',
    `signed_date`     date          NULL                COMMENT '供应商签收日期',
    `currency_code`   varchar(8)    NOT NULL DEFAULT 'CNY' COMMENT '退款或抵扣币种（采购订单币种，没有采购订单为 CNY）',
    `exchange_rate`   decimal(18,6) NOT NULL DEFAULT 1.000000 COMMENT '汇率（采购订单汇率，6 位小数）',
    `refund_amount`   decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '供应商退款或抵扣（原币）',
    `refund_cny`      decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '供应商退款或抵扣（CNY）= 原币 × 汇率，HALF_UP 两位',
    `settle_note`     varchar(300)  NOT NULL DEFAULT '' COMMENT '完成说明（退款或抵扣方式）',
    `remark`          varchar(500)  NOT NULL DEFAULT '' COMMENT '备注',
    `void_reason`     varchar(200)  NOT NULL DEFAULT '' COMMENT '作废原因',
    `migrated`        tinyint(1)    NOT NULL DEFAULT 0  COMMENT '是否历史迁移（0-否、1-是：迁移生成，不冲减应付）',
    `deleted_at`      datetime      NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`     datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`       varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`     datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`       varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_return_no` (`tenant_id`, `return_no`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_supplier_id` (`supplier_id`),
    KEY `idx_po_id` (`po_id`),
    KEY `idx_return_receipt_id` (`return_receipt_id`),
    KEY `idx_purchase_receipt_id` (`purchase_receipt_id`),
    KEY `idx_after_sales_id` (`after_sales_id`),
    KEY `idx_so_id` (`so_id`),
    KEY `idx_owner_id` (`owner_id`),
    KEY `idx_payer_id` (`payer_id`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购退货单';

CREATE TABLE `purchase_return_item` (
    `id`                 bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`          int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID',
    `purchase_return_id` bigint        NOT NULL            COMMENT '采购退货单ID，关联purchase_return.id',
    `receipt_item_id`    bigint        NULL                COMMENT '来源退货入库单行ID（售后退货），关联return_receipt_item.id',
    `disposition_id`     bigint        NULL                COMMENT '来源去向ID（售后退货），关联sales_return_disposition.id',
    `discrepancy_id`     bigint        NULL                COMMENT '来源到货差异ID，关联receiving_discrepancy.id',
    `so_item_id`         bigint        NULL                COMMENT '订单型号行ID（售后退货），关联sales_order_item.id',
    `po_item_id`         bigint        NULL                COMMENT '采购订单行ID，关联purchase_order_item.id',
    `model`              varchar(128)  NOT NULL DEFAULT '' COMMENT '型号',
    `brand`              varchar(64)   NOT NULL DEFAULT '' COMMENT '品牌',
    `quantity`           int(11)       NOT NULL            COMMENT '数量',
    `deleted_at`         datetime      NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`        datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`          varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`        datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`          varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_purchase_return_id` (`purchase_return_id`),
    KEY `idx_receipt_item_id` (`receipt_item_id`),
    KEY `idx_disposition_id` (`disposition_id`),
    KEY `idx_discrepancy_id` (`discrepancy_id`),
    KEY `idx_so_item_id` (`so_item_id`),
    KEY `idx_po_item_id` (`po_item_id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购退货单行';

CREATE TABLE `fin_refund_request_item` (
    `id`                   bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`            int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID',
    `request_id`           bigint        NOT NULL            COMMENT '退款申请ID，关联fin_refund_request.id',
    `line_type`            tinyint(2)    NOT NULL DEFAULT 2  COMMENT '行类型（1-退货型号、2-其他）',
    `so_item_id`           bigint        NULL                COMMENT '订单型号行ID（退货型号），关联sales_order_item.id',
    `sales_return_item_id` bigint        NULL                COMMENT '销售退货单行ID（退货型号），关联sales_return_item.id',
    `item_name`            varchar(128)  NOT NULL DEFAULT '' COMMENT '项目名称（退货型号为型号；其他如「退款」「差价补偿」）',
    `brand`                varchar(64)   NOT NULL DEFAULT '' COMMENT '品牌（退货型号）',
    `quantity`             int(11)       NULL                COMMENT '退货数量（退货型号）',
    `unit_price`           decimal(18,2) NULL                COMMENT '成交单价（订单币种，退货型号）',
    `currency_code`        varchar(8)    NOT NULL            COMMENT '币种（订单币种）',
    `amount`               decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '金额（订单币种）',
    `note`                 varchar(300)  NOT NULL DEFAULT '' COMMENT '说明',
    `deleted_at`           datetime      NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`          datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`            varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`          datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`            varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_request_id` (`request_id`),
    KEY `idx_so_item_id` (`so_item_id`),
    KEY `idx_sales_return_item_id` (`sales_return_item_id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退款申请明细';

CREATE TABLE `document_log` (
    `id`              bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`       int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID',
    `doc_type`        varchar(4)    NOT NULL            COMMENT '单据类型代码（TH-销售退货单、RR-退货入库单、PR-采购退货单、RA-退款申请）',
    `doc_id`          bigint        NOT NULL            COMMENT '单据ID',
    `action`          varchar(32)   NOT NULL DEFAULT '' COMMENT '操作（如 确认退货、验收、寄出）',
    `content`         varchar(1000) NOT NULL DEFAULT '' COMMENT '内容',
    `operator_id`     bigint        NOT NULL DEFAULT 0  COMMENT '操作人用户ID',
    `deleted_at`      datetime      NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`     datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`       varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`     datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`       varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_doc` (`doc_type`, `doc_id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='单据操作日志（详情页的操作记录）';

ALTER TABLE `sales_return_disposition`
    MODIFY COLUMN `cost_cny` decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '冲减原订单成本（CNY）：退回供应商 = 采购退货单完成后分摊的退款或抵扣（未完成为 0），转闲置 = 数量 × 原采购成本（HALF_UP 两位），报废为 0',
    MODIFY COLUMN `freight_cny` decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '退回运费（CNY，计入原订单成本）：采购退货单寄出后分摊的运费',
    MODIFY COLUMN `supplier_id` bigint NULL COMMENT '退回的供应商ID，关联supplier.id',
    MODIFY COLUMN `tracking_no` varchar(64) NOT NULL DEFAULT '' COMMENT '历史字段（快递改记在采购退货单），只读',
    MODIFY COLUMN `supplier_refund_cny` decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '历史字段（供应商退款改记在采购退货单），只读',
    ADD COLUMN `receipt_id` bigint NULL COMMENT '退货入库单ID，关联return_receipt.id' AFTER `return_item_id`,
    ADD COLUMN `receipt_item_id` bigint NULL COMMENT '退货入库单行ID，关联return_receipt_item.id' AFTER `receipt_id`,
    ADD COLUMN `purchase_return_id` bigint NULL COMMENT '退回供应商生成的采购退货单ID，关联purchase_return.id' AFTER `hold_id`,
    ADD INDEX `idx_receipt_id` (`receipt_id`),
    ADD INDEX `idx_purchase_return_id` (`purchase_return_id`);

ALTER TABLE `receiving_discrepancy`
    MODIFY COLUMN `return_carrier` varchar(32) NOT NULL DEFAULT '' COMMENT '历史字段（退货快递改记在采购退货单），只读',
    MODIFY COLUMN `return_tracking_no` varchar(64) NOT NULL DEFAULT '' COMMENT '历史字段（退货快递改记在采购退货单），只读',
    MODIFY COLUMN `return_freight` decimal(18,2) NULL COMMENT '退货运费（CNY，计入订单成本）：由采购退货单寄出时同步写入',
    MODIFY COLUMN `return_payer_id` bigint NULL COMMENT '历史字段（垫付人改记在采购退货单），只读',
    ADD COLUMN `purchase_return_id` bigint NULL COMMENT '退货类处理生成的采购退货单ID，关联purchase_return.id' AFTER `free_of_charge`,
    ADD INDEX `idx_purchase_return_id` (`purchase_return_id`);

ALTER TABLE `fin_refund_request`
    MODIFY COLUMN `status` tinyint(2) NOT NULL DEFAULT 2 COMMENT '状态（0-待验收、1-待审批、2-待财务处理、3-已执行、4-已驳回、5-已撤回）',
    ADD COLUMN `sales_return_id` bigint NULL COMMENT '来源销售退货单ID，关联sales_return.id（由退货退款生成时）' AFTER `so_id`,
    ADD COLUMN `refund_first` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否先退款（0-否：待验收，退货入库后才能执行；1-是）' AFTER `sales_return_id`,
    ADD INDEX `idx_sales_return_id` (`sales_return_id`);

ALTER TABLE `stock_hold`
    MODIFY COLUMN `return_id` bigint NOT NULL DEFAULT 0 COMMENT '来源退货入库单ID（售后退货），关联return_receipt.id';

-- ---------------------------------------------------------------- 2. 存量销售退货单 → 销售退货单 + 退货入库单

-- 处理方式：同一售后单有未撤回的退款申请的按退货退款，否则仅退货（存量申请与退货单没有直接关联，不回写 sales_return_id）
UPDATE `sales_return` r
SET r.`handling` = IF(EXISTS (SELECT 1 FROM `fin_refund_request` q
                              WHERE q.`after_sales_id` = r.`after_sales_id` AND q.`status` <> 5 AND q.`deleted_at` IS NULL), 2, 1),
    r.`refund_first` = 1,
    r.`confirmed_at` = r.`create_time`,
    r.`confirmed_by` = NULL;

INSERT INTO `return_receipt` (`tenant_id`, `receipt_no`, `sales_return_id`, `after_sales_id`, `so_id`, `owner_id`, `status`,
                              `received_date`, `received_by`, `receive_note`, `remark`, `void_reason`, `create_time`, `create_by`, `update_time`, `update_by`)
SELECT r.`tenant_id`,
       CONCAT('RR', DATE_FORMAT(r.`create_time`, '%Y%m%d'),
              LPAD(ROW_NUMBER() OVER (PARTITION BY r.`tenant_id`, DATE(r.`create_time`) ORDER BY r.`id`), 3, '0')),
       r.`id`, r.`after_sales_id`, r.`so_id`, IFNULL(a.`owner_id`, 0), r.`status`,
       r.`received_date`, r.`received_by`, r.`receive_note`, '', '', r.`create_time`, r.`create_by`, r.`update_time`, r.`update_by`
FROM `sales_return` r
LEFT JOIN `after_sales` a ON a.`id` = r.`after_sales_id`
WHERE r.`status` IN (1, 2, 3) AND r.`deleted_at` IS NULL;

INSERT INTO `document_sequence` (`tenant_id`, `doc_type`, `biz_date`, `last_no`)
SELECT x.`tenant_id`, 'RR', DATE(x.`create_time`), COUNT(*)
FROM `return_receipt` x
GROUP BY x.`tenant_id`, DATE(x.`create_time`)
ON DUPLICATE KEY UPDATE `last_no` = GREATEST(`last_no`, VALUES(`last_no`));

-- 状态：1 待入库 → 2 待入库；2 待处理、3 已处理 → 3 已入库；4 已作废不变
UPDATE `sales_return` SET `status` = CASE `status` WHEN 1 THEN 2 WHEN 2 THEN 3 WHEN 3 THEN 3 ELSE `status` END;

INSERT INTO `return_receipt_item` (`tenant_id`, `receipt_id`, `return_item_id`, `so_item_id`, `model`, `brand`, `quantity`,
                                   `received_qty`, `qualified_qty`, `disposed_qty`, `create_time`, `create_by`, `update_time`, `update_by`)
SELECT i.`tenant_id`, rr.`id`, i.`id`, i.`so_item_id`, i.`model`, i.`brand`, i.`quantity`, i.`received_qty`, i.`qualified_qty`,
       IFNULL((SELECT SUM(d.`quantity`) FROM `sales_return_disposition` d WHERE d.`return_item_id` = i.`id` AND d.`deleted_at` IS NULL), 0),
       i.`create_time`, i.`create_by`, i.`update_time`, i.`update_by`
FROM `sales_return_item` i
JOIN `return_receipt` rr ON rr.`sales_return_id` = i.`return_id`
WHERE i.`deleted_at` IS NULL;

UPDATE `sales_return_disposition` d
JOIN `return_receipt` rr ON rr.`sales_return_id` = d.`return_id`
JOIN `return_receipt_item` ri ON ri.`receipt_id` = rr.`id` AND ri.`return_item_id` = d.`return_item_id`
SET d.`receipt_id` = rr.`id`, d.`receipt_item_id` = ri.`id`;

UPDATE `stock_hold` h
JOIN `return_receipt` rr ON rr.`sales_return_id` = h.`return_id`
SET h.`return_id` = rr.`id`
WHERE h.`source_type` = 3 AND h.`return_id` > 0;

-- 销售退货单的验收照片移到退货入库单（附件类型 RETURN_RECEIPT）
UPDATE `biz_attachment` f
JOIN `return_receipt` rr ON rr.`sales_return_id` = f.`owner_id` AND rr.`tenant_id` = f.`tenant_id`
SET f.`owner_type` = 'RETURN_RECEIPT', f.`owner_id` = rr.`id`
WHERE f.`owner_type` = 'SALES_RETURN' AND f.`owner_id` > 0;

-- ---------------------------------------------------------------- 3. 存量退回供应商 → 已完成的采购退货单

CREATE TABLE `tmp_v1525_pr` (
    `seq`          int          NOT NULL AUTO_INCREMENT,
    `tenant_id`    int          NOT NULL,
    `biz_date`     date         NOT NULL,
    `kind`         tinyint      NOT NULL COMMENT '1-去向、2-到货差异',
    `src_id`       bigint       NOT NULL,
    `return_no`    varchar(32)  NOT NULL DEFAULT '',
    PRIMARY KEY (`seq`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='V1.5.25 迁移用临时表，迁移结束删除';

INSERT INTO `tmp_v1525_pr` (`tenant_id`, `biz_date`, `kind`, `src_id`)
SELECT x.`tenant_id`, x.`biz_date`, x.`kind`, x.`src_id` FROM (
    SELECT d.`tenant_id`, DATE(d.`create_time`) AS `biz_date`, 1 AS `kind`, d.`id` AS `src_id`, d.`create_time` AS `t`
    FROM `sales_return_disposition` d
    WHERE d.`disposition` = 1 AND d.`deleted_at` IS NULL
    UNION ALL
    SELECT g.`tenant_id`, DATE(IFNULL(g.`handled_at`, g.`update_time`)), 2, g.`id`, IFNULL(g.`handled_at`, g.`update_time`)
    FROM `receiving_discrepancy` g
    WHERE g.`status` = 2 AND g.`resolution` IN (3, 4, 6) AND g.`deleted_at` IS NULL
) x
ORDER BY x.`t`, x.`kind`, x.`src_id`;

UPDATE `tmp_v1525_pr` t
JOIN (SELECT `seq`, ROW_NUMBER() OVER (PARTITION BY `tenant_id`, `biz_date` ORDER BY `seq`) AS n FROM `tmp_v1525_pr`) z ON z.`seq` = t.`seq`
SET t.`return_no` = CONCAT('PR', DATE_FORMAT(t.`biz_date`, '%Y%m%d'), LPAD(z.n, 3, '0'));

INSERT INTO `document_sequence` (`tenant_id`, `doc_type`, `biz_date`, `last_no`)
SELECT `tenant_id`, 'PR', `biz_date`, COUNT(*) FROM `tmp_v1525_pr` GROUP BY `tenant_id`, `biz_date`
ON DUPLICATE KEY UPDATE `last_no` = GREATEST(`last_no`, VALUES(`last_no`));

-- 售后来源：运费、快递、供应商退款搬到采购退货单；垫付人为空（以前不进报销）；采购订单取该型号行最早的有效采购订单
INSERT INTO `purchase_return` (`tenant_id`, `return_no`, `supplier_id`, `source_type`, `po_id`, `return_receipt_id`, `purchase_receipt_id`,
                               `after_sales_id`, `so_id`, `owner_id`, `status`, `ship_date`, `carrier`, `tracking_no`, `freight_cny`, `payer_id`,
                               `signed_date`, `currency_code`, `exchange_rate`, `refund_amount`, `refund_cny`, `settle_note`, `remark`,
                               `migrated`, `create_time`, `create_by`, `update_time`, `update_by`)
SELECT d.`tenant_id`, t.`return_no`, IFNULL(d.`supplier_id`, 0), 1,
       (SELECT MIN(pi.`po_id`) FROM `purchase_order_item` pi JOIN `purchase_order` po ON po.`id` = pi.`po_id`
        WHERE pi.`so_item_id` = d.`so_item_id` AND pi.`deleted_at` IS NULL AND po.`deleted_at` IS NULL AND po.`supplier_id` = d.`supplier_id`),
       d.`receipt_id`, NULL, r.`after_sales_id`, d.`so_id`, d.`operator_id`, 3, DATE(d.`create_time`), '', d.`tracking_no`, d.`freight_cny`, NULL,
       DATE(d.`create_time`), 'CNY', 1, d.`supplier_refund_cny`, d.`supplier_refund_cny`, '历史迁移，未冲减应付', d.`remark`,
       1, d.`create_time`, d.`create_by`, d.`update_time`, d.`update_by`
FROM `tmp_v1525_pr` t
JOIN `sales_return_disposition` d ON d.`id` = t.`src_id`
JOIN `sales_return` r ON r.`id` = d.`return_id`
WHERE t.`kind` = 1;

INSERT INTO `purchase_return_item` (`tenant_id`, `purchase_return_id`, `receipt_item_id`, `disposition_id`, `so_item_id`, `po_item_id`,
                                    `model`, `brand`, `quantity`, `create_time`, `create_by`, `update_time`, `update_by`)
SELECT d.`tenant_id`, p.`id`, d.`receipt_item_id`, d.`id`, d.`so_item_id`,
       (SELECT MIN(pi.`id`) FROM `purchase_order_item` pi WHERE pi.`po_id` = p.`po_id` AND pi.`so_item_id` = d.`so_item_id` AND pi.`deleted_at` IS NULL),
       i.`model`, i.`brand`, d.`quantity`, d.`create_time`, d.`create_by`, d.`update_time`, d.`update_by`
FROM `tmp_v1525_pr` t
JOIN `sales_return_disposition` d ON d.`id` = t.`src_id`
JOIN `sales_return_item` i ON i.`id` = d.`return_item_id`
JOIN `purchase_return` p ON p.`tenant_id` = t.`tenant_id` AND p.`return_no` = t.`return_no`
WHERE t.`kind` = 1;

UPDATE `sales_return_disposition` d
JOIN `tmp_v1525_pr` t ON t.`kind` = 1 AND t.`src_id` = d.`id`
JOIN `purchase_return` p ON p.`tenant_id` = t.`tenant_id` AND p.`return_no` = t.`return_no`
SET d.`purchase_return_id` = p.`id`;

-- 到货差异来源：快递、运费、垫付人搬到采购退货单
INSERT INTO `purchase_return` (`tenant_id`, `return_no`, `supplier_id`, `source_type`, `po_id`, `return_receipt_id`, `purchase_receipt_id`,
                               `after_sales_id`, `so_id`, `owner_id`, `status`, `ship_date`, `carrier`, `tracking_no`, `freight_cny`, `payer_id`,
                               `signed_date`, `currency_code`, `exchange_rate`, `refund_amount`, `refund_cny`, `settle_note`, `remark`,
                               `migrated`, `create_time`, `create_by`, `update_time`, `update_by`)
SELECT g.`tenant_id`, t.`return_no`, IFNULL(po.`supplier_id`, 0), CASE g.`resolution` WHEN 3 THEN 2 WHEN 4 THEN 3 ELSE 4 END,
       g.`po_id`, NULL, g.`receipt_id`, NULL, NULL, g.`purchaser_id`, 3, t.`biz_date`, g.`return_carrier`, g.`return_tracking_no`,
       IFNULL(g.`return_freight`, 0), IF(IFNULL(g.`return_freight`, 0) > 0, g.`return_payer_id`, NULL), t.`biz_date`,
       IFNULL(po.`currency_code`, 'CNY'), IFNULL(po.`exchange_rate`, 1), 0, 0, '历史迁移', g.`note`,
       1, IFNULL(g.`handled_at`, g.`update_time`), g.`create_by`, g.`update_time`, g.`update_by`
FROM `tmp_v1525_pr` t
JOIN `receiving_discrepancy` g ON g.`id` = t.`src_id`
LEFT JOIN `purchase_order` po ON po.`id` = g.`po_id`
WHERE t.`kind` = 2;

INSERT INTO `purchase_return_item` (`tenant_id`, `purchase_return_id`, `discrepancy_id`, `po_item_id`, `model`, `brand`, `quantity`,
                                    `create_time`, `create_by`, `update_time`, `update_by`)
SELECT g.`tenant_id`, p.`id`, g.`id`, g.`po_item_id`, g.`model`, g.`brand`, g.`quantity`, p.`create_time`, g.`create_by`, g.`update_time`, g.`update_by`
FROM `tmp_v1525_pr` t
JOIN `receiving_discrepancy` g ON g.`id` = t.`src_id`
JOIN `purchase_return` p ON p.`tenant_id` = t.`tenant_id` AND p.`return_no` = t.`return_no`
WHERE t.`kind` = 2;

UPDATE `receiving_discrepancy` g
JOIN `tmp_v1525_pr` t ON t.`kind` = 2 AND t.`src_id` = g.`id`
JOIN `purchase_return` p ON p.`tenant_id` = t.`tenant_id` AND p.`return_no` = t.`return_no`
SET g.`purchase_return_id` = p.`id`;

-- 报销明细「退货运费」（source_type 3）与运费截图改指采购退货单
UPDATE `fin_reimbursement_line` l
JOIN `receiving_discrepancy` g ON g.`id` = l.`source_id` AND g.`tenant_id` = l.`tenant_id`
JOIN `purchase_return` p ON p.`id` = g.`purchase_return_id`
SET l.`source_id` = p.`id`, l.`source_no` = p.`return_no`
WHERE l.`source_type` = 3;

UPDATE `biz_attachment` f
JOIN `receiving_discrepancy` g ON g.`id` = f.`owner_id` AND g.`tenant_id` = f.`tenant_id`
JOIN `purchase_return` p ON p.`id` = g.`purchase_return_id`
SET f.`owner_id` = p.`id`
WHERE f.`owner_type` = 'RETURN_PROOF' AND f.`owner_id` > 0;

DROP TABLE `tmp_v1525_pr`;

-- ---------------------------------------------------------------- 4. 存量退款申请补「退款」明细

INSERT INTO `fin_refund_request_item` (`tenant_id`, `request_id`, `line_type`, `item_name`, `currency_code`, `amount`,
                                       `create_time`, `create_by`, `update_time`, `update_by`)
SELECT q.`tenant_id`, q.`id`, 2, '退款', q.`currency_code`, q.`amount`, q.`create_time`, q.`create_by`, q.`update_time`, q.`update_by`
FROM `fin_refund_request` q
WHERE NOT EXISTS (SELECT 1 FROM `fin_refund_request_item` x WHERE x.`request_id` = q.`id`);

-- ---------------------------------------------------------------- 5. 菜单、按钮与授权

INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`)
SELECT v.* FROM (
              SELECT 110231 AS id, 100081 AS pid, 'RS2110231' AS code, '销售退货' AS name, 2 AS type, 7 AS sort, 'rollback' AS li, '' AS lsi, '' AS di, '' AS dsi,
                     '/sales/returns' AS path, '' AS permission, 1 AS status, '' AS micro_app, 'sys' AS cb, 'sys' AS ub
    UNION ALL SELECT 110232, 100082, 'RS2110232', '采购退货', 2, 8, 'undo', '', '', '', '/purchase/returns', '', 1, '', 'sys', 'sys'
    UNION ALL SELECT 110233, 110232, 'RS3110233', '维护采购退货单', 3, 1, '', '', '', '', '', 'purchase:purchase-return:edit', 1, '', 'sys', 'sys'
) v
WHERE NOT EXISTS (SELECT 1 FROM `resource` r WHERE r.`id` = v.id);

-- 销售管理：…销售订单、销售退货、售后管理；采购管理：…发货跟踪、采购退货、供应商库
UPDATE `resource` SET `sort` = 8, `update_time` = NOW() WHERE `id` = 100120;
UPDATE `resource` SET `sort` = 9, `update_time` = NOW() WHERE `id` = 100062;

-- 套餐：含售后管理的补销售退货；含退货入库或采购订单的补采购退货与按钮
UPDATE `tenant_package` SET `menu_ids` = JSON_ARRAY_APPEND(`menu_ids`, '$', 110231)
WHERE JSON_CONTAINS(`menu_ids`, '100120') AND NOT JSON_CONTAINS(`menu_ids`, '110231');
UPDATE `tenant_package` SET `menu_ids` = JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(`menu_ids`, '$', 110232), '$', 110233)
WHERE (JSON_CONTAINS(`menu_ids`, '100121') OR JSON_CONTAINS(`menu_ids`, '100088')) AND NOT JSON_CONTAINS(`menu_ids`, '110232');

-- 角色：有售后管理的得到销售退货；有「退货去向处理」或「处理到货差异」按钮的得到采购退货与「维护采购退货单」；租户管理员全部
INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT x.`role_code`, 110231, 'RS2110231' FROM `role_resource` x
WHERE x.`resource_id` = 100120
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = x.`role_code` AND y.`resource_id` = 110231);

INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT x.`role_code`, s.`id`, s.`code` FROM `role_resource` x
JOIN `resource` s ON s.`id` IN (110232, 110233)
WHERE x.`resource_id` IN (110222, 110192)
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = x.`role_code` AND y.`resource_id` = s.`id`);

INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT r.`code`, s.`id`, s.`code`
FROM `role` r
JOIN `resource` s ON s.`id` IN (110231, 110232, 110233)
WHERE r.`code` = 'ROLE_ADMIN' AND r.`is_built_in` = 1
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = r.`code` AND y.`resource_id` = s.`id`);

-- ---------------------------------------------------------------- 6. 校验
-- 有不一致时下面的 SELECT … INTO 取到两行而报错（Result consisted of more than one row），迁移中止。
-- MySQL 的建表、加列不能回滚，中止后需从迁移前的备份恢复，修正后重跑。

-- 每张待入库、已入库的销售退货单都有退货入库单
SELECT 1 INTO @v1525_chk FROM (SELECT 1 AS a UNION ALL SELECT 2) t
WHERE EXISTS (SELECT 1 FROM `sales_return` r
              WHERE r.`status` IN (2, 3) AND r.`deleted_at` IS NULL
                AND NOT EXISTS (SELECT 1 FROM `return_receipt` x WHERE x.`sales_return_id` = r.`id`));

-- 每条退回供应商去向、每条已处理的退货类差异都有采购退货单
SELECT 1 INTO @v1525_chk FROM (SELECT 1 AS a UNION ALL SELECT 2) t
WHERE EXISTS (SELECT 1 FROM `sales_return_disposition` d
              WHERE d.`disposition` = 1 AND d.`deleted_at` IS NULL AND d.`purchase_return_id` IS NULL)
   OR EXISTS (SELECT 1 FROM `receiving_discrepancy` g
              WHERE g.`status` = 2 AND g.`resolution` IN (3, 4, 6) AND g.`deleted_at` IS NULL AND g.`purchase_return_id` IS NULL);

-- 报销明细「退货运费」都指向采购退货单
SELECT 1 INTO @v1525_chk FROM (SELECT 1 AS a UNION ALL SELECT 2) t
WHERE EXISTS (SELECT 1 FROM `fin_reimbursement_line` l
              WHERE l.`source_type` = 3 AND l.`deleted_at` IS NULL
                AND NOT EXISTS (SELECT 1 FROM `purchase_return` p WHERE p.`id` = l.`source_id` AND p.`tenant_id` = l.`tenant_id`));

-- 每张退款申请明细合计等于表头金额
SELECT 1 INTO @v1525_chk FROM (SELECT 1 AS a UNION ALL SELECT 2) t
WHERE EXISTS (SELECT 1 FROM `fin_refund_request` q
              WHERE q.`amount` <> (SELECT IFNULL(SUM(x.`amount`), 0) FROM `fin_refund_request_item` x
                                   WHERE x.`request_id` = q.`id` AND x.`deleted_at` IS NULL));

-- ---------------------------------------------------------------- 回退（手动执行，与旧版本代码一起回退）
-- UPDATE `sales_return` SET `status` = CASE WHEN `status` = 2 THEN 1 WHEN `status` = 3 THEN
--     IFNULL((SELECT CASE x.`status` WHEN 2 THEN 2 WHEN 3 THEN 3 ELSE 2 END FROM `return_receipt` x WHERE x.`sales_return_id` = `sales_return`.`id` LIMIT 1), 2)
--     WHEN `status` = 1 THEN 4 ELSE `status` END;
-- UPDATE `sales_return_item` i JOIN `return_receipt_item` x ON x.`return_item_id` = i.`id`
--     SET i.`received_qty` = x.`received_qty`, i.`qualified_qty` = x.`qualified_qty`;
-- UPDATE `stock_hold` h JOIN `return_receipt` x ON x.`id` = h.`return_id` SET h.`return_id` = x.`sales_return_id` WHERE h.`source_type` = 3;
-- UPDATE `biz_attachment` f JOIN `return_receipt` x ON x.`id` = f.`owner_id` SET f.`owner_type` = 'SALES_RETURN', f.`owner_id` = x.`sales_return_id`
--     WHERE f.`owner_type` = 'RETURN_RECEIPT';
-- UPDATE `fin_reimbursement_line` l JOIN `receiving_discrepancy` g ON g.`purchase_return_id` = l.`source_id` SET l.`source_id` = g.`id` WHERE l.`source_type` = 3;
-- UPDATE `biz_attachment` f JOIN `receiving_discrepancy` g ON g.`purchase_return_id` = f.`owner_id` SET f.`owner_id` = g.`id` WHERE f.`owner_type` = 'RETURN_PROOF';
-- （迁移后新建的采购退货单的运费、退款需先人工核对再回退）
-- DELETE FROM `role_resource` WHERE `resource_id` IN (110231, 110232, 110233);
-- DELETE FROM `resource` WHERE `id` IN (110231, 110232, 110233);
-- UPDATE `resource` SET `sort` = 7 WHERE `id` = 100120; UPDATE `resource` SET `sort` = 8 WHERE `id` = 100062;
-- ALTER TABLE `fin_refund_request` DROP INDEX `idx_sales_return_id`, DROP COLUMN `sales_return_id`, DROP COLUMN `refund_first`;
-- ALTER TABLE `receiving_discrepancy` DROP INDEX `idx_purchase_return_id`, DROP COLUMN `purchase_return_id`;
-- ALTER TABLE `sales_return_disposition` DROP INDEX `idx_receipt_id`, DROP INDEX `idx_purchase_return_id`,
--     DROP COLUMN `receipt_id`, DROP COLUMN `receipt_item_id`, DROP COLUMN `purchase_return_id`;
-- ALTER TABLE `sales_return` DROP COLUMN `handling`, DROP COLUMN `refund_first`, DROP COLUMN `confirmed_at`, DROP COLUMN `confirmed_by`;
-- DROP TABLE `fin_refund_request_item`; DROP TABLE `purchase_return_item`; DROP TABLE `purchase_return`;
-- DROP TABLE `return_receipt_item`; DROP TABLE `return_receipt`; DROP TABLE `document_log`;
-- DELETE FROM `document_sequence` WHERE `doc_type` IN ('RR', 'PR');

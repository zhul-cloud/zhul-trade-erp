-- 库存三期：成本（openspec inventory-costing）
-- 批次成本四项、流水业务日期与分项金额、成本调整单、库存月结与快照；财务管理增加「成本调整」「库存月结」菜单

-- ---------------------------------------------------------------- 批次成本四项
-- unit_price_cny 含义改为单位成本 = 四项之和；上线前的批次只有货款，成本口径升级任务按新口径重算并生成调整单
ALTER TABLE `inv_batch`
    MODIFY COLUMN `unit_price_cny` decimal(18,6) NOT NULL DEFAULT 0 COMMENT '单位成本（人民币）= 货款 + 开票成本 + 采购费用 + 质量索赔，HALF_UP 6 位',
    ADD COLUMN `cost_goods`   decimal(18,6) NOT NULL DEFAULT 0 COMMENT '单位成本 · 货款（原币单价 × 采购订单实际汇率，6 位）' AFTER `unit_price_cny`,
    ADD COLUMN `cost_invoice` decimal(18,6) NOT NULL DEFAULT 0 COMMENT '单位成本 · 开票成本（按行货款比例分摊，6 位）' AFTER `cost_goods`,
    ADD COLUMN `cost_fee`     decimal(18,6) NOT NULL DEFAULT 0 COMMENT '单位成本 · 采购费用（费用减优惠，按行货款比例分摊，可为负，6 位）' AFTER `cost_invoice`,
    ADD COLUMN `cost_claim`   decimal(18,6) NOT NULL DEFAULT 0 COMMENT '单位成本 · 质量索赔（折价接收的折价按批次数量平均，为负数，6 位）' AFTER `cost_fee`;
UPDATE `inv_batch` SET `cost_goods` = `unit_price_cny`;

-- ---------------------------------------------------------------- 流水：业务日期、分项金额、成本调整
ALTER TABLE `inv_move`
    ADD COLUMN `biz_date` date NULL COMMENT '业务日期（单据日期：入库、交货代、出运、验收、寄出、出库日期；反向流水与成本调整为当天），按它归属月份' AFTER `move_time`,
    MODIFY COLUMN `op_type` tinyint NOT NULL COMMENT '操作类型（1-入库、2-出库、3-调入、4-调出、5-转入、6-转出、7-预占、8-释放、9-成本调整）',
    MODIFY COLUMN `quantity` int NOT NULL COMMENT '数量（入库、调入、转入、预占为正，出库、调出、转出、释放为负，成本调整为 0）',
    MODIFY COLUMN `amount_cny` decimal(18,2) NOT NULL DEFAULT 0 COMMENT '人民币金额 = 四项分项金额之和（各项 = 数量 × 单位成本分项，HALF_UP 2 位）；预占、释放为 0',
    ADD COLUMN `amount_goods`   decimal(18,2) NOT NULL DEFAULT 0 COMMENT '金额 · 货款' AFTER `amount_cny`,
    ADD COLUMN `amount_invoice` decimal(18,2) NOT NULL DEFAULT 0 COMMENT '金额 · 开票成本' AFTER `amount_goods`,
    ADD COLUMN `amount_fee`     decimal(18,2) NOT NULL DEFAULT 0 COMMENT '金额 · 采购费用' AFTER `amount_invoice`,
    ADD COLUMN `amount_claim`   decimal(18,2) NOT NULL DEFAULT 0 COMMENT '金额 · 质量索赔' AFTER `amount_fee`,
    MODIFY COLUMN `biz_type` varchar(32) NOT NULL COMMENT '业务类型（PURCHASE_IN-采购入库、PURCHASE_IN_REVERSE-冲销采购入库、DISCOUNT_ACCEPT-折价接收、HANDOVER-交货代、HANDOVER_UNDO-撤销交货代、SALES_OUT-销售出库、RETURN_IN-退货入库、RETURN_IN_UNDO-撤回退货入库、PURCHASE_RETURN_OUT-采购退货出库、OTHER_OUT-其他出库、OTHER_OUT_VOID-作废其他出库、SYSTEM_IN-系统外入库、RESERVE-预占、RELEASE-释放、MERGE-商品合并、OPENING-期初、COST_ADJUST-成本调整）',
    MODIFY COLUMN `doc_type` varchar(32) NOT NULL DEFAULT '' COMMENT '来源单据类型（RC-采购入库单、OB-销售出库单、SH-出运单、RR-退货入库单、PR-采购退货单、OO-其他出库单、SO-销售订单、RD-到货差异、CA-成本调整单）';
UPDATE `inv_move` SET `biz_date` = DATE(`move_time`), `amount_goods` = `amount_cny`;
ALTER TABLE `inv_move`
    MODIFY COLUMN `biz_date` date NOT NULL COMMENT '业务日期（单据日期：入库、交货代、出运、验收、寄出、出库日期；反向流水与成本调整为当天），按它归属月份',
    ADD KEY `idx_tenant_biz_date` (`tenant_id`, `biz_date`);

-- ---------------------------------------------------------------- 成本调整单
CREATE TABLE IF NOT EXISTS `inv_cost_adjust` (
    `id`               bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`        int           NOT NULL DEFAULT 0 COMMENT '租户ID',
    `adjust_no`        varchar(32)   NOT NULL DEFAULT '' COMMENT '成本调整单号（CA + 年月日 + 当日流水，如 CA20261205001）',
    `adjust_date`      date          NOT NULL COMMENT '调整日期',
    `period_month`     varchar(7)    NOT NULL DEFAULT '' COMMENT '所属月份（YYYY-MM，调整日期所在月份）',
    `reason`           tinyint       NOT NULL COMMENT '触发原因（1-外币付款、2-作废付款、3-供应商退款、4-采购订单费用变化、5-折价接收、6-重新打开折价、7-每日核对、8-成本口径升级）',
    `source_doc_type`  varchar(16)   NOT NULL DEFAULT '' COMMENT '来源单据类型（PAY-付款单、PO-采购订单、RD-到货差异；每日核对、成本口径升级为空）',
    `source_doc_id`    bigint        NULL COMMENT '来源单据ID',
    `source_doc_no`    varchar(32)   NOT NULL DEFAULT '' COMMENT '来源单号',
    `batch_count`      int           NOT NULL DEFAULT 0 COMMENT '调整的批次数',
    `first_model`      varchar(128)  NOT NULL DEFAULT '' COMMENT '第一个批次的型号（列表展示）',
    `currency_code`    varchar(8)    NOT NULL DEFAULT 'CNY' COMMENT '金额币种（人民币）',
    `stock_amount`     decimal(18,2) NOT NULL DEFAULT 0 COMMENT '在库调整合计（人民币，可为负）',
    `cogs_amount`      decimal(18,2) NOT NULL DEFAULT 0 COMMENT '销售成本调整合计（人民币，可为负）',
    `other_amount`     decimal(18,2) NOT NULL DEFAULT 0 COMMENT '其他出库调整合计（人民币，可为负）',
    `remark`           varchar(500)  NOT NULL DEFAULT '' COMMENT '备注',
    `deleted_at`       datetime      NULL COMMENT '软删除时间，NULL表示未删除',
    `create_time`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`        varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`        varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_adjust_no` (`tenant_id`, `adjust_no`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_period_month` (`period_month`),
    KEY `idx_reason` (`reason`),
    KEY `idx_source_doc_id` (`source_doc_id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_update_time` (`update_time`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成本调整单（系统自动生成）';

CREATE TABLE IF NOT EXISTS `inv_cost_adjust_item` (
    `id`               bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`        int           NOT NULL DEFAULT 0 COMMENT '租户ID',
    `adjust_id`        bigint        NOT NULL COMMENT '成本调整单ID，关联inv_cost_adjust.id',
    `batch_id`         bigint        NOT NULL COMMENT '批次ID，关联inv_batch.id',
    `product_id`       bigint        NOT NULL COMMENT '商品ID，关联product.id',
    `part`             tinyint       NOT NULL COMMENT '部分（1-在库、2-已出运、3-其他出库）',
    `warehouse_id`     bigint        NULL COMMENT '在库部分的仓库ID，关联inv_warehouse.id',
    `doc_type`         varchar(32)   NOT NULL DEFAULT '' COMMENT '已出运、其他出库的出库单据类型（SH-出运单、OO-其他出库单、PR-采购退货单）',
    `doc_id`           bigint        NULL COMMENT '出库单据ID',
    `doc_no`           varchar(32)   NOT NULL DEFAULT '' COMMENT '出库单号',
    `so_item_id`       bigint        NULL COMMENT '已出运部分的销售订单型号行ID，关联sales_order_item.id',
    `quantity`         int           NOT NULL DEFAULT 0 COMMENT '数量',
    `old_unit_cost`    decimal(18,6) NOT NULL DEFAULT 0 COMMENT '原单位成本',
    `new_unit_cost`    decimal(18,6) NOT NULL DEFAULT 0 COMMENT '新单位成本',
    `diff_goods`       decimal(18,6) NOT NULL DEFAULT 0 COMMENT '单位成本变化 · 货款',
    `diff_invoice`     decimal(18,6) NOT NULL DEFAULT 0 COMMENT '单位成本变化 · 开票成本',
    `diff_fee`         decimal(18,6) NOT NULL DEFAULT 0 COMMENT '单位成本变化 · 采购费用',
    `diff_claim`       decimal(18,6) NOT NULL DEFAULT 0 COMMENT '单位成本变化 · 质量索赔',
    `currency_code`    varchar(8)    NOT NULL DEFAULT 'CNY' COMMENT '金额币种（人民币）',
    `amount_goods`     decimal(18,2) NOT NULL DEFAULT 0 COMMENT '调整金额 · 货款 = 数量 × 变化，HALF_UP 2 位',
    `amount_invoice`   decimal(18,2) NOT NULL DEFAULT 0 COMMENT '调整金额 · 开票成本',
    `amount_fee`       decimal(18,2) NOT NULL DEFAULT 0 COMMENT '调整金额 · 采购费用',
    `amount_claim`     decimal(18,2) NOT NULL DEFAULT 0 COMMENT '调整金额 · 质量索赔',
    `amount_cny`       decimal(18,2) NOT NULL DEFAULT 0 COMMENT '调整金额合计 = 四项之和',
    `deleted_at`       datetime      NULL COMMENT '软删除时间，NULL表示未删除',
    `create_time`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`        varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`        varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_adjust_id` (`adjust_id`),
    KEY `idx_batch_id` (`batch_id`),
    KEY `idx_product_id` (`product_id`),
    KEY `idx_so_item_id` (`so_item_id`),
    KEY `idx_doc_id` (`doc_id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成本调整单明细（每个批次按在库、已出运、其他出库拆成多行）';

-- ---------------------------------------------------------------- 库存月结
CREATE TABLE IF NOT EXISTS `inv_period` (
    `id`               bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`        int           NOT NULL DEFAULT 0 COMMENT '租户ID',
    `period_month`     varchar(7)    NOT NULL COMMENT '月份（YYYY-MM）',
    `status`           tinyint       NOT NULL DEFAULT 0 COMMENT '状态（0-未结账、1-已结账）',
    `closed_by`        bigint        NULL COMMENT '结账人用户ID',
    `closed_by_name`   varchar(32)   NOT NULL DEFAULT '' COMMENT '结账人姓名',
    `closed_at`        datetime      NULL COMMENT '结账时间',
    `reopened_by`      bigint        NULL COMMENT '最近一次反结账人用户ID',
    `reopened_by_name` varchar(32)   NOT NULL DEFAULT '' COMMENT '最近一次反结账人姓名',
    `reopened_at`      datetime      NULL COMMENT '最近一次反结账时间',
    `reopen_reason`    varchar(200)  NOT NULL DEFAULT '' COMMENT '最近一次反结账原因',
    `currency_code`    varchar(8)    NOT NULL DEFAULT 'CNY' COMMENT '金额币种（人民币）',
    `closing_amount`   decimal(18,2) NOT NULL DEFAULT 0 COMMENT '结账时的期末库存金额',
    `cogs_amount`      decimal(18,2) NOT NULL DEFAULT 0 COMMENT '结账时的本月销售成本（含针对出运的成本调整）',
    `adjust_amount`    decimal(18,2) NOT NULL DEFAULT 0 COMMENT '结账时的本月成本调整合计',
    `deleted_at`       datetime      NULL COMMENT '软删除时间，NULL表示未删除',
    `create_time`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`        varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`        varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_month` (`tenant_id`, `period_month`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存月结（每月一行，结账后锁定业务日期在该月的库存进出）';

CREATE TABLE IF NOT EXISTS `inv_period_ledger` (
    `id`               bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`        int           NOT NULL DEFAULT 0 COMMENT '租户ID',
    `period_id`        bigint        NOT NULL COMMENT '月结ID，关联inv_period.id',
    `period_month`     varchar(7)    NOT NULL COMMENT '月份（YYYY-MM）',
    `warehouse_id`     bigint        NOT NULL COMMENT '仓库ID，关联inv_warehouse.id',
    `product_id`       bigint        NOT NULL COMMENT '商品ID，关联product.id',
    `opening_qty`      int           NOT NULL DEFAULT 0 COMMENT '期初数量',
    `opening_amount`   decimal(18,2) NOT NULL DEFAULT 0 COMMENT '期初金额（人民币）',
    `in_qty`           int           NOT NULL DEFAULT 0 COMMENT '本月入库数量（入库、调入）',
    `in_amount`        decimal(18,2) NOT NULL DEFAULT 0 COMMENT '本月入库金额',
    `out_qty`          int           NOT NULL DEFAULT 0 COMMENT '本月出库数量（出库、调出）',
    `out_amount`       decimal(18,2) NOT NULL DEFAULT 0 COMMENT '本月出库金额',
    `adjust_amount`    decimal(18,2) NOT NULL DEFAULT 0 COMMENT '本月成本调整金额',
    `closing_qty`      int           NOT NULL DEFAULT 0 COMMENT '期末数量',
    `closing_amount`   decimal(18,2) NOT NULL DEFAULT 0 COMMENT '期末金额 = 期初 + 入库 − 出库 + 成本调整',
    `currency_code`    varchar(8)    NOT NULL DEFAULT 'CNY' COMMENT '金额币种（人民币）',
    `deleted_at`       datetime      NULL COMMENT '软删除时间（反结账作废快照），NULL表示有效',
    `create_time`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`        varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`        varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_period_id` (`period_id`),
    KEY `idx_period_month` (`period_month`),
    KEY `idx_warehouse_id` (`warehouse_id`),
    KEY `idx_product_id` (`product_id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='月结快照：库存台账（月份 × 仓库 × 商品）';

CREATE TABLE IF NOT EXISTS `inv_period_cogs` (
    `id`               bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`        int           NOT NULL DEFAULT 0 COMMENT '租户ID',
    `period_id`        bigint        NOT NULL COMMENT '月结ID，关联inv_period.id',
    `period_month`     varchar(7)    NOT NULL COMMENT '月份（YYYY-MM）',
    `kind`             tinyint       NOT NULL COMMENT '类型（1-出运结转、2-成本调整）',
    `so_item_id`       bigint        NOT NULL COMMENT '销售订单型号行ID，关联sales_order_item.id',
    `product_id`       bigint        NOT NULL COMMENT '商品ID，关联product.id',
    `doc_id`           bigint        NULL COMMENT '出运单ID（出运结转）或成本调整单ID（成本调整）',
    `doc_no`           varchar(32)   NOT NULL DEFAULT '' COMMENT '出运单号或成本调整单号',
    `biz_date`         date          NULL COMMENT '出运日期或调整日期',
    `quantity`         int           NOT NULL DEFAULT 0 COMMENT '数量',
    `system_qty`       int           NOT NULL DEFAULT 0 COMMENT '其中来自系统外入库（成本为 0）的数量',
    `currency_code`    varchar(8)    NOT NULL DEFAULT 'CNY' COMMENT '金额币种（人民币）',
    `amount_goods`     decimal(18,2) NOT NULL DEFAULT 0 COMMENT '货款',
    `amount_invoice`   decimal(18,2) NOT NULL DEFAULT 0 COMMENT '开票成本',
    `amount_fee`       decimal(18,2) NOT NULL DEFAULT 0 COMMENT '采购费用',
    `amount_claim`     decimal(18,2) NOT NULL DEFAULT 0 COMMENT '质量索赔（负数）',
    `amount_cny`       decimal(18,2) NOT NULL DEFAULT 0 COMMENT '销售成本合计 = 四项之和',
    `deleted_at`       datetime      NULL COMMENT '软删除时间（反结账作废快照），NULL表示有效',
    `create_time`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`        varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`        varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_period_id` (`period_id`),
    KEY `idx_period_month` (`period_month`),
    KEY `idx_so_item_id` (`so_item_id`),
    KEY `idx_product_id` (`product_id`),
    KEY `idx_doc_id` (`doc_id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='月结快照：销售成本（按订单型号行与出运单 / 成本调整单）';

-- ---------------------------------------------------------------- 菜单与按钮
INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`)
SELECT v.* FROM (
              SELECT 100129 AS id, 100083 AS pid, 'RS2100129' AS code, '成本调整' AS name, 2 AS type, 10 AS sort, 'diff' AS li, '' AS lsi, '' AS di, '' AS dsi,
                     '/finance/cost-adjustments' AS path, '' AS permission, 1 AS status, '' AS micro_app, 'sys' AS cb, 'sys' AS ub
    UNION ALL SELECT 100130, 100083, 'RS2100130', '库存月结', 2, 11, 'lock', '', '', '', '/finance/periods', '', 1, '', 'sys', 'sys'
    UNION ALL SELECT 110252, 100130, 'RS3110252', '结账',   3, 1, '', '', '', '', '', 'inventory:period:close', 1, '', 'sys', 'sys'
    UNION ALL SELECT 110253, 100130, 'RS3110253', '反结账', 3, 2, '', '', '', '', '', 'inventory:period:reopen', 1, '', 'sys', 'sys'
) v
WHERE NOT EXISTS (SELECT 1 FROM `resource` r WHERE r.`id` = v.id);

-- 套餐：含收款管理的补全部新菜单与按钮
UPDATE `tenant_package` SET `menu_ids` = JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(`menu_ids`,
        '$', 100129), '$', 100130), '$', 110252), '$', 110253)
WHERE `menu_ids` IS NOT NULL AND JSON_VALID(`menu_ids`)
  AND JSON_CONTAINS(`menu_ids`, '100085')
  AND NOT JSON_CONTAINS(`menu_ids`, '100129');

-- 角色：有收款管理菜单的得到成本调整、库存月结与结账；反结账只授予租户管理员
INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT x.`role_code`, s.`id`, s.`code`
FROM `role_resource` x
JOIN `resource` s ON s.`id` IN (100129, 100130, 110252)
WHERE x.`resource_id` = 100085
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = x.`role_code` AND y.`resource_id` = s.`id`);

INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT r.`code`, s.`id`, s.`code`
FROM `role` r
JOIN `resource` s ON s.`id` IN (100129, 100130, 110252, 110253)
WHERE r.`code` = 'ROLE_ADMIN' AND r.`is_built_in` = 1
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = r.`code` AND y.`resource_id` = s.`id`);

-- ---------------------------------------------------------------- 回退（手动执行）
-- 批次单位成本保留新口径的值，不影响库存底座使用；成本调整流水（op_type = 9）先删除再删列。
-- DELETE FROM `role_resource` WHERE `resource_id` IN (100129, 100130, 110252, 110253);
-- DELETE FROM `resource` WHERE `id` IN (100129, 100130, 110252, 110253);
-- DROP TABLE `inv_period_cogs`, `inv_period_ledger`, `inv_period`, `inv_cost_adjust_item`, `inv_cost_adjust`;
-- DELETE FROM `inv_move` WHERE `op_type` = 9;
-- ALTER TABLE `inv_move` DROP KEY `idx_tenant_biz_date`, DROP COLUMN `biz_date`, DROP COLUMN `amount_goods`, DROP COLUMN `amount_invoice`, DROP COLUMN `amount_fee`, DROP COLUMN `amount_claim`;
-- ALTER TABLE `inv_batch` DROP COLUMN `cost_goods`, DROP COLUMN `cost_invoice`, DROP COLUMN `cost_fee`, DROP COLUMN `cost_claim`;
-- DELETE FROM `sys_config` WHERE `config_key` = 'inventory.costing.upgraded';
-- DELETE FROM `flyway_schema_history` WHERE `version` = '1.5.56';

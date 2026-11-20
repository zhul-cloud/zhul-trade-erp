-- 库存一期：底座与自动过账（openspec inventory-foundation）
-- 仓库、批次、库存流水、库存余额、预占、其他出库单；采购退货单增加来源「库存退回」；仓库管理菜单调整

-- ---------------------------------------------------------------- 仓库
CREATE TABLE IF NOT EXISTS `inv_warehouse` (
    `id`           bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`    int          NOT NULL DEFAULT 0 COMMENT '租户ID',
    `code`         varchar(16)  NOT NULL DEFAULT '' COMMENT '仓库编码（WH + 4 位序号，如 WH0001，租户内唯一）',
    `name`         varchar(64)  NOT NULL DEFAULT '' COMMENT '仓库名称（货代仓跟着货代名称）',
    `type`         tinyint      NOT NULL DEFAULT 1 COMMENT '类型（1-自有仓、2-货代仓）',
    `forwarder_id` bigint       NULL COMMENT '关联货代ID（货代仓必填），关联supplier.id',
    `status`       tinyint      NOT NULL DEFAULT 1 COMMENT '状态（0-停用、1-启用；货代仓随货代状态）',
    `deleted_at`   datetime     NULL COMMENT '软删除时间，NULL表示未删除',
    `create_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`    varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`    varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_code` (`tenant_id`, `code`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_forwarder_id` (`forwarder_id`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='仓库（自有仓、货代仓）';

-- ---------------------------------------------------------------- 批次
CREATE TABLE IF NOT EXISTS `inv_batch` (
    `id`                    bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`             int           NOT NULL DEFAULT 0 COMMENT '租户ID',
    `batch_no`              varchar(48)   NOT NULL DEFAULT '' COMMENT '批次号（采购入库「入库单号-行序号」、退货入库「退货入库单号-行序号」、系统外入库「SYS-出库单号-行序号」、期初「INIT-序号」）',
    `product_id`            bigint        NOT NULL COMMENT '商品ID，关联product.id',
    `grade`                 varchar(32)   NOT NULL DEFAULT '' COMMENT '成色（取销售订单型号行，如 全新原装；没有为空）',
    `source_type`           tinyint       NOT NULL COMMENT '来源（1-采购入库、2-退货入库、3-系统外入库、4-期初）',
    `source_id`             bigint        NULL COMMENT '来源单据ID（采购入库单、退货入库单、销售出库单）',
    `source_item_id`        bigint        NULL COMMENT '来源单据行ID',
    `po_item_id`            bigint        NULL COMMENT '来源采购订单行ID，关联purchase_order_item.id',
    `so_item_id`            bigint        NULL COMMENT '来源销售订单型号行ID，关联sales_order_item.id',
    `in_date`               date          NOT NULL COMMENT '入库日期',
    `currency_code`         varchar(8)    NOT NULL DEFAULT 'CNY' COMMENT '入库单价币种',
    `unit_price`            decimal(18,6) NOT NULL DEFAULT 0 COMMENT '入库单价（原币，不含税，6 位小数）',
    `exchange_rate`         decimal(18,6) NOT NULL DEFAULT 1 COMMENT '汇率（6 位小数）',
    `unit_price_cny`        decimal(18,6) NOT NULL DEFAULT 0 COMMENT '入库单价（人民币）= 原币 × 汇率，HALF_UP 6 位',
    `deleted_at`            datetime      NULL COMMENT '软删除时间，NULL表示未删除',
    `create_time`           datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`             varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`           datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`             varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_batch_no` (`tenant_id`, `batch_no`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_product_id` (`product_id`),
    KEY `idx_source` (`source_type`, `source_item_id`),
    KEY `idx_po_item_id` (`po_item_id`),
    KEY `idx_so_item_id` (`so_item_id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存批次';

-- ---------------------------------------------------------------- 流水（只增不改）
CREATE TABLE IF NOT EXISTS `inv_move` (
    `id`             bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`      int           NOT NULL DEFAULT 0 COMMENT '租户ID',
    `move_time`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
    `warehouse_id`   bigint        NOT NULL COMMENT '仓库ID，关联inv_warehouse.id',
    `product_id`     bigint        NOT NULL COMMENT '商品ID，关联product.id',
    `batch_id`       bigint        NOT NULL COMMENT '批次ID，关联inv_batch.id',
    `stock_status`   tinyint       NOT NULL DEFAULT 1 COMMENT '库存状态（1-合格、2-不良）',
    `op_type`        tinyint       NOT NULL COMMENT '操作类型（1-入库、2-出库、3-调入、4-调出、5-转入、6-转出、7-预占、8-释放）',
    `quantity`       int           NOT NULL COMMENT '数量（入库、调入、转入、预占为正，出库、调出、转出、释放为负）',
    `target_type`    tinyint       NULL COMMENT '涉及的预占对象（1-销售订单型号行、2-采购退货单行）；预占、释放必填，出库、调拨的是预占的货时填写',
    `target_id`      bigint        NULL COMMENT '预占对象ID（sales_order_item.id 或 purchase_return_item.id）',
    `unit_price_cny` decimal(18,6) NOT NULL DEFAULT 0 COMMENT '人民币单价（取批次）',
    `amount_cny`     decimal(18,2) NOT NULL DEFAULT 0 COMMENT '人民币金额 = 数量 × 单价，HALF_UP 2 位；预占、释放为 0',
    `biz_type`       varchar(32)   NOT NULL COMMENT '业务类型（PURCHASE_IN-采购入库、PURCHASE_IN_REVERSE-冲销采购入库、DISCOUNT_ACCEPT-折价接收、HANDOVER-交货代、HANDOVER_UNDO-撤销交货代、SALES_OUT-销售出库、RETURN_IN-退货入库、RETURN_IN_UNDO-撤回退货入库、PURCHASE_RETURN_OUT-采购退货出库、OTHER_OUT-其他出库、OTHER_OUT_VOID-作废其他出库、SYSTEM_IN-系统外入库、RESERVE-预占、RELEASE-释放、MERGE-商品合并、OPENING-期初）',
    `biz_label`      varchar(32)   NOT NULL DEFAULT '' COMMENT '业务补充说明（如其他出库类型「报废」）',
    `doc_type`       varchar(32)   NOT NULL DEFAULT '' COMMENT '来源单据类型（RC-采购入库单、OB-销售出库单、SH-出运单、RR-退货入库单、PR-采购退货单、OO-其他出库单、SO-销售订单、RD-到货差异）',
    `doc_id`         bigint        NULL COMMENT '来源单据ID',
    `doc_no`         varchar(32)   NOT NULL DEFAULT '' COMMENT '来源单号',
    `doc_item_id`    bigint        NULL COMMENT '来源单据行ID',
    `reverse_of`     bigint        NULL COMMENT '反向冲销的原流水ID，关联inv_move.id（唯一：同一条流水只能冲销一次）',
    `operator_id`    bigint        NULL COMMENT '操作人用户ID',
    `operator_name`  varchar(32)   NOT NULL DEFAULT '' COMMENT '操作人姓名',
    `create_time`    datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`      varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`    datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`      varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_reverse_of` (`reverse_of`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_tenant_time` (`tenant_id`, `move_time`),
    KEY `idx_warehouse_id` (`warehouse_id`),
    KEY `idx_product_id` (`product_id`),
    KEY `idx_batch_id` (`batch_id`),
    KEY `idx_doc` (`doc_type`, `doc_id`),
    KEY `idx_doc_no` (`doc_no`),
    KEY `idx_target` (`target_type`, `target_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存流水（只增不改；作废、冲销生成反向流水）';

-- ---------------------------------------------------------------- 余额
CREATE TABLE IF NOT EXISTS `inv_balance` (
    `id`           bigint      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`    int         NOT NULL DEFAULT 0 COMMENT '租户ID',
    `warehouse_id` bigint      NOT NULL COMMENT '仓库ID，关联inv_warehouse.id',
    `product_id`   bigint      NOT NULL COMMENT '商品ID，关联product.id',
    `batch_id`     bigint      NOT NULL COMMENT '批次ID，关联inv_batch.id',
    `stock_status` tinyint     NOT NULL DEFAULT 1 COMMENT '库存状态（1-合格、2-不良）',
    `on_hand`      int         NOT NULL DEFAULT 0 COMMENT '结存数量（不能为负）',
    `reserved`     int         NOT NULL DEFAULT 0 COMMENT '预占数量（订单预占 + 退货预占，不超过结存）',
    `last_move_time` datetime  NULL COMMENT '最近变动时间',
    `version`      int         NOT NULL DEFAULT 0 COMMENT '版本号',
    `create_time`  datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`    varchar(32) NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`  datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`    varchar(32) NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_balance` (`tenant_id`, `warehouse_id`, `product_id`, `batch_id`, `stock_status`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_product_id` (`product_id`),
    KEY `idx_batch_id` (`batch_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存余额（仓库 × 商品 × 批次 × 状态）';

-- ---------------------------------------------------------------- 预占
CREATE TABLE IF NOT EXISTS `inv_reservation` (
    `id`           bigint      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`    int         NOT NULL DEFAULT 0 COMMENT '租户ID',
    `target_type`  tinyint     NOT NULL COMMENT '预占对象（1-销售订单型号行、2-采购退货单行）',
    `target_id`    bigint      NOT NULL COMMENT '预占对象ID（sales_order_item.id 或 purchase_return_item.id）',
    `warehouse_id` bigint      NOT NULL COMMENT '仓库ID，关联inv_warehouse.id',
    `product_id`   bigint      NOT NULL COMMENT '商品ID，关联product.id',
    `batch_id`     bigint      NOT NULL COMMENT '批次ID，关联inv_batch.id',
    `stock_status` tinyint     NOT NULL DEFAULT 1 COMMENT '库存状态（1-合格、2-不良）',
    `quantity`     int         NOT NULL DEFAULT 0 COMMENT '预占数量（为 0 表示已全部释放或出库）',
    `create_time`  datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`    varchar(32) NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`  datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`    varchar(32) NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_reservation` (`tenant_id`, `target_type`, `target_id`, `warehouse_id`, `batch_id`, `stock_status`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_target` (`target_type`, `target_id`),
    KEY `idx_batch_id` (`batch_id`),
    KEY `idx_product_id` (`product_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存预占（订单预占、退货预占）';

-- ---------------------------------------------------------------- 其他出库单
CREATE TABLE IF NOT EXISTS `inv_other_outbound` (
    `id`               bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`        int           NOT NULL DEFAULT 0 COMMENT '租户ID',
    `outbound_no`      varchar(32)   NOT NULL DEFAULT '' COMMENT '其他出库单号（OO + 年月日 + 当日流水，如 OO20261121001）',
    `outbound_type`    tinyint       NOT NULL COMMENT '出库类型（1-报废、2-转样品、3-货代丢失、4-货损）',
    `warehouse_id`     bigint        NOT NULL COMMENT '仓库ID，关联inv_warehouse.id',
    `outbound_date`    date          NOT NULL COMMENT '出库日期',
    `reason`           varchar(200)  NOT NULL DEFAULT '' COMMENT '原因',
    `status`           tinyint       NOT NULL DEFAULT 1 COMMENT '状态（1-已出库、2-已作废）',
    `currency_code`    varchar(8)    NOT NULL DEFAULT 'CNY' COMMENT '金额币种（人民币）',
    `amount_cny`       decimal(18,2) NOT NULL DEFAULT 0 COMMENT '金额（人民币，明细合计）',
    `source_doc_type`  varchar(16)   NOT NULL DEFAULT '' COMMENT '来源单据类型（RR-退货入库单；手工新建为空）',
    `source_doc_id`    bigint        NULL COMMENT '来源单据ID（退货去向报废时为退货入库单ID）',
    `source_doc_no`    varchar(32)   NOT NULL DEFAULT '' COMMENT '来源单号',
    `source_ref_id`    bigint        NULL COMMENT '来源去向ID，关联sales_return_disposition.id',
    `remark`           varchar(500)  NOT NULL DEFAULT '' COMMENT '备注',
    `void_reason`      varchar(200)  NOT NULL DEFAULT '' COMMENT '作废原因',
    `deleted_at`       datetime      NULL COMMENT '软删除时间，NULL表示未删除',
    `create_time`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`        varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`        varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_outbound_no` (`tenant_id`, `outbound_no`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_warehouse_id` (`warehouse_id`),
    KEY `idx_source_doc_id` (`source_doc_id`),
    KEY `idx_source_ref_id` (`source_ref_id`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_update_time` (`update_time`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='其他出库单';

CREATE TABLE IF NOT EXISTS `inv_other_outbound_item` (
    `id`                 bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`          int           NOT NULL DEFAULT 0 COMMENT '租户ID',
    `other_outbound_id`  bigint        NOT NULL COMMENT '其他出库单ID，关联inv_other_outbound.id',
    `product_id`         bigint        NOT NULL COMMENT '商品ID，关联product.id',
    `batch_id`           bigint        NOT NULL COMMENT '批次ID，关联inv_batch.id',
    `stock_status`       tinyint       NOT NULL DEFAULT 1 COMMENT '库存状态（1-合格、2-不良）',
    `quantity`           int           NOT NULL COMMENT '数量',
    `currency_code`      varchar(8)    NOT NULL DEFAULT 'CNY' COMMENT '金额币种（人民币）',
    `unit_price_cny`     decimal(18,6) NOT NULL DEFAULT 0 COMMENT '单价（人民币，取批次）',
    `amount_cny`         decimal(18,2) NOT NULL DEFAULT 0 COMMENT '金额（人民币）= 数量 × 单价，HALF_UP 2 位',
    `deleted_at`         datetime      NULL COMMENT '软删除时间，NULL表示未删除',
    `create_time`        datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`          varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`        datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`          varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_other_outbound_id` (`other_outbound_id`),
    KEY `idx_product_id` (`product_id`),
    KEY `idx_batch_id` (`batch_id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='其他出库单明细';

-- ---------------------------------------------------------------- 采购退货单：来源「库存退回」
ALTER TABLE `purchase_return`
    MODIFY COLUMN `source_type` tinyint NOT NULL COMMENT '来源（1-售后退货、2-到货不良·退货换货、3-到货不良·退货不补、4-多发退回、5-库存退回）';
ALTER TABLE `purchase_return_item`
    ADD COLUMN `warehouse_id` bigint  NULL COMMENT '退货出库的仓库ID（库存退回），关联inv_warehouse.id' AFTER `po_item_id`,
    ADD COLUMN `batch_id`     bigint  NULL COMMENT '退货出库的批次ID（库存退回），关联inv_batch.id' AFTER `warehouse_id`,
    ADD COLUMN `stock_status` tinyint NULL COMMENT '退货出库的库存状态（1-合格、2-不良；库存退回）' AFTER `batch_id`,
    ADD KEY `idx_batch_id` (`batch_id`);

-- ---------------------------------------------------------------- 菜单与按钮
INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`)
SELECT v.* FROM (
              SELECT 100124 AS id, 100090 AS pid, 'RS2100124' AS code, '其他出库' AS name, 2 AS type, 4 AS sort, 'export' AS li, '' AS lsi, '' AS di, '' AS dsi,
                     '/warehouse/other-outbounds' AS path, '' AS permission, 1 AS status, '' AS micro_app, 'sys' AS cb, 'sys' AS ub
    UNION ALL SELECT 100125, 100090, 'RS2100125', '库存查询', 2, 5, 'database', '', '', '', '/warehouse/stocks',    '', 1, '', 'sys', 'sys'
    UNION ALL SELECT 100126, 100090, 'RS2100126', '库存流水', 2, 6, 'swap', '', '', '', '/warehouse/moves',  '', 1, '', 'sys', 'sys'
    UNION ALL SELECT 100127, 100090, 'RS2100127', '库存台账', 2, 7, 'book', '', '', '', '/warehouse/ledger',    '', 1, '', 'sys', 'sys'
    UNION ALL SELECT 100128, 100090, 'RS2100128', '仓库档案', 2, 8, 'bank', '', '', '', '/warehouse/warehouses', '', 1, '', 'sys', 'sys'
    UNION ALL SELECT 110248, 100124, 'RS3110248', '新建其他出库',   3, 1, '', '', '', '', '', 'inventory:other-outbound:edit', 1, '', 'sys', 'sys'
    UNION ALL SELECT 110249, 100124, 'RS3110249', '作废其他出库单', 3, 2, '', '', '', '', '', 'inventory:other-outbound:void', 1, '', 'sys', 'sys'
    UNION ALL SELECT 110250, 100125, 'RS3110250', '退回供应商',     3, 1, '', '', '', '', '', 'inventory:return-to-supplier', 1, '', 'sys', 'sys'
    UNION ALL SELECT 110251, 100128, 'RS3110251', '编辑仓库与库存模式', 3, 1, '', '', '', '', '', 'inventory:warehouse:edit', 1, '', 'sys', 'sys'
) v
WHERE NOT EXISTS (SELECT 1 FROM `resource` r WHERE r.`id` = v.id);

-- 闲置库存菜单与「处置闲置库存」按钮停用（system/navigation：/warehouse/holds 重定向到库存查询）
UPDATE `resource` SET `status` = 0, `update_time` = NOW() WHERE `id` IN (100092, 110194);

-- 仓库管理排序：采购入库、销售出库、退货入库、其他出库、库存查询、库存流水、库存台账、仓库档案、实拍任务
UPDATE `resource` SET `sort` = CASE `id` WHEN 100091 THEN 1 WHEN 100095 THEN 2 WHEN 100121 THEN 3 WHEN 100124 THEN 4
        WHEN 100125 THEN 5 WHEN 100126 THEN 6 WHEN 100127 THEN 7 WHEN 100128 THEN 8 WHEN 100093 THEN 9 END,
    `update_time` = NOW()
WHERE `id` IN (100091, 100095, 100121, 100124, 100125, 100126, 100127, 100128, 100093);

-- 套餐：含采购入库或闲置库存的补全部新菜单与按钮
UPDATE `tenant_package` SET `menu_ids` = JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(
        JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(`menu_ids`,
        '$', 100124), '$', 100125), '$', 100126), '$', 100127), '$', 100128), '$', 110248), '$', 110249), '$', 110250), '$', 110251)
WHERE `menu_ids` IS NOT NULL AND JSON_VALID(`menu_ids`)
  AND (JSON_CONTAINS(`menu_ids`, '100091') OR JSON_CONTAINS(`menu_ids`, '100092'))
  AND NOT JSON_CONTAINS(`menu_ids`, '100125');

-- 角色：有采购入库或闲置库存菜单的得到库存查询、库存流水、库存台账、其他出库与新建；
-- 有「处置闲置库存」按钮的得到作废其他出库单与退回供应商；仓库档案与库存模式只授予租户管理员
INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT x.`role_code`, s.`id`, s.`code`
FROM `role_resource` x
JOIN `resource` s ON s.`id` IN (100124, 100125, 100126, 100127, 110248)
WHERE x.`resource_id` IN (100091, 100092)
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = x.`role_code` AND y.`resource_id` = s.`id`);

INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT x.`role_code`, s.`id`, s.`code`
FROM `role_resource` x
JOIN `resource` s ON s.`id` IN (110249, 110250)
WHERE x.`resource_id` = 110194
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = x.`role_code` AND y.`resource_id` = s.`id`);

INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT r.`code`, s.`id`, s.`code`
FROM `role` r
JOIN `resource` s ON s.`id` IN (100124, 100125, 100126, 100127, 100128, 110248, 110249, 110250, 110251)
WHERE r.`code` = 'ROLE_ADMIN' AND r.`is_built_in` = 1
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = r.`code` AND y.`resource_id` = s.`id`);

-- ---------------------------------------------------------------- 回退（手动执行）
-- 先把可用库存导回闲置库存（上线期间新产生的暂存）：按 inv_balance 中没有预占的合格结存，人工核对后补 stock_hold。
-- DELETE FROM `role_resource` WHERE `resource_id` IN (100124, 100125, 100126, 100127, 100128, 110248, 110249, 110250, 110251);
-- DELETE FROM `resource` WHERE `id` IN (100124, 100125, 100126, 100127, 100128, 110248, 110249, 110250, 110251);
-- UPDATE `resource` SET `status` = 1 WHERE `id` IN (100092, 110194);
-- ALTER TABLE `purchase_return_item` DROP KEY `idx_batch_id`, DROP COLUMN `stock_status`, DROP COLUMN `batch_id`, DROP COLUMN `warehouse_id`;
-- DROP TABLE `inv_other_outbound_item`, `inv_other_outbound`, `inv_reservation`, `inv_balance`, `inv_move`, `inv_batch`, `inv_warehouse`;
-- DELETE FROM `sys_config` WHERE `config_key` IN ('inventory.mode', 'inventory.opening.done');

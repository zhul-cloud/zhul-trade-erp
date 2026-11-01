-- 销售订单部分取消（OpenSpec 变更 sales-order-partial-cancel）
-- 型号行的原数量与已取消数量、部分取消记录、采购订单行的「来源订单减少」数量、闲置库存的来源、字典「部分取消原因」。
-- 回退：见 openspec/changes/sales-order-partial-cancel/design.md 的 Migration Plan，回退 SQL 在文件末尾的注释中。

-- ---------------------------------------------------------------- 型号行：原数量与已取消数量（quantity 表示剩余数量）

ALTER TABLE `sales_order_item`
    ADD COLUMN `original_quantity`  int(11) NOT NULL DEFAULT 0 COMMENT '原数量（成交时的数量；quantity 为部分取消后的剩余数量）' AFTER `quantity`,
    ADD COLUMN `cancelled_quantity` int(11) NOT NULL DEFAULT 0 COMMENT '已部分取消的数量' AFTER `original_quantity`;

UPDATE `sales_order_item` SET `original_quantity` = `quantity` WHERE `original_quantity` = 0;

-- ---------------------------------------------------------------- 部分取消记录

CREATE TABLE `sales_order_partial_cancel` (
    `id`             bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`      int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID',
    `so_id`          bigint        NOT NULL            COMMENT '销售订单ID，关联sales_order.id',
    `so_item_id`     bigint        NOT NULL            COMMENT '订单型号行ID，关联sales_order_item.id',
    `model`          varchar(128)  NOT NULL DEFAULT '' COMMENT '型号',
    `quantity`       int(11)       NOT NULL            COMMENT '本次取消数量',
    `currency_code`  varchar(8)    NOT NULL            COMMENT '币种（订单币种）',
    `unit_price`     decimal(18,2) NOT NULL            COMMENT '单价（原币）',
    `amount`         decimal(18,2) NOT NULL            COMMENT '取消金额（原币）= 单价 × 数量，HALF_UP 两位',
    `amount_cny`     decimal(18,2) NOT NULL            COMMENT '取消金额（CNY）= 取消金额 × 订单汇率，HALF_UP 两位',
    `total_before`   decimal(18,2) NOT NULL            COMMENT '取消前订单合计（原币）',
    `total_after`    decimal(18,2) NOT NULL            COMMENT '取消后订单合计（原币）',
    `reason`         varchar(32)   NOT NULL DEFAULT '' COMMENT '原因（字典 partial_cancel_reason 编码）',
    `remark`         varchar(200)  NOT NULL DEFAULT '' COMMENT '说明',
    `result`         varchar(500)  NOT NULL DEFAULT '' COMMENT '采购与库存处理结果摘要',
    `operator_id`    bigint        NOT NULL DEFAULT 0  COMMENT '操作人用户ID',
    `deleted_at`     datetime      NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`    datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`      varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`    datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`      varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_so_id` (`so_id`),
    KEY `idx_so_item_id` (`so_item_id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售订单部分取消记录';

-- ---------------------------------------------------------------- 采购订单行：来源订单减少的数量（累计；采购数量不自动改）

ALTER TABLE `purchase_order_item`
    ADD COLUMN `so_reduced_quantity` int(11) NOT NULL DEFAULT 0 COMMENT '来源订单部分取消后减少的数量（累计，提示采购员处理）' AFTER `quantity`;

-- ---------------------------------------------------------------- 闲置库存：来源

ALTER TABLE `stock_hold`
    ADD COLUMN `source_type` tinyint(2) NOT NULL DEFAULT 1 COMMENT '来源（1-到货差异、2-订单部分取消）' AFTER `tenant_id`,
    ADD COLUMN `so_id`       bigint     NOT NULL DEFAULT 0 COMMENT '来源销售订单ID（订单部分取消），关联sales_order.id' AFTER `po_id`,
    ADD COLUMN `so_item_id`  bigint     NOT NULL DEFAULT 0 COMMENT '来源订单型号行ID（订单部分取消），关联sales_order_item.id' AFTER `so_id`,
    ADD INDEX `idx_hold_source` (`source_type`),
    ADD INDEX `idx_hold_so_item` (`so_item_id`),
    ADD INDEX `idx_hold_receipt` (`receipt_id`);

-- ---------------------------------------------------------------- 字典：部分取消原因

INSERT INTO `dict_type` (`tenant_id`, `dict_type`, `dict_name`, `is_builtin`, `status`, `remark`, `create_by`, `update_by`)
SELECT 0, 'partial_cancel_reason', '部分取消原因', 1, 1, '销售订单按型号部分取消的原因', 'sys', 'sys' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `dict_type` d WHERE d.`dict_type` = 'partial_cancel_reason' AND d.`tenant_id` = 0);

INSERT INTO `dict_item` (`tenant_id`, `dict_type_id`, `dict_type`, `item_code`, `item_name`, `item_name_en`, `item_value`, `sort_order`, `is_default`, `status`, `remark`, `create_by`, `update_by`)
SELECT 0, t.`id`, t.`dict_type`, v.code, v.name, '', v.code, v.s, v.d, 1, '', 'sys', 'sys'
FROM `dict_type` t
JOIN (
              SELECT 'OUT_OF_STOCK' AS code, '供应商缺货' AS name, 1 AS s, 1 AS d
    UNION ALL SELECT 'CUSTOMER',     '客户取消部分', 2, 0
    UNION ALL SELECT 'OTHER',        '其他',         3, 0
) v
WHERE t.`dict_type` = 'partial_cancel_reason' AND t.`tenant_id` = 0
  AND NOT EXISTS (SELECT 1 FROM `dict_item` i WHERE i.`dict_type` = 'partial_cancel_reason' AND i.`item_code` = v.code AND i.`tenant_id` = 0);

-- ---------------------------------------------------------------- 回退（手动执行；先确认 sales_order_partial_cancel 没有记录，
-- 有记录时旧版本会把剩余数量当成交数量显示，PI 转成的订单收款状态也会回到按 PI 合计）
-- DELETE FROM `dict_item` WHERE `dict_type` = 'partial_cancel_reason' AND `tenant_id` = 0;
-- DELETE FROM `dict_type` WHERE `dict_type` = 'partial_cancel_reason' AND `tenant_id` = 0;
-- ALTER TABLE `stock_hold` DROP INDEX `idx_hold_source`, DROP INDEX `idx_hold_so_item`, DROP INDEX `idx_hold_receipt`,
--     DROP COLUMN `source_type`, DROP COLUMN `so_id`, DROP COLUMN `so_item_id`;
-- ALTER TABLE `purchase_order_item` DROP COLUMN `so_reduced_quantity`;
-- DROP TABLE `sales_order_partial_cancel`;
-- ALTER TABLE `sales_order_item` DROP COLUMN `original_quantity`, DROP COLUMN `cancelled_quantity`;

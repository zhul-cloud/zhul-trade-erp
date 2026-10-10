-- ===========================
-- 出库与出运：发货通知 / 销售出库单、装箱、国内快递、出运单、单证组、运费分摊、货代对账（供应链第④期）
-- 见 openspec/changes/add-outbound-shipping
-- ===========================

ALTER TABLE `supplier`
    ADD COLUMN `volume_divisor` int(11) NOT NULL DEFAULT 5000 COMMENT '体积系数（服务商用于计费重：长×宽×高÷体积系数）' AFTER `supplier_type`;

ALTER TABLE `biz_attachment`
    MODIFY COLUMN `owner_type` varchar(16) NOT NULL DEFAULT '' COMMENT '所属单据类型（SHIPMENT-供应商发货单、RECEIPT-采购入库单、SHOOT-拍摄任务、LOGISTICS-出运单面单）',
    MODIFY COLUMN `kind` tinyint(2) NOT NULL DEFAULT 1 COMMENT '类别（1-图片、2-视频、3-文件，如 PDF 面单）';

ALTER TABLE `supplier_shipment`
    ADD COLUMN `direct_forwarder_id` bigint NULL COMMENT '直发货代：供应商直接发到这家货代（供应商ID），为空表示发到福州仓库' AFTER `po_id`,
    ADD COLUMN `logistics_id` bigint NULL COMMENT '直发货放进的出运单ID，关联logistics_shipment.id' AFTER `direct_forwarder_id`,
    ADD KEY `idx_direct_forwarder_id` (`direct_forwarder_id`),
    ADD KEY `idx_logistics_id` (`logistics_id`);

CREATE TABLE `outbound_order` (
    `id`          bigint      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`   int(11)     NOT NULL DEFAULT 0  COMMENT '租户ID',
    `ob_no`              varchar(32)  NOT NULL DEFAULT '' COMMENT '出库单编号（如 OB20261012001）',
    `so_id`              bigint       NOT NULL DEFAULT 0  COMMENT '销售订单ID，关联sales_order.id',
    `customer_id`        bigint       NOT NULL DEFAULT 0  COMMENT '客户ID，关联customer.id',
    `owner_id`           bigint       NOT NULL DEFAULT 0  COMMENT '订单业务员用户ID（数据权限）',
    `forwarder_id`       bigint       NOT NULL DEFAULT 0  COMMENT '货代（服务商）ID，关联supplier.id',
    `source`             tinyint(2)   NOT NULL DEFAULT 1  COMMENT '来源（1-发货通知、2-直发货代）',
    `status`             tinyint(2)   NOT NULL DEFAULT 1  COMMENT '状态（1-待打包、2-已打包、3-已交货代、4-已撤回）',
    `courier_waybill_id` bigint       NULL                COMMENT '国内快递ID，关联courier_waybill.id',
    `logistics_id`       bigint       NULL                COMMENT '出运单ID，关联logistics_shipment.id',
    `supplier_shipment_id` bigint     NULL                COMMENT '直发货代时的供应商发货单ID，关联supplier_shipment.id',
    `note`               varchar(300) NOT NULL DEFAULT '' COMMENT '备注',
    `withdraw_reason`    varchar(200) NOT NULL DEFAULT '' COMMENT '撤回原因',
    `packed_by`          bigint       NULL                COMMENT '打包人用户ID',
    `packed_at`          datetime     NULL                COMMENT '打包时间',
    `deleted_at`  datetime    NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`   varchar(32) NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`   varchar(32) NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted_at` (`deleted_at`),
    UNIQUE KEY `uk_tenant_ob_no` (`tenant_id`, `ob_no`),
    KEY `idx_so_id` (`so_id`),
    KEY `idx_customer_id` (`customer_id`),
    KEY `idx_owner_id` (`owner_id`),
    KEY `idx_forwarder_id` (`forwarder_id`),
    KEY `idx_status` (`status`),
    KEY `idx_courier_waybill_id` (`courier_waybill_id`),
    KEY `idx_logistics_id` (`logistics_id`),
    KEY `idx_supplier_shipment_id` (`supplier_shipment_id`)
) ENGINE=InnoDB AUTO_INCREMENT=10000 DEFAULT CHARSET=utf8mb4 COMMENT='销售出库单（发货通知生成）';

CREATE TABLE `outbound_order_item` (
    `id`          bigint      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`   int(11)     NOT NULL DEFAULT 0  COMMENT '租户ID',
    `outbound_id`   bigint        NOT NULL DEFAULT 0  COMMENT '出库单ID，关联outbound_order.id',
    `so_item_id`    bigint        NOT NULL DEFAULT 0  COMMENT '订单型号行ID，关联sales_order_item.id',
    `model`         varchar(128)  NOT NULL DEFAULT '' COMMENT '型号',
    `brand`         varchar(64)   NOT NULL DEFAULT '' COMMENT '品牌',
    `quantity`      int(11)       NOT NULL DEFAULT 0  COMMENT '数量',
    `unit_price_cny` decimal(18,6) NOT NULL DEFAULT 0  COMMENT '单价折人民币（订单单价×订单汇率，完整精度），按货值分摊运费用',
    `deleted_at`  datetime    NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`   varchar(32) NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`   varchar(32) NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted_at` (`deleted_at`),
    KEY `idx_outbound_id` (`outbound_id`),
    KEY `idx_so_item_id` (`so_item_id`)
) ENGINE=InnoDB AUTO_INCREMENT=10000 DEFAULT CHARSET=utf8mb4 COMMENT='销售出库单行';

CREATE TABLE `outbound_box` (
    `id`          bigint      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`   int(11)     NOT NULL DEFAULT 0  COMMENT '租户ID',
    `outbound_id`  bigint        NOT NULL DEFAULT 0  COMMENT '出库单ID，关联outbound_order.id',
    `box_no`       int(11)       NOT NULL DEFAULT 1  COMMENT '箱号（出库单内从 1 起）',
    `length`       int(11)       NOT NULL DEFAULT 0  COMMENT '长（厘米）',
    `width`        int(11)       NOT NULL DEFAULT 0  COMMENT '宽（厘米）',
    `height`       int(11)       NOT NULL DEFAULT 0  COMMENT '高（厘米）',
    `gross_weight` decimal(10,2) NOT NULL DEFAULT 0  COMMENT '毛重（千克）',
    `net_weight`   decimal(10,2) NULL                COMMENT '净重（千克），为空按毛重',
    `deleted_at`  datetime    NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`   varchar(32) NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`   varchar(32) NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted_at` (`deleted_at`),
    KEY `idx_outbound_id` (`outbound_id`)
) ENGINE=InnoDB AUTO_INCREMENT=10000 DEFAULT CHARSET=utf8mb4 COMMENT='出库单的箱子';

CREATE TABLE `outbound_box_item` (
    `id`          bigint      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`   int(11)     NOT NULL DEFAULT 0  COMMENT '租户ID',
    `box_id`           bigint  NOT NULL DEFAULT 0 COMMENT '箱ID，关联outbound_box.id',
    `outbound_item_id` bigint  NOT NULL DEFAULT 0 COMMENT '出库单行ID，关联outbound_order_item.id',
    `quantity`         int(11) NOT NULL DEFAULT 0 COMMENT '数量',
    `deleted_at`  datetime    NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`   varchar(32) NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`   varchar(32) NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted_at` (`deleted_at`),
    KEY `idx_box_id` (`box_id`),
    KEY `idx_outbound_item_id` (`outbound_item_id`)
) ENGINE=InnoDB AUTO_INCREMENT=10000 DEFAULT CHARSET=utf8mb4 COMMENT='箱内型号与数量';

CREATE TABLE `courier_waybill` (
    `id`          bigint      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`   int(11)     NOT NULL DEFAULT 0  COMMENT '租户ID',
    `carrier`      varchar(32)   NOT NULL DEFAULT '' COMMENT '快递公司',
    `tracking_no`  varchar(64)   NOT NULL DEFAULT '' COMMENT '快递单号',
    `sent_date`    date          NULL                COMMENT '发出日期',
    `freight`      decimal(18,2) NOT NULL DEFAULT 0  COMMENT '运费（CNY）',
    `payer_id`     bigint        NOT NULL DEFAULT 0  COMMENT '垫付人用户ID（以后走报销）',
    `forwarder_id` bigint        NOT NULL DEFAULT 0  COMMENT '货代ID，关联supplier.id',
    `status`       tinyint(2)    NOT NULL DEFAULT 1  COMMENT '状态（1-有效、2-已撤销）',
    `undo_reason`  varchar(200)  NOT NULL DEFAULT '' COMMENT '撤销原因',
    `deleted_at`  datetime    NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`   varchar(32) NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`   varchar(32) NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted_at` (`deleted_at`),
    KEY `idx_forwarder_id` (`forwarder_id`),
    KEY `idx_payer_id` (`payer_id`),
    KEY `idx_status` (`status`),
    KEY `idx_tracking_no` (`tracking_no`)
) ENGINE=InnoDB AUTO_INCREMENT=10000 DEFAULT CHARSET=utf8mb4 COMMENT='国内快递（出库单发往货代，可多张共用一票）';

CREATE TABLE `logistics_shipment` (
    `id`          bigint      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`   int(11)     NOT NULL DEFAULT 0  COMMENT '租户ID',
    `sh_no`        varchar(32)   NOT NULL DEFAULT '' COMMENT '出运单编号（如 SH20261014001）',
    `customer_id`  bigint        NOT NULL DEFAULT 0  COMMENT '客户ID，关联customer.id',
    `forwarder_id` bigint        NOT NULL DEFAULT 0  COMMENT '货代ID，关联supplier.id',
    `owner_id`     bigint        NOT NULL DEFAULT 0  COMMENT '业务员用户ID（数据权限）',
    `status`       tinyint(2)    NOT NULL DEFAULT 1  COMMENT '状态（1-待出运、2-已出运、3-已作废）',
    `carrier`      varchar(32)   NOT NULL DEFAULT '' COMMENT '承运商（如 DHL）',
    `waybill_no`   varchar(64)   NOT NULL DEFAULT '' COMMENT '运单号',
    `shipped_date` date          NULL                COMMENT '出运日期',
    `freight`      decimal(18,2) NULL                COMMENT '国际运费（CNY）',
    `reconciled`   tinyint(1)    NOT NULL DEFAULT 0  COMMENT '是否已对账（0-否、1-是）',
    `void_reason`  varchar(200)  NOT NULL DEFAULT '' COMMENT '作废原因',
    `note`         varchar(300)  NOT NULL DEFAULT '' COMMENT '备注',
    `deleted_at`  datetime    NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`   varchar(32) NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`   varchar(32) NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted_at` (`deleted_at`),
    UNIQUE KEY `uk_tenant_sh_no` (`tenant_id`, `sh_no`),
    KEY `idx_customer_id` (`customer_id`),
    KEY `idx_forwarder_id` (`forwarder_id`),
    KEY `idx_owner_id` (`owner_id`),
    KEY `idx_status` (`status`),
    KEY `idx_shipped_date` (`shipped_date`)
) ENGINE=InnoDB AUTO_INCREMENT=10000 DEFAULT CHARSET=utf8mb4 COMMENT='出运单';

CREATE TABLE `shipment_doc_group` (
    `id`          bigint      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`   int(11)     NOT NULL DEFAULT 0  COMMENT '租户ID',
    `logistics_id` bigint       NOT NULL DEFAULT 0  COMMENT '出运单ID，关联logistics_shipment.id',
    `so_ids`       varchar(500) NOT NULL DEFAULT '' COMMENT '包含的订单ID（逗号分隔）',
    `ci_no`        varchar(32)  NOT NULL DEFAULT '' COMMENT '商业发票编号',
    `pl_no`        varchar(32)  NOT NULL DEFAULT '' COMMENT '装箱单编号（与 CI 共用主体）',
    `payment_ref`  varchar(200) NOT NULL DEFAULT '' COMMENT '付款参考（如 TT 号），写进 CI',
    `status`       tinyint(2)   NOT NULL DEFAULT 1  COMMENT '状态（1-有效、2-已作废）',
    `deleted_at`  datetime    NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`   varchar(32) NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`   varchar(32) NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted_at` (`deleted_at`),
    KEY `idx_logistics_id` (`logistics_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=10000 DEFAULT CHARSET=utf8mb4 COMMENT='出运单的单证组（一张 CI + 一张 PL）';

CREATE TABLE `freight_allocation` (
    `id`          bigint      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`   int(11)     NOT NULL DEFAULT 0  COMMENT '租户ID',
    `source_type` tinyint(2)    NOT NULL DEFAULT 1  COMMENT '运费来源（1-国内快递、2-国际运费）',
    `source_id`   bigint        NOT NULL DEFAULT 0  COMMENT '来源ID（courier_waybill.id 或 logistics_shipment.id）',
    `outbound_id` bigint        NOT NULL DEFAULT 0  COMMENT '出库单ID，关联outbound_order.id',
    `box_id`      bigint        NOT NULL DEFAULT 0  COMMENT '箱ID，关联outbound_box.id',
    `so_item_id`  bigint        NOT NULL DEFAULT 0  COMMENT '订单型号行ID，关联sales_order_item.id',
    `amount`      decimal(18,2) NOT NULL DEFAULT 0  COMMENT '分到的运费（CNY）',
    `deleted_at`  datetime    NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`   varchar(32) NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`   varchar(32) NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted_at` (`deleted_at`),
    KEY `idx_source` (`source_type`, `source_id`),
    KEY `idx_outbound_id` (`outbound_id`),
    KEY `idx_box_id` (`box_id`),
    KEY `idx_so_item_id` (`so_item_id`)
) ENGINE=InnoDB AUTO_INCREMENT=10000 DEFAULT CHARSET=utf8mb4 COMMENT='运费分摊结果（运费 → 箱 → 订单型号行）';

CREATE TABLE `forwarder_statement` (
    `id`          bigint      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`   int(11)     NOT NULL DEFAULT 0  COMMENT '租户ID',
    `forwarder_id`    bigint        NOT NULL DEFAULT 0  COMMENT '货代ID，关联supplier.id',
    `period`          char(7)       NOT NULL DEFAULT '' COMMENT '对账月份（YYYY-MM）',
    `status`          tinyint(2)    NOT NULL DEFAULT 1  COMMENT '状态（1-草稿、2-已确认）',
    `our_total`       decimal(18,2) NOT NULL DEFAULT 0  COMMENT '我们登记的运费合计（CNY）',
    `statement_total` decimal(18,2) NOT NULL DEFAULT 0  COMMENT '对账金额合计（CNY）',
    `confirmed_by`    bigint        NULL                COMMENT '确认人用户ID',
    `confirmed_at`    datetime      NULL                COMMENT '确认时间',
    `deleted_at`  datetime    NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`   varchar(32) NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`   varchar(32) NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted_at` (`deleted_at`),
    KEY `idx_forwarder_id` (`forwarder_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=10000 DEFAULT CHARSET=utf8mb4 COMMENT='货代月结对账单';

CREATE TABLE `forwarder_statement_line` (
    `id`          bigint      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`   int(11)     NOT NULL DEFAULT 0  COMMENT '租户ID',
    `statement_id`     bigint        NOT NULL DEFAULT 0  COMMENT '对账单ID，关联forwarder_statement.id',
    `logistics_id`     bigint        NOT NULL DEFAULT 0  COMMENT '出运单ID，关联logistics_shipment.id',
    `our_freight`      decimal(18,2) NOT NULL DEFAULT 0  COMMENT '我们登记的运费（CNY）',
    `statement_amount` decimal(18,2) NOT NULL DEFAULT 0  COMMENT '对账金额（CNY）',
    `note`             varchar(200)  NOT NULL DEFAULT '' COMMENT '差额说明',
    `deleted_at`  datetime    NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`   varchar(32) NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`   varchar(32) NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted_at` (`deleted_at`),
    KEY `idx_statement_id` (`statement_id`),
    KEY `idx_logistics_id` (`logistics_id`)
) ENGINE=InnoDB AUTO_INCREMENT=10000 DEFAULT CHARSET=utf8mb4 COMMENT='货代对账单的出运单';


-- ---------------------------------------------------------------- 菜单与按钮
INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`)
SELECT v.* FROM (
              SELECT 100095 AS id, 100090 AS pid, 'RS2100095' AS code, '出库打包' AS name, 2 AS type, 2 AS sort, 'export' AS li, '' AS lsi, '' AS di, '' AS dsi,
                     '/warehouse/outbounds' AS path, '' AS permission, 1 AS status, '' AS micro_app, 'sys' AS cb, 'sys' AS ub
    UNION ALL SELECT 100096, 0,      'RS1100096', '单证物流', 1, 4, 'global',    '', '', '', '/logistics', '', 1, '', 'sys', 'sys'
    UNION ALL SELECT 100097, 100096, 'RS2100097', '出运单',   2, 1, 'send',      '', '', '', '/logistics/shipments', '', 1, '', 'sys', 'sys'
    UNION ALL SELECT 100098, 100096, 'RS2100098', '货代对账', 2, 2, 'audit',     '', '', '', '/logistics/statements', '', 1, '', 'sys', 'sys'
    UNION ALL SELECT 110197, 100079, 'RS3110197', '发货通知',     3, 20, '', '', '', '', '', 'sales:order:ship-notice', 1, '', 'sys', 'sys'
    UNION ALL SELECT 110198, 100095, 'RS3110198', '打包出库',     3, 1, '', '', '', '', '', 'warehouse:outbound:pack', 1, '', 'sys', 'sys'
    UNION ALL SELECT 110199, 100097, 'RS3110199', '出运单编辑',   3, 1, '', '', '', '', '', 'logistics:shipment:edit', 1, '', 'sys', 'sys'
    UNION ALL SELECT 110200, 100098, 'RS3110200', '货代对账',     3, 1, '', '', '', '', '', 'logistics:statement:edit', 1, '', 'sys', 'sys'
) v
WHERE NOT EXISTS (SELECT 1 FROM `resource` r WHERE r.`id` = v.id);

-- 仓库管理：入库验收、出库打包、暂存货、拍摄任务
UPDATE `resource` SET `sort` = 3, `update_time` = NOW(), `update_by` = 'sys' WHERE `id` = 100092;
UPDATE `resource` SET `sort` = 4, `update_time` = NOW(), `update_by` = 'sys' WHERE `id` = 100093;

UPDATE `tenant_package`
SET `menu_ids` = JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(
                 JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(
        `menu_ids`, '$', 100095), '$', 100096), '$', 100097), '$', 100098), '$', 110197), '$', 110198), '$', 110199), '$', 110200)
WHERE `name` IN ('标准版', '旗舰版') AND NOT JSON_CONTAINS(`menu_ids`, '100095');

-- 仓库：出库打包
INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT 'ROLE_WH', s.`id`, s.`code` FROM `resource` s
WHERE s.`id` IN (100095, 110198)
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = 'ROLE_WH' AND y.`resource_id` = s.`id`);

-- 业务员（有「销售订单」菜单的角色）：发货通知、单证物流 → 出运单
INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT x.`role_code`, s.`id`, s.`code`
FROM `role_resource` x
JOIN `resource` s ON s.`id` IN (110197, 100096, 100097, 110199)
WHERE x.`resource_id` = 100079
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = x.`role_code` AND y.`resource_id` = s.`id`);

-- 内置租户管理员：全部
INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT r.`code`, s.`id`, s.`code`
FROM `role` r
JOIN `resource` s ON s.`id` IN (100095, 100096, 100097, 100098, 110197, 110198, 110199, 110200)
WHERE r.`code` = 'ROLE_ADMIN' AND r.`is_built_in` = 1
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = r.`code` AND y.`resource_id` = s.`id`);
